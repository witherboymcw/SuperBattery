package com.witherboymcw.superbattery.monitors

import android.content.Intent

/**
 * Data class representing current battery status snapshot.
 */
data class BatteryInfo(
    val voltageMv: Int = 0,
    val temperatureC: Float = 0f,
    val level: Int = -1,
    val scale: Int = -1,
    val status: Int = -1,
    val isCharging: Boolean = false,
    @Transient val rawIntent: Intent? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BatteryInfo) return false
        return voltageMv == other.voltageMv &&
                temperatureC == other.temperatureC &&
                level == other.level &&
                scale == other.scale &&
                status == other.status &&
                isCharging == other.isCharging
    }

    override fun hashCode(): Int {
        var result = voltageMv
        result = 31 * result + temperatureC.hashCode()
        result = 31 * result + level
        result = 31 * result + scale
        result = 31 * result + status
        result = 31 * result + isCharging.hashCode()
        return result
    }
}

/**
 * Data class representing current network speed snapshot.
 */
data class NetworkSpeed(
    val rxRate: Long = 0L,
    val txRate: Long = 0L,
    val totalRxBytes: Long = -1L,
    val totalTxBytes: Long = -1L,
    val isSupported: Boolean = true
)
