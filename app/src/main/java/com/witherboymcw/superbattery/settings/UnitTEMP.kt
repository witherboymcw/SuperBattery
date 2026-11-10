package com.witherboymcw.superbattery.settings

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.TextView
import com.witherboymcw.superbattery.BatteryWidget
import com.witherboymcw.superbattery.MainActivity
import com.witherboymcw.superbattery.R

class UnitTEMP(private val activity: MainActivity) {

    companion object {
        const val KEY_TEMP_UNIT_DROPDOWN_OPEN = "temp_unit_dropdown_open"
    }

    private var headerTempUnit: View? = null
    private var dropdownTempUnit: View? = null
    private var arrowTempUnit: ImageView? = null
    private var selectedTempUnitText: TextView? = null

    fun bind(savedInstanceState: Bundle?, onUnitChanged: () -> Unit) {
        headerTempUnit = activity.findViewById(R.id.header_temp_unit)
        dropdownTempUnit = activity.findViewById(R.id.dropdown_temp_unit)
        arrowTempUnit = activity.findViewById(R.id.arrow_temp_unit)
        selectedTempUnitText = activity.findViewById(R.id.selected_temp_unit_text)

        headerTempUnit?.setOnClickListener {
            val isExpanded = dropdownTempUnit?.visibility == View.VISIBLE
            dropdownTempUnit?.visibility = if (isExpanded) View.GONE else View.VISIBLE
            arrowTempUnit?.animate()?.rotation(if (isExpanded) 0f else 180f)?.setDuration(200L)?.start()
        }

        if (savedInstanceState?.getBoolean(KEY_TEMP_UNIT_DROPDOWN_OPEN) == true) {
            dropdownTempUnit?.visibility = View.VISIBLE
            arrowTempUnit?.rotation = 180f
        }

        val tempGroup = activity.findViewById<RadioGroup>(R.id.temp_unit_radio_group)
        val prefs = activity.getSharedPreferences(BatteryWidget.SETTINGS_PREFS, Context.MODE_PRIVATE)
        val currentUnit = prefs.getString(BatteryWidget.TEMP_UNIT_KEY, "celsius") ?: "celsius"

        when (currentUnit) {
            "fahrenheit" -> {
                tempGroup?.check(R.id.temp_unit_fahrenheit)
                selectedTempUnitText?.text = activity.getString(R.string.temp_unit_fahrenheit)
            }
            "kelvin" -> {
                tempGroup?.check(R.id.temp_unit_kelvin)
                selectedTempUnitText?.text = activity.getString(R.string.temp_unit_kelvin)
            }
            else -> {
                tempGroup?.check(R.id.temp_unit_celsius)
                selectedTempUnitText?.text = activity.getString(R.string.temp_unit_celsius)
            }
        }

        tempGroup?.setOnCheckedChangeListener { _, checkedId ->
            val code = when (checkedId) {
                R.id.temp_unit_fahrenheit -> "fahrenheit"
                R.id.temp_unit_kelvin -> "kelvin"
                else -> "celsius"
            }
            selectedTempUnitText?.text = when (code) {
                "fahrenheit" -> activity.getString(R.string.temp_unit_fahrenheit)
                "kelvin" -> activity.getString(R.string.temp_unit_kelvin)
                else -> activity.getString(R.string.temp_unit_celsius)
            }
            prefs.edit().putString(BatteryWidget.TEMP_UNIT_KEY, code).apply()
            onUnitChanged()
        }
    }
}
