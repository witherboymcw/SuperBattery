package com.witherboymcw.superbattery.awareness

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.RectF
import android.util.AttributeSet
import android.view.Surface
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ScrollView
import com.witherboymcw.superbattery.R
import com.witherboymcw.superbattery.SwipeBackFrameLayout
import com.witherboymcw.superbattery.templates.dp
import com.witherboymcw.superbattery.templates.getDisplayRotationCompat
import com.witherboymcw.superbattery.widgets.BatteryWidget
import kotlin.math.abs

/**
 * Custom container layout that coordinates with [NotchAwareness] to shift scrollable content
 * around notch obstructions in landscape orientation, and draw radial cutout masks.
 */
class NotchAwareFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SwipeBackFrameLayout(context, attrs, defStyleAttr) {

    private val prefs = context.getSharedPreferences(BatteryWidget.SETTINGS_PREFS, Context.MODE_PRIVATE)

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == NotchGeometry.KEY_NOTCH_CUSTOM_MODE ||
            key == NotchGeometry.KEY_NOTCH_IS_CENTER ||
            key == NotchGeometry.KEY_NOTCH_X ||
            key == NotchGeometry.KEY_NOTCH_Y ||
            key == NotchGeometry.KEY_NOTCH_RADIUS ||
            key == NotchGeometry.KEY_NOTCH_PILL_WIDTH) {
            invalidate()
            requestLayout()
        }
    }

    private val scrollChangeListener = ViewTreeObserver.OnScrollChangedListener {
        updateChildOffsets()
    }

    private val tempNotchRect = RectF()
    private val tempRootLoc = IntArray(2)
    private val tempCardLoc = IntArray(2)

    init {
        clipChildren = false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        clipChildren = false
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

    private fun updateChildOffsets() {
        val rotation = getDisplayRotationCompat()

        if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) {
            resetAllChildTranslations(this)
            return
        }

        val rect = NotchGeometry.getNotchRect(context, this, tempNotchRect)
        if (rect == null || height <= 0 || width <= 0) {
            resetAllChildTranslations(this)
            return
        }

        val isRight = rotation == Surface.ROTATION_270 || rect.centerX() > width / 2f
        if (isRight) {
            resetAllChildTranslations(this)
            return
        }

        val clearanceDp = dp(12f)
        val maxEdgeDistPx = dp(48f)
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

        val reductionPx = dp(56f).toInt()
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
            if ((child.id == R.id.settings_scroll_left || child.id == R.id.hotspot_scroll_left) && child is ScrollView) {
                child.clipChildren = false
                child.elevation = dp(10f)
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
                    if (card.id == R.id.tv_settings_title || card.id == R.id.tv_hotspot_title) continue
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
        NotchAwareness.drawRadialMask(canvas, width, height, this)
    }
}
