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

class UnitRAM(private val activity: MainActivity) {

    companion object {
        const val KEY_RAM_UNIT_DROPDOWN_OPEN = "ram_unit_dropdown_open"
    }

    private var headerRamUnit: View? = null
    private var dropdownRamUnit: View? = null
    private var arrowRamUnit: ImageView? = null
    private var selectedRamUnitText: TextView? = null

    fun bind(savedInstanceState: Bundle?, onUnitChanged: () -> Unit) {
        headerRamUnit = activity.findViewById(R.id.header_ram_unit)
        dropdownRamUnit = activity.findViewById(R.id.dropdown_ram_unit)
        arrowRamUnit = activity.findViewById(R.id.arrow_ram_unit)
        selectedRamUnitText = activity.findViewById(R.id.selected_ram_unit_text)

        headerRamUnit?.setOnClickListener {
            val isExpanded = dropdownRamUnit?.visibility == View.VISIBLE
            dropdownRamUnit?.visibility = if (isExpanded) View.GONE else View.VISIBLE
            arrowRamUnit?.animate()?.rotation(if (isExpanded) 0f else 180f)?.setDuration(200L)?.start()
        }

        if (savedInstanceState?.getBoolean(KEY_RAM_UNIT_DROPDOWN_OPEN) == true) {
            dropdownRamUnit?.visibility = View.VISIBLE
            arrowRamUnit?.rotation = 180f
        }

        val ramGroup = activity.findViewById<RadioGroup>(R.id.ram_unit_radio_group)
        val prefs = activity.getSharedPreferences(BatteryWidget.SETTINGS_PREFS, Context.MODE_PRIVATE)
        val currentUnit = prefs.getString(BatteryWidget.RAM_UNIT_KEY, "mb") ?: "mb"

        if (currentUnit == "gb") {
            ramGroup?.check(R.id.ram_unit_gb)
            selectedRamUnitText?.text = activity.getString(R.string.ram_unit_gb)
        } else {
            ramGroup?.check(R.id.ram_unit_mb)
            selectedRamUnitText?.text = activity.getString(R.string.ram_unit_mb)
        }

        ramGroup?.setOnCheckedChangeListener { _, checkedId ->
            val code = if (checkedId == R.id.ram_unit_gb) "gb" else "mb"
            selectedRamUnitText?.text = if (code == "gb") {
                activity.getString(R.string.ram_unit_gb)
            } else {
                activity.getString(R.string.ram_unit_mb)
            }
            prefs.edit().putString(BatteryWidget.RAM_UNIT_KEY, code).apply()
            onUnitChanged()
        }
    }
}
