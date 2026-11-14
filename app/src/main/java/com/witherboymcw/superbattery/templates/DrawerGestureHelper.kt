package com.witherboymcw.superbattery.templates

import android.content.Context
import android.content.res.Configuration
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.EditText
import android.widget.SeekBar
import kotlin.math.abs

/**
 * Shared helper responsible for horizontal swipe gesture detection, VelocityTracker lifecycle,
 * non-blocking view checks (SeekBar, EditText, etc.), and touch threshold calculations.
 */
class DrawerGestureHelper(context: Context) {

    val touchSlop: Float = ViewConfiguration.get(context).scaledTouchSlop.toFloat() * 0.25f
    val minFlingVelocity: Float = ViewConfiguration.get(context).scaledMinimumFlingVelocity.toFloat()

    private var velocityTracker: VelocityTracker? = null

    // Pre-allocated array to prevent allocations during ACTION_MOVE / onTouchEvent
    private val tempLocation = IntArray(2)

    fun addMovement(ev: MotionEvent) {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain()
        }
        velocityTracker?.addMovement(ev)
    }

    fun recycleVelocityTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

    fun computeXVelocity(units: Int = 1000): Float {
        velocityTracker?.computeCurrentVelocity(units)
        return velocityTracker?.xVelocity ?: 0f
    }

    fun isHorizontalSwipe(dx: Float, dy: Float, sensitivity: Float = 1f): Boolean {
        val rawDx = abs(dx)
        val rawDy = abs(dy)
        val effectiveDx = rawDx * sensitivity
        return effectiveDx > touchSlop && rawDx > rawDy * 0.8f
    }

    fun isFlingLeft(xVelocity: Float): Boolean {
        return xVelocity < -(minFlingVelocity * 0.15f)
    }

    fun isFlingRight(xVelocity: Float): Boolean {
        return xVelocity > (minFlingVelocity * 0.15f)
    }

    fun getSwipeThreshold(context: Context): Float {
        val isLandscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        return if (isLandscape) 0.12f else 0.22f
    }

    fun isTouchInView(view: View?, rawX: Float, rawY: Float): Boolean {
        if (view == null || view.visibility != View.VISIBLE) return false
        view.getLocationOnScreen(tempLocation)
        val left = tempLocation[0]
        val top = tempLocation[1]
        val right = left + view.width
        val bottom = top + view.height
        return rawX >= left && rawX <= right && rawY >= top && rawY <= bottom
    }

    fun isTouchInInteractiveChild(parent: ViewGroup, rawX: Float, rawY: Float): Boolean {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i) ?: continue
            if (child.visibility != View.VISIBLE) continue
            if (isTouchInView(child, rawX, rawY)) {
                if (child is SeekBar || child is EditText) return true
                if (child is ViewGroup && isTouchInInteractiveChild(child, rawX, rawY)) return true
            }
        }
        return false
    }
}
