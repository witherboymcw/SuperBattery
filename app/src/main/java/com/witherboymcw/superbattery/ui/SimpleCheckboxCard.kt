package com.witherboymcw.superbattery.ui

import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView

data class CheckboxItemSpec(
    val checkBox: CheckBox?,
    val initialChecked: Boolean,
    val onCheckedChange: (Boolean) -> Unit
)

class SimpleCheckboxCard {

    companion object {
        fun bind(
            headerView: View?,
            dropdownView: View?,
            arrowView: ImageView?,
            summaryTextView: TextView?,
            savedInstanceStateKey: String? = null,
            savedInstanceState: Bundle? = null,
            items: List<CheckboxItemSpec>,
            onHeaderClick: (() -> Unit)? = null,
            summaryFormatter: ((checkedCount: Int, totalCount: Int) -> String)? = { checked, total -> "$checked/$total" }
        ) {
            SimpleOptionCard.setupExpandableHeader(
                headerView,
                dropdownView,
                arrowView,
                savedInstanceStateKey,
                savedInstanceState,
                onHeaderClick
            )

            fun updateSummary() {
                if (summaryTextView != null && summaryFormatter != null) {
                    val checkedCount = items.count { it.checkBox?.isChecked == true }
                    val totalCount = items.size
                    summaryTextView.text = summaryFormatter(checkedCount, totalCount)
                }
            }

            items.forEach { itemSpec ->
                val cb = itemSpec.checkBox ?: return@forEach
                cb.setOnCheckedChangeListener(null)
                cb.isChecked = itemSpec.initialChecked

                cb.setOnCheckedChangeListener { _, isChecked ->
                    itemSpec.onCheckedChange(isChecked)
                    updateSummary()
                }
            }

            updateSummary()
        }

        fun updateSummary(
            summaryTextView: TextView?,
            items: List<CheckBox?>,
            summaryFormatter: (checkedCount: Int, totalCount: Int) -> String = { checked, total -> "$checked/$total" }
        ) {
            if (summaryTextView != null) {
                val checkedCount = items.count { it?.isChecked == true }
                val totalCount = items.size
                summaryTextView.text = summaryFormatter(checkedCount, totalCount)
            }
        }
    }
}
