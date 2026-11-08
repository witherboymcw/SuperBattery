package com.witherboymcw.superbattery

import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
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
    private val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity.toFloat()
    private var velocityTracker: VelocityTracker? = null

    private fun addMovement(ev: MotionEvent) {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain()
        }
        velocityTracker?.addMovement(ev)
    }

    private fun recycleVelocityTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (canSwipe?.invoke() == false) {
            return super.onInterceptTouchEvent(ev)
        }
        if (ev.action == MotionEvent.ACTION_DOWN) {
            recycleVelocityTracker()
        }
        addMovement(ev)
        
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
                }
            }
        }
        
        val result = isSwipingLeft || super.onInterceptTouchEvent(ev)
        
        if (ev.action == MotionEvent.ACTION_UP || ev.action == MotionEvent.ACTION_CANCEL) {
            isSwipingLeft = false
            recycleVelocityTracker()
        }
        return result
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (canSwipe?.invoke() == false) {
            return super.onTouchEvent(ev)
        }
        if (ev.action == MotionEvent.ACTION_DOWN) {
            recycleVelocityTracker()
        }
        addMovement(ev)
        
        val screenWidth = width.toFloat().takeIf { it > 0 } ?: 1080f
        var handled = false
        
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = ev.rawX
                startY = ev.rawY
                isSwipingLeft = false
                handled = true
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
                    handled = true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isSwipingLeft) {
                    val effectiveDx = (ev.rawX - startX) * swipeSensitivity
                    val currentX = (screenWidth + effectiveDx).coerceIn(0f, screenWidth)
                    val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                    val threshold = if (isLandscape) 0.18f else 0.40f
                    
                    var isFling = false
                    if (ev.action == MotionEvent.ACTION_UP) {
                        velocityTracker?.computeCurrentVelocity(1000)
                        val xVelocity = velocityTracker?.xVelocity ?: 0f
                        if (xVelocity < -(minFlingVelocity * 0.75f)) {
                            isFling = true
                        }
                    }
                    
                    val shouldOpen = isFling || currentX < screenWidth * (1f - threshold)
                    swipeListener?.onSwipeEnd(shouldOpen)
                    isSwipingLeft = false
                    handled = true
                } else if (ev.action == MotionEvent.ACTION_UP) {
                    performClick()
                }
            }
        }
        
        if (ev.action == MotionEvent.ACTION_UP || ev.action == MotionEvent.ACTION_CANCEL) {
            recycleVelocityTracker()
        }
        
        return handled || super.onTouchEvent(ev)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
