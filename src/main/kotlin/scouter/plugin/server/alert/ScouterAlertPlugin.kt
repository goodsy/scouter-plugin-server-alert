package scouter.plugin.server.alert

import scouter.lang.pack.AlertPack
import scouter.lang.pack.ObjectPack
import scouter.lang.pack.PerfCounterPack
import scouter.lang.pack.XLogPack
import scouter.lang.plugin.PluginConstants
import scouter.lang.plugin.annotation.ServerPlugin
import scouter.plugin.server.alert.common.AlertLevel
import scouter.plugin.server.alert.monitoring.CounterMonitor
import scouter.plugin.server.alert.monitoring.ThresholdConfigLoader
import scouter.plugin.server.alert.uitl.AgentFilter
import scouter.plugin.server.alert.formatter.ChannelDispatcher
import scouter.plugin.server.alert.monitoring.XLogErrorMonitor
import scouter.server.Configure
import scouter.server.core.AgentManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Scouter Server Built-in Plugin
 *
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │  agent 화이트리스트 필터 → 3채널 전송 (telegram/slack/email)           │
 * │  임계치 설정: conf/metric-thresholds.json (60초 폴링 자동 반영)         │
 * └─────────────────────────────────────────────────────────────────────┘
 */
class ScouterAlertPlugin {

    private val conf = Configure.getInstance()
    private val filter = AgentFilter()
    private val dispatcher = ChannelDispatcher()
    private val monitor = CounterMonitor() // TPS 이전값 상태 보관
    private val xlogMonitor = XLogErrorMonitor()
    private val registeredAgents = ConcurrentHashMap.newKeySet<Int>()

    // -----------------------------------------------
    // ① Alert Plugin - Alert 수신
    // -----------------------------------------------
    @ServerPlugin(PluginConstants.PLUGIN_SERVER_ALERT)
    fun alert(pack: AlertPack) {
        // AGENT UP/DOWN 이벤트 처리
        if (handleAgentStatusAlert(pack)) return

        val objName = AgentManager.getAgentName(pack.objHash) ?: return
        if (!isAllowed(objName)) return

        // 매칭되는 지표가 없거나 설정이 없을 경우 기본 "all" 그룹 발송
        dispatcher.dispatchAlert(
            objName         = objName,
            pack            = pack,
            thresholdConfig = ThresholdConfigLoader.get(),
        )
    }

    // -----------------------------------------------
    // ② XLog Plugin - XLog 수신
    // -----------------------------------------------
    @ServerPlugin(PluginConstants.PLUGIN_SERVER_XLOG)
    fun xlog(pack: XLogPack) {
        if (!conf.getBoolean("ext_plugin_scouter_alert_xlog_enabled", false)) return

        val objName = AgentManager.getAgentName(pack.objHash) ?: return
        if (!isAllowed(objName)) return

        val thresholdMs = conf.getInt("ext_plugin_scouter_alert_xlog_threshold_ms", 3000)
        if (pack.elapsed <= thresholdMs) return

        val event = xlogMonitor.handle(pack)

        if (event != null) {
            // XLog Error 이벤트: 에러 포맷
            dispatcher.dispatchXlogError(
                objName         = objName,
                event           = event,
                thresholdConfig = ThresholdConfigLoader.get(),
            )
        } else {
            // Slow TX (에러 없음): slow 포맷
            dispatcher.dispatchXlogSlow(
                objName         = objName,
                pack            = pack,
                thresholdMs     = thresholdMs,
                thresholdConfig = ThresholdConfigLoader.get(),
            )
        }
    }

    // -----------------------------------------------
    // ③ Object Plugin - Agent heartbeat 수신
    // -----------------------------------------------
    @ServerPlugin(PluginConstants.PLUGIN_SERVER_OBJECT)
    fun objectPlugin(pack: ObjectPack) {
        if (!isAllowed(pack.objName)) return

        // 최초 연결 유무
        val cached = AgentManager.getAgent(pack.objHash)
        if (cached != null) return

        // 중복 알림 방지 : 같은 collector 세션에 이미 UP 일람 발송 존재하면 Skip
        if (!registeredAgents.add(pack.objHash)) return

        dispatcher.dispatchObjectStatus(
            pack            = pack,
            status          = "UP ✅",
            level           = AlertLevel.INFO,
            thresholdConfig = ThresholdConfigLoader.get(),
            ignoreMinLevel  = true,
        )
    }

    // -----------------------------------------------
    // ④ Counter Plugin - 성능 지표 임계치 감시
    // -----------------------------------------------
    @ServerPlugin(PluginConstants.PLUGIN_SERVER_COUNTER)
    fun counter(pack: PerfCounterPack) {
        if (!isAllowed(pack.objName)) return
        val thresholdConfig = ThresholdConfigLoader.get()
        if (thresholdConfig == null || !thresholdConfig.isEnabled()) return

        val events = monitor.check(pack, thresholdConfig)
        for (event in events) {
            dispatcher.dispatchCounterAlert(event, thresholdConfig)
        }
    }

    private fun handleAgentStatusAlert(pack: AlertPack): Boolean {
        val title = pack.title ?: return false
        val titleUpper = title.uppercase()

        val isDown = titleUpper.contains("INACTIVE_OBJECT")
        val isReconnect = titleUpper.contains("ACTIVATED_OBJECT")

        if (!isDown && !isReconnect) return false

        var objName = AgentManager.getAgentName(pack.objHash) ?: pack.objType
        if (!isAllowed(objName)) return true
        if (isDown) registeredAgents.remove(pack.objHash)

        val objectPack = AgentManager.getAgent(pack.objHash)

        if (objectPack != null) {
            val (status, level) =
                if (isDown)
                    "DOWN 🔴" to AlertLevel.FATAL
                else
                    "RECONNECTED ✅" to AlertLevel.INFO

            dispatcher.dispatchObjectStatus(
                pack            = objectPack,
                status          = status,
                level           = level,
                thresholdConfig = ThresholdConfigLoader.get(),
                ignoreMinLevel  = true,
            )
        } else {
            // objectPack 없는 경우 Alert 팩 그대로 발송
            dispatcher.dispatchAlert(
                objName         = objName!!,
                pack            = pack,
                thresholdConfig = ThresholdConfigLoader.get(),
                ignoreMinLevel  = true,
            )
        }

        return true
    }

    // -----------------------------------------------
    // 공통: agent 화이트리스트 체크
    // -----------------------------------------------
    private fun isAllowed(objName: String?): Boolean {
        return filter.isAllowed(objName)
    }
}

