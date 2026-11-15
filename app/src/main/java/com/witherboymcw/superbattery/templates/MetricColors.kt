package com.witherboymcw.superbattery.templates

import com.witherboymcw.superbattery.R

/**
 * Centralized palette and threshold state resolvers for battery, voltage, RAM, and temperature metrics.
 */
object MetricColors {
    val COLOR_GREEN: Int = 0xFF00E676.toInt()
    val COLOR_AMBER: Int = 0xFFFFB300.toInt()
    val COLOR_RED: Int = 0xFFFF5252.toInt()

    fun getBatteryColor(percent: Int): Int = when {
        percent >= 20 -> COLOR_GREEN
        percent <= 10 -> COLOR_RED
        else -> COLOR_AMBER
    }

    fun getRamColor(usedPercent: Float): Int = when {
        usedPercent >= 90f -> COLOR_RED
        usedPercent >= 80f -> COLOR_AMBER
        else -> COLOR_GREEN
    }

    fun getTemperatureColor(celsius: Float): Int = when {
        celsius <= 40f -> COLOR_GREEN
        celsius < 45f -> COLOR_AMBER
        else -> COLOR_RED
    }

    fun getTemperatureStatusRes(celsius: Float): Int = when {
        celsius <= 40f -> R.string.temp_status_nominal
        celsius < 45f -> R.string.temp_status_hot
        else -> R.string.temp_status_critical
    }
}
