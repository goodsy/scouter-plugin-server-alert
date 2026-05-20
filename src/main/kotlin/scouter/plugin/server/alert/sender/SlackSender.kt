package scouter.plugin.server.alert.sender

import scouter.plugin.server.alert.uitl.LogUtil

class SlackSender(private val channel: String?) : HttpMessageSender() {
    override fun isConfigured(): Boolean {
        return !channel.isNullOrBlank()
    }

    override fun send(message: String) {
        if (!isConfigured()) {
            LogUtil.error(this.javaClass, "webhook URL channel 미설정 - Skip")
            return
        }
        try {
            post("$API_URL/$channel", buildPayload(message))
        } catch (e: Exception) {
            LogUtil.error(this.javaClass, "전송 실패 [channel=$channel, message=$message]", e)
        }
    }

    private fun buildPayload(message: String): String {
        return "{\"text\":\"```${message.jsonEscape()}```\"}"
    }

    private fun post(
        endpoint: String,
        payload: String,
    ) {
        super.post(endpoint, payload, TIMEOUT_MS)
    }

    companion object {
        private const val API_URL = "https://hooks.slack.com/services"
        private const val TIMEOUT_MS = 5_000
    }
}
