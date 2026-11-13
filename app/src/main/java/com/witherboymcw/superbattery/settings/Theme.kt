package com.witherboymcw.superbattery.settings

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import com.witherboymcw.superbattery.MainActivity
import com.witherboymcw.superbattery.R
import com.witherboymcw.superbattery.templates.OptionItem
import com.witherboymcw.superbattery.templates.SimpleOptionCard
import com.witherboymcw.superbattery.widgets.BatteryWidget

class Theme(private val activity: MainActivity) {

    companion object {
        const val KEY_THEME_DROPDOWN_OPEN = "theme_dropdown_open"
        const val APP_THEME_KEY = "app_theme"
    }

    fun bind(savedInstanceState: Bundle?) {
        val headerTheme = activity.findViewById<View>(R.id.header_theme)
        val dropdownTheme = activity.findViewById<View>(R.id.dropdown_theme)
        val arrowTheme = activity.findViewById<ImageView>(R.id.arrow_theme)
        val selectedThemeText = activity.findViewById<TextView>(R.id.selected_theme_text)
        val themeGroup = activity.findViewById<RadioGroup>(R.id.theme_radio_group)

        val prefs = activity.getSharedPreferences(BatteryWidget.SETTINGS_PREFS, Context.MODE_PRIVATE)
        val currentThemeMode = prefs.getString(APP_THEME_KEY, "system") ?: "system"

        val options = listOf(
            OptionItem(R.id.theme_system, "system", activity.getString(R.string.at_system)),
            OptionItem(R.id.theme_light, "light", activity.getString(R.string.theme_light)),
            OptionItem(R.id.theme_dark, "dark", activity.getString(R.string.theme_dark)),
            OptionItem(R.id.theme_oled, "oled", activity.getString(R.string.theme_oled))
        )

        SimpleOptionCard.bind(
            headerView = headerTheme,
            dropdownView = dropdownTheme,
            arrowView = arrowTheme,
            selectedTextView = selectedThemeText,
            radioGroup = themeGroup,
            savedInstanceStateKey = KEY_THEME_DROPDOWN_OPEN,
            savedInstanceState = savedInstanceState,
            currentValue = currentThemeMode,
            options = options
        ) { targetMode ->
            val activeTheme = prefs.getString(APP_THEME_KEY, "system") ?: "system"
            if (activeTheme != targetMode) {
                prefs.edit().putString(APP_THEME_KEY, targetMode).apply()
                val mode = when (targetMode) {
                    "light" -> AppCompatDelegate.MODE_NIGHT_NO
                    "dark", "oled" -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                AppCompatDelegate.setDefaultNightMode(mode)
                activity.recreate()
            }
        }
    }
}
