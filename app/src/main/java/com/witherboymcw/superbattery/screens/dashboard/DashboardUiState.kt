package com.witherboymcw.superbattery.screens.dashboard

import com.witherboymcw.superbattery.HistoryGraphView
import com.witherboymcw.superbattery.R
import com.witherboymcw.superbattery.RamUnit
import com.witherboymcw.superbattery.TempUnit
import com.witherboymcw.superbattery.VoltageUnit

/**
 * Immutable UI State representing formatted data for the main dashboard screen.
 */
data class DashboardUiState(
    // Battery
    val batteryTitleRes: Int = R.string.metric_battery,
    val batteryValueText: String = "—",
    val batteryPercent: Int = 0,
    val batteryColor: Int = 0,

    // Voltage
    val voltageTitleRes: Int = R.string.voltage_title,
    val voltageCurrentText: String = "—",
    val voltagePowerText: String = "—",
    val voltageSamples: Collection<HistoryGraphView.Sample> = emptyList(),
    val minVoltage: Float = 0f,
    val maxVoltage: Float = 0f,
    val voltageSuffix: String = "",
    val voltageDecimalPlaces: Int = 0,
    val voltageUnit: VoltageUnit = VoltageUnit.MV,
    val isUseSystemBattery: Boolean = false,

    // RAM
    val ramCurrentPercentText: String = "—",
    val ramUsageDetailText: String = "—",
    val ramSamples: Collection<HistoryGraphView.Sample> = emptyList(),
    val maxRam: Float = 0f,
    val ramSuffix: String = "",
    val ramDecimalPlaces: Int = 0,
    val ramUnit: RamUnit = RamUnit.MB,
    val totalMb: Long = 0L,

    // Temperature
    val temperatureCurrentText: String = "—",
    val temperatureStatusRes: Int = R.string.temp_status_nominal,
    val temperatureSamples: Collection<HistoryGraphView.Sample> = emptyList(),
    val minTemp: Float = 0f,
    val maxTemp: Float = 0f,
    val tempSuffix: String = "",
    val tempUnit: TempUnit = TempUnit.CELSIUS,

    // Network Speed
    val isNetworkSupported: Boolean = true,
    val netTxTotalText: String = "—",
    val netTxSpeedText: String = "—",
    val netRxTotalText: String = "—",
    val netRxSpeedText: String = "—",

    // Metadata
    val sampleTime: Long = 0L
)
