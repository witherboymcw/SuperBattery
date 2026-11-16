package com.witherboymcw.superbattery.monitors

import android.net.TrafficStats
import android.os.SystemClock
import com.witherboymcw.superbattery.monitors.NetworkSpeed

/**
 * Source module responsible for measuring network bytes via TrafficStats and calculating upload/download rates.
 */
class NetworkTrafficSource {

    private var previousRx = -1L
    private var previousTx = -1L
    private var previousNetworkTime = 0L
    private var lastNonZeroRxRate: Long? = null
    private var lastNonZeroTxRate: Long? = null
    private var lastSpeed: NetworkSpeed = NetworkSpeed(
        rxRate = 0L,
        txRate = 0L,
        totalRxBytes = -1L,
        totalTxBytes = -1L,
        isSupported = true
    )

    fun resetNetworkStats() {
        previousRx = -1L
        previousTx = -1L
        previousNetworkTime = 0L
        lastNonZeroRxRate = null
        lastNonZeroTxRate = null
        lastSpeed = NetworkSpeed(
            rxRate = 0L,
            txRate = 0L,
            totalRxBytes = -1L,
            totalTxBytes = -1L,
            isSupported = true
        )
    }

    fun updateNetworkSpeed(): NetworkSpeed {
        val now = SystemClock.elapsedRealtime()

        if (previousNetworkTime > 0L && (now - previousNetworkTime) < 250L) {
            return lastSpeed
        }

        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()

        if (rx == TrafficStats.UNSUPPORTED.toLong() || tx == TrafficStats.UNSUPPORTED.toLong()) {
            val unsupportedSpeed = NetworkSpeed(rxRate = 0L, txRate = 0L, totalRxBytes = rx, totalTxBytes = tx, isSupported = false)
            lastSpeed = unsupportedSpeed
            return unsupportedSpeed
        }

        val elapsedSeconds = if (previousNetworkTime > 0L) {
            (now - previousNetworkTime) / 1000f
        } else 1f

        val rxRate = if (previousRx >= 0 && rx >= previousRx) ((rx - previousRx) / elapsedSeconds).toLong() else 0L
        val txRate = if (previousTx >= 0 && tx >= previousTx) ((tx - previousTx) / elapsedSeconds).toLong() else 0L

        previousRx = rx
        previousTx = tx
        previousNetworkTime = now

        val displayedTxRate = if (txRate > 0L) {
            lastNonZeroTxRate = txRate
            txRate
        } else {
            lastNonZeroTxRate ?: 0L
        }

        val displayedRxRate = if (rxRate > 0L) {
            lastNonZeroRxRate = rxRate
            rxRate
        } else {
            lastNonZeroRxRate ?: 0L
        }

        val speed = NetworkSpeed(
            rxRate = displayedRxRate,
            txRate = displayedTxRate,
            totalRxBytes = rx,
            totalTxBytes = tx,
            isSupported = true
        )
        lastSpeed = speed
        return speed
    }
}
