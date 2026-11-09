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

    enum class SwipeDirection {
        NONE,
        LEFT_TO_SETTINGS,
        RIGHT_TO_HOTSPOT
    }

    interface DualSwipeListener {
        fun onSwipeStart(direction: SwipeDirection)
        fun onSwipeProgress(direction: SwipeDirection, dx: Float, targetX: Float)
        fun onSwipeEnd(direction: SwipeDirection, shouldOpen: Boolean)
    }

    interface SwipeListener {
        fun onSwipeStart()
        fun onSwipeProgress(dx: Float, targetX: Float)
        fun onSwipeEnd(shouldOpen: Boolean)
    }

    var canSwipe: (() -> Boolean)? = null
    var swipeListener: SwipeListener? = null
    var dualSwipeListener: DualSwipeListener? = null
    var swipeSensitivity: Float = 1f

    private var startX = 0f
    private var startY = 0f
    private var currentDirection = SwipeDirection.NONE
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat() * 0.25f
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
                currentDirection = SwipeDirection.NONE
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - startX
                val dy = ev.rawY - startY
                val effectiveDx = dx * swipeSensitivity
                if (currentDirection == SwipeDirection.NONE && abs(dx) > touchSlop && abs(dx) > abs(dy)) {
                    if (effectiveDx < 0) {
                        currentDirection = SwipeDirection.LEFT_TO_SETTINGS
                    } else {
                        currentDirection = SwipeDirection.RIGHT_TO_HOTSPOT
                    }
                    parent?.requestDisallowInterceptTouchEvent(true)
                    dualSwipeListener?.onSwipeStart(currentDirection)
                    if (currentDirection == SwipeDirection.LEFT_TO_SETTINGS) {
                        swipeListener?.onSwipeStart()
                    }
                }
            }
        }
        
        val result = currentDirection != SwipeDirection.NONE || super.onInterceptTouchEvent(ev)
        
        if (ev.action == MotionEvent.ACTION_UP || ev.action == MotionEvent.ACTION_CANCEL) {
            currentDirection = SwipeDirection.NONE
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
                currentDirection = SwipeDirection.NONE
                handled = true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - startX
                val dy = ev.rawY - startY
                val effectiveDx = dx * swipeSensitivity

                if (currentDirection == SwipeDirection.NONE && abs(dx) > touchSlop && abs(dx) > abs(dy)) {
                    if (effectiveDx < 0) {
                        currentDirection = SwipeDirection.LEFT_TO_SETTINGS
                    } else {
                        currentDirection = SwipeDirection.RIGHT_TO_HOTSPOT
                    }
                    parent?.requestDisallowInterceptTouchEvent(true)
                    dualSwipeListener?.onSwipeStart(currentDirection)
                    if (currentDirection == SwipeDirection.LEFT_TO_SETTINGS) {
                        swipeListener?.onSwipeStart()
                    }
                }

                if (currentDirection == SwipeDirection.LEFT_TO_SETTINGS) {
                    val targetX = (screenWidth + effectiveDx).coerceIn(0f, screenWidth)
                    dualSwipeListener?.onSwipeProgress(currentDirection, effectiveDx, targetX)
                    swipeListener?.onSwipeProgress(effectiveDx, targetX)
                    handled = true
                } else if (currentDirection == SwipeDirection.RIGHT_TO_HOTSPOT) {
                    val targetX = (-screenWidth + effectiveDx).coerceIn(-screenWidth, 0f)
                    dualSwipeListener?.onSwipeProgress(currentDirection, effectiveDx, targetX)
                    handled = true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (currentDirection != SwipeDirection.NONE) {
                    val effectiveDx = (ev.rawX - startX) * swipeSensitivity
                    val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                    val threshold = if (isLandscape) 0.18f else 0.40f

                    velocityTracker?.computeCurrentVelocity(1000)
                    val xVelocity = velocityTracker?.xVelocity ?: 0f

                    if (currentDirection == SwipeDirection.LEFT_TO_SETTINGS) {
                        val currentX = (screenWidth + effectiveDx).coerceIn(0f, screenWidth)
                        val isFling = ev.action == MotionEvent.ACTION_UP && xVelocity < -(minFlingVelocity * 0.25f)
                        val shouldOpen = isFling || currentX < screenWidth * (1f - threshold)
                        dualSwipeListener?.onSwipeEnd(currentDirection, shouldOpen)
                        swipeListener?.onSwipeEnd(shouldOpen)
                    } else if (currentDirection == SwipeDirection.RIGHT_TO_HOTSPOT) {
                        val currentX = (-screenWidth + effectiveDx).coerceIn(-screenWidth, 0f)
                        val isFling = ev.action == MotionEvent.ACTION_UP && xVelocity > (minFlingVelocity * 0.25f)
                        val shouldOpen = isFling || currentX > -screenWidth * (1f - threshold)
                        dualSwipeListener?.onSwipeEnd(currentDirection, shouldOpen)
                    }

                    currentDirection = SwipeDirection.NONE
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
