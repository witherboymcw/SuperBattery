package com.witherboymcw.superbattery.ui

import android.view.View
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.annotation.IdRes

/**
 * Base class for slide-in drawer screens (Settings, Hotspot).
 *
 * Uses [ComponentActivity] instead of the concrete MainActivity to remove the
 * upward dependency on the root package. For action-button animation the host
 * Activity must implement [SettingsHost]; if it does not, button animations are
 * silently skipped.
 */
open class DrawerScreen(
    @param:IdRes private val containerId: Int,
    private val slideDirection: SlideDirection = SlideDirection.FROM_RIGHT,
    @param:IdRes private val actionButtonId: Int? = null,
) {
    enum class SlideDirection {
        FROM_RIGHT,
        FROM_LEFT,
        FROM_TOP
    }

    protected var activityRef: ComponentActivity? = null
    var isOpen: Boolean = false

    private val signMultiplier: Float
        get() = if (slideDirection == SlideDirection.FROM_RIGHT) 1f else -1f

    val backCallback = object : OnBackPressedCallback(false) {
        private var initialSize = 0f

        override fun handleOnBackStarted(backEvent: BackEventCompat) {
            val container = activityRef?.findViewById<View>(containerId) ?: return
            if (slideDirection == SlideDirection.FROM_TOP) {
                initialSize = container.height.toFloat().takeIf { it > 0f }
                    ?: activityRef?.resources?.displayMetrics?.heightPixels?.toFloat()
                    ?: Motion.FALLBACK_WIDTH
            } else {
                initialSize = container.width.toFloat().takeIf { it > 0f } ?: Motion.FALLBACK_WIDTH
            }
        }

        override fun handleOnBackProgressed(backEvent: BackEventCompat) {
            val container = activityRef?.findViewById<View>(containerId) ?: return
            val progress = backEvent.progress
            if (slideDirection == SlideDirection.FROM_TOP) {
                container.translationY = -initialSize * progress
            } else {
                container.translationX = signMultiplier * initialSize * progress
            }
        }

        override fun handleOnBackCancelled() {
            val container = activityRef?.findViewById<View>(containerId) ?: return
            container.animate().cancel()
            if (slideDirection == SlideDirection.FROM_TOP) {
                val height = container.height.toFloat().takeIf { it > 0f }
                    ?: activityRef?.resources?.displayMetrics?.heightPixels?.toFloat()
                    ?: Motion.FALLBACK_WIDTH
                val remainingDistance = Math.abs(container.translationY)
                val remainingRatio = (remainingDistance / height).coerceIn(0f, 1f)
                val duration = (remainingRatio * 250L).toLong().coerceIn(100L, 250L)
                container.animate()
                    .translationY(0f)
                    .setDuration(duration)
                    .setInterpolator(Motion.interpolator)
                    .start()
            } else {
                val width = container.width.toFloat().takeIf { it > 0f } ?: Motion.FALLBACK_WIDTH
                val remainingDistance = Math.abs(container.translationX)
                val remainingRatio = (remainingDistance / width).coerceIn(0f, 1f)
                val duration = (remainingRatio * 250L).toLong().coerceIn(100L, 250L)
                container.animate()
                    .translationX(0f)
                    .setDuration(duration)
                    .setInterpolator(Motion.interpolator)
                    .start()
            }
        }

        override fun handleOnBackPressed() {
            val act = activityRef ?: return
            closeScreen(act)
        }
    }

    open fun openScreen(activity: ComponentActivity, fromCurrent: Boolean = false) {
        val container = activity.findViewById<View>(containerId) ?: return
        container.animate().cancel()
        isOpen = true
        onPreOpen(activity)
        container.visibility = View.VISIBLE
        if (slideDirection == SlideDirection.FROM_TOP) {
            val height = container.height.toFloat().takeIf { it > 0 }
                ?: activity.resources.displayMetrics.heightPixels.toFloat()
            if (!fromCurrent) {
                container.translationY = -height
            }
            val remainingDistance = Math.abs(container.translationY)
            val remainingRatio = (remainingDistance / height).coerceIn(0f, 1f)
            val duration = if (fromCurrent) {
                (remainingRatio * 250L).toLong().coerceIn(100L, 250L)
            } else {
                280L
            }
            container.animate()
                .translationY(0f)
                .setDuration(duration)
                .setInterpolator(Motion.interpolator)
                .withLayer()
                .withEndAction {
                    onPostOpen(activity)
                }
                .start()
            animateActionButtonOnOpen(activity, duration)
        } else {
            val width = container.width.toFloat().takeIf { it > 0 } ?: Motion.FALLBACK_WIDTH
            if (!fromCurrent) {
                container.translationX = signMultiplier * width
            }
            val remainingDistance = Math.abs(container.translationX)
            val remainingRatio = (remainingDistance / width).coerceIn(0f, 1f)
            val duration = if (fromCurrent) {
                (remainingRatio * 250L).toLong().coerceIn(100L, 250L)
            } else {
                280L
            }
            container.animate()
                .translationX(0f)
                .setDuration(duration)
                .setInterpolator(Motion.interpolator)
                .withLayer()
                .withEndAction {
                    onPostOpen(activity)
                }
                .start()
            animateActionButtonOnOpen(activity, duration)
        }

        backCallback.isEnabled = true
    }

    open fun closeScreen(activity: ComponentActivity, animate: Boolean = true) {
        onPreClose()
        val container = activity.findViewById<View>(containerId) ?: return
        container.animate().cancel()
        if (container.visibility != View.VISIBLE) return
        isOpen = false
        backCallback.isEnabled = false
        if (animate) {
            if (slideDirection == SlideDirection.FROM_TOP) {
                val height = container.height.toFloat().takeIf { it > 0 }
                    ?: activity.resources.displayMetrics.heightPixels.toFloat()
                val remainingDistance = Math.abs(-height - container.translationY)
                val remainingRatio = (remainingDistance / height).coerceIn(0f, 1f)
                val duration = (remainingRatio * 250L).toLong().coerceIn(100L, 250L)
                container.animate()
                    .translationY(-height)
                    .setDuration(duration)
                    .setInterpolator(Motion.interpolator)
                    .withLayer()
                    .withEndAction {
                        container.visibility = View.GONE
                    }
                    .start()
                animateActionButtonOnClose(activity, animate = true, duration = duration)
            } else {
                val width = container.width.toFloat().takeIf { it > 0 } ?: Motion.FALLBACK_WIDTH
                val targetX = signMultiplier * width
                val remainingDistance = Math.abs(targetX - container.translationX)
                val remainingRatio = (remainingDistance / width).coerceIn(0f, 1f)
                val duration = (remainingRatio * 250L).toLong().coerceIn(100L, 250L)
                container.animate()
                    .translationX(targetX)
                    .setDuration(duration)
                    .setInterpolator(Motion.interpolator)
                    .withLayer()
                    .withEndAction {
                        container.visibility = View.GONE
                    }
                    .start()
                animateActionButtonOnClose(activity, animate = true, duration = duration)
            }
        } else {
            container.visibility = View.GONE
            container.translationX = 0f
            container.translationY = 0f
            animateActionButtonOnClose(activity, animate = false)
        }
        onPostClose(activity)
    }

    open fun restoreOpenState(activity: ComponentActivity) {
        val container = activity.findViewById<View>(containerId) ?: return
        container.animate().cancel()
        onPreOpen(activity)
        container.visibility = View.VISIBLE
        container.translationX = 0f
        container.translationY = 0f
        backCallback.isEnabled = true
        isOpen = true
        animateActionButtonOnRestore(activity)
        onPostOpen(activity)
    }

    protected open fun onPreOpen(activity: ComponentActivity) {}
    protected open fun onPreClose() {}
    protected open fun onPostOpen(activity: ComponentActivity) {}
    protected open fun onPostClose(activity: ComponentActivity) {}

    private fun resolveActionButton(activity: ComponentActivity): View? {
        if (actionButtonId == null) return null
        val byId = activity.findViewById<View>(actionButtonId)
        if (byId != null) return byId
        // If the host implements SettingsHost, fall back to its declared button
        return (activity as? SettingsHost)?.settingsActionButton
    }

    private fun animateActionButtonOnOpen(activity: ComponentActivity, duration: Long = 280L) {
        val btn = resolveActionButton(activity) ?: return
        btn.animate().cancel()
        btn.animate()
            .rotation(90f)
            .setDuration(duration)
            .setInterpolator(Motion.interpolator)
            .start()
    }

    private fun animateActionButtonOnClose(activity: ComponentActivity, animate: Boolean, duration: Long = 280L) {
        val btn = resolveActionButton(activity) ?: return
        btn.animate().cancel()
        if (animate) {
            btn.animate()
                .rotation(0f)
                .setDuration(duration)
                .setInterpolator(Motion.interpolator)
                .start()
        } else {
            btn.rotation = 0f
        }
    }

    private fun animateActionButtonOnRestore(activity: ComponentActivity) {
        resolveActionButton(activity)?.rotation = 90f
    }

    open fun unbind() {
        backCallback.remove()
        activityRef = null
    }
}
