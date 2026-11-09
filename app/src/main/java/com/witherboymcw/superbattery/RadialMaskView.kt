package com.witherboymcw.superbattery

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Surface
import android.view.View

class RadialMaskView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val notchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.BLACK
    }
    private val maskPath = Path()
    private val prefs = context.getSharedPreferences(SettingsViewController.SETTINGS_PREFS, Context.MODE_PRIVATE)

    private var cachedNotchCustomMode = 0
    private var cachedNotchIsCenter = true
    private var cachedNotchX = -1f
    private var cachedNotchY = -1f

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "notch_custom_mode" || key == "notch_is_center" || key == "notch_x" || key == "notch_y") {
            reloadNotchPrefs()
            invalidate()
        }
    }

    private fun reloadNotchPrefs() {
        cachedNotchCustomMode = prefs.getInt("notch_custom_mode", 0)
        cachedNotchIsCenter = prefs.getBoolean("notch_is_center", true)
        cachedNotchX = prefs.getFloat("notch_x", -1f)
        cachedNotchY = prefs.getFloat("notch_y", -1f)
    }

    private val tempNotchRect = RectF()

    init {
        val typedValue = TypedValue()
        if (context.theme.resolveAttribute(android.R.attr.colorBackground, typedValue, true)) {
            maskPaint.color = typedValue.data
        } else {
            maskPaint.color = Color.BLACK
        }
        reloadNotchPrefs()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        reloadNotchPrefs()
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawMask(canvas, width, height, this)
    }

    fun drawMask(canvas: Canvas, width: Int, height: Int, hostView: View) {
        val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            hostView.display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay.rotation
        }

        // Do NOT draw mask in portrait or portrait-reversed
        if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) return

        if (cachedNotchCustomMode == 2) return

        var hasNotchRect = false
        val clearanceDp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12f, resources.displayMetrics)

        if (cachedNotchCustomMode == 1) {
            if (cachedNotchX != -1f && cachedNotchY != -1f) {
                val notchRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24f, resources.displayMetrics)
                val finalY = if (cachedNotchIsCenter) height / 2f else cachedNotchY
                tempNotchRect.set(cachedNotchX - notchRadius, finalY - notchRadius, cachedNotchX + notchRadius, finalY + notchRadius)
                hasNotchRect = true
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                hostView.rootWindowInsets?.displayCutout?.boundingRects?.firstOrNull()?.let { rect ->
                    tempNotchRect.set(rect)
                    hasNotchRect = true
                }
            }
        }

        if (!hasNotchRect) return

        // Proximity Guard Clause (48dp)
        val maxEdgeDistPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48f, resources.displayMetrics)
        val distLeft = tempNotchRect.left
        val distRight = width - tempNotchRect.right
        val distanceFromEdge = if (rotation == Surface.ROTATION_270) distRight else distLeft
        if (distanceFromEdge > maxEdgeDistPx) return

        val isRight = rotation == Surface.ROTATION_270 || tempNotchRect.centerX() > width / 2f

        if (isRight) {
            // Right Notch: Perfect Circular Mask centered at notch bounds
            val circleRadius = maxOf(tempNotchRect.width(), tempNotchRect.height()) / 2f + clearanceDp
            maskPath.reset()
            maskPath.addCircle(tempNotchRect.centerX(), tempNotchRect.centerY(), circleRadius, Path.Direction.CW)
            canvas.drawPath(maskPath, maskPaint)
        }

        if (cachedNotchCustomMode == 1) {
            canvas.drawOval(tempNotchRect, notchPaint)
        }
    }
}
