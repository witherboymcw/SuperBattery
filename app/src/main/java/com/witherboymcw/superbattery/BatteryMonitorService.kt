package com.witherboymcw.superbattery

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder

class BatteryMonitorService : Service() {

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                val refreshIntent = Intent(context, BatteryWidget::class.java).apply {
                    action = BatteryWidget.ACTION_MANUAL_REFRESH
                    setPackage(context.packageName)
                }
                context.sendBroadcast(refreshIntent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    override fun onDestroy() {
        unregisterReceiver(batteryReceiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
