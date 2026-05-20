package scouter.plugin.server.alert.monitoring

import scouter.plugin.server.alert.common.AlertLevel

/**
 * metric-thresholds.json thresholds[] 단건 모델
 *
 * channelGroups 배열 지원:
 *   동일 임계치를 여러 그룹에 동시 적용 가능
 *   예) "channelGroups": ["ops", "dev"]
 *   예) "channelGroups": ["dev"]          ← 단일 그룹도 배열로 작성
 *
 * [지원 metric]
 *   TPS / ActiveService / ErrorRate / Elapsed
 *   GcTime / GcCount / HeapPct / HeapUsed
 */
data class MetricThreshold(
    /** 지표명 (PerfCounterPack key, 대소문자 무시) */
    var metric: String? = null,
    /** false = 체크 스킵 */
    var enabled: Boolean = true,
    /** 초과 시 WARN (-1 = 사용 안 함) */
    var warnValue: Double = -1.0,
    /** 초과 시 ERROR (-1 = 사용 안 함) */
    var errorValue: Double = -1.0,
    /** 초과 시 FATAL (-1 = 사용 안 함) */
    var fatalValue: Double = -1.0,
    /**
     * 전송 채널 그룹 목록
     * 복수 지정 시 각 그룹의 레벨별 채널로 모두 전송
     * 예) ["ops", "dev"] → ops 채널 + dev 채널 동시 전송
     */
    var channelGroups: List<String> = listOf("all"),
    /** TPS 전용: 이전 TPS > 0 → 현재 0 이면 WARN */
    var tpsZeroCheck: Boolean = false,
    /** TPS 전용: 이전 TPS 대비 N배 초과 시 WARN (0 = 사용 안 함) */
    var tpsSpikeRatio: Double = 0.0,
    /** TPS 전용: TPS Spike 최소 임계치 값 (0 = 사용 안 함) */
    var tpsSpikeMinBase: Double = 1.0,
    /** ErrorRate 전용 : 에러율 증가율 WARN 임계치 (%p) */
    var errorRateDiffWarn: Double = -1.0,
    /** ErrorRate 전용 : 에러율 증가율 FATAL 임계치 (%p) */
    var errorRateDiffFatal: Double = -1.0,
    /** 동일 objName+metric+group+level 중복 방지 대기시간(초) */
    var cooldownSec: Int = 60,
    /**
     * true = 알림 1회 발송 후 수치 복구전까지 재발송 없음
     * 복구 기준(지표별)
     *  ErrorRate → 0% 복구 시 리셋
     *  TPS       → 0 < tps < warnValue 복구 시 리셋
     *  그 외 지표  → value <= warnValue 복구 시 리셋
     *  false = 기존 cooldownSec 방식 유지
     */
    var sentOnce: Boolean = false,
) {
    fun isTps(): Boolean = "TPS".equals(metric, ignoreCase = true)

    fun isErrorRate(): Boolean = "ErrorRate".equals(metric, ignoreCase = true)

    fun hasWarnValue(): Boolean = warnValue >= 0

    fun hasErrorValue(): Boolean = errorValue >= 0

    fun hasFatalValue(): Boolean = fatalValue >= 0

    fun hasSpikeCheck(): Boolean = tpsSpikeRatio > 0

    fun hasErrorRateDiff(): Boolean = errorRateDiffWarn >= 0 || errorRateDiffFatal >= 0

    fun isRecovered(value: Double): Boolean {
        if (!hasWarnValue()) return false

        return when {
            isErrorRate() -> value <= (warnValue * 0.5)
            isTps() -> value > 0 && value <= (warnValue * 0.9)
            else -> value <= (warnValue * 0.9)
        }
    }

    fun decideLevel(value: Double): AlertLevel? {
        return when {
            hasFatalValue() && value > fatalValue -> AlertLevel.FATAL
            hasErrorValue() && value > errorValue -> AlertLevel.ERROR
            hasWarnValue() && value > warnValue -> AlertLevel.WARN
            else -> null
        }
    }

    fun stableId(): String {
        return "${metric?.uppercase()}::${channelGroups.sorted().joinToString(",")}"
    }

    fun formatMessage(
        metricName: String,
        value: Double,
        level: AlertLevel,
    ): String {
        return "[${channelGroups.joinToString("+")}] $metricName $level 초과 (%.2f)".format(value)
    }

    override fun toString(): String {
        // return "MetricThreshold(metric=$metric, groups=$channelGroups, warn=$warnValue, error=$errorValue, fatal=$fatalValue, cooldownSec=$cooldownSec)"
        return "MetricThreshold(metric=$metric, warn=$warnValue, error=$errorValue, fatal=$fatalValue, cooldownSec=$cooldownSec)"
    }
}
