package com.witherboymcw.superbattery

import android.content.Context
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

    init {
        val typedValue = TypedValue()
        if (context.theme.resolveAttribute(android.R.attr.colorBackground, typedValue, true)) {
            maskPaint.color = typedValue.data
        } else {
            maskPaint.color = Color.BLACK
        }
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

        val notchMode = prefs.getInt("notch_custom_mode", 0)
        if (notchMode == 2) return

        var notchRect: RectF? = null
        val clearanceDp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12f, resources.displayMetrics)

        if (notchMode == 1) {
            val isCentered = prefs.getBoolean("notch_is_center", true)
            val nX = prefs.getFloat("notch_x", -1f)
            val nY = prefs.getFloat("notch_y", -1f)
            if (nX != -1f && nY != -1f) {
                val notchRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24f, resources.displayMetrics)
                val finalY = if (isCentered) height / 2f else nY
                notchRect = RectF(nX - notchRadius, finalY - notchRadius, nX + notchRadius, finalY + notchRadius)
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                hostView.rootWindowInsets?.displayCutout?.boundingRects?.firstOrNull()?.let { rect ->
                    notchRect = RectF(rect)
                }
            }
        }

        if (notchRect == null) return
        val rect = notchRect!!

        // Proximity Guard Clause (48dp)
        val maxEdgeDistPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48f, resources.displayMetrics)
        val distLeft = rect.left
        val distRight = width - rect.right
        val distanceFromEdge = if (rotation == Surface.ROTATION_270) distRight else distLeft
        if (distanceFromEdge > maxEdgeDistPx) return

        val isRight = rotation == Surface.ROTATION_270 || rect.centerX() > width / 2f

        if (isRight) {
            // Right Notch: Perfect Circular Mask centered at notch bounds
            val circleRadius = maxOf(rect.width(), rect.height()) / 2f + clearanceDp
            maskPath.reset()
            maskPath.addCircle(rect.centerX(), rect.centerY(), circleRadius, Path.Direction.CW)
            canvas.drawPath(maskPath, maskPaint)
        }

        if (notchMode == 1) {
            canvas.drawOval(rect, notchPaint)
        }
    }
}
