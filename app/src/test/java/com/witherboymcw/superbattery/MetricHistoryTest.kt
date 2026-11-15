package com.witherboymcw.superbattery

import org.junit.Test
import org.junit.Assert.*

class MetricHistoryTest {
    @Test
    fun testMetricHistoryManagerPruningAndPoolReuse() {
        val manager = MetricHistoryManager(historyWindowMs = 60_000L, maxHistoryPoints = 5)
        val startTime = 100_000L

        // Add 5 samples
        for (i in 0 until 5) {
            manager.addVoltageSample(3800f + i, startTime + i * 1_000L)
        }

        assertEquals(5, manager.voltageHistory.size)
        assertEquals(0, manager.samplePool.size)

        // Adding 6th sample should trigger MAX_HISTORY_POINTS pruning -> 1 sample recycled to pool
        manager.addVoltageSample(4000f, startTime + 5_000L)
        assertEquals(5, manager.voltageHistory.size)
        assertEquals(0, manager.samplePool.size) // Recycled object consumed immediately

        // Add sample with time jump > 60s to prune old samples
        manager.addVoltageSample(4100f, startTime + 70_000L)
        // All previous samples (100s to 105s) are older than 70s - 60s = 10s.
        // Thus 5 old samples are moved to pool, 1 sample is reused from pool for 4100f.
        assertEquals(1, manager.voltageHistory.size)
        assertEquals(4100f, manager.voltageHistory.first().value, 0.001f)
        assertEquals(4, manager.samplePool.size)
    }
}
