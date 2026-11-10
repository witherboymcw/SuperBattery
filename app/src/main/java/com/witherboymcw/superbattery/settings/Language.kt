package com.witherboymcw.superbattery.settings

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.TextView
import com.witherboymcw.superbattery.BatteryWidget
import com.witherboymcw.superbattery.MainActivity
import com.witherboymcw.superbattery.R

class Language(private val activity: MainActivity) {

    companion object {
        const val KEY_LANGUAGE_DROPDOWN_OPEN = "language_dropdown_open"
    }

    private var headerLanguage: View? = null
    private var dropdownLanguage: View? = null
    private var arrowLanguage: ImageView? = null
    private var selectedLanguageText: TextView? = null

    fun bind(savedInstanceState: Bundle?, onLanguageChanged: (String) -> Unit) {
        headerLanguage = activity.findViewById(R.id.header_language)
        dropdownLanguage = activity.findViewById(R.id.dropdown_language)
        arrowLanguage = activity.findViewById(R.id.arrow_language)
        selectedLanguageText = activity.findViewById(R.id.selected_language_text)

        headerLanguage?.setOnClickListener {
            val isExpanded = dropdownLanguage?.visibility == View.VISIBLE
            dropdownLanguage?.visibility = if (isExpanded) View.GONE else View.VISIBLE
            arrowLanguage?.animate()?.rotation(if (isExpanded) 0f else 180f)?.setDuration(200L)?.start()
        }

        if (savedInstanceState?.getBoolean(KEY_LANGUAGE_DROPDOWN_OPEN) == true) {
            dropdownLanguage?.visibility = View.VISIBLE
            arrowLanguage?.rotation = 180f
        }

        val langGroup = activity.findViewById<RadioGroup>(R.id.language_radio_group)
        val savedLang = activity.getSharedPreferences(BatteryWidget.SETTINGS_PREFS, android.content.Context.MODE_PRIVATE)
            .getString(BatteryWidget.APP_LANGUAGE_KEY, "system") ?: "system"

        when (savedLang) {
            "vi" -> {
                langGroup?.check(R.id.lang_vietnamese)
                selectedLanguageText?.text = activity.getString(R.string.language_vietnamese)
            }
            "en" -> {
                langGroup?.check(R.id.lang_english)
                selectedLanguageText?.text = activity.getString(R.string.language_english)
            }
            "art", "en-0rot", "en-x-brainrot", "en-brainrot", "en-BR" -> {
                langGroup?.check(R.id.lang_brainrot)
                selectedLanguageText?.text = activity.getString(R.string.language_brainrot)
            }
            else -> {
                langGroup?.check(R.id.lang_system)
                selectedLanguageText?.text = activity.getString(R.string.at_system)
            }
        }

        langGroup?.setOnCheckedChangeListener { _, checkedId ->
            val targetLang = when (checkedId) {
                R.id.lang_vietnamese -> "vi"
                R.id.lang_english -> "en"
                R.id.lang_brainrot -> "art"
                else -> "system"
            }
            selectedLanguageText?.text = when (targetLang) {
                "vi" -> activity.getString(R.string.language_vietnamese)
                "en" -> activity.getString(R.string.language_english)
                "art" -> activity.getString(R.string.language_brainrot)
                else -> activity.getString(R.string.at_system)
            }
            onLanguageChanged(targetLang)
        }
    }
}
