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

class UnitVOLT(private val activity: MainActivity) {

    companion object {
        const val KEY_VOLTAGE_UNIT_DROPDOWN_OPEN = "voltage_unit_dropdown_open"
    }

    fun bind(savedInstanceState: Bundle?, onUnitChanged: () -> Unit) {
        val headerVoltageUnit = activity.findViewById<View>(R.id.header_voltage_unit)
        val dropdownVoltageUnit = activity.findViewById<View>(R.id.dropdown_voltage_unit)
        val arrowVoltageUnit = activity.findViewById<ImageView>(R.id.arrow_voltage_unit)
        val selectedVoltageUnitText = activity.findViewById<TextView>(R.id.selected_voltage_unit_text)
        val voltageGroup = activity.findViewById<RadioGroup>(R.id.voltage_unit_radio_group)

        val prefs = activity.getSharedPreferences(BatteryWidget.SETTINGS_PREFS, Context.MODE_PRIVATE)
        val currentUnit = prefs.getString(BatteryWidget.VOLTAGE_UNIT_KEY, "mv") ?: "mv"

        val options = listOf(
            OptionItem(R.id.voltage_unit_mv, "mv", activity.getString(R.string.voltage_unit_mv)),
            OptionItem(R.id.voltage_unit_v, "v", activity.getString(R.string.voltage_unit_v))
        )

        SimpleOptionCard.bind(
            headerView = headerVoltageUnit,
            dropdownView = dropdownVoltageUnit,
            arrowView = arrowVoltageUnit,
            selectedTextView = selectedVoltageUnitText,
            radioGroup = voltageGroup,
            savedInstanceStateKey = KEY_VOLTAGE_UNIT_DROPDOWN_OPEN,
            savedInstanceState = savedInstanceState,
            currentValue = currentUnit,
            options = options
        ) { code ->
            prefs.edit().putString(BatteryWidget.VOLTAGE_UNIT_KEY, code).apply()
            onUnitChanged()
        }
    }
}
