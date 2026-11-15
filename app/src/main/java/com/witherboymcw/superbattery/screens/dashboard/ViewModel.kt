package com.witherboymcw.superbattery.screens.dashboard

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import com.witherboymcw.superbattery.BatteryInfo
import com.witherboymcw.superbattery.HistoryGraphView
import com.witherboymcw.superbattery.MetricHistoryManager
import com.witherboymcw.superbattery.NetworkSpeed
import com.witherboymcw.superbattery.R
import com.witherboymcw.superbattery.RamUnit
import com.witherboymcw.superbattery.TempUnit
import com.witherboymcw.superbattery.VoltageUnit
import com.witherboymcw.superbattery.awareness.BatteryMath
import com.witherboymcw.superbattery.awareness.BatteryPowerInfo
import com.witherboymcw.superbattery.templates.MetricColors

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

    fun buildUiState(
        batteryInfo: BatteryInfo,
        powerInfo: BatteryPowerInfo?,
        networkSpeed: NetworkSpeed,
        totalMemBytes: Long,
        availMemBytes: Long,
        voltageUnit: VoltageUnit,
        ramUnit: RamUnit,
        tempUnit: TempUnit,
        isUseSystemBattery: Boolean,
        sampleTime: Long = SystemClock.elapsedRealtime()
    ): DashboardUiState {
        val voltageMv = batteryInfo.voltageMv
        val temperatureC = batteryInfo.temperatureC

        val smoothedVoltageMv = BatteryMath.getSmoothedVoltage(voltageMv, sampleTime)
        val batteryPercent = if (voltageMv > 0 || isUseSystemBattery) {
            BatteryMath.getBatteryPercentage(isUseSystemBattery, batteryInfo.level, batteryInfo.scale, smoothedVoltageMv)
        } else null

        val hasPowerInfo = powerInfo != null
        val powerWatts = powerInfo?.powerWatts ?: 0f
        val isCharging = powerInfo?.isCharging ?: false

        val signedWatts = if (hasPowerInfo) {
            if (isCharging) powerWatts else -powerWatts
        } else 0f
        val isWattsPositive = signedWatts > 0f

        val voltageTitleRes = if (isUseSystemBattery && isWattsPositive) R.string.charging_true else R.string.voltage_title

        if (voltageMv > 0) addVoltageSample(voltageMv.toFloat(), sampleTime)
        addTemperatureSample(temperatureC, sampleTime)

        val totalMb = totalMemBytes / (1024L * 1024L)
        val availableMb = availMemBytes / (1024L * 1024L)
        val usedMb = (totalMb - availableMb).coerceAtLeast(0L)
        addRamSample(usedMb.toFloat(), sampleTime)

        val statusRes = MetricColors.getTemperatureStatusRes(temperatureC)
        val (minVoltageMv, maxVoltageMv) = getVoltageBoundsMv()

        return DashboardUiState(
            batteryTitleRes = R.string.metric_battery,
            batteryPercent = batteryPercent,
            voltageMv = voltageMv,
            voltageTitleRes = voltageTitleRes,
            hasPowerInfo = hasPowerInfo,
            powerWatts = powerWatts,
            isCharging = isCharging,
            voltageSamples = voltageHistory,
            minVoltageMv = minVoltageMv,
            maxVoltageMv = maxVoltageMv,
            voltageUnit = voltageUnit,
            isUseSystemBattery = isUseSystemBattery,
            usedRamMb = usedMb,
            totalRamMb = totalMb,
            ramSamples = ramHistory,
            ramUnit = ramUnit,
            temperatureC = temperatureC,
            temperatureStatusRes = statusRes,
            temperatureSamples = temperatureHistory,
            tempUnit = tempUnit,
            isNetworkSupported = networkSpeed.isSupported,
            netTxTotalBytes = networkSpeed.totalTxBytes,
            netTxSpeedBps = networkSpeed.txRate,
            netRxTotalBytes = networkSpeed.totalRxBytes,
            netRxSpeedBps = networkSpeed.rxRate,
            sampleTime = sampleTime,
            batteryLevel = batteryInfo.level,
            batteryScale = batteryInfo.scale
        )
    }
}
