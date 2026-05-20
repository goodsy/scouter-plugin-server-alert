package scouter.plugin.server.alert.sender

import scouter.plugin.server.alert.uitl.LogUtil

class TelegramSender(private val token: String?, private val chatId: String?) : HttpMessageSender() {
    override fun isConfigured(): Boolean {
        return !token.isNullOrBlank() && !chatId.isNullOrBlank()
    }

    override fun send(message: String) {
        LogUtil.info(this.javaClass, "send [chatId=$chatId]")
        if (!isConfigured()) {
            LogUtil.error(this.javaClass, "chatId, token 미설정")
            return
        }
        try {
            val truncated = if (message.length > 4096) message.substring(0, 4090) + "..." else message
            post(API_URL.format(token), buildPayload(truncated))
            LogUtil.info(this.javaClass, "전송 완료 [chatId=$chatId]")
        } catch (e: Exception) {
            LogUtil.error(this.javaClass, "전송 실패 [chatId=$chatId]", e)
        }
    }

    private fun buildPayload(text: String): String {
        return "{\"chat_id\":\"$chatId\",\"text\":\"${text.jsonEscape()}\"}"
    }

    private fun post(
        endpoint: String,
        payload: String,
    ) {
        super.post(endpoint, payload, TIMEOUT_MS)
    }

    companion object {
        private const val API_URL = "https://api.telegram.org/bot%s/sendMessage"
        private const val TIMEOUT_MS = 5_000
    }
}
