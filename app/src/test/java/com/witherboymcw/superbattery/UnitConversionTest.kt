package com.witherboymcw.superbattery

import com.witherboymcw.superbattery.screens.settings.SettingsHelper
import org.junit.Test
import org.junit.Assert.*
import java.util.Locale

class UnitConversionTest {
    @Test
    fun testTemperatureUnitConversions() {
        val tempC = 35.0f

        // Celsius (Direct enum & SettingsHelper)
        val celsiusEnumVal = TempUnit.CELSIUS.fromCelsius(tempC)
        assertEquals(35.0f, celsiusEnumVal, 0.001f)
        assertEquals(35.0f, TempUnit.CELSIUS.toCelsius(celsiusEnumVal), 0.001f)

        val celsiusVal = SettingsHelper.convertTemperature(tempC, TempUnit.CELSIUS)
        assertEquals(35.0f, celsiusVal, 0.001f)
        assertEquals(35.0f, SettingsHelper.convertToCelsius(celsiusVal, TempUnit.CELSIUS), 0.001f)

        // Fahrenheit
        val fahrenheitEnumVal = TempUnit.FAHRENHEIT.fromCelsius(tempC)
        assertEquals(95.0f, fahrenheitEnumVal, 0.001f)
        assertEquals(35.0f, TempUnit.FAHRENHEIT.toCelsius(fahrenheitEnumVal), 0.001f)

        val fahrenheitVal = SettingsHelper.convertTemperature(tempC, TempUnit.FAHRENHEIT)
        assertEquals(95.0f, fahrenheitVal, 0.001f)
        assertEquals(35.0f, SettingsHelper.convertToCelsius(fahrenheitVal, TempUnit.FAHRENHEIT), 0.001f)

        // Kelvin
        val kelvinEnumVal = TempUnit.KELVIN.fromCelsius(tempC)
        assertEquals(308.15f, kelvinEnumVal, 0.001f)
        assertEquals(35.0f, TempUnit.KELVIN.toCelsius(kelvinEnumVal), 0.001f)

        val kelvinVal = SettingsHelper.convertTemperature(tempC, TempUnit.KELVIN)
        assertEquals(308.15f, kelvinVal, 0.001f)
        assertEquals(35.0f, SettingsHelper.convertToCelsius(kelvinVal, TempUnit.KELVIN), 0.001f)
    }

    @Test
    fun testTemperatureFormatting() {
        val tempC = 35.0f
        assertEquals("35ºC", TempUnit.CELSIUS.formatWidget(tempC))
        assertEquals("95ºF", TempUnit.FAHRENHEIT.formatWidget(tempC))
        assertEquals("308 K", TempUnit.KELVIN.formatWidget(tempC))

        assertEquals("35ºC", SettingsHelper.formatWidgetTemperature(tempC, TempUnit.CELSIUS))
        assertEquals("95ºF", SettingsHelper.formatWidgetTemperature(tempC, TempUnit.FAHRENHEIT))
        assertEquals("308 K", SettingsHelper.formatWidgetTemperature(tempC, TempUnit.KELVIN))
    }

    @Test
    fun testVoltageUnitConversionsAndFormatting() {
        val voltageMv = 3850

        // Millivolts
        val mvEnumVal = VoltageUnit.MV.fromMv(voltageMv.toFloat())
        assertEquals(3850.0f, mvEnumVal, 0.001f)
        assertEquals(3850.0f, VoltageUnit.MV.toMv(mvEnumVal), 0.001f)
        assertEquals("3850 mV", VoltageUnit.MV.format(voltageMv))

        val mvVal = SettingsHelper.convertVoltage(voltageMv.toFloat(), VoltageUnit.MV)
        assertEquals(3850.0f, mvVal, 0.001f)
        assertEquals(3850.0f, SettingsHelper.convertToVoltageMv(mvVal, VoltageUnit.MV), 0.001f)
        assertEquals("3850 mV", SettingsHelper.formatVoltage(voltageMv, VoltageUnit.MV))

        // Volts
        val vEnumVal = VoltageUnit.V.fromMv(voltageMv.toFloat())
        assertEquals(3.85f, vEnumVal, 0.001f)
        assertEquals(3850.0f, VoltageUnit.V.toMv(vEnumVal), 0.001f)

        val vVal = SettingsHelper.convertVoltage(voltageMv.toFloat(), VoltageUnit.V)
        assertEquals(3.85f, vVal, 0.001f)
        assertEquals(3850.0f, SettingsHelper.convertToVoltageMv(vVal, VoltageUnit.V), 0.001f)

        // Volts formatting (Direct enum & SettingsHelper)
        val defaultFormattedV = VoltageUnit.V.format(voltageMv)
        assertEquals(String.format(Locale.getDefault(), "%.2f %s", 3.85f, VoltageUnit.V.symbol), defaultFormattedV)
        val settingsFormattedV = SettingsHelper.formatVoltage(voltageMv, VoltageUnit.V)
        assertEquals(defaultFormattedV, settingsFormattedV)

        // Volts Decimal Formatting in Vietnamese
        val viLocale = Locale.forLanguageTag("vi")
        val formattedVi = String.format(viLocale, "%.1f %s", 4.3f, VoltageUnit.V.symbol)
        assertEquals("4,3 V", formattedVi)
    }

    @Test
    fun testRamUnitConversions() {
        val ramMb = 2048.0f

        // Megabytes
        val mbEnumVal = RamUnit.MB.fromMb(ramMb)
        assertEquals(2048.0f, mbEnumVal, 0.001f)
        assertEquals(2048.0f, RamUnit.MB.toMb(mbEnumVal), 0.001f)

        val mbVal = SettingsHelper.convertRam(ramMb, RamUnit.MB)
        assertEquals(2048.0f, mbVal, 0.001f)
        assertEquals(2048.0f, SettingsHelper.convertToRamMb(mbVal, RamUnit.MB), 0.001f)

        // Gigabytes
        val gbEnumVal = RamUnit.GB.fromMb(ramMb)
        assertEquals(2.0f, gbEnumVal, 0.001f)
        assertEquals(2048.0f, RamUnit.GB.toMb(gbEnumVal), 0.001f)

        val gbVal = SettingsHelper.convertRam(ramMb, RamUnit.GB)
        assertEquals(2.0f, gbVal, 0.001f)
        assertEquals(2048.0f, SettingsHelper.convertToRamMb(gbVal, RamUnit.GB), 0.001f)
    }
}
