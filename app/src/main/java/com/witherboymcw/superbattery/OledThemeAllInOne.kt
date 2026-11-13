package com.witherboymcw.superbattery

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.os.Build
import android.provider.Settings
import android.view.Display
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.card.MaterialCardView
import com.witherboymcw.superbattery.screens.settings.SettingsViewController
import com.witherboymcw.superbattery.widgets.BatteryWidget

/**
 * Top-level hardware condition checker for guaranteed OLED display detection.
 * Preserved for unit testing and hardware condition evaluation.
 */
internal fun checkGuaranteedOledConditions(
    hasFingerprint: Boolean,
    hasUdfps: Boolean,
    isWideColorGamut: Boolean,
    desiredMinLuminance: Float?,
    supportedHdrTypesCount: Int,
    isAodSupported: Boolean
): Boolean {
    if (hasFingerprint && hasUdfps) {
        return true
    }
    return isWideColorGamut &&
            desiredMinLuminance == 0.0f &&
            supportedHdrTypesCount >= 2 &&
            isAodSupported
}

/**
 * Hardware OLED display detector.
 */
object OledDetector {
    fun isGuaranteedOled(context: Context): Boolean {
        val hasFingerprint = context.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
        val hasUdfps = context.packageManager.hasSystemFeature("android.hardware.fingerprint.under_display")
                || context.packageManager.hasSystemFeature("vendor.display.enable_udfps")
        @Suppress("DEPRECATION")
        val display: Display? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
                ?: (context as? android.app.Activity)?.display
        } else {
            (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay
                ?: (context as? android.app.Activity)?.windowManager?.defaultDisplay
        }
        val wide = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && display?.isWideColorGamut == true
        val hdr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) display?.hdrCapabilities else null
        val doze = try { Settings.Secure.getInt(context.contentResolver, "doze_always_on", 0) != 0 } catch (_: Exception) { false }
        val id = context.resources.getIdentifier("config_dozeAlwaysOn", "bool", "android")
        val configDoze = id != 0 && try { context.resources.getBoolean(id) } catch (_: Exception) { false }
        return checkGuaranteedOledConditions(
            hasFingerprint,
            hasUdfps,
            wide,
            hdr?.desiredMinLuminance,
            hdr?.supportedHdrTypes?.size ?: 0,
            doze || configDoze
        )
    }
}

/**
 * Unified OLED Theme Architecture & Helper.
 * Encapsulates screen hardware detection, theme selection preferences, activity theme application,
 * card view styling, and graph background theme application.
 */
object OledThemeAllInOne {

    const val ASSUME_OLED_KEY = "assume_oled"
    const val KEY_ASSUME_OLED_DROPDOWN_OPEN = "key_assume_oled_dropdown_open"

    const val OPTION_NONE = "none"
    const val OPTION_OLED = "oled"
    const val OPTION_NOT_OLED = "not_oled"

    private const val IS_FIRST_LAUNCH_KEY = "is_first_launch"

    /**
     * Determines whether OLED mode should be considered effective based on user preference or hardware detection.
     */
    fun isEffectiveOled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(BatteryWidget.SETTINGS_PREFS, Context.MODE_PRIVATE)
        val assumeOled = prefs.getString(ASSUME_OLED_KEY, OPTION_NONE) ?: OPTION_NONE
        return when (assumeOled) {
            OPTION_OLED -> true
            OPTION_NOT_OLED -> false
            else -> OledDetector.isGuaranteedOled(context)
        }
    }

    /**
     * Checks if the OLED theme is currently active (either explicitly selected or system default with effective OLED in night mode).
     */
    fun isOledThemeActive(context: Context): Boolean {
        val prefs = context.getSharedPreferences(BatteryWidget.SETTINGS_PREFS, Context.MODE_PRIVATE)
        val savedTheme = prefs.getString(SettingsViewController.APP_THEME_KEY, "system") ?: "system"
        val isEffective = isEffectiveOled(context)
        val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        return savedTheme == OPTION_OLED || (savedTheme == "system" && isEffective && isNight)
    }

    /**
     * Returns the effective theme name for history graphs ("oled" or saved theme).
     */
    fun getEffectiveThemeForGraph(context: Context): String {
        val prefs = context.getSharedPreferences(BatteryWidget.SETTINGS_PREFS, Context.MODE_PRIVATE)
        val savedTheme = prefs.getString(SettingsViewController.APP_THEME_KEY, "system") ?: "system"
        return if (isOledThemeActive(context)) OPTION_OLED else savedTheme
    }

    /**
     * Applies the initial theme configuration for an activity, including first-launch OLED defaults and AppCompatDelegate night mode settings.
     */
    fun applyActivityTheme(activity: AppCompatActivity) {
        val prefs = activity.getSharedPreferences(BatteryWidget.SETTINGS_PREFS, Context.MODE_PRIVATE)
        val isEffective = isEffectiveOled(activity)

        if (prefs.getBoolean(IS_FIRST_LAUNCH_KEY, true)) {
            val editor = prefs.edit().putBoolean(IS_FIRST_LAUNCH_KEY, false)
            if (isEffective) {
                editor.putString(SettingsViewController.APP_THEME_KEY, OPTION_OLED)
            }
            editor.apply()
        }

        val savedTheme = prefs.getString(SettingsViewController.APP_THEME_KEY, "system") ?: "system"
        if (savedTheme == OPTION_OLED) {
            activity.setTheme(R.style.Theme_SuperBattery_Oled)
            if (AppCompatDelegate.getDefaultNightMode() != AppCompatDelegate.MODE_NIGHT_YES) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
        } else if (savedTheme == "system" && isEffective) {
            val isNight = (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            if (isNight) {
                activity.setTheme(R.style.Theme_SuperBattery_Oled)
            }
            if (AppCompatDelegate.getDefaultNightMode() != AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        } else {
            val targetNightMode = when (savedTheme) {
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            if (AppCompatDelegate.getDefaultNightMode() != targetNightMode) {
                AppCompatDelegate.setDefaultNightMode(targetNightMode)
            }
        }
    }

    /**
     * Applies standard card theme styling (transparent background + stroke in OLED mode, solid surface + no stroke in standard mode).
     */
    fun applyCardTheme(card: MaterialCardView?, context: Context, isOled: Boolean = isOledThemeActive(context)) {
        if (card == null) return
        val strokeWidthPx = (context.resources.displayMetrics.density * 1f).toInt()
        val defaultSurface = com.google.android.material.color.MaterialColors.getColor(
            context, com.google.android.material.R.attr.colorSurface, Color.TRANSPARENT
        )
        val outlineColor = com.google.android.material.color.MaterialColors.getColor(
            context, com.google.android.material.R.attr.colorOutline, Color.GRAY
        )

        card.strokeColor = outlineColor
        if (isOled) {
            card.setCardBackgroundColor(Color.TRANSPARENT)
            card.strokeWidth = strokeWidthPx
        } else {
            card.setCardBackgroundColor(defaultSurface)
            card.strokeWidth = 0
        }
    }

    /**
     * Applies card theme to any View if it is a MaterialCardView.
     */
    fun applyCardTheme(view: View?, context: Context, isOled: Boolean = isOledThemeActive(context)) {
        (view as? MaterialCardView)?.let { applyCardTheme(it, context, isOled) }
    }

    /**
     * Applies solid background styling for back navigation cards.
     */
    fun applySolidCardTheme(card: MaterialCardView?, context: Context) {
        if (card == null) return
        val defaultSurface = com.google.android.material.color.MaterialColors.getColor(
            context, com.google.android.material.R.attr.colorSurface, Color.TRANSPARENT
        )
        card.setCardBackgroundColor(defaultSurface)
        card.strokeWidth = 0
    }

    /**
     * Applies card theme to all child MaterialCardView elements in a container.
     */
    fun applyCardThemeToContainer(container: ViewGroup?, context: Context, isOled: Boolean = isOledThemeActive(context)) {
        if (container == null) return
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child is MaterialCardView) {
                applyCardTheme(child, context, isOled)
            }
        }
    }

    /**
     * Applies OLED background and plot background styling to history graphs.
     */
    fun applyGraphTheme(context: Context, vararg graphs: HistoryGraphView?, isOled: Boolean = isOledThemeActive(context)) {
        val bg = if (isOled) Color.BLACK else Color.TRANSPARENT
        graphs.forEach { graph ->
            graph?.setBackgroundColor(bg)
            graph?.setPlotBackgroundColor(bg)
        }
    }
}
