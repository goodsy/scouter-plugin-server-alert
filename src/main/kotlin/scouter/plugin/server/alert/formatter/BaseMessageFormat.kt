package scouter.plugin.server.alert.formatter

import scouter.lang.pack.AlertPack
import scouter.lang.pack.ObjectPack
import scouter.lang.pack.XLogPack
import scouter.plugin.server.alert.common.AlertLevel
import scouter.plugin.server.alert.monitoring.AlertEvent
import scouter.plugin.server.alert.monitoring.XlogErrorEvent
import scouter.server.core.AgentManager
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import scouter.lang.AlertLevel as ScouterAlertLevel

/**
 * 모든 채널 포맷터가 공유하는 공통 유틸.
 * - 타임스탬프 변환
 * - emoji 매핑
 * - prefix 상수
 *
 * 각 채널 구현체는 이 클래스를 상속하고
 * buildBody*(…) 메서드만 오버라이드한다.
 */
abstract class BaseMessageFormat : MessageFormat {
    protected val prefixSubject = "[Scouter]"
    private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    protected fun ts(epochMs: Long): String =
        LocalDateTime.ofInstant(
            Instant.ofEpochMilli(epochMs),
            ZoneId.of("Asia/Seoul"),
        ).format(timeFormatter)

    protected fun AlertLevel.emoji(): String =
        when (this) {
            AlertLevel.FATAL -> "🚨"
            AlertLevel.ERROR -> "🔴"
            AlertLevel.WARN -> "⚠️"
            AlertLevel.INFO -> "✅"
            else -> "❓"
        }

    protected fun agentName(objHash: Int): String = AgentManager.getAgentName(objHash) ?: "unknown"

    // ── subject (채널 공통 - 오버라이드 가능) ──────────────
    protected open fun alertSubject(pack: AlertPack): String {
        val level = AlertLevel.of(pack.level.toInt())
        return "$prefixSubject[${ScouterAlertLevel.getName(pack.level)}] ${agentName(pack.objHash)} - ${pack.title}"
    }

    protected open fun objectSubject(
        pack: ObjectPack,
        status: String,
    ): String = "$prefixSubject[OBJECT $status] ${pack.objName}"

    protected open fun counterSubject(event: AlertEvent): String =
        "$prefixSubject[Counter][${event.level}] ${event.objName} - ${event.metric}"

    protected open fun xlogErrorSubject(event: XlogErrorEvent): String = "$prefixSubject[🔴 Xlog Error] ${event.objName} - ${event.service}"

    protected open fun xlogSlowSubject(
        pack: XLogPack,
        thresholdMs: Int,
    ): String = "$prefixSubject[SLOW TX] ${agentName(pack.objHash)} - ${pack.service}"

    // ── body 구현은 각 채널 구현체 담당 ────────────────────
    protected abstract fun buildAlertBody(pack: AlertPack): String

    protected abstract fun buildObjectBody(
        pack: ObjectPack,
        status: String,
    ): String

    protected abstract fun buildCounterBody(event: AlertEvent): String

    protected abstract fun buildXlogErrorBody(event: XlogErrorEvent): String

    protected abstract fun buildXlogSlowBody(
        pack: XLogPack,
        thresholdMs: Int,
    ): String

    // ── MessageFormat 인터페이스 위임 ───────────────────────
    override fun formatAlert(pack: AlertPack) = FormattedMessage(alertSubject(pack), buildAlertBody(pack))

    override fun formatObjectStatus(
        pack: ObjectPack,
        status: String,
    ) = FormattedMessage(objectSubject(pack, status), buildObjectBody(pack, status))

    override fun formatCounterAlert(event: AlertEvent) = FormattedMessage(counterSubject(event), buildCounterBody(event))

    override fun formatXlogError(event: XlogErrorEvent) = FormattedMessage(xlogErrorSubject(event), buildXlogErrorBody(event))

    override fun formatXlogSlow(
        pack: XLogPack,
        thresholdMs: Int,
    ) = FormattedMessage(xlogSlowSubject(pack, thresholdMs), buildXlogSlowBody(pack, thresholdMs))
}
