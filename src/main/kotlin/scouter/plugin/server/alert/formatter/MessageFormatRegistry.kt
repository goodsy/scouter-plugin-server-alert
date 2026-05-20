package scouter.plugin.server.alert.formatter

import scouter.plugin.server.alert.common.Channel
import scouter.plugin.server.alert.uitl.LogUtil

/**
 * Channel → MessageFormat 구현체 매핑 레지스트리.
 *
 * [채널 추가]
 *   - 새 채널 추가 시 이 파일에 register() 추가
 *   - Dispatcher / Sender 는 수정 불필요
 *
 * [채널 등록]
 *   SLACK    → SlackMessageFormat   (mrkdwn 블록 포맷)
 *   TELEGRAM → TelegramMessageFormat (plain text, 4096자 제한)
 *   EMAIL    → EmailMessageFormat    (HTML body)
 */
object MessageFormatRegistry {
    /** Channel.name(대문자) → MessageFormat 구현체 */
    private val registry = mutableMapOf<String, MessageFormat>()

    init {
        register(Channel.SLACK, SlackMessageFormat())
        register(Channel.TELEGRAM, TelegramMessageFormat())
        register(Channel.EMAIL, EmailMessageFormat())
    }

    fun register(
        channel: Channel,
        format: MessageFormat,
    ) {
        registry[channel.name] = format
        LogUtil.info(this.javaClass, "MessageFormat 등록: ${channel.name} → ${format::class.simpleName}")
    }

    /**
     * 채널에 해당하는 포맷터를 반환.
     * 미등록 채널은 SlackMessageFormat(plain text)을 fallback으로 반환.
     */
    fun resolve(channel: Channel): MessageFormat {
        return registry[channel.name]
            ?: run {
                LogUtil.error(this.javaClass, "포맷터 미등록: ${channel.name} → PlainText fallback 사용")
                SlackMessageFormat()
            }
    }
}
