package com.witherboymcw.superbattery.settings

import android.content.Context
import android.view.View
import com.google.android.material.checkbox.MaterialCheckBox
import com.witherboymcw.superbattery.MainActivity
import com.witherboymcw.superbattery.R
import com.witherboymcw.superbattery.templates.SimpleCheckboxCard
import com.witherboymcw.superbattery.widgets.BatteryWidget

class UseSysBatt(private val activity: MainActivity) {

    fun bind(onSystemBatteryToggled: (Boolean) -> Unit) {
        val cardSystemBattery = activity.findViewById<View>(R.id.card_use_system_battery)
        val checkboxSystemBattery = activity.findViewById<MaterialCheckBox>(R.id.checkbox_use_system_battery)

        val isUseSystemBattery = activity.getSharedPreferences(BatteryWidget.SETTINGS_PREFS, Context.MODE_PRIVATE)
            .getBoolean(BatteryWidget.USE_SYSTEM_BATTERY_KEY, false)

        SimpleCheckboxCard.bind(cardSystemBattery, checkboxSystemBattery, isUseSystemBattery) { isChecked ->
            activity.getSharedPreferences(BatteryWidget.SETTINGS_PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(BatteryWidget.USE_SYSTEM_BATTERY_KEY, isChecked)
                .apply()
            onSystemBatteryToggled(isChecked)
        }
    }
}
