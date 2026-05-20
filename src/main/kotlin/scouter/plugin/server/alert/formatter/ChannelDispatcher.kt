package scouter.plugin.server.alert.formatter

import scouter.lang.pack.AlertPack
import scouter.lang.pack.ObjectPack
import scouter.lang.pack.XLogPack
import scouter.plugin.server.alert.common.AlertLevel
import scouter.plugin.server.alert.common.Channel
import scouter.plugin.server.alert.monitoring.AlertEvent
import scouter.plugin.server.alert.monitoring.ThresholdConfig
import scouter.plugin.server.alert.monitoring.XlogErrorEvent
import scouter.plugin.server.alert.sender.EmailSender
import scouter.plugin.server.alert.sender.SlackSender
import scouter.plugin.server.alert.sender.TelegramSender
import scouter.plugin.server.alert.uitl.LogUtil
import scouter.server.Configure

/**
 * 채널 라우팅 + 메시지 포맷 위임 디스패처.
 *
 * [변경 전]
 *   ChannelDispatcher 에 formatAlert(), formatCounterAlert() 등 직접 호출 →
 *   채널별 포맷 분기 없이 동일한 plain text 발송
 *
 * [변경 후]
 *   send*(…) 메서드에 원본 Pack/Event 를 전달 →
 *   발송 직전에 MessageFormatRegistry.resolve(channel) 로 채널별 포맷터 선택 →
 *   FormattedMessage(subject, body) 생성 후 각 Sender 로 전달
 *
 * [확장 방법]
 *   새 채널 추가: Channel enum + MessageFormat 구현체 + MessageFormatRegistry.register() 1줄
 *   ChannelDispatcher 수정 불필요
 */
class ChannelDispatcher {
    private val conf = Configure.getInstance()

    // ── Alert (scouter 자체 알림) ────────────────────────────────────────
    fun dispatchAlert(
        objName: String,
        pack: AlertPack,
        thresholdConfig: ThresholdConfig?,
        ignoreMinLevel: Boolean = false,
    ) {
        val level = AlertLevel.Companion.of(pack.level.toInt())
        routeAndSend(
            objName = objName,
            channelGroup = "all",
            level = level,
            thresholdConfig = thresholdConfig,
            ignoreMinLevel = ignoreMinLevel,
        ) { channel ->
            MessageFormatRegistry.resolve(channel).formatAlert(pack)
        }
    }

    // ── Object UP/DOWN ───────────────────────────────────────────────────
    fun dispatchObjectStatus(
        pack: ObjectPack,
        status: String,
        level: AlertLevel,
        thresholdConfig: ThresholdConfig?,
        ignoreMinLevel: Boolean = true,
    ) {
        routeAndSend(
            objName = pack.objName,
            channelGroup = "all",
            level = level,
            thresholdConfig = thresholdConfig,
            ignoreMinLevel = ignoreMinLevel,
        ) { channel ->
            MessageFormatRegistry.resolve(channel).formatObjectStatus(pack, status)
        }
    }

    // ── Counter 임계치 이벤트 ────────────────────────────────────────────
    fun dispatchCounterAlert(
        event: AlertEvent,
        thresholdConfig: ThresholdConfig?,
    ) {
        routeAndSend(
            objName = event.objName,
            channelGroup = event.channelGroup,
            level = event.level,
            thresholdConfig = thresholdConfig,
        ) { channel ->
            MessageFormatRegistry.resolve(channel).formatCounterAlert(event)
        }
    }

    // ── XLog Error ───────────────────────────────────────────────────────
    fun dispatchXlogError(
        objName: String,
        event: XlogErrorEvent,
        thresholdConfig: ThresholdConfig?,
    ) {
        routeAndSend(
            objName = objName,
            channelGroup = "dev-xlog",
            level = event.level,
            thresholdConfig = thresholdConfig,
        ) { channel ->
            MessageFormatRegistry.resolve(channel).formatXlogError(event)
        }
    }

    // ── XLog Slow TX ─────────────────────────────────────────────────────
    fun dispatchXlogSlow(
        objName: String,
        pack: XLogPack,
        thresholdMs: Int,
        thresholdConfig: ThresholdConfig?,
    ) {
        routeAndSend(
            objName = objName,
            channelGroup = "dev-xlog",
            level = AlertLevel.WARN,
            thresholdConfig = thresholdConfig,
        ) {
                channel ->
            MessageFormatRegistry.resolve(channel).formatXlogSlow(pack, thresholdMs)
        }
    }

    /**
     *   @param formatProvider 채널(Channel) 을 받아 FormattedMessage 를 생성
     *   채널별로 다른 포맷터 인스턴스를 사용하므로 채널 결정 후 호출.
     */
    private fun routeAndSend(
        objName: String,
        channelGroup: String,
        level: AlertLevel,
        thresholdConfig: ThresholdConfig?,
        ignoreMinLevel: Boolean = false,
        formatProvider: (Channel) -> FormattedMessage,
    ) {
        // 최소 레벨 필터
        val minLevel = conf.getInt("ext_plugin_scouter_alert_alert_min_level", scouter.lang.AlertLevel.ERROR.toInt())
        if (!ignoreMinLevel && level.value < minLevel) return

        // 채널 목록 결정
        val channelNames =
            thresholdConfig?.resolveChannels(channelGroup, level.name)
                ?: listOf(Channel.TELEGRAM.name, Channel.SLACK.name)

        if (channelNames.isEmpty()) {
            LogUtil.info(this.javaClass, "채널 매핑 없음 group=$channelGroup level=$level objName=$objName")
            return
        }

        for (chName in channelNames) {
            val channel = Channel.Companion.of(chName)
            if (channel == Channel.UNKNOWN) {
                LogUtil.error(this.javaClass, "알 수 없는 채널: $chName")
                continue
            }

            // 채널이 결정된 후 포맷 생성 (채널별 포맷터 적용)
            val formatted =
                try {
                    formatProvider(channel)
                } catch (e: Exception) {
                    LogUtil.error(this.javaClass, "포맷 생성 실패 channel=$chName", e)
                    continue
                }

            sendToChannel(channel, formatted)
        }
    }

    private fun sendToChannel(
        channel: Channel,
        formatted: FormattedMessage,
    ) {
        when (channel) {
            Channel.TELEGRAM -> sendTelegram(formatted.body)
            Channel.SLACK -> sendSlack(formatted.body)
            Channel.EMAIL -> sendEmail(formatted.subject, formatted.body)
            else -> LogUtil.error(this.javaClass, "sendToChannel: 처리 불가 채널 ${channel.name}")
        }
    }

    // ── Sender 위임 ──────────────────────────────────────────────────────
    private fun sendTelegram(body: String) {
        if (!conf.getBoolean("ext_plugin_scouter_alert_telegram_enabled", true)) return
        TelegramSender(
            conf.getValue("ext_plugin_scouter_alert_telegram_token", ""),
            conf.getValue("ext_plugin_scouter_alert_telegram_chat_id", ""),
        ).send(body)
    }

    private fun sendSlack(body: String) {
        if (!conf.getBoolean("ext_plugin_scouter_alert_slack_enabled", true)) return
        SlackSender(
            conf.getValue("ext_plugin_scouter_alert_slack_channel", ""),
        ).send(body)
    }

    private fun sendEmail(
        subject: String,
        body: String,
    ) {
        if (!conf.getBoolean("ext_plugin_scouter_alert_email_enabled", false)) return
        EmailSender(
            conf.getValue("ext_plugin_scouter_alert_email_user_id", ""),
            conf.getValue("ext_plugin_scouter_alert_email_password", ""),
            conf.getValue("ext_plugin_scouter_alert_email_to", ""),
        ).send(subject, body)
    }
}
