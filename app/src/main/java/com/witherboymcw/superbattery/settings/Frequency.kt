package com.witherboymcw.superbattery.settings

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.TextView
import com.witherboymcw.superbattery.MainActivity
import com.witherboymcw.superbattery.R
import com.witherboymcw.superbattery.screens.settings.SettingsViewController
import com.witherboymcw.superbattery.templates.OptionItem
import com.witherboymcw.superbattery.templates.SimpleOptionCard
import com.witherboymcw.superbattery.widgets.BatteryWidget

class Frequency(private val activity: MainActivity) {

    companion object {
        const val KEY_FREQUENCY_DROPDOWN_OPEN = "frequency_dropdown_open"
    }

    fun bind(
        savedInstanceState: Bundle?,
        currentIntervalMs: Long,
        onIntervalChanged: (Long) -> Unit
    ) {
        val headerFrequency = activity.findViewById<View>(R.id.header_update_frequency)
        val dropdownFrequency = activity.findViewById<View>(R.id.dropdown_update_frequency)
        val arrowFrequency = activity.findViewById<ImageView>(R.id.arrow_update_frequency)
        val selectedFrequencyText = activity.findViewById<TextView>(R.id.selected_frequency_text)
        val freqGroup = activity.findViewById<RadioGroup>(R.id.frequency_radio_group)

        val options = listOf(
            OptionItem(R.id.freq_500, 500L, activity.getString(R.string.update_every_half_second)),
            OptionItem(R.id.freq_1000, 1_000L, activity.getString(R.string.update_every_second)),
            OptionItem(R.id.freq_2000, 2_000L, activity.getString(R.string.update_every_two_seconds))
        )

        SimpleOptionCard.bind(
            headerView = headerFrequency,
            dropdownView = dropdownFrequency,
            arrowView = arrowFrequency,
            selectedTextView = selectedFrequencyText,
            radioGroup = freqGroup,
            savedInstanceStateKey = KEY_FREQUENCY_DROPDOWN_OPEN,
            savedInstanceState = savedInstanceState,
            currentValue = currentIntervalMs,
            options = options
        ) { selectedMs ->
            onIntervalChanged(selectedMs)
            activity.getSharedPreferences(BatteryWidget.SETTINGS_PREFS, Context.MODE_PRIVATE).edit()
                .putLong(SettingsViewController.REFRESH_INTERVAL_KEY, selectedMs)
                .apply()
        }
    }
}
