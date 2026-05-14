package scouter.plugin.server.alert.monitoring

import scouter.plugin.server.alert.common.AlertLevel

/**
 * CounterMonitor에서 생성된 임계치 초과 이벤트 DTO.
 * ScouterAlertPlugin ↔ ChannelDispatcher 간 데이터 전달용.
 */
data class AlertEvent(
    val objName: String,
    val metric: String,
    val value: Double,
    val level: AlertLevel,
    val message: String,
    val channelGroup: String
) {
    fun levelInt(): Int = level.value
}
