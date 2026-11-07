package com.witherboymcw.superbattery

enum class TempUnit(val code: String, val symbol: String) {
    CELSIUS("celsius", "ºC"),
    FAHRENHEIT("fahrenheit", "ºF"),
    KELVIN("kelvin", "K");

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

    companion object {
        fun fromCode(code: String?): RamUnit = when (code) {
            "gb" -> GB
            else -> MB
        }
    }
}

