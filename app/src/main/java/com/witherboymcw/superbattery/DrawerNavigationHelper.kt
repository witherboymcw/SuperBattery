package com.witherboymcw.superbattery

import android.view.View
import com.witherboymcw.superbattery.screens.dashboard.DashboardLayout
import com.witherboymcw.superbattery.screens.hotspot.HotspotViewController
import com.witherboymcw.superbattery.screens.settings.SettingsViewController
import com.witherboymcw.superbattery.screens.standby.StandbyViewController
import com.witherboymcw.superbattery.ui.Motion

/**
 * Helper responsible for orchestrating DashboardLayout swipe gestures
 * to open/close Settings, Hotspot, and Standby containers.
 */
internal object DrawerNavigationHelper {

    fun setupDashboardSwipeToOpen(
        activity: MainActivity,
        dashboardView: View,
        settingsViewController: SettingsViewController,
        hotspotViewController: HotspotViewController,
        standbyViewController: StandbyViewController
    ) {
        val containerSettings = activity.findViewById<View>(R.id.settings_container)
        val containerHotspot = activity.findViewById<View>(R.id.hotspot_container)
        val containerStandby = activity.findViewById<View>(R.id.standby_container)
        val dashboard = dashboardView as? DashboardLayout ?: return

        dashboard.swipeSensitivity = 1f
        dashboard.canSwipe = {
            !settingsViewController.isOpen && !hotspotViewController.isOpen && !standbyViewController.isOpen
        }
        dashboard.dualSwipeListener = object : DashboardLayout.DualSwipeListener {
            override fun onSwipeStart(direction: DashboardLayout.SwipeDirection) {
                val screenWidth = dashboard.width.toFloat().takeIf { it > 0 } ?: Motion.FALLBACK_WIDTH
                val screenHeight = dashboard.height.toFloat().takeIf { it > 0 } ?: Motion.FALLBACK_WIDTH
                if (direction == DashboardLayout.SwipeDirection.LEFT_TO_SETTINGS) {
                    containerSettings?.visibility = View.VISIBLE
                    containerSettings?.translationX = screenWidth
                    activity.dashboardViews.btnSettings?.rotation = 0f
                } else if (direction == DashboardLayout.SwipeDirection.RIGHT_TO_HOTSPOT) {
                    containerHotspot?.visibility = View.VISIBLE
                    containerHotspot?.translationX = -screenWidth
                } else if (direction == DashboardLayout.SwipeDirection.TOP_TO_STANDBY) {
                    standbyViewController.prepareForSwipe(activity)
                    containerStandby?.visibility = View.VISIBLE
                    containerStandby?.translationY = -screenHeight
                }
            }

            override fun onSwipeProgress(direction: DashboardLayout.SwipeDirection, delta: Float, targetVal: Float) {
                val screenWidth = dashboard.width.toFloat().takeIf { it > 0 } ?: Motion.FALLBACK_WIDTH
                if (direction == DashboardLayout.SwipeDirection.LEFT_TO_SETTINGS) {
                    containerSettings?.visibility = View.VISIBLE
                    containerSettings?.translationX = targetVal
                    val progress = (1f - (targetVal / screenWidth)).coerceIn(0f, 1f)
                    activity.dashboardViews.btnSettings?.rotation = progress * 90f
                } else if (direction == DashboardLayout.SwipeDirection.RIGHT_TO_HOTSPOT) {
                    containerHotspot?.visibility = View.VISIBLE
                    containerHotspot?.translationX = targetVal
                } else if (direction == DashboardLayout.SwipeDirection.TOP_TO_STANDBY) {
                    standbyViewController.prepareForSwipe(activity)
                    containerStandby?.visibility = View.VISIBLE
                    containerStandby?.translationY = targetVal
                }
            }

            override fun onSwipeEnd(direction: DashboardLayout.SwipeDirection, shouldOpen: Boolean) {
                val screenWidth = dashboard.width.toFloat().takeIf { it > 0 } ?: Motion.FALLBACK_WIDTH
                val screenHeight = dashboard.height.toFloat().takeIf { it > 0 } ?: Motion.FALLBACK_WIDTH
                if (direction == DashboardLayout.SwipeDirection.LEFT_TO_SETTINGS) {
                    if (shouldOpen) {
                        settingsViewController.openScreen(activity, fromCurrent = true)
                    } else {
                        settingsViewController.isOpen = false
                        containerSettings?.animate()?.cancel()
                        val remainingDistance = Math.abs(screenWidth - (containerSettings?.translationX ?: screenWidth))
                        val ratio = (remainingDistance / screenWidth).coerceIn(0f, 1f)
                        val duration = (ratio * 250L).toLong().coerceIn(100L, 250L)
                        containerSettings?.animate()
                            ?.translationX(screenWidth)
                            ?.setDuration(duration)
                            ?.setInterpolator(Motion.interpolator)
                            ?.withLayer()
                            ?.withEndAction { containerSettings.visibility = View.GONE }
                            ?.start()
                        val btnSettings = activity.dashboardViews.btnSettings
                        btnSettings?.animate()?.cancel()
                        btnSettings?.animate()
                            ?.rotation(0f)
                            ?.setDuration(duration)
                            ?.setInterpolator(Motion.interpolator)
                            ?.start()
                    }
                } else if (direction == DashboardLayout.SwipeDirection.RIGHT_TO_HOTSPOT) {
                    if (shouldOpen) {
                        hotspotViewController.openScreen(activity, fromCurrent = true)
                    } else {
                        hotspotViewController.isOpen = false
                        containerHotspot?.animate()?.cancel()
                        val remainingDistance = Math.abs(-screenWidth - (containerHotspot?.translationX ?: -screenWidth))
                        val ratio = (remainingDistance / screenWidth).coerceIn(0f, 1f)
                        val duration = (ratio * 250L).toLong().coerceIn(100L, 250L)
                        containerHotspot?.animate()
                            ?.translationX(-screenWidth)
                            ?.setDuration(duration)
                            ?.setInterpolator(Motion.interpolator)
                            ?.withLayer()
                            ?.withEndAction { containerHotspot.visibility = View.GONE }
                            ?.start()
                    }
                } else if (direction == DashboardLayout.SwipeDirection.TOP_TO_STANDBY) {
                    if (shouldOpen) {
                        standbyViewController.openScreen(activity, fromCurrent = true)
                    } else {
                        standbyViewController.isOpen = false
                        containerStandby?.animate()?.cancel()
                        val remainingDistance = Math.abs(-screenHeight - (containerStandby?.translationY ?: -screenHeight))
                        val ratio = (remainingDistance / screenHeight).coerceIn(0f, 1f)
                        val duration = (ratio * 250L).toLong().coerceIn(100L, 250L)
                        containerStandby?.animate()
                            ?.translationY(-screenHeight)
                            ?.setDuration(duration)
                            ?.setInterpolator(Motion.interpolator)
                            ?.withLayer()
                            ?.withEndAction { containerStandby.visibility = View.GONE }
                            ?.start()
                    }
                }
            }
        }
    }
}
