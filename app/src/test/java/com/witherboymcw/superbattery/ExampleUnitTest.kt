package com.witherboymcw.superbattery

import org.junit.Test
import org.junit.Assert.*

class ExampleUnitTest {
    @Test
    fun testLunarCalendarKnownDates() {
        // Tết 2024: 10/02/2024 -> 1/1/2024 Giáp Thìn
        val lunar2024 = VietnameseLunarCalendar.fromSolar(10, 2, 2024)
        assertEquals(1, lunar2024.day)
        assertEquals(1, lunar2024.month)
        assertEquals(2024, lunar2024.year)
        assertFalse(lunar2024.isLeapMonth)
        assertEquals("Giáp Thìn", VietnameseLunarCalendar.canChiYear(lunar2024.year))

        // Tết 2025: 29/01/2025 -> 1/1/2025 Ất Tỵ
        val lunar2025 = VietnameseLunarCalendar.fromSolar(29, 1, 2025)
        assertEquals(1, lunar2025.day)
        assertEquals(1, lunar2025.month)
        assertEquals(2025, lunar2025.year)
        assertFalse(lunar2025.isLeapMonth)
        assertEquals("Ất Tỵ", VietnameseLunarCalendar.canChiYear(lunar2025.year))

        // Tết 2026: 17/02/2026 -> 1/1/2026 Bính Ngọ
        val lunar2026 = VietnameseLunarCalendar.fromSolar(17, 2, 2026)
        assertEquals(1, lunar2026.day)
        assertEquals(1, lunar2026.month)
        assertEquals(2026, lunar2026.year)
        assertFalse(lunar2026.isLeapMonth)
        assertEquals("Bính Ngọ", VietnameseLunarCalendar.canChiYear(lunar2026.year))
    }

    @Test
    fun testTemperatureUnitConversions() {
        val tempC = 35.0f

        // Celsius
        val celsiusVal = SettingsHelper.convertTemperature(tempC, TempUnit.CELSIUS)
        assertEquals(35.0f, celsiusVal, 0.001f)
        assertEquals(35.0f, SettingsHelper.convertToCelsius(celsiusVal, TempUnit.CELSIUS), 0.001f)

        // Fahrenheit: 35 * 1.8 + 32 = 95
        val fahrenheitVal = SettingsHelper.convertTemperature(tempC, TempUnit.FAHRENHEIT)
        assertEquals(95.0f, fahrenheitVal, 0.001f)
        assertEquals(35.0f, SettingsHelper.convertToCelsius(fahrenheitVal, TempUnit.FAHRENHEIT), 0.001f)

        // Kelvin: 35 + 273.15 = 308.15
        val kelvinVal = SettingsHelper.convertTemperature(tempC, TempUnit.KELVIN)
        assertEquals(308.15f, kelvinVal, 0.001f)
        assertEquals(35.0f, SettingsHelper.convertToCelsius(kelvinVal, TempUnit.KELVIN), 0.001f)
    }

    @Test
    fun testTemperatureFormatting() {
        val tempC = 35.0f
        assertEquals("35ºC", SettingsHelper.formatWidgetTemperature(tempC, TempUnit.CELSIUS))
        assertEquals("95ºF", SettingsHelper.formatWidgetTemperature(tempC, TempUnit.FAHRENHEIT))
        assertEquals("308 K", SettingsHelper.formatWidgetTemperature(tempC, TempUnit.KELVIN))
    }

    @Test
    fun testVoltageUnitConversionsAndFormatting() {
        val voltageMv = 3850

        // Millivolts
        val mvVal = SettingsHelper.convertVoltage(voltageMv.toFloat(), VoltageUnit.MV)
        assertEquals(3850.0f, mvVal, 0.001f)
        assertEquals(3850.0f, SettingsHelper.convertToVoltageMv(mvVal, VoltageUnit.MV), 0.001f)
        assertEquals("3850 mV", SettingsHelper.formatVoltage(voltageMv, VoltageUnit.MV))

        // Volts (US Locale)
        val vVal = SettingsHelper.convertVoltage(voltageMv.toFloat(), VoltageUnit.V)
        assertEquals(3.85f, vVal, 0.001f)
        assertEquals(3850.0f, SettingsHelper.convertToVoltageMv(vVal, VoltageUnit.V), 0.001f)

        // Volts Decimal Formatting in Vietnamese
        val viLocale = java.util.Locale.forLanguageTag("vi")
        val formattedVi = String.format(viLocale, "%.1f %s", 4.3f, VoltageUnit.V.symbol)
        assertEquals("4,3 V", formattedVi)
    }

    @Test
    fun testRamUnitConversions() {
        val ramMb = 2048.0f

        // Megabytes
        val mbVal = SettingsHelper.convertRam(ramMb, RamUnit.MB)
        assertEquals(2048.0f, mbVal, 0.001f)
        assertEquals(2048.0f, SettingsHelper.convertToRamMb(ramMb, RamUnit.MB), 0.001f)

        // Gigabytes
        val gbVal = SettingsHelper.convertRam(ramMb, RamUnit.GB)
        assertEquals(2.0f, gbVal, 0.001f)
        assertEquals(2048.0f, SettingsHelper.convertToRamMb(gbVal, RamUnit.GB), 0.001f)
    }

    @Test
    fun testOledDetectionUnderDisplayFingerprint() {
        // Guaranteed OLED if under-display fingerprint sensor is present
        assertTrue(
            checkGuaranteedOledConditions(
                hasFingerprint = true,
                hasUdfps = true,
                isWideColorGamut = false,
                desiredMinLuminance = null,
                supportedHdrTypesCount = 0,
                isAodSupported = false
            )
        )
    }

    @Test
    fun testOledDetectionDisplayParametersAllMet() {
        // Guaranteed OLED if all 4 display criteria are satisfied
        assertTrue(
            checkGuaranteedOledConditions(
                hasFingerprint = false,
                hasUdfps = false,
                isWideColorGamut = true,
                desiredMinLuminance = 0.0f,
                supportedHdrTypesCount = 2,
                isAodSupported = true
            )
        )
    }

    @Test
    fun testOledDetectionLcdFailsStrictCriteria() {
        // LCD with non-zero min luminance -> false
        assertFalse(
            checkGuaranteedOledConditions(
                hasFingerprint = false,
                hasUdfps = false,
                isWideColorGamut = true,
                desiredMinLuminance = 0.02f,
                supportedHdrTypesCount = 2,
                isAodSupported = true
            )
        )

        // LCD without wide color gamut -> false
        assertFalse(
            checkGuaranteedOledConditions(
                hasFingerprint = false,
                hasUdfps = false,
                isWideColorGamut = false,
                desiredMinLuminance = 0.0f,
                supportedHdrTypesCount = 2,
                isAodSupported = true
            )
        )

        // LCD with insufficient HDR types -> false
        assertFalse(
            checkGuaranteedOledConditions(
                hasFingerprint = false,
                hasUdfps = false,
                isWideColorGamut = true,
                desiredMinLuminance = 0.0f,
                supportedHdrTypesCount = 1,
                isAodSupported = true
            )
        )

        // LCD without AOD support -> false
        assertFalse(
            checkGuaranteedOledConditions(
                hasFingerprint = false,
                hasUdfps = false,
                isWideColorGamut = true,
                desiredMinLuminance = 0.0f,
                supportedHdrTypesCount = 2,
                isAodSupported = false
            )
        )
    }
}
