package com.witherboymcw.superbattery

import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

class SwipeBackFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    interface SwipeListener {
        fun onSwipeProgress(translationX: Float)
        fun onSwipeEnd(shouldClose: Boolean)
    }

    var swipeListener: SwipeListener? = null
    var swipeSensitivity: Float = 1f
    private var startX = 0f
    private var startY = 0f
    private var isSwipingRight = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = ev.rawX
                startY = ev.rawY
                isSwipingRight = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - startX
                val dy = ev.rawY - startY
                val effectiveDx = dx * swipeSensitivity
                if (!isSwipingRight && effectiveDx > touchSlop && abs(dx) > abs(dy)) {
                    isSwipingRight = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
        }
        return isSwipingRight || super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_MOVE -> {
                val dx = ((ev.rawX - startX) * swipeSensitivity).coerceAtLeast(0f)
                if (isSwipingRight) {
                    swipeListener?.onSwipeProgress(dx)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isSwipingRight) {
                    val dx = ((ev.rawX - startX) * swipeSensitivity).coerceAtLeast(0f)
                    val widthVal = width.toFloat().takeIf { it > 0 } ?: 1080f
                    val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                    val threshold = if (isLandscape) 0.09f else 0.20f
                    swipeListener?.onSwipeEnd(dx > widthVal * threshold)
                    isSwipingRight = false
                    return true
                } else if (ev.action == MotionEvent.ACTION_UP) {
                    performClick()
                }
            }
        }
        return super.onTouchEvent(ev)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
