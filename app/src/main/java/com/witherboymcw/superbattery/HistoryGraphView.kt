package com.witherboymcw.superbattery

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import com.witherboymcw.superbattery.screens.settings.SettingsHelper
import kotlin.math.max

class HistoryGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    data class Sample(var timestampMs: Long = 0L, var value: Float = 0f)

    private val points = ArrayList<Sample>()
    private val transformedValues = FloatArray(MAX_CAPACITY)
    private val sampleColors = IntArray(MAX_CAPACITY)

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = 0xff00c853.toInt()
    }
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = com.witherboymcw.superbattery.templates.MetricColors.COLOR_GREEN
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
    private val timeLabelCache = Array(61) { "${it}${context.getString(R.string.unit_seconds_suffix)}" }
    private val timeLabelWidthCache = FloatArray(61) { textPaint.measureText(timeLabelCache[it]) }
    private val waitingForSamplesText = context.getString(R.string.waiting_for_samples)
    private val waitingForSamplesTextWidth = textPaint.measureText(waitingForSamplesText)

    private var lowerBound = 0f
    private var upperBound = 100f
    private var axisSuffix = ""
    private var nowMs = SystemClock.elapsedRealtime()
    private var hasCustomColors = false

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
        decimalPlaces: Int = 0,
        valueTransform: ((Float) -> Float)? = null,
        valueColor: ((Float) -> Int)? = null
    ) {
        points.clear()
        if (samples.size > MAX_CAPACITY) {
            val skipCount = samples.size - MAX_CAPACITY
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
        hasCustomColors = valueColor != null

        // Pre-compute transformed values and colors outside of onDraw
        val numPoints = points.size
        var idx = 0
        while (idx < numPoints) {
            val s = points[idx]
            val transformed = valueTransform?.invoke(s.value) ?: s.value
            transformedValues[idx] = transformed
            if (valueColor != null) {
                sampleColors[idx] = valueColor.invoke(transformed)
            }
            idx++
        }

        val range = upperBound - lowerBound
        val formatStr = if (decimalPlaces > 0) "%.${decimalPlaces}f" else "%.0f"
        val locale = SettingsHelper.getAppLocale(context)
        yLabelTexts[0] = String.format(locale, formatStr, upperBound) + axisSuffix
        yLabelTexts[1] = String.format(locale, formatStr, upperBound - range * 0.25f) + axisSuffix
        yLabelTexts[2] = String.format(locale, formatStr, upperBound - range * 0.5f) + axisSuffix
        yLabelTexts[3] = String.format(locale, formatStr, upperBound - range * 0.75f) + axisSuffix
        yLabelTexts[4] = String.format(locale, formatStr, lowerBound) + axisSuffix

        var maxW = 0f
        for (i in 0..4) {
            val w = textPaint.measureText(yLabelTexts[i])
            if (w > maxW) maxW = w
        }
        cachedLabelWidth = maxW

        invalidate()
    }

    var onAxisOffsetsChanged: ((leftOffset: Float, rightOffset: Float, plotWidth: Float) -> Unit)? = null
    private var lastReportedLeft = -1f
    private var lastReportedRightOffset = -1f
    private var pendingLeft = 0f
    private var pendingRightOffset = 0f
    private var pendingPlotWidth = 0f
    private val axisOffsetRunnable = Runnable {
        onAxisOffsetsChanged?.invoke(pendingLeft, pendingRightOffset, pendingPlotWidth)
    }

    var suppressMinYLabel: Boolean = false

    override fun onDetachedFromWindow() {
        removeCallbacks(axisOffsetRunnable)
        super.onDetachedFromWindow()
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
        val top = paddingTop + labelHeight / 2f + strokeInset
        val bottom = height - paddingBottom - labelHeight * 0.75f - strokeInset - 4f * density
        if (right <= left || bottom <= top) return

        val rightOffset = width - right
        val plotWidth = right - left
        if (left != lastReportedLeft || rightOffset != lastReportedRightOffset) {
            lastReportedLeft = left
            lastReportedRightOffset = rightOffset
            if (onAxisOffsetsChanged != null) {
                pendingLeft = left
                pendingRightOffset = rightOffset
                pendingPlotWidth = plotWidth
                removeCallbacks(axisOffsetRunnable)
                post(axisOffsetRunnable)
            }
        }

        if (plotBgPaint.color != Color.TRANSPARENT) {
            canvas.drawRect(left, top, right, bottom, plotBgPaint)
        }

        val labelX = left - 6f * density
        val secondsPerWindow = TIME_WINDOW_MS / 1_000L
        val windowStart = nowMs - TIME_WINDOW_MS

        // Y-axis grid lines and labels (Right-aligned at labelX)
        textPaint.textAlign = Paint.Align.RIGHT
        for (i in 0..4) {
            val fraction = i / 4f
            val y = top + (bottom - top) * fraction
            canvas.drawLine(left, y, right, y, gridPaint)
            if (i > 0 && i < 4) {
                val baseline = y - (fontMetrics.ascent + fontMetrics.descent) / 2f
                canvas.drawText(yLabelTexts[i], labelX, baseline, textPaint)
            }
        }
        val maxLabelBaseline = top - (fontMetrics.ascent + fontMetrics.descent) / 2f
        val minLabelBaseline = bottom - (fontMetrics.ascent + fontMetrics.descent) / 2f
        canvas.drawText(yLabelTexts[0], labelX, maxLabelBaseline, textPaint)
        if (!suppressMinYLabel) {
            canvas.drawText(yLabelTexts[4], labelX, minLabelBaseline, textPaint)
        }
        textPaint.textAlign = Paint.Align.LEFT

        // X-axis ruler ticks and labels (every 20s)
        val timeLabelBaseline = height - paddingBottom - fontMetrics.descent + 8f * density
        val plotHeight = bottom - top

        var secondsAgo = 0L
        while (secondsAgo <= secondsPerWindow) {
            val x = right - (secondsAgo.toFloat() / secondsPerWindow) * plotWidth
            val isMajor = secondsAgo % 20L == 0L
            if (isMajor) {
                canvas.drawLine(x, top, x, bottom, majorGridPaint)
                canvas.drawLine(x, bottom, x, bottom + 7f * density, tickPaint)
                if (secondsAgo != 0L) {
                    val secIdx = secondsAgo.toInt()
                    val label = timeLabelCache[secIdx]
                    val labelW = timeLabelWidthCache[secIdx]
                    val textLabelX = (x - labelW / 2f).coerceIn(left, right - labelW)
                    canvas.drawText(label, textLabelX, timeLabelBaseline, textPaint)
                }
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
            if (ts >= windowStart && ts <= nowMs) {
                if (startIndex == -1) startIndex = pIdx
                endIndex = pIdx
            }
            pIdx++
        }
        val visibleCount = if (startIndex != -1) (endIndex - startIndex + 1) else 0

        if (visibleCount == 0) {
            val textX = left + (plotWidth - waitingForSamplesTextWidth) / 2f
            val textY = top + plotHeight / 2f - (fontMetrics.ascent + fontMetrics.descent) / 2f
            canvas.drawText(waitingForSamplesText, textX, textY, textPaint)
            return
        }

        val boundDiff = upperBound - lowerBound

        if (visibleCount == 1) {
            val sample = points[startIndex]
            val transformedValue = transformedValues[startIndex]
            val x = left + ((sample.timestampMs - windowStart).toFloat() / TIME_WINDOW_MS) * plotWidth
            val norm = ((transformedValue - lowerBound) / boundDiff).coerceIn(0f, 1f)
            val y = bottom - norm * plotHeight
            pointPaint.color = if (hasCustomColors) sampleColors[startIndex] else linePaint.color
            canvas.drawCircle(x, y, linePaint.strokeWidth, pointPaint)
        } else {
            if (!hasCustomColors) {
                renderPath.reset()
                var i = startIndex
                while (i <= endIndex) {
                    val sample = points[i]
                    val transformedValue = transformedValues[i]
                    val x = left + ((sample.timestampMs - windowStart).toFloat() / TIME_WINDOW_MS) * plotWidth
                    val norm = ((transformedValue - lowerBound) / boundDiff).coerceIn(0f, 1f)
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
                    val prevTransformed = transformedValues[i - 1]
                    val currTransformed = transformedValues[i]
                    val prevX = left + ((prevS.timestampMs - windowStart).toFloat() / TIME_WINDOW_MS) * plotWidth
                    val prevY = bottom - (((prevTransformed - lowerBound) / boundDiff).coerceIn(0f, 1f)) * plotHeight
                    val currX = left + ((currS.timestampMs - windowStart).toFloat() / TIME_WINDOW_MS) * plotWidth
                    val currY = bottom - (((currTransformed - lowerBound) / boundDiff).coerceIn(0f, 1f)) * plotHeight

                    linePaint.color = sampleColors[i]
                    canvas.drawLine(prevX, prevY, currX, currY, linePaint)
                    i++
                }
            }

            // Single dot on newest sample
            val lastSample = points[endIndex]
            val lastTransformed = transformedValues[endIndex]
            val lastX = left + ((lastSample.timestampMs - windowStart).toFloat() / TIME_WINDOW_MS) * plotWidth
            val lastY = bottom - (((lastTransformed - lowerBound) / boundDiff).coerceIn(0f, 1f)) * plotHeight
            pointPaint.color = if (hasCustomColors) sampleColors[endIndex] else linePaint.color
            canvas.drawCircle(lastX, lastY, linePaint.strokeWidth * 1.5f, pointPaint)
        }
    }

    companion object {
        private const val TIME_WINDOW_MS = 60_000L
        private const val MAX_CAPACITY = 240
    }
}
