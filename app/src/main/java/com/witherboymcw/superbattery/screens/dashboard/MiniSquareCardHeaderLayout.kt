package com.witherboymcw.superbattery.screens.dashboard

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import kotlin.math.abs

/**
 * A custom header layout for summary cards in mini square mode.
 * Dynamically adjusts font size of line 3 (and hides it if font size falls below minimum threshold)
 * based on available card height, and evenly spaces visible text lines vertically across the card height.
 */
class MiniSquareCardHeaderLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ViewGroup(context, attrs, defStyleAttr) {

    internal data class LayoutState(
        val line3Fits: Boolean,
        val selectedSp: Float,
    )

    internal companion object {
        fun calculateLayoutState(
            availHeight: Int,
            h1: Int,
            h2: Int,
            measureH3ForSp: (Float) -> Int,
            defaultLine3Sp: Float = 14f,
            minLine3Sp: Float = 9.5f,
            density: Float = 1f,
        ): LayoutState {
            // Minimum gap required between elements and padding (at least 2dp per gap across 4 gaps)
            val minTotalGaps = (8 * density).toInt()
            var selectedSp = defaultLine3Sp
            var fits = false

            var testSp = defaultLine3Sp
            while (testSp >= (minLine3Sp - 0.01f)) {
                val h3 = measureH3ForSp(testSp)
                if ((h1 + h2 + h3 + minTotalGaps) <= availHeight) {
                    fits = true
                    selectedSp = testSp
                    break
                }
                testSp -= 1.0f
            }

            return LayoutState(
                line3Fits = fits,
                selectedSp = selectedSp,
            )
        }
    }

    var defaultLine3Sp: Float = 14f
    var minLine3Sp: Float = 9.5f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)

        val availWidth = (width - paddingLeft - paddingRight).coerceAtLeast(0)
        val availHeight = (height - paddingTop - paddingBottom).coerceAtLeast(0)

        if (childCount < 2) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        val child0 = getChildAt(0)
        val child1 = getChildAt(1)
        val child2 = if (childCount >= 3) getChildAt(2) else null

        val widthSpec = MeasureSpec.makeMeasureSpec(availWidth, MeasureSpec.EXACTLY)
        val unspecifiedSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)

        child0.measure(widthSpec, unspecifiedSpec)
        child1.measure(widthSpec, unspecifiedSpec)

        val h1 = child0.measuredHeight
        val h2 = child1.measuredHeight

        if (child2 is TextView) {
            val (line3Fits, selectedSp) = calculateLayoutState(
                availHeight = availHeight,
                h1 = h1,
                h2 = h2,
                measureH3ForSp = { testSp ->
                    updateTextViewSp(child2, testSp)
                    if (child2.visibility != VISIBLE) {
                        child2.visibility = VISIBLE
                    }
                    child2.measure(widthSpec, unspecifiedSpec)
                    child2.measuredHeight
                },
                defaultLine3Sp = defaultLine3Sp,
                minLine3Sp = minLine3Sp,
                density = resources.displayMetrics.density,
            )

            if (line3Fits) {
                updateTextViewSp(child2, selectedSp)
                if (child2.visibility != VISIBLE) {
                    child2.visibility = VISIBLE
                }
                child2.measure(widthSpec, unspecifiedSpec)
            } else {
                if (child2.visibility != GONE) {
                    child2.visibility = GONE
                }
            }
        }

        setMeasuredDimension(width, height)
    }

    @Suppress("DEPRECATION")
    private fun updateTextViewSp(textView: TextView, sp: Float) {
        val currentSp = textView.textSize / resources.displayMetrics.scaledDensity
        if (abs(currentSp - sp) > 0.1f) {
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val visibleChildren = mutableListOf<View>()
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility != GONE) {
                visibleChildren.add(child)
            }
        }

        if (visibleChildren.isEmpty()) return

        val totalChildHeight = visibleChildren.sumOf { it.measuredHeight }
        val availHeight = (b - t) - paddingTop - paddingBottom
        val remainingHeight = (availHeight - totalChildHeight).coerceAtLeast(0)

        // Divide remaining height into equal gaps (above first child, between children, below last child)
        val gapCount = visibleChildren.size + 1
        val gap = remainingHeight.toFloat() / gapCount.toFloat()

        var currentY = paddingTop.toFloat() + gap
        for (child in visibleChildren) {
            val childW = child.measuredWidth
            val childH = child.measuredHeight
            val childLeft = paddingLeft + ((r - l - paddingLeft - paddingRight - childW) / 2)
            child.layout(childLeft, currentY.toInt(), childLeft + childW, (currentY + childH).toInt())
            currentY += childH + gap
        }
    }
}
