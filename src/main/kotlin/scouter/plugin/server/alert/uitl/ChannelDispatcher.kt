package scouter.plugin.server.alert.uitl

import scouter.lang.AlertLevel as ScouterAlertLevel
import scouter.plugin.server.alert.common.AlertLevel
import scouter.plugin.server.alert.common.Channel
import scouter.plugin.server.alert.monitoring.ThresholdConfig
import scouter.plugin.server.alert.sender.EmailSender
import scouter.plugin.server.alert.sender.SlackSender
import scouter.plugin.server.alert.sender.TelegramSender
import scouter.server.Configure

class ChannelDispatcher {

    private val conf = Configure.getInstance()

    fun dispatch(
        objName: String,
        level: Int,
        message: String,
        subject: String,
        thresholdConfig: ThresholdConfig?,
        ignoreMinLevel: Boolean = false
    ) {
        val levelName = AlertLevel.of(level).name
        //dispatch(objName, "all", levelName, message, subject, thresholdConfig, ignoreMinLevel)
    }

    fun dispatch(
        objName: String,
        channelGroup: String,
        levelName: String,
        message: String,
        subject: String,
        thresholdConfig: ThresholdConfig?,
        ignoreMinLevel: Boolean = false
    ) {
        val minLevel = conf.getInt("ext_plugin_scouter_alert_alert_min_level", ScouterAlertLevel.ERROR.toInt())
        val currentLevel = AlertLevel.of(levelName).value

        if (!ignoreMinLevel && currentLevel < minLevel) {
            return
        }

        val channels = thresholdConfig?.resolveChannels(channelGroup, levelName)
            ?: listOf(Channel.TELEGRAM.name, Channel.SLACK.name)

        if (channels.isEmpty()) {
            LogUtil.info(this.javaClass, "채널 매핑 없음 group=$channelGroup level=$levelName objName=$objName")
            return
        }

        sendToChannels(channels, message, subject)
    }


    private fun sendToChannels(channels: List<String>, message: String, emailSubject: String) {
        for (ch in channels) {
            when (Channel.of(ch)) {
                Channel.TELEGRAM -> sendTelegram(message)
                Channel.SLACK -> sendSlack(message)
                Channel.EMAIL -> sendEmail(emailSubject, message)
                else -> LogUtil.error(this.javaClass, "알 수 없는 채널 : $ch")
            }
        }
    }

    private fun sendTelegram(message: String) {
        if (!conf.getBoolean("ext_plugin_scouter_alert_telegram_enabled", true)) return

        TelegramSender(
            conf.getValue("ext_plugin_scouter_alert_telegram_token", ""),
            conf.getValue("ext_plugin_scouter_alert_telegram_chat_id", "")
        ).send(message)
    }

    private fun sendSlack(message: String) {
        if (!conf.getBoolean("ext_plugin_scouter_alert_slack_enabled", true)) return

        SlackSender(
            conf.getValue("ext_plugin_scouter_alert_slack_channel", "")
        ).send(message)
    }

    private fun sendEmail(subject: String, body: String) {
        if (!conf.getBoolean("ext_plugin_scouter_alert_email_enabled", false)) return

        EmailSender(
            conf.getValue("ext_plugin_scouter_alert_email_user_id", ""),
            conf.getValue("ext_plugin_scouter_alert_email_password", ""),
            conf.getValue("ext_plugin_scouter_alert_email_to", "")
        ).send(subject, body)
    }

}
