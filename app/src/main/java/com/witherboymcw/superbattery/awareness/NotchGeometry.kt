package com.witherboymcw.superbattery.awareness

import android.content.Context
import android.content.res.Configuration
import android.graphics.RectF
import android.os.Build
import android.view.Surface
import android.view.View
import com.witherboymcw.superbattery.settings.AppPreferences
import com.witherboymcw.superbattery.core.dp
import com.witherboymcw.superbattery.core.getDisplayRotationCompat

/**
 * Pure geometry engine and status calculations for display cutouts, simulated notch bounds,
 * and header layout avoidance shifts.
 */
object NotchGeometry {

    const val KEY_NOTCH_CUSTOM_MODE = AppPreferences.KEY_NOTCH_CUSTOM_MODE
    const val KEY_NOTCH_IS_CENTER = AppPreferences.KEY_NOTCH_IS_CENTER
    const val KEY_NOTCH_X = AppPreferences.KEY_NOTCH_X
    const val KEY_NOTCH_Y = AppPreferences.KEY_NOTCH_Y
    const val KEY_NOTCH_RADIUS = AppPreferences.KEY_NOTCH_RADIUS
    const val KEY_NOTCH_PILL_WIDTH = AppPreferences.KEY_NOTCH_PILL_WIDTH

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

    data class HeaderMetrics(
        val headerType: HeaderType,
        val totalWidth: Float,
        val cutoutInfo: CutoutInfo,
        val leftControlsWidth: Float = 0f,
        val settingsButtonWidth: Float = 0f,
        val gap: Float = 0f,
        val titleWidth: Float = 0f,
        val defaultLeftControlsX: Float = 0f,
        val defaultSettingsX: Float = 0f,
        val defaultTitleCenterX: Float = 0f,
        val defaultBackLeft: Float = 0f,
        val margin: Float = 16f,
        val clearance: Float = 12f
    )

    fun getNotchRect(context: Context, hostView: View, tempRect: RectF = RectF()): RectF? {
        val notchMode = AppPreferences.getNotchCustomMode(context, MODE_SYSTEM)
        if (notchMode == MODE_IGNORE) return null

        if (notchMode == MODE_FORCE) {
            val isCenter = AppPreferences.isNotchCenter(context)
            val cachedX = AppPreferences.getNotchX(context)
            val cachedY = AppPreferences.getNotchY(context)

            val displayMetrics = context.resources.displayMetrics
            val radiusDp = AppPreferences.getNotchRadius(context, DEFAULT_NOTCH_RADIUS)
            val pillWidthDp = AppPreferences.getNotchPillWidth(context, DEFAULT_NOTCH_PILL_WIDTH)

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
        val rotation = rootViewGroup.getDisplayRotationCompat()

        if (context.resources.configuration.orientation != Configuration.ORIENTATION_PORTRAIT || rotation == Surface.ROTATION_180) {
            return CutoutInfo(CutoutPosition.NONE)
        }

        liveCutoutOverride?.let { return it }

        val notchMode = AppPreferences.getNotchCustomMode(context, MODE_SYSTEM)

        if (notchMode == MODE_IGNORE) {
            return CutoutInfo(CutoutPosition.NONE)
        }

        val screenWidth = rootViewGroup.width.toFloat().takeIf { it > 0f }
            ?: context.resources.displayMetrics.widthPixels.toFloat()

        if (notchMode == MODE_FORCE) {
            val isCentered = AppPreferences.isNotchCenter(context)
            val nX = AppPreferences.getNotchX(context)
            val radiusDp = AppPreferences.getNotchRadius(context, DEFAULT_NOTCH_RADIUS)
            val pillWidthDp = AppPreferences.getNotchPillWidth(context, DEFAULT_NOTCH_PILL_WIDTH)

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
        return computeHeaderShift(HeaderMetrics(
            headerType, totalWidth, cutoutInfo, leftControlsWidth, settingsButtonWidth, gap,
            titleWidth, defaultLeftControlsX, defaultSettingsX, defaultTitleCenterX, defaultBackLeft, margin, clearance
        ))
    }

    fun computeHeaderShift(metrics: HeaderMetrics): HeaderShiftResult {
        return when (metrics.headerType) {
            HeaderType.HOME -> computeHomeHeaderShift(metrics)
            HeaderType.SETTINGS -> computeSettingsHeaderShift(metrics)
            HeaderType.HOTSPOT -> computeHotspotHeaderShift(metrics)
        }
    }

    private fun centerTitle(leftBound: Float, rightBound: Float, titleWidth: Float): Float {
        return if (leftBound + titleWidth <= rightBound) {
            (leftBound + rightBound) / 2f
        } else {
            leftBound + titleWidth / 2f
        }
    }

    private fun computeHomeHeaderShift(m: HeaderMetrics): HeaderShiftResult {
        val W = m.totalWidth
        val cutoutLeft = m.cutoutInfo.rectLeft
        val cutoutRight = m.cutoutInfo.rectRight
        val groupWidth = m.leftControlsWidth + m.gap + m.settingsButtonWidth

        return when (m.cutoutInfo.position) {
            CutoutPosition.NONE -> {
                val groupTargetLeft = m.margin
                val leftControlsTransX = groupTargetLeft - m.defaultLeftControlsX
                val settingsTransX = (groupTargetLeft + m.leftControlsWidth + m.gap) - m.defaultSettingsX

                val controlsRight = groupTargetLeft + groupWidth
                val leftBoundary = controlsRight + m.clearance
                val rightBoundary = W - m.margin

                val targetTitleCenterX = centerTitle(leftBoundary, rightBoundary, m.titleWidth)
                HeaderShiftResult(
                    leftControlsTranslationX = leftControlsTransX,
                    rightControlsTranslationX = settingsTransX,
                    titleTranslationX = targetTitleCenterX - m.defaultTitleCenterX
                )
            }
            CutoutPosition.CENTER -> {
                val maxGroupLeft = (cutoutLeft - groupWidth).coerceAtLeast(m.margin)
                val groupTargetLeft = ((cutoutLeft - m.clearance) - groupWidth).coerceIn(m.margin, maxGroupLeft)
                val leftControlsTransX = groupTargetLeft - m.defaultLeftControlsX
                val settingsTransX = (groupTargetLeft + m.leftControlsWidth + m.gap) - m.defaultSettingsX

                val targetTitleCenterX = W * 0.75f
                HeaderShiftResult(
                    leftControlsTranslationX = leftControlsTransX,
                    rightControlsTranslationX = settingsTransX,
                    titleTranslationX = targetTitleCenterX - m.defaultTitleCenterX
                )
            }
            CutoutPosition.TOP_LEFT -> {
                val leftBoundary = (cutoutRight + m.margin).coerceAtLeast(0f)
                val rightBoundary = W / 2f - m.clearance
                val targetTitleCenterX = centerTitle(leftBoundary, rightBoundary, m.titleWidth)

                val groupTargetLeft = (W / 2f) + (((W / 2f) - groupWidth) / 2f)
                val minGroupLeft = W / 2f + m.gap
                val maxGroupLeft = (W - m.margin - groupWidth).coerceAtLeast(minGroupLeft)
                val clampedGroupLeft = groupTargetLeft.coerceIn(minGroupLeft, maxGroupLeft)

                val leftControlsTransX = clampedGroupLeft - m.defaultLeftControlsX
                val settingsTransX = (clampedGroupLeft + m.leftControlsWidth + m.gap) - m.defaultSettingsX

                HeaderShiftResult(
                    leftControlsTranslationX = leftControlsTransX,
                    rightControlsTranslationX = settingsTransX,
                    titleTranslationX = targetTitleCenterX - m.defaultTitleCenterX
                )
            }
            CutoutPosition.TOP_RIGHT -> {
                val clampedGroupLeft = m.margin
                val leftControlsTransX = clampedGroupLeft - m.defaultLeftControlsX
                val settingsTransX = (clampedGroupLeft + m.leftControlsWidth + m.gap) - m.defaultSettingsX

                val controlsRight = clampedGroupLeft + groupWidth
                val leftBoundary = controlsRight + m.clearance
                val rightBoundary = (cutoutLeft - m.clearance).coerceAtMost(W)

                val targetTitleCenterX = centerTitle(leftBoundary, rightBoundary, m.titleWidth)
                HeaderShiftResult(
                    leftControlsTranslationX = leftControlsTransX,
                    rightControlsTranslationX = settingsTransX,
                    titleTranslationX = targetTitleCenterX - m.defaultTitleCenterX
                )
            }
        }
    }

    private fun computeSettingsHeaderShift(m: HeaderMetrics): HeaderShiftResult {
        return when (m.cutoutInfo.position) {
            CutoutPosition.NONE, CutoutPosition.TOP_RIGHT -> {
                HeaderShiftResult(0f, 0f, 0f)
            }
            CutoutPosition.CENTER -> {
                val targetTitleCenterX = m.totalWidth * 0.75f
                HeaderShiftResult(
                    leftControlsTranslationX = 0f,
                    titleTranslationX = targetTitleCenterX - m.defaultTitleCenterX
                )
            }
            CutoutPosition.TOP_LEFT -> {
                val targetBackLeft = (m.cutoutInfo.rectRight + m.margin).coerceAtLeast(m.margin)
                val backTransX = targetBackLeft - m.defaultBackLeft

                val shiftedBackRight = targetBackLeft + m.leftControlsWidth
                val leftBoundary = shiftedBackRight + m.clearance
                val rightBoundary = m.totalWidth - m.margin
                val targetTitleCenterX = centerTitle(leftBoundary, rightBoundary, m.titleWidth)
                HeaderShiftResult(
                    leftControlsTranslationX = backTransX,
                    titleTranslationX = targetTitleCenterX - m.defaultTitleCenterX
                )
            }
        }
    }

    private fun computeHotspotHeaderShift(m: HeaderMetrics): HeaderShiftResult {
        return when (m.cutoutInfo.position) {
            CutoutPosition.NONE, CutoutPosition.TOP_LEFT -> {
                HeaderShiftResult(0f, 0f, 0f)
            }
            CutoutPosition.CENTER -> {
                val targetTitleCenterX = m.totalWidth * 0.25f
                HeaderShiftResult(
                    rightControlsTranslationX = 0f,
                    titleTranslationX = targetTitleCenterX - m.defaultTitleCenterX
                )
            }
            CutoutPosition.TOP_RIGHT -> {
                val targetBackRight = (m.cutoutInfo.rectLeft - m.margin).coerceAtMost(m.totalWidth - m.margin)
                val targetBackLeft = targetBackRight - m.leftControlsWidth
                val backTransX = targetBackLeft - m.defaultBackLeft

                val leftBoundary = m.margin
                val rightBoundary = targetBackLeft - m.clearance
                val targetTitleCenterX = centerTitle(leftBoundary, rightBoundary, m.titleWidth)
                HeaderShiftResult(
                    rightControlsTranslationX = backTransX,
                    titleTranslationX = targetTitleCenterX - m.defaultTitleCenterX
                )
            }
        }
    }
}
