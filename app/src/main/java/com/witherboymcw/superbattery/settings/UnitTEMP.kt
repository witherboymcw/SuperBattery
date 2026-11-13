package com.witherboymcw.superbattery.settings

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.TextView
import com.witherboymcw.superbattery.MainActivity
import com.witherboymcw.superbattery.R
import com.witherboymcw.superbattery.templates.OptionItem
import com.witherboymcw.superbattery.templates.SimpleOptionCard
import com.witherboymcw.superbattery.widgets.BatteryWidget

class UnitTEMP(private val activity: MainActivity) {

    companion object {
        const val KEY_TEMP_UNIT_DROPDOWN_OPEN = "temp_unit_dropdown_open"
    }

    fun bind(savedInstanceState: Bundle?, onUnitChanged: () -> Unit) {
        val headerTempUnit = activity.findViewById<View>(R.id.header_temp_unit)
        val dropdownTempUnit = activity.findViewById<View>(R.id.dropdown_temp_unit)
        val arrowTempUnit = activity.findViewById<ImageView>(R.id.arrow_temp_unit)
        val selectedTempUnitText = activity.findViewById<TextView>(R.id.selected_temp_unit_text)
        val tempGroup = activity.findViewById<RadioGroup>(R.id.temp_unit_radio_group)

        val prefs = activity.getSharedPreferences(BatteryWidget.SETTINGS_PREFS, Context.MODE_PRIVATE)
        val currentUnit = prefs.getString(BatteryWidget.TEMP_UNIT_KEY, "celsius") ?: "celsius"

        val options = listOf(
            OptionItem(R.id.temp_unit_celsius, "celsius", activity.getString(R.string.temp_unit_celsius)),
            OptionItem(R.id.temp_unit_fahrenheit, "fahrenheit", activity.getString(R.string.temp_unit_fahrenheit)),
            OptionItem(R.id.temp_unit_kelvin, "kelvin", activity.getString(R.string.temp_unit_kelvin))
        )

        SimpleOptionCard.bind(
            headerView = headerTempUnit,
            dropdownView = dropdownTempUnit,
            arrowView = arrowTempUnit,
            selectedTextView = selectedTempUnitText,
            radioGroup = tempGroup,
            savedInstanceStateKey = KEY_TEMP_UNIT_DROPDOWN_OPEN,
            savedInstanceState = savedInstanceState,
            currentValue = currentUnit,
            options = options
        ) { code ->
            prefs.edit().putString(BatteryWidget.TEMP_UNIT_KEY, code).apply()
            onUnitChanged()
        }
    }
}
