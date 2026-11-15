package com.witherboymcw.superbattery.awareness

import org.junit.Test
import org.junit.Assert.*

class BatteryPowerTest {
    @Test
    fun testBatteryPowerInfoLogic() {
        // 1. AOSP standard sign convention: negative rawCurrent (< 0) means charging into battery
        val aospCharging = computeBatteryPowerInfo(
            isPluggedCharging = true,
            voltageMv = 4000,
            rawCurrent = -1_500_000, // -1500 mA in uA
            isDualCell = false,
            isSamsung = false
        )
        assertNotNull(aospCharging)
        assertTrue(aospCharging!!.isCharging)
        assertEquals(4.0f, aospCharging.voltageVolts, 0.001f)
        assertEquals(1500.0f, aospCharging.currentMilliAmperes, 0.001f)
        assertEquals(6.0f, aospCharging.powerWatts, 0.001f)

        val aospDischarging = computeBatteryPowerInfo(
            isPluggedCharging = false,
            voltageMv = 3800,
            rawCurrent = 500_000, // +500 mA in uA
            isDualCell = false,
            isSamsung = false
        )
        assertNotNull(aospDischarging)
        assertFalse(aospDischarging!!.isCharging)

        // 2. Samsung sign convention: positive rawCurrent (> 0) means charging into battery
        val samsungCharging = computeBatteryPowerInfo(
            isPluggedCharging = true,
            voltageMv = 4000,
            rawCurrent = 1_500_000, // +1500 mA in uA
            isDualCell = false,
            isSamsung = true
        )
        assertNotNull(samsungCharging)
        assertTrue(samsungCharging!!.isCharging)

        // 3. Dual-cell (2S) battery - single-cell reported voltage (< 5500 mV) should be doubled
        val dualCellSingleInput = computeBatteryPowerInfo(
            isPluggedCharging = true,
            voltageMv = 4000, // Single cell ~4.0V
            rawCurrent = -2_000_000,
            isDualCell = true,
            isSamsung = false
        )
        assertNotNull(dualCellSingleInput)
        assertEquals(8.0f, dualCellSingleInput!!.voltageVolts, 0.001f) // 4.0V * 2 = 8.0V
        assertEquals(2000.0f, dualCellSingleInput.currentMilliAmperes, 0.001f) // I_pack = I_cell
        assertEquals(16.0f, dualCellSingleInput.powerWatts, 0.001f) // P = 8.0V * 2.0A = 16W

        // 4. Dual-cell (2S) battery - pack reported voltage (>= 5500 mV) should NOT be doubled again
        val dualCellPackInput = computeBatteryPowerInfo(
            isPluggedCharging = true,
            voltageMv = 8000, // Pack voltage ~8.0V
            rawCurrent = -2_000_000,
            isDualCell = true,
            isSamsung = false
        )
        assertNotNull(dualCellPackInput)
        assertEquals(8.0f, dualCellPackInput!!.voltageVolts, 0.001f) // Already pack voltage
        assertEquals(2000.0f, dualCellPackInput.currentMilliAmperes, 0.001f)
        assertEquals(16.0f, dualCellPackInput.powerWatts, 0.001f)

        // 5. Stale cached charging sample when charger is unplugged (Samsung & AOSP)
        val samsungUnpluggedStale = computeBatteryPowerInfo(
            isPluggedCharging = false,
            voltageMv = 4000,
            rawCurrent = 2_500_000, // Stale +2.5A charging current on Samsung
            isDualCell = false,
            isSamsung = true
        )
        assertNull(samsungUnpluggedStale)

        val aospUnpluggedStale = computeBatteryPowerInfo(
            isPluggedCharging = false,
            voltageMv = 4000,
            rawCurrent = -2_500_000, // Stale -2.5A charging current on AOSP
            isDualCell = false,
            isSamsung = false
        )
        assertNull(aospUnpluggedStale)
    }
}
