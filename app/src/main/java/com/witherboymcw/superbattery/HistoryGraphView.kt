package com.witherboymcw.superbattery

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.min

class HistoryGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    data class Sample(val timestampMs: Long, val value: Float)

    private val points = ArrayList<Sample>()
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = 0xff00c853.toInt()
    }
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.rgb(0, 230, 118)
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density
        color = 0x33888888
    }
    private val majorGridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density
        color = 0x55888888
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density
        color = 0x66888888
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11f * resources.displayMetrics.scaledDensity
        color = 0xff888888.toInt()
    }
    private var lowerBound = 0f
    private var upperBound = 100f
    private var axisSuffix = ""
    private var colorForValue: ((Float) -> Int)? = null
    private var nowMs = SystemClock.elapsedRealtime()

    fun setHistory(
        samples: List<Sample>,
        minValue: Float,
        maxValue: Float,
        suffix: String = "",
        currentTimeMs: Long = SystemClock.elapsedRealtime(),
        valueColor: ((Float) -> Int)? = null
    ) {
        points.clear()
        points.addAll(samples.takeLast(240))
        lowerBound = minValue
        upperBound = max(minValue + 1f, maxValue)
        axisSuffix = suffix
        nowMs = currentTimeMs
        colorForValue = valueColor
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val labelWidth = max(
            textPaint.measureText("${upperBound.toInt()}$axisSuffix"),
            textPaint.measureText("${lowerBound.toInt()}$axisSuffix")
        )
        val left = paddingLeft + labelWidth + 10f * resources.displayMetrics.density
        val right = width - paddingRight - 4f
        val labelHeight = textPaint.fontMetrics.descent - textPaint.fontMetrics.ascent
        val strokeInset = linePaint.strokeWidth / 2f + 2f * resources.displayMetrics.density
        val top = paddingTop + labelHeight + strokeInset + 4f * resources.displayMetrics.density
        val bottom = height - paddingBottom - labelHeight * 2f - strokeInset - 4f * resources.displayMetrics.density
        if (right <= left || bottom <= top) return

        val labelX = paddingLeft.toFloat()
        val fontMetrics = textPaint.fontMetrics
        val secondsPerWindow = TIME_WINDOW_MS / 1_000L
        val windowStart = nowMs - TIME_WINDOW_MS
        val density = resources.displayMetrics.density
        for (i in 0..4) {
            val fraction = i / 4f
            val y = top + (bottom - top) * fraction
            canvas.drawLine(left, y, right, y, gridPaint)
            if (i in 1..3) {
                val value = upperBound - (upperBound - lowerBound) * fraction
                val baseline = y - (fontMetrics.ascent + fontMetrics.descent) / 2f
                canvas.drawText("${value.toInt()}$axisSuffix", labelX, baseline, textPaint)
            }
        }
        val maxLabelBaseline = paddingTop - fontMetrics.ascent
        val minLabelBaseline = bottom - (fontMetrics.ascent + fontMetrics.descent) / 2f
        canvas.drawText("${upperBound.toInt()}$axisSuffix", labelX, maxLabelBaseline, textPaint)
        canvas.drawText("${lowerBound.toInt()}$axisSuffix", labelX, minLabelBaseline, textPaint)

        // Minor ruler ticks mark every second; stronger grid lines and labels mark each 10 seconds.
        val timeLabelBaseline = height - paddingBottom - fontMetrics.descent
        for (secondsAgo in 0L..secondsPerWindow) {
            val x = right - (secondsAgo.toFloat() / secondsPerWindow) * (right - left)
            val isMajor = secondsAgo % 10L == 0L
            if (isMajor) {
                canvas.drawLine(x, top, x, bottom, majorGridPaint)
                canvas.drawLine(x, bottom, x, bottom + 7f * density, tickPaint)
                val label = "${secondsAgo}s"
                val labelWidth = textPaint.measureText(label)
                val labelX = when (secondsAgo) {
                    secondsPerWindow -> x
                    0L -> x - labelWidth
                    else -> x - labelWidth / 2f
                }
                canvas.drawText(label, labelX, timeLabelBaseline, textPaint)
            } else {
                canvas.drawLine(x, bottom, x, bottom + 3f * density, tickPaint)
            }
        }

        val visiblePoints = points.filter { it.timestampMs in windowStart..nowMs }
        if (visiblePoints.isEmpty()) {
            canvas.drawText(context.getString(R.string.waiting_for_samples), left + 34f, (top + bottom) / 2f, textPaint)
            return
        }
        fun xFor(sample: Sample) = left + ((sample.timestampMs - windowStart).toFloat() / TIME_WINDOW_MS) * (right - left)
        fun yFor(sample: Sample): Float {
            val normalized = ((sample.value - lowerBound) / (upperBound - lowerBound)).coerceIn(0f, 1f)
            return bottom - normalized * (bottom - top)
        }
        val valueColor = colorForValue
        if (valueColor == null) {
            val path = Path()
            visiblePoints.forEachIndexed { index, sample ->
                val x = xFor(sample)
                val y = yFor(sample)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            canvas.drawPath(path, linePaint)
        } else {
            if (visiblePoints.size == 1) {
                pointPaint.color = valueColor(visiblePoints.first().value)
                canvas.drawCircle(xFor(visiblePoints.first()), yFor(visiblePoints.first()), linePaint.strokeWidth, pointPaint)
            } else {
                for (index in 1 until visiblePoints.size) {
                    val previous = visiblePoints[index - 1]
                    val current = visiblePoints[index]
                    linePaint.color = valueColor(current.value)
                    canvas.drawLine(xFor(previous), yFor(previous), xFor(current), yFor(current), linePaint)
                }
                visiblePoints.forEach { sample ->
                    pointPaint.color = valueColor(sample.value)
                    canvas.drawCircle(xFor(sample), yFor(sample), linePaint.strokeWidth, pointPaint)
                }
            }
        }
    }

    companion object {
        private const val TIME_WINDOW_MS = 60_000L
    }
}
