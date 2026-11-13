package com.witherboymcw.superbattery.awareness

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.view.Surface
import android.view.View
import android.view.WindowManager
import com.witherboymcw.superbattery.MainActivity
import com.witherboymcw.superbattery.R
import com.witherboymcw.superbattery.templates.getDisplayRotationCompat
import com.witherboymcw.superbattery.screens.settings.SettingsViewController
import com.witherboymcw.superbattery.widgets.BatteryWidget
import kotlin.math.roundToInt

/**
 * Encapsulated chin padding calculation engine and storage helper for Super Battery.
 * Provides pure padding calculations, stateless SharedPreferences access, and foldable checks.
 */
data class ChinPaddingValues(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

data class ChinPaddingResult(
    val dashboardPadding: ChinPaddingValues,
    val singleScrollViewPadding: ChinPaddingValues,
    val splitLeftScrollViewPadding: ChinPaddingValues,
    val splitRightScrollViewPadding: ChinPaddingValues
)

object ChinAwareness {

    const val KEY_THICK_CHIN_LEVEL = "thick_chin_level"
    const val KEY_THICK_CHIN_DROPDOWN_OPEN = "key_thick_chin_dropdown_open"

    const val MIN_CHIN_LEVEL = 0
    const val MAX_CHIN_LEVEL = 256

    /**
     * Reads the saved thick chin level from SharedPreferences.
     */
    fun getThickChinLevel(context: Context): Int {
        val prefs = context.getSharedPreferences(BatteryWidget.SETTINGS_PREFS, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_THICK_CHIN_LEVEL, 0).coerceIn(MIN_CHIN_LEVEL, MAX_CHIN_LEVEL)
    }

    /**
     * Saves the thick chin level to SharedPreferences if changed.
     */
    fun saveThickChinLevel(context: Context, level: Int) {
        val prefs = context.getSharedPreferences(BatteryWidget.SETTINGS_PREFS, Context.MODE_PRIVATE)
        val coercedLevel = level.coerceIn(MIN_CHIN_LEVEL, MAX_CHIN_LEVEL)
        val saved = prefs.getInt(KEY_THICK_CHIN_LEVEL, -1)
        if (saved != coercedLevel) {
            prefs.edit().putInt(KEY_THICK_CHIN_LEVEL, coercedLevel).apply()
        }
    }

    /**
     * Pure function to calculate dynamic thick chin padding based on rotation, density, level, theme, and foldability.
     */
    fun calculateChinPadding(
        density: Float,
        rotation: Int,
        level: Int,
        isNight: Boolean,
        savedTheme: String = "system",
        isFoldable: Boolean = false
    ): ChinPaddingResult {
        val isLight = savedTheme == "light" || (savedTheme == "system" && !isNight)
        val rawLevel = level.coerceIn(MIN_CHIN_LEVEL, MAX_CHIN_LEVEL)
        val effectiveLevel = if (isLight || isFoldable) 0 else rawLevel
        val reductionDp = effectiveLevel * (1f / 16f)

        val default16Px = (16f * density).roundToInt()
        val default8Px = (8f * density).roundToInt()
        val default12Px = (12f * density).roundToInt()
        val default24Px = (24f * density).roundToInt()

        val reduced12Px = ((12f - reductionDp).coerceAtLeast(0f) * density).roundToInt()
        val reduced16Px = ((16f - reductionDp).coerceAtLeast(0f) * density).roundToInt()
        val reduced24Px = ((24f - reductionDp).coerceAtLeast(8f) * density).roundToInt()

        val dashboardPadding = ChinPaddingValues(
            left = if (rotation == Surface.ROTATION_270) reduced16Px else default16Px,
            top = if (rotation == Surface.ROTATION_180) reduced16Px else default16Px,
            right = if (rotation == Surface.ROTATION_90) reduced16Px else default16Px,
            bottom = if (rotation == Surface.ROTATION_0) reduced16Px else default16Px
        )

        val singleScrollViewPadding = ChinPaddingValues(
            left = if (rotation == Surface.ROTATION_270) reduced16Px else default16Px,
            top = if (rotation == Surface.ROTATION_180) reduced16Px else default16Px,
            right = if (rotation == Surface.ROTATION_90) reduced16Px else default16Px,
            bottom = if (rotation == Surface.ROTATION_0) reduced24Px else default24Px
        )

        val splitLeftScrollViewPadding = ChinPaddingValues(
            left = if (rotation == Surface.ROTATION_270) reduced16Px else default16Px,
            top = if (rotation == Surface.ROTATION_180) reduced12Px else default12Px,
            right = default8Px,
            bottom = if (rotation == Surface.ROTATION_0) reduced24Px else default24Px
        )

        val splitRightScrollViewPadding = ChinPaddingValues(
            left = default8Px,
            top = if (rotation == Surface.ROTATION_180) reduced12Px else default12Px,
            right = if (rotation == Surface.ROTATION_90) reduced16Px else default16Px,
            bottom = if (rotation == Surface.ROTATION_0) reduced24Px else default24Px
        )

        return ChinPaddingResult(
            dashboardPadding = dashboardPadding,
            singleScrollViewPadding = singleScrollViewPadding,
            splitLeftScrollViewPadding = splitLeftScrollViewPadding,
            splitRightScrollViewPadding = splitRightScrollViewPadding
        )
    }

    /**
     * Applies dynamic thick chin padding to Home, Settings, and Hotspot screens of the activity.
     */
    fun applyThickChinPadding(activity: MainActivity, overrideLevel: Int? = activity.thickChinOverrideLevel) {
        val prefs = activity.getSharedPreferences(BatteryWidget.SETTINGS_PREFS, Context.MODE_PRIVATE)

        val savedTheme = prefs.getString(SettingsViewController.APP_THEME_KEY, "system") ?: "system"
        val isNight = (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val isFoldable = FoldableAwareness.isFoldableDevice(activity)
        val density = activity.resources.displayMetrics.density

        val rawLevel = overrideLevel ?: activity.thickChinOverrideLevel ?: getThickChinLevel(activity)

        val rotation = activity.getDisplayRotationCompat()

        val paddingResult = calculateChinPadding(
            density = density,
            rotation = rotation,
            level = rawLevel,
            isNight = isNight,
            savedTheme = savedTheme,
            isFoldable = isFoldable
        )

        // 1. Home Dashboard
        activity.findViewById<View>(R.id.dashboard)?.let { dashboard ->
            val p = paddingResult.dashboardPadding
            dashboard.setPaddingRelative(p.left, p.top, p.right, p.bottom)
        }

        // 2. Settings Screen
        val singleScrollView = activity.findViewById<View>(R.id.settings_scroll_view)
        if (singleScrollView != null) {
            val p = paddingResult.singleScrollViewPadding
            singleScrollView.setPaddingRelative(p.left, p.top, p.right, p.bottom)
        } else {
            val scrollLeft = activity.findViewById<View>(R.id.settings_scroll_left)
            val scrollRight = activity.findViewById<View>(R.id.settings_scroll_right)
            val pLeft = paddingResult.splitLeftScrollViewPadding
            val pRight = paddingResult.splitRightScrollViewPadding
            scrollLeft?.setPaddingRelative(pLeft.left, pLeft.top, pLeft.right, pLeft.bottom)
            scrollRight?.setPaddingRelative(pRight.left, pRight.top, pRight.right, pRight.bottom)
        }

        // 3. Hotspot Screen
        val singleHotspotScrollView = activity.findViewById<View>(R.id.hotspot_scroll_view)
        if (singleHotspotScrollView != null) {
            val p = paddingResult.singleScrollViewPadding
            singleHotspotScrollView.setPaddingRelative(p.left, p.top, p.right, p.bottom)
        } else {
            val scrollLeft = activity.findViewById<View>(R.id.hotspot_scroll_left)
            val scrollRight = activity.findViewById<View>(R.id.hotspot_scroll_right)
            val pLeft = paddingResult.splitLeftScrollViewPadding
            val pRight = paddingResult.splitRightScrollViewPadding
            scrollLeft?.setPaddingRelative(pLeft.left, pLeft.top, pLeft.right, pLeft.bottom)
            scrollRight?.setPaddingRelative(pRight.left, pRight.top, pRight.right, pRight.bottom)
        }
    }

    /**
     * Hides the thick chin setting card if the device is a foldable device.
     */
    fun checkFoldableChinVisibility(activity: MainActivity) {
        if (FoldableAwareness.isFoldableDevice(activity)) {
            activity.findViewById<View>(R.id.card_thick_chin)?.visibility = View.GONE
        }
    }
}
