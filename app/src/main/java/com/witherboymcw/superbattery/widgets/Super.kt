package com.witherboymcw.superbattery.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.widget.RemoteViews
import com.witherboymcw.superbattery.DeviceConnectionCounter
import com.witherboymcw.superbattery.R
import com.witherboymcw.superbattery.awareness.BatteryMath
import com.witherboymcw.superbattery.screens.settings.SettingsHelper

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
        if (intent.action == BatteryWidget.ACTION_MANUAL_REFRESH) {
            BatteryWidget.handleWidgetTap(context)
            BatteryWidget.updateAllWidgets(context, AppWidgetManager.getInstance(context))
        }
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
            if (ids.isEmpty()) return
            ids.forEach { updateAppWidget(context, manager, it) }
        }

        private fun updateAppWidget(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val batteryStatus = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            val rawVoltage = batteryStatus?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
            val voltage = if (rawVoltage > 0) BatteryMath.getSmoothedVoltage(rawVoltage) else 0
            val temperatureC =
                (batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
            val percent = BatteryMath.getBatteryPercentage(context, batteryStatus, voltage)

            val localizedContext = SettingsHelper.getLocalizedContext(context)
            val formattedTemp = SettingsHelper.formatWidgetTemperature(context, temperatureC)
            BatteryWidget.updateCriticalEvent(
                context,
                "temperature",
                temperatureC >= 45f,
                localizedContext.getString(R.string.critical_temperature_title),
                localizedContext.getString(R.string.critical_temperature_message, formattedTemp)
            )

            val views = RemoteViews(context.packageName, R.layout.widget_super_5x1).apply {
                setTextViewText(R.id.super_temperature, formattedTemp)
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
                setTextColor(R.id.super_battery_percent, BatteryMath.percentageColor(percent))
                val voltageUnit = SettingsHelper.getVoltageUnit(context)
                setTextViewText(R.id.super_voltage, if (voltage > 0) SettingsHelper.formatVoltage(voltage, voltageUnit) else "—")
            }
            val tapIntent = Intent(context, WidgetTapTrampolineActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            }
            val tapPendingIntent = PendingIntent.getActivity(
                context,
                300_000 + appWidgetId,
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.super_widget_root, tapPendingIntent)
            manager.updateAppWidget(appWidgetId, views)
        }
    }
}
