package com.witherboymcw.superbattery.awareness

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.TypedValue
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.witherboymcw.superbattery.MainActivity
import com.witherboymcw.superbattery.R
import com.witherboymcw.superbattery.templates.dp
import com.witherboymcw.superbattery.templates.getDisplayRotationCompat
import com.witherboymcw.superbattery.widgets.BatteryWidget

typealias CutoutPosition = NotchGeometry.CutoutPosition
typealias CutoutInfo = NotchGeometry.CutoutInfo

/**
 * Unified notch interaction and avoidance facade for Super Battery.
 * Delegates geometry calculation to [NotchGeometry] and interactive overlay to [NotchOverlayEditor].
 */
object NotchAwareness {

    const val KEY_NOTCH_CUSTOM_MODE = NotchGeometry.KEY_NOTCH_CUSTOM_MODE
    const val KEY_NOTCH_IS_CENTER = NotchGeometry.KEY_NOTCH_IS_CENTER
    const val KEY_NOTCH_X = NotchGeometry.KEY_NOTCH_X
    const val KEY_NOTCH_Y = NotchGeometry.KEY_NOTCH_Y
    const val KEY_NOTCH_RADIUS = NotchGeometry.KEY_NOTCH_RADIUS
    const val KEY_NOTCH_PILL_WIDTH = NotchGeometry.KEY_NOTCH_PILL_WIDTH

    const val MODE_SYSTEM = NotchGeometry.MODE_SYSTEM
    const val MODE_FORCE = NotchGeometry.MODE_FORCE
    const val MODE_IGNORE = NotchGeometry.MODE_IGNORE

    const val DEFAULT_NOTCH_RADIUS = NotchGeometry.DEFAULT_NOTCH_RADIUS
    const val MIN_NOTCH_RADIUS = NotchGeometry.MIN_NOTCH_RADIUS
    const val MAX_NOTCH_RADIUS = NotchGeometry.MAX_NOTCH_RADIUS

    const val DEFAULT_NOTCH_PILL_WIDTH = NotchGeometry.DEFAULT_NOTCH_PILL_WIDTH
    const val MIN_NOTCH_PILL_WIDTH = NotchGeometry.MIN_NOTCH_PILL_WIDTH
    const val MAX_NOTCH_PILL_WIDTH = NotchGeometry.MAX_NOTCH_PILL_WIDTH

    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val notchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.BLACK
    }
    private val maskPath = Path()
    private val sharedTempRect = RectF()
    private val sharedTypedValue = TypedValue()

    fun getNotchRect(context: Context, hostView: View, tempRect: RectF = RectF()): RectF? {
        return NotchGeometry.getNotchRect(context, hostView, tempRect)
    }

    fun getCutoutInfo(context: Context, rootViewGroup: View): CutoutInfo {
        return NotchGeometry.getCutoutInfo(context, rootViewGroup, NotchOverlayEditor.liveCutoutOverride)
    }

    fun updateAllHeaders(activity: MainActivity) {
        updateHomeHeaderLayout(activity)
        updateSettingsHeaderLayout(activity)
        updateHotspotHeaderLayout(activity)
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
        val isLandscape = activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val position = if (isLandscape) CutoutPosition.NONE else cutoutInfo.position

        val shift = NotchGeometry.computeHeaderShift(
            headerType = NotchGeometry.HeaderType.HOME,
            totalWidth = homeHeader.width.toFloat(),
            cutoutInfo = cutoutInfo.copy(position = position),
            leftControlsWidth = leftControls.width.toFloat(),
            settingsButtonWidth = btnSettings.width.toFloat(),
            gap = activity.dp(4f),
            titleWidth = dashboardTitle.width.toFloat(),
            defaultLeftControlsX = leftControls.left.toFloat(),
            defaultSettingsX = btnSettings.left.toFloat(),
            defaultTitleCenterX = dashboardTitle.left + dashboardTitle.width / 2f,
            margin = activity.dp(16f),
            clearance = activity.dp(12f)
        )

        leftControls.translationX = shift.leftControlsTranslationX
        btnSettings.translationX = shift.rightControlsTranslationX
        dashboardTitle.translationX = shift.titleTranslationX
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

        val shift = NotchGeometry.computeHeaderShift(
            headerType = NotchGeometry.HeaderType.SETTINGS,
            totalWidth = parentView.width.toFloat(),
            cutoutInfo = cutoutInfo,
            leftControlsWidth = cardBack.width.toFloat(),
            titleWidth = tvSettingsTitle.width.toFloat(),
            defaultBackLeft = cardBack.left.toFloat(),
            defaultTitleCenterX = tvSettingsTitle.left + tvSettingsTitle.width / 2f,
            margin = activity.dp(16f),
            clearance = activity.dp(12f)
        )

        cardBack.translationX = shift.leftControlsTranslationX
        tvSettingsTitle.translationX = shift.titleTranslationX
    }

    fun updateHotspotHeaderLayout(activity: MainActivity) {
        val root = activity.findViewById<View>(R.id.main) ?: return
        val cardBack = activity.findViewById<View>(R.id.card_hotspot_back)
            ?: activity.findViewById<View>(R.id.hotspot_back_button) ?: return
        val tvHotspotTitle = activity.findViewById<TextView>(R.id.tv_hotspot_title) ?: return

        val parentView = cardBack.parent as? View ?: root
        if (parentView.width <= 0 || tvHotspotTitle.width <= 0) {
            tvHotspotTitle.post { updateHotspotHeaderLayout(activity) }
            return
        }

        val cutoutInfo = getCutoutInfo(activity, root)

        val shift = NotchGeometry.computeHeaderShift(
            headerType = NotchGeometry.HeaderType.HOTSPOT,
            totalWidth = parentView.width.toFloat(),
            cutoutInfo = cutoutInfo,
            leftControlsWidth = cardBack.width.toFloat(),
            titleWidth = tvHotspotTitle.width.toFloat(),
            defaultBackLeft = cardBack.left.toFloat(),
            defaultTitleCenterX = tvHotspotTitle.left + tvHotspotTitle.width / 2f,
            margin = activity.dp(16f),
            clearance = activity.dp(12f)
        )

        cardBack.translationX = shift.rightControlsTranslationX
        tvHotspotTitle.translationX = shift.titleTranslationX
    }

    fun drawRadialMask(canvas: Canvas, width: Int, height: Int, hostView: View) {
        val context = hostView.context
        val rotation = hostView.getDisplayRotationCompat()

        val rect = getNotchRect(context, hostView, sharedTempRect) ?: return

        if (rotation != Surface.ROTATION_0 && rotation != Surface.ROTATION_180) {
            val clearanceDp = hostView.dp(12f)
            val maxEdgeDistPx = hostView.dp(48f)
            val distLeft = rect.left
            val distRight = width - rect.right
            val distanceFromEdge = if (rotation == Surface.ROTATION_270) distRight else distLeft
            if (distanceFromEdge <= maxEdgeDistPx) {
                val isRight = rotation == Surface.ROTATION_270 || rect.centerX() > width / 2f
                if (isRight) {
                    if (context.theme.resolveAttribute(android.R.attr.colorBackground, sharedTypedValue, true)) {
                        maskPaint.color = sharedTypedValue.data
                    } else {
                        maskPaint.color = Color.BLACK
                    }
                    val circleRadius = maxOf(rect.width(), rect.height()) / 2f + clearanceDp
                    maskPath.reset()
                    maskPath.addCircle(rect.centerX(), rect.centerY(), circleRadius, Path.Direction.CW)
                    canvas.drawPath(maskPath, maskPaint)
                }
            }
        }

        val prefs = context.getSharedPreferences(BatteryWidget.SETTINGS_PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_NOTCH_CUSTOM_MODE, MODE_SYSTEM) == MODE_FORCE) {
            val cornerRadius = minOf(rect.width(), rect.height()) / 2f
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, notchPaint)
        }
    }

    fun dismissNotchPositionOverlay() {
        NotchOverlayEditor.dismissNotchPositionOverlay()
    }

    fun showNotchPositionOverlay(activity: MainActivity) {
        NotchOverlayEditor.showNotchPositionOverlay(activity) {
            updateAllHeaders(activity)
        }
    }
}
