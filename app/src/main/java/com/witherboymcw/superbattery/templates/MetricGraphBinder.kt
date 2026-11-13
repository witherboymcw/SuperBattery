package com.witherboymcw.superbattery.templates

import android.view.View
import android.view.ViewGroup
import com.witherboymcw.superbattery.HistoryGraphView

/**
 * Shared binder responsible for setting up HistoryGraphView axis offset callbacks and updating graph history samples.
 */
object MetricGraphBinder {

    fun bindHeaderOffsets(
        graph: HistoryGraphView?,
        headerView: View?,
        trailingView: View?
    ) {
        graph?.onAxisOffsetsChanged = { leftOffset, rightOffset, plotWidth ->
            headerView?.setPaddingRelative(
                leftOffset.toInt(),
                headerView.paddingTop,
                rightOffset.toInt(),
                headerView.paddingBottom
            )
            val margin20s = (plotWidth / 3f).toInt()
            (trailingView?.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                if (lp.marginEnd != margin20s) {
                    lp.marginEnd = margin20s
                    trailingView.layoutParams = lp
                }
            }
        }
    }

    fun updateGraph(
        graph: HistoryGraphView?,
        samples: Collection<HistoryGraphView.Sample>,
        minValue: Float,
        maxValue: Float,
        suffix: String = "",
        currentTimeMs: Long,
        decimalPlaces: Int = 0,
        valueTransform: ((Float) -> Float)? = null,
        valueColor: ((Float) -> Int)? = null
    ) {
        graph?.setHistory(
            samples = samples,
            minValue = minValue,
            maxValue = maxValue,
            suffix = suffix,
            currentTimeMs = currentTimeMs,
            decimalPlaces = decimalPlaces,
            valueTransform = valueTransform,
            valueColor = valueColor
        )
    }
}
