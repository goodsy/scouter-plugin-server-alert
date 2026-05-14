package scouter.plugin.server.alert.uitl

import scouter.server.Configure
import scouter.server.Logger

/**
 * scouter agent 화이트리스트 필터
 * scouter.conf 설정:
 *   ext_plugin_scouter_alert_agent_whitelist=ims,mms,bld-card,api,batch
 * - objName에 whitelist 항목이 포함(contains)되면 허용
 * - 목록 미설정 시 전체 허용 (fallback)
 * - 대소문자 무시
 */
class AgentFilter {

    private var allowAllLogged = false
    private val conf = Configure.getInstance()

    /**
     * @param objName PerfCounterPack / AlertPack 등의 objName
     * @return true = 허용 (전송 대상), false = 스킵
     */
    fun isAllowed(objName: String?): Boolean {
        if (objName == null) return false

        val raw = conf.getValue(CONF_KEY, "")
        if (raw.isNullOrBlank()) {
            // 미설정 → 전체 미허용
            return false
        }

        val whitelist = raw.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.lowercase() }
            .toSet()

        val lowerObjName = objName.lowercase()
        val allowed = whitelist.any { lowerObjName.contains(it) }

        when {
            !allowed -> {
                if (!allowAllLogged) {
                    allowAllLogged = true
                }
            }
        }
        return allowed
    }

    companion object {
        private const val CONF_KEY = "ext_plugin_scouter_alert_agent_whitelist"
    }
}
