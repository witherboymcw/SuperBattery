package com.witherboymcw.superbattery.settings

import android.content.Context
import android.view.View
import com.google.android.material.checkbox.MaterialCheckBox
import com.witherboymcw.superbattery.BatteryWidget
import com.witherboymcw.superbattery.MainActivity
import com.witherboymcw.superbattery.R

class UseSysBatt(private val activity: MainActivity) {

    fun bind(onSystemBatteryToggled: (Boolean) -> Unit) {
        val cardSystemBattery = activity.findViewById<View>(R.id.card_use_system_battery)
        val checkboxSystemBattery = activity.findViewById<MaterialCheckBox>(R.id.checkbox_use_system_battery)

        val isUseSystemBattery = activity.getSharedPreferences(BatteryWidget.SETTINGS_PREFS, Context.MODE_PRIVATE)
            .getBoolean(BatteryWidget.USE_SYSTEM_BATTERY_KEY, false)
        checkboxSystemBattery?.isChecked = isUseSystemBattery

        cardSystemBattery?.setOnClickListener {
            val nextValue = !(checkboxSystemBattery?.isChecked ?: false)
            checkboxSystemBattery?.isChecked = nextValue
            activity.getSharedPreferences(BatteryWidget.SETTINGS_PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(BatteryWidget.USE_SYSTEM_BATTERY_KEY, nextValue)
                .apply()
            onSystemBatteryToggled(nextValue)
        }

        checkboxSystemBattery?.setOnCheckedChangeListener { _, isChecked ->
            activity.getSharedPreferences(BatteryWidget.SETTINGS_PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(BatteryWidget.USE_SYSTEM_BATTERY_KEY, isChecked)
                .apply()
            onSystemBatteryToggled(isChecked)
        }
    }
}
