package scouter.plugin.server.alert.monitoring

import scouter.lang.pack.PerfCounterPack
import scouter.lang.value.NumberValue
import scouter.plugin.server.alert.common.AlertLevel
import scouter.plugin.server.alert.uitl.LogUtil
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * PerfCounterPack 수신 시 임계치 체크
 *
 * [ErrorRate 정책]
 *   - 현재값 > 5분 전값 → 발송
 *   - 발송 후 5분간 cooldown → 미발송
 *   - cooldown 해제 후: 현재값 <= 5분 전값 AND 현재값 <= warnValue → 미발송
 *
 * [그 외 지표]
 *   - sentOnce: 1회 발송 후 복구 전까지 재발송 없음
 *   - cooldown: cooldownSec 동안 재발송 없음
 */
class CounterMonitor {

    private val cooldownMap = ConcurrentHashMap<String, Long>()
    private val prevTpsMap = ConcurrentHashMap<String, Double>()
    private val prevTpsTimeMap = ConcurrentHashMap<String, Long>()
    private val errorHistoryMap = ConcurrentHashMap<String, ErrorHistory>()
    private val sentOnceSet = ConcurrentHashMap.newKeySet<String>()

    private val lastCleanup = AtomicLong(System.currentTimeMillis())

    /** Alert 발생에 필요한 기본 정보를 담는 컨텍스트 (내부용) */
    private data class AlertContext(
        val events: MutableList<AlertEvent>,
        val objName: String,
        val metric: String,
        val value: Double
    )

    /** ErrorRate 체크에 필요한 상태를 묶는 컨텍스트 (내부용) */
    private data class ErrorRateContext(
        val events: MutableList<AlertEvent>,
        val objName: String,
        val currentErr: Double,
        val prevErr: Double,
        val hasPrev: Boolean,
        val now: Long,
        val cooldownMs: Long
    )

    fun check(pack: PerfCounterPack, config: ThresholdConfig): List<AlertEvent> {
        val events = mutableListOf<AlertEvent>()
        if (!config.isEnabled()) return events

        val objName = pack.objName

        checkTps(events, pack, config, objName)
        checkErrorRate(events, pack, config, objName)

        checkAllGroups(events, pack, config, "ActiveService", objName)
        checkAllGroups(events, pack, config, "Elapsed", objName)
        checkAllGroups(events, pack, config, "GcTime", objName)
        checkAllGroups(events, pack, config, "GcCount", objName)
        checkAllGroups(events, pack, config, "HeapPct", objName)
        checkHeapUsed(events, pack, config, objName)

        cleanupIfNeeded()
        return events
    }

    // TPS
    private fun checkTps(events: MutableList<AlertEvent>, pack: PerfCounterPack, config: ThresholdConfig, objName: String) {
        val list = config.getAll("TPS")
        if (list.isEmpty()) return

        val tps = getDouble(pack, "TPS")
        val prevTps = prevTpsMap[objName]
        val ctx = AlertContext(events, objName, "TPS", tps)

        list.forEach { th ->
            checkThresholdAndRecovery(ctx, th, listOf("TPS_SPIKE"))

            if (th.tpsZeroCheck && prevTps != null && prevTps > 0.0 && tps == 0.0) {
                addEventsForGroups(ctx, AlertLevel.WARN, "TPS 0 감지 (이전: $prevTps → 현재: 0)", th)
            }
            if (th.hasSpikeCheck() && prevTps != null && prevTps > th.tpsSpikeMinBase && tps > prevTps * th.tpsSpikeRatio) {
                val spikeCtx = ctx.copy(metric = "TPS_SPIKE")
                val msg = "TPS 급증 (이전: %.1f → 현재: %.1f, %.1f배)".format(prevTps, tps, tps / prevTps)
                addEventsForGroups(spikeCtx, AlertLevel.WARN, msg, th)
            }
        }
        prevTpsMap[objName] = tps
        prevTpsTimeMap[objName] = System.currentTimeMillis()
    }

    // ErrorRate
    // ① 절대값: 현재값 > cooldownSec 전 값 → 발송
    // ② 증가율: errorRateDiffWarn/Fatal 설정 시에만 동작
    private fun checkErrorRate(events: MutableList<AlertEvent>, pack: PerfCounterPack, config: ThresholdConfig, objName: String) {
        val list = config.getAll("ErrorRate")
        if (list.isEmpty()) return

        val currentErr = getDouble(pack, "ErrorRate")
        val now = System.currentTimeMillis()

        val history = errorHistoryMap.computeIfAbsent(objName) { ErrorHistory() }
        history.add(currentErr, now)

        list.forEach { th ->

            val cooldownMs = th.cooldownSec * 1000L
            val targetTime = now - cooldownMs
            val hasPrev = history.hasDataBefore(targetTime)
            val prevErr = if (hasPrev) history.getRateBefore(targetTime) else -1.0
            val ctx = ErrorRateContext(events, objName, currentErr, prevErr, hasPrev, now, cooldownMs)

            // ① 절대값 체크
            checkErrorRateAbsolute(ctx, th)

            // ② 증가율 체크 - errorRateDiffWarn 또는 errorRateDiffFatal 설정 시에만
            if (th.hasErrorRateDiff()) {
                checkErrorRateDiff(ctx, th)
            }
        }
    }

    private fun checkErrorRateAbsolute(ctx: ErrorRateContext, th: MetricThreshold) {

        val (events, objName, currentErr, prevErr, hasPrev, now, cooldownMs) = ctx

        val cooldownKey = "${objName}::ErrorRate::${th.channelGroups.sorted().joinToString(",")}"
        val last = cooldownMap[cooldownKey]
        val inCooldown = last != null && now - last < cooldownMs

        //ErrorRate 절대값 cooldown 중
        if (inCooldown) return
        //ErrorRate 절대값 미발송
        if (currentErr <= prevErr && currentErr <= th.warnValue)  return

        val level = th.decideLevel(currentErr) ?: return
        cooldownMap[cooldownKey] = now

        val msg =
            if (!hasPrev || prevErr < 0) {
                "에러율 최초 감지 (현재: %.1f%%)".format(currentErr)
            }else if (currentErr < prevErr) {
                "에러율 감소 (${th.cooldownSec}초 전 : %.1f%% / 현재: %.1f%%)".format(prevErr, currentErr)
            }else{
                "에러율 상승 (${th.cooldownSec}초 전 : %.1f%% / 현재: %.1f%%)".format(prevErr, currentErr)
            }

        LogUtil.info("Alert Sent (ErrorRate 절대값): $cooldownKey, prev=$prevErr, current=$currentErr, level=$level")

        th.channelGroups.forEach { group ->
            events.add(AlertEvent(objName, "ErrorRate", currentErr, level, msg, group))
        }
    }

    private fun checkErrorRateDiff(ctx: ErrorRateContext, th: MetricThreshold) {
        val (events, objName, currentErr, prevErr, hasPrev, now, cooldownMs) = ctx
        if (!hasPrev || prevErr < 0) return

        val diff = currentErr - prevErr
        if (diff <= 0) return

        val cooldownKey = "${objName}::ErrorRateDiff::${th.channelGroups.sorted().joinToString(",")}"
        val last = cooldownMap[cooldownKey]
        val inCooldown = last != null && now - last < cooldownMs

        if (inCooldown) {
            LogUtil.info("ErrorRate 증가율 cooldown 중 [objName=$objName, diff=$diff, remain=${cooldownMs - (now - last!!)}ms]")
            return
        }

        val level = when {
            th.errorRateDiffFatal >= 0 && diff >= th.errorRateDiffFatal -> AlertLevel.FATAL
            th.errorRateDiffWarn >= 0 && diff >= th.errorRateDiffWarn -> AlertLevel.WARN
            else -> null
        } ?: return

        cooldownMap[cooldownKey] = now
        val msg = "에러율 급증 (${th.cooldownSec}초 전 대비 +%.1f%%p, 현재: %.1f%%)".format(diff, currentErr)
        LogUtil.info("Alert Sent (ErrorRate 증가율): $cooldownKey, diff=$diff, current=$currentErr, level=$level")

        th.channelGroups.forEach { group ->
            events.add(AlertEvent(objName, "ErrorRateDiff", diff, level, msg, group))
        }
    }

    // -----------------------------------------------
    // HeapUsed (bytes → MB 변환)
    // -----------------------------------------------
    private fun checkHeapUsed(events: MutableList<AlertEvent>, pack: PerfCounterPack, config: ThresholdConfig, objName: String) {
        val list = config.getAll("HeapUsed")
        if (list.isEmpty()) return

        val heapMb = getDouble(pack, "HeapUsed") / (1024.0 * 1024.0)
        val ctx = AlertContext(events, objName, "HeapUsed(MB)", heapMb)
        list.forEach { th ->
            checkThresholdAndRecovery(ctx, th)
        }
    }

    // 공통 지표 (ActiveService / Elapsed / GcTime / GcCount / HeapPct)
    private fun checkAllGroups(
        events: MutableList<AlertEvent>,
        pack: PerfCounterPack,
        config: ThresholdConfig,
        metricName: String,
        objName: String
    ) {
        val list = config.getAll(metricName)
        if (list.isEmpty()) return

        val value = getDouble(pack, metricName)
        val ctx = AlertContext(events, objName, metricName, value)
        list.forEach { th ->
            checkThresholdAndRecovery(ctx, th)
        }
    }

    // sentOnce / cooldown 공통 처리
    private fun checkThresholdAndRecovery(ctx: AlertContext, th: MetricThreshold, subMetrics: List<String> = emptyList()) {
        if (th.sentOnce && th.isRecovered(ctx.value)) {
            resetSentOnce(ctx.objName, ctx.metric, th.channelGroups, th)
            subMetrics.forEach { sub ->
                resetSentOnce(ctx.objName, sub, th.channelGroups, th)
            }
        }
        processThreshold(ctx, th)
    }

    private fun processThreshold(ctx: AlertContext, th: MetricThreshold) {
        val level = th.decideLevel(ctx.value)
        if (level != null) {
            val msg = th.formatMessage(ctx.metric, ctx.value, level)
            addEventsForGroups(ctx, level, msg, th)
        }
    }

    private fun addEventsForGroups(
        ctx: AlertContext,
        level: AlertLevel,
        message: String,
        th: MetricThreshold
    ) {
        val groups = th.channelGroups
        if (groups.isEmpty()) return

        val now = System.currentTimeMillis()
        groups.forEach { group ->
            if (th.sentOnce) {
                val key = "${ctx.objName}::${ctx.metric}::$group::${th.stableId()}"
                if (sentOnceSet.add(key)) {
                    LogUtil.info("Alert Sent (sentOnce): $key, value=${ctx.value}")
                    ctx.events.add(AlertEvent(ctx.objName, ctx.metric, ctx.value, level, message, group))
                }
            } else {
                val key = "${ctx.objName}::${ctx.metric}::$group::$level"
                val last = cooldownMap[key]
                if (last == null || now - last >= th.cooldownSec * 1000L) {
                    cooldownMap[key] = now
                    LogUtil.info("Alert Sent (cooldown): $key, value=${ctx.value}")
                    ctx.events.add(AlertEvent(ctx.objName, ctx.metric, ctx.value, level, message, group))
                }
            }
        }
    }


    private fun getDouble(pack: PerfCounterPack, key: String): Double {
        return try {
            val v = pack.data.get(key)
            when (v){
                is NumberValue -> (v as Number).toDouble()
                is Number -> v.toDouble()
                else -> 0.0
            }
        }catch (e: Exception){
            LogUtil.error(this.javaClass, "지표 없음 : $key", e)
            0.0
        }
    }


    private fun resetSentOnce(objName: String, metric: String, groups: List<String>?, th: MetricThreshold) {
        groups?.forEach { group ->
            val key = "$objName::$metric::$group::${th.stableId()}"
            if (sentOnceSet.remove(key)) {
                LogUtil.info("Alert Reset (sentOnce): $key")
            }
        }
    }

    private fun cleanupIfNeeded() {
        val now = System.currentTimeMillis()
        val last = lastCleanup.get()
        if (now - last < CLEANUP_INTERVAL_MS) return
        if (!lastCleanup.compareAndSet(last, now)) return

        cooldownMap.entries.removeIf { now - it.value > MAX_COOLDOWN_MS }

        prevTpsTimeMap.entries.removeIf { (objName, time) ->
            val stale = now - time > STALE_AGENT_MS
            if (stale) prevTpsMap.remove(objName)
            stale
        }

        errorHistoryMap.entries.removeIf { it.value.isEmpty() }
    }

    companion object {
        private const val CLEANUP_INTERVAL_MS = 5 * 60 * 1000L
        private const val STALE_AGENT_MS      = 10 * 60 * 1000L
        private const val MAX_COOLDOWN_MS     = 60 * 60 * 1000L
    }
}
