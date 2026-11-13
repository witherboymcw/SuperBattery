package com.witherboymcw.superbattery.templates

import android.view.View
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.annotation.IdRes
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.witherboymcw.superbattery.MainActivity

open class DrawerScreen(
    @param:IdRes private val containerId: Int,
    private val slideDirection: SlideDirection = SlideDirection.FROM_RIGHT,
    @param:IdRes private val actionButtonId: Int? = null,
) {
    enum class SlideDirection {
        FROM_RIGHT,
        FROM_LEFT
    }

    protected var activityRef: MainActivity? = null
    var isOpen: Boolean = false

    private val signMultiplier: Float
        get() = if (slideDirection == SlideDirection.FROM_RIGHT) 1f else -1f

    val backCallback = object : OnBackPressedCallback(false) {
        private var initialWidth = 0f

        override fun handleOnBackStarted(backEvent: BackEventCompat) {
            val container = activityRef?.findViewById<View>(containerId) ?: return
            initialWidth = container.width.toFloat().takeIf { it > 0f } ?: 1080f
        }

        override fun handleOnBackProgressed(backEvent: BackEventCompat) {
            val container = activityRef?.findViewById<View>(containerId) ?: return
            val progress = backEvent.progress
            container.translationX = signMultiplier * initialWidth * progress
        }

        override fun handleOnBackCancelled() {
            val container = activityRef?.findViewById<View>(containerId) ?: return
            container.animate()
                .translationX(0f)
                .setDuration(150L)
                .setInterpolator(FastOutSlowInInterpolator())
                .start()
        }

        override fun handleOnBackPressed() {
            val act = activityRef ?: return
            closeScreen(act)
        }
    }

    open fun openScreen(activity: MainActivity) {
        val container = activity.findViewById<View>(containerId) ?: return
        isOpen = true
        container.visibility = View.VISIBLE
        val width = container.width.toFloat().takeIf { it > 0 } ?: 1080f
        container.translationX = signMultiplier * width
        container.animate()
            .translationX(0f)
            .setDuration(280L)
            .setInterpolator(FastOutSlowInInterpolator())
            .withLayer()
            .start()

        animateActionButtonOnOpen(activity)
        backCallback.isEnabled = true
        onPostOpen(activity)
    }

    open fun closeScreen(activity: MainActivity, animate: Boolean = true) {
        onPreClose()
        val container = activity.findViewById<View>(containerId) ?: return
        if (container.visibility != View.VISIBLE) return
        isOpen = false
        backCallback.isEnabled = false
        if (animate) {
            val width = container.width.toFloat().takeIf { it > 0 } ?: 1080f
            container.animate()
                .translationX(signMultiplier * width)
                .setDuration(280L)
                .setInterpolator(FastOutSlowInInterpolator())
                .withLayer()
                .withEndAction {
                    container.visibility = View.GONE
                }
                .start()
            animateActionButtonOnClose(activity, animate = true)
        } else {
            container.animate().cancel()
            container.visibility = View.GONE
            container.translationX = 0f
            animateActionButtonOnClose(activity, animate = false)
        }
        onPostClose(activity)
    }

    open fun restoreOpenState(activity: MainActivity) {
        val container = activity.findViewById<View>(containerId) ?: return
        container.visibility = View.VISIBLE
        container.translationX = 0f
        backCallback.isEnabled = true
        isOpen = true
        animateActionButtonOnRestore(activity)
    }

    protected open fun onPreClose() {}
    protected open fun onPostOpen(activity: MainActivity) {}
    protected open fun onPostClose(activity: MainActivity) {}

    private fun animateActionButtonOnOpen(activity: MainActivity) {
        if (actionButtonId != null) {
            val btn = activity.findViewById<View>(actionButtonId) ?: activity.btnSettings
            btn?.animate()
                ?.rotation(90f)
                ?.setDuration(280L)
                ?.setInterpolator(FastOutSlowInInterpolator())
                ?.start()
        }
    }

    private fun animateActionButtonOnClose(activity: MainActivity, animate: Boolean) {
        if (actionButtonId != null) {
            val btn = activity.findViewById<View>(actionButtonId) ?: activity.btnSettings
            if (animate) {
                btn?.animate()
                    ?.rotation(0f)
                    ?.setDuration(280L)
                    ?.setInterpolator(FastOutSlowInInterpolator())
                    ?.start()
            } else {
                btn?.animate()?.cancel()
                btn?.rotation = 0f
            }
        }
    }

    private fun animateActionButtonOnRestore(activity: MainActivity) {
        if (actionButtonId != null) {
            val btn = activity.findViewById<View>(actionButtonId) ?: activity.btnSettings
            btn?.rotation = 90f
        }
    }

    open fun unbind() {
        backCallback.remove()
        activityRef = null
    }
}
