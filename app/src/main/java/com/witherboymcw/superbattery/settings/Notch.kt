package com.witherboymcw.superbattery.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper

import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import com.witherboymcw.superbattery.MainActivity
import com.witherboymcw.superbattery.R
import com.witherboymcw.superbattery.awareness.NotchAwareness
import com.witherboymcw.superbattery.ui.SimpleSeekbarCard

class Notch(private val activity: MainActivity) {

    companion object {
        const val KEY_NOTCH_DROPDOWN_OPEN = "key_notch_dropdown_open"
    }

    private val commitHandler = Handler(Looper.getMainLooper())

    private var headerNotch: View? = null
    private var dropdownNotch: View? = null
    private var arrowNotch: ImageView? = null
    private var selectedNotchText: TextView? = null

    private var prefListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    fun bind(savedInstanceState: Bundle?) {
        headerNotch = activity.findViewById(R.id.header_notch)
        dropdownNotch = activity.findViewById(R.id.dropdown_notch)
        arrowNotch = activity.findViewById(R.id.arrow_notch)
        selectedNotchText = activity.findViewById(R.id.selected_notch_text)

        val notchModeGroup = activity.findViewById<RadioGroup>(R.id.notch_mode_radio_group)
        val notchPosContainer = activity.findViewById<View>(R.id.notch_position_container)
        val notchPosGroup = activity.findViewById<RadioGroup>(R.id.notch_position_radio_group)
        val btnSetNotchPos = activity.findViewById<Button>(R.id.btn_set_notch_position)

        // Size Seekbar & Buttons
        val selectedNotchSizeText = activity.findViewById<TextView>(R.id.selected_notch_size_text)
        val notchSizeSeekBar = activity.findViewById<SeekBar>(R.id.notch_size_seekbar)
        val btnSizeMinus10 = activity.findViewById<View>(R.id.btn_notch_size_minus_10)
        val btnSizeMinus1 = activity.findViewById<View>(R.id.btn_notch_size_minus_1)
        val btnSizePlus1 = activity.findViewById<View>(R.id.btn_notch_size_plus_1)
        val btnSizePlus10 = activity.findViewById<View>(R.id.btn_notch_size_plus_10)

        // Pill Seekbar & Buttons
        val selectedNotchPillText = activity.findViewById<TextView>(R.id.selected_notch_pill_text)
        val notchPillSeekBar = activity.findViewById<SeekBar>(R.id.notch_pill_seekbar)
        val btnPillMinus10 = activity.findViewById<View>(R.id.btn_notch_pill_minus_10)
        val btnPillMinus1 = activity.findViewById<View>(R.id.btn_notch_pill_minus_1)
        val btnPillPlus1 = activity.findViewById<View>(R.id.btn_notch_pill_plus_1)
        val btnPillPlus10 = activity.findViewById<View>(R.id.btn_notch_pill_plus_10)

        val prefs = AppPreferences.getPreferences(activity)

        if (headerNotch != null && dropdownNotch != null && arrowNotch != null && selectedNotchText != null) {

            fun syncSeekBars() {
                val mode = AppPreferences.getNotchCustomMode(activity, NotchAwareness.MODE_SYSTEM)
                selectedNotchText?.text = when (mode) {
                    NotchAwareness.MODE_FORCE -> activity.getString(R.string.notch_mode_force)
                    NotchAwareness.MODE_IGNORE -> activity.getString(R.string.notch_mode_ignore)
                    else -> activity.getString(R.string.notch_mode_system)
                }
                notchPosContainer?.visibility = if (mode == NotchAwareness.MODE_FORCE) View.VISIBLE else View.GONE

                val isCenter = AppPreferences.isNotchCenter(activity)
                if (isCenter) {
                    notchPosGroup?.check(R.id.notch_pos_center)
                } else {
                    notchPosGroup?.check(R.id.notch_pos_not_center)
                }

                val sizeDp = AppPreferences.getNotchRadius(activity, NotchAwareness.DEFAULT_NOTCH_RADIUS)
                selectedNotchSizeText?.text = "${sizeDp.toInt()} dp"
                if (notchSizeSeekBar?.progress != sizeDp.toInt()) {
                    notchSizeSeekBar?.progress = sizeDp.toInt()
                }

                val pillDp = AppPreferences.getNotchPillWidth(activity, NotchAwareness.DEFAULT_NOTCH_PILL_WIDTH)
                selectedNotchPillText?.text = "${pillDp.toInt()} dp"
                if (notchPillSeekBar?.progress != pillDp.toInt()) {
                    notchPillSeekBar?.progress = pillDp.toInt()
                }
            }

            syncSeekBars()

            prefListener?.let { prefs.unregisterOnSharedPreferenceChangeListener(it) }
            prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == NotchAwareness.KEY_NOTCH_RADIUS ||
                    key == NotchAwareness.KEY_NOTCH_PILL_WIDTH ||
                    key == NotchAwareness.KEY_NOTCH_IS_CENTER ||
                    key == NotchAwareness.KEY_NOTCH_CUSTOM_MODE) {
                    syncSeekBars()
                }
            }
            prefs.registerOnSharedPreferenceChangeListener(prefListener)

            notchModeGroup?.setOnCheckedChangeListener { _, checkedId ->
                val mode = when (checkedId) {
                    R.id.notch_mode_force -> NotchAwareness.MODE_FORCE
                    R.id.notch_mode_ignore -> NotchAwareness.MODE_IGNORE
                    else -> NotchAwareness.MODE_SYSTEM
                }
                AppPreferences.setNotchCustomMode(activity, mode)
                syncSeekBars()
                NotchAwareness.updateAllHeaders(activity)
            }

            notchPosGroup?.setOnCheckedChangeListener { _, checkedId ->
                AppPreferences.setNotchCenter(activity, checkedId == R.id.notch_pos_center)
                NotchAwareness.updateAllHeaders(activity)
            }

            fun updateSize(newValDp: Float, updateSeekBar: Boolean = true) {
                val clamped = newValDp.coerceIn(NotchAwareness.MIN_NOTCH_RADIUS, NotchAwareness.MAX_NOTCH_RADIUS)
                AppPreferences.setNotchRadius(activity, clamped)
                selectedNotchSizeText?.text = "${clamped.toInt()} dp"
                if (updateSeekBar && notchSizeSeekBar?.progress != clamped.toInt()) {
                    notchSizeSeekBar?.progress = clamped.toInt()
                }
                NotchAwareness.updateAllHeaders(activity)
            }

            SimpleSeekbarCard.setupRepeatButton(
                btnSizeMinus10,
                -10,
                { updateSize(it.toFloat()) },
                { AppPreferences.getNotchRadius(activity, NotchAwareness.DEFAULT_NOTCH_RADIUS).toInt() }
            )
            SimpleSeekbarCard.setupRepeatButton(
                btnSizeMinus1,
                -1,
                { updateSize(it.toFloat()) },
                { AppPreferences.getNotchRadius(activity, NotchAwareness.DEFAULT_NOTCH_RADIUS).toInt() }
            )
            SimpleSeekbarCard.setupRepeatButton(
                btnSizePlus1,
                1,
                { updateSize(it.toFloat()) },
                { AppPreferences.getNotchRadius(activity, NotchAwareness.DEFAULT_NOTCH_RADIUS).toInt() }
            )
            SimpleSeekbarCard.setupRepeatButton(
                btnSizePlus10,
                10,
                { updateSize(it.toFloat()) },
                { AppPreferences.getNotchRadius(activity, NotchAwareness.DEFAULT_NOTCH_RADIUS).toInt() }
            )

            notchSizeSeekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        selectedNotchSizeText?.text = "$progress dp"
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    val progress = seekBar?.progress ?: return
                    updateSize(progress.toFloat(), updateSeekBar = false)
                }
            })

            fun updatePill(newValDp: Float, updateSeekBar: Boolean = true) {
                val clamped = newValDp.coerceIn(NotchAwareness.MIN_NOTCH_PILL_WIDTH, NotchAwareness.MAX_NOTCH_PILL_WIDTH)
                AppPreferences.setNotchPillWidth(activity, clamped)
                selectedNotchPillText?.text = "${clamped.toInt()} dp"
                if (updateSeekBar && notchPillSeekBar?.progress != clamped.toInt()) {
                    notchPillSeekBar?.progress = clamped.toInt()
                }
                NotchAwareness.updateAllHeaders(activity)
            }

            SimpleSeekbarCard.setupRepeatButton(
                btnPillMinus10,
                -10,
                { updatePill(it.toFloat()) },
                { AppPreferences.getNotchPillWidth(activity, NotchAwareness.DEFAULT_NOTCH_PILL_WIDTH).toInt() }
            )
            SimpleSeekbarCard.setupRepeatButton(
                btnPillMinus1,
                -1,
                { updatePill(it.toFloat()) },
                { AppPreferences.getNotchPillWidth(activity, NotchAwareness.DEFAULT_NOTCH_PILL_WIDTH).toInt() }
            )
            SimpleSeekbarCard.setupRepeatButton(
                btnPillPlus1,
                1,
                { updatePill(it.toFloat()) },
                { AppPreferences.getNotchPillWidth(activity, NotchAwareness.DEFAULT_NOTCH_PILL_WIDTH).toInt() }
            )
            SimpleSeekbarCard.setupRepeatButton(
                btnPillPlus10,
                10,
                { updatePill(it.toFloat()) },
                { AppPreferences.getNotchPillWidth(activity, NotchAwareness.DEFAULT_NOTCH_PILL_WIDTH).toInt() }
            )

            notchPillSeekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        selectedNotchPillText?.text = "$progress dp"
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    val progress = seekBar?.progress ?: return
                    updatePill(progress.toFloat(), updateSeekBar = false)
                }
            })

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

    fun unbind() {
        NotchAwareness.dismissNotchPositionOverlay()
        prefListener?.let {
            val prefs = AppPreferences.getPreferences(activity)
            prefs.unregisterOnSharedPreferenceChangeListener(it)
            prefListener = null
        }
        commitHandler.removeCallbacksAndMessages(null)
        headerNotch = null
        dropdownNotch = null
        arrowNotch = null
        selectedNotchText = null
    }
}
