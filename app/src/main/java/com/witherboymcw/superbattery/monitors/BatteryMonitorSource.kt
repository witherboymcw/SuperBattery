package com.witherboymcw.superbattery.monitors

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import com.witherboymcw.superbattery.BatteryInfo

/**
 * Source module responsible for querying and building BatteryInfo snapshots.
 */
class BatteryMonitorSource {

    fun getBatteryIntent(context: Context): Intent? {
        return try {
            ContextCompat.registerReceiver(
                context,
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_EXPORTED
            )
        } catch (_: Exception) {
            null
        }
    }

    fun getBatteryInfo(context: Context): BatteryInfo {
        val batteryIntent = getBatteryIntent(context)
        val voltageMv = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val tempRaw = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val temperatureC = tempRaw / 10f
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        return BatteryInfo(
            voltageMv = voltageMv,
            temperatureC = temperatureC,
            level = level,
            scale = scale,
            status = status,
            isCharging = isCharging,
            rawIntent = batteryIntent
        )
    }
}
