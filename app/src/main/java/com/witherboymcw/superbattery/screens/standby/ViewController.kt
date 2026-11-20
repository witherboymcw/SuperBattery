package com.witherboymcw.superbattery.screens.standby

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewPropertyAnimator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.witherboymcw.superbattery.MainActivity
import com.witherboymcw.superbattery.R
import com.witherboymcw.superbattery.SwipeBackFrameLayout
import com.witherboymcw.superbattery.VietnameseLunarCalendar
import com.witherboymcw.superbattery.awareness.FoldableAwareness
import com.witherboymcw.superbattery.core.LocaleManager
import com.witherboymcw.superbattery.ui.DrawerScreen
import com.witherboymcw.superbattery.ui.Motion
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

internal class StandbyViewController : DrawerScreen(
    containerId = R.id.standby_container,
    slideDirection = SlideDirection.FROM_TOP,
) {
    private var virtualBox: View? = null
    private var hintView: StandbyMiniSquareHintView? = null
    private var standbyClockView: StandbyClockView? = null
    private var tvWeekday: TextView? = null
    private var tvSolarDate: TextView? = null
    private var tvLunarDate: TextView? = null
    private var homeIndicatorView: View? = null

    private var sensorManager: SensorManager? = null
    private var proximitySensor: Sensor? = null
    private var lightSensor: Sensor? = null
    private var accelerometerSensor: Sensor? = null
    private var sensorEventListener: SensorEventListener? = null
    private var isSensorRegistered: Boolean = false

    private var isProximityNear: Boolean = false
    private var isLightDropActive: Boolean = false
    private var isMicroMovementActive: Boolean = false
    private var isCombinedTriggerActive: Boolean = false
    private var remainingExtraBounces: Int = -1

    private val luxHistory = ArrayDeque<Pair<Long, Float>>()
    private val lightDropHandler = Handler(Looper.getMainLooper())
    private val resetLightDropRunnable = Runnable {
        isLightDropActive = false
        updateCombinedSensorState()
    }

    private var lastAccelMagnitude: Float = -1f
    private val microMovementHandler = Handler(Looper.getMainLooper())
    private val resetMicroMovementRunnable = Runnable {
        isMicroMovementActive = false
        updateCombinedSensorState()
    }

    private var isBouncing: Boolean = false
    private var bounceAnimator: Animator? = null
    private var fadeAnimator: ViewPropertyAnimator? = null
    private val bounceHandler = Handler(Looper.getMainLooper())
    private val bounceRunnable = Runnable { triggerBounceAnimation() }

    private var fadeOutCooldownUntil: Long = 0L
    private val cooldownHandler = Handler(Looper.getMainLooper())
    private val cooldownEndRunnable = Runnable {
        updateCombinedSensorState()
    }

    private var skipWarning: Boolean = false
    private var isMiniSquareForceFullScreen: Boolean = false

    private var lastUpdatedDayOfYear: Int = -1
    private val dateCheckHandler = Handler(Looper.getMainLooper())
    private val checkDateRunnable = object : Runnable {
        override fun run() {
            val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
            if (today != lastUpdatedDayOfYear) {
                updateDateAndCalendar()
            }
            dateCheckHandler.postDelayed(this, 1000L)
        }
    }

    private fun startDateCheck() {
        updateDateAndCalendar()
        dateCheckHandler.removeCallbacks(checkDateRunnable)
        dateCheckHandler.postDelayed(checkDateRunnable, 1000L)
    }

    private fun stopDateCheck() {
        dateCheckHandler.removeCallbacks(checkDateRunnable)
    }

    private fun setupContentViews(activity: ComponentActivity) {
        virtualBox = activity.findViewById(R.id.standby_virtual_box)
        standbyClockView = activity.findViewById(R.id.standby_clock_view)
        tvWeekday = activity.findViewById(R.id.tv_standby_weekday)
        tvSolarDate = activity.findViewById(R.id.tv_standby_solar_date)
        tvLunarDate = activity.findViewById(R.id.tv_standby_lunar_date)
        homeIndicatorView = activity.findViewById(R.id.standby_home_indicator)
        homeIndicatorView?.visibility = View.INVISIBLE
        homeIndicatorView?.alpha = 0f

        tvLunarDate?.setOnClickListener {
            skipWarning = !skipWarning
            updateLunarTextColor()
        }

        val constraintLayout = standbyClockView?.parent as? View
        if (constraintLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(constraintLayout) { v, insets ->
                val sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                val cutout = insets.displayCutout

                var safeLeft = sysBars.left
                var safeTop = sysBars.top
                var safeRight = sysBars.right
                var safeBottom = sysBars.bottom

                if (cutout != null) {
                    safeLeft = maxOf(safeLeft, cutout.safeInsetLeft)
                    safeTop = maxOf(safeTop, cutout.safeInsetTop)
                    safeRight = maxOf(safeRight, cutout.safeInsetRight)
                    safeBottom = maxOf(safeBottom, cutout.safeInsetBottom)
                }

                val isMiniSquare = FoldableAwareness.isMiniSquareMode(activity)
                val isStateB = (isMiniSquare && !activity.isInMultiWindowMode) || (isMiniSquare && isMiniSquareForceFullScreen)

                if (isStateB) {
                    v.setPadding(safeLeft, safeTop, safeRight, safeBottom)
                } else {
                    v.setPadding(
                        maxOf(safeLeft, (24f * v.resources.displayMetrics.density).toInt()),
                        maxOf(safeTop, (32f * v.resources.displayMetrics.density).toInt()),
                        maxOf(safeRight, (24f * v.resources.displayMetrics.density).toInt()),
                        maxOf(safeBottom, (32f * v.resources.displayMetrics.density).toInt())
                    )
                }

                insets
            }
        }

        updateDateAndCalendar()
    }

    fun prepareForSwipe(activity: ComponentActivity) {
        onPreOpen(activity)
    }

    override fun onPreOpen(activity: ComponentActivity) {
        (activity as? MainActivity)?.hideSystemBars()
        updateUiStateForMode(activity, isOpening = true)
        startDateCheck()
    }

    override fun onPostOpen(activity: ComponentActivity) {
        (activity as? MainActivity)?.hideSystemBars()
        registerSensors(activity)
    }

    override fun onPreClose() {
        // Keep views visible during the slide-up exit animation
    }

    override fun onPostClose(activity: ComponentActivity) {
        clearAllPendingCallbacks()
        stopDateCheck()
        unregisterSensors()
        cancelIndicatorAnimations()
        isMiniSquareForceFullScreen = false
        updateUiStateForMode(activity, isOpening = false)
        (activity as? MainActivity)?.hideSystemBars()
    }

    fun bind(activity: ComponentActivity, savedInstanceState: Bundle? = null) {
        this.activityRef = activity
        activity.onBackPressedDispatcher.addCallback(activity, backCallback)

        val standbyContainer = activity.findViewById<View>(R.id.standby_container) ?: return
        standbyContainer.isClickable = true
        standbyContainer.isFocusable = true

        hintView = activity.findViewById(R.id.standby_mini_square_hint)

        setupContentViews(activity)

        val swipeContainer = standbyContainer as? SwipeBackFrameLayout
        swipeContainer?.swipeDirection = SwipeBackFrameLayout.SWIPE_DIRECTION_UP

        swipeContainer?.swipeListener = object : SwipeBackFrameLayout.SwipeListener {
            override fun onSwipeProgress(translation: Float) {
                swipeContainer.translationY = translation
            }

            override fun onSwipeEnd(shouldClose: Boolean) {
                if (shouldClose) {
                    closeScreen(activity)
                } else {
                    swipeContainer.animate()?.cancel()
                    val sizeVal = swipeContainer.height.toFloat().takeIf { it > 0 } ?: Motion.FALLBACK_WIDTH
                    val remainingDistance = abs(swipeContainer.translationY)

                    val ratio = (remainingDistance / sizeVal).coerceIn(0f, 1f)
                    val duration = (ratio * 250L).toLong().coerceIn(100L, 250L)

                    swipeContainer.animate()
                        .translationY(0f)
                        .setDuration(duration)
                        .setInterpolator(Motion.interpolator)
                        .withLayer()
                        .start()
                }
            }
        }

        val doubleTapDetector = GestureDetector(activity, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val isMiniSquare = FoldableAwareness.isMiniSquareMode(activity)
                if (isMiniSquare && activity.isInMultiWindowMode) {
                    isMiniSquareForceFullScreen = true
                    (activity as? MainActivity)?.hideSystemBars()
                    updateUiStateForMode(activity, isOpening = true)
                    return true
                }
                return super.onDoubleTap(e)
            }
        })

        standbyContainer.setOnTouchListener { v, event ->
            doubleTapDetector.onTouchEvent(event)
            v.onTouchEvent(event)
        }

        if (isOpen) {
            updateUiStateForMode(activity, isOpening = true)
            startDateCheck()
        }
    }

    private fun updateUiStateForMode(activity: ComponentActivity, isOpening: Boolean) {

        val hv = hintView ?: activity.findViewById<StandbyMiniSquareHintView>(R.id.standby_mini_square_hint)
        val vb = virtualBox ?: activity.findViewById<View>(R.id.standby_virtual_box)
        val dateContainer = activity.findViewById<LinearLayout>(R.id.standby_date_container)
        val isMiniSquare = FoldableAwareness.isMiniSquareMode(activity)

        if (!isOpening) {
            hv?.stopHintAnimation()
            hv?.visibility = View.GONE
            vb?.visibility = View.GONE
            return
        }

        val swipeContainer = activity.findViewById<View>(R.id.standby_container) as? SwipeBackFrameLayout
        swipeContainer?.swipeDirection = SwipeBackFrameLayout.SWIPE_DIRECTION_UP

        val isStateB = (isMiniSquare && !activity.isInMultiWindowMode) || (isMiniSquare && isMiniSquareForceFullScreen)
        val isStateA = isMiniSquare && activity.isInMultiWindowMode && !isMiniSquareForceFullScreen

        if (isStateA) {
            // State A: Mini Square Multi-Window Hint
            hv?.visibility = View.VISIBLE
            hv?.startHintAnimation()
            vb?.visibility = View.GONE
        } else if (isStateB) {
            // State B: Mini Square Full Screen Clock
            hv?.stopHintAnimation()
            hv?.visibility = View.GONE
            vb?.visibility = View.VISIBLE
            dateContainer?.visibility = View.GONE

            val clockView = standbyClockView
            val parentLayout = clockView?.parent as? ConstraintLayout
            if (clockView != null && parentLayout != null) {
                val set = ConstraintSet()
                set.clone(parentLayout)
                set.clear(clockView.id)
                set.connect(clockView.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                set.connect(clockView.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
                set.connect(clockView.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                set.connect(clockView.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                set.setDimensionRatio(clockView.id, "1:1")
                set.applyTo(parentLayout)
            }
        } else {
            // State C: Normal Full Screen (Portrait or Landscape)
            hv?.stopHintAnimation()
            hv?.visibility = View.GONE
            vb?.visibility = View.VISIBLE
            dateContainer?.visibility = View.VISIBLE

            val isLandscape = activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val targetGravity = if (isLandscape) Gravity.END else Gravity.CENTER

            dateContainer?.gravity = targetGravity
            tvWeekday?.gravity = targetGravity
            tvSolarDate?.gravity = targetGravity
            tvLunarDate?.gravity = targetGravity
        }
    }

    private fun updateDateAndCalendar() {
        val act = activityRef ?: return
        val appLocale = LocaleManager.getAppLocale(act)
        val cal = Calendar.getInstance()

        val dayOfWeek = cal[Calendar.DAY_OF_WEEK]
        val dayOfMonth = cal[Calendar.DAY_OF_MONTH]
        val month = cal[Calendar.MONTH] + 1
        val year = cal[Calendar.YEAR]
        lastUpdatedDayOfYear = cal[Calendar.DAY_OF_YEAR]

        val lang = appLocale.language.lowercase()

        // 1. Weekday
        val weekdayStr = when (lang) {
            "vi" -> when (dayOfWeek) {
                Calendar.SUNDAY -> "Chủ Nhật"
                Calendar.MONDAY -> "Thứ Hai"
                Calendar.TUESDAY -> "Thứ Ba"
                Calendar.WEDNESDAY -> "Thứ Tư"
                Calendar.THURSDAY -> "Thứ Năm"
                Calendar.FRIDAY -> "Thứ Sáu"
                Calendar.SATURDAY -> "Thứ Bảy"
                else -> ""
            }
            "zh" -> when (dayOfWeek) {
                Calendar.SUNDAY -> "星期日"
                Calendar.MONDAY -> "星期一"
                Calendar.TUESDAY -> "星期二"
                Calendar.WEDNESDAY -> "星期三"
                Calendar.THURSDAY -> "星期四"
                Calendar.FRIDAY -> "星期五"
                Calendar.SATURDAY -> "星期六"
                else -> ""
            }
            else -> when (dayOfWeek) {
                Calendar.SUNDAY -> "Sunday"
                Calendar.MONDAY -> "Monday"
                Calendar.TUESDAY -> "Tuesday"
                Calendar.WEDNESDAY -> "Wednesday"
                Calendar.THURSDAY -> "Thursday"
                Calendar.FRIDAY -> "Friday"
                Calendar.SATURDAY -> "Saturday"
                else -> ""
            }
        }
        tvWeekday?.text = weekdayStr

        // 2. Solar Date
        val yellowColor = Color.parseColor("#FFD60A")
        val dayStr = dayOfMonth.toString()
        val monthStr = month.toString()
        val yearStr = year.toString()

        val spannable = when (lang) {
            "vi" -> {
                val prefix1 = "Ngày "
                val prefix2 = " Tháng "
                val prefix3 = " Năm "
                val solarText = "$prefix1$dayStr$prefix2$monthStr$prefix3$yearStr"
                val sb = SpannableStringBuilder(solarText)

                val dayStart = prefix1.length
                val dayEnd = dayStart + dayStr.length
                val monthStart = dayEnd + prefix2.length
                val monthEnd = monthStart + monthStr.length
                val yearStart = monthEnd + prefix3.length
                val yearEnd = yearStart + yearStr.length

                sb.setSpan(ForegroundColorSpan(yellowColor), dayStart, dayEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(ForegroundColorSpan(yellowColor), monthStart, monthEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(ForegroundColorSpan(yellowColor), yearStart, yearEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb
            }
            "zh" -> {
                val solarText = "${yearStr}年${monthStr}月${dayStr}日"
                val sb = SpannableStringBuilder(solarText)

                val yIdx = solarText.indexOf(yearStr)
                if (yIdx >= 0) sb.setSpan(ForegroundColorSpan(yellowColor), yIdx, yIdx + yearStr.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                val mIdx = solarText.indexOf("${monthStr}月")
                if (mIdx >= 0) sb.setSpan(ForegroundColorSpan(yellowColor), mIdx, mIdx + monthStr.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                val dIdx = solarText.indexOf("${dayStr}日")
                if (dIdx >= 0) sb.setSpan(ForegroundColorSpan(yellowColor), dIdx, dIdx + dayStr.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb
            }
            else -> {
                val monthName = SimpleDateFormat("MMMM", Locale.ENGLISH).format(cal.time)
                val solarText = "$monthName $dayStr, $yearStr"
                val sb = SpannableStringBuilder(solarText)

                val dayIdx = solarText.indexOf(dayStr)
                if (dayIdx >= 0) sb.setSpan(ForegroundColorSpan(yellowColor), dayIdx, dayIdx + dayStr.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                val yearIdx = solarText.lastIndexOf(yearStr)
                if (yearIdx >= 0) sb.setSpan(ForegroundColorSpan(yellowColor), yearIdx, yearIdx + yearStr.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb
            }
        }
        tvSolarDate?.text = spannable

        // 3. Lunar Date & Can Chi
        val lunarDate = VietnameseLunarCalendar.fromSolar(dayOfMonth, month, year)
        val lunarText = when (lang) {
            "vi" -> {
                val canChiStr = VietnameseLunarCalendar.canChiYear(lunarDate.year)
                val leapStr = if (lunarDate.isLeapMonth) " (Nhuận)" else ""
                "Ngày ${lunarDate.day} tháng ${lunarDate.month}$leapStr, Năm $canChiStr"
            }
            "zh" -> {
                lunarDateStringChinese(lunarDate.day, lunarDate.month, lunarDate.year, lunarDate.isLeapMonth)
            }
            else -> {
                lunarDateStringEnglish(lunarDate.day, lunarDate.month, lunarDate.year, lunarDate.isLeapMonth)
            }
        }
        tvLunarDate?.text = lunarText

        updateLunarTextColor()
    }

    private fun lunarDateStringChinese(lunarDay: Int, lunarMonth: Int, lunarYear: Int, isLeapMonth: Boolean): String {
        val stems = arrayOf("甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸")
        val branches = arrayOf("子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥")
        val months = arrayOf("正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "腊")
        val units = arrayOf("", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十")

        val stem = stems[Math.floorMod(lunarYear - 4, 10)]
        val branch = branches[Math.floorMod(lunarYear - 4, 12)]
        val monthName = months[Math.floorMod(lunarMonth - 1, 12)]
        val leapPrefix = if (isLeapMonth) "闰" else ""

        val dayString = when (lunarDay) {
            in 1..10 -> "初" + units[lunarDay]
            in 11..19 -> "十" + units[lunarDay - 10]
            20 -> "二十"
            in 21..29 -> "廿" + units[lunarDay - 20]
            30 -> "三十"
            else -> lunarDay.toString()
        }

        return "${stem}${branch}年 ${leapPrefix}${monthName}月 $dayString"
    }

    private fun lunarDateStringEnglish(lunarDay: Int, lunarMonth: Int, lunarYear: Int, isLeapMonth: Boolean): String {
        val heavenlyStems = arrayOf(
            "Aethel",  // Giáp
            "Elowen",  // Ất
            "Solos",   // Bính
            "Ignis",   // Đinh
            "Titan",   // Mậu
            "Gaia",    // Kỷ
            "Valiant", // Canh
            "Aureon",  // Tân
            "Oceanus", // Nhâm
            "Zephyr"   // Quý
        )

        val earthlyBranches = arrayOf(
            "Rat",     // Tý
            "Ox",      // Sửu
            "Tiger",   // Dần
            "Cat",     // Mão
            "Dragon",  // Thìn
            "Snake",   // Tị
            "Horse",   // Ngọ
            "Goat",    // Mùi
            "Monkey",  // Thân
            "Rooster", // Dậu
            "Dog",     // Tuất
            "Pig"      // Hợi
        )

        val months = arrayOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )

        val stem = heavenlyStems[Math.floorMod(lunarYear - 4, 10)]
        val branch = earthlyBranches[Math.floorMod(lunarYear - 4, 12)]
        val monthString = months[Math.floorMod(lunarMonth - 1, 12)]
        val leapStr = if (isLeapMonth) " (Leap)" else ""

        return "$monthString $lunarDay$leapStr, $stem the $branch"
    }

    private fun updateLunarTextColor() {
        val textColor = if (skipWarning) Color.parseColor("#34C759") else Color.parseColor("#FFD60A")
        tvLunarDate?.setTextColor(textColor)
        standbyClockView?.setSkipWarning(skipWarning)
    }

    fun onResume(activity: ComponentActivity) {
        this.activityRef = activity
        if (isOpen) {
            registerSensors(activity)
        }
    }

    fun onPause() {
        unregisterSensors()
        cancelIndicatorAnimations()
    }

    override fun unbind() {
        clearAllPendingCallbacks()
        stopDateCheck()
        unregisterSensors()
        cancelIndicatorAnimations()
        isMiniSquareForceFullScreen = false
        activityRef?.let {
            updateUiStateForMode(it, isOpening = false)
        }
        virtualBox = null
        hintView = null
        standbyClockView = null
        tvWeekday = null
        tvSolarDate = null
        tvLunarDate = null
        homeIndicatorView = null
        sensorManager = null
        proximitySensor = null
        lightSensor = null
        accelerometerSensor = null
        sensorEventListener = null
        super.unbind()
    }

    private fun clearAllPendingCallbacks() {
        lightDropHandler.removeCallbacksAndMessages(null)
        microMovementHandler.removeCallbacksAndMessages(null)
        bounceHandler.removeCallbacksAndMessages(null)
        cooldownHandler.removeCallbacksAndMessages(null)
        dateCheckHandler.removeCallbacksAndMessages(null)
    }

    private fun registerSensors(activity: ComponentActivity) {
        if (isSensorRegistered) return
        if (sensorManager == null) {
            sensorManager = activity.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        }
        val sm = sensorManager ?: return

        if (proximitySensor == null) {
            proximitySensor = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        }
        if (lightSensor == null) {
            lightSensor = sm.getDefaultSensor(Sensor.TYPE_LIGHT)
        }
        if (accelerometerSensor == null) {
            accelerometerSensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        }

        if (sensorEventListener == null) {
            sensorEventListener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    if (event == null) return
                    when (event.sensor.type) {
                        Sensor.TYPE_PROXIMITY -> handleProximityEvent(event)
                        Sensor.TYPE_LIGHT -> handleLightEvent(event)
                        Sensor.TYPE_ACCELEROMETER -> handleAccelerometerEvent(event)
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
        }

        val listener = sensorEventListener ?: return
        proximitySensor?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        lightSensor?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        accelerometerSensor?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        isSensorRegistered = true
    }

    private fun unregisterSensors() {
        if (!isSensorRegistered) return
        sensorEventListener?.let { listener ->
            sensorManager?.unregisterListener(listener)
        }
        isSensorRegistered = false

        luxHistory.clear()
        lightDropHandler.removeCallbacks(resetLightDropRunnable)
        microMovementHandler.removeCallbacks(resetMicroMovementRunnable)

        isProximityNear = false
        isLightDropActive = false
        isMicroMovementActive = false
        isCombinedTriggerActive = false
        remainingExtraBounces = -1
        lastAccelMagnitude = -1f
    }

    private fun handleProximityEvent(event: SensorEvent) {
        val distance = event.values[0]
        val maxRange = event.sensor.maximumRange
        val isNear = distance < maxRange
        if (isProximityNear != isNear) {
            isProximityNear = isNear
            updateCombinedSensorState()
        }
    }

    private fun handleLightEvent(event: SensorEvent) {
        val currentLux = event.values[0]
        val now = SystemClock.elapsedRealtime()

        while (luxHistory.isNotEmpty() && (now - luxHistory.first().first > 1000L)) {
            luxHistory.removeFirst()
        }

        if (luxHistory.isNotEmpty()) {
            val avgLux = luxHistory.sumOf { it.second.toDouble() }.toFloat() / luxHistory.size
            val timeSpan = now - luxHistory.first().first
            if (timeSpan >= 200L && avgLux >= 5.0f) {
                val dropRatio = (avgLux - currentLux) / avgLux
                if (dropRatio >= 0.30f) {
                    triggerLightDrop()
                }
            }
        }

        luxHistory.addLast(Pair(now, currentLux))
    }

    private fun triggerLightDrop() {
        isLightDropActive = true
        lightDropHandler.removeCallbacks(resetLightDropRunnable)
        lightDropHandler.postDelayed(resetLightDropRunnable, 1500L)
        updateCombinedSensorState()
    }

    private fun handleAccelerometerEvent(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val magnitude = kotlin.math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()

        if (lastAccelMagnitude >= 0f) {
            val delta = abs(magnitude - lastAccelMagnitude)
            if (delta > 0.35f) {
                triggerMicroMovement()
            }
        }
        lastAccelMagnitude = magnitude
    }

    private fun triggerMicroMovement() {
        isMicroMovementActive = true
        microMovementHandler.removeCallbacks(resetMicroMovementRunnable)
        microMovementHandler.postDelayed(resetMicroMovementRunnable, 2000L)
        updateCombinedSensorState()
    }

    private fun isSensorTriggerActive(): Boolean =
        SystemClock.elapsedRealtime() >= fadeOutCooldownUntil && (isProximityNear || isLightDropActive || isMicroMovementActive)

    private fun updateCombinedSensorState() {
        val newActive = isSensorTriggerActive()
        if (isCombinedTriggerActive != newActive) {
            isCombinedTriggerActive = newActive

            if (newActive) {
                remainingExtraBounces = -1
                fadeInIndicator()
                if (!isBouncing) {
                    startBounceLoopIfNeeded()
                }
            } else {
                remainingExtraBounces = kotlin.random.Random.nextInt(3, 5)
                if (!isBouncing) {
                    startBounceLoopIfNeeded()
                }
            }
        } else if (newActive) {
            remainingExtraBounces = -1
            fadeInIndicator()
            if (!isBouncing) {
                startBounceLoopIfNeeded()
            }
        }
    }

    private fun fadeInIndicator() {
        if (SystemClock.elapsedRealtime() < fadeOutCooldownUntil) return
        val indicator = homeIndicatorView ?: activityRef?.findViewById<View>(R.id.standby_home_indicator) ?: return
        fadeAnimator?.cancel()
        indicator.animate()?.cancel()
        indicator.visibility = View.VISIBLE
        fadeAnimator = indicator.animate()
            .alpha(1f)
            .setDuration(300L)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    fadeAnimator = null
                }
            })
        fadeAnimator?.start()
    }

    private fun fadeOutIndicator() {
        val indicator = homeIndicatorView ?: activityRef?.findViewById<View>(R.id.standby_home_indicator) ?: return
        if (indicator.visibility != View.VISIBLE && indicator.alpha == 0f) return

        fadeOutCooldownUntil = SystemClock.elapsedRealtime() + 5000L
        cooldownHandler.removeCallbacks(cooldownEndRunnable)
        cooldownHandler.postDelayed(cooldownEndRunnable, 5000L)

        isCombinedTriggerActive = false
        remainingExtraBounces = 0

        fadeAnimator?.cancel()
        indicator.animate()?.cancel()
        fadeAnimator = indicator.animate()
            .alpha(0f)
            .setDuration(300L)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    indicator.visibility = View.INVISIBLE
                    indicator.translationY = 0f
                    fadeAnimator = null
                }
            })
        fadeAnimator?.start()
    }

    private fun startBounceLoopIfNeeded() {
        bounceHandler.removeCallbacks(bounceRunnable)
        val shouldRun = isCombinedTriggerActive || remainingExtraBounces > 0
        if (!isBouncing && shouldRun && isOpen) {
            triggerBounceAnimation()
        }
    }

    private fun triggerBounceAnimation() {
        val indicator = homeIndicatorView ?: activityRef?.findViewById<View>(R.id.standby_home_indicator) ?: return
        val act = activityRef ?: return

        val active = isCombinedTriggerActive
        if (!isOpen || (!active && remainingExtraBounces <= 0) || isBouncing) {
            if (!isOpen || (!active && remainingExtraBounces <= 0)) {
                fadeOutIndicator()
            }
            return
        }

        if (!active) {
            remainingExtraBounces--
        }

        val density = act.resources.displayMetrics.density
        val bouncePx = 14f * density

        isBouncing = true
        bounceAnimator?.cancel()
        indicator.translationY = 0f

        val upAnim = ObjectAnimator.ofFloat(indicator, View.TRANSLATION_Y, 0f, -bouncePx).apply {
            duration = 350L
            interpolator = DecelerateInterpolator(1.5f)
        }
        val downAnim = ObjectAnimator.ofFloat(indicator, View.TRANSLATION_Y, -bouncePx, 0f).apply {
            duration = 350L
            interpolator = AccelerateInterpolator(1.5f)
        }

        val set = AnimatorSet()
        set.playSequentially(upAnim, downAnim)
        set.addListener(object : AnimatorListenerAdapter() {
            private var isCancelled = false

            override fun onAnimationCancel(animation: Animator) {
                isCancelled = true
                isBouncing = false
                indicator.translationY = 0f
            }

            override fun onAnimationEnd(animation: Animator) {
                bounceAnimator = null
                indicator.translationY = 0f
                if (isCancelled) return
                isBouncing = false

                val stillActive = isCombinedTriggerActive
                if (stillActive) {
                    remainingExtraBounces = -1
                    bounceHandler.removeCallbacks(bounceRunnable)
                    bounceHandler.postDelayed(bounceRunnable, 1500L)
                } else if (remainingExtraBounces > 0) {
                    bounceHandler.removeCallbacks(bounceRunnable)
                    bounceHandler.postDelayed(bounceRunnable, 1500L)
                } else {
                    fadeOutIndicator()
                }
            }
        })

        bounceAnimator = set
        set.start()
    }

    private fun cancelIndicatorAnimations() {
        bounceHandler.removeCallbacks(bounceRunnable)
        lightDropHandler.removeCallbacks(resetLightDropRunnable)
        microMovementHandler.removeCallbacks(resetMicroMovementRunnable)
        cooldownHandler.removeCallbacks(cooldownEndRunnable)

        bounceAnimator?.cancel()
        bounceAnimator = null
        isBouncing = false
        remainingExtraBounces = -1
        fadeOutCooldownUntil = 0L

        fadeAnimator?.cancel()
        fadeAnimator = null

        val indicator = homeIndicatorView ?: activityRef?.findViewById<View>(R.id.standby_home_indicator)
        indicator?.animate()?.cancel()
        indicator?.alpha = 0f
        indicator?.visibility = View.INVISIBLE
        indicator?.translationY = 0f

        isProximityNear = false
        isLightDropActive = false
        isMicroMovementActive = false
        isCombinedTriggerActive = false
        lastAccelMagnitude = -1f
        luxHistory.clear()
    }
}
