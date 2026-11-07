package com.witherboymcw.superbattery

import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.LinearLayout
import kotlin.math.abs

class DashboardLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    interface SwipeListener {
        fun onSwipeStart()
        fun onSwipeProgress(dx: Float, targetX: Float)
        fun onSwipeEnd(shouldOpen: Boolean)
    }

    var canSwipe: (() -> Boolean)? = null
    var swipeListener: SwipeListener? = null
    var swipeSensitivity: Float = 1f
    private var startX = 0f
    private var startY = 0f
    private var isSwipingLeft = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (canSwipe?.invoke() == false) {
            return super.onInterceptTouchEvent(ev)
        }
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = ev.rawX
                startY = ev.rawY
                isSwipingLeft = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - startX
                val dy = ev.rawY - startY
                val effectiveDx = dx * swipeSensitivity
                if (!isSwipingLeft && effectiveDx < -touchSlop && abs(dx) > abs(dy)) {
                    isSwipingLeft = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    swipeListener?.onSwipeStart()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isSwipingLeft = false
            }
        }
        return isSwipingLeft || super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (canSwipe?.invoke() == false) {
            return super.onTouchEvent(ev)
        }
        val screenWidth = width.toFloat().takeIf { it > 0 } ?: 1080f
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = ev.rawX
                startY = ev.rawY
                isSwipingLeft = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - startX
                val dy = ev.rawY - startY
                val effectiveDx = dx * swipeSensitivity
                if (!isSwipingLeft && effectiveDx < -touchSlop && abs(dx) > abs(dy)) {
                    isSwipingLeft = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    swipeListener?.onSwipeStart()
                }
                if (isSwipingLeft) {
                    val targetX = (screenWidth + effectiveDx).coerceIn(0f, screenWidth)
                    swipeListener?.onSwipeProgress(effectiveDx, targetX)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isSwipingLeft) {
                    val effectiveDx = (ev.rawX - startX) * swipeSensitivity
                    val currentX = (screenWidth + effectiveDx).coerceIn(0f, screenWidth)
                    val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                    val threshold = if (isLandscape) 0.09f else 0.20f
                    swipeListener?.onSwipeEnd(currentX < screenWidth * (1f - threshold))
                    isSwipingLeft = false
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
