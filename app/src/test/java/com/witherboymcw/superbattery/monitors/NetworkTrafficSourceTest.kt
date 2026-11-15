package com.witherboymcw.superbattery.monitors

import android.net.TrafficStats
import android.os.SystemClock
import org.junit.Test
import org.junit.Assert.*
import org.mockito.kotlin.*
import org.mockito.Mockito

class NetworkTrafficSourceTest {

    @Test
    fun testUpdateNetworkSpeedThrottle250MsBoundaryAndRates() {
        Mockito.mockStatic(SystemClock::class.java).use { systemClockMock ->
            Mockito.mockStatic(TrafficStats::class.java).use { trafficStatsMock ->
                // Initial time: 1000L
                systemClockMock.`when`<Long> { SystemClock.elapsedRealtime() }.thenReturn(1000L)
                trafficStatsMock.`when`<Long> { TrafficStats.getTotalRxBytes() }.thenReturn(1000L)
                trafficStatsMock.`when`<Long> { TrafficStats.getTotalTxBytes() }.thenReturn(500L)

                val source = NetworkTrafficSource()

                // First call at 1000L - should calculate initial speed (rates 0)
                val speed1 = source.updateNetworkSpeed()
                assertEquals(1000L, speed1.totalRxBytes)
                assertEquals(500L, speed1.totalTxBytes)
                assertEquals(0L, speed1.rxRate)
                assertEquals(0L, speed1.txRate)

                // Second call at 1249L (delta = 249ms < 250ms) - should return cached lastSpeed (throttled)
                systemClockMock.`when`<Long> { SystemClock.elapsedRealtime() }.thenReturn(1249L)
                trafficStatsMock.`when`<Long> { TrafficStats.getTotalRxBytes() }.thenReturn(2000L)
                trafficStatsMock.`when`<Long> { TrafficStats.getTotalTxBytes() }.thenReturn(1000L)

                val speed2 = source.updateNetworkSpeed()
                assertEquals(speed1.totalRxBytes, speed2.totalRxBytes)
                assertEquals(speed1.totalTxBytes, speed2.totalTxBytes)
                // Verify TrafficStats.getTotalRxBytes was not called again
                trafficStatsMock.verify({ TrafficStats.getTotalRxBytes() }, times(1))

                // Third call at 1250L (delta = 250ms >= 250ms from previous accepted time 1000L) - should recalculate
                systemClockMock.`when`<Long> { SystemClock.elapsedRealtime() }.thenReturn(1250L)
                trafficStatsMock.`when`<Long> { TrafficStats.getTotalRxBytes() }.thenReturn(3500L)
                trafficStatsMock.`when`<Long> { TrafficStats.getTotalTxBytes() }.thenReturn(1750L)

                val speed3 = source.updateNetworkSpeed()
                assertEquals(3500L, speed3.totalRxBytes)
                assertEquals(1750L, speed3.totalTxBytes)
                // Elapsed time: (1250 - 1000) / 1000f = 0.25 seconds.
                // Rx delta: 3500 - 1000 = 2500 bytes. RxRate = 2500 / 0.25 = 10000 bytes/sec.
                // Tx delta: 1750 - 500 = 1250 bytes. TxRate = 1250 / 0.25 = 5000 bytes/sec.
                assertEquals(10000L, speed3.rxRate)
                assertEquals(5000L, speed3.txRate)
                trafficStatsMock.verify({ TrafficStats.getTotalRxBytes() }, times(2))
            }
        }
    }
}
