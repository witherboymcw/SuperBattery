package com.witherboymcw.superbattery.screens.standby

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.sin

class StandbyMiniSquareHintView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(85, 255, 255, 255)
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(85, 255, 255, 255)
        strokeWidth = context.resources.displayMetrics.density * 1.5f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val bracketPath = Path()
    private var animator: ValueAnimator? = null
    private var currentTimeMs: Float = 0f

    init {
        isClickable = false
        isFocusable = false
    }

    fun startHintAnimation() {
        if (animator?.isRunning == true) return
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 2800f).apply {
            duration = 2800L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                currentTimeMs = anim.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun stopHintAnimation() {
        animator?.cancel()
        animator = null
        currentTimeMs = 0f
        invalidate()
    }

    override fun onDetachedFromWindow() {
        stopHintAnimation()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val density = resources.displayMetrics.density
        val cx = width / 2f
        val cy = height / 2f

        val baseDotRadius = 3f * density
        val minRippleRadius = 4f * density
        val maxRippleRadius = 20f * density
        val baseBracketDist = 18f * density
        val maxBracketNudge = 4f * density
        val bracketLegLen = 8f * density

        var dotScale = 1.0f
        var ripple1Progress = 0f
        var ripple2Progress = 0f
        var bracketNudgeFactor = 0f

        val t = currentTimeMs

        // Beat 1: 0ms..150ms (dot scale), 0ms..400ms (ripple 1)
        if (t in 0f..150f) {
            val progress = t / 150f
            dotScale = 1.0f - 0.25f * sin(progress * PI.toFloat())
        }
        if (t in 0f..400f) {
            ripple1Progress = t / 400f
        }

        // Beat 2: 220ms..370ms (dot scale 2), 220ms..620ms (ripple 2), 220ms..470ms (bracket nudge)
        if (t in 220f..370f) {
            val progress = (t - 220f) / 150f
            dotScale = 1.0f - 0.25f * sin(progress * PI.toFloat())
        }
        if (t in 220f..620f) {
            ripple2Progress = (t - 220f) / 400f
        }
        if (t in 220f..470f) {
            val progress = (t - 220f) / 250f
            bracketNudgeFactor = sin(progress * PI.toFloat())
        }

        // 1. Draw Center Dot
        val currentDotRadius = baseDotRadius * dotScale
        canvas.drawCircle(cx, cy, currentDotRadius, dotPaint)

        // 2. Draw Ripple Ring 1
        if (ripple1Progress > 0f) {
            val r = minRippleRadius + (maxRippleRadius - minRippleRadius) * ripple1Progress
            val alpha = ((1f - ripple1Progress) * 85).toInt().coerceIn(0, 85)
            strokePaint.color = Color.argb(alpha, 255, 255, 255)
            canvas.drawCircle(cx, cy, r, strokePaint)
        }

        // 3. Draw Ripple Ring 2
        if (ripple2Progress > 0f) {
            val r = minRippleRadius + (maxRippleRadius - minRippleRadius) * ripple2Progress
            val alpha = ((1f - ripple2Progress) * 85).toInt().coerceIn(0, 85)
            strokePaint.color = Color.argb(alpha, 255, 255, 255)
            canvas.drawCircle(cx, cy, r, strokePaint)
        }

        // Reset stroke paint color
        strokePaint.color = Color.argb(85, 255, 255, 255)

        // 4. Draw 4 Corner Brackets
        val dist = baseBracketDist + maxBracketNudge * bracketNudgeFactor
        val leg = bracketLegLen

        // Top-Left corner bracket
        bracketPath.reset()
        bracketPath.moveTo(cx - dist + leg, cy - dist)
        bracketPath.lineTo(cx - dist, cy - dist)
        bracketPath.lineTo(cx - dist, cy - dist + leg)
        canvas.drawPath(bracketPath, strokePaint)

        // Top-Right corner bracket
        bracketPath.reset()
        bracketPath.moveTo(cx + dist - leg, cy - dist)
        bracketPath.lineTo(cx + dist, cy - dist)
        bracketPath.lineTo(cx + dist, cy - dist + leg)
        canvas.drawPath(bracketPath, strokePaint)

        // Bottom-Right corner bracket
        bracketPath.reset()
        bracketPath.moveTo(cx + dist, cy + dist - leg)
        bracketPath.lineTo(cx + dist, cy + dist)
        bracketPath.lineTo(cx + dist - leg, cy + dist)
        canvas.drawPath(bracketPath, strokePaint)

        // Bottom-Left corner bracket
        bracketPath.reset()
        bracketPath.moveTo(cx - dist, cy + dist - leg)
        bracketPath.lineTo(cx - dist, cy + dist)
        bracketPath.lineTo(cx - dist + leg, cy + dist)
        canvas.drawPath(bracketPath, strokePaint)
    }
}
