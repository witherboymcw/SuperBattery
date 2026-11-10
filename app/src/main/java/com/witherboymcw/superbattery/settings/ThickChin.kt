package com.witherboymcw.superbattery.settings

import android.os.Bundle
import android.os.Handler
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import com.witherboymcw.superbattery.ChinAwareness
import com.witherboymcw.superbattery.FoldableAwareness
import com.witherboymcw.superbattery.MainActivity
import com.witherboymcw.superbattery.R

class ThickChin(private val activity: MainActivity) {

    private val commitHandler = Handler(android.os.Looper.getMainLooper())
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

        headerThickChin?.setOnClickListener {
            val isExpanded = dropdownThickChin?.visibility == View.VISIBLE
            dropdownThickChin?.visibility = if (isExpanded) View.GONE else View.VISIBLE
            arrowThickChin?.animate()?.rotation(if (isExpanded) 0f else 180f)?.setDuration(200L)?.start()
        }

        if (savedInstanceState?.getBoolean(ChinAwareness.KEY_THICK_CHIN_DROPDOWN_OPEN) == true) {
            dropdownThickChin?.visibility = View.VISIBLE
            arrowThickChin?.rotation = 180f
        }

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

        selectedThickChinText?.text = savedThickChinLevel.toString()
        if (thickChinSeekBar?.progress != savedThickChinLevel) {
            thickChinSeekBar?.progress = savedThickChinLevel
        }

        fun setupRepeatButton(button: View?, delta: Int) {
            button ?: return
            var repeatCount = 0
            val repeatRunnable = object : Runnable {
                override fun run() {
                    if (!button.isAttachedToWindow) {
                        commitHandler.removeCallbacks(this)
                        return
                    }
                    repeatCount++
                    val current = pendingThickChinLevel ?: ChinAwareness.getThickChinLevel(activity)
                    updateThickChin(current + delta)
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
                        val current = pendingThickChinLevel ?: ChinAwareness.getThickChinLevel(activity)
                        updateThickChin(current + delta)
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
                        scheduleCommit()
                        true
                    }
                    else -> false
                }
            }
        }

        setupRepeatButton(btnMinus10, -10)
        setupRepeatButton(btnMinus1, -1)
        setupRepeatButton(btnPlus1, 1)
        setupRepeatButton(btnPlus10, 10)

        btnQuick0?.setOnClickListener { updateThickChin(0) }
        btnQuick256?.setOnClickListener { updateThickChin(256) }

        thickChinSeekBar?.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> v.parent?.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.parent?.requestDisallowInterceptTouchEvent(false)
            }
            false
        }

        thickChinSeekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    updateThickChin(progress, updateSeekBar = false)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                commitHandler.removeCallbacks(commitRunnable)
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                scheduleCommit()
            }
        })
    }
}
