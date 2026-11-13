package com.witherboymcw.superbattery.screens.dashboard

import androidx.lifecycle.ViewModel
import com.witherboymcw.superbattery.HistoryGraphView
import com.witherboymcw.superbattery.MetricHistoryManager

class DashboardViewModel(
    val metricHistoryManager: MetricHistoryManager = MetricHistoryManager()
) : ViewModel() {

    val voltageHistory: ArrayDeque<HistoryGraphView.Sample>
        get() = metricHistoryManager.voltageHistory

    val ramHistory: ArrayDeque<HistoryGraphView.Sample>
        get() = metricHistoryManager.ramHistory

    val temperatureHistory: ArrayDeque<HistoryGraphView.Sample>
        get() = metricHistoryManager.temperatureHistory

    var isVoltageShiftedHigh: Boolean = false
        private set

    fun addVoltageSample(voltageMv: Float, timestampMs: Long) {
        metricHistoryManager.addVoltageSample(voltageMv, timestampMs)
        updateVoltageShiftState()
    }

    fun addTemperatureSample(temperatureC: Float, timestampMs: Long) {
        metricHistoryManager.addTemperatureSample(temperatureC, timestampMs)
    }

    fun addRamSample(usedMb: Float, timestampMs: Long) {
        metricHistoryManager.addRamSample(usedMb, timestampMs)
    }

    fun updateVoltageShiftState() {
        val hasPointAbove4250 = voltageHistory.any { it.value > 4250f }
        val hasPointAbove4000 = voltageHistory.any { it.value > 4000f }

        if (!isVoltageShiftedHigh && hasPointAbove4250) {
            isVoltageShiftedHigh = true
        } else if (isVoltageShiftedHigh && !hasPointAbove4000) {
            isVoltageShiftedHigh = false
        }
    }

    fun getVoltageBoundsMv(): Pair<Float, Float> {
        updateVoltageShiftState()
        return if (isVoltageShiftedHigh) {
            Pair(3550f, 4550f)
        } else {
            Pair(3300f, 4300f)
        }
    }
}
