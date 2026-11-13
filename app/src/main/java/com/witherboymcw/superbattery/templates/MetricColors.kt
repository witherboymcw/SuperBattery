package com.witherboymcw.superbattery.templates

import android.graphics.Color
import com.witherboymcw.superbattery.R

/**
 * Centralized palette and threshold state resolvers for battery, voltage, RAM, and temperature metrics.
 */
object MetricColors {
    val COLOR_GREEN: Int = Color.rgb(0, 230, 118)
    val COLOR_AMBER: Int = Color.rgb(255, 179, 0)
    val COLOR_RED: Int = Color.rgb(255, 82, 82)

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
