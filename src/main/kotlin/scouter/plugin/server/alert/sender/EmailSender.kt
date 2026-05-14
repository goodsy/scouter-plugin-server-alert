package scouter.plugin.server.alert.sender

import scouter.plugin.server.alert.uitl.LogUtil

class EmailSender(
    private val userId: String?,
    private val password: String?,
    private val to: String?,
) : HttpMessageSender() {
    override fun isConfigured(): Boolean = !userId.isNullOrBlank() && !password.isNullOrBlank() && !to.isNullOrBlank()

    override fun send(message: String) {
        send("Scouter Alert", message)
    }

    fun send(
        subject: String,
        body: String,
    ) {
        if (!isConfigured()) {
            LogUtil.error(this.javaClass, "Email Api URL 미설정 - Skip")
            return
        }

        val receivers = to!!.split(",")
        for (receiver in receivers) {
            val trimmedReceiver = receiver.trim()
            if (trimmedReceiver.isEmpty()) continue

            try {
                post(API_URL, buildPayload(subject, body, trimmedReceiver))
            } catch (e: Exception) {
                LogUtil.error(this.javaClass, "전송 실패 → receiver=$trimmedReceiver", e)
            }
        }
    }

    private fun buildPayload(
        subject: String,
        body: String,
        receiver: String,
    ): String {
        val escaped = body.jsonEscape()

        return """
            {
                "ecare_no":"$escaped",
                "receiver_id":"$receiver",
                "receiver_nm":"$receiver",
                "receiver":"$receiver",
                "sender_nm":"$FROM_NAME",
                "sender":"$FROM_EMAIL",
                "subject":"$subject",
                "jonmun":"$escaped"
            }
            """.trimIndent().replace("\n", "").replace(" ", "")
    }

    private fun post(
        endpoint: String,
        payload: String,
    ) {
        super.post(endpoint, payload, TIMEOUT_MS)
    }

    companion object {
        private const val API_URL = "https://"
        private const val TIMEOUT_MS = 5_000
        private const val FROM_NAME = "form_name"
        private const val FROM_EMAIL = "from@gmail.com"
    }
}
