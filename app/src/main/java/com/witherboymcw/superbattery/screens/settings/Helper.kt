package com.witherboymcw.superbattery.screens.settings

import android.content.Context
import com.witherboymcw.superbattery.RamUnit
import com.witherboymcw.superbattery.TempUnit
import com.witherboymcw.superbattery.VoltageUnit
import com.witherboymcw.superbattery.settings.AppPreferences
import java.util.Locale

object SettingsHelper {

    fun getVoltageUnit(context: Context): VoltageUnit {
        return VoltageUnit.fromCode(AppPreferences.getVoltageUnit(context))
    }

    fun convertVoltage(voltageMv: Float, unit: VoltageUnit): Float = when (unit) {
        VoltageUnit.MV -> voltageMv
        VoltageUnit.V -> voltageMv / 1000f
    }

    fun convertToVoltageMv(value: Float, unit: VoltageUnit): Float = when (unit) {
        VoltageUnit.MV -> value
        VoltageUnit.V -> value * 1000f
    }

    fun formatVoltage(voltageMv: Int, unit: VoltageUnit): String = when (unit) {
        VoltageUnit.MV -> "$voltageMv ${unit.symbol}"
        VoltageUnit.V -> String.format(Locale.getDefault(), "%.2f %s", voltageMv / 1000f, unit.symbol)
    }

    fun getRamUnit(context: Context): RamUnit {
        return RamUnit.fromCode(AppPreferences.getRamUnit(context))
    }

    fun convertRam(ramMb: Float, unit: RamUnit): Float = when (unit) {
        RamUnit.MB -> ramMb
        RamUnit.GB -> ramMb / 1024f
    }

    fun convertToRamMb(value: Float, unit: RamUnit): Float = when (unit) {
        RamUnit.MB -> value
        RamUnit.GB -> value * 1024f
    }

    fun getTemperatureUnit(context: Context): TempUnit {
        return TempUnit.fromCode(AppPreferences.getTempUnit(context))
    }

    fun convertTemperature(tempCelsius: Float, unit: TempUnit): Float = when (unit) {
        TempUnit.CELSIUS -> tempCelsius
        TempUnit.FAHRENHEIT -> tempCelsius * 1.8f + 32f
        TempUnit.KELVIN -> tempCelsius + 273.15f
    }

    fun convertToCelsius(value: Float, unit: TempUnit): Float = when (unit) {
        TempUnit.CELSIUS -> value
        TempUnit.FAHRENHEIT -> (value - 32f) / 1.8f
        TempUnit.KELVIN -> value - 273.15f
    }

    fun formatDashboardTemperature(context: Context, tempCelsius: Float): String {
        val unit = getTemperatureUnit(context)
        val converted = convertTemperature(tempCelsius, unit)
        val locale = getAppLocale(context)
        return String.format(locale, "%.1f %s", converted, unit.symbol)
    }

    fun formatWidgetTemperature(context: Context, tempCelsius: Float): String {
        val unit = getTemperatureUnit(context)
        return formatWidgetTemperature(tempCelsius, unit)
    }

    fun formatWidgetTemperature(tempCelsius: Float, unit: TempUnit): String {
        val converted = convertTemperature(tempCelsius, unit)
        return when (unit) {
            TempUnit.KELVIN -> String.format(Locale.getDefault(), "%.0f %s", converted, unit.symbol)
            else -> String.format(Locale.getDefault(), "%.0f%s", converted, unit.symbol)
        }
    }

    fun getSystemPrimaryLanguage(): String =
        com.witherboymcw.superbattery.templates.LocaleManager.getSystemPrimaryLanguage()

    fun getAppLocale(context: Context): Locale =
        com.witherboymcw.superbattery.templates.LocaleManager.getAppLocale(context)

    fun getLocalizedContext(context: Context): Context =
        com.witherboymcw.superbattery.templates.LocaleManager.getLocalizedContext(context)

    fun isUseSystemBatteryCounter(context: Context): Boolean {
        return AppPreferences.isUseSystemBatteryCounter(context)
    }

    fun isDualCellBattery(context: Context): Boolean {
        return AppPreferences.isDualCellBattery(context)
    }
}
