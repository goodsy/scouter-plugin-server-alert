package scouter.plugin.server.fingerpay.monitoring

import scouter.plugin.server.alert.common.AlertLevel


data class XlogErrorEvent(
    val objName: String,
    val service: String,
    val errorMessage: String,
    val elapsedMs: Int = 0,
    val txid: Long = 0L,
    val clientIp: String?,
    val endTime: Long = 0L,
    val level: AlertLevel = AlertLevel.ERROR,
)
