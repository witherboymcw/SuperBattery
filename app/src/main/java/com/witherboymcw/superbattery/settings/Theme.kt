package com.witherboymcw.superbattery.settings

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import com.witherboymcw.superbattery.BatteryWidget
import com.witherboymcw.superbattery.MainActivity
import com.witherboymcw.superbattery.R

class Theme(private val activity: MainActivity) {

    companion object {
        const val KEY_THEME_DROPDOWN_OPEN = "theme_dropdown_open"
        const val APP_THEME_KEY = "app_theme"
    }

    private var headerTheme: View? = null
    private var dropdownTheme: View? = null
    private var arrowTheme: ImageView? = null
    private var selectedThemeText: TextView? = null

    fun bind(savedInstanceState: Bundle?) {
        headerTheme = activity.findViewById(R.id.header_theme)
        dropdownTheme = activity.findViewById(R.id.dropdown_theme)
        arrowTheme = activity.findViewById(R.id.arrow_theme)
        selectedThemeText = activity.findViewById(R.id.selected_theme_text)

        headerTheme?.setOnClickListener {
            val isExpanded = dropdownTheme?.visibility == View.VISIBLE
            dropdownTheme?.visibility = if (isExpanded) View.GONE else View.VISIBLE
            arrowTheme?.animate()?.rotation(if (isExpanded) 0f else 180f)?.setDuration(200L)?.start()
        }

        if (savedInstanceState?.getBoolean(KEY_THEME_DROPDOWN_OPEN) == true) {
            dropdownTheme?.visibility = View.VISIBLE
            arrowTheme?.rotation = 180f
        }

        val themeGroup = activity.findViewById<RadioGroup>(R.id.theme_radio_group)
        val prefs = activity.getSharedPreferences(BatteryWidget.SETTINGS_PREFS, android.content.Context.MODE_PRIVATE)
        val currentThemeMode = prefs.getString(APP_THEME_KEY, "system") ?: "system"

        when (currentThemeMode) {
            "light" -> {
                themeGroup?.check(R.id.theme_light)
                selectedThemeText?.text = activity.getString(R.string.theme_light)
            }
            "dark" -> {
                themeGroup?.check(R.id.theme_dark)
                selectedThemeText?.text = activity.getString(R.string.theme_dark)
            }
            "oled" -> {
                themeGroup?.check(R.id.theme_oled)
                selectedThemeText?.text = activity.getString(R.string.theme_oled)
            }
            else -> {
                themeGroup?.check(R.id.theme_system)
                selectedThemeText?.text = activity.getString(R.string.at_system)
            }
        }

        themeGroup?.setOnCheckedChangeListener { _, checkedId ->
            val targetMode = when (checkedId) {
                R.id.theme_light -> "light"
                R.id.theme_dark -> "dark"
                R.id.theme_oled -> "oled"
                else -> "system"
            }
            selectedThemeText?.text = when (targetMode) {
                "light" -> activity.getString(R.string.theme_light)
                "dark" -> activity.getString(R.string.theme_dark)
                "oled" -> activity.getString(R.string.theme_oled)
                else -> activity.getString(R.string.at_system)
            }

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
