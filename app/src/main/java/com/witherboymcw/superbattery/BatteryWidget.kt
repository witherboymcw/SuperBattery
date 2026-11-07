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
import android.graphics.Color
import java.util.LinkedList
import java.util.Locale

enum class TempUnit(val code: String, val symbol: String) {
    CELSIUS("celsius", "ºC"),
    FAHRENHEIT("fahrenheit", "ºF"),
    KELVIN("kelvin", "K");

    companion object {
        fun fromCode(code: String?): TempUnit = when (code) {
            "fahrenheit" -> FAHRENHEIT
            "kelvin" -> KELVIN
            else -> CELSIUS
        }
    }
}

enum class VoltageUnit(val code: String, val symbol: String) {
    MV("mv", "mV"),
    V("v", "V");

    companion object {
        fun fromCode(code: String?): VoltageUnit = when (code) {
            "v" -> V
            else -> MV
        }
    }
}

enum class RamUnit(val code: String, val symbol: String) {
    MB("mb", "MB"),
    GB("gb", "GB");

    companion object {
        fun fromCode(code: String?): RamUnit = when (code) {
            "gb" -> GB
            else -> MB
        }
    }
}

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
            action == Intent.ACTION_POWER_CONNECTED ||
            action == Intent.ACTION_POWER_DISCONNECTED ||
            action == Intent.ACTION_SCREEN_ON) {

            if (action == ACTION_MANUAL_REFRESH) {
                handleWidgetTap(context)
            }
            
            val appWidgetManager = AppWidgetManager.getInstance(context)
            updateAllWidgets(context, appWidgetManager)
        }
    }

    companion object {
        const val ACTION_MANUAL_REFRESH = "com.witherboymcw.superbattery.ACTION_MANUAL_REFRESH"
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

        internal fun getVoltageUnit(context: Context): VoltageUnit {
            val prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            return VoltageUnit.fromCode(prefs.getString(VOLTAGE_UNIT_KEY, "mv"))
        }

        internal fun convertVoltage(voltageMv: Float, unit: VoltageUnit): Float {
            return when (unit) {
                VoltageUnit.MV -> voltageMv
                VoltageUnit.V -> voltageMv / 1000f
            }
        }

        internal fun convertToVoltageMv(value: Float, unit: VoltageUnit): Float {
            return when (unit) {
                VoltageUnit.MV -> value
                VoltageUnit.V -> value * 1000f
            }
        }

        internal fun formatVoltage(voltageMv: Int, unit: VoltageUnit): String {
            return when (unit) {
                VoltageUnit.MV -> "$voltageMv mV"
                VoltageUnit.V -> String.format(Locale.getDefault(), "%.2f V", voltageMv / 1000f)
            }
        }

        internal fun getRamUnit(context: Context): RamUnit {
            val prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            return RamUnit.fromCode(prefs.getString(RAM_UNIT_KEY, "mb"))
        }

        internal fun convertRam(ramMb: Float, unit: RamUnit): Float {
            return when (unit) {
                RamUnit.MB -> ramMb
                RamUnit.GB -> ramMb / 1024f
            }
        }

        internal fun convertToRamMb(value: Float, unit: RamUnit): Float {
            return when (unit) {
                RamUnit.MB -> value
                RamUnit.GB -> value * 1024f
            }
        }

        internal fun getTemperatureUnit(context: Context): TempUnit {
            val prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            return TempUnit.fromCode(prefs.getString(TEMP_UNIT_KEY, "celsius"))
        }

        internal fun convertTemperature(tempCelsius: Float, unit: TempUnit): Float {
            return when (unit) {
                TempUnit.CELSIUS -> tempCelsius
                TempUnit.FAHRENHEIT -> tempCelsius * 1.8f + 32f
                TempUnit.KELVIN -> tempCelsius + 273.15f
            }
        }

        internal fun convertToCelsius(value: Float, unit: TempUnit): Float {
            return when (unit) {
                TempUnit.CELSIUS -> value
                TempUnit.FAHRENHEIT -> (value - 32f) / 1.8f
                TempUnit.KELVIN -> value - 273.15f
            }
        }

        internal fun formatDashboardTemperature(context: Context, tempCelsius: Float): String {
            val unit = getTemperatureUnit(context)
            val converted = convertTemperature(tempCelsius, unit)
            return when (unit) {
                TempUnit.KELVIN -> String.format(Locale.getDefault(), "%.1f K", converted)
                else -> String.format(Locale.getDefault(), "%.1f%s", converted, unit.symbol)
            }
        }

        internal fun formatWidgetTemperature(context: Context, tempCelsius: Float): String {
            val unit = getTemperatureUnit(context)
            return formatWidgetTemperature(tempCelsius, unit)
        }

        internal fun formatWidgetTemperature(tempCelsius: Float, unit: TempUnit): String {
            val converted = convertTemperature(tempCelsius, unit)
            return when (unit) {
                TempUnit.KELVIN -> String.format(Locale.getDefault(), "%.0f K", converted)
                else -> String.format(Locale.getDefault(), "%.0f%s", converted, unit.symbol)
            }
        }

        private const val VOLTAGE_SAMPLE_LIMIT = 15
        private const val VOLTAGE_SAMPLE_INTERVAL_MS = 500L
        private const val VOLTAGE_SAMPLE_RESET_GAP_MS = 60_000L
        private val voltageSamples = LinkedList<Int>()
        private var lastVoltageSampleTimeMs = Long.MIN_VALUE

        internal fun getSystemPrimaryLanguage(): String {
            val locales = android.content.res.Resources.getSystem().configuration.locales
            return if (!locales.isEmpty) {
                locales[0]?.language ?: "en"
            } else {
                "en"
            }
        }

        internal fun getAppLocale(context: Context): Locale {
            val prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            val savedLang = prefs.getString(APP_LANGUAGE_KEY, "system") ?: "system"
            if (savedLang == "system" || savedLang.isEmpty()) {
                return if (getSystemPrimaryLanguage() == "vi") Locale.forLanguageTag("vi") else Locale.ENGLISH
            }
            val tag = when (savedLang) {
                "en-BR", "en-brainrot", "en-x-brainrot", "en-0rot" -> "art"
                else -> savedLang
            }
            return Locale.forLanguageTag(tag)
        }

        internal fun getLocalizedContext(context: Context): Context {
            val locale = getAppLocale(context)
            val config = android.content.res.Configuration(context.resources.configuration)
            config.setLocale(locale)
            return context.createConfigurationContext(config)
        }

        internal fun isUseSystemBatteryCounter(context: Context): Boolean {
            return context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
                .getBoolean(USE_SYSTEM_BATTERY_KEY, false)
        }

        internal fun getSystemBatteryPercentage(batteryIntent: Intent?): Int {
            if (batteryIntent == null) return 0
            val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            return if (level >= 0 && scale > 0) {
                (level * 100f / scale).toInt()
            } else 0
        }

        internal fun getBatteryPercentage(context: Context, batteryIntent: Intent?, voltageMv: Int): Int {
            return if (isUseSystemBatteryCounter(context)) {
                getSystemBatteryPercentage(batteryIntent)
            } else {
                if (voltageMv > 0) calculatePercentage(voltageMv) else 0
            }
        }
        // Single conservative voltage-to-charge curve, compressed to 3550–4140 mV.
        private val dischargeTable = listOf(
            4140 to 100,
            4133 to 99,
            4126 to 98,
            4118 to 97,
            4111 to 96,
            4104 to 95,
            4097 to 94,
            4090 to 93,
            4083 to 92,
            4076 to 91,
            4069 to 90,
            4062 to 89,
            4055 to 88,
            4048 to 87,
            4041 to 86,
            4034 to 85,
            4028 to 84,
            4021 to 83,
            4014 to 82,
            4007 to 81,
            4000 to 80,
            3993 to 79,
            3986 to 78,
            3979 to 77,
            3971 to 76,
            3964 to 75,
            3957 to 74,
            3950 to 73,
            3943 to 72,
            3936 to 71,
            3930 to 70,
            3924 to 69,
            3918 to 68,
            3912 to 67,
            3906 to 66,
            3900 to 65,
            3894 to 64,
            3889 to 63,
            3884 to 62,
            3879 to 61,
            3875 to 60,
            3871 to 59,
            3867 to 58,
            3863 to 57,
            3860 to 56,
            3857 to 55,
            3853 to 54,
            3850 to 53,
            3847 to 52,
            3844 to 51,
            3841 to 50,
            3838 to 49,
            3835 to 48,
            3832 to 47,
            3829 to 46,
            3827 to 45,
            3824 to 44,
            3821 to 43,
            3818 to 42,
            3816 to 41,
            3813 to 40,
            3810 to 39,
            3808 to 38,
            3805 to 37,
            3802 to 36,
            3800 to 35,
            3797 to 34,
            3794 to 33,
            3791 to 32,
            3789 to 31,
            3786 to 30,
            3783 to 29,
            3781 to 28,
            3778 to 27,
            3776 to 26,
            3774 to 25,
            3771 to 24,
            3768 to 23,
            3765 to 22,
            3762 to 21,
            3758 to 20,
            3754 to 19,
            3749 to 18,
            3743 to 17,
            3737 to 16,
            3731 to 15,
            3723 to 14,
            3716 to 13,
            3707 to 12,
            3698 to 11,
            3689 to 10,
            3678 to 9,
            3664 to 8,
            3649 to 7,
            3634 to 6,
            3619 to 5,
            3605 to 4,
            3591 to 3,
            3577 to 2,
            3564 to 1,
            3550 to 0
        )

        @Synchronized
        internal fun getSmoothedVoltage(rawMv: Int, sampleTimeMs: Long = SystemClock.elapsedRealtime()): Int {
            if (rawMv <= 0) return rawMv
            if (lastVoltageSampleTimeMs != Long.MIN_VALUE &&
                sampleTimeMs - lastVoltageSampleTimeMs >= VOLTAGE_SAMPLE_RESET_GAP_MS
            ) {
                voltageSamples.clear()
                lastVoltageSampleTimeMs = Long.MIN_VALUE
            }
            val shouldAddSample = lastVoltageSampleTimeMs == Long.MIN_VALUE ||
                sampleTimeMs - lastVoltageSampleTimeMs >= VOLTAGE_SAMPLE_INTERVAL_MS
            if (shouldAddSample) {
                if (voltageSamples.size >= VOLTAGE_SAMPLE_LIMIT) {
                    voltageSamples.removeFirst()
                }
                voltageSamples.add(rawMv)
                lastVoltageSampleTimeMs = sampleTimeMs
            }
            return if (voltageSamples.isEmpty()) rawMv else voltageSamples.average().toInt()
        }

        internal fun calculatePercentage(voltageMv: Int): Int {
            if (voltageMv <= 0) return 0
            if (voltageMv >= dischargeTable.first().first) return 100
            if (voltageMv <= dischargeTable.last().first) return 0

            for (i in 0 until dischargeTable.size - 1) {
                val (vHigh, pHigh) = dischargeTable[i]
                val (vLow, pLow) = dischargeTable[i + 1]
                if (voltageMv in vLow..vHigh) {
                    val ratio = (voltageMv - vLow).toFloat() / (vHigh - vLow)
                    return (pLow + ratio * (pHigh - pLow)).toInt()
                }
            }
            return 0
        }

        internal fun percentageColor(percent: Int): Int = when {
            percent >= 20 -> Color.rgb(0, 230, 118)
            percent <= 10 -> Color.rgb(255, 82, 82)
            else -> Color.rgb(255, 179, 0)
        }

        internal fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            batteryStatus: Intent?,
            smoothedVoltageMv: Int,
            percent: Int = getBatteryPercentage(context, batteryStatus, smoothedVoltageMv)
        ) {
            val views = RemoteViews(context.packageName, R.layout.battery_widget)
            
            views.setTextViewText(R.id.appwidget_percent, "$percent%")
            val percentColor = percentageColor(percent)
            views.setTextColor(R.id.appwidget_percent, percentColor)
            
            val voltageUnit = getVoltageUnit(context)
            views.setTextViewText(R.id.appwidget_voltage, formatVoltage(smoothedVoltageMv, voltageUnit))

            val refreshIntent = Intent(context, BatteryWidget::class.java).apply {
                action = ACTION_MANUAL_REFRESH
                setPackage(context.packageName)
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
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
            val smoothedVoltageMv = getSmoothedVoltage(rawVoltageMv)
            val percent = getBatteryPercentage(context, batteryStatus, smoothedVoltageMv)

            val isSystemBattery = isUseSystemBatteryCounter(context)
            val isBatteryLevelCritical = !isSystemBattery && percent in 1..10
            val localizedContext = getLocalizedContext(context)
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
