package com.witherboymcw.superbattery.battery

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.witherboymcw.superbattery.SystemMonitorManager
import com.witherboymcw.superbattery.widgets.BatteryWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class BatteryMonitorService : Service() {

    private lateinit var systemMonitorManager: SystemMonitorManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        systemMonitorManager = SystemMonitorManager(this)
        systemMonitorManager.startMonitoring()
        serviceScope.launch {
            systemMonitorManager.batteryInfoState.collect { info ->
                if (info.voltageMv in 1..3400 && !info.isCharging) {
                    LowVoltageNotification.send(this@BatteryMonitorService, info.voltageMv)
                } else if (info.voltageMv >= 3500 || info.isCharging) {
                    LowVoltageNotification.clearAlertState(this@BatteryMonitorService)
                }
                val refreshIntent = Intent(this@BatteryMonitorService, BatteryWidget::class.java).apply {
                    action = BatteryWidget.ACTION_AUTO_UPDATE
                    setPackage(packageName)
                }
                sendBroadcast(refreshIntent)
            }
        }
    }

    override fun onDestroy() {
        systemMonitorManager.stopMonitoring()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
