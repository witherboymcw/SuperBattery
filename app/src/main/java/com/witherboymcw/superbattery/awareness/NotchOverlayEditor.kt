package com.witherboymcw.superbattery.awareness

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import com.witherboymcw.superbattery.MainActivity
import com.witherboymcw.superbattery.R
import com.witherboymcw.superbattery.settings.Permissions
import com.witherboymcw.superbattery.templates.dp
import com.witherboymcw.superbattery.templates.getDisplayRotationCompat
import com.witherboymcw.superbattery.widgets.BatteryWidget
import java.lang.ref.WeakReference
import kotlin.math.abs

/**
 * Overlay window editor for interactively dragging, sizing, and positioning simulated notch cutouts.
 */
object NotchOverlayEditor {

    var liveCutoutOverride: NotchGeometry.CutoutInfo? = null
        private set

    private var activeOverlayViewRef: WeakReference<View>? = null
    private var activeWindowManagerRef: WeakReference<WindowManager>? = null

    fun dismissNotchPositionOverlay() {
        liveCutoutOverride = null
        val overlay = activeOverlayViewRef?.get()
        val wm = activeWindowManagerRef?.get()
        if (overlay != null && wm != null) {
            try {
                wm.removeView(overlay)
            } catch (_: Exception) {}
        }
        activeOverlayViewRef?.clear()
        activeOverlayViewRef = null
        activeWindowManagerRef?.clear()
        activeWindowManagerRef = null
    }

    fun showNotchPositionOverlay(activity: MainActivity, onRefresh: () -> Unit) {
        dismissNotchPositionOverlay()

        if (!Permissions.isOverlayEnabled(activity)) {
            try {
                activity.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${activity.packageName}")
                    )
                )
            } catch (_: Exception) {}
            return
        }

        val wm = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val screenWidth: Float
        val screenHeight: Float
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            screenWidth = bounds.width().toFloat()
            screenHeight = bounds.height().toFloat()
        } else {
            val dm = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            screenWidth = dm.widthPixels.toFloat()
            screenHeight = dm.heightPixels.toFloat()
        }

        val isLandscape = activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val rotation = activity.getDisplayRotationCompat()

        val prefs = activity.getSharedPreferences(BatteryWidget.SETTINGS_PREFS, Context.MODE_PRIVATE)
        var isCenter = prefs.getBoolean(NotchGeometry.KEY_NOTCH_IS_CENTER, true)
        var currentRadiusDp = prefs.getFloat(NotchGeometry.KEY_NOTCH_RADIUS, NotchGeometry.DEFAULT_NOTCH_RADIUS)
        var currentPillDp = prefs.getFloat(NotchGeometry.KEY_NOTCH_PILL_WIDTH, NotchGeometry.DEFAULT_NOTCH_PILL_WIDTH)

        var currentX = prefs.getFloat(NotchGeometry.KEY_NOTCH_X, -1f)
        var currentY = prefs.getFloat(NotchGeometry.KEY_NOTCH_Y, -1f)

        val radiusPxInitial = activity.dp(currentRadiusDp)

        if (isLandscape || rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
            if (currentY < 0f || isCenter) currentY = screenHeight / 2f
            val isRight = rotation == Surface.ROTATION_270
            if (currentX < 0f) {
                currentX = if (isRight) screenWidth - radiusPxInitial else radiusPxInitial
            }
        } else {
            if (currentX < 0f || isCenter) currentX = screenWidth / 2f
            if (currentY < 0f) currentY = radiusPxInitial
        }

        val overlayView = FrameLayout(activity)
        overlayView.setBackgroundColor(0x77000000.toInt())

        val cutoutView = View(activity)
        val cutoutDrawable = GradientDrawable().apply {
            setColor(Color.BLACK)
            setStroke(activity.dp(2f).toInt(), Color.WHITE)
        }
        cutoutView.background = cutoutDrawable

        fun updateCutoutLayout() {
            val radiusPx = activity.dp(currentRadiusDp)
            val pillPx = activity.dp(currentPillDp)

            val halfW: Float
            val halfH: Float

            if (isLandscape || rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
                halfW = radiusPx
                halfH = radiusPx + pillPx / 2f
                if (isCenter) {
                    currentY = screenHeight / 2f
                }
            } else {
                halfW = radiusPx + pillPx / 2f
                halfH = radiusPx
                if (isCenter) {
                    currentX = screenWidth / 2f
                }
            }

            cutoutDrawable.cornerRadius = minOf(halfW, halfH)

            val lp = FrameLayout.LayoutParams((halfW * 2f).toInt(), (halfH * 2f).toInt())
            lp.leftMargin = (currentX - halfW).toInt().coerceIn(0, (screenWidth - halfW * 2f).toInt().coerceAtLeast(0))
            lp.topMargin = (currentY - halfH).toInt().coerceIn(0, (screenHeight - halfH * 2f).toInt().coerceAtLeast(0))
            cutoutView.layoutParams = lp
        }

        updateCutoutLayout()
        overlayView.addView(cutoutView)

        // Control Panel
        val controlCard = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xEE1E1E1E.toInt())
            val pPx = activity.dp(16f).toInt()
            setPadding(pPx, pPx, pPx, pPx)
        }

        val cardParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM
            val marginPx = activity.dp(16f).toInt()
            setMargins(marginPx, marginPx, marginPx, marginPx)
        }

        val tvInstruction = TextView(activity).apply {
            text = activity.getString(R.string.notch_instruction_text)
            setTextColor(Color.WHITE)
            textSize = 12f
            gravity = Gravity.CENTER
        }
        controlCard.addView(tvInstruction)

        val radioGroupPos = RadioGroup(activity).apply {
            orientation = RadioGroup.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 8)
        }
        val radioCenter = RadioButton(activity).apply {
            text = activity.getString(R.string.notch_pos_center)
            setTextColor(Color.WHITE)
            isChecked = isCenter
        }
        val radioManual = RadioButton(activity).apply {
            text = activity.getString(R.string.notch_pos_not_center)
            setTextColor(Color.WHITE)
            isChecked = !isCenter
        }
        radioGroupPos.addView(radioCenter)
        radioGroupPos.addView(radioManual)
        controlCard.addView(radioGroupPos)

        val sizeHeaderLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 0)
        }
        val tvSizeTitle = TextView(activity).apply {
            text = activity.getString(R.string.notch_size_title)
            setTextColor(Color.WHITE)
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tvSizeVal = TextView(activity).apply {
            text = "${currentRadiusDp.toInt()} dp"
            setTextColor(0xFFCCCCCC.toInt())
            textSize = 12f
        }
        sizeHeaderLayout.addView(tvSizeTitle)
        sizeHeaderLayout.addView(tvSizeVal)
        controlCard.addView(sizeHeaderLayout)

        val sizeSeekLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val seekBarSize = SeekBar(activity).apply {
            max = NotchGeometry.MAX_NOTCH_RADIUS.toInt()
            progress = currentRadiusDp.toInt()
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        sizeSeekLayout.addView(seekBarSize)
        controlCard.addView(sizeSeekLayout)

        val pillHeaderLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 0)
        }
        val tvPillTitle = TextView(activity).apply {
            text = activity.getString(R.string.notch_pill_title)
            setTextColor(Color.WHITE)
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tvPillVal = TextView(activity).apply {
            text = "${currentPillDp.toInt()} dp"
            setTextColor(0xFFCCCCCC.toInt())
            textSize = 12f
        }
        pillHeaderLayout.addView(tvPillTitle)
        pillHeaderLayout.addView(tvPillVal)
        controlCard.addView(pillHeaderLayout)

        val pillSeekLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val seekBarPill = SeekBar(activity).apply {
            max = NotchGeometry.MAX_NOTCH_PILL_WIDTH.toInt()
            progress = currentPillDp.toInt()
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        pillSeekLayout.addView(seekBarPill)
        controlCard.addView(pillSeekLayout)

        val btnSave = Button(activity).apply {
            text = "Done"
            textSize = 12f
        }
        controlCard.addView(btnSave, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = 8
        })

        overlayView.addView(controlCard, cardParams)

        fun refreshPreview() {
            val radiusPx = activity.dp(currentRadiusDp)
            val pillWidthPx = activity.dp(currentPillDp)
            val halfWidth = radiusPx + pillWidthPx / 2f

            val cX = if (isCenter || currentX < 0f) screenWidth / 2f else currentX
            val leftBound = cX - halfWidth
            val rightBound = cX + halfWidth

            val pos = when {
                isCenter -> NotchGeometry.CutoutPosition.CENTER
                cX < screenWidth * 0.35f -> NotchGeometry.CutoutPosition.TOP_LEFT
                cX > screenWidth * 0.65f -> NotchGeometry.CutoutPosition.TOP_RIGHT
                else -> NotchGeometry.CutoutPosition.CENTER
            }
            liveCutoutOverride = NotchGeometry.CutoutInfo(pos, leftBound, rightBound)
            updateCutoutLayout()
            onRefresh()
        }

        fun commitToPrefs() {
            liveCutoutOverride = null
            prefs.edit()
                .putBoolean(NotchGeometry.KEY_NOTCH_IS_CENTER, isCenter)
                .putFloat(NotchGeometry.KEY_NOTCH_X, currentX)
                .putFloat(NotchGeometry.KEY_NOTCH_Y, currentY)
                .putFloat(NotchGeometry.KEY_NOTCH_RADIUS, currentRadiusDp)
                .putFloat(NotchGeometry.KEY_NOTCH_PILL_WIDTH, currentPillDp)
                .apply()
            updateCutoutLayout()
            onRefresh()
        }

        radioGroupPos.setOnCheckedChangeListener { _, checkedId ->
            isCenter = (checkedId == radioCenter.id)
            commitToPrefs()
        }

        seekBarSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentRadiusDp = progress.toFloat().coerceIn(NotchGeometry.MIN_NOTCH_RADIUS, NotchGeometry.MAX_NOTCH_RADIUS)
                    tvSizeVal.text = "${currentRadiusDp.toInt()} dp"
                    refreshPreview()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                commitToPrefs()
            }
        })

        seekBarPill.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentPillDp = progress.toFloat().coerceIn(NotchGeometry.MIN_NOTCH_PILL_WIDTH, NotchGeometry.MAX_NOTCH_PILL_WIDTH)
                    tvPillVal.text = "${currentPillDp.toInt()} dp"
                    refreshPreview()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                commitToPrefs()
            }
        })

        btnSave.setOnClickListener {
            commitToPrefs()
            dismissNotchPositionOverlay()
        }

        val scaleDetector = ScaleGestureDetector(activity, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scaleFactor = detector.scaleFactor
                val newRadius = (currentRadiusDp * scaleFactor).coerceIn(NotchGeometry.MIN_NOTCH_RADIUS, NotchGeometry.MAX_NOTCH_RADIUS)
                if (abs(newRadius - currentRadiusDp) > 0.1f) {
                    currentRadiusDp = newRadius
                    tvSizeVal.text = "${currentRadiusDp.toInt()} dp"
                    seekBarSize.progress = currentRadiusDp.toInt()
                    refreshPreview()
                }
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                commitToPrefs()
            }
        })

        var touchStartX = 0f
        var touchStartY = 0f
        var initialX = 0f
        var initialY = 0f

        overlayView.setOnTouchListener { v, event ->
            scaleDetector.onTouchEvent(event)

            if (event.pointerCount == 1) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        touchStartX = event.rawX
                        touchStartY = event.rawY
                        initialX = currentX
                        initialY = currentY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - touchStartX
                        val dy = event.rawY - touchStartY
                        if (isLandscape || rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
                            if (isCenter && abs(dy) > 10f) {
                                isCenter = false
                                radioManual.isChecked = true
                            }
                            if (!isCenter) {
                                currentY = initialY + dy
                            }
                            currentX = initialX + dx
                        } else {
                            if (isCenter && abs(dx) > 10f) {
                                isCenter = false
                                radioManual.isChecked = true
                            }
                            if (!isCenter) {
                                currentX = initialX + dx
                            }
                            currentY = initialY + dy
                        }
                        refreshPreview()
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        v.performClick()
                        commitToPrefs()
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        commitToPrefs()
                        true
                    }
                    else -> true
                }
            } else {
                true
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        try {
            wm.addView(overlayView, params)
            activeWindowManagerRef = WeakReference(wm)
            activeOverlayViewRef = WeakReference(overlayView)
        } catch (_: Exception) {}
    }
}
