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
    private val plotBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.TRANSPARENT
    }

    private val renderPath = Path()
    private val fontMetrics = Paint.FontMetrics()
    private val yLabelTexts = Array(5) { "" }
    private var cachedLabelWidth = 0f
    private val timeLabelCache = Array(61) { "${it}s" }
    private val waitingForSamplesText = context.getString(R.string.waiting_for_samples)

    private var lowerBound = 0f
    private var upperBound = 100f
    private var axisSuffix = ""
    private var colorForValue: ((Float) -> Int)? = null
    private var nowMs = SystemClock.elapsedRealtime()

    fun setPlotBackgroundColor(color: Int) {
        if (plotBgPaint.color != color) {
            plotBgPaint.color = color
            invalidate()
        }
    }

    fun setHistory(
        samples: Collection<Sample>,
        minValue: Float,
        maxValue: Float,
        suffix: String = "",
        currentTimeMs: Long = SystemClock.elapsedRealtime(),
        valueColor: ((Float) -> Int)? = null
    ) {
        points.clear()
        if (samples.size > 240) {
            val skipCount = samples.size - 240
            var count = 0
            for (s in samples) {
                if (count++ >= skipCount) points.add(s)
            }
        } else {
            points.addAll(samples)
        }

        lowerBound = minValue
        upperBound = max(minValue + 1f, maxValue)
        axisSuffix = suffix
        nowMs = currentTimeMs
        colorForValue = valueColor

        val range = upperBound - lowerBound
        yLabelTexts[0] = "${upperBound.toInt()}$axisSuffix"
        yLabelTexts[1] = "${(upperBound - range * 0.25f).toInt()}$axisSuffix"
        yLabelTexts[2] = "${(upperBound - range * 0.5f).toInt()}$axisSuffix"
        yLabelTexts[3] = "${(upperBound - range * 0.75f).toInt()}$axisSuffix"
        yLabelTexts[4] = "${lowerBound.toInt()}$axisSuffix"

        var maxW = 0f
        for (i in 0..4) {
            val w = textPaint.measureText(yLabelTexts[i])
            if (w > maxW) maxW = w
        }
        cachedLabelWidth = maxW

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        textPaint.getFontMetrics(fontMetrics)
        val density = resources.displayMetrics.density
        val labelWidth = cachedLabelWidth
        val left = paddingLeft + labelWidth + 10f * density
        val right = width - paddingRight - 4f
        val labelHeight = fontMetrics.descent - fontMetrics.ascent
        val strokeInset = linePaint.strokeWidth / 2f + 2f * density
        val top = paddingTop + labelHeight + strokeInset + 4f * density
        val bottom = height - paddingBottom - labelHeight * 2f - strokeInset - 4f * density
        if (right <= left || bottom <= top) return

        if (plotBgPaint.color != Color.TRANSPARENT) {
            canvas.drawRect(left, top, right, bottom, plotBgPaint)
        }

        val labelX = paddingLeft.toFloat()
        val secondsPerWindow = TIME_WINDOW_MS / 1_000L
        val windowStart = nowMs - TIME_WINDOW_MS

        // Y-axis grid lines and labels
        for (i in 0..4) {
            val fraction = i / 4f
            val y = top + (bottom - top) * fraction
            canvas.drawLine(left, y, right, y, gridPaint)
            if (i in 1..3) {
                val baseline = y - (fontMetrics.ascent + fontMetrics.descent) / 2f
                canvas.drawText(yLabelTexts[i], labelX, baseline, textPaint)
            }
        }
        val maxLabelBaseline = paddingTop - fontMetrics.ascent
        val minLabelBaseline = bottom - (fontMetrics.ascent + fontMetrics.descent) / 2f
        canvas.drawText(yLabelTexts[0], labelX, maxLabelBaseline, textPaint)
        canvas.drawText(yLabelTexts[4], labelX, minLabelBaseline, textPaint)

        // X-axis ruler ticks and labels (every 20s)
        val timeLabelBaseline = height - paddingBottom - fontMetrics.descent
        val plotWidth = right - left
        val plotHeight = bottom - top

        var secondsAgo = 0L
        while (secondsAgo <= secondsPerWindow) {
            val x = right - (secondsAgo.toFloat() / secondsPerWindow) * plotWidth
            val isMajor = secondsAgo % 20L == 0L
            if (isMajor) {
                canvas.drawLine(x, top, x, bottom, majorGridPaint)
                canvas.drawLine(x, bottom, x, bottom + 7f * density, tickPaint)
                val label = timeLabelCache[secondsAgo.toInt()]
                val labelW = textPaint.measureText(label)
                val textLabelX = (x - labelW / 2f).coerceIn(left, right - labelW)
                canvas.drawText(label, textLabelX, timeLabelBaseline, textPaint)
            } else {
                canvas.drawLine(x, bottom, x, bottom + 3f * density, tickPaint)
            }
            secondsAgo++
        }

        // Visible points index window (Zero allocation)
        var startIndex = -1
        var endIndex = -1
        val numPoints = points.size
        var pIdx = 0
        while (pIdx < numPoints) {
            val ts = points[pIdx].timestampMs
            if (ts in windowStart..nowMs) {
                if (startIndex == -1) startIndex = pIdx
                endIndex = pIdx
            }
            pIdx++
        }
        val visibleCount = if (startIndex != -1) (endIndex - startIndex + 1) else 0

        if (visibleCount == 0) {
            val textWidth = textPaint.measureText(waitingForSamplesText)
            val textX = left + (plotWidth - textWidth) / 2f
            val textY = top + plotHeight / 2f - (fontMetrics.ascent + fontMetrics.descent) / 2f
            canvas.drawText(waitingForSamplesText, textX, textY, textPaint)
            return
        }

        val boundDiff = upperBound - lowerBound
        val valueColor = colorForValue

        if (visibleCount == 1) {
            val sample = points[startIndex]
            val x = left + ((sample.timestampMs - windowStart).toFloat() / TIME_WINDOW_MS) * plotWidth
            val norm = ((sample.value - lowerBound) / boundDiff).coerceIn(0f, 1f)
            val y = bottom - norm * plotHeight
            pointPaint.color = valueColor?.invoke(sample.value) ?: linePaint.color
            canvas.drawCircle(x, y, linePaint.strokeWidth, pointPaint)
        } else {
            if (valueColor == null) {
                renderPath.reset()
                var i = startIndex
                while (i <= endIndex) {
                    val sample = points[i]
                    val x = left + ((sample.timestampMs - windowStart).toFloat() / TIME_WINDOW_MS) * plotWidth
                    val norm = ((sample.value - lowerBound) / boundDiff).coerceIn(0f, 1f)
                    val y = bottom - norm * plotHeight
                    if (i == startIndex) {
                        renderPath.moveTo(x, y)
                    } else {
                        renderPath.lineTo(x, y)
                    }
                    i++
                }
                canvas.drawPath(renderPath, linePaint)
            } else {
                var i = startIndex + 1
                while (i <= endIndex) {
                    val prevS = points[i - 1]
                    val currS = points[i]
                    val prevX = left + ((prevS.timestampMs - windowStart).toFloat() / TIME_WINDOW_MS) * plotWidth
                    val prevY = bottom - (((prevS.value - lowerBound) / boundDiff).coerceIn(0f, 1f)) * plotHeight
                    val currX = left + ((currS.timestampMs - windowStart).toFloat() / TIME_WINDOW_MS) * plotWidth
                    val currY = bottom - (((currS.value - lowerBound) / boundDiff).coerceIn(0f, 1f)) * plotHeight

                    linePaint.color = valueColor.invoke(currS.value)
                    canvas.drawLine(prevX, prevY, currX, currY, linePaint)
                    i++
                }
            }

            // Single dot on newest sample
            val lastSample = points[endIndex]
            val lastX = left + ((lastSample.timestampMs - windowStart).toFloat() / TIME_WINDOW_MS) * plotWidth
            val lastY = bottom - (((lastSample.value - lowerBound) / boundDiff).coerceIn(0f, 1f)) * plotHeight
            pointPaint.color = valueColor?.invoke(lastSample.value) ?: linePaint.color
            canvas.drawCircle(lastX, lastY, linePaint.strokeWidth * 1.5f, pointPaint)
        }
    }

    companion object {
        private const val TIME_WINDOW_MS = 60_000L
    }
}
