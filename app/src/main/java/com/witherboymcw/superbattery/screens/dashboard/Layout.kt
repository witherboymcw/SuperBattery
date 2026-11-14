package com.witherboymcw.superbattery.screens.dashboard

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.LinearLayout
import com.witherboymcw.superbattery.awareness.CornerAwareness
import com.witherboymcw.superbattery.templates.DrawerGestureHelper

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
    private var dragStartX = 0f
    private var currentDirection = SwipeDirection.NONE
    private val gestureHelper = DrawerGestureHelper(context)

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (canSwipe?.invoke() == false) {
            return super.onInterceptTouchEvent(ev)
        }
        if (ev.action == MotionEvent.ACTION_DOWN) {
            gestureHelper.recycleVelocityTracker()
        }
        gestureHelper.addMovement(ev)

        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = ev.rawX
                startY = ev.rawY
                dragStartX = ev.rawX
                currentDirection = SwipeDirection.NONE
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - startX
                val dy = ev.rawY - startY
                val effectiveDx = dx * swipeSensitivity
                if (currentDirection == SwipeDirection.NONE && gestureHelper.isHorizontalSwipe(dx, dy, swipeSensitivity)) {
                    if (!gestureHelper.isTouchInInteractiveChild(this, ev.rawX, ev.rawY)) {
                        if (effectiveDx < 0) {
                            currentDirection = SwipeDirection.LEFT_TO_SETTINGS
                        } else {
                            currentDirection = SwipeDirection.RIGHT_TO_HOTSPOT
                        }
                        dragStartX = ev.rawX
                        parent?.requestDisallowInterceptTouchEvent(true)
                        dualSwipeListener?.onSwipeStart(currentDirection)
                        if (currentDirection == SwipeDirection.LEFT_TO_SETTINGS) {
                            swipeListener?.onSwipeStart()
                        }
                    }
                }
            }
        }

        val result = currentDirection != SwipeDirection.NONE || super.onInterceptTouchEvent(ev)

        if (ev.action == MotionEvent.ACTION_UP || ev.action == MotionEvent.ACTION_CANCEL) {
            currentDirection = SwipeDirection.NONE
            gestureHelper.recycleVelocityTracker()
        }
        return result
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (canSwipe?.invoke() == false) {
            return super.onTouchEvent(ev)
        }
        if (ev.action == MotionEvent.ACTION_DOWN) {
            gestureHelper.recycleVelocityTracker()
        }
        gestureHelper.addMovement(ev)

        val screenWidth = width.toFloat().takeIf { it > 0 } ?: 1080f
        var handled = false

        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = ev.rawX
                startY = ev.rawY
                dragStartX = ev.rawX
                currentDirection = SwipeDirection.NONE
                handled = true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - startX
                val dy = ev.rawY - startY
                val effectiveDx = dx * swipeSensitivity

                if (currentDirection == SwipeDirection.NONE && gestureHelper.isHorizontalSwipe(dx, dy, swipeSensitivity)) {
                    if (!gestureHelper.isTouchInInteractiveChild(this, ev.rawX, ev.rawY)) {
                        if (effectiveDx < 0) {
                            currentDirection = SwipeDirection.LEFT_TO_SETTINGS
                        } else {
                            currentDirection = SwipeDirection.RIGHT_TO_HOTSPOT
                        }
                        dragStartX = ev.rawX
                        parent?.requestDisallowInterceptTouchEvent(true)
                        dualSwipeListener?.onSwipeStart(currentDirection)
                        if (currentDirection == SwipeDirection.LEFT_TO_SETTINGS) {
                            swipeListener?.onSwipeStart()
                        }
                    }
                }

                val dragDx = (ev.rawX - dragStartX) * swipeSensitivity

                if (currentDirection == SwipeDirection.LEFT_TO_SETTINGS) {
                    val targetX = (screenWidth + dragDx).coerceIn(0f, screenWidth)
                    dualSwipeListener?.onSwipeProgress(currentDirection, dragDx, targetX)
                    swipeListener?.onSwipeProgress(dragDx, targetX)
                    handled = true
                } else if (currentDirection == SwipeDirection.RIGHT_TO_HOTSPOT) {
                    val targetX = (-screenWidth + dragDx).coerceIn(-screenWidth, 0f)
                    dualSwipeListener?.onSwipeProgress(currentDirection, dragDx, targetX)
                    handled = true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (currentDirection != SwipeDirection.NONE) {
                    val dragDx = (ev.rawX - dragStartX) * swipeSensitivity
                    val threshold = gestureHelper.getSwipeThreshold(context)

                    val xVelocity = gestureHelper.computeXVelocity(1000)

                    if (currentDirection == SwipeDirection.LEFT_TO_SETTINGS) {
                        val currentX = (screenWidth + dragDx).coerceIn(0f, screenWidth)
                        val isFling = ev.action == MotionEvent.ACTION_UP && gestureHelper.isFlingLeft(xVelocity)
                        val shouldOpen = isFling || currentX < screenWidth * (1f - threshold)
                        dualSwipeListener?.onSwipeEnd(currentDirection, shouldOpen)
                        swipeListener?.onSwipeEnd(shouldOpen)
                    } else if (currentDirection == SwipeDirection.RIGHT_TO_HOTSPOT) {
                        val currentX = (-screenWidth + dragDx).coerceIn(-screenWidth, 0f)
                        val isFling = ev.action == MotionEvent.ACTION_UP && gestureHelper.isFlingRight(xVelocity)
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
            gestureHelper.recycleVelocityTracker()
        }

        return handled || super.onTouchEvent(ev)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun applyAsymmetricCorners(insets: androidx.core.view.WindowInsetsCompat) {
        CornerAwareness.applyAsymmetricCorners(this, insets)
    }
}
