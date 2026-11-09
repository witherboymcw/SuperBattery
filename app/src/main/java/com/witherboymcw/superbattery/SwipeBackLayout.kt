package com.witherboymcw.superbattery

import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

open class SwipeBackFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        const val SWIPE_DIRECTION_RIGHT = 1 // Swiping right closes container (e.g., Settings on right)
        const val SWIPE_DIRECTION_LEFT = -1  // Swiping left closes container (e.g., Hotspot on left)
    }

    interface SwipeListener {
        fun onSwipeProgress(translationX: Float)
        fun onSwipeEnd(shouldClose: Boolean)
    }

    var swipeListener: SwipeListener? = null
    var swipeSensitivity: Float = 1f
    var swipeDirection: Int = SWIPE_DIRECTION_RIGHT

    private var startX = 0f
    private var startY = 0f
    private var isSwiping = false
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
        if (ev.action == MotionEvent.ACTION_DOWN) {
            recycleVelocityTracker()
        }
        addMovement(ev)

        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = ev.rawX
                startY = ev.rawY
                isSwiping = false
            }
            MotionEvent.ACTION_MOVE -> {
                val rawDx = if (swipeDirection == SWIPE_DIRECTION_LEFT) startX - ev.rawX else ev.rawX - startX
                val dy = ev.rawY - startY
                val effectiveDx = rawDx * swipeSensitivity
                if (!isSwiping && effectiveDx > touchSlop && abs(rawDx) > abs(dy)) {
                    isSwiping = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
        }
        
        val result = isSwiping || super.onInterceptTouchEvent(ev)
        
        if (ev.action == MotionEvent.ACTION_UP || ev.action == MotionEvent.ACTION_CANCEL) {
            isSwiping = false
            recycleVelocityTracker()
        }
        
        return result
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            recycleVelocityTracker()
        }
        addMovement(ev)
        
        var handled = false
        
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                handled = true // Need to handle DOWN to receive MOVE
            }
            MotionEvent.ACTION_MOVE -> {
                val rawDx = if (swipeDirection == SWIPE_DIRECTION_LEFT) startX - ev.rawX else ev.rawX - startX
                val dx = (rawDx * swipeSensitivity).coerceAtLeast(0f)
                if (isSwiping) {
                    val signedDx = if (swipeDirection == SWIPE_DIRECTION_LEFT) -dx else dx
                    swipeListener?.onSwipeProgress(signedDx)
                    handled = true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isSwiping) {
                    val rawDx = if (swipeDirection == SWIPE_DIRECTION_LEFT) startX - ev.rawX else ev.rawX - startX
                    val dx = (rawDx * swipeSensitivity).coerceAtLeast(0f)
                    val widthVal = width.toFloat().takeIf { it > 0 } ?: 1080f
                    val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                    val threshold = if (isLandscape) 0.18f else 0.40f
                    
                    var isFling = false
                    if (ev.action == MotionEvent.ACTION_UP) {
                        velocityTracker?.computeCurrentVelocity(1000)
                        val xVelocity = velocityTracker?.xVelocity ?: 0f
                        if (swipeDirection == SWIPE_DIRECTION_LEFT) {
                            if (xVelocity < -(minFlingVelocity * 0.25f)) {
                                isFling = true
                            }
                        } else {
                            if (xVelocity > (minFlingVelocity * 0.25f)) {
                                isFling = true
                            }
                        }
                    }
                    
                    val shouldClose = isFling || dx > widthVal * threshold
                    swipeListener?.onSwipeEnd(shouldClose)
                    isSwiping = false
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
