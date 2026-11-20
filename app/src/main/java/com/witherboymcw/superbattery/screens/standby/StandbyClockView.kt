package com.witherboymcw.superbattery.screens.standby

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.witherboymcw.superbattery.awareness.BatteryMath
import com.witherboymcw.superbattery.monitors.BatteryMonitorSource
import com.witherboymcw.superbattery.screens.settings.SettingsHelper
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.cos
import kotlin.math.sin

class StandbyClockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var showSeconds: Boolean = true
    private var animProgress: Float = 1.0f // 1.0 = showing seconds, 0.0 = hidden seconds
    private var secondsAnimator: ValueAnimator? = null

    private var batteryPct: Int = 50
    private var ramPct: Int = 0
    private var batteryTempC: Float = 0f
    private var skipWarning: Boolean = false

    private val density = resources.displayMetrics.density
    private val batteryMonitorSource = BatteryMonitorSource()

    private val calendar = Calendar.getInstance()
    private val digitCache = Array(60) { String.format(Locale.ROOT, "%02d", it) }
    private val bgExecutor = Executors.newSingleThreadExecutor()

    // Paints
    private val outerTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 10f * density
        strokeCap = Paint.Cap.ROUND
    }

    private val outerArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 10f * density
        strokeCap = Paint.Cap.ROUND
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val innerTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f * density
        strokeCap = Paint.Cap.ROUND
        color = Color.argb(128, 128, 128, 128)
    }

    private val innerArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f * density
        strokeCap = Paint.Cap.ROUND
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#8E8E93")
        textAlign = Paint.Align.CENTER
        typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(Typeface.DEFAULT, 600, false)
        } else {
            Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
    }

    private val textHhMmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        fontFeatureSettings = "tnum"
    }

    private val textColonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val textSsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#8E8E93")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val outerArcRect = RectF()
    private val innerArcRect = RectF()

    private val gestureDetector by lazy {
        GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                performClick()
                toggleSeconds()
                return true
            }
        }, Handler(Looper.getMainLooper()))
    }

    private val tickRunnable = object : Runnable {
        override fun run() {
            updateBatteryInfo()
            invalidate()
            val now = System.currentTimeMillis()
            val delay = 1000L - (now % 1000L)
            postDelayed(this, delay)
        }
    }

    fun setSkipWarning(skip: Boolean) {
        if (skipWarning != skip) {
            skipWarning = skip
            invalidate()
        }
    }

    fun updateMetrics() {
        try {
            val info = batteryMonitorSource.getBatteryInfo(context)
            val isSystem = SettingsHelper.isUseSystemBatteryCounter(context)
            val pct = BatteryMath.getBatteryPercentage(
                isSystem,
                info.level,
                info.scale,
                info.voltageMv
            )
            val newBatteryPct = pct.coerceIn(0, 100)
            val newTempC = info.temperatureC

            val appContext = context.applicationContext
            val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            if (am != null) {
                bgExecutor.execute {
                    try {
                        val mi = android.app.ActivityManager.MemoryInfo()
                        am.getMemoryInfo(mi)
                        val total = mi.totalMem
                        val avail = mi.availMem
                        val used = total - avail
                        val newRamPct = if (total > 0) {
                            ((used.toDouble() / total.toDouble()) * 100).toInt().coerceIn(0, 100)
                        } else ramPct

                        if (newBatteryPct != batteryPct || newTempC != batteryTempC || newRamPct != ramPct) {
                            post {
                                batteryPct = newBatteryPct
                                batteryTempC = newTempC
                                ramPct = newRamPct
                                invalidate()
                            }
                        }
                    } catch (_: Exception) {}
                }
            } else {
                if (newBatteryPct != batteryPct || newTempC != batteryTempC) {
                    batteryPct = newBatteryPct
                    batteryTempC = newTempC
                    invalidate()
                }
            }
        } catch (e: Exception) {
            // Guard against background context issues
        }
    }

    fun updateBatteryInfo() {
        updateMetrics()
    }

    private fun toggleSeconds() {
        showSeconds = !showSeconds
        secondsAnimator?.cancel()
        val target = if (showSeconds) 1.0f else 0.0f
        secondsAnimator = ValueAnimator.ofFloat(animProgress, target).apply {
            duration = 200L
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                animProgress = anim.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            performClick()
        }
        gestureDetector.onTouchEvent(event)
        return true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateBatteryInfo()
        removeCallbacks(tickRunnable)
        post(tickRunnable)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tickRunnable)
        secondsAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    private val batteryTier: Int
        get() {
            if (skipWarning) return 4
            return when {
                batteryPct > 50 -> 4
                batteryPct in 21..50 -> 2
                batteryPct in 11..20 -> 1
                else -> 0
            }
        }

    private val batteryColor: Int
        get() {
            return when (batteryTier) {
                4, 3 -> Color.parseColor("#34C759")
                2 -> Color.parseColor("#FFD60A")
                1 -> Color.parseColor("#FF9F0A")
                else -> Color.parseColor("#FF3B30")
            }
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val size = minOf(w, h)
        val cx = w / 2f
        val cy = h / 2f

        val stroke10 = 10f * density
        val outerRadius = (size / 2f) - stroke10
        val innerRadius = outerRadius - 20f * density

        calendar.timeInMillis = System.currentTimeMillis()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val second = calendar.get(Calendar.SECOND)

        // ----------------------------------------------------
        // 1. Outer 60-Second Progress Ring
        // ----------------------------------------------------
        val isTier0 = batteryTier == 0
        outerTrackPaint.color = if (isTier0) {
            Color.argb(76, 255, 214, 10)
        } else {
            Color.argb(76, 128, 128, 128)
        }
        canvas.drawCircle(cx, cy, outerRadius, outerTrackPaint)

        outerArcRect.set(cx - outerRadius, cy - outerRadius, cx + outerRadius, cy + outerRadius)
        val sweepSecond = 360f * (second / 60f)

        val activeAlpha = if (isTier0) {
            val millis = System.currentTimeMillis() % 1000L
            val sinVal = (sin((millis / 1000f) * 2 * Math.PI).toFloat() + 1f) / 2f
            0.4f + 0.6f * sinVal
        } else {
            1.0f
        }
        outerArcPaint.color = Color.parseColor("#34C759")
        outerArcPaint.alpha = (activeAlpha * 255).toInt().coerceIn(0, 255)
        canvas.drawArc(outerArcRect, -90f, sweepSecond, false, outerArcPaint)

        if (isTier0) {
            postInvalidateOnAnimation()
        }

        // ----------------------------------------------------
        // 2. 12 Hour Tick Dots
        // ----------------------------------------------------
        val dotRadius = size * 0.01f
        for (i in 0 until 12) {
            val rad = Math.toRadians((i * 30.0) - 90.0)
            val dotX = cx + (outerRadius * cos(rad)).toFloat()
            val dotY = cy + (outerRadius * sin(rad)).toFloat()
            canvas.drawCircle(dotX, dotY, dotRadius, dotPaint)
        }

        // ----------------------------------------------------
        // 3. 3 Inner Gauge Arcs
        // ----------------------------------------------------
        innerArcRect.set(cx - innerRadius, cy - innerRadius, cx + innerRadius, cy + innerRadius)

        // 3A. Top Blue Arc (RAM Usage) - Angle: 240° -> 290°, span 50°
        canvas.drawArc(innerArcRect, 240f, 50f, false, innerTrackPaint)
        val activeRamRatio = (ramPct / 100f).coerceIn(0f, 1f)
        val activeRamSweep = 50f * activeRamRatio
        innerArcPaint.color = Color.parseColor("#007AFF")
        canvas.drawArc(innerArcRect, 240f, activeRamSweep, false, innerArcPaint)

        drawArcLabel(canvas, "$ramPct%", 300f, cx, cy, innerRadius, size)

        // Animated angles for green & orange arcs based on animProgress
        val startGreenArc = 120f + 10f * animProgress
        val endGreenArc = 150f + 20f * animProgress
        val arcGreenTxt = 110f + 10f * animProgress

        val startOrangeArc = 60f - 10f * animProgress
        val endOrangeArc = 30f - 20f * animProgress
        val arcOrangeTxt = 70f - 10f * animProgress

        // 3B. Bottom-Left Battery Arc (Battery Percentage)
        val greenSpan = endGreenArc - startGreenArc
        canvas.drawArc(innerArcRect, startGreenArc, greenSpan, false, innerTrackPaint)

        val activeBatteryRatio = (batteryPct / 100f).coerceIn(0f, 1f)
        val activeGreenSweep = greenSpan * activeBatteryRatio
        innerArcPaint.color = batteryColor
        canvas.drawArc(innerArcRect, startGreenArc, activeGreenSweep, false, innerArcPaint)

        drawArcLabel(canvas, "$batteryPct%", arcGreenTxt, cx, cy, innerRadius, size)

        // 3C. Bottom-Right Orange Arc (Battery Temperature)
        val orangeSpan = endOrangeArc - startOrangeArc
        canvas.drawArc(innerArcRect, startOrangeArc, orangeSpan, false, innerTrackPaint)

        val activeTempRatio = (batteryTempC / 50f).coerceIn(0f, 1f)
        val activeOrangeSweep = orangeSpan * activeTempRatio
        val tempColor = if (batteryTempC >= 40f) Color.parseColor("#FF3B30") else Color.parseColor("#FF9F0A")
        innerArcPaint.color = tempColor
        canvas.drawArc(innerArcRect, startOrangeArc, activeOrangeSweep, false, innerArcPaint)

        val tempStr = if (batteryTempC > 0f) "${batteryTempC.toInt()}°C" else "--°C"
        drawArcLabel(canvas, tempStr, arcOrangeTxt, cx, cy, innerRadius, size)

        // ----------------------------------------------------
        // 4. Center Digital Clock
        // ----------------------------------------------------
        val hhMmTextSize = size * 0.24f
        val ssTextSize = size * 0.13f

        textHhMmPaint.textSize = hhMmTextSize
        textColonPaint.textSize = hhMmTextSize
        textSsPaint.textSize = ssTextSize

        val hhStr = digitCache[hour]
        val mmStr = digitCache[minute]
        val ssStr = digitCache[second]

        // Vertical positioning
        val hhMmOffsetY = (1f - animProgress) * (size * 0.06f)
        val baseHhMmY = cy - (size * 0.02f) + hhMmOffsetY
        val baseSsY = cy + (size * 0.14f)

        // Center HH:mm horizontally
        val hhWidth = textHhMmPaint.measureText(hhStr)
        val colonWidth = textColonPaint.measureText(":")
        val mmWidth = textHhMmPaint.measureText(mmStr)
        val totalHhMmWidth = hhWidth + colonWidth + mmWidth
        val startX = cx - (totalHhMmWidth / 2f)

        val fontMetricsHh = textHhMmPaint.fontMetrics
        val hhMmBaselineY = baseHhMmY - (fontMetricsHh.descent + fontMetricsHh.ascent) / 2f

        textHhMmPaint.textAlign = Paint.Align.LEFT
        textColonPaint.textAlign = Paint.Align.LEFT

        // Draw HH
        canvas.drawText(hhStr, startX, hhMmBaselineY, textHhMmPaint)
        // Draw blinking colon
        textColonPaint.alpha = if (second % 2 == 0) 255 else 0
        canvas.drawText(":", startX + hhWidth, hhMmBaselineY, textColonPaint)
        // Draw mm
        canvas.drawText(mmStr, startX + hhWidth + colonWidth, hhMmBaselineY, textHhMmPaint)

        // Draw ss if visible or animating
        if (animProgress > 0f) {
            textSsPaint.alpha = (animProgress * 255).toInt().coerceIn(0, 255)
            val fontMetricsSs = textSsPaint.fontMetrics
            val ssBaselineY = baseSsY - (fontMetricsSs.descent + fontMetricsSs.ascent) / 2f
            canvas.drawText(ssStr, cx, ssBaselineY, textSsPaint)
        }
    }

    private fun drawArcLabel(canvas: Canvas, text: String, angleDeg: Float, cx: Float, cy: Float, innerRadius: Float, size: Float) {
        labelPaint.textSize = size * 0.04f
        val rad = Math.toRadians(angleDeg.toDouble())
        val labelRadius = innerRadius - (14f * density)
        val lx = cx + (cos(rad) * labelRadius).toFloat()
        val ly = cy + (sin(rad) * labelRadius).toFloat()

        val fm = labelPaint.fontMetrics
        val baselineY = ly - (fm.descent + fm.ascent) / 2f
        canvas.drawText(text, lx, baselineY, labelPaint)
    }
}
