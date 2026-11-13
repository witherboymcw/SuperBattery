package com.witherboymcw.superbattery.awareness

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.provider.Settings
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.witherboymcw.superbattery.MainActivity
import com.witherboymcw.superbattery.R
import com.witherboymcw.superbattery.SwipeBackFrameLayout
import com.witherboymcw.superbattery.widgets.BatteryWidget
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import kotlin.math.abs

/**
 * Unified notch interaction and avoidance architecture for Super Battery.
 * Handles display cutout detection, preference configuration, header layout notch avoidance,
 * landscape scroll content detour calculations, radial mask rendering, and simulated notch position interaction.
 */
object NotchAwareness {

    const val KEY_NOTCH_CUSTOM_MODE = "notch_custom_mode" // 0: System, 1: Force/Custom, 2: Force Ignore
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

    /**
     * Resolves the notch bounding rectangle considering system cutouts, orientation, and custom simulated mode.
     */
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

            val radiusPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, radiusDp, displayMetrics)
            val pillWidthPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, pillWidthDp, displayMetrics)

            val isLandscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                hostView.display?.rotation ?: Surface.ROTATION_0
            } else {
                @Suppress("DEPRECATION")
                (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
            }

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

    private var liveCutoutOverride: CutoutInfo? = null

    /**
     * Resolves display cutout position and boundary bounds for layout calculations.
     */
    fun getCutoutInfo(context: Context, rootViewGroup: View): CutoutInfo {
        liveCutoutOverride?.let { return it }
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

            val radiusPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, radiusDp, context.resources.displayMetrics)
            val pillWidthPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, pillWidthDp, context.resources.displayMetrics)
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

    /**
     * Updates all header layouts (Home, Settings, Hotspot) to avoid notch/cutout collision.
     */
    fun updateAllHeaders(activity: MainActivity) {
        updateHomeHeaderLayout(activity)
        updateSettingsHeaderLayout(activity)
        updateHotspotHeaderLayout(activity)
    }

    /**
     * Adjusts Home screen header views around the cutout, taking pill width expansion into account.
     */
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

        val cutoutLeft = cutoutInfo.rectLeft
        val cutoutRight = cutoutInfo.rectRight

        when (cutoutInfo.position) {
            CutoutPosition.NONE -> {
                leftControls.translationX = 0f
                btnSettings.translationX = 0f
                dashboardTitle.translationX = 0f
            }
            CutoutPosition.CENTER -> {
                val groupTargetLeft = ((cutoutLeft - clearance) - groupWidth).coerceIn(margin, cutoutLeft - groupWidth)
                leftControls.translationX = groupTargetLeft - defaultLeftControlsX
                btnSettings.translationX = (groupTargetLeft + lcWidth + gap) - defaultSettingsX

                val targetTitleCenterX = W * 0.75f
                dashboardTitle.translationX = targetTitleCenterX - defaultTitleCenterX
            }
            CutoutPosition.TOP_LEFT -> {
                val leftBoundary = (cutoutRight + margin).coerceAtLeast(0f)
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

    /**
     * Adjusts Settings screen header views around the cutout, taking pill width expansion into account.
     */
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

        val cutoutRight = cutoutInfo.rectRight

        when (cutoutInfo.position) {
            CutoutPosition.NONE, CutoutPosition.TOP_RIGHT -> {
                cardBack.translationX = 0f
                tvSettingsTitle.translationX = 0f
            }
            CutoutPosition.CENTER -> {
                cardBack.translationX = 0f
                val targetTitleCenterX = W * 0.75f
                tvSettingsTitle.translationX = targetTitleCenterX - defaultTitleCenterX
            }
            CutoutPosition.TOP_LEFT -> {
                val targetBackLeft = (cutoutRight + margin).coerceAtLeast(margin)
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

    /**
     * Adjusts Hotspot screen header views around the cutout, taking pill width expansion into account.
     */
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
        val W = parentView.width.toFloat()
        val clearance = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12f, activity.resources.displayMetrics)
        val margin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16f, activity.resources.displayMetrics)

        cardBack.translationX = 0f
        tvHotspotTitle.translationX = 0f

        val defaultBackLeft = cardBack.left.toFloat()
        val defaultTitleCenterX = tvHotspotTitle.left + tvHotspotTitle.width / 2f
        val backWidth = cardBack.width.toFloat()
        val titleWidth = tvHotspotTitle.width.toFloat()

        val cutoutLeft = cutoutInfo.rectLeft

        when (cutoutInfo.position) {
            CutoutPosition.NONE, CutoutPosition.TOP_LEFT -> {
                cardBack.translationX = 0f
                tvHotspotTitle.translationX = 0f
            }
            CutoutPosition.CENTER -> {
                cardBack.translationX = 0f
                val targetTitleCenterX = W * 0.25f
                tvHotspotTitle.translationX = targetTitleCenterX - defaultTitleCenterX
            }
            CutoutPosition.TOP_RIGHT -> {
                val targetBackRight = (cutoutLeft - margin).coerceAtMost(W - margin)
                val targetBackLeft = targetBackRight - backWidth
                cardBack.translationX = targetBackLeft - defaultBackLeft

                val leftBoundary = margin
                val rightBoundary = targetBackLeft - clearance
                val targetTitleCenterX = if (leftBoundary + titleWidth <= rightBoundary) {
                    (leftBoundary + rightBoundary) / 2f
                } else {
                    leftBoundary + titleWidth / 2f
                }
                tvHotspotTitle.translationX = targetTitleCenterX - defaultTitleCenterX
            }
        }
    }

    /**
     * Renders radial mask and force notch overlay on canvas in landscape mode.
     */
    fun drawRadialMask(canvas: Canvas, width: Int, height: Int, hostView: View) {
        val context = hostView.context
        val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            hostView.display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
        }

        if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) return

        val rect = getNotchRect(context, hostView, sharedTempRect) ?: return

        // Proximity Guard Clause (48dp)
        val clearanceDp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12f, hostView.resources.displayMetrics)
        val maxEdgeDistPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48f, hostView.resources.displayMetrics)
        val distLeft = rect.left
        val distRight = width - rect.right
        val distanceFromEdge = if (rotation == Surface.ROTATION_270) distRight else distLeft
        if (distanceFromEdge > maxEdgeDistPx) return

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

        val prefs = context.getSharedPreferences(BatteryWidget.SETTINGS_PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_NOTCH_CUSTOM_MODE, MODE_SYSTEM) == MODE_FORCE) {
            val cornerRadius = minOf(rect.width(), rect.height()) / 2f
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, notchPaint)
        }
    }

    private var activeOverlayViewRef: java.lang.ref.WeakReference<View>? = null
    private var activeWindowManagerRef: java.lang.ref.WeakReference<WindowManager>? = null

    /**
     * Dismisses and removes any active notch position overlay window to prevent WindowManager memory leaks.
     */
    fun dismissNotchPositionOverlay() {
        liveCutoutOverride = null
        val overlay = activeOverlayViewRef?.get()
        val wm = activeWindowManagerRef?.get()
        if (overlay != null && wm != null) {
            try {
                wm.removeView(overlay)
            } catch (_: Exception) {}
        }
        activeOverlayViewRef?.clear()
        activeOverlayViewRef = null
        activeWindowManagerRef?.clear()
        activeWindowManagerRef = null
    }

    /**
     * Shows an interactive overlay allowing the user to set/drag the simulated notch position,
     * reading display orientation first, and synchronizing seekbars seamlessly.
     */
    fun showNotchPositionOverlay(activity: MainActivity) {
        dismissNotchPositionOverlay()

        if (!com.witherboymcw.superbattery.settings.Permissions.isOverlayEnabled(activity)) {
            try {
                activity.startActivity(Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${activity.packageName}")
                ))
            } catch (_: Exception) {}
            return
        }

        val wm = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayMetrics = activity.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels.toFloat()
        val screenHeight = displayMetrics.heightPixels.toFloat()

        val isLandscape = activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            (activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
        }

        val prefs = activity.getSharedPreferences(BatteryWidget.SETTINGS_PREFS, Context.MODE_PRIVATE)
        var isCenter = prefs.getBoolean(KEY_NOTCH_IS_CENTER, true)
        var currentRadiusDp = prefs.getFloat(KEY_NOTCH_RADIUS, DEFAULT_NOTCH_RADIUS)
        var currentPillDp = prefs.getFloat(KEY_NOTCH_PILL_WIDTH, DEFAULT_NOTCH_PILL_WIDTH)

        var currentX = prefs.getFloat(KEY_NOTCH_X, -1f)
        var currentY = prefs.getFloat(KEY_NOTCH_Y, -1f)

        val radiusPxInitial = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, currentRadiusDp, displayMetrics)

        if (isLandscape || rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
            if (currentY < 0f || isCenter) currentY = screenHeight / 2f
            val isRight = rotation == Surface.ROTATION_270
            if (currentX < 0f) {
                currentX = if (isRight) screenWidth - radiusPxInitial else radiusPxInitial
            }
        } else {
            if (currentX < 0f || isCenter) currentX = screenWidth / 2f
            if (currentY < 0f) currentY = radiusPxInitial
        }

        val overlayView = FrameLayout(activity)
        overlayView.setBackgroundColor(0x77000000.toInt())

        val cutoutView = View(activity)
        val cutoutDrawable = GradientDrawable().apply {
            setColor(Color.BLACK)
            setStroke(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2f, displayMetrics).toInt(), Color.WHITE)
        }
        cutoutView.background = cutoutDrawable

        fun updateCutoutLayout() {
            val radiusPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, currentRadiusDp, displayMetrics)
            val pillPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, currentPillDp, displayMetrics)

            val halfW: Float
            val halfH: Float

            if (isLandscape || rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
                halfW = radiusPx
                halfH = radiusPx + pillPx / 2f
                if (isCenter) {
                    currentY = screenHeight / 2f
                }
            } else {
                halfW = radiusPx + pillPx / 2f
                halfH = radiusPx
                if (isCenter) {
                    currentX = screenWidth / 2f
                }
            }

            cutoutDrawable.cornerRadius = minOf(halfW, halfH)

            val lp = FrameLayout.LayoutParams((halfW * 2f).toInt(), (halfH * 2f).toInt())
            lp.leftMargin = (currentX - halfW).toInt().coerceIn(0, (screenWidth - halfW * 2f).toInt().coerceAtLeast(0))
            lp.topMargin = (currentY - halfH).toInt().coerceIn(0, (screenHeight - halfH * 2f).toInt().coerceAtLeast(0))
            cutoutView.layoutParams = lp
        }

        updateCutoutLayout()
        overlayView.addView(cutoutView)

        // Floating Control Panel Card
        val controlCard = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xEE1E1E1E.toInt())
            val pPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16f, displayMetrics).toInt()
            setPadding(pPx, pPx, pPx, pPx)
        }

        val cardParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM
            val marginPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16f, displayMetrics).toInt()
            setMargins(marginPx, marginPx, marginPx, marginPx)
        }

        val tvInstruction = TextView(activity).apply {
            text = activity.getString(R.string.notch_instruction_text)
            setTextColor(Color.WHITE)
            textSize = 12f
            gravity = Gravity.CENTER
        }
        controlCard.addView(tvInstruction)

        // Center / Manual Position RadioGroup
        val radioGroupPos = RadioGroup(activity).apply {
            orientation = RadioGroup.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 8)
        }
        val radioCenter = RadioButton(activity).apply {
            text = activity.getString(R.string.notch_pos_center)
            setTextColor(Color.WHITE)
            isChecked = isCenter
        }
        val radioManual = RadioButton(activity).apply {
            text = activity.getString(R.string.notch_pos_not_center)
            setTextColor(Color.WHITE)
            isChecked = !isCenter
        }
        radioGroupPos.addView(radioCenter)
        radioGroupPos.addView(radioManual)
        controlCard.addView(radioGroupPos)

        // Size Seekbar Section
        val sizeHeaderLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 0)
        }
        val tvSizeTitle = TextView(activity).apply {
            text = activity.getString(R.string.notch_size_title)
            setTextColor(Color.WHITE)
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tvSizeVal = TextView(activity).apply {
            text = "${currentRadiusDp.toInt()} dp"
            setTextColor(0xFFCCCCCC.toInt())
            textSize = 12f
        }
        sizeHeaderLayout.addView(tvSizeTitle)
        sizeHeaderLayout.addView(tvSizeVal)
        controlCard.addView(sizeHeaderLayout)

        val sizeSeekLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val seekBarSize = SeekBar(activity).apply {
            max = MAX_NOTCH_RADIUS.toInt()
            progress = currentRadiusDp.toInt()
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        sizeSeekLayout.addView(seekBarSize)
        controlCard.addView(sizeSeekLayout)

        // Pill Seekbar Section
        val pillHeaderLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 0)
        }
        val tvPillTitle = TextView(activity).apply {
            text = activity.getString(R.string.notch_pill_title)
            setTextColor(Color.WHITE)
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tvPillVal = TextView(activity).apply {
            text = "${currentPillDp.toInt()} dp"
            setTextColor(0xFFCCCCCC.toInt())
            textSize = 12f
        }
        pillHeaderLayout.addView(tvPillTitle)
        pillHeaderLayout.addView(tvPillVal)
        controlCard.addView(pillHeaderLayout)

        val pillSeekLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val seekBarPill = SeekBar(activity).apply {
            max = MAX_NOTCH_PILL_WIDTH.toInt()
            progress = currentPillDp.toInt()
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        pillSeekLayout.addView(seekBarPill)
        controlCard.addView(pillSeekLayout)

        // Done Button
        val btnSave = Button(activity).apply {
            text = "Done"
            textSize = 12f
        }
        controlCard.addView(btnSave, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = 8
        })

        overlayView.addView(controlCard, cardParams)

        fun refreshPreview() {
            val radiusPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, currentRadiusDp, displayMetrics)
            val pillWidthPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, currentPillDp, displayMetrics)
            val halfWidth = radiusPx + pillWidthPx / 2f

            val cX = if (isCenter || currentX < 0f) screenWidth / 2f else currentX
            val leftBound = cX - halfWidth
            val rightBound = cX + halfWidth

            val pos = when {
                isCenter -> CutoutPosition.CENTER
                cX < screenWidth * 0.35f -> CutoutPosition.TOP_LEFT
                cX > screenWidth * 0.65f -> CutoutPosition.TOP_RIGHT
                else -> CutoutPosition.CENTER
            }
            liveCutoutOverride = CutoutInfo(pos, leftBound, rightBound)
            updateCutoutLayout()
            updateAllHeaders(activity)
        }

        fun commitToPrefs() {
            liveCutoutOverride = null
            prefs.edit()
                .putBoolean(KEY_NOTCH_IS_CENTER, isCenter)
                .putFloat(KEY_NOTCH_X, currentX)
                .putFloat(KEY_NOTCH_Y, currentY)
                .putFloat(KEY_NOTCH_RADIUS, currentRadiusDp)
                .putFloat(KEY_NOTCH_PILL_WIDTH, currentPillDp)
                .apply()
            updateCutoutLayout()
            updateAllHeaders(activity)
        }

        radioGroupPos.setOnCheckedChangeListener { _, checkedId ->
            isCenter = (checkedId == radioCenter.id)
            commitToPrefs()
        }

        seekBarSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentRadiusDp = progress.toFloat().coerceIn(MIN_NOTCH_RADIUS, MAX_NOTCH_RADIUS)
                    tvSizeVal.text = "${currentRadiusDp.toInt()} dp"
                    refreshPreview()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                commitToPrefs()
            }
        })

        seekBarPill.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentPillDp = progress.toFloat().coerceIn(MIN_NOTCH_PILL_WIDTH, MAX_NOTCH_PILL_WIDTH)
                    tvPillVal.text = "${currentPillDp.toInt()} dp"
                    refreshPreview()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                commitToPrefs()
            }
        })

        btnSave.setOnClickListener {
            commitToPrefs()
            dismissNotchPositionOverlay()
        }

        // Pinch-to-zoom gesture detector
        val scaleDetector = ScaleGestureDetector(activity, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scaleFactor = detector.scaleFactor
                val newRadius = (currentRadiusDp * scaleFactor).coerceIn(MIN_NOTCH_RADIUS, MAX_NOTCH_RADIUS)
                if (abs(newRadius - currentRadiusDp) > 0.1f) {
                    currentRadiusDp = newRadius
                    tvSizeVal.text = "${currentRadiusDp.toInt()} dp"
                    seekBarSize.progress = currentRadiusDp.toInt()
                    refreshPreview()
                }
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                commitToPrefs()
            }
        })

        var touchStartX = 0f
        var touchStartY = 0f
        var initialX = 0f
        var initialY = 0f

        overlayView.setOnTouchListener { v, event ->
            scaleDetector.onTouchEvent(event)

            if (event.pointerCount == 1) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        touchStartX = event.rawX
                        touchStartY = event.rawY
                        initialX = currentX
                        initialY = currentY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - touchStartX
                        val dy = event.rawY - touchStartY
                        if (isLandscape || rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
                            if (!isCenter) {
                                currentY = initialY + dy
                            }
                            currentX = initialX + dx
                        } else {
                            if (!isCenter) {
                                currentX = initialX + dx
                            }
                            currentY = initialY + dy
                        }
                        refreshPreview()
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        v.performClick()
                        commitToPrefs()
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        commitToPrefs()
                        true
                    }
                    else -> true
                }
            } else {
                true
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        try {
            wm.addView(overlayView, params)
            activeWindowManagerRef = java.lang.ref.WeakReference(wm)
            activeOverlayViewRef = java.lang.ref.WeakReference(overlayView)
        } catch (_: Exception) {}
    }
}

/**
 * Custom container layout that coordinates with [NotchAwareness] to shift scrollable content
 * around notch obstructions in landscape orientation, and draw radial cutout masks.
 */
class NotchAwareFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SwipeBackFrameLayout(context, attrs, defStyleAttr) {

    private val prefs = context.getSharedPreferences(BatteryWidget.SETTINGS_PREFS, Context.MODE_PRIVATE)

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == NotchAwareness.KEY_NOTCH_CUSTOM_MODE ||
            key == NotchAwareness.KEY_NOTCH_IS_CENTER ||
            key == NotchAwareness.KEY_NOTCH_X ||
            key == NotchAwareness.KEY_NOTCH_Y ||
            key == NotchAwareness.KEY_NOTCH_RADIUS ||
            key == NotchAwareness.KEY_NOTCH_PILL_WIDTH) {
            invalidate()
            requestLayout()
        }
    }

    private val scrollChangeListener = ViewTreeObserver.OnScrollChangedListener {
        updateChildOffsets()
    }

    private val tempNotchRect = RectF()
    private val tempRootLoc = IntArray(2)
    private val tempCardLoc = IntArray(2)

    init {
        clipChildren = false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        clipChildren = false
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        viewTreeObserver.addOnScrollChangedListener(scrollChangeListener)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        if (viewTreeObserver.isAlive) {
            viewTreeObserver.removeOnScrollChangedListener(scrollChangeListener)
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        updateChildOffsets()
    }

    private fun updateChildOffsets() {
        val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
        }

        if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) {
            resetAllChildTranslations(this)
            return
        }

        val rect = NotchAwareness.getNotchRect(context, this, tempNotchRect)
        if (rect == null || height <= 0 || width <= 0) {
            resetAllChildTranslations(this)
            return
        }

        val isRight = rotation == Surface.ROTATION_270 || rect.centerX() > width / 2f
        if (isRight) {
            resetAllChildTranslations(this)
            return
        }

        val clearanceDp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12f, resources.displayMetrics)
        val maxEdgeDistPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48f, resources.displayMetrics)
        val distLeft = rect.left
        if (distLeft > maxEdgeDistPx) {
            resetAllChildTranslations(this)
            return
        }

        val expandedRight = rect.right + clearanceDp
        val peakX = expandedRight
        val peakY = rect.centerY()
        val totalHeight = height.toFloat()

        getLocationOnScreen(tempRootLoc)

        val reductionPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 56f, resources.displayMetrics).toInt()
        applyDetourToScrollChildren(this, peakX, peakY, totalHeight, tempRootLoc[1], reductionPx)
    }

    private fun applyDetourToScrollChildren(
        viewGroup: ViewGroup,
        peakX: Float,
        peakY: Float,
        totalHeight: Float,
        rootScreenY: Int,
        reductionPx: Int
    ) {
        viewGroup.clipChildren = false
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            if ((child.id == R.id.settings_scroll_left || child.id == R.id.hotspot_scroll_left) && child is ScrollView) {
                child.clipChildren = false
                child.elevation = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10f, resources.displayMetrics)
                val scrollContent = child.getChildAt(0) as? ViewGroup ?: continue
                scrollContent.clipChildren = false
                for (j in 0 until scrollContent.childCount) {
                    val card = scrollContent.getChildAt(j)
                    val lp = card.layoutParams as? ViewGroup.MarginLayoutParams
                    if (lp != null && lp.rightMargin != reductionPx) {
                        lp.rightMargin = reductionPx
                        card.layoutParams = lp
                    }

                    card.getLocationOnScreen(tempCardLoc)
                    val cardCenterY = (tempCardLoc[1] - rootScreenY) + card.height / 2f

                    val denominator = if (cardCenterY < peakY) peakY else (totalHeight - peakY)
                    val t = if (denominator > 0f) {
                        (1f - abs(cardCenterY - peakY) / denominator).coerceIn(0f, 1f)
                    } else 0f

                    val arcDepth = if (t > 0f) peakX * (1f - (1f - t) * (1f - t)) else 0f
                    card.translationX = arcDepth
                }
            } else if (child is ViewGroup) {
                child.clipChildren = false
                applyDetourToScrollChildren(child, peakX, peakY, totalHeight, rootScreenY, reductionPx)
            }
        }
    }

    private fun resetAllChildTranslations(viewGroup: ViewGroup) {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            if (child is ScrollView) {
                val scrollContent = child.getChildAt(0) as? ViewGroup ?: continue
                for (j in 0 until scrollContent.childCount) {
                    val card = scrollContent.getChildAt(j)
                    if (card.id == R.id.tv_settings_title || card.id == R.id.tv_hotspot_title) continue
                    card.translationX = 0f
                    val lp = card.layoutParams as? ViewGroup.MarginLayoutParams
                    if (lp != null && lp.rightMargin != 0) {
                        lp.rightMargin = 0
                        card.layoutParams = lp
                    }
                }
            } else if (child is ViewGroup) {
                resetAllChildTranslations(child)
            }
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        updateChildOffsets()
        super.dispatchDraw(canvas)
        NotchAwareness.drawRadialMask(canvas, width, height, this)
    }
}
