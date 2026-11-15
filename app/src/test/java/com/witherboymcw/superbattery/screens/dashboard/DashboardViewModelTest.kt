package com.witherboymcw.superbattery.screens.dashboard

import com.witherboymcw.superbattery.monitors.BatteryInfo
import com.witherboymcw.superbattery.monitors.NetworkSpeed
import com.witherboymcw.superbattery.R
import com.witherboymcw.superbattery.RamUnit
import com.witherboymcw.superbattery.TempUnit
import com.witherboymcw.superbattery.VoltageUnit
import com.witherboymcw.superbattery.awareness.BatteryPowerInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DashboardViewModelTest {

    private lateinit var viewModel: DashboardViewModel

    @Before
    fun setUp() {
        viewModel = DashboardViewModel()
    }

    @Test
    fun testBuildUiStateWithoutContext_normalBattery() {
        val batteryInfo = BatteryInfo(
            voltageMv = 3800,
            temperatureC = 35.0f,
            level = 80,
            scale = 100,
            status = 2,
            isCharging = true
        )
        val powerInfo = BatteryPowerInfo(isCharging = true, powerWatts = 15.0f, currentMilliAmperes = 3000f, voltageVolts = 5.0f)
        val networkSpeed = NetworkSpeed(isSupported = true, totalTxBytes = 1024L, txRate = 100L, totalRxBytes = 2048L, rxRate = 200L)

        val state = viewModel.buildUiState(
            batteryInfo = batteryInfo,
            powerInfo = powerInfo,
            networkSpeed = networkSpeed,
            totalMemBytes = 8_000_000_000L,
            availMemBytes = 4_000_000_000L,
            voltageUnit = VoltageUnit.MV,
            ramUnit = RamUnit.GB,
            tempUnit = TempUnit.CELSIUS,
            isUseSystemBattery = true,
            sampleTime = 1000L
        )

        assertEquals(80, state.batteryPercent)
        assertEquals(3800, state.voltageMv)
        assertTrue(state.hasPowerInfo)
        assertEquals(15.0f, state.powerWatts, 0.001f)
        assertTrue(state.isCharging)
        assertEquals(R.string.charging_true, state.voltageTitleRes)
        assertEquals(R.string.temp_status_nominal, state.temperatureStatusRes)
        assertEquals(35.0f, state.temperatureC, 0.001f)
    }

    @Test
    fun testHighTemperatureAlert() {
        val batteryInfo = BatteryInfo(
            voltageMv = 3900,
            temperatureC = 46.0f,
            level = 50,
            scale = 100
        )
        val state = viewModel.buildUiState(
            batteryInfo = batteryInfo,
            powerInfo = null,
            networkSpeed = NetworkSpeed(isSupported = false),
            totalMemBytes = 4_000_000_000L,
            availMemBytes = 2_000_000_000L,
            voltageUnit = VoltageUnit.V,
            ramUnit = RamUnit.MB,
            tempUnit = TempUnit.CELSIUS,
            isUseSystemBattery = false,
            sampleTime = 1000L
        )

        assertEquals(R.string.temp_status_critical, state.temperatureStatusRes)
    }

    @Test
    fun testVoltageShiftState() {
        // Sample below 4000mV
        viewModel.addVoltageSample(3800f, 1000L)
        var bounds = viewModel.getVoltageBoundsMv()
        assertEquals(3300f, bounds.first)
        assertEquals(4300f, bounds.second)

        // Sample above 4250mV triggers shift
        viewModel.addVoltageSample(4300f, 2000L)
        bounds = viewModel.getVoltageBoundsMv()
        assertEquals(3550f, bounds.first)
        assertEquals(4550f, bounds.second)
    }
}
