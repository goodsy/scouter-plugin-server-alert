package scouter.plugin.server.alert.uitl

import scouter.lang.AlertLevel as ScouterAlertLevel
import scouter.lang.pack.AlertPack
import scouter.lang.pack.ObjectPack
import scouter.lang.pack.XLogPack
import scouter.plugin.server.alert.common.AlertLevel
import scouter.plugin.server.alert.monitoring.AlertEvent
import scouter.server.core.AgentManager
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object MessageFormatter {

    private val DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val PREFIX = "[Scouter]"

    data class AlertContent(
        val subject: String,
        val body: String,
    )

    private fun AlertLevel.emoji(): String {
        return when (this) {
            AlertLevel.FATAL -> "🚨"
            AlertLevel.ERROR -> "🔴"
            AlertLevel.WARN  -> "⚠️"
            AlertLevel.INFO  -> "✅"
            else -> "❓"
        }
    }

    private fun ts(epochMs: Long): String = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.of("Asia/Seoul")).format(DTF)

    fun formatAlert(pack: AlertPack): String {
        val level = AlertLevel.of(pack.level.toInt())
        val emoji = level.emoji()

        val body =
            buildString {
                appendLine("$emoji [$level] ${pack.title}")
                appendLine("시간  : ${MessageFormatter.ts(pack.time)}")
                appendLine("대상  : ${AgentManager.getAgentName(pack.objHash)} (${pack.objType})")
                appendLine("메시지: ${pack.message}")
            }

        return AlertContent(
            subject = "$PREFIX[${pack.title}]",
            body = body,
        )
    }

    fun formatObjectStatus(
        pack: ObjectPack,
        status: String,
    ): String =
        """
        |🖥️ [OBJECT $status] OBJECT 상태 변경
        |시간    : ${MessageFormatter.ts(System.currentTimeMillis())}
        |인스턴스: ${pack.objName}
        |타입    : ${pack.objType}
        """.trimMargin()

    fun formatObjectSubject(
        pack: ObjectPack,
        status: String,
    ): String = "${MessageFormatter.PREFIX}[OBJECT $status] ${pack.objName}"

    fun formatCounterAlert(event: AlertEvent): scouter.plugin.server.fingerpay.uitl.MessageFormatter.AlertContent {
        val emoji = event.level.emoji()
        val label = if (event.level == FingerAlertLevel.FATAL) "FATAL" else event.level.name

        val body =
            buildString {
                appendLine("$emoji [$label][Counter] ${event.metric}")
                appendLine("시간  : ${scouter.plugin.server.fingerpay.uitl.MessageFormatter.ts(System.currentTimeMillis())}")
                appendLine("대상  : ${event.objName}")
                appendLine("지표  : ${event.metric}")
                appendLine("현재값 : ${"%.2f".format(event.value)}")
                appendLine("내용  : ${event.message}")
            }

        return AlertContent(
            subject = "${MessageFormatter.PREFIX}[Counter][${event.level}] ${event.objName} - ${event.metric}",
            body = body,
        )
    }

    fun formatCounterSubject(event: AlertEvent): String = "${MessageFormatter.PREFIX}[Counter][${event.level}] ${event.objName} - ${event.metric}"

    fun formatEmailSubject(pack: AlertPack): String {
        return "[%s] %s - %s".format(
            ScouterAlertLevel.getName(pack.level), AgentManager.getAgentName(pack.objHash), pack.title
        )
    }

    fun formatEmailBody(pack: AlertPack): String {
        return "[%s] %s - %s".format(
            ScouterAlertLevel.getName(pack.level), AgentManager.getAgentName(pack.objHash), pack.title
        )
    }

    fun formatXlog(
        pack: XLogPack,
        thresholdMs: Int,
    ): MessageFormatter.AlertContent {
        val body =
            buildString {
                appendLine("🐢 [SLOW TX] 응답시간 초과 (임계치: ${thresholdMs}ms)")
                appendLine("시간    : ${MessageFormatter.ts(pack.endTime)}")
                appendLine("서비스  : ${pack.service}")
                appendLine("응답시간: ${pack.elapsed}ms")
                appendLine("대상    : ${AgentManager.getAgentName(pack.objHash)}")
            }

        return AlertContent(
            subject = "${MessageFormatter.PREFIX}[SLOW TX] ${AgentManager.getAgentName(pack.objHash)} - ${pack.service}",
            body = body,
        )
    }

    fun formatXlogSubject(pack: XLogPack): String = "${MessageFormatter.PREFIX}[SLOW TX] ${AgentManager.getAgentName(pack.objHash)} - ${pack.service}"

    fun formatXlogError(event: XlogErrorEvent): MessageFormatter.AlertContent {
        val body =
            buildString {
                appendLine("🔴 Scouter Xlog Error")
                appendLine("시간    : ${MessageFormatter.ts(event.endTime)}")
                appendLine("대상    : ${event.objName}")
                appendLine("서비스  : ${event.service}")
                appendLine("에러    : ${event.errorMessage}")
                appendLine("응답시간 : ${event.elapsedMs}ms")
                appendLine("TXID   : ${event.txid}")
            }

        return AlertContent(
            subject = "${MessageFormatter.PREFIX}[🔴 Scouter Xlog Error] ${event.objName} - ${event.service}",
            body = body,
        )
    }

    fun formatXlogErrorSubject(pack: XLogPack): String =
        "${MessageFormatter.PREFIX}[🔴 Scouter Error Transaction] ${AgentManager.getAgentName(pack.objHash)} - ${pack.service}"

}
