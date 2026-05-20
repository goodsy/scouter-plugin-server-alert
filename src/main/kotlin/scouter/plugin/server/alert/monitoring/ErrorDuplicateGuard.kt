package scouter.plugin.server.alert.monitoring

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

object ErrorDuplicateGuard {
    private const val SUPPRESS_MS = 60_000L
    private const val CLEANUP_INTERVAL_MS = 60_000L

    private val cache = ConcurrentHashMap<String, Long>()
    private val lastCleanup = AtomicLong(System.currentTimeMillis())

    fun shouldSend(
        objName: String,
        service: String,
        error: String,
    ): Boolean {
        val key = "$objName|$service|$error"
        val now = System.currentTimeMillis()

        val last = cache[key]
        if (last != null && now - last < SUPPRESS_MS) return false

        cache[key] = now
        cleanupIfNeeded(now)
        return true
    }

    private fun cleanupIfNeeded(now: Long) {
        val last = lastCleanup.get()
        if (now - last < CLEANUP_INTERVAL_MS) return
        if (!lastCleanup.compareAndSet(last, now)) return
        cache.entries.removeIf { now - it.value >= SUPPRESS_MS }
    }
}
