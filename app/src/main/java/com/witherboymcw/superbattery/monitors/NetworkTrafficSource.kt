package com.witherboymcw.superbattery.monitors

import android.net.TrafficStats
import android.os.SystemClock
import com.witherboymcw.superbattery.NetworkSpeed

/**
 * Source module responsible for measuring network bytes via TrafficStats and calculating upload/download rates.
 */
class NetworkTrafficSource {

    private var previousRx = -1L
    private var previousTx = -1L
    private var previousNetworkTime = 0L
    private var lastNonZeroRxRate: Long? = null
    private var lastNonZeroTxRate: Long? = null

    fun resetNetworkStats() {
        previousRx = -1L
        previousTx = -1L
        previousNetworkTime = 0L
        lastNonZeroRxRate = null
        lastNonZeroTxRate = null
    }

    fun updateNetworkSpeed(): NetworkSpeed {
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        val now = SystemClock.elapsedRealtime()

        if (rx == TrafficStats.UNSUPPORTED.toLong() || tx == TrafficStats.UNSUPPORTED.toLong()) {
            return NetworkSpeed(rxRate = 0L, txRate = 0L, totalRxBytes = rx, totalTxBytes = tx, isSupported = false)
        }

        val elapsedSeconds = if (previousNetworkTime > 0L) {
            ((now - previousNetworkTime).coerceAtLeast(1L)) / 1000f
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

        return NetworkSpeed(
            rxRate = displayedRxRate,
            txRate = displayedTxRate,
            totalRxBytes = rx,
            totalTxBytes = tx,
            isSupported = true
        )
    }
}
