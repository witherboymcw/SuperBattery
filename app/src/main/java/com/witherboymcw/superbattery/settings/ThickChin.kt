package com.witherboymcw.superbattery.settings

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import com.witherboymcw.superbattery.MainActivity
import com.witherboymcw.superbattery.R
import com.witherboymcw.superbattery.awareness.ChinAwareness
import com.witherboymcw.superbattery.awareness.FoldableAwareness
import com.witherboymcw.superbattery.templates.SimpleSeekbarCard

class ThickChin(private val activity: MainActivity) {

    private val commitHandler = Handler(Looper.getMainLooper())
    private var pendingThickChinLevel: Int? = null

    private val commitRunnable = Runnable {
        flushCommit()
    }

    fun flushCommit() {
        commitHandler.removeCallbacks(commitRunnable)
        pendingThickChinLevel?.let { level ->
            ChinAwareness.saveThickChinLevel(activity, level)
            pendingThickChinLevel = null
        }
    }

    fun scheduleCommit() {
        commitHandler.removeCallbacks(commitRunnable)
        commitHandler.postDelayed(commitRunnable, 500L)
    }

    fun bind(savedInstanceState: Bundle?) {
        if (FoldableAwareness.isFoldableDevice(activity)) {
            activity.findViewById<View>(R.id.card_thick_chin)?.visibility = View.GONE
            return
        }

        val headerThickChin = activity.findViewById<View>(R.id.header_thick_chin)
        val dropdownThickChin = activity.findViewById<View>(R.id.dropdown_thick_chin)
        val arrowThickChin = activity.findViewById<ImageView>(R.id.arrow_thick_chin)
        val selectedThickChinText = activity.findViewById<TextView>(R.id.selected_thick_chin_text)
        val thickChinSeekBar = activity.findViewById<SeekBar>(R.id.thick_chin_seekbar)
        val btnMinus10 = activity.findViewById<View>(R.id.btn_thick_chin_minus_10)
        val btnMinus1 = activity.findViewById<View>(R.id.btn_thick_chin_minus_1)
        val btnPlus1 = activity.findViewById<View>(R.id.btn_thick_chin_plus_1)
        val btnPlus10 = activity.findViewById<View>(R.id.btn_thick_chin_plus_10)
        val btnQuick0 = activity.findViewById<View>(R.id.btn_thick_chin_quick_0)
        val btnQuick256 = activity.findViewById<View>(R.id.btn_thick_chin_quick_256)

        val savedThickChinLevel = ChinAwareness.getThickChinLevel(activity)
        pendingThickChinLevel = savedThickChinLevel

        fun updateThickChin(newLevel: Int, updateSeekBar: Boolean = true) {
            val level = newLevel.coerceIn(ChinAwareness.MIN_CHIN_LEVEL, ChinAwareness.MAX_CHIN_LEVEL)
            pendingThickChinLevel = level
            activity.thickChinOverrideLevel = level
            selectedThickChinText?.text = level.toString()
            if (updateSeekBar && thickChinSeekBar?.progress != level) {
                thickChinSeekBar?.progress = level
            }
            activity.applyThickChinPadding(level)
            scheduleCommit()
        }

        SimpleSeekbarCard.bind(
            headerView = headerThickChin,
            dropdownView = dropdownThickChin,
            arrowView = arrowThickChin,
            valueTextView = selectedThickChinText,
            seekBar = thickChinSeekBar,
            savedInstanceStateKey = ChinAwareness.KEY_THICK_CHIN_DROPDOWN_OPEN,
            savedInstanceState = savedInstanceState,
            currentValue = savedThickChinLevel,
            valueFormatter = { it.toString() },
            onProgressUpdate = { progress ->
                updateThickChin(progress, updateSeekBar = false)
            },
            onCommit = {
                scheduleCommit()
            }
        )

        val getCurrent = { pendingThickChinLevel ?: ChinAwareness.getThickChinLevel(activity) }
        SimpleSeekbarCard.setupRepeatButton(btnMinus10, -10, { updateThickChin(it) }, getCurrent) { scheduleCommit() }
        SimpleSeekbarCard.setupRepeatButton(btnMinus1, -1, { updateThickChin(it) }, getCurrent) { scheduleCommit() }
        SimpleSeekbarCard.setupRepeatButton(btnPlus1, 1, { updateThickChin(it) }, getCurrent) { scheduleCommit() }
        SimpleSeekbarCard.setupRepeatButton(btnPlus10, 10, { updateThickChin(it) }, getCurrent) { scheduleCommit() }

        btnQuick0?.setOnClickListener { updateThickChin(0) }
        btnQuick256?.setOnClickListener { updateThickChin(256) }
    }
}
