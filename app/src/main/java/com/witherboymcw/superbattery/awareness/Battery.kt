package com.witherboymcw.superbattery.awareness

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.SystemClock
import com.witherboymcw.superbattery.screens.settings.SettingsHelper
import java.util.Locale

data class BatteryPowerInfo(
    val isCharging: Boolean,
    val powerWatts: Float,
    val currentMilliAmperes: Float,
    val voltageVolts: Float
) {
    fun getCurrentPowerString(context: Context): String {
        val signedWatts = if (isCharging) powerWatts else -powerWatts
        val sign = if (isCharging && signedWatts > 0f) "+" else ""
        val locale = SettingsHelper.getAppLocale(context)
        return String.format(locale, "%s%.1fW", sign, signedWatts)
    }

    val currentPowerString: String
        get() {
            val signedWatts = if (isCharging) powerWatts else -powerWatts
            val sign = if (isCharging && signedWatts > 0f) "+" else ""
            return String.format(Locale.getDefault(), "%s%.1fW", sign, signedWatts)
        }
}

fun getBatteryPowerInfo(
    context: Context,
    isDualCell: Boolean = SettingsHelper.isDualCellBattery(context)
): BatteryPowerInfo? {
    val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return null
    val batteryIntent = context.registerReceiver(
        null,
        android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
    ) ?: return null

    val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    val isPluggedCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

    val voltageMv = batteryIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
    if (voltageMv <= 0) return null

    var rawCurrent = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
    if (rawCurrent == Int.MIN_VALUE || rawCurrent == 0) {
        rawCurrent = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
        if (rawCurrent == Int.MIN_VALUE || rawCurrent == 0) return null
    }

    // Chuẩn hóa nếu kernel OEM (như Samsung) trả về mA thay vì µA
    // Khi app đang mở trên màn hình, dòng thực tế luôn > 25mA (25,000 µA) và < 25A (25,000 mA)
    var currentUa = kotlin.math.abs(rawCurrent).toFloat()
    if (currentUa in 1.0f..25_000.0f) {
        currentUa *= 1000f
    }

    val cellMultiplier = if (isDualCell) 2f else 1f
    val voltageVolts = (voltageMv / 1000f) * cellMultiplier
    val currentAmperes = currentUa / 1_000_000f
    val powerWatts = voltageVolts * currentAmperes

    val isSamsung = android.os.Build.MANUFACTURER.equals("samsung", ignoreCase = true)
    val isNetCharging = when {
        !isPluggedCharging -> false
        isSamsung -> rawCurrent > 0
        else -> true // Giữ mặc định theo trạng thái sạc của hệ thống cho các hãng khác
    }

    return BatteryPowerInfo(
        isCharging = isNetCharging,
        powerWatts = powerWatts,
        currentMilliAmperes = currentUa / 1000f,
        voltageVolts = voltageVolts
    )
}

object BatteryMath {
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

    fun getBatteryPowerInfo(
        context: Context,
        isDualCell: Boolean = SettingsHelper.isDualCellBattery(context)
    ): BatteryPowerInfo? =
        com.witherboymcw.superbattery.awareness.getBatteryPowerInfo(context, isDualCell)

    fun getSystemBatteryPercentage(batteryIntent: Intent?): Int {
        if (batteryIntent == null) return 0
        val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return if (level >= 0 && scale > 0) {
            (level * 100f / scale).toInt()
        } else 0
    }

    fun getBatteryPercentage(context: Context, batteryIntent: Intent?, voltageMv: Int): Int {
        return getBatteryPercentage(SettingsHelper.isUseSystemBatteryCounter(context), batteryIntent, voltageMv)
    }

    fun getBatteryPercentage(isSystemBattery: Boolean, batteryIntent: Intent?, voltageMv: Int): Int {
        return if (isSystemBattery) {
            getSystemBatteryPercentage(batteryIntent)
        } else {
            if (voltageMv > 0) calculatePercentage(voltageMv) else 0
        }
    }

    @Synchronized
    fun getSmoothedVoltage(rawMv: Int, sampleTimeMs: Long = SystemClock.elapsedRealtime()): Int {
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

    fun calculatePercentage(voltageMv: Int): Int {
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

    fun percentageColor(percent: Int): Int = com.witherboymcw.superbattery.templates.MetricColors.getBatteryColor(percent)
}
