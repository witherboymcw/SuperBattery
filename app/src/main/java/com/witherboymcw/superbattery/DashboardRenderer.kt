package com.witherboymcw.superbattery

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.media.AudioManager
import android.view.View
import com.witherboymcw.superbattery.awareness.BatteryMath
import com.witherboymcw.superbattery.awareness.FoldableAwareness
import com.witherboymcw.superbattery.awareness.getBatteryPowerInfo
import com.witherboymcw.superbattery.screens.dashboard.DashboardUiState
import com.witherboymcw.superbattery.screens.settings.SettingsHelper
import com.witherboymcw.superbattery.templates.MetricColors
import com.witherboymcw.superbattery.templates.MetricGraphBinder

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

    fun renderDashboardUiState(state: DashboardUiState, batteryIntent: Intent?) {
        setBatteryCard(state.batteryTitleRes, state.batteryValueText, state.batteryPercent, state.batteryColor)

        views.tvVoltageCurrent?.text = state.voltageCurrentText
        views.tvVoltagePower?.text = state.voltagePowerText
        views.tvVoltageTitle?.setText(state.voltageTitleRes)

        MetricGraphBinder.updateGraph(
            graph = views.voltageGraph,
            samples = state.voltageSamples,
            minValue = state.minVoltage,
            maxValue = state.maxVoltage,
            suffix = state.voltageSuffix,
            currentTimeMs = state.sampleTime,
            decimalPlaces = state.voltageDecimalPlaces,
            valueTransform = { rawMv -> SettingsHelper.convertVoltage(rawMv, state.voltageUnit) },
            valueColor = { voltage ->
                val rawMv = SettingsHelper.convertToVoltageMv(voltage, state.voltageUnit).toInt()
                val percent = BatteryMath.getBatteryPercentage(state.isUseSystemBattery, batteryIntent, rawMv)
                MetricColors.getBatteryColor(percent)
            }
        )

        views.tvRamCurrent?.text = state.ramCurrentPercentText
        views.tvRamUsageDetail?.text = state.ramUsageDetailText

        MetricGraphBinder.updateGraph(
            graph = views.ramGraph,
            samples = state.ramSamples,
            minValue = 0f,
            maxValue = state.maxRam,
            suffix = state.ramSuffix,
            currentTimeMs = state.sampleTime,
            decimalPlaces = state.ramDecimalPlaces,
            valueTransform = { rawMb -> SettingsHelper.convertRam(rawMb, state.ramUnit) },
            valueColor = { ramVal ->
                val ramMb = SettingsHelper.convertToRamMb(ramVal, state.ramUnit)
                val ramUsedPercent = if (state.totalMb > 0) ramMb * 100f / state.totalMb else 0f
                MetricColors.getRamColor(ramUsedPercent)
            }
        )

        views.tvTemperatureCurrent?.text = state.temperatureCurrentText
        views.tvTemperatureStatus?.text = activity.getString(state.temperatureStatusRes)

        MetricGraphBinder.updateGraph(
            graph = views.tempGraph,
            samples = state.temperatureSamples,
            minValue = state.minTemp,
            maxValue = state.maxTemp,
            suffix = state.tempSuffix,
            currentTimeMs = state.sampleTime,
            decimalPlaces = 0,
            valueTransform = { rawC -> SettingsHelper.convertTemperature(rawC, state.tempUnit) },
            valueColor = { tempVal ->
                val celsius = SettingsHelper.convertToCelsius(tempVal, state.tempUnit)
                MetricColors.getTemperatureColor(celsius)
            }
        )

        views.tvNetTxTotal?.text = state.netTxTotalText
        views.tvNetTxSpeed?.text = state.netTxSpeedText
        views.tvNetRxTotal?.text = state.netRxTotalText
        views.tvNetRxSpeed?.text = state.netRxSpeedText
    }
}
