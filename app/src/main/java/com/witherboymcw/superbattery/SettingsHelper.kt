package com.witherboymcw.superbattery

import android.content.Context
import java.util.Locale

object SettingsHelper {
    private const val SETTINGS_PREFS = BatteryWidget.SETTINGS_PREFS
    private const val VOLTAGE_UNIT_KEY = BatteryWidget.VOLTAGE_UNIT_KEY
    private const val RAM_UNIT_KEY = BatteryWidget.RAM_UNIT_KEY
    private const val TEMP_UNIT_KEY = BatteryWidget.TEMP_UNIT_KEY
    private const val APP_LANGUAGE_KEY = BatteryWidget.APP_LANGUAGE_KEY
    private const val USE_SYSTEM_BATTERY_KEY = BatteryWidget.USE_SYSTEM_BATTERY_KEY

    fun getVoltageUnit(context: Context): VoltageUnit {
        val prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        return VoltageUnit.fromCode(prefs.getString(VOLTAGE_UNIT_KEY, "mv"))
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
        val prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        return RamUnit.fromCode(prefs.getString(RAM_UNIT_KEY, "mb"))
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
        val prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        return TempUnit.fromCode(prefs.getString(TEMP_UNIT_KEY, "celsius"))
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

    fun getSystemPrimaryLanguage(): String {
        val locales = android.content.res.Resources.getSystem().configuration.locales
        return if (!locales.isEmpty) {
            locales[0]?.language ?: "en"
        } else {
            "en"
        }
    }

    fun getAppLocale(context: Context): Locale {
        val prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        val savedLang = prefs.getString(APP_LANGUAGE_KEY, "system") ?: "system"
        if (savedLang == "system" || savedLang.isEmpty()) {
            return if (getSystemPrimaryLanguage() == "vi") Locale.forLanguageTag("vi") else Locale.ENGLISH
        }
        val tag = when (savedLang) {
            "en-BR", "en-brainrot", "en-x-brainrot", "en-0rot" -> "art"
            else -> savedLang
        }
        return Locale.forLanguageTag(tag)
    }

    fun getLocalizedContext(context: Context): Context {
        val locale = getAppLocale(context)
        val config = android.content.res.Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    fun isUseSystemBatteryCounter(context: Context): Boolean {
        return context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            .getBoolean(USE_SYSTEM_BATTERY_KEY, false)
    }
}
