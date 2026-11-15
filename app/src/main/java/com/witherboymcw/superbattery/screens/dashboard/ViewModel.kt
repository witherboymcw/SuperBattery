package com.witherboymcw.superbattery.screens.dashboard

import android.content.Context
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
import com.witherboymcw.superbattery.screens.settings.SettingsHelper
import com.witherboymcw.superbattery.templates.MetricColors
import java.util.Locale

class DashboardViewModel(
    val metricHistoryManager: MetricHistoryManager = MetricHistoryManager()
) : ViewModel() {

    private val byteUnits = arrayOf("KB", "MB", "GB", "TB")

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
        context: Context,
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
        val battery = batteryInfo.rawIntent

        val smoothedVoltageMv = BatteryMath.getSmoothedVoltage(voltageMv, sampleTime)
        val batteryPercent = if (voltageMv > 0 || isUseSystemBattery) {
            BatteryMath.getBatteryPercentage(isUseSystemBattery, battery, smoothedVoltageMv)
        } else null

        val dash = context.getString(R.string.placeholder_dash)
        val batteryValueText = batteryPercent?.let { "$it%" } ?: dash
        val batteryColor = MetricColors.getBatteryColor(batteryPercent ?: 0)

        val voltageCurrentText = if (voltageMv > 0) {
            SettingsHelper.formatVoltage(voltageMv, voltageUnit)
        } else dash

        val temperatureCurrentText = SettingsHelper.formatDashboardTemperature(context, temperatureC)

        val voltagePowerText = powerInfo?.getCurrentPowerString(context) ?: dash

        val signedWatts = if (powerInfo != null) {
            if (powerInfo.isCharging) powerInfo.powerWatts else -powerInfo.powerWatts
        } else 0f
        val isWattsPositive = signedWatts > 0f

        val voltageTitleRes = if (isUseSystemBattery && isWattsPositive) R.string.charging_true else R.string.voltage_title

        if (voltageMv > 0) addVoltageSample(voltageMv.toFloat(), sampleTime)
        if (battery != null) addTemperatureSample(temperatureC, sampleTime)

        val totalMb = totalMemBytes / (1024L * 1024L)
        val availableMb = availMemBytes / (1024L * 1024L)
        val usedMb = (totalMb - availableMb).coerceAtLeast(0L)
        val usedPercent = if (totalMb > 0) (usedMb * 100 / totalMb).toInt() else 0
        addRamSample(usedMb.toFloat(), sampleTime)

        val ramCurrentPercentText = context.getString(R.string.current_ram_format, usedPercent)

        val locale = SettingsHelper.getAppLocale(context)
        val ramValConverted = SettingsHelper.convertRam(usedMb.toFloat(), ramUnit)
        val ramSuffixStr = context.getString(if (ramUnit == RamUnit.GB) R.string.unit_gb else R.string.unit_mb)
        val ramUsageDetailText = if (ramUnit == RamUnit.GB) {
            String.format(locale, "%.1f %s", ramValConverted, ramSuffixStr)
        } else {
            String.format(locale, "%.0f %s", ramValConverted, ramSuffixStr)
        }

        val statusRes = MetricColors.getTemperatureStatusRes(temperatureC)

        val (minVoltageMv, maxVoltageMv) = getVoltageBoundsMv()
        val (minVoltage, maxVoltage, voltageSuffix) = when (voltageUnit) {
            VoltageUnit.MV -> Triple(minVoltageMv, maxVoltageMv, " " + context.getString(R.string.unit_mv))
            VoltageUnit.V -> Triple(minVoltageMv / 1000f, maxVoltageMv / 1000f, " " + context.getString(R.string.unit_v))
        }
        val voltageDecimalPlaces = if (voltageUnit == VoltageUnit.V) 2 else 0

        val maxRam = SettingsHelper.convertRam(totalMb.toFloat(), ramUnit).coerceAtLeast(0.1f)
        val ramSuffix = " " + context.getString(if (ramUnit == RamUnit.GB) R.string.unit_gb else R.string.unit_mb)
        val ramDecimalPlaces = if (ramUnit == RamUnit.GB) 1 else 0

        val (minTemp, maxTemp, tempSuffix) = when (tempUnit) {
            TempUnit.CELSIUS -> Triple(0f, 60f, context.getString(R.string.unit_celsius))
            TempUnit.FAHRENHEIT -> Triple(32f, 140f, context.getString(R.string.unit_fahrenheit))
            TempUnit.KELVIN -> Triple(273f, 333f, " " + context.getString(R.string.unit_kelvin))
        }

        val (netTxTotalText, netTxSpeedText, netRxTotalText, netRxSpeedText) = if (!networkSpeed.isSupported) {
            val unavail = context.getString(R.string.network_unavailable)
            Tuple4(unavail, "—", unavail, "—")
        } else {
            Tuple4(
                formatBytes(networkSpeed.totalTxBytes),
                formatBytes(networkSpeed.txRate) + "/s",
                formatBytes(networkSpeed.totalRxBytes),
                formatBytes(networkSpeed.rxRate) + "/s"
            )
        }

        return DashboardUiState(
            batteryTitleRes = R.string.metric_battery,
            batteryValueText = batteryValueText,
            batteryPercent = batteryPercent ?: 0,
            batteryColor = batteryColor,
            voltageTitleRes = voltageTitleRes,
            voltageCurrentText = voltageCurrentText,
            voltagePowerText = voltagePowerText,
            voltageSamples = voltageHistory,
            minVoltage = minVoltage,
            maxVoltage = maxVoltage,
            voltageSuffix = voltageSuffix,
            voltageDecimalPlaces = voltageDecimalPlaces,
            voltageUnit = voltageUnit,
            isUseSystemBattery = isUseSystemBattery,
            ramCurrentPercentText = ramCurrentPercentText,
            ramUsageDetailText = ramUsageDetailText,
            ramSamples = ramHistory,
            maxRam = maxRam,
            ramSuffix = ramSuffix,
            ramDecimalPlaces = ramDecimalPlaces,
            ramUnit = ramUnit,
            totalMb = totalMb,
            temperatureCurrentText = temperatureCurrentText,
            temperatureStatusRes = statusRes,
            temperatureSamples = temperatureHistory,
            minTemp = minTemp,
            maxTemp = maxTemp,
            tempSuffix = tempSuffix,
            tempUnit = tempUnit,
            isNetworkSupported = networkSpeed.isSupported,
            netTxTotalText = netTxTotalText,
            netTxSpeedText = netTxSpeedText,
            netRxTotalText = netRxTotalText,
            netRxSpeedText = netRxSpeedText,
            sampleTime = sampleTime
        )
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        var value = bytes.toDouble()
        var unit = -1
        while (value >= 1024 && unit < byteUnits.lastIndex) {
            value /= 1024
            unit++
        }
        return String.format(Locale.getDefault(), "%.1f %s", value, byteUnits[unit])
    }

    private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
