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
        FROM_LEFT
    }

    protected var activityRef: ComponentActivity? = null
    var isOpen: Boolean = false

    private val signMultiplier: Float
        get() = if (slideDirection == SlideDirection.FROM_RIGHT) 1f else -1f

    val backCallback = object : OnBackPressedCallback(false) {
        private var initialWidth = 0f

        override fun handleOnBackStarted(backEvent: BackEventCompat) {
            val container = activityRef?.findViewById<View>(containerId) ?: return
            initialWidth = container.width.toFloat().takeIf { it > 0f } ?: Motion.FALLBACK_WIDTH
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
                .setInterpolator(Motion.interpolator)
                .start()
        }

        override fun handleOnBackPressed() {
            val act = activityRef ?: return
            closeScreen(act)
        }
    }

    open fun openScreen(activity: ComponentActivity) {
        val container = activity.findViewById<View>(containerId) ?: return
        isOpen = true
        container.visibility = View.VISIBLE
        val width = container.width.toFloat().takeIf { it > 0 } ?: Motion.FALLBACK_WIDTH
        container.translationX = signMultiplier * width
        container.animate()
            .translationX(0f)
            .setDuration(280L)
            .setInterpolator(Motion.interpolator)
            .withLayer()
            .start()

        animateActionButtonOnOpen(activity)
        backCallback.isEnabled = true
        onPostOpen(activity)
    }

    open fun closeScreen(activity: ComponentActivity, animate: Boolean = true) {
        onPreClose()
        val container = activity.findViewById<View>(containerId) ?: return
        if (container.visibility != View.VISIBLE) return
        isOpen = false
        backCallback.isEnabled = false
        if (animate) {
            val width = container.width.toFloat().takeIf { it > 0 } ?: Motion.FALLBACK_WIDTH
            container.animate()
                .translationX(signMultiplier * width)
                .setDuration(280L)
                .setInterpolator(Motion.interpolator)
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

    open fun restoreOpenState(activity: ComponentActivity) {
        val container = activity.findViewById<View>(containerId) ?: return
        container.visibility = View.VISIBLE
        container.translationX = 0f
        backCallback.isEnabled = true
        isOpen = true
        animateActionButtonOnRestore(activity)
    }

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

    private fun animateActionButtonOnOpen(activity: ComponentActivity) {
        resolveActionButton(activity)?.animate()
            ?.rotation(90f)
            ?.setDuration(280L)
            ?.setInterpolator(Motion.interpolator)
            ?.start()
    }

    private fun animateActionButtonOnClose(activity: ComponentActivity, animate: Boolean) {
        val btn = resolveActionButton(activity) ?: return
        if (animate) {
            btn.animate()
                .rotation(0f)
                .setDuration(280L)
                .setInterpolator(Motion.interpolator)
                .start()
        } else {
            btn.animate().cancel()
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
