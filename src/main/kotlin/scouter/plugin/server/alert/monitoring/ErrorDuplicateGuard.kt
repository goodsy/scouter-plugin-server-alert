package scouter.plugin.server.alert.monitoring

import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.set

object ErrorDuplicateGuard {
    private const val SUPPRESS_MS = 60_000L

    private val cache = ConcurrentHashMap<String, Long>()

    fun shouldSend(
        objName: String,
        service: String,
        error: String,
    ): Boolean {
        val key = "$objName|$service|$error"

        val now = System.currentTimeMillis()

        val last = cache[key]

        if (last != null && now - last < SUPPRESS_MS) {
            return false
        }

        cache[key] = now

        return true
    }
}
