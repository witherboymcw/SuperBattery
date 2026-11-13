package com.witherboymcw.superbattery.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import com.witherboymcw.superbattery.MainActivity
import com.witherboymcw.superbattery.R
import com.witherboymcw.superbattery.awareness.NotchAwareness
import com.witherboymcw.superbattery.widgets.BatteryWidget

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

    private fun setupRepeatButton(
        button: View?,
        delta: Float,
        onUpdate: (Float) -> Unit,
        getCurrent: () -> Float
    ) {
        button ?: return
        var repeatCount = 0
        val repeatRunnable = object : Runnable {
            override fun run() {
                if (!button.isAttachedToWindow) {
                    commitHandler.removeCallbacks(this)
                    return
                }
                repeatCount++
                val current = getCurrent()
                onUpdate(current + delta)
                val nextDelay = if (repeatCount < 3) 500L else 250L
                commitHandler.postDelayed(this, nextDelay)
            }
        }

        button.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                commitHandler.removeCallbacks(repeatRunnable)
            }
        })

        button.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    repeatCount = 0
                    val current = getCurrent()
                    onUpdate(current + delta)
                    commitHandler.postDelayed(repeatRunnable, 500L)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (event.x < 0 || event.x > v.width || event.y < 0 || event.y > v.height) {
                        commitHandler.removeCallbacks(repeatRunnable)
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                    false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    commitHandler.removeCallbacks(repeatRunnable)
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                    if (event.action == MotionEvent.ACTION_UP) {
                        v.performClick()
                    }
                    true
                }
                else -> false
            }
        }
    }

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

        val prefs = activity.getSharedPreferences(BatteryWidget.SETTINGS_PREFS, Context.MODE_PRIVATE)

        if (headerNotch != null && dropdownNotch != null && arrowNotch != null && selectedNotchText != null) {

            fun syncSeekBars() {
                val mode = prefs.getInt(NotchAwareness.KEY_NOTCH_CUSTOM_MODE, NotchAwareness.MODE_SYSTEM)
                selectedNotchText?.text = when (mode) {
                    NotchAwareness.MODE_FORCE -> activity.getString(R.string.notch_mode_force)
                    NotchAwareness.MODE_IGNORE -> activity.getString(R.string.notch_mode_ignore)
                    else -> activity.getString(R.string.notch_mode_system)
                }
                notchPosContainer?.visibility = if (mode == NotchAwareness.MODE_FORCE) View.VISIBLE else View.GONE

                val isCenter = prefs.getBoolean(NotchAwareness.KEY_NOTCH_IS_CENTER, true)
                if (isCenter) {
                    notchPosGroup?.check(R.id.notch_pos_center)
                } else {
                    notchPosGroup?.check(R.id.notch_pos_not_center)
                }

                val sizeDp = prefs.getFloat(NotchAwareness.KEY_NOTCH_RADIUS, NotchAwareness.DEFAULT_NOTCH_RADIUS)
                selectedNotchSizeText?.text = "${sizeDp.toInt()} dp"
                if (notchSizeSeekBar?.progress != sizeDp.toInt()) {
                    notchSizeSeekBar?.progress = sizeDp.toInt()
                }

                val pillDp = prefs.getFloat(NotchAwareness.KEY_NOTCH_PILL_WIDTH, NotchAwareness.DEFAULT_NOTCH_PILL_WIDTH)
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
                prefs.edit().putInt(NotchAwareness.KEY_NOTCH_CUSTOM_MODE, mode).apply()
                syncSeekBars()
                NotchAwareness.updateAllHeaders(activity)
            }

            notchPosGroup?.setOnCheckedChangeListener { _, checkedId ->
                prefs.edit().putBoolean(NotchAwareness.KEY_NOTCH_IS_CENTER, checkedId == R.id.notch_pos_center).apply()
                NotchAwareness.updateAllHeaders(activity)
            }

            fun updateSize(newValDp: Float, updateSeekBar: Boolean = true) {
                val clamped = newValDp.coerceIn(NotchAwareness.MIN_NOTCH_RADIUS, NotchAwareness.MAX_NOTCH_RADIUS)
                prefs.edit().putFloat(NotchAwareness.KEY_NOTCH_RADIUS, clamped).apply()
                selectedNotchSizeText?.text = "${clamped.toInt()} dp"
                if (updateSeekBar && notchSizeSeekBar?.progress != clamped.toInt()) {
                    notchSizeSeekBar?.progress = clamped.toInt()
                }
                NotchAwareness.updateAllHeaders(activity)
            }

            setupRepeatButton(
                btnSizeMinus10,
                -10f,
                { updateSize(it) },
                { prefs.getFloat(NotchAwareness.KEY_NOTCH_RADIUS, NotchAwareness.DEFAULT_NOTCH_RADIUS) }
            )
            setupRepeatButton(
                btnSizeMinus1,
                -1f,
                { updateSize(it) },
                { prefs.getFloat(NotchAwareness.KEY_NOTCH_RADIUS, NotchAwareness.DEFAULT_NOTCH_RADIUS) }
            )
            setupRepeatButton(
                btnSizePlus1,
                1f,
                { updateSize(it) },
                { prefs.getFloat(NotchAwareness.KEY_NOTCH_RADIUS, NotchAwareness.DEFAULT_NOTCH_RADIUS) }
            )
            setupRepeatButton(
                btnSizePlus10,
                10f,
                { updateSize(it) },
                { prefs.getFloat(NotchAwareness.KEY_NOTCH_RADIUS, NotchAwareness.DEFAULT_NOTCH_RADIUS) }
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
                prefs.edit().putFloat(NotchAwareness.KEY_NOTCH_PILL_WIDTH, clamped).apply()
                selectedNotchPillText?.text = "${clamped.toInt()} dp"
                if (updateSeekBar && notchPillSeekBar?.progress != clamped.toInt()) {
                    notchPillSeekBar?.progress = clamped.toInt()
                }
                NotchAwareness.updateAllHeaders(activity)
            }

            setupRepeatButton(
                btnPillMinus10,
                -10f,
                { updatePill(it) },
                { prefs.getFloat(NotchAwareness.KEY_NOTCH_PILL_WIDTH, NotchAwareness.DEFAULT_NOTCH_PILL_WIDTH) }
            )
            setupRepeatButton(
                btnPillMinus1,
                -1f,
                { updatePill(it) },
                { prefs.getFloat(NotchAwareness.KEY_NOTCH_PILL_WIDTH, NotchAwareness.DEFAULT_NOTCH_PILL_WIDTH) }
            )
            setupRepeatButton(
                btnPillPlus1,
                1f,
                { updatePill(it) },
                { prefs.getFloat(NotchAwareness.KEY_NOTCH_PILL_WIDTH, NotchAwareness.DEFAULT_NOTCH_PILL_WIDTH) }
            )
            setupRepeatButton(
                btnPillPlus10,
                10f,
                { updatePill(it) },
                { prefs.getFloat(NotchAwareness.KEY_NOTCH_PILL_WIDTH, NotchAwareness.DEFAULT_NOTCH_PILL_WIDTH) }
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
            val prefs = activity.getSharedPreferences(BatteryWidget.SETTINGS_PREFS, Context.MODE_PRIVATE)
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
