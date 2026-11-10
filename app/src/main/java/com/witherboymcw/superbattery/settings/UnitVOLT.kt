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

class UnitVOLT(private val activity: MainActivity) {

    companion object {
        const val KEY_VOLTAGE_UNIT_DROPDOWN_OPEN = "voltage_unit_dropdown_open"
    }

    private var headerVoltageUnit: View? = null
    private var dropdownVoltageUnit: View? = null
    private var arrowVoltageUnit: ImageView? = null
    private var selectedVoltageUnitText: TextView? = null

    fun bind(savedInstanceState: Bundle?, onUnitChanged: () -> Unit) {
        headerVoltageUnit = activity.findViewById(R.id.header_voltage_unit)
        dropdownVoltageUnit = activity.findViewById(R.id.dropdown_voltage_unit)
        arrowVoltageUnit = activity.findViewById(R.id.arrow_voltage_unit)
        selectedVoltageUnitText = activity.findViewById(R.id.selected_voltage_unit_text)

        headerVoltageUnit?.setOnClickListener {
            val isExpanded = dropdownVoltageUnit?.visibility == View.VISIBLE
            dropdownVoltageUnit?.visibility = if (isExpanded) View.GONE else View.VISIBLE
            arrowVoltageUnit?.animate()?.rotation(if (isExpanded) 0f else 180f)?.setDuration(200L)?.start()
        }

        if (savedInstanceState?.getBoolean(KEY_VOLTAGE_UNIT_DROPDOWN_OPEN) == true) {
            dropdownVoltageUnit?.visibility = View.VISIBLE
            arrowVoltageUnit?.rotation = 180f
        }

        val voltageGroup = activity.findViewById<RadioGroup>(R.id.voltage_unit_radio_group)
        val prefs = activity.getSharedPreferences(BatteryWidget.SETTINGS_PREFS, Context.MODE_PRIVATE)
        val currentUnit = prefs.getString(BatteryWidget.VOLTAGE_UNIT_KEY, "mv") ?: "mv"

        if (currentUnit == "v") {
            voltageGroup?.check(R.id.voltage_unit_v)
            selectedVoltageUnitText?.text = activity.getString(R.string.voltage_unit_v)
        } else {
            voltageGroup?.check(R.id.voltage_unit_mv)
            selectedVoltageUnitText?.text = activity.getString(R.string.voltage_unit_mv)
        }

        voltageGroup?.setOnCheckedChangeListener { _, checkedId ->
            val code = if (checkedId == R.id.voltage_unit_v) "v" else "mv"
            selectedVoltageUnitText?.text = if (code == "v") {
                activity.getString(R.string.voltage_unit_v)
            } else {
                activity.getString(R.string.voltage_unit_mv)
            }
            prefs.edit().putString(BatteryWidget.VOLTAGE_UNIT_KEY, code).apply()
            onUnitChanged()
        }
    }
}
