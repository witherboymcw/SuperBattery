package com.witherboymcw.superbattery

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.TypedValue
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView

object NotchHeaderHelper {

    enum class CutoutPosition {
        NONE,
        CENTER,
        TOP_LEFT,
        TOP_RIGHT
    }

    data class CutoutInfo(
        val position: CutoutPosition,
        val rectLeft: Float = 0f,
        val rectRight: Float = 0f
    )

    fun getCutoutInfo(context: Context, rootViewGroup: View): CutoutInfo {
        val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            rootViewGroup.display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
        }

        if (context.resources.configuration.orientation != Configuration.ORIENTATION_PORTRAIT || rotation == Surface.ROTATION_180) {
            return CutoutInfo(CutoutPosition.NONE)
        }

        val prefs = context.getSharedPreferences(BatteryWidget.SETTINGS_PREFS, Context.MODE_PRIVATE)
        val notchMode = prefs.getInt("notch_custom_mode", 0) // 0: System, 1: Force/Custom, 2: Force Ignore

        if (notchMode == 2) {
            return CutoutInfo(CutoutPosition.NONE)
        }

        val screenWidth = rootViewGroup.width.toFloat().takeIf { it > 0f }
            ?: context.resources.displayMetrics.widthPixels.toFloat()

        if (notchMode == 1) { // Force/Custom simulated notch
            val isCentered = prefs.getBoolean("notch_is_center", true)
            val nX = prefs.getFloat("notch_x", -1f)
            val notchRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24f, context.resources.displayMetrics)

            if (isCentered || nX < 0f) {
                val cX = screenWidth / 2f
                return CutoutInfo(CutoutPosition.CENTER, cX - notchRadius, cX + notchRadius)
            } else {
                val cX = nX
                val leftBound = cX - notchRadius
                val rightBound = cX + notchRadius
                val pos = when {
                    cX < screenWidth * 0.35f -> CutoutPosition.TOP_LEFT
                    cX > screenWidth * 0.65f -> CutoutPosition.TOP_RIGHT
                    else -> CutoutPosition.CENTER
                }
                return CutoutInfo(pos, leftBound, rightBound)
            }
        }

        // System cutout (notchMode == 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val insets = rootViewGroup.rootWindowInsets
            val cutout = insets?.displayCutout
            val rects = cutout?.boundingRects
            if (!rects.isNullOrEmpty()) {
                val topCutout = rects.firstOrNull { it.top <= 200 } ?: rects.first()
                if (topCutout.width() > 0 && topCutout.bottom > 0) {
                    val cX = topCutout.centerX().toFloat()
                    val pos = when {
                        cX < screenWidth * 0.35f -> CutoutPosition.TOP_LEFT
                        cX > screenWidth * 0.65f -> CutoutPosition.TOP_RIGHT
                        else -> CutoutPosition.CENTER
                    }
                    return CutoutInfo(pos, topCutout.left.toFloat(), topCutout.right.toFloat())
                }
            }
        }

        return CutoutInfo(CutoutPosition.NONE)
    }

    fun updateHomeHeaderLayout(activity: MainActivity) {
        val root = activity.findViewById<View>(R.id.main) ?: return
        val homeHeader = activity.findViewById<ViewGroup>(R.id.home_header_container) ?: return
        val leftControls = activity.findViewById<View>(R.id.left_controls_container) ?: return
        val btnSettings = activity.findViewById<View>(R.id.settings_button) ?: return
        val dashboardTitle = activity.findViewById<TextView>(R.id.dashboard_title) ?: return

        if (homeHeader.width <= 0 || dashboardTitle.width <= 0) {
            dashboardTitle.post { updateHomeHeaderLayout(activity) }
            return
        }

        val cutoutInfo = getCutoutInfo(activity, root)
        val W = homeHeader.width.toFloat()
        val gap = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4f, activity.resources.displayMetrics)
        val clearance = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12f, activity.resources.displayMetrics)
        val margin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16f, activity.resources.displayMetrics)

        leftControls.translationX = 0f
        btnSettings.translationX = 0f
        dashboardTitle.translationX = 0f

        val defaultLeftControlsX = leftControls.left.toFloat()
        val defaultSettingsX = btnSettings.left.toFloat()
        val defaultTitleCenterX = dashboardTitle.left + dashboardTitle.width / 2f

        val lcWidth = leftControls.width.toFloat()
        val sbWidth = btnSettings.width.toFloat()
        val titleWidth = dashboardTitle.width.toFloat()
        val groupWidth = lcWidth + gap + sbWidth

        when (cutoutInfo.position) {
            CutoutPosition.NONE -> {
                leftControls.translationX = 0f
                btnSettings.translationX = 0f
                dashboardTitle.translationX = 0f
            }
            CutoutPosition.CENTER -> {
                val groupTargetLeft = ((W / 2f) - groupWidth) / 2f
                val clampedGroupLeft = groupTargetLeft.coerceIn(margin, (W / 2f) - groupWidth)

                leftControls.translationX = clampedGroupLeft - defaultLeftControlsX
                btnSettings.translationX = (clampedGroupLeft + lcWidth + gap) - defaultSettingsX

                val targetTitleCenterX = (W / 2f) + (W / 4f)
                dashboardTitle.translationX = targetTitleCenterX - defaultTitleCenterX
            }
            CutoutPosition.TOP_LEFT -> {
                val cutoutRight = cutoutInfo.rectRight
                val leftBoundary = (cutoutRight + clearance).coerceAtLeast(0f)
                val rightBoundary = W / 2f - clearance
                val targetTitleCenterX = if (leftBoundary < rightBoundary) {
                    (leftBoundary + rightBoundary) / 2f
                } else {
                    leftBoundary + titleWidth / 2f
                }
                dashboardTitle.translationX = targetTitleCenterX - defaultTitleCenterX

                val groupTargetLeft = (W / 2f) + (((W / 2f) - groupWidth) / 2f)
                val clampedGroupLeft = groupTargetLeft.coerceIn(W / 2f + gap, W - margin - groupWidth)

                leftControls.translationX = clampedGroupLeft - defaultLeftControlsX
                btnSettings.translationX = (clampedGroupLeft + lcWidth + gap) - defaultSettingsX
            }
            CutoutPosition.TOP_RIGHT -> {
                val clampedGroupLeft = margin
                leftControls.translationX = clampedGroupLeft - defaultLeftControlsX
                btnSettings.translationX = (clampedGroupLeft + lcWidth + gap) - defaultSettingsX

                val controlsRight = clampedGroupLeft + groupWidth
                val cutoutLeft = cutoutInfo.rectLeft
                val leftBoundary = controlsRight + clearance
                val rightBoundary = (cutoutLeft - clearance).coerceAtMost(W)

                val targetTitleCenterX = if (leftBoundary < rightBoundary) {
                    (leftBoundary + rightBoundary) / 2f
                } else {
                    leftBoundary + titleWidth / 2f
                }
                dashboardTitle.translationX = targetTitleCenterX - defaultTitleCenterX
            }
        }
    }

    fun updateSettingsHeaderLayout(activity: MainActivity) {
        val root = activity.findViewById<View>(R.id.main) ?: return
        val cardBack = activity.findViewById<View>(R.id.card_settings_back)
            ?: activity.findViewById<View>(R.id.settings_back_button) ?: return
        val tvSettingsTitle = activity.findViewById<TextView>(R.id.tv_settings_title) ?: return

        val parentView = cardBack.parent as? View ?: root
        if (parentView.width <= 0 || tvSettingsTitle.width <= 0) {
            tvSettingsTitle.post { updateSettingsHeaderLayout(activity) }
            return
        }

        val cutoutInfo = getCutoutInfo(activity, root)
        val W = parentView.width.toFloat()
        val clearance = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12f, activity.resources.displayMetrics)
        val margin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16f, activity.resources.displayMetrics)

        cardBack.translationX = 0f
        tvSettingsTitle.translationX = 0f

        val defaultBackLeft = cardBack.left.toFloat()
        val defaultTitleCenterX = tvSettingsTitle.left + tvSettingsTitle.width / 2f
        val backWidth = cardBack.width.toFloat()
        val titleWidth = tvSettingsTitle.width.toFloat()

        when (cutoutInfo.position) {
            CutoutPosition.NONE, CutoutPosition.TOP_RIGHT -> {
                cardBack.translationX = 0f
                tvSettingsTitle.translationX = 0f
            }
            CutoutPosition.CENTER -> {
                cardBack.translationX = 0f

                val targetTitleCenterX = (W / 2f) + (W / 4f)
                tvSettingsTitle.translationX = targetTitleCenterX - defaultTitleCenterX
            }
            CutoutPosition.TOP_LEFT -> {
                val cutoutRight = cutoutInfo.rectRight
                val targetBackLeft = (cutoutRight + clearance).coerceAtLeast(margin)
                cardBack.translationX = targetBackLeft - defaultBackLeft

                val shiftedBackRight = targetBackLeft + backWidth
                val leftBoundary = shiftedBackRight + clearance
                val rightBoundary = W - margin
                val targetTitleCenterX = if (leftBoundary + titleWidth <= rightBoundary) {
                    (leftBoundary + rightBoundary) / 2f
                } else {
                    leftBoundary + titleWidth / 2f
                }
                tvSettingsTitle.translationX = targetTitleCenterX - defaultTitleCenterX
            }
        }
    }
}
