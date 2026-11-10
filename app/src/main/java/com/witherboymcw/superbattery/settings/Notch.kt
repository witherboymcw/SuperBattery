package com.witherboymcw.superbattery.settings

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.TextView
import com.witherboymcw.superbattery.BatteryWidget
import com.witherboymcw.superbattery.MainActivity
import com.witherboymcw.superbattery.NotchAwareness
import com.witherboymcw.superbattery.R

class Notch(private val activity: MainActivity) {

    companion object {
        const val KEY_NOTCH_DROPDOWN_OPEN = "key_notch_dropdown_open"
    }

    private var headerNotch: View? = null
    private var dropdownNotch: View? = null
    private var arrowNotch: ImageView? = null
    private var selectedNotchText: TextView? = null

    fun bind(savedInstanceState: Bundle?) {
        headerNotch = activity.findViewById(R.id.header_notch)
        dropdownNotch = activity.findViewById(R.id.dropdown_notch)
        arrowNotch = activity.findViewById(R.id.arrow_notch)
        selectedNotchText = activity.findViewById(R.id.selected_notch_text)

        val notchModeGroup = activity.findViewById<RadioGroup>(R.id.notch_mode_radio_group)
        val notchPosContainer = activity.findViewById<View>(R.id.notch_position_container)
        val notchPosGroup = activity.findViewById<RadioGroup>(R.id.notch_position_radio_group)
        val btnSetNotchPos = activity.findViewById<Button>(R.id.btn_set_notch_position)

        val prefs = activity.getSharedPreferences(BatteryWidget.SETTINGS_PREFS, Context.MODE_PRIVATE)

        if (headerNotch != null && dropdownNotch != null && arrowNotch != null && selectedNotchText != null) {
            val notchMode = prefs.getInt(NotchAwareness.KEY_NOTCH_CUSTOM_MODE, NotchAwareness.MODE_SYSTEM)
            when (notchMode) {
                NotchAwareness.MODE_SYSTEM -> notchModeGroup?.check(R.id.notch_mode_system)
                NotchAwareness.MODE_FORCE -> notchModeGroup?.check(R.id.notch_mode_force)
                NotchAwareness.MODE_IGNORE -> notchModeGroup?.check(R.id.notch_mode_ignore)
            }

            fun updateNotchText() {
                val mode = prefs.getInt(NotchAwareness.KEY_NOTCH_CUSTOM_MODE, NotchAwareness.MODE_SYSTEM)
                selectedNotchText?.text = when(mode) {
                    NotchAwareness.MODE_FORCE -> activity.getString(R.string.notch_mode_force)
                    NotchAwareness.MODE_IGNORE -> activity.getString(R.string.notch_mode_ignore)
                    else -> activity.getString(R.string.notch_mode_system)
                }
                notchPosContainer?.visibility = if (mode == NotchAwareness.MODE_FORCE) View.VISIBLE else View.GONE
            }
            updateNotchText()

            val isCenter = prefs.getBoolean(NotchAwareness.KEY_NOTCH_IS_CENTER, true)
            if (isCenter) {
                notchPosGroup?.check(R.id.notch_pos_center)
            } else {
                notchPosGroup?.check(R.id.notch_pos_not_center)
            }

            notchModeGroup?.setOnCheckedChangeListener { _, checkedId ->
                val mode = when(checkedId) {
                    R.id.notch_mode_force -> NotchAwareness.MODE_FORCE
                    R.id.notch_mode_ignore -> NotchAwareness.MODE_IGNORE
                    else -> NotchAwareness.MODE_SYSTEM
                }
                prefs.edit().putInt(NotchAwareness.KEY_NOTCH_CUSTOM_MODE, mode).apply()
                updateNotchText()
                NotchAwareness.updateAllHeaders(activity)
            }

            notchPosGroup?.setOnCheckedChangeListener { _, checkedId ->
                prefs.edit().putBoolean(NotchAwareness.KEY_NOTCH_IS_CENTER, checkedId == R.id.notch_pos_center).apply()
                NotchAwareness.updateAllHeaders(activity)
            }

            btnSetNotchPos?.setOnClickListener {
                NotchAwareness.showNotchPositionOverlay(activity)
            }

            val isNotchOpen = savedInstanceState?.getBoolean(KEY_NOTCH_DROPDOWN_OPEN, false) ?: false
            if (isNotchOpen) {
                dropdownNotch?.visibility = View.VISIBLE
                arrowNotch?.rotation = 180f
            }

            headerNotch?.setOnClickListener {
                if (dropdownNotch?.visibility == View.VISIBLE) {
                    dropdownNotch?.visibility = View.GONE
                    arrowNotch?.animate()?.rotation(0f)?.setDuration(200)?.start()
                } else {
                    dropdownNotch?.visibility = View.VISIBLE
                    arrowNotch?.animate()?.rotation(180f)?.setDuration(200)?.start()
                }
            }
        }
    }
}
