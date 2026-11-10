package com.witherboymcw.superbattery

import android.app.Service
import android.content.Intent
import android.os.IBinder

class BatteryMonitorService : Service() {

    private lateinit var systemMonitorManager: SystemMonitorManager

    override fun onCreate() {
        super.onCreate()
        systemMonitorManager = SystemMonitorManager(this)
        systemMonitorManager.setListener(object : SystemMonitorManager.Listener {
            override fun onBatteryChanged(batteryInfo: BatteryInfo) {
                val refreshIntent = Intent(this@BatteryMonitorService, BatteryWidget::class.java).apply {
                    action = BatteryWidget.ACTION_AUTO_UPDATE
                    setPackage(packageName)
                }
                sendBroadcast(refreshIntent)
            }
        })
        systemMonitorManager.startMonitoring()
    }

    override fun onDestroy() {
        systemMonitorManager.stopMonitoring()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
