package com.witherboymcw.superbattery

import android.view.View
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.witherboymcw.superbattery.screens.dashboard.DashboardLayout
import com.witherboymcw.superbattery.screens.hotspot.HotspotViewController
import com.witherboymcw.superbattery.screens.settings.SettingsViewController

/**
 * Helper responsible for orchestrating DashboardLayout dual swipe gestures
 * to open/close Settings and Hotspot containers.
 */
internal object DrawerNavigationHelper {

    fun setupDashboardSwipeToOpen(
        activity: MainActivity,
        dashboardView: View,
        settingsViewController: SettingsViewController,
        hotspotViewController: HotspotViewController
    ) {
        val containerSettings = activity.findViewById<View>(R.id.settings_container)
        val containerHotspot = activity.findViewById<View>(R.id.hotspot_container)
        val dashboard = dashboardView as? DashboardLayout ?: return

        dashboard.swipeSensitivity = 1f
        dashboard.canSwipe = { !settingsViewController.isOpen && !hotspotViewController.isOpen }
        dashboard.dualSwipeListener = object : DashboardLayout.DualSwipeListener {
            override fun onSwipeStart(direction: DashboardLayout.SwipeDirection) {
                val screenWidth = dashboard.width.toFloat().takeIf { it > 0 } ?: 1080f
                if (direction == DashboardLayout.SwipeDirection.LEFT_TO_SETTINGS) {
                    containerSettings?.visibility = View.VISIBLE
                    containerSettings?.translationX = screenWidth
                    activity.btnSettings?.rotation = 0f
                } else if (direction == DashboardLayout.SwipeDirection.RIGHT_TO_HOTSPOT) {
                    containerHotspot?.visibility = View.VISIBLE
                    containerHotspot?.translationX = -screenWidth
                }
            }

            override fun onSwipeProgress(direction: DashboardLayout.SwipeDirection, dx: Float, targetX: Float) {
                val screenWidth = dashboard.width.toFloat().takeIf { it > 0 } ?: 1080f
                if (direction == DashboardLayout.SwipeDirection.LEFT_TO_SETTINGS) {
                    containerSettings?.visibility = View.VISIBLE
                    containerSettings?.translationX = targetX
                    val progress = (1f - (targetX / screenWidth)).coerceIn(0f, 1f)
                    activity.btnSettings?.rotation = progress * 90f
                } else if (direction == DashboardLayout.SwipeDirection.RIGHT_TO_HOTSPOT) {
                    containerHotspot?.visibility = View.VISIBLE
                    containerHotspot?.translationX = targetX
                }
            }

            override fun onSwipeEnd(direction: DashboardLayout.SwipeDirection, shouldOpen: Boolean) {
                val screenWidth = dashboard.width.toFloat().takeIf { it > 0 } ?: 1080f
                if (direction == DashboardLayout.SwipeDirection.LEFT_TO_SETTINGS) {
                    if (shouldOpen) {
                        settingsViewController.isOpen = true
                        containerSettings?.animate()
                            ?.translationX(0f)
                            ?.setDuration(200L)
                            ?.setInterpolator(FastOutSlowInInterpolator())
                            ?.withLayer()
                            ?.start()
                        activity.btnSettings?.animate()
                            ?.rotation(90f)
                            ?.setDuration(200L)
                            ?.setInterpolator(FastOutSlowInInterpolator())
                            ?.start()
                        settingsViewController.backCallback.isEnabled = true
                    } else {
                        settingsViewController.isOpen = false
                        containerSettings?.animate()
                            ?.translationX(screenWidth)
                            ?.setDuration(200L)
                            ?.setInterpolator(FastOutSlowInInterpolator())
                            ?.withLayer()
                            ?.withEndAction { containerSettings.visibility = View.GONE }
                            ?.start()
                        activity.btnSettings?.animate()
                            ?.rotation(0f)
                            ?.setDuration(200L)
                            ?.setInterpolator(FastOutSlowInInterpolator())
                            ?.start()
                    }
                } else if (direction == DashboardLayout.SwipeDirection.RIGHT_TO_HOTSPOT) {
                    if (shouldOpen) {
                        hotspotViewController.isOpen = true
                        containerHotspot?.animate()
                            ?.translationX(0f)
                            ?.setDuration(200L)
                            ?.setInterpolator(FastOutSlowInInterpolator())
                            ?.withLayer()
                            ?.start()
                        hotspotViewController.backCallback.isEnabled = true
                    } else {
                        hotspotViewController.isOpen = false
                        containerHotspot?.animate()
                            ?.translationX(-screenWidth)
                            ?.setDuration(200L)
                            ?.setInterpolator(FastOutSlowInInterpolator())
                            ?.withLayer()
                            ?.withEndAction { containerHotspot.visibility = View.GONE }
                            ?.start()
                    }
                }
            }
        }
    }
}
