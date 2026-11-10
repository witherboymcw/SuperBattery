package com.witherboymcw.superbattery.settings

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.TextView
import com.witherboymcw.superbattery.BatteryWidget
import com.witherboymcw.superbattery.MainActivity
import com.witherboymcw.superbattery.OledThemeAllInOne
import com.witherboymcw.superbattery.R

class Oled(private val activity: MainActivity) {

    companion object {
        const val KEY_ASSUME_OLED_DROPDOWN_OPEN = OledThemeAllInOne.KEY_ASSUME_OLED_DROPDOWN_OPEN
    }

    private var headerAssumeOled: View? = null
    private var dropdownAssumeOled: View? = null
    private var arrowAssumeOled: ImageView? = null
    private var selectedAssumeOledText: TextView? = null

    fun bind(savedInstanceState: Bundle?) {
        headerAssumeOled = activity.findViewById(R.id.header_assume_oled)
        dropdownAssumeOled = activity.findViewById(R.id.dropdown_assume_oled)
        arrowAssumeOled = activity.findViewById(R.id.arrow_assume_oled)
        selectedAssumeOledText = activity.findViewById(R.id.selected_assume_oled_text)

        headerAssumeOled?.setOnClickListener {
            val isExpanded = dropdownAssumeOled?.visibility == View.VISIBLE
            dropdownAssumeOled?.visibility = if (isExpanded) View.GONE else View.VISIBLE
            arrowAssumeOled?.animate()?.rotation(if (isExpanded) 0f else 180f)?.setDuration(200L)?.start()
        }

        if (savedInstanceState?.getBoolean(KEY_ASSUME_OLED_DROPDOWN_OPEN) == true) {
            dropdownAssumeOled?.visibility = View.VISIBLE
            arrowAssumeOled?.rotation = 180f
        }

        val assumeOledGroup = activity.findViewById<RadioGroup>(R.id.assume_oled_radio_group)
        val prefs = activity.getSharedPreferences(BatteryWidget.SETTINGS_PREFS, Context.MODE_PRIVATE)
        val currentAssumeOled = prefs.getString(OledThemeAllInOne.ASSUME_OLED_KEY, OledThemeAllInOne.OPTION_NONE) ?: OledThemeAllInOne.OPTION_NONE

        when (currentAssumeOled) {
            OledThemeAllInOne.OPTION_OLED -> {
                assumeOledGroup?.check(R.id.assume_oled_force_oled)
                selectedAssumeOledText?.text = activity.getString(R.string.assume_oled_opt_oled)
            }
            OledThemeAllInOne.OPTION_NOT_OLED -> {
                assumeOledGroup?.check(R.id.assume_oled_not_oled)
                selectedAssumeOledText?.text = activity.getString(R.string.assume_oled_opt_not_oled)
            }
            else -> {
                assumeOledGroup?.check(R.id.assume_oled_none)
                selectedAssumeOledText?.text = activity.getString(R.string.assume_oled_opt_none)
            }
        }

        assumeOledGroup?.setOnCheckedChangeListener { _, checkedId ->
            val targetValue = when (checkedId) {
                R.id.assume_oled_force_oled -> OledThemeAllInOne.OPTION_OLED
                R.id.assume_oled_not_oled -> OledThemeAllInOne.OPTION_NOT_OLED
                else -> OledThemeAllInOne.OPTION_NONE
            }
            val textRes = when (targetValue) {
                OledThemeAllInOne.OPTION_OLED -> R.string.assume_oled_opt_oled
                OledThemeAllInOne.OPTION_NOT_OLED -> R.string.assume_oled_opt_not_oled
                else -> R.string.assume_oled_opt_none
            }
            selectedAssumeOledText?.text = activity.getString(textRes)

            val activeValue = prefs.getString(OledThemeAllInOne.ASSUME_OLED_KEY, OledThemeAllInOne.OPTION_NONE) ?: OledThemeAllInOne.OPTION_NONE
            if (activeValue != targetValue) {
                prefs.edit().putString(OledThemeAllInOne.ASSUME_OLED_KEY, targetValue).apply()
                activity.recreate()
            }
        }
    }
}
