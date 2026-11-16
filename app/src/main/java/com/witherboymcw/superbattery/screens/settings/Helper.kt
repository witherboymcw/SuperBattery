package com.witherboymcw.superbattery.screens.settings

import android.content.Context
import com.witherboymcw.superbattery.RamUnit
import com.witherboymcw.superbattery.TempUnit
import com.witherboymcw.superbattery.VoltageUnit
import com.witherboymcw.superbattery.settings.AppPreferences
import java.util.Locale

object SettingsHelper {

    fun getVoltageUnit(context: Context): VoltageUnit =
        VoltageUnit.fromCode(AppPreferences.getVoltageUnitCode(context))

    /** Delegates to [VoltageUnit.fromMv]. */
    fun convertVoltage(voltageMv: Float, unit: VoltageUnit): Float = unit.fromMv(voltageMv)

    /** Delegates to [VoltageUnit.toMv]. */
    fun convertToVoltageMv(value: Float, unit: VoltageUnit): Float = unit.toMv(value)

    /** Delegates to [VoltageUnit.format]. */
    fun formatVoltage(voltageMv: Int, unit: VoltageUnit): String = unit.format(voltageMv)

    fun getRamUnit(context: Context): RamUnit =
        RamUnit.fromCode(AppPreferences.getRamUnitCode(context))

    /** Delegates to [RamUnit.fromMb]. */
    fun convertRam(ramMb: Float, unit: RamUnit): Float = unit.fromMb(ramMb)

    /** Delegates to [RamUnit.toMb]. */
    fun convertToRamMb(value: Float, unit: RamUnit): Float = unit.toMb(value)

    fun getTemperatureUnit(context: Context): TempUnit =
        TempUnit.fromCode(AppPreferences.getTempUnitCode(context))

    /** Delegates to [TempUnit.fromCelsius]. */
    fun convertTemperature(tempCelsius: Float, unit: TempUnit): Float = unit.fromCelsius(tempCelsius)

    /** Delegates to [TempUnit.toCelsius]. */
    fun convertToCelsius(value: Float, unit: TempUnit): Float = unit.toCelsius(value)

    fun formatDashboardTemperature(context: Context, tempCelsius: Float): String {
        val unit = getTemperatureUnit(context)
        val converted = unit.fromCelsius(tempCelsius)
        val locale = getAppLocale(context)
        return String.format(locale, "%.1f %s", converted, unit.symbol)
    }

    fun formatWidgetTemperature(context: Context, tempCelsius: Float): String {
        val unit = getTemperatureUnit(context)
        return unit.formatWidget(tempCelsius)
    }

    /** Delegates to [TempUnit.formatWidget]. */
    fun formatWidgetTemperature(tempCelsius: Float, unit: TempUnit): String =
        unit.formatWidget(tempCelsius)

    fun getSystemPrimaryLanguage(): String =
        com.witherboymcw.superbattery.core.LocaleManager.getSystemPrimaryLanguage()

    fun getAppLocale(context: Context): Locale =
        com.witherboymcw.superbattery.core.LocaleManager.getAppLocale(context)

    fun getLocalizedContext(context: Context): Context =
        com.witherboymcw.superbattery.core.LocaleManager.getLocalizedContext(context)

    fun isUseSystemBatteryCounter(context: Context): Boolean =
        AppPreferences.isUseSystemBatteryCounter(context)

    fun isDualCellBattery(context: Context): Boolean =
        AppPreferences.isDualCellBattery(context)
}
