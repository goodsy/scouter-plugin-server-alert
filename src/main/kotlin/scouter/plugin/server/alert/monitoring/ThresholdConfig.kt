package scouter.plugin.server.alert.monitoring

/**
 * metric-thresholds.json 루트 모델
 *
 * thresholdMap 키: metric 단독 (동일 metric은 1개 등록 원칙)
 * 복수 그룹은 MetricThreshold.channelGroups 배열로 처리
 *
 * ErrorRate처럼 그룹별 임계치가 다른 경우만 예외적으로 2개 등록:
 *   → 키: "ErrorRate:OPS", "ErrorRate:DEV" (channelGroups 단일 배열)
 */
class ThresholdConfig {
    var enabled: Boolean = true

    /** Map<그룹명, Map<레벨명, List<채널명>>> */
    var channelGroups: Map<String, Map<String, List<String>>>? = null

    var thresholds: List<MetricThreshold>? = null

    // 키: "METRIC" 또는 "METRIC:GROUP" (그룹별 임계치 다를 때)
    private var thresholdMap: Map<String, MetricThreshold>? = null

    fun init() {
        thresholdMap =
            thresholds?.filter { it.metric != null }
                ?.associateBy { toKey(it) }
    }

    /**
     * 해당 metric의 모든 threshold 반환
     * (동일 metric 복수 등록 케이스 포함)
     */
    fun getAll(metricName: String?): List<MetricThreshold> {
        if (thresholds == null || metricName == null) return emptyList()

        return thresholds!!.filter { metricName.equals(it.metric, ignoreCase = true) && it.enabled }
    }

    fun resolveChannels(
        groupName: String?,
        levelName: String?,
    ): List<String> {
        if (channelGroups == null || groupName == null || levelName == null) return emptyList()

        val group = channelGroups!![groupName] ?: return emptyList()
        return group[levelName.uppercase()] ?: emptyList()
    }

    fun isEnabled(): Boolean {
        return enabled && !thresholds.isNullOrEmpty()
    }

    private fun toKey(t: MetricThreshold): String {
        // channelGroups 단일 + 임계치 다를 때: "METRIC:GROUP" 복합키
        if (t.channelGroups.size == 1) {
            return "${t.metric?.uppercase()}:${t.channelGroups[0].uppercase()}"
        }
        return t.metric?.uppercase() ?: ""
    }
}
