package scouter.plugin.server.alert.formatter

import scouter.lang.pack.AlertPack
import scouter.lang.pack.ObjectPack
import scouter.lang.pack.XLogPack
import scouter.plugin.server.alert.common.AlertLevel
import scouter.plugin.server.alert.monitoring.AlertEvent
import scouter.plugin.server.alert.monitoring.XlogErrorEvent

/**
 * Slack 채널 메시지 포맷.
 *
 * Slack Incoming Webhook payload 의 "text" 필드에 들어갈 mrkdwn 형식.
 *   - 헤더를 *bold* 로 강조
 *   - 각 필드를 `key: value` 목록으로 나열
 *   - SlackSender.buildPayload() 는 body 를 ```code block``` 으로 감쌈
 *
 * [확장 포인트]
 *   - Block Kit JSON 을 사용하려면 buildSlackBlocks(…) 메서드를 추가하고
 *     SlackSender.buildPayload() 에서 "blocks" 필드로 발송하면 됨.
 *     해당 변경은 SlackMessageFormat + SlackSender 만 수정하면 됨.
 */
class SlackMessageFormat : BaseMessageFormat() {
    override fun buildAlertBody(pack: AlertPack): String {
        val level = AlertLevel.of(pack.level.toInt())
        val emoji = level.emoji()
        return buildString {
            appendLine("$emoji *[$level] ${pack.title}*")
            appendLine("시간   : ${ts(pack.time)}")
            appendLine("대상   : ${agentName(pack.objHash)} (${pack.objType})")
            appendLine("메시지: ${pack.message}")
        }
    }

    override fun buildObjectBody(
        pack: ObjectPack,
        status: String,
    ): String =
        buildString {
            appendLine("🖥️ *[OBJECT $status]*")
            appendLine("시간    : ${ts(System.currentTimeMillis())}")
            appendLine("인스턴스: ${pack.objName}")
            appendLine("타입    : ${pack.objType}")
        }

    override fun buildCounterBody(event: AlertEvent): String {
        val emoji = event.level.emoji()
        return buildString {
            appendLine("$emoji *[${event.level}][Counter] ${event.metric}*")
            appendLine("시간   : ${ts(System.currentTimeMillis())}")
            appendLine("대상   : ${event.objName}")
            appendLine("지표   : ${event.metric}")
            appendLine("현재값 : ${"%.2f".format(event.value)}")
            appendLine("내용   : ${event.message}")
        }
    }

    override fun buildXlogErrorBody(event: XlogErrorEvent): String =
        buildString {
            appendLine("🔴 *Scouter Xlog Error*")
            appendLine("시간    : ${ts(event.endTime)}")
            appendLine("대상    : ${event.objName}")
            appendLine("서비스  : ${event.service}")
            appendLine("에러    : ${event.errorMessage}")
            appendLine("응답시간: ${event.elapsedMs}ms")
            appendLine("TXID   : ${event.txid}")
        }

    override fun buildXlogSlowBody(
        pack: XLogPack,
        thresholdMs: Int,
    ): String =
        buildString {
            appendLine("🐢 *[SLOW TX] 응답시간 초과 (임계치: ${thresholdMs}ms)*")
            appendLine("시간    : ${ts(pack.endTime)}")
            appendLine("서비스  : ${pack.service}")
            appendLine("응답시간: ${pack.elapsed}ms")
            appendLine("대상    : ${agentName(pack.objHash)}")
        }
}
