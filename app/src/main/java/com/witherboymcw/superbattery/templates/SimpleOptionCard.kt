package com.witherboymcw.superbattery.templates

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.TextView

data class OptionItem<T>(
    val id: Int,
    val value: T,
    val displayText: String
)

class SimpleOptionCard {

    companion object {
        fun setupExpandableHeader(
            headerView: View?,
            dropdownView: View?,
            arrowView: ImageView?,
            savedInstanceStateKey: String? = null,
            savedInstanceState: Bundle? = null
        ) {
            headerView?.setOnClickListener {
                val isExpanded = dropdownView?.visibility == View.VISIBLE
                dropdownView?.visibility = if (isExpanded) View.GONE else View.VISIBLE
                arrowView?.animate()?.rotation(if (isExpanded) 0f else 180f)?.setDuration(200L)?.start()
            }

            if (savedInstanceStateKey != null && savedInstanceState?.getBoolean(savedInstanceStateKey) == true) {
                dropdownView?.visibility = View.VISIBLE
                arrowView?.rotation = 180f
            }
        }

        fun <T> bind(
            headerView: View?,
            dropdownView: View?,
            arrowView: ImageView?,
            selectedTextView: TextView?,
            radioGroup: RadioGroup?,
            savedInstanceStateKey: String? = null,
            savedInstanceState: Bundle? = null,
            currentValue: T,
            options: List<OptionItem<T>>,
            onSelectionChanged: (T) -> Unit
        ) {
            setupExpandableHeader(headerView, dropdownView, arrowView, savedInstanceStateKey, savedInstanceState)

            val initialOption = options.firstOrNull { it.value == currentValue } ?: options.firstOrNull()
            if (initialOption != null && radioGroup != null) {
                radioGroup.check(initialOption.id)
                selectedTextView?.text = initialOption.displayText
            }

            radioGroup?.setOnCheckedChangeListener { _, checkedId ->
                val selectedOption = options.firstOrNull { it.id == checkedId } ?: return@setOnCheckedChangeListener
                selectedTextView?.text = selectedOption.displayText
                if (selectedOption.value != currentValue) {
                    onSelectionChanged(selectedOption.value)
                }
            }
        }
    }
}
