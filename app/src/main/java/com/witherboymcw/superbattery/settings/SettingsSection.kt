package com.witherboymcw.superbattery.settings

import android.content.Context
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import com.witherboymcw.superbattery.R

enum class SettingsSection(
    @IdRes val headerId: Int,
    @IdRes val dropdownId: Int,
    @IdRes val arrowId: Int,
    @StringRes val titleRes: Int,
    val stateKey: String
) {
    LANGUAGE(R.id.header_language, R.id.dropdown_language, R.id.arrow_language, R.string.language, "language_dropdown_open"),
    PERMISSIONS(R.id.header_super_permissions, R.id.dropdown_super_permissions, R.id.arrow_super_permissions, R.string.super_permissions_header, "super_permissions_dropdown_open"),
    FREQUENCY(R.id.header_update_frequency, R.id.dropdown_update_frequency, R.id.arrow_update_frequency, R.string.update_frequency, "frequency_dropdown_open"),
    THEME(R.id.header_theme, R.id.dropdown_theme, R.id.arrow_theme, R.string.theme_header, "theme_dropdown_open"),
    HARDWARE(R.id.header_hardware_preferences, R.id.dropdown_hardware_preferences, R.id.arrow_hardware_preferences, R.string.hardware_preferences, "hardware_preferences_dropdown_open"),
    VOLTAGE_UNIT(R.id.header_voltage_unit, R.id.dropdown_voltage_unit, R.id.arrow_voltage_unit, R.string.voltage_unit_header, "voltage_unit_dropdown_open"),
    RAM_UNIT(R.id.header_ram_unit, R.id.dropdown_ram_unit, R.id.arrow_ram_unit, R.string.ram_unit_header, "ram_unit_dropdown_open"),
    TEMP_UNIT(R.id.header_temp_unit, R.id.dropdown_temp_unit, R.id.arrow_temp_unit, R.string.temp_unit_header, "temp_unit_dropdown_open"),
    THICK_CHIN(R.id.header_thick_chin, R.id.dropdown_thick_chin, R.id.arrow_thick_chin, R.string.thick_chin_header, "key_thick_chin_dropdown_open"),
    NOTCH(R.id.header_notch, R.id.dropdown_notch, R.id.arrow_notch, R.string.notch_header, "key_notch_dropdown_open"),
    ASSUME_OLED(R.id.header_assume_oled, R.id.dropdown_assume_oled, R.id.arrow_assume_oled, R.string.assume_oled_header, "key_assume_oled_dropdown_open")
}

data class OptionSpec<T>(
    @IdRes val radioId: Int,
    val value: T,
    @StringRes val labelRes: Int
)

class OptionCardSpec<T>(
    val section: SettingsSection,
    @IdRes val radioGroupId: Int,
    @IdRes val selectedTextId: Int,
    val options: List<OptionSpec<T>>,
    val read: (Context) -> T,
    val write: (Context, T) -> Unit
)
