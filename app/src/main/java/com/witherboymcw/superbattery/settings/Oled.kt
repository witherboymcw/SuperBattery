package com.witherboymcw.superbattery.settings

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.TextView
import com.witherboymcw.superbattery.MainActivity
import com.witherboymcw.superbattery.OledThemeAllInOne
import com.witherboymcw.superbattery.R
import com.witherboymcw.superbattery.templates.OptionItem
import com.witherboymcw.superbattery.templates.SimpleOptionCard
import com.witherboymcw.superbattery.widgets.BatteryWidget

class Oled(private val activity: MainActivity) {

    companion object {
        const val KEY_ASSUME_OLED_DROPDOWN_OPEN = OledThemeAllInOne.KEY_ASSUME_OLED_DROPDOWN_OPEN
    }

    fun bind(savedInstanceState: Bundle?) {
        val headerAssumeOled = activity.findViewById<View>(R.id.header_assume_oled)
        val dropdownAssumeOled = activity.findViewById<View>(R.id.dropdown_assume_oled)
        val arrowAssumeOled = activity.findViewById<ImageView>(R.id.arrow_assume_oled)
        val selectedAssumeOledText = activity.findViewById<TextView>(R.id.selected_assume_oled_text)
        val assumeOledGroup = activity.findViewById<RadioGroup>(R.id.assume_oled_radio_group)

        val prefs = activity.getSharedPreferences(BatteryWidget.SETTINGS_PREFS, Context.MODE_PRIVATE)
        val currentAssumeOled = prefs.getString(OledThemeAllInOne.ASSUME_OLED_KEY, OledThemeAllInOne.OPTION_NONE) ?: OledThemeAllInOne.OPTION_NONE

        val options = listOf(
            OptionItem(R.id.assume_oled_none, OledThemeAllInOne.OPTION_NONE, activity.getString(R.string.assume_oled_opt_none)),
            OptionItem(R.id.assume_oled_force_oled, OledThemeAllInOne.OPTION_OLED, activity.getString(R.string.assume_oled_opt_oled)),
            OptionItem(R.id.assume_oled_not_oled, OledThemeAllInOne.OPTION_NOT_OLED, activity.getString(R.string.assume_oled_opt_not_oled))
        )

        SimpleOptionCard.bind(
            headerView = headerAssumeOled,
            dropdownView = dropdownAssumeOled,
            arrowView = arrowAssumeOled,
            selectedTextView = selectedAssumeOledText,
            radioGroup = assumeOledGroup,
            savedInstanceStateKey = KEY_ASSUME_OLED_DROPDOWN_OPEN,
            savedInstanceState = savedInstanceState,
            currentValue = currentAssumeOled,
            options = options
        ) { targetValue ->
            val activeValue = prefs.getString(OledThemeAllInOne.ASSUME_OLED_KEY, OledThemeAllInOne.OPTION_NONE) ?: OledThemeAllInOne.OPTION_NONE
            if (activeValue != targetValue) {
                prefs.edit().putString(OledThemeAllInOne.ASSUME_OLED_KEY, targetValue).apply()
                activity.recreate()
            }
        }
    }
}
