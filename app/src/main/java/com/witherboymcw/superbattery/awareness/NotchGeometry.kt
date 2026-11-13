package com.witherboymcw.superbattery.awareness

import android.content.Context
import android.content.res.Configuration
import android.graphics.RectF
import android.os.Build
import android.view.Surface
import android.view.View
import com.witherboymcw.superbattery.templates.dp
import com.witherboymcw.superbattery.templates.getDisplayRotationCompat
import com.witherboymcw.superbattery.widgets.BatteryWidget

/**
 * Pure geometry engine and status calculations for display cutouts, simulated notch bounds,
 * and header layout avoidance shifts.
 */
object NotchGeometry {

    const val KEY_NOTCH_CUSTOM_MODE = "notch_custom_mode"
    const val KEY_NOTCH_IS_CENTER = "notch_is_center"
    const val KEY_NOTCH_X = "notch_x"
    const val KEY_NOTCH_Y = "notch_y"
    const val KEY_NOTCH_RADIUS = "notch_radius"
    const val KEY_NOTCH_PILL_WIDTH = "notch_pill_width"

    const val MODE_SYSTEM = 0
    const val MODE_FORCE = 1
    const val MODE_IGNORE = 2

    const val DEFAULT_NOTCH_RADIUS = 24f
    const val MIN_NOTCH_RADIUS = 8f
    const val MAX_NOTCH_RADIUS = 80f

    const val DEFAULT_NOTCH_PILL_WIDTH = 0f
    const val MIN_NOTCH_PILL_WIDTH = 0f
    const val MAX_NOTCH_PILL_WIDTH = 160f

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

    enum class HeaderType {
        HOME,
        SETTINGS,
        HOTSPOT
    }

    data class HeaderShiftResult(
        val leftControlsTranslationX: Float = 0f,
        val rightControlsTranslationX: Float = 0f,
        val titleTranslationX: Float = 0f
    )

    fun getNotchRect(context: Context, hostView: View, tempRect: RectF = RectF()): RectF? {
        val prefs = context.getSharedPreferences(BatteryWidget.SETTINGS_PREFS, Context.MODE_PRIVATE)
        val notchMode = prefs.getInt(KEY_NOTCH_CUSTOM_MODE, MODE_SYSTEM)
        if (notchMode == MODE_IGNORE) return null

        if (notchMode == MODE_FORCE) {
            val isCenter = prefs.getBoolean(KEY_NOTCH_IS_CENTER, true)
            val cachedX = prefs.getFloat(KEY_NOTCH_X, -1f)
            val cachedY = prefs.getFloat(KEY_NOTCH_Y, -1f)

            val displayMetrics = context.resources.displayMetrics
            val radiusDp = prefs.getFloat(KEY_NOTCH_RADIUS, DEFAULT_NOTCH_RADIUS)
            val pillWidthDp = prefs.getFloat(KEY_NOTCH_PILL_WIDTH, DEFAULT_NOTCH_PILL_WIDTH)

            val radiusPx = hostView.dp(radiusDp)
            val pillWidthPx = hostView.dp(pillWidthDp)

            val isLandscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val rotation = hostView.getDisplayRotationCompat()

            val hostWidth = hostView.width.toFloat().takeIf { it > 0f } ?: displayMetrics.widthPixels.toFloat()
            val hostHeight = hostView.height.toFloat().takeIf { it > 0f } ?: displayMetrics.heightPixels.toFloat()

            if (isLandscape || rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
                val cY = if (isCenter || cachedY < 0f) hostHeight / 2f else cachedY
                val isRight = rotation == Surface.ROTATION_270
                val cX = if (isRight) {
                    if (cachedX >= 0f && cachedX > hostWidth / 2f) cachedX else hostWidth - radiusPx
                } else {
                    if (cachedX >= 0f && cachedX < hostWidth / 2f) cachedX else radiusPx
                }

                val halfW = radiusPx
                val halfH = radiusPx + pillWidthPx / 2f
                tempRect.set(cX - halfW, cY - halfH, cX + halfW, cY + halfH)
                return tempRect
            } else {
                val cX = if (isCenter || cachedX < 0f) hostWidth / 2f else cachedX
                val cY = if (cachedY < 0f) radiusPx else cachedY

                val halfWidth = radiusPx + pillWidthPx / 2f
                val halfHeight = radiusPx

                tempRect.set(cX - halfWidth, cY - halfHeight, cX + halfWidth, cY + halfHeight)
                return tempRect
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                hostView.rootWindowInsets?.displayCutout?.boundingRects?.firstOrNull()?.let { rect ->
                    tempRect.set(rect)
                    return tempRect
                }
            }
        }
        return null
    }

    fun getCutoutInfo(context: Context, rootViewGroup: View, liveCutoutOverride: CutoutInfo? = null): CutoutInfo {
        liveCutoutOverride?.let { return it }
        val rotation = rootViewGroup.getDisplayRotationCompat()

        if (context.resources.configuration.orientation != Configuration.ORIENTATION_PORTRAIT || rotation == Surface.ROTATION_180) {
            return CutoutInfo(CutoutPosition.NONE)
        }

        val prefs = context.getSharedPreferences(BatteryWidget.SETTINGS_PREFS, Context.MODE_PRIVATE)
        val notchMode = prefs.getInt(KEY_NOTCH_CUSTOM_MODE, MODE_SYSTEM)

        if (notchMode == MODE_IGNORE) {
            return CutoutInfo(CutoutPosition.NONE)
        }

        val screenWidth = rootViewGroup.width.toFloat().takeIf { it > 0f }
            ?: context.resources.displayMetrics.widthPixels.toFloat()

        if (notchMode == MODE_FORCE) {
            val isCentered = prefs.getBoolean(KEY_NOTCH_IS_CENTER, true)
            val nX = prefs.getFloat(KEY_NOTCH_X, -1f)
            val radiusDp = prefs.getFloat(KEY_NOTCH_RADIUS, DEFAULT_NOTCH_RADIUS)
            val pillWidthDp = prefs.getFloat(KEY_NOTCH_PILL_WIDTH, DEFAULT_NOTCH_PILL_WIDTH)

            val radiusPx = context.dp(radiusDp)
            val pillWidthPx = context.dp(pillWidthDp)
            val halfWidth = radiusPx + pillWidthPx / 2f

            val cX = if (isCentered || nX < 0f) screenWidth / 2f else nX
            val leftBound = cX - halfWidth
            val rightBound = cX + halfWidth

            val pos = when {
                isCentered -> CutoutPosition.CENTER
                cX < screenWidth * 0.35f -> CutoutPosition.TOP_LEFT
                cX > screenWidth * 0.65f -> CutoutPosition.TOP_RIGHT
                else -> CutoutPosition.CENTER
            }
            return CutoutInfo(pos, leftBound, rightBound)
        }

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

    fun computeHeaderShift(
        headerType: HeaderType,
        totalWidth: Float,
        cutoutInfo: CutoutInfo,
        leftControlsWidth: Float = 0f,
        settingsButtonWidth: Float = 0f,
        gap: Float = 0f,
        titleWidth: Float = 0f,
        defaultLeftControlsX: Float = 0f,
        defaultSettingsX: Float = 0f,
        defaultTitleCenterX: Float = 0f,
        defaultBackLeft: Float = 0f,
        margin: Float = 16f,
        clearance: Float = 12f
    ): HeaderShiftResult {
        val W = totalWidth
        val cutoutLeft = cutoutInfo.rectLeft
        val cutoutRight = cutoutInfo.rectRight

        return when (headerType) {
            HeaderType.HOME -> {
                val groupWidth = leftControlsWidth + gap + settingsButtonWidth
                when (cutoutInfo.position) {
                    CutoutPosition.NONE -> {
                        val groupTargetLeft = margin
                        val leftControlsTransX = groupTargetLeft - defaultLeftControlsX
                        val settingsTransX = (groupTargetLeft + leftControlsWidth + gap) - defaultSettingsX

                        val controlsRight = groupTargetLeft + groupWidth
                        val leftBoundary = controlsRight + clearance
                        val rightBoundary = W - margin

                        val targetTitleCenterX = if (leftBoundary + titleWidth <= rightBoundary) {
                            (leftBoundary + rightBoundary) / 2f
                        } else {
                            leftBoundary + titleWidth / 2f
                        }
                        HeaderShiftResult(
                            leftControlsTranslationX = leftControlsTransX,
                            rightControlsTranslationX = settingsTransX,
                            titleTranslationX = targetTitleCenterX - defaultTitleCenterX
                        )
                    }
                    CutoutPosition.CENTER -> {
                        val groupTargetLeft = ((cutoutLeft - clearance) - groupWidth).coerceIn(margin, cutoutLeft - groupWidth)
                        val leftControlsTransX = groupTargetLeft - defaultLeftControlsX
                        val settingsTransX = (groupTargetLeft + leftControlsWidth + gap) - defaultSettingsX

                        val targetTitleCenterX = W * 0.75f
                        HeaderShiftResult(
                            leftControlsTranslationX = leftControlsTransX,
                            rightControlsTranslationX = settingsTransX,
                            titleTranslationX = targetTitleCenterX - defaultTitleCenterX
                        )
                    }
                    CutoutPosition.TOP_LEFT -> {
                        val leftBoundary = (cutoutRight + margin).coerceAtLeast(0f)
                        val rightBoundary = W / 2f - clearance
                        val targetTitleCenterX = if (leftBoundary < rightBoundary) {
                            (leftBoundary + rightBoundary) / 2f
                        } else {
                            leftBoundary + titleWidth / 2f
                        }

                        val groupTargetLeft = (W / 2f) + (((W / 2f) - groupWidth) / 2f)
                        val clampedGroupLeft = groupTargetLeft.coerceIn(W / 2f + gap, W - margin - groupWidth)

                        val leftControlsTransX = clampedGroupLeft - defaultLeftControlsX
                        val settingsTransX = (clampedGroupLeft + leftControlsWidth + gap) - defaultSettingsX

                        HeaderShiftResult(
                            leftControlsTranslationX = leftControlsTransX,
                            rightControlsTranslationX = settingsTransX,
                            titleTranslationX = targetTitleCenterX - defaultTitleCenterX
                        )
                    }
                    CutoutPosition.TOP_RIGHT -> {
                        val clampedGroupLeft = margin
                        val leftControlsTransX = clampedGroupLeft - defaultLeftControlsX
                        val settingsTransX = (clampedGroupLeft + leftControlsWidth + gap) - defaultSettingsX

                        val controlsRight = clampedGroupLeft + groupWidth
                        val leftBoundary = controlsRight + clearance
                        val rightBoundary = (cutoutLeft - clearance).coerceAtMost(W)

                        val targetTitleCenterX = if (leftBoundary < rightBoundary) {
                            (leftBoundary + rightBoundary) / 2f
                        } else {
                            leftBoundary + titleWidth / 2f
                        }
                        HeaderShiftResult(
                            leftControlsTranslationX = leftControlsTransX,
                            rightControlsTranslationX = settingsTransX,
                            titleTranslationX = targetTitleCenterX - defaultTitleCenterX
                        )
                    }
                }
            }
            HeaderType.SETTINGS -> {
                when (cutoutInfo.position) {
                    CutoutPosition.NONE, CutoutPosition.TOP_RIGHT -> {
                        HeaderShiftResult(0f, 0f, 0f)
                    }
                    CutoutPosition.CENTER -> {
                        val targetTitleCenterX = W * 0.75f
                        HeaderShiftResult(
                            leftControlsTranslationX = 0f,
                            titleTranslationX = targetTitleCenterX - defaultTitleCenterX
                        )
                    }
                    CutoutPosition.TOP_LEFT -> {
                        val targetBackLeft = (cutoutRight + margin).coerceAtLeast(margin)
                        val backTransX = targetBackLeft - defaultBackLeft

                        val shiftedBackRight = targetBackLeft + leftControlsWidth
                        val leftBoundary = shiftedBackRight + clearance
                        val rightBoundary = W - margin
                        val targetTitleCenterX = if (leftBoundary + titleWidth <= rightBoundary) {
                            (leftBoundary + rightBoundary) / 2f
                        } else {
                            leftBoundary + titleWidth / 2f
                        }
                        HeaderShiftResult(
                            leftControlsTranslationX = backTransX,
                            titleTranslationX = targetTitleCenterX - defaultTitleCenterX
                        )
                    }
                }
            }
            HeaderType.HOTSPOT -> {
                when (cutoutInfo.position) {
                    CutoutPosition.NONE, CutoutPosition.TOP_LEFT -> {
                        HeaderShiftResult(0f, 0f, 0f)
                    }
                    CutoutPosition.CENTER -> {
                        val targetTitleCenterX = W * 0.25f
                        HeaderShiftResult(
                            rightControlsTranslationX = 0f,
                            titleTranslationX = targetTitleCenterX - defaultTitleCenterX
                        )
                    }
                    CutoutPosition.TOP_RIGHT -> {
                        val targetBackRight = (cutoutLeft - margin).coerceAtMost(W - margin)
                        val targetBackLeft = targetBackRight - leftControlsWidth
                        val backTransX = targetBackLeft - defaultBackLeft

                        val leftBoundary = margin
                        val rightBoundary = targetBackLeft - clearance
                        val targetTitleCenterX = if (leftBoundary + titleWidth <= rightBoundary) {
                            (leftBoundary + rightBoundary) / 2f
                        } else {
                            leftBoundary + titleWidth / 2f
                        }
                        HeaderShiftResult(
                            rightControlsTranslationX = backTransX,
                            titleTranslationX = targetTitleCenterX - defaultTitleCenterX
                        )
                    }
                }
            }
        }
    }
}
