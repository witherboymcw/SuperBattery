package com.witherboymcw.superbattery

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
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ScrollView
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

    const val MODE_SYSTEM = 0
    const val MODE_FORCE = 1
    const val MODE_IGNORE = 2

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
     * Resolves the notch bounding rectangle considering system cutouts and custom simulated mode.
     */
    fun getNotchRect(context: Context, hostView: View, tempRect: RectF = RectF()): RectF? {
        val prefs = context.getSharedPreferences(BatteryWidget.SETTINGS_PREFS, Context.MODE_PRIVATE)
        val notchMode = prefs.getInt(KEY_NOTCH_CUSTOM_MODE, MODE_SYSTEM)
        if (notchMode == MODE_IGNORE) return null

        if (notchMode == MODE_FORCE) {
            val cachedX = prefs.getFloat(KEY_NOTCH_X, -1f)
            val cachedY = prefs.getFloat(KEY_NOTCH_Y, -1f)
            val isCenter = prefs.getBoolean(KEY_NOTCH_IS_CENTER, true)
            if (cachedX != -1f && cachedY != -1f) {
                val notchRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24f, context.resources.displayMetrics)
                val finalY = if (isCenter) hostView.height / 2f else cachedY
                tempRect.set(cachedX - notchRadius, finalY - notchRadius, cachedX + notchRadius, finalY + notchRadius)
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

    /**
     * Resolves display cutout position and boundary bounds for layout calculations.
     */
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
        val notchMode = prefs.getInt(KEY_NOTCH_CUSTOM_MODE, MODE_SYSTEM)

        if (notchMode == MODE_IGNORE) {
            return CutoutInfo(CutoutPosition.NONE)
        }

        val screenWidth = rootViewGroup.width.toFloat().takeIf { it > 0f }
            ?: context.resources.displayMetrics.widthPixels.toFloat()

        if (notchMode == MODE_FORCE) {
            val isCentered = prefs.getBoolean(KEY_NOTCH_IS_CENTER, true)
            val nX = prefs.getFloat(KEY_NOTCH_X, -1f)
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
     * Adjusts Home screen header views (left controls, settings button, dashboard title) around the cutout.
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

    /**
     * Adjusts Settings screen header views around the cutout.
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
     * Adjusts Hotspot screen header views around the cutout.
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

        when (cutoutInfo.position) {
            CutoutPosition.NONE, CutoutPosition.TOP_LEFT -> {
                cardBack.translationX = 0f
                tvHotspotTitle.translationX = 0f
            }
            CutoutPosition.CENTER -> {
                cardBack.translationX = 0f

                val targetTitleCenterX = (W / 4f)
                tvHotspotTitle.translationX = targetTitleCenterX - defaultTitleCenterX
            }
            CutoutPosition.TOP_RIGHT -> {
                val cutoutLeft = cutoutInfo.rectLeft
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
            canvas.drawOval(rect, notchPaint)
        }
    }

    /**
     * Shows an interactive overlay allowing the user to set/drag the simulated notch position.
     */
    fun showNotchPositionOverlay(activity: MainActivity) {
        val wm = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val overlayView = FrameLayout(activity)
        overlayView.setBackgroundColor(0x88000000.toInt())

        val circleView = View(activity)
        circleView.background = ShapeDrawable(OvalShape()).apply {
            paint.color = Color.BLACK
        }
        val radius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24f, activity.resources.displayMetrics).toInt()
        val lp = FrameLayout.LayoutParams(radius * 2, radius * 2)

        val prefs = activity.getSharedPreferences(BatteryWidget.SETTINGS_PREFS, Context.MODE_PRIVATE)
        val isCenter = prefs.getBoolean(KEY_NOTCH_IS_CENTER, true)
        val currentX = prefs.getFloat(KEY_NOTCH_X, 0f)
        val currentY = prefs.getFloat(KEY_NOTCH_Y, 0f)

        lp.leftMargin = currentX.toInt() - radius
        lp.topMargin = if (isCenter) activity.resources.displayMetrics.heightPixels / 2 - radius else currentY.toInt() - radius
        circleView.layoutParams = lp

        overlayView.addView(circleView)

        val textInstruction = TextView(activity)
        textInstruction.text = activity.getString(R.string.notch_instruction_text)
        textInstruction.setTextColor(Color.WHITE)
        textInstruction.gravity = Gravity.CENTER
        overlayView.addView(textInstruction, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
        })

        var dX = 0f
        var dY = 0f

        circleView.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dX = view.x - event.rawX
                    dY = view.y - event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    view.animate()
                        .x(event.rawX + dX)
                        .apply {
                            if (!isCenter) {
                                y(event.rawY + dY)
                            }
                        }
                        .setDuration(0)
                        .start()
                    true
                }
                else -> false
            }
        }

        overlayView.setOnClickListener {
            prefs.edit()
                .putFloat(KEY_NOTCH_X, circleView.x + radius)
                .putFloat(KEY_NOTCH_Y, circleView.y + radius)
                .apply()
            try {
                wm.removeView(overlayView)
            } catch (_: Exception) {}
            updateAllHeaders(activity)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(activity)) {
            try {
                activity.startActivity(Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${activity.packageName}")
                ))
            } catch (_: Exception) {}
            return
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
            key == NotchAwareness.KEY_NOTCH_Y) {
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
