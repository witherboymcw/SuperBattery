package com.witherboymcw.superbattery

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Build
import android.provider.Settings
import android.view.Display
import android.view.WindowManager

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

object OledDetector {
    fun isGuaranteedOled(context: Context): Boolean {
        val hasFingerprint = context.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
        val hasUdfps = context.packageManager.hasSystemFeature("android.hardware.fingerprint.under_display") || context.packageManager.hasSystemFeature("vendor.display.enable_udfps")
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
        return checkGuaranteedOledConditions(hasFingerprint, hasUdfps, wide, hdr?.desiredMinLuminance, hdr?.supportedHdrTypes?.size ?: 0, doze || configDoze)
    }
}
