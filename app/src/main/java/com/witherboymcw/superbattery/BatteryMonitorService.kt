package com.witherboymcw.superbattery

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import androidx.core.content.ContextCompat

class BatteryMonitorService : Service() {

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                val refreshIntent = Intent(context, BatteryWidget::class.java).apply {
                    action = BatteryWidget.ACTION_AUTO_UPDATE
                    setPackage(context.packageName)
                }
                context.sendBroadcast(refreshIntent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(
            this,
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(batteryReceiver)
        } catch (_: IllegalArgumentException) {
            // Receiver was already unregistered or not registered
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
