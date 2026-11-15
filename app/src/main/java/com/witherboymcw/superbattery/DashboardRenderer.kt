package com.witherboymcw.superbattery

import android.app.Activity
import android.content.res.Configuration
import android.media.AudioManager
import com.witherboymcw.superbattery.awareness.BatteryMath
import com.witherboymcw.superbattery.awareness.FoldableAwareness
import com.witherboymcw.superbattery.screens.dashboard.DashboardUiState
import com.witherboymcw.superbattery.screens.settings.SettingsHelper
import com.witherboymcw.superbattery.templates.MetricColors
import com.witherboymcw.superbattery.templates.MetricGraphBinder
import com.witherboymcw.superbattery.utils.Formatters

class DashboardRenderer(private val views: DashboardViews, private val activity: Activity) {

    fun setBatteryCard(titleId: Int, value: String, percent: Int = 0, color: Int = MetricColors.COLOR_GREEN) {
        views.cardBatteryTitle?.text = activity.getString(titleId)
        views.cardBatteryValue?.text = value
        views.batteryRing?.setProgressAndColor(percent, color)
    }

    fun updateBatteryRingGeometry() {
        val isMiniSquare = FoldableAwareness.isMiniSquareMode(activity)
        val isLandscape = activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val useLandscapeRing = isLandscape || isMiniSquare
        val locale = SettingsHelper.getAppLocale(activity)
        val supportedLang = com.witherboymcw.superbattery.templates.SupportedLanguage.fromLanguageCode(locale.language)

        if (useLandscapeRing) {
            views.batteryRing?.startAngle = 270f
            views.batteryRing?.maxSweep = supportedLang.batteryRingMaxSweep
        } else {
            views.batteryRing?.startAngle = 210f
            views.batteryRing?.maxSweep = -240f
        }
    }

    fun updateWifiStatus(isConnected: Boolean) {
        views.ivNetworkWifi?.alpha = if (isConnected) 1.0f else 0.3f
    }

    fun updateBluetoothStatus(isConnected: Boolean) {
        views.ivNetworkBluetooth?.alpha = if (isConnected) 1.0f else 0.3f
    }

    fun updateSimSignalStatus(isReady: Boolean) {
        views.ivNetworkSim1?.alpha = if (isReady) 1.0f else 0.3f
    }

    fun updateRingerModeIcon(mode: Int) {
        val iconRes = when (mode) {
            AudioManager.RINGER_MODE_SILENT -> R.drawable.ic_ringer_silent
            AudioManager.RINGER_MODE_VIBRATE -> R.drawable.ic_ringer_vibrate
            else -> R.drawable.ic_ringer_sound
        }
        views.btnRingerModeToggle?.setImageResource(iconRes)
        views.btnRingerModeToggle?.alpha = 1.0f
    }

    fun renderDashboardUiState(state: DashboardUiState) {
        val dash = activity.getString(R.string.placeholder_dash)
        val locale = SettingsHelper.getAppLocale(activity)

        // 1. Battery Card
        val batteryValueText = state.batteryPercent?.let { "$it%" } ?: dash
        val batteryColor = MetricColors.getBatteryColor(state.batteryPercent ?: 0)
        setBatteryCard(state.batteryTitleRes, batteryValueText, state.batteryPercent ?: 0, batteryColor)

        // 2. Voltage Section
        views.tvVoltageTitle?.setText(state.voltageTitleRes)
        views.tvVoltageCurrent?.text = if (state.voltageMv > 0) {
            SettingsHelper.formatVoltage(state.voltageMv, state.voltageUnit)
        } else dash

        views.tvVoltagePower?.text = if (state.hasPowerInfo) {
            val signedWatts = if (state.isCharging) state.powerWatts else -state.powerWatts
            val sign = if (state.isCharging && signedWatts > 0f) "+" else ""
            String.format(locale, "%s%.1fW", sign, signedWatts)
        } else dash

        val (minVoltage, maxVoltage, voltageSuffix) = when (state.voltageUnit) {
            VoltageUnit.MV -> Triple(state.minVoltageMv, state.maxVoltageMv, " " + activity.getString(R.string.unit_mv))
            VoltageUnit.V -> Triple(state.minVoltageMv / 1000f, state.maxVoltageMv / 1000f, " " + activity.getString(R.string.unit_v))
        }
        val voltageDecimalPlaces = if (state.voltageUnit == VoltageUnit.V) 2 else 0

        MetricGraphBinder.updateGraph(
            graph = views.voltageGraph,
            samples = state.voltageSamples,
            minValue = minVoltage,
            maxValue = maxVoltage,
            suffix = voltageSuffix,
            currentTimeMs = state.sampleTime,
            decimalPlaces = voltageDecimalPlaces,
            valueTransform = { rawMv -> SettingsHelper.convertVoltage(rawMv, state.voltageUnit) },
            valueColor = { voltage ->
                val rawMv = SettingsHelper.convertToVoltageMv(voltage, state.voltageUnit).toInt()
                val percent = BatteryMath.getBatteryPercentage(state.isUseSystemBattery, state.batteryLevel, state.batteryScale, rawMv)
                MetricColors.getBatteryColor(percent)
            }
        )

        // 3. RAM Section
        val usedPercent = if (state.totalRamMb > 0) (state.usedRamMb * 100 / state.totalRamMb).toInt() else 0
        views.tvRamCurrent?.text = activity.getString(R.string.current_ram_format, usedPercent)

        val ramValConverted = SettingsHelper.convertRam(state.usedRamMb.toFloat(), state.ramUnit)
        val ramSuffixStr = activity.getString(if (state.ramUnit == RamUnit.GB) R.string.unit_gb else R.string.unit_mb)
        views.tvRamUsageDetail?.text = if (state.ramUnit == RamUnit.GB) {
            String.format(locale, "%.1f %s", ramValConverted, ramSuffixStr)
        } else {
            String.format(locale, "%.0f %s", ramValConverted, ramSuffixStr)
        }

        val maxRam = SettingsHelper.convertRam(state.totalRamMb.toFloat(), state.ramUnit).coerceAtLeast(0.1f)
        val ramSuffix = " " + activity.getString(if (state.ramUnit == RamUnit.GB) R.string.unit_gb else R.string.unit_mb)
        val ramDecimalPlaces = if (state.ramUnit == RamUnit.GB) 1 else 0

        MetricGraphBinder.updateGraph(
            graph = views.ramGraph,
            samples = state.ramSamples,
            minValue = 0f,
            maxValue = maxRam,
            suffix = ramSuffix,
            currentTimeMs = state.sampleTime,
            decimalPlaces = ramDecimalPlaces,
            valueTransform = { rawMb -> SettingsHelper.convertRam(rawMb, state.ramUnit) },
            valueColor = { ramVal ->
                val ramMb = SettingsHelper.convertToRamMb(ramVal, state.ramUnit)
                val ramUsedPercent = if (state.totalRamMb > 0) ramMb * 100f / state.totalRamMb else 0f
                MetricColors.getRamColor(ramUsedPercent)
            }
        )

        // 4. Temperature Section
        views.tvTemperatureCurrent?.text = SettingsHelper.formatDashboardTemperature(activity, state.temperatureC)
        views.tvTemperatureStatus?.text = activity.getString(state.temperatureStatusRes)

        val (minTemp, maxTemp, tempSuffix) = when (state.tempUnit) {
            TempUnit.CELSIUS -> Triple(0f, 60f, activity.getString(R.string.unit_celsius))
            TempUnit.FAHRENHEIT -> Triple(32f, 140f, activity.getString(R.string.unit_fahrenheit))
            TempUnit.KELVIN -> Triple(273f, 333f, " " + activity.getString(R.string.unit_kelvin))
        }

        MetricGraphBinder.updateGraph(
            graph = views.tempGraph,
            samples = state.temperatureSamples,
            minValue = minTemp,
            maxValue = maxTemp,
            suffix = tempSuffix,
            currentTimeMs = state.sampleTime,
            decimalPlaces = 0,
            valueTransform = { rawC -> SettingsHelper.convertTemperature(rawC, state.tempUnit) },
            valueColor = { tempVal ->
                val celsius = SettingsHelper.convertToCelsius(tempVal, state.tempUnit)
                MetricColors.getTemperatureColor(celsius)
            }
        )

        // 5. Network Speed Section
        if (!state.isNetworkSupported) {
            val unavail = activity.getString(R.string.network_unavailable)
            views.tvNetTxTotal?.text = unavail
            views.tvNetTxSpeed?.text = dash
            views.tvNetRxTotal?.text = unavail
            views.tvNetRxSpeed?.text = dash
        } else {
            views.tvNetTxTotal?.text = Formatters.formatByteSize(state.netTxTotalBytes)
            views.tvNetTxSpeed?.text = Formatters.formatByteSize(state.netTxSpeedBps) + "/s"
            views.tvNetRxTotal?.text = Formatters.formatByteSize(state.netRxTotalBytes)
            views.tvNetRxSpeed?.text = Formatters.formatByteSize(state.netRxSpeedBps) + "/s"
        }
    }
}
