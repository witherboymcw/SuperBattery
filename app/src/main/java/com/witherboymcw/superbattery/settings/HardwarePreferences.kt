package com.witherboymcw.superbattery.settings

import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import com.witherboymcw.superbattery.MainActivity
import com.witherboymcw.superbattery.R
import com.witherboymcw.superbattery.templates.CheckboxItemSpec
import com.witherboymcw.superbattery.templates.SimpleCheckboxCard

class HardwarePreferences(private val activity: MainActivity) {

    companion object {
        const val KEY_HARDWARE_PREFERENCES_DROPDOWN_OPEN = "hardware_preferences_dropdown_open"
    }

    fun bind(savedInstanceState: Bundle?, onHardwarePrefChanged: () -> Unit) {
        val header = activity.findViewById<View>(R.id.header_hardware_preferences)
        val dropdown = activity.findViewById<View>(R.id.dropdown_hardware_preferences)
        val arrow = activity.findViewById<ImageView>(R.id.arrow_hardware_preferences)
        val summaryText = activity.findViewById<TextView>(R.id.selected_hardware_preferences_text)

        val cbUseSysBatt = activity.findViewById<CheckBox>(R.id.cb_use_system_battery)
        val cbDualCell = activity.findViewById<CheckBox>(R.id.cb_dual_cell_battery)

        val isUseSysBatt = AppPreferences.isUseSystemBatteryCounter(activity)
        val isDualCell = AppPreferences.isDualCellBattery(activity)

        val items = listOf(
            CheckboxItemSpec(
                checkBox = cbUseSysBatt,
                initialChecked = isUseSysBatt,
                onCheckedChange = { isChecked ->
                    AppPreferences.setUseSystemBatteryCounter(activity, isChecked)
                    onHardwarePrefChanged()
                }
            ),
            CheckboxItemSpec(
                checkBox = cbDualCell,
                initialChecked = isDualCell,
                onCheckedChange = { isChecked ->
                    AppPreferences.setDualCellBattery(activity, isChecked)
                    onHardwarePrefChanged()
                }
            )
        )

        SimpleCheckboxCard.bind(
            headerView = header,
            dropdownView = dropdown,
            arrowView = arrow,
            summaryTextView = summaryText,
            savedInstanceStateKey = KEY_HARDWARE_PREFERENCES_DROPDOWN_OPEN,
            savedInstanceState = savedInstanceState,
            items = items
        )
    }
}
