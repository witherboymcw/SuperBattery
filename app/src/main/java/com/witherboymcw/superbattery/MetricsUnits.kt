package com.witherboymcw.superbattery

import java.util.Locale

enum class TempUnit(val code: String, val symbol: String) {
    CELSIUS("celsius", "ºC"),
    FAHRENHEIT("fahrenheit", "ºF"),
    KELVIN("kelvin", "K");

    /** Converts a Celsius value to this unit. */
    fun fromCelsius(tempCelsius: Float): Float = when (this) {
        CELSIUS -> tempCelsius
        FAHRENHEIT -> tempCelsius * 1.8f + 32f
        KELVIN -> tempCelsius + 273.15f
    }

    /** Converts a value in this unit back to Celsius. */
    fun toCelsius(value: Float): Float = when (this) {
        CELSIUS -> value
        FAHRENHEIT -> (value - 32f) / 1.8f
        KELVIN -> value - 273.15f
    }

    /** Formats a Celsius value as widget text in this unit (no decimal for K). */
    fun formatWidget(tempCelsius: Float): String {
        val converted = fromCelsius(tempCelsius)
        return when (this) {
            KELVIN -> String.format(Locale.getDefault(), "%.0f %s", converted, symbol)
            else -> String.format(Locale.getDefault(), "%.0f%s", converted, symbol)
        }
    }

    companion object {
        fun fromCode(code: String?): TempUnit = when (code) {
            "fahrenheit" -> FAHRENHEIT
            "kelvin" -> KELVIN
            else -> CELSIUS
        }
    }
}

enum class VoltageUnit(val code: String, val symbol: String) {
    MV("mv", "mV"),
    V("v", "V");

    /** Converts a millivolt value to this unit. */
    fun fromMv(voltageMv: Float): Float = when (this) {
        MV -> voltageMv
        V -> voltageMv / 1000f
    }

    /** Converts a value in this unit back to millivolts. */
    fun toMv(value: Float): Float = when (this) {
        MV -> value
        V -> value * 1000f
    }

    /** Formats an integer millivolt value as a display string. */
    fun format(voltageMv: Int): String = when (this) {
        MV -> "$voltageMv $symbol"
        V -> String.format(Locale.getDefault(), "%.2f %s", voltageMv / 1000f, symbol)
    }

    companion object {
        fun fromCode(code: String?): VoltageUnit = when (code) {
            "v" -> V
            else -> MV
        }
    }
}

enum class RamUnit(val code: String, val symbol: String) {
    MB("mb", "MB"),
    GB("gb", "GB");

    /** Converts a megabyte value to this unit. */
    fun fromMb(ramMb: Float): Float = when (this) {
        MB -> ramMb
        GB -> ramMb / 1024f
    }

    /** Converts a value in this unit back to megabytes. */
    fun toMb(value: Float): Float = when (this) {
        MB -> value
        GB -> value * 1024f
    }

    companion object {
        fun fromCode(code: String?): RamUnit = when (code) {
            "gb" -> GB
            else -> MB
        }
    }
}
