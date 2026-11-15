package com.witherboymcw.superbattery.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * Single Source of Truth for all application settings and configuration preferences.
 * Centralizes SharedPreferences storage, keys, defaults, and type-safe getters/setters.
 */
object AppPreferences {

    const val PREFS_NAME = "dashboard_settings"

    // Preference Keys
    const val KEY_APP_LANGUAGE = "app_language"
    const val KEY_APP_THEME = "app_theme"
    const val KEY_ASSUME_OLED = "assume_oled"
    const val KEY_IS_FIRST_LAUNCH = "is_first_launch"
    const val KEY_REFRESH_INTERVAL_MS = "refresh_interval_ms"

    const val KEY_TEMP_UNIT = "temp_unit"
    const val KEY_VOLTAGE_UNIT = "voltage_unit"
    const val KEY_RAM_UNIT = "ram_unit"

    const val KEY_USE_SYSTEM_BATTERY_COUNTER = "use_system_battery_counter"
    const val KEY_IS_DUAL_CELL_BATTERY = "is_dual_cell_battery"

    const val KEY_THICK_CHIN_LEVEL = "thick_chin_level"

    const val KEY_NOTCH_CUSTOM_MODE = "notch_custom_mode"
    const val KEY_NOTCH_IS_CENTER = "notch_is_center"
    const val KEY_NOTCH_RADIUS = "notch_radius"
    const val KEY_NOTCH_PILL_WIDTH = "notch_pill_width"
    const val KEY_NOTCH_X = "notch_x"
    const val KEY_NOTCH_Y = "notch_y"

    const val KEY_ENABLE_POST_NOTIFICATIONS = "enable_post_notifications"
    const val KEY_ENABLE_NOTIFICATION_ACCESS = "enable_notification_access"
    const val KEY_ENABLE_PHONE_STATE = "enable_phone_state"
    const val KEY_ENABLE_OVERLAY = "enable_overlay"

    // Default Values
    const val DEFAULT_LANGUAGE = "system"
    const val DEFAULT_THEME = "system"
    const val DEFAULT_ASSUME_OLED = "none"
    const val DEFAULT_REFRESH_INTERVAL_MS = 1000L
    const val DEFAULT_TEMP_UNIT = "celsius"
    const val DEFAULT_VOLTAGE_UNIT = "mv"
    const val DEFAULT_RAM_UNIT = "mb"

    fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // --- Language ---
    fun getAppLanguage(context: Context): String {
        return getPreferences(context).getString(KEY_APP_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
    }

    fun setAppLanguage(context: Context, language: String) {
        getPreferences(context).edit().putString(KEY_APP_LANGUAGE, language).apply()
    }

    // --- Theme ---
    fun getAppTheme(context: Context): String {
        return getPreferences(context).getString(KEY_APP_THEME, DEFAULT_THEME) ?: DEFAULT_THEME
    }

    fun setAppTheme(context: Context, theme: String) {
        getPreferences(context).edit().putString(KEY_APP_THEME, theme).apply()
    }

    // --- Assume OLED ---
    fun getAssumeOled(context: Context): String {
        return getPreferences(context).getString(KEY_ASSUME_OLED, DEFAULT_ASSUME_OLED) ?: DEFAULT_ASSUME_OLED
    }

    fun setAssumeOled(context: Context, mode: String) {
        getPreferences(context).edit().putString(KEY_ASSUME_OLED, mode).apply()
    }

    // --- First Launch ---
    fun isFirstLaunch(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_IS_FIRST_LAUNCH, true)
    }

    fun setFirstLaunch(context: Context, isFirstLaunch: Boolean) {
        getPreferences(context).edit().putBoolean(KEY_IS_FIRST_LAUNCH, isFirstLaunch).apply()
    }

    // --- Refresh Interval ---
    fun getRefreshIntervalMs(context: Context): Long {
        return getPreferences(context).getLong(KEY_REFRESH_INTERVAL_MS, DEFAULT_REFRESH_INTERVAL_MS)
    }

    fun setRefreshIntervalMs(context: Context, intervalMs: Long) {
        getPreferences(context).edit().putLong(KEY_REFRESH_INTERVAL_MS, intervalMs).apply()
    }

    // --- Units ---
    fun getTempUnit(context: Context): String {
        return getPreferences(context).getString(KEY_TEMP_UNIT, DEFAULT_TEMP_UNIT) ?: DEFAULT_TEMP_UNIT
    }

    fun setTempUnit(context: Context, unit: String) {
        getPreferences(context).edit().putString(KEY_TEMP_UNIT, unit).apply()
    }

    fun getVoltageUnit(context: Context): String {
        return getPreferences(context).getString(KEY_VOLTAGE_UNIT, DEFAULT_VOLTAGE_UNIT) ?: DEFAULT_VOLTAGE_UNIT
    }

    fun setVoltageUnit(context: Context, unit: String) {
        getPreferences(context).edit().putString(KEY_VOLTAGE_UNIT, unit).apply()
    }

    fun getRamUnit(context: Context): String {
        return getPreferences(context).getString(KEY_RAM_UNIT, DEFAULT_RAM_UNIT) ?: DEFAULT_RAM_UNIT
    }

    fun setRamUnit(context: Context, unit: String) {
        getPreferences(context).edit().putString(KEY_RAM_UNIT, unit).apply()
    }

    // --- Hardware Preferences ---
    fun isUseSystemBatteryCounter(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_USE_SYSTEM_BATTERY_COUNTER, true)
    }

    fun setUseSystemBatteryCounter(context: Context, useSystem: Boolean) {
        getPreferences(context).edit().putBoolean(KEY_USE_SYSTEM_BATTERY_COUNTER, useSystem).apply()
    }

    fun isDualCellBattery(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_IS_DUAL_CELL_BATTERY, false)
    }

    fun setDualCellBattery(context: Context, isDualCell: Boolean) {
        getPreferences(context).edit().putBoolean(KEY_IS_DUAL_CELL_BATTERY, isDualCell).apply()
    }

    // --- Thick Chin Level ---
    fun getThickChinLevel(context: Context): Int {
        return getPreferences(context).getInt(KEY_THICK_CHIN_LEVEL, 0)
    }

    fun setThickChinLevel(context: Context, level: Int) {
        getPreferences(context).edit().putInt(KEY_THICK_CHIN_LEVEL, level).apply()
    }

    // --- Notch Settings ---
    fun getNotchCustomMode(context: Context, defaultMode: Int): Int {
        return getPreferences(context).getInt(KEY_NOTCH_CUSTOM_MODE, defaultMode)
    }

    fun setNotchCustomMode(context: Context, mode: Int) {
        getPreferences(context).edit().putInt(KEY_NOTCH_CUSTOM_MODE, mode).apply()
    }

    fun isNotchCenter(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_NOTCH_IS_CENTER, true)
    }

    fun setNotchCenter(context: Context, isCenter: Boolean) {
        getPreferences(context).edit().putBoolean(KEY_NOTCH_IS_CENTER, isCenter).apply()
    }

    fun getNotchRadius(context: Context, defaultRadius: Float): Float {
        return getPreferences(context).getFloat(KEY_NOTCH_RADIUS, defaultRadius)
    }

    fun setNotchRadius(context: Context, radius: Float) {
        getPreferences(context).edit().putFloat(KEY_NOTCH_RADIUS, radius).apply()
    }

    fun getNotchPillWidth(context: Context, defaultWidth: Float): Float {
        return getPreferences(context).getFloat(KEY_NOTCH_PILL_WIDTH, defaultWidth)
    }

    fun setNotchPillWidth(context: Context, width: Float) {
        getPreferences(context).edit().putFloat(KEY_NOTCH_PILL_WIDTH, width).apply()
    }

    fun getNotchX(context: Context): Float {
        return getPreferences(context).getFloat(KEY_NOTCH_X, -1f)
    }

    fun setNotchX(context: Context, x: Float) {
        getPreferences(context).edit().putFloat(KEY_NOTCH_X, x).apply()
    }

    fun getNotchY(context: Context): Float {
        return getPreferences(context).getFloat(KEY_NOTCH_Y, -1f)
    }

    fun setNotchY(context: Context, y: Float) {
        getPreferences(context).edit().putFloat(KEY_NOTCH_Y, y).apply()
    }

    // --- Permissions Toggles ---
    fun isPostNotificationsEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_ENABLE_POST_NOTIFICATIONS, true)
    }

    fun setPostNotificationsEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit().putBoolean(KEY_ENABLE_POST_NOTIFICATIONS, enabled).apply()
    }

    fun isNotificationAccessEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_ENABLE_NOTIFICATION_ACCESS, true)
    }

    fun setNotificationAccessEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit().putBoolean(KEY_ENABLE_NOTIFICATION_ACCESS, enabled).apply()
    }

    fun isPhoneStateEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_ENABLE_PHONE_STATE, true)
    }

    fun setPhoneStateEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit().putBoolean(KEY_ENABLE_PHONE_STATE, enabled).apply()
    }

    fun isOverlayEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_ENABLE_OVERLAY, true)
    }

    fun setOverlayEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit().putBoolean(KEY_ENABLE_OVERLAY, enabled).apply()
    }
}
