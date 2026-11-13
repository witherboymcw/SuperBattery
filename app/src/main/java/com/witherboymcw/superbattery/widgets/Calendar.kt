package com.witherboymcw.superbattery.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.witherboymcw.superbattery.R
import com.witherboymcw.superbattery.VietnameseLunarCalendar
import com.witherboymcw.superbattery.screens.settings.SettingsHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

class CalendarWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
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
            Intent.ACTION_LOCALE_CHANGED -> {
                val manager = AppWidgetManager.getInstance(context)
                BatteryWidget.updateAllWidgets(context, manager)
            }
        }
    }

    companion object {
        internal fun updateAllWidgets(context: Context, manager: AppWidgetManager) {
            val ids = manager.getAppWidgetIds(ComponentName(context, CalendarWidgetProvider::class.java))
            if (ids.isEmpty()) return
            ids.forEach { updateWidget(context, manager, it) }
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_calendar_3x1).apply {
                applyDateText(
                    context,
                    this,
                    R.id.calendar_solar_date,
                    R.id.calendar_lunar_date,
                    "EEEE, dd MMMM"
                )
            }
            val tapIntent = Intent(context, WidgetTapTrampolineActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            }
            val tapPendingIntent = PendingIntent.getActivity(
                context,
                100_000 + appWidgetId,
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.calendar_widget_root, tapPendingIntent)
            manager.updateAppWidget(appWidgetId, views)
        }

        internal fun applyDateText(
            context: Context,
            views: RemoteViews,
            solarViewId: Int,
            lunarViewId: Int,
            solarPattern: String
        ) {
            val now = Calendar.getInstance()
            val day = now.get(Calendar.DAY_OF_MONTH)
            val month = now.get(Calendar.MONTH) + 1
            val year = now.get(Calendar.YEAR)
            val appLocale = SettingsHelper.getAppLocale(context)
            val solarFormat = SimpleDateFormat(solarPattern, appLocale).apply {
                timeZone = now.timeZone
            }
            val solarText = solarFormat.format(Date())
            val lunar = VietnameseLunarCalendar.fromSolar(day, month, year)
            val lunarText = String.format(
                appLocale,
                "%02d - %02d - %04d %s",
                lunar.day,
                lunar.month,
                lunar.year,
                VietnameseLunarCalendar.canChiYear(lunar.year)
            )
            views.setTextViewText(solarViewId, solarText)
            views.setTextViewText(lunarViewId, lunarText)
        }
    }
}

