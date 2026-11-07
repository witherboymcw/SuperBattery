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
            return getBatteryPercentage(isUseSystemBatteryCounter(context), batteryIntent, voltageMv)
        }

        internal fun getBatteryPercentage(isSystemBattery: Boolean, batteryIntent: Intent?, voltageMv: Int): Int {
            return if (isSystemBattery) {
                getSystemBatteryPercentage(batteryIntent)
            } else {
                if (voltageMv > 0) calculatePercentage(voltageMv) else 0
            }
        }
        // Single conservative voltage-to-charge curve, compressed to 3550–4140 mV.
        private val DISCHARGE_VOLTAGES = intArrayOf(
            4140, 4133, 4126, 4118, 4111, 4104, 4097, 4090, 4083, 4076,
            4069, 4062, 4055, 4048, 4041, 4034, 4028, 4021, 4014, 4007,
            4000, 3993, 3986, 3979, 3971, 3964, 3957, 3950, 3943, 3936,
            3930, 3924, 3918, 3912, 3906, 3900, 3894, 3889, 3884, 3879,
            3875, 3871, 3867, 3863, 3860, 3857, 3853, 3850, 3847, 3844,
            3841, 3838, 3835, 3832, 3829, 3827, 3824, 3821, 3818, 3816,
            3813, 3810, 3808, 3805, 3802, 3800, 3797, 3794, 3791, 3789,
            3786, 3783, 3781, 3778, 3776, 3774, 3771, 3768, 3765, 3762,
            3758, 3754, 3749, 3743, 3737, 3731, 3723, 3716, 3707, 3698,
            3689, 3678, 3664, 3649, 3634, 3619, 3605, 3591, 3577, 3564,
            3550
        )

        private const val VOLTAGE_SAMPLE_LIMIT = 15
        private const val VOLTAGE_SAMPLE_INTERVAL_MS = 500L
        private const val VOLTAGE_SAMPLE_RESET_GAP_MS = 60_000L
        private val voltageBuffer = IntArray(VOLTAGE_SAMPLE_LIMIT)
        private var voltageSampleCount = 0
        private var voltageSampleHead = 0
        private var voltageRunningSum = 0L
        private var lastVoltageSampleTimeMs = Long.MIN_VALUE

        @Synchronized
        internal fun getSmoothedVoltage(rawMv: Int, sampleTimeMs: Long = SystemClock.elapsedRealtime()): Int {
            if (rawMv <= 0) return rawMv
            if (lastVoltageSampleTimeMs != Long.MIN_VALUE &&
                sampleTimeMs - lastVoltageSampleTimeMs >= VOLTAGE_SAMPLE_RESET_GAP_MS
            ) {
                voltageSampleCount = 0
                voltageSampleHead = 0
                voltageRunningSum = 0L
                lastVoltageSampleTimeMs = Long.MIN_VALUE
            }
            val shouldAddSample = lastVoltageSampleTimeMs == Long.MIN_VALUE ||
                sampleTimeMs - lastVoltageSampleTimeMs >= VOLTAGE_SAMPLE_INTERVAL_MS
            if (shouldAddSample) {
                if (voltageSampleCount < VOLTAGE_SAMPLE_LIMIT) {
                    voltageBuffer[voltageSampleHead] = rawMv
                    voltageRunningSum += rawMv
                    voltageSampleCount++
                } else {
                    voltageRunningSum -= voltageBuffer[voltageSampleHead]
                    voltageBuffer[voltageSampleHead] = rawMv
                    voltageRunningSum += rawMv
                }
                voltageSampleHead = (voltageSampleHead + 1) % VOLTAGE_SAMPLE_LIMIT
                lastVoltageSampleTimeMs = sampleTimeMs
            }
            return if (voltageSampleCount == 0) rawMv else (voltageRunningSum / voltageSampleCount).toInt()
        }

        internal fun calculatePercentage(voltageMv: Int): Int {
            if (voltageMv <= 0) return 0
            val maxV = DISCHARGE_VOLTAGES[0]
            val minV = DISCHARGE_VOLTAGES[DISCHARGE_VOLTAGES.lastIndex]
            if (voltageMv >= maxV) return 100
            if (voltageMv <= minV) return 0

            var low = 0
            var high = DISCHARGE_VOLTAGES.lastIndex - 1
            while (low <= high) {
                val mid = (low + high) ushr 1
                val vHigh = DISCHARGE_VOLTAGES[mid]
                val vLow = DISCHARGE_VOLTAGES[mid + 1]
                if (voltageMv in vLow..vHigh) {
                    val pHigh = 100 - mid
                    val pLow = pHigh - 1
                    val ratio = (voltageMv - vLow).toFloat() / (vHigh - vLow)
                    return (pLow + ratio * (pHigh - pLow)).toInt()
                } else if (voltageMv > vHigh) {
                    high = mid - 1
                } else {
                    low = mid + 1
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
