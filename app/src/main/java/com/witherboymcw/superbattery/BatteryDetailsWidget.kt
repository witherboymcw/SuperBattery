package com.witherboymcw.superbattery

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.widget.RemoteViews
import android.graphics.Color

class BatteryDetailsWidget : AppWidgetProvider() {

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
            BatteryWidget.updateAllWidgets(context, AppWidgetManager.getInstance(context))
        }
    }

    companion object {
        internal fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val batteryStatus = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            val tempTenths = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            val tempCelsius = tempTenths / 10.0f

            val views = RemoteViews(context.packageName, R.layout.battery_details_widget)
            views.setTextViewText(R.id.appwidget_temperature, String.format("%.0f°C", tempCelsius))
            val temperatureColor = temperatureColor(tempCelsius)
            views.setTextColor(R.id.appwidget_temperature, temperatureColor)
            BatteryWidget.updateCriticalEvent(
                context,
                "temperature",
                tempCelsius >= 45f,
                context.getString(R.string.critical_temperature_title),
                context.getString(R.string.critical_temperature_message, tempCelsius.toInt())
            )
            views.setTextViewText(
                R.id.appwidget_clients,
                DeviceConnectionCounter.displayText(context)
            )

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
            views.setOnClickPendingIntent(R.id.widget_details_root, refreshPendingIntent)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        internal fun temperatureColor(tempCelsius: Float): Int = when {
            tempCelsius <= 40f -> Color.rgb(0, 230, 118)
            tempCelsius < 45f -> Color.rgb(255, 179, 0)
            else -> Color.rgb(255, 82, 82)
        }
    }
}
