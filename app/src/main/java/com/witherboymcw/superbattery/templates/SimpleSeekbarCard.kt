package com.witherboymcw.superbattery.templates

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView

class SimpleSeekbarCard {

    companion object {
        private val handler = Handler(Looper.getMainLooper())

        fun setupRepeatButton(
            button: View?,
            delta: Int,
            onUpdate: (Int) -> Unit,
            getCurrent: () -> Int,
            onCommit: () -> Unit = {}
        ) {
            button ?: return
            var repeatCount = 0
            val repeatRunnable = object : Runnable {
                override fun run() {
                    if (!button.isAttachedToWindow) {
                        handler.removeCallbacks(this)
                        return
                    }
                    repeatCount++
                    val current = getCurrent()
                    onUpdate(current + delta)
                    val nextDelay = if (repeatCount < 3) 500L else 250L
                    handler.postDelayed(this, nextDelay)
                }
            }

            button.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {}
                override fun onViewDetachedFromWindow(v: View) {
                    handler.removeCallbacks(repeatRunnable)
                }
            })

            button.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                        repeatCount = 0
                        val current = getCurrent()
                        onUpdate(current + delta)
                        handler.postDelayed(repeatRunnable, 500L)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (event.x < 0 || event.x > v.width || event.y < 0 || event.y > v.height) {
                            handler.removeCallbacks(repeatRunnable)
                            v.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                        false
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        handler.removeCallbacks(repeatRunnable)
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                        if (event.action == MotionEvent.ACTION_UP) {
                            v.performClick()
                        }
                        onCommit()
                        true
                    }
                    else -> false
                }
            }
        }

        fun bind(
            headerView: View?,
            dropdownView: View?,
            arrowView: ImageView?,
            valueTextView: TextView?,
            seekBar: SeekBar?,
            savedInstanceStateKey: String? = null,
            savedInstanceState: Bundle? = null,
            currentValue: Int,
            valueFormatter: (Int) -> String = { it.toString() },
            onProgressUpdate: (Int) -> Unit,
            onCommit: (Int) -> Unit
        ) {
            SimpleOptionCard.setupExpandableHeader(headerView, dropdownView, arrowView, savedInstanceStateKey, savedInstanceState)

            valueTextView?.text = valueFormatter(currentValue)
            if (seekBar?.progress != currentValue) {
                seekBar?.progress = currentValue
            }

            seekBar?.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> v.parent?.requestDisallowInterceptTouchEvent(true)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.parent?.requestDisallowInterceptTouchEvent(false)
                }
                false
            }

            seekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        valueTextView?.text = valueFormatter(progress)
                        onProgressUpdate(progress)
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {
                    val progress = sb?.progress ?: return
                    onCommit(progress)
                }
            })
        }
    }
}
