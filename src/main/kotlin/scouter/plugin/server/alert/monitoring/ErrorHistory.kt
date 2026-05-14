package scouter.plugin.server.alert.monitoring

import java.util.ArrayList

/**
 * 에러율 이력 관리. 최근 10분 간의 에러율 데이터를 보관하며
 * 특정 시점 이전의 값을 탐색할 수 있다.
 *
 * Thread-safe (synchronized 메서드)
 */
class ErrorHistory {
    private val rates = ArrayList<Double>()
    private val times = ArrayList<Long>()

    constructor()

    constructor(initialRate: Double, time: Long) {
        add(initialRate, time)
    }

    @Synchronized
    fun add(rate: Double, time: Long) {
        rates.add(rate)
        times.add(time)

        // 10분 이상 된 데이터 삭제
        val cutoff = time - 600_000
        while (times.isNotEmpty() && times[0] < cutoff) {
            rates.removeAt(0)
            times.removeAt(0)
        }
    }

    @Synchronized
    fun hasDataBefore(targetTime: Long): Boolean {
        return times.any { it <= targetTime }
    }

    /**
     * targetTime 이전에 기록된 값 중 가장 가까운 값을 반환한다.
     * 기록이 없으면 0.0 을 반환한다.
     */
    @Synchronized
    fun getRateBefore(targetTime: Long): Double {
        if (times.isEmpty()) return 0.0
        var bestIdx = -1

        var minDiff = Long.MAX_VALUE
        for (i in times.indices) {
            val time = times[i]

            if (time > targetTime) continue
            val diff = targetTime - time

            if (diff < minDiff) {
                minDiff = diff
                bestIdx = i
            }
        }

        return if (bestIdx >= 0) rates[bestIdx] else 0.0
    }
}
