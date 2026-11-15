package com.witherboymcw.superbattery.screens.dashboard

import com.witherboymcw.superbattery.HistoryGraphView
import com.witherboymcw.superbattery.R
import com.witherboymcw.superbattery.RamUnit
import com.witherboymcw.superbattery.TempUnit
import com.witherboymcw.superbattery.VoltageUnit

/**
 * Immutable UI State representing raw data for the main dashboard screen.
 */
data class DashboardUiState(
    // Battery
    val batteryTitleRes: Int = R.string.metric_battery,
    val batteryPercent: Int? = null,
    val voltageMv: Int = 0,
    val voltageTitleRes: Int = R.string.voltage_title,
    val hasPowerInfo: Boolean = false,
    val powerWatts: Float = 0f,
    val isCharging: Boolean = false,

    // Voltage Graph
    val voltageSamples: Collection<HistoryGraphView.Sample> = emptyList(),
    val minVoltageMv: Float = 0f,
    val maxVoltageMv: Float = 0f,
    val voltageUnit: VoltageUnit = VoltageUnit.MV,
    val isUseSystemBattery: Boolean = false,

    // RAM
    val usedRamMb: Long = 0L,
    val totalRamMb: Long = 0L,
    val ramSamples: Collection<HistoryGraphView.Sample> = emptyList(),
    val ramUnit: RamUnit = RamUnit.MB,

    // Temperature
    val temperatureC: Float = 0f,
    val temperatureStatusRes: Int = R.string.temp_status_nominal,
    val temperatureSamples: Collection<HistoryGraphView.Sample> = emptyList(),
    val tempUnit: TempUnit = TempUnit.CELSIUS,

    // Network Speed
    val isNetworkSupported: Boolean = true,
    val netTxTotalBytes: Long = 0L,
    val netTxSpeedBps: Long = 0L,
    val netRxTotalBytes: Long = 0L,
    val netRxSpeedBps: Long = 0L,

    // Metadata
    val sampleTime: Long = 0L,
    val batteryLevel: Int = -1,
    val batteryScale: Int = -1
)
