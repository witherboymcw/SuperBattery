package com.witherboymcw.superbattery

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout
import com.witherboymcw.superbattery.awareness.FoldableAwareness
import com.witherboymcw.superbattery.ui.DrawerGestureHelper
import com.witherboymcw.superbattery.ui.Motion

open class SwipeBackFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        const val SWIPE_DIRECTION_RIGHT = 1 // Swiping right closes container (e.g., Settings on right)
        const val SWIPE_DIRECTION_LEFT = -1  // Swiping left closes container (e.g., Hotspot on left)
        const val SWIPE_DIRECTION_UP = 2    // Swiping up closes container (e.g., Standby on top)
    }

    interface SwipeListener {
        fun onSwipeProgress(translation: Float)
        fun onSwipeEnd(shouldClose: Boolean)
    }

    var swipeListener: SwipeListener? = null
    var swipeSensitivity: Float = 1f
    var swipeDirection: Int = SWIPE_DIRECTION_RIGHT

    private var startX = 0f
    private var startY = 0f
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var isSwiping = false
    private val gestureHelper = DrawerGestureHelper(context)

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            gestureHelper.recycleVelocityTracker()
        }
        gestureHelper.addMovement(ev)

        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = ev.rawX
                startY = ev.rawY
                dragStartX = ev.rawX
                dragStartY = ev.rawY
                isSwiping = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - startX
                val dy = ev.rawY - startY
                if (swipeDirection == SWIPE_DIRECTION_UP) {
                    val rawDy = -dy
                    if (!isSwiping && rawDy > 0 && gestureHelper.isVerticalSwipe(dx, dy, swipeSensitivity)) {
                        if (!gestureHelper.isTouchInInteractiveChild(this, ev.rawX, ev.rawY)) {
                            isSwiping = true
                            dragStartY = ev.rawY
                            parent?.requestDisallowInterceptTouchEvent(true)
                        }
                    }
                } else {
                    val rawDx = if (swipeDirection == SWIPE_DIRECTION_LEFT) -dx else dx
                    if (!isSwiping && rawDx > 0 && gestureHelper.isHorizontalSwipe(rawDx, dy, swipeSensitivity)) {
                        if (!gestureHelper.isTouchInInteractiveChild(this, ev.rawX, ev.rawY)) {
                            isSwiping = true
                            dragStartX = ev.rawX
                            parent?.requestDisallowInterceptTouchEvent(true)
                        }
                    }
                }
            }
        }

        val result = isSwiping || super.onInterceptTouchEvent(ev)

        if (ev.action == MotionEvent.ACTION_UP || ev.action == MotionEvent.ACTION_CANCEL) {
            isSwiping = false
            gestureHelper.recycleVelocityTracker()
        }

        return result
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            gestureHelper.recycleVelocityTracker()
        }
        gestureHelper.addMovement(ev)

        var handled = false

        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = ev.rawX
                startY = ev.rawY
                dragStartX = ev.rawX
                dragStartY = ev.rawY
                isSwiping = false
                handled = true // Need to handle DOWN to receive MOVE
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - startX
                val dy = ev.rawY - startY
                if (swipeDirection == SWIPE_DIRECTION_UP) {
                    val rawDy = -dy
                    if (!isSwiping && rawDy > 0 && gestureHelper.isVerticalSwipe(dx, dy, swipeSensitivity)) {
                        if (!gestureHelper.isTouchInInteractiveChild(this, ev.rawX, ev.rawY)) {
                            isSwiping = true
                            dragStartY = ev.rawY
                            parent?.requestDisallowInterceptTouchEvent(true)
                        }
                    }
                    if (isSwiping) {
                        val dragDy = ev.rawY - dragStartY
                        val clampedDy = (-dragDy * swipeSensitivity).coerceAtLeast(0f)
                        swipeListener?.onSwipeProgress(-clampedDy)
                        handled = true
                    }
                } else {
                    val rawDx = if (swipeDirection == SWIPE_DIRECTION_LEFT) -dx else dx
                    if (!isSwiping && rawDx > 0 && gestureHelper.isHorizontalSwipe(rawDx, dy, swipeSensitivity)) {
                        if (!gestureHelper.isTouchInInteractiveChild(this, ev.rawX, ev.rawY)) {
                            isSwiping = true
                            dragStartX = ev.rawX
                            parent?.requestDisallowInterceptTouchEvent(true)
                        }
                    }
                    if (isSwiping) {
                        val dragDx = ev.rawX - dragStartX
                        val rawClampedDx = if (swipeDirection == SWIPE_DIRECTION_LEFT) -dragDx else dragDx
                        val clampedDx = (rawClampedDx * swipeSensitivity).coerceAtLeast(0f)
                        val signedDx = if (swipeDirection == SWIPE_DIRECTION_LEFT) -clampedDx else clampedDx
                        swipeListener?.onSwipeProgress(signedDx)
                        handled = true
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isSwiping) {
                    if (swipeDirection == SWIPE_DIRECTION_UP) {
                        val dragDy = ev.rawY - dragStartY
                        val clampedDy = (-dragDy * swipeSensitivity).coerceAtLeast(0f)
                        val heightVal = height.toFloat().takeIf { it > 0 } ?: Motion.FALLBACK_WIDTH
                        val activity = context as? Activity
                        val isMiniSquare = activity != null && FoldableAwareness.isMiniSquareMode(activity)
                        val threshold = gestureHelper.getSwipeThreshold(context, isMiniSquare)

                        var isFling = false
                        if (ev.action == MotionEvent.ACTION_UP) {
                            val yVelocity = gestureHelper.computeYVelocity(1000)
                            isFling = gestureHelper.isFlingUp(yVelocity)
                        }

                        val shouldClose = isFling || clampedDy > heightVal * threshold
                        swipeListener?.onSwipeEnd(shouldClose)
                    } else {
                        val dragDx = ev.rawX - dragStartX
                        val rawClampedDx = if (swipeDirection == SWIPE_DIRECTION_LEFT) -dragDx else dragDx
                        val clampedDx = (rawClampedDx * swipeSensitivity).coerceAtLeast(0f)
                        val widthVal = width.toFloat().takeIf { it > 0 } ?: Motion.FALLBACK_WIDTH
                        val activity = context as? Activity
                        val isMiniSquare = activity != null && FoldableAwareness.isMiniSquareMode(activity)
                        val threshold = gestureHelper.getSwipeThreshold(context, isMiniSquare)

                        var isFling = false
                        if (ev.action == MotionEvent.ACTION_UP) {
                            val xVelocity = gestureHelper.computeXVelocity(1000)
                            isFling = if (swipeDirection == SWIPE_DIRECTION_LEFT) {
                                gestureHelper.isFlingLeft(xVelocity)
                            } else {
                                gestureHelper.isFlingRight(xVelocity)
                            }
                        }

                        val shouldClose = isFling || clampedDx > widthVal * threshold
                        swipeListener?.onSwipeEnd(shouldClose)
                    }
                    isSwiping = false
                    handled = true
                } else if (ev.action == MotionEvent.ACTION_UP) {
                    performClick()
                }
            }
        }

        if (ev.action == MotionEvent.ACTION_UP || ev.action == MotionEvent.ACTION_CANCEL) {
            gestureHelper.recycleVelocityTracker()
        }

        return handled || super.onTouchEvent(ev)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
