package com.witherboymcw.superbattery

/**
 * Manages metric sample history buffers (voltage, RAM, temperature)
 * and object recycling via [samplePool].
 * Completely independent of Android UI lifecycle.
 */
class MetricHistoryManager(
    private val historyWindowMs: Long = HISTORY_WINDOW_MS,
    private val maxHistoryPoints: Int = MAX_HISTORY_POINTS
) {
    val voltageHistory = ArrayDeque<HistoryGraphView.Sample>()
    val ramHistory = ArrayDeque<HistoryGraphView.Sample>()
    val temperatureHistory = ArrayDeque<HistoryGraphView.Sample>()
    val samplePool = ArrayDeque<HistoryGraphView.Sample>()

    fun addVoltageSample(value: Float, timestampMs: Long) {
        addSample(voltageHistory, value, timestampMs)
    }

    fun addRamSample(value: Float, timestampMs: Long) {
        addSample(ramHistory, value, timestampMs)
    }

    fun addTemperatureSample(value: Float, timestampMs: Long) {
        addSample(temperatureHistory, value, timestampMs)
    }

    fun addSample(history: ArrayDeque<HistoryGraphView.Sample>, value: Float, timestampMs: Long) {
        val earliest = timestampMs - historyWindowMs
        while (history.isNotEmpty() && history.first().timestampMs < earliest) {
            samplePool.addLast(history.removeFirst())
        }
        while (history.size >= maxHistoryPoints) {
            samplePool.addLast(history.removeFirst())
        }
        val sample = if (samplePool.isNotEmpty()) {
            samplePool.removeLast().apply {
                this.timestampMs = timestampMs
                this.value = value
            }
        } else {
            HistoryGraphView.Sample(timestampMs, value)
        }
        history.addLast(sample)
    }

    fun clear() {
        samplePool.addAll(voltageHistory)
        voltageHistory.clear()
        samplePool.addAll(ramHistory)
        ramHistory.clear()
        samplePool.addAll(temperatureHistory)
        temperatureHistory.clear()
    }

    companion object {
        const val HISTORY_WINDOW_MS = 60_000L
        const val MAX_HISTORY_POINTS = 240
    }
}
