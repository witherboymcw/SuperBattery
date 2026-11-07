package com.witherboymcw.superbattery

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import android.widget.RemoteViews

class BatteryWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateBatteryWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action

        if (action == ACTION_MANUAL_REFRESH ||
            action == ACTION_AUTO_UPDATE ||
            action == Intent.ACTION_POWER_CONNECTED ||
            action == Intent.ACTION_POWER_DISCONNECTED ||
            action == Intent.ACTION_SCREEN_ON
        ) {

            if (action == ACTION_MANUAL_REFRESH) {
                handleWidgetTap(context)
            }

            val appWidgetManager = AppWidgetManager.getInstance(context)
            updateAllWidgets(context, appWidgetManager)
        }
    }

    companion object {
        const val ACTION_MANUAL_REFRESH = "com.witherboymcw.superbattery.ACTION_MANUAL_REFRESH"
        const val ACTION_AUTO_UPDATE = "com.witherboymcw.superbattery.ACTION_AUTO_UPDATE"
        const val EXTRA_CLOSE_SETTINGS = "com.witherboymcw.superbattery.EXTRA_CLOSE_SETTINGS"
        const val SETTINGS_PREFS = "dashboard_settings"
        const val USE_SYSTEM_BATTERY_KEY = "use_system_battery_counter"
        const val APP_LANGUAGE_KEY = "app_language"
        const val TEMP_UNIT_KEY = "temp_unit"
        const val VOLTAGE_UNIT_KEY = "voltage_unit"
        const val RAM_UNIT_KEY = "ram_unit"

        private var lastWidgetTapTimestampMs: Long = 0L

        internal fun handleWidgetTap(context: Context) {
            val now = SystemClock.elapsedRealtime()
            val interval = now - lastWidgetTapTimestampMs
            if (interval in 50..400) {
                lastWidgetTapTimestampMs = 0L
                val mainIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(EXTRA_CLOSE_SETTINGS, true)
                }
                context.startActivity(mainIntent)
            } else {
                lastWidgetTapTimestampMs = now
            }
            HotspotNotificationListener.refreshFromWidgetTap()
        }

        internal fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            batteryStatus: Intent?,
            smoothedVoltageMv: Int,
            percent: Int = BatteryMath.getBatteryPercentage(context, batteryStatus, smoothedVoltageMv)
        ) {
            val views = RemoteViews(context.packageName, R.layout.battery_widget)
            
            views.setTextViewText(R.id.appwidget_percent, "$percent%")
            val percentColor = BatteryMath.percentageColor(percent)
            views.setTextColor(R.id.appwidget_percent, percentColor)
            
            val voltageUnit = SettingsHelper.getVoltageUnit(context)
            views.setTextViewText(R.id.appwidget_voltage, SettingsHelper.formatVoltage(smoothedVoltageMv, voltageUnit))

            val refreshIntent = Intent(context, BatteryWidget::class.java).apply {
                action = ACTION_MANUAL_REFRESH
                setPackage(context.packageName)
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, refreshPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        internal fun updateAllWidgets(context: Context, appWidgetManager: AppWidgetManager) {
            val batteryIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, BatteryWidget::class.java)
            )
            updateBatteryWidgets(context, appWidgetManager, batteryIds)

            val detailsIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, BatteryDetailsWidget::class.java)
            )
            if (detailsIds.isNotEmpty()) {
                detailsIds.forEach { BatteryDetailsWidget.updateAppWidget(context, appWidgetManager, it) }
            }

            CalendarWidgetProvider.updateAllWidgets(context, appWidgetManager)
            SuperWidgetProvider.updateAllWidgets(context, appWidgetManager)
        }

        private fun updateBatteryWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val rawVoltageMv = batteryStatus?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
            val smoothedVoltageMv = BatteryMath.getSmoothedVoltage(rawVoltageMv)
            val percent = BatteryMath.getBatteryPercentage(context, batteryStatus, smoothedVoltageMv)

            val isSystemBattery = SettingsHelper.isUseSystemBatteryCounter(context)
            val isBatteryLevelCritical = !isSystemBattery && percent in 1..10
            val localizedContext = SettingsHelper.getLocalizedContext(context)
            updateCriticalEvent(
                context,
                "battery_percent",
                isBatteryLevelCritical,
                localizedContext.getString(R.string.critical_percent_title),
                localizedContext.getString(R.string.critical_percent_message, percent)
            )

            if (appWidgetIds.isEmpty()) return
            appWidgetIds.forEach { updateAppWidget(context, appWidgetManager, it, batteryStatus, smoothedVoltageMv, percent) }
        }

        internal fun updateCriticalEvent(context: Context, key: String, active: Boolean, title: String, message: String) {
            CriticalEventNotification.update(context, key, active, title, message)
        }
    }
}
