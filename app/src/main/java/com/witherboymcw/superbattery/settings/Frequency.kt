package com.witherboymcw.superbattery.settings

import android.os.Bundle
import android.os.Handler
import android.view.View
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.TextView
import com.witherboymcw.superbattery.BatteryWidget
import com.witherboymcw.superbattery.MainActivity
import com.witherboymcw.superbattery.R
import com.witherboymcw.superbattery.SettingsViewController

class Frequency(private val activity: MainActivity) {

    companion object {
        const val KEY_FREQUENCY_DROPDOWN_OPEN = "frequency_dropdown_open"
    }

    private var headerFrequency: View? = null
    private var dropdownFrequency: View? = null
    private var arrowFrequency: ImageView? = null
    private var selectedFrequencyText: TextView? = null

    fun bind(
        savedInstanceState: Bundle?,
        currentIntervalMs: Long,
        handler: Handler,
        refreshRunnable: Runnable,
        onIntervalChanged: (Long) -> Unit
    ) {
        headerFrequency = activity.findViewById(R.id.header_update_frequency)
        dropdownFrequency = activity.findViewById(R.id.dropdown_update_frequency)
        arrowFrequency = activity.findViewById(R.id.arrow_update_frequency)
        selectedFrequencyText = activity.findViewById(R.id.selected_frequency_text)

        headerFrequency?.setOnClickListener {
            val isExpanded = dropdownFrequency?.visibility == View.VISIBLE
            dropdownFrequency?.visibility = if (isExpanded) View.GONE else View.VISIBLE
            arrowFrequency?.animate()?.rotation(if (isExpanded) 0f else 180f)?.setDuration(200L)?.start()
        }

        if (savedInstanceState?.getBoolean(KEY_FREQUENCY_DROPDOWN_OPEN) == true) {
            dropdownFrequency?.visibility = View.VISIBLE
            arrowFrequency?.rotation = 180f
        }

        val freqGroup = activity.findViewById<RadioGroup>(R.id.frequency_radio_group)
        val initialFreqRes = when (currentIntervalMs) {
            500L -> {
                freqGroup?.check(R.id.freq_500)
                R.string.update_every_half_second
            }
            1_000L -> {
                freqGroup?.check(R.id.freq_1000)
                R.string.update_every_second
            }
            else -> {
                freqGroup?.check(R.id.freq_2000)
                R.string.update_every_two_seconds
            }
        }
        selectedFrequencyText?.text = activity.getString(initialFreqRes)

        freqGroup?.setOnCheckedChangeListener { _, checkedId ->
            val selectedMs = when (checkedId) {
                R.id.freq_500 -> 500L
                R.id.freq_1000 -> 1_000L
                R.id.freq_2000 -> 2_000L
                else -> 2_000L
            }
            val textRes = when (selectedMs) {
                500L -> R.string.update_every_half_second
                1_000L -> R.string.update_every_second
                else -> R.string.update_every_two_seconds
            }
            selectedFrequencyText?.text = activity.getString(textRes)
            onIntervalChanged(selectedMs)
            activity.getSharedPreferences(BatteryWidget.SETTINGS_PREFS, android.content.Context.MODE_PRIVATE).edit()
                .putLong(SettingsViewController.REFRESH_INTERVAL_KEY, selectedMs)
                .apply()
            handler.removeCallbacks(refreshRunnable)
            handler.post(refreshRunnable)
        }
    }
}
