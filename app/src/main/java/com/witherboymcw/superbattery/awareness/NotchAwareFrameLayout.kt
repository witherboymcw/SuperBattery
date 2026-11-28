package com.witherboymcw.superbattery.awareness

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.RectF
import android.util.AttributeSet
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ScrollView
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import com.witherboymcw.superbattery.R
import com.witherboymcw.superbattery.SwipeBackFrameLayout
import com.witherboymcw.superbattery.core.dp
import com.witherboymcw.superbattery.core.getDisplayRotationCompat
import java.util.WeakHashMap
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

    private val prefs = com.witherboymcw.superbattery.settings.AppPreferences.getPreferences(context)

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

    private val attachedRecyclerViews = WeakHashMap<RecyclerView, RecyclerView.OnScrollListener>()

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
        attachedRecyclerViews.clear()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        updateChildOffsets()
    }

    fun updateChildOffsets() {
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

    private val rvLayoutChangeListener = OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        updateChildOffsets()
    }

    private fun ensureRecyclerViewScrollListener(rv: RecyclerView) {
        if (!attachedRecyclerViews.containsKey(rv)) {
            val listener = object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    updateChildOffsets()
                }
            }
            rv.addOnScrollListener(listener)
            rv.addOnLayoutChangeListener(rvLayoutChangeListener)
            attachedRecyclerViews[rv] = listener
        }
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
            if (child.id == R.id.settings_scroll_left && (child is ScrollView || child is NestedScrollView)) {
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

                    val influenceRadius = totalHeight * 0.75f
                    val minOffset = dp(16f)
                    val extraOffset = (peakX - minOffset).coerceAtLeast(0f)
                    val distY = abs(cardCenterY - peakY)
                    val t = (1f - distY / influenceRadius).coerceIn(0f, 1f)
                    val smoothT = t * t * (3f - 2f * t)
                    card.translationX = minOffset + extraOffset * smoothT
                }
            } else if (child.id == R.id.hotspot_recycler_view && child is RecyclerView) {
                child.clipChildren = false
                child.elevation = dp(10f)
                ensureRecyclerViewScrollListener(child)
                for (j in 0 until child.childCount) {
                    val card = child.getChildAt(j)
                    if (card.id == R.id.tv_hotspot_title || card.findViewById<View>(R.id.tv_hotspot_title) != null) continue
                    val lp = card.layoutParams as? ViewGroup.MarginLayoutParams
                    if (lp != null && lp.rightMargin != reductionPx) {
                        lp.rightMargin = reductionPx
                        card.layoutParams = lp
                    }

                    card.getLocationOnScreen(tempCardLoc)
                    val cardCenterY = (tempCardLoc[1] - rootScreenY) + card.height / 2f

                    val influenceRadius = totalHeight * 0.75f
                    val minOffset = dp(16f)
                    val extraOffset = (peakX - minOffset).coerceAtLeast(0f)
                    val distY = abs(cardCenterY - peakY)
                    val t = (1f - distY / influenceRadius).coerceIn(0f, 1f)
                    val smoothT = t * t * (3f - 2f * t)
                    card.translationX = minOffset + extraOffset * smoothT
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
            if (child is ScrollView || child is NestedScrollView) {
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
            } else if (child is RecyclerView) {
                for (j in 0 until child.childCount) {
                    val card = child.getChildAt(j)
                    if (card.id == R.id.tv_settings_title || card.id == R.id.tv_hotspot_title || card.findViewById<View>(R.id.tv_hotspot_title) != null) continue
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
        NotchAwareness.drawNotchOverlay(canvas, this)
    }
}
