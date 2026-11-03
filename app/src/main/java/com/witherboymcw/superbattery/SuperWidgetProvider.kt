package com.witherboymcw.superbattery

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.widget.RemoteViews

class SuperWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { updateAppWidget(context, appWidgetManager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            Intent.ACTION_DATE_CHANGED,
            "android.intent.action.TIME_SET",
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_LOCALE_CHANGED -> BatteryWidget.updateAllWidgets(
                context,
                AppWidgetManager.getInstance(context)
            )
        }
    }

    companion object {
        internal fun updateAllWidgets(context: Context, manager: AppWidgetManager) {
            val ids = manager.getAppWidgetIds(ComponentName(context, SuperWidgetProvider::class.java))
            ids.forEach { updateAppWidget(context, manager, it) }
        }

        private fun updateAppWidget(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val batteryStatus = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            val rawVoltage = batteryStatus?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
            val voltage = if (rawVoltage > 0) BatteryWidget.getSmoothedVoltage(rawVoltage) else 0
            val temperatureC =
                (batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
            val percent = BatteryWidget.getBatteryPercentage(context, batteryStatus, voltage)

            val views = RemoteViews(context.packageName, R.layout.widget_super_5x1).apply {
                setTextViewText(R.id.super_temperature, String.format("%.0f°C", temperatureC))
                setTextColor(R.id.super_temperature, BatteryDetailsWidget.temperatureColor(temperatureC))
                setTextViewText(R.id.super_clients, DeviceConnectionCounter.displayText(context))
                CalendarWidgetProvider.applyDateText(
                    context,
                    this,
                    R.id.super_solar_date,
                    R.id.super_lunar_date,
                    "EEE, dd - MM"
                )
                setTextViewText(R.id.super_battery_percent, "$percent%")
                setTextColor(R.id.super_battery_percent, BatteryWidget.percentageColor(percent))
                setTextViewText(R.id.super_voltage, if (voltage > 0) "$voltage mV" else "— mV")
            }
            val refreshIntent = Intent(context, BatteryWidget::class.java).apply {
                action = BatteryWidget.ACTION_MANUAL_REFRESH
                setPackage(context.packageName)
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.super_widget_root, refreshPendingIntent)
            manager.updateAppWidget(appWidgetId, views)
        }
    }
}
