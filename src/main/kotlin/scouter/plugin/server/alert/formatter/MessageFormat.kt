package scouter.plugin.server.alert.formatter

import scouter.lang.pack.AlertPack
import scouter.lang.pack.ObjectPack
import scouter.lang.pack.XLogPack
import scouter.plugin.server.alert.monitoring.AlertEvent
import scouter.plugin.server.fingerpay.monitoring.XlogErrorEvent

/**
 * 채널별 메시지 포맷 전략 인터페이스.
 *
 * 각 채널(Slack / Telegram / Email)은 이 인터페이스를 구현한다.
 * ChannelDispatcher는 Channel enum → MessageFormat 구현체를 매핑해
 * 발송 직전에 포맷을 결정한다.
 *
 * [확장 방법]
 *   1. MessageFormat 구현체 추가 (e.g. WebhookMessageFormat)
 *   2. Channel enum에 값 추가
 *   3. MessageFormatRegistry.register() 호출 1줄 추가
 *   → MessageFormatter, ChannelDispatcher 수정 없음
 */
interface MessageFormat {

    /**
     * Alert 알림 포맷 (scouter 자체 알림)
     * @return FormattedMessage (subject + body)
     */
    fun formatAlert(pack: AlertPack): FormattedMessage

    /**
     * Object UP/DOWN/RECONNECT 상태 변경 포맷
     */
    fun formatObjectStatus(pack: ObjectPack, status: String): FormattedMessage

    /**
     * Counter 임계치 초과 이벤트 포맷
     */
    fun formatCounterAlert(event: AlertEvent): FormattedMessage

    /**
     * XLog 에러 트랜잭션 포맷
     */
    fun formatXlogError(event: XlogErrorEvent): FormattedMessage

    /**
     * XLog Slow TX 포맷
     */
    fun formatXlogSlow(pack: XLogPack, thresholdMs: Int): FormattedMessage
}

/**
 * 메시지 포맷 결과 DTO.
 * - subject : 이메일 제목 / Slack 첫 줄 / Telegram 첫 줄 등 채널별 활용
 * - body    : 본문 (plain text / markdown / HTML 등 채널 구현체가 결정)
 */
data class FormattedMessage(
    val subject: String,
    val body: String,
)
