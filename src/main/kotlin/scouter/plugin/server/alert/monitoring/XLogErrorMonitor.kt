package scouter.plugin.server.fingerpay.monitoring

import scouter.lang.TextTypes
import scouter.lang.pack.XLogPack
import scouter.plugin.server.alert.common.AlertLevel
import scouter.plugin.server.alert.monitoring.ErrorDuplicateGuard
import scouter.plugin.server.alert.uitl.LogUtil
import scouter.server.core.AgentManager
import scouter.server.db.TextRD
import scouter.util.DateUtil
import java.net.InetAddress

class XLogErrorMonitor {
    fun handle(pack: XLogPack): XlogErrorEvent? {
        try {
            return toEvent(pack)
        } catch (e: Exception) {
            LogUtil.error(this.javaClass, "XLogErrorMonitor error", e)
            return null
        }
    }

    private fun toEvent(pack: XLogPack): XlogErrorEvent? {
        if (pack.error == 0) return null

        val objName = AgentManager.getAgent(pack.objHash)?.objName ?: return null

        val date = DateUtil.yyyymmdd(pack.endTime)

        val service =
            TextRD.getString(
                date,
                TextTypes.SERVICE,
                pack.service,
            ) ?: "unknown"

        val errorMessage =
            TextRD.getString(
                date,
                TextTypes.ERROR,
                pack.error,
            ) ?: "unknown"

        if (!ErrorDuplicateGuard.shouldSend(objName, service, errorMessage)) return null

        val clientIp =
            try {
                InetAddress.getByAddress(pack.ipaddr).hostAddress
            } catch (_: Exception) {
                null
            }

        val level =
            if (pack.elapsed >= 5000) {
                AlertLevel.FATAL
            } else {
                AlertLevel.ERROR
            }

        return XlogErrorEvent(
            objName = objName,
            service = service,
            errorMessage = errorMessage,
            txid = pack.txid,
            clientIp = clientIp,
            endTime = pack.endTime,
            level = level,
        )
    }
}
