package scouter.plugin.server.alert.sender

import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets

interface MessageSender {
    fun send(message: String)

    fun isConfigured(): Boolean
}

abstract class HttpMessageSender : MessageSender {
    protected fun post(
        endpoint: String,
        payload: String,
        timeoutMs: Int = 5_000,
    ) {
        val conn = URI(endpoint).toURL().openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.doOutput = true
            conn.outputStream.use { os ->
                os.write(payload.toByteArray(StandardCharsets.UTF_8))
            }
            val code = conn.responseCode
            if (code != 200) {
                conn.errorStream?.use { it.readBytes() }
                throw RuntimeException("HTTP $code")
            }
            conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    protected fun String.jsonEscape(): String {
        return this.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")
    }
}
