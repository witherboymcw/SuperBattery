package com.witherboymcw.superbattery

import android.content.Context
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

    private val scrollChangeListener = ViewTreeObserver.OnScrollChangedListener {
        updateChildOffsets()
    }

    init {
        clipChildren = false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        clipChildren = false
        viewTreeObserver.addOnScrollChangedListener(scrollChangeListener)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewTreeObserver.removeOnScrollChangedListener(scrollChangeListener)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        updateChildOffsets()
    }

    private fun getNotchRect(): RectF? {
        val notchMode = prefs.getInt("notch_custom_mode", 0)
        if (notchMode == 2) return null

        var notchRect: RectF? = null
        if (notchMode == 1) {
            val isCentered = prefs.getBoolean("notch_is_center", true)
            val nX = prefs.getFloat("notch_x", -1f)
            val nY = prefs.getFloat("notch_y", -1f)
            if (nX != -1f && nY != -1f) {
                val notchRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24f, resources.displayMetrics)
                val finalY = if (isCentered) height / 2f else nY
                notchRect = RectF(nX - notchRadius, finalY - notchRadius, nX + notchRadius, finalY + notchRadius)
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                rootWindowInsets?.displayCutout?.boundingRects?.firstOrNull()?.let { rect ->
                    notchRect = RectF(rect)
                }
            }
        }
        return notchRect
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

        val rootLoc = IntArray(2)
        getLocationOnScreen(rootLoc)

        val reductionPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 56f, resources.displayMetrics).toInt()
        applyDetourToScrollChildren(this, peakX, peakY, totalHeight, rootLoc[1], reductionPx)
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

                    val cardLoc = IntArray(2)
                    card.getLocationOnScreen(cardLoc)
                    val cardCenterY = (cardLoc[1] - rootScreenY) + card.height / 2f

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
