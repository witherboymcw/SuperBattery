package com.witherboymcw.superbattery

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class BatteryRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var progress: Int = 100
    private var density = resources.displayMetrics.density
    private var strokeWidth = 5f * density
    private var dotRadius = 1.875f * density

    private val unitCos = FloatArray(6)
    private val unitSin = FloatArray(6)

    var startAngle: Float = 270f
        set(value) {
            if (field != value) {
                field = value
                recalculateDotVectors()
                invalidate()
            }
        }

    var maxSweep: Float = 300f
        set(value) {
            if (field != value) {
                field = value
                recalculateDotVectors()
                invalidate()
            }
        }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x33888888
        strokeCap = Paint.Cap.ROUND
        strokeWidth = this@BatteryRingView.strokeWidth
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.rgb(0, 230, 118)
        strokeCap = Paint.Cap.ROUND
        strokeWidth = this@BatteryRingView.strokeWidth
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xAA888888.toInt()
    }

    private val arcBounds = RectF()

    init {
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.BatteryRingView, defStyleAttr, 0)
            startAngle = a.getFloat(R.styleable.BatteryRingView_ringStartAngle, startAngle)
            maxSweep = a.getFloat(R.styleable.BatteryRingView_ringMaxSweep, maxSweep)
            a.recycle()
        }
        recalculateDotVectors()
    }

    private fun recalculateDotVectors() {
        for (i in 0..5) {
            val frac = i / 5f
            val tickAngleDeg = startAngle + maxSweep * frac
            val tickAngleRad = Math.toRadians(tickAngleDeg.toDouble())
            unitCos[i] = Math.cos(tickAngleRad).toFloat()
            unitSin[i] = Math.sin(tickAngleRad).toFloat()
        }
    }

    fun setProgressAndColor(percent: Int, color: Int) {
        val newProgress = percent.coerceIn(0, 100)
        var changed = false
        if (progress != newProgress) {
            progress = newProgress
            changed = true
        }
        if (progressPaint.color != color) {
            progressPaint.color = color
            changed = true
        }
        if (changed) {
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val diameter = minOf(width - paddingLeft - paddingRight, height - paddingTop - paddingBottom) - strokeWidth
        if (diameter <= 0f) return

        val cx = paddingLeft + (width - paddingLeft - paddingRight) / 2f
        val cy = paddingTop + (height - paddingTop - paddingBottom) / 2f
        val radius = diameter / 2f

        arcBounds.set(cx - radius, cy - radius, cx + radius, cy + radius)

        // 1. Draw background track arc
        canvas.drawArc(arcBounds, startAngle, maxSweep, false, trackPaint)

        // 2. Draw active progress arc
        val sweepAngle = maxSweep * (progress / 100f)
        if (sweepAngle != 0f) {
            canvas.drawArc(arcBounds, startAngle, sweepAngle, false, progressPaint)
        }

        // 3. Draw 6 dots overlaid directly on the Ring path (at 0%, 20%, 40%, 60%, 80%, 100%)
        for (i in 0..5) {
            val dotX = cx + radius * unitCos[i]
            val dotY = cy + radius * unitSin[i]
            canvas.drawCircle(dotX, dotY, dotRadius, dotPaint)
        }
    }
}