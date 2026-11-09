package com.witherboymcw.superbattery

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.RectF
import android.os.Build
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Surface
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ScrollView
import kotlin.math.abs

class NotchAwareFrameLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : SwipeBackFrameLayout(context, attrs, defStyleAttr) {

    private val radialMaskView = RadialMaskView(context)
    private val prefs = context.getSharedPreferences(SettingsViewController.SETTINGS_PREFS, Context.MODE_PRIVATE)

    private var cachedNotchCustomMode = 0
    private var cachedNotchIsCenter = true
    private var cachedNotchX = -1f
    private var cachedNotchY = -1f

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "notch_custom_mode" || key == "notch_is_center" || key == "notch_x" || key == "notch_y") {
            reloadNotchPrefs()
            invalidate()
        }
    }

    private fun reloadNotchPrefs() {
        cachedNotchCustomMode = prefs.getInt("notch_custom_mode", 0)
        cachedNotchIsCenter = prefs.getBoolean("notch_is_center", true)
        cachedNotchX = prefs.getFloat("notch_x", -1f)
        cachedNotchY = prefs.getFloat("notch_y", -1f)
    }

    private val scrollChangeListener = ViewTreeObserver.OnScrollChangedListener {
        updateChildOffsets()
    }

    private val tempNotchRect = RectF()
    private val tempRootLoc = IntArray(2)
    private val tempCardLoc = IntArray(2)

    init {
        clipChildren = false
        reloadNotchPrefs()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        clipChildren = false
        reloadNotchPrefs()
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        viewTreeObserver.addOnScrollChangedListener(scrollChangeListener)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        if (viewTreeObserver.isAlive) {
            viewTreeObserver.removeOnScrollChangedListener(scrollChangeListener)
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        updateChildOffsets()
    }

    private fun getNotchRect(): RectF? {
        if (cachedNotchCustomMode == 2) return null

        if (cachedNotchCustomMode == 1) {
            if (cachedNotchX != -1f && cachedNotchY != -1f) {
                val notchRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24f, resources.displayMetrics)
                val finalY = if (cachedNotchIsCenter) height / 2f else cachedNotchY
                tempNotchRect.set(cachedNotchX - notchRadius, finalY - notchRadius, cachedNotchX + notchRadius, finalY + notchRadius)
                return tempNotchRect
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                rootWindowInsets?.displayCutout?.boundingRects?.firstOrNull()?.let { rect ->
                    tempNotchRect.set(rect)
                    return tempNotchRect
                }
            }
        }
        return null
    }

    private fun updateChildOffsets() {
        val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay.rotation
        }

        if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) {
            resetAllChildTranslations(this)
            return
        }

        val rect = getNotchRect()
        if (rect == null || height <= 0 || width <= 0) {
            resetAllChildTranslations(this)
            return
        }

        val isRight = rotation == Surface.ROTATION_270 || rect.centerX() > width / 2f
        if (isRight) {
            resetAllChildTranslations(this)
            return
        }

        // Left Notch: Calculate dynamic detour for left column ScrollView (@+id/settings_scroll_left)
        val clearanceDp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12f, resources.displayMetrics)
        val maxEdgeDistPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48f, resources.displayMetrics)
        val distLeft = rect.left
        if (distLeft > maxEdgeDistPx) {
            resetAllChildTranslations(this)
            return
        }

        val expandedRight = rect.right + clearanceDp
        val peakX = expandedRight
        val peakY = rect.centerY()
        val totalHeight = height.toFloat()

        getLocationOnScreen(tempRootLoc)

        val reductionPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 56f, resources.displayMetrics).toInt()
        applyDetourToScrollChildren(this, peakX, peakY, totalHeight, tempRootLoc[1], reductionPx)
    }

    private fun applyDetourToScrollChildren(
        viewGroup: ViewGroup,
        peakX: Float,
        peakY: Float,
        totalHeight: Float,
        rootScreenY: Int,
        reductionPx: Int
    ) {
        viewGroup.clipChildren = false
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            if (child.id == R.id.settings_scroll_left && child is ScrollView) {
                child.clipChildren = false
                child.elevation = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10f, resources.displayMetrics)
                val scrollContent = child.getChildAt(0) as? ViewGroup ?: continue
                scrollContent.clipChildren = false
                for (j in 0 until scrollContent.childCount) {
                    val card = scrollContent.getChildAt(j)
                    val lp = card.layoutParams as? ViewGroup.MarginLayoutParams
                    if (lp != null && lp.rightMargin != reductionPx) {
                        lp.rightMargin = reductionPx
                        card.layoutParams = lp
                    }

                    card.getLocationOnScreen(tempCardLoc)
                    val cardCenterY = (tempCardLoc[1] - rootScreenY) + card.height / 2f

                    val denominator = if (cardCenterY < peakY) peakY else (totalHeight - peakY)
                    val t = if (denominator > 0f) {
                        (1f - abs(cardCenterY - peakY) / denominator).coerceIn(0f, 1f)
                    } else 0f

                    val arcDepth = if (t > 0f) peakX * (1f - (1f - t) * (1f - t)) else 0f
                    card.translationX = arcDepth
                }
            } else if (child is ViewGroup) {
                child.clipChildren = false
                applyDetourToScrollChildren(child, peakX, peakY, totalHeight, rootScreenY, reductionPx)
            }
        }
    }

    private fun resetAllChildTranslations(viewGroup: ViewGroup) {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            if (child is ScrollView) {
                val scrollContent = child.getChildAt(0) as? ViewGroup ?: continue
                for (j in 0 until scrollContent.childCount) {
                    val card = scrollContent.getChildAt(j)
                    if (card.id == R.id.tv_settings_title) continue
                    card.translationX = 0f
                    val lp = card.layoutParams as? ViewGroup.MarginLayoutParams
                    if (lp != null && lp.rightMargin != 0) {
                        lp.rightMargin = 0
                        card.layoutParams = lp
                    }
                }
            } else if (child is ViewGroup) {
                resetAllChildTranslations(child)
            }
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        updateChildOffsets()
        super.dispatchDraw(canvas)
        radialMaskView.drawMask(canvas, width, height, this)
    }
}
