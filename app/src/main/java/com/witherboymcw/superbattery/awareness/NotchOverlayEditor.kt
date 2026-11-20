package com.witherboymcw.superbattery.awareness

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import com.witherboymcw.superbattery.MainActivity
import com.witherboymcw.superbattery.R
import com.witherboymcw.superbattery.settings.AppPreferences
import com.witherboymcw.superbattery.settings.Permissions
import com.witherboymcw.superbattery.core.dp
import com.witherboymcw.superbattery.core.getDisplayRotationCompat
import java.lang.ref.WeakReference
import kotlin.math.abs

/**
 * Overlay window editor for interactively dragging, sizing, and positioning simulated notch cutouts.
 */
object NotchOverlayEditor {

    private const val TAG = "NotchOverlayEditor"

    var liveCutoutOverride: NotchGeometry.CutoutInfo? = null
        private set

    private var activeSession: EditorSession? = null

    fun dismissNotchPositionOverlay() {
        liveCutoutOverride = null
        activeSession?.dismiss()
        activeSession = null
    }

    fun showNotchPositionOverlay(activity: MainActivity, onRefresh: () -> Unit) {
        dismissNotchPositionOverlay()

        if (!Permissions.isOverlayEnabled(activity)) {
            try {
                activity.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${activity.packageName}"))
                )
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Failed to open overlay permission settings", e)
            }
            return
        }

        activeSession = EditorSession(activity, onRefresh).apply { start() }
    }

    private class EditorSession(activity: MainActivity, private val onRefresh: () -> Unit) {
        private val activityRef = WeakReference(activity)
        val context = activity.applicationContext
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val screenWidth: Float
        val screenHeight: Float
        val isLandscape = context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val rotation = context.getDisplayRotationCompat()

        var isCenter = AppPreferences.isNotchCenter(context)
        var currentRadiusDp = AppPreferences.getNotchRadius(context, NotchGeometry.DEFAULT_NOTCH_RADIUS)
        var currentPillDp = AppPreferences.getNotchPillWidth(context, NotchGeometry.DEFAULT_NOTCH_PILL_WIDTH)
        var currentX = AppPreferences.getNotchX(context)
        var currentY = AppPreferences.getNotchY(context)

        lateinit var overlayView: View
        lateinit var cutoutView: View
        lateinit var cutoutDrawable: GradientDrawable
        lateinit var tvSizeVal: TextView
        lateinit var tvPillVal: TextView
        lateinit var radioManual: RadioButton
        lateinit var seekBarSize: SeekBar

        private var isAttached = false

        init {
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
        }

        fun start() {
            val activity = activityRef.get() ?: return
            val radiusPxInitial = context.dp(currentRadiusDp)
            if (isLandscape || rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
                if (currentY < 0f || isCenter) currentY = screenHeight / 2f
                val isRight = rotation == Surface.ROTATION_270
                if (currentX < 0f) currentX = if (isRight) screenWidth - radiusPxInitial else radiusPxInitial
            } else {
                if (currentX < 0f || isCenter) currentX = screenWidth / 2f
                if (currentY < 0f) currentY = radiusPxInitial
            }

            overlayView = LayoutInflater.from(activity).inflate(R.layout.overlay_notch_editor, null)
            cutoutView = overlayView.findViewById(R.id.cutout_view)
            cutoutDrawable = GradientDrawable().apply {
                setColor(Color.BLACK)
                setStroke(context.dp(2f).toInt(), Color.WHITE)
            }
            cutoutView.background = cutoutDrawable

            tvSizeVal = overlayView.findViewById(R.id.tv_size_val)
            tvPillVal = overlayView.findViewById(R.id.tv_pill_val)
            radioManual = overlayView.findViewById(R.id.radio_manual)
            seekBarSize = overlayView.findViewById(R.id.seek_bar_size)

            setupControls()
            updateCutoutLayout()
            attachGestures()

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
                if (!isAttached) {
                    wm.addView(overlayView, params)
                    isAttached = true
                }
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Failed to attach notch editor overlay", e)
                isAttached = false
            }
        }

        fun dismiss() {
            if (isAttached && ::overlayView.isInitialized) {
                try {
                    wm.removeViewImmediate(overlayView)
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "Failed to remove notch editor overlay", e)
                } finally {
                    isAttached = false
                }
            }
        }

        private fun updateCutoutLayout() {
            val radiusPx = context.dp(currentRadiusDp)
            val pillPx = context.dp(currentPillDp)

            val halfW: Float
            val halfH: Float

            if (isLandscape || rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
                halfW = radiusPx
                halfH = radiusPx + pillPx / 2f
                if (isCenter) currentY = screenHeight / 2f
            } else {
                halfW = radiusPx + pillPx / 2f
                halfH = radiusPx
                if (isCenter) currentX = screenWidth / 2f
            }

            cutoutDrawable.cornerRadius = minOf(halfW, halfH)

            val lp = FrameLayout.LayoutParams((halfW * 2f).toInt(), (halfH * 2f).toInt())
            lp.leftMargin = (currentX - halfW).toInt().coerceIn(0, (screenWidth - halfW * 2f).toInt().coerceAtLeast(0))
            lp.topMargin = (currentY - halfH).toInt().coerceIn(0, (screenHeight - halfH * 2f).toInt().coerceAtLeast(0))
            cutoutView.layoutParams = lp
        }

        private fun refreshPreview() {
            val radiusPx = context.dp(currentRadiusDp)
            val pillWidthPx = context.dp(currentPillDp)
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

        private fun commitToPrefs() {
            liveCutoutOverride = null
            AppPreferences.setNotchCenter(context, isCenter)
            AppPreferences.setNotchX(context, currentX)
            AppPreferences.setNotchY(context, currentY)
            AppPreferences.setNotchRadius(context, currentRadiusDp)
            AppPreferences.setNotchPillWidth(context, currentPillDp)
            updateCutoutLayout()
            onRefresh()
        }

        private fun setupControls() {
            val radioCenter: RadioButton = overlayView.findViewById(R.id.radio_center)
            val radioGroupPos: RadioGroup = overlayView.findViewById(R.id.radio_group_pos)
            val seekBarPill: SeekBar = overlayView.findViewById(R.id.seek_bar_pill)
            val btnSave: Button = overlayView.findViewById(R.id.btn_save)

            radioCenter.isChecked = isCenter
            radioManual.isChecked = !isCenter
            tvSizeVal.text = "${currentRadiusDp.toInt()} dp"
            seekBarSize.max = NotchGeometry.MAX_NOTCH_RADIUS.toInt()
            seekBarSize.progress = currentRadiusDp.toInt()

            tvPillVal.text = "${currentPillDp.toInt()} dp"
            seekBarPill.max = NotchGeometry.MAX_NOTCH_PILL_WIDTH.toInt()
            seekBarPill.progress = currentPillDp.toInt()

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
                override fun onStopTrackingTouch(seekBar: SeekBar?) { commitToPrefs() }
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
                override fun onStopTrackingTouch(seekBar: SeekBar?) { commitToPrefs() }
            })

            btnSave.setOnClickListener {
                commitToPrefs()
                dismissNotchPositionOverlay()
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        private fun attachGestures() {
            val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
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
                override fun onScaleEnd(detector: ScaleGestureDetector) { commitToPrefs() }
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
                                if (!isCenter) currentY = initialY + dy
                                currentX = initialX + dx
                            } else {
                                if (isCenter && abs(dx) > 10f) {
                                    isCenter = false
                                    radioManual.isChecked = true
                                }
                                if (!isCenter) currentX = initialX + dx
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
                } else true
            }
        }
    }
}
