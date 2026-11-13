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

class Language(private val activity: MainActivity) {

    companion object {
        const val KEY_LANGUAGE_DROPDOWN_OPEN = "language_dropdown_open"
    }

    fun bind(savedInstanceState: Bundle?, onLanguageChanged: (String) -> Unit) {
        val headerLanguage = activity.findViewById<View>(R.id.header_language)
        val dropdownLanguage = activity.findViewById<View>(R.id.dropdown_language)
        val arrowLanguage = activity.findViewById<ImageView>(R.id.arrow_language)
        val selectedLanguageText = activity.findViewById<TextView>(R.id.selected_language_text)
        val langGroup = activity.findViewById<RadioGroup>(R.id.language_radio_group)

        val prefs = activity.getSharedPreferences(BatteryWidget.SETTINGS_PREFS, Context.MODE_PRIVATE)
        val savedLang = prefs.getString(BatteryWidget.APP_LANGUAGE_KEY, "system") ?: "system"
        val currentLang = com.witherboymcw.superbattery.templates.LocaleManager.normalizeLanguageTag(savedLang)

        val options = listOf(
            OptionItem(R.id.lang_system, "system", activity.getString(R.string.at_system)),
            OptionItem(R.id.lang_vietnamese, "vi", activity.getString(R.string.language_vietnamese)),
            OptionItem(R.id.lang_english, "en", activity.getString(R.string.language_english)),
            OptionItem(R.id.lang_brainrot, "art", activity.getString(R.string.language_brainrot))
        )

        SimpleOptionCard.bind(
            headerView = headerLanguage,
            dropdownView = dropdownLanguage,
            arrowView = arrowLanguage,
            selectedTextView = selectedLanguageText,
            radioGroup = langGroup,
            savedInstanceStateKey = KEY_LANGUAGE_DROPDOWN_OPEN,
            savedInstanceState = savedInstanceState,
            currentValue = currentLang,
            options = options
        ) { targetLang ->
            val activeLang = prefs.getString(BatteryWidget.APP_LANGUAGE_KEY, "system") ?: "system"
            val normalizedActiveLang = com.witherboymcw.superbattery.templates.LocaleManager.normalizeLanguageTag(activeLang)
            if (normalizedActiveLang != targetLang) {
                onLanguageChanged(targetLang)
            }
        }
    }
}
