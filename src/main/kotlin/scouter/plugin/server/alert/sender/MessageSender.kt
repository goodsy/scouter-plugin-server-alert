package scouter.plugin.server.alert.sender

import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

interface MessageSender {
    fun send(message: String)
    fun isConfigured(): Boolean
}

abstract class HttpMessageSender : MessageSender {
    protected fun post(endpoint: String, payload: String, timeoutMs: Int = 5_000) {
        val conn = URL(endpoint).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.doOutput = true
        conn.outputStream.use { os ->
            os.write(payload.toByteArray(StandardCharsets.UTF_8))
        }
        val code = conn.responseCode
        if (code != 200) throw RuntimeException("HTTP $code")
    }

    protected fun String.jsonEscape(): String {
        return this.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")
    }
}
