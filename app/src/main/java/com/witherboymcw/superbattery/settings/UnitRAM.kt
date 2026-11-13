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

class UnitRAM(private val activity: MainActivity) {

    companion object {
        const val KEY_RAM_UNIT_DROPDOWN_OPEN = "ram_unit_dropdown_open"
    }

    fun bind(savedInstanceState: Bundle?, onUnitChanged: () -> Unit) {
        val headerRamUnit = activity.findViewById<View>(R.id.header_ram_unit)
        val dropdownRamUnit = activity.findViewById<View>(R.id.dropdown_ram_unit)
        val arrowRamUnit = activity.findViewById<ImageView>(R.id.arrow_ram_unit)
        val selectedRamUnitText = activity.findViewById<TextView>(R.id.selected_ram_unit_text)
        val ramGroup = activity.findViewById<RadioGroup>(R.id.ram_unit_radio_group)

        val prefs = activity.getSharedPreferences(BatteryWidget.SETTINGS_PREFS, Context.MODE_PRIVATE)
        val currentUnit = prefs.getString(BatteryWidget.RAM_UNIT_KEY, "mb") ?: "mb"

        val options = listOf(
            OptionItem(R.id.ram_unit_mb, "mb", activity.getString(R.string.ram_unit_mb)),
            OptionItem(R.id.ram_unit_gb, "gb", activity.getString(R.string.ram_unit_gb))
        )

        SimpleOptionCard.bind(
            headerView = headerRamUnit,
            dropdownView = dropdownRamUnit,
            arrowView = arrowRamUnit,
            selectedTextView = selectedRamUnitText,
            radioGroup = ramGroup,
            savedInstanceStateKey = KEY_RAM_UNIT_DROPDOWN_OPEN,
            savedInstanceState = savedInstanceState,
            currentValue = currentUnit,
            options = options
        ) { code ->
            prefs.edit().putString(BatteryWidget.RAM_UNIT_KEY, code).apply()
            onUnitChanged()
        }
    }
}
