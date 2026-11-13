package com.witherboymcw.superbattery.templates

import android.view.View
import com.google.android.material.checkbox.MaterialCheckBox

class SimpleCheckboxCard {

    companion object {
        fun bind(
            cardView: View?,
            checkboxView: MaterialCheckBox?,
            initialChecked: Boolean,
            onCheckedChange: (Boolean) -> Unit
        ) {
            checkboxView?.isChecked = initialChecked

            cardView?.setOnClickListener {
                val nextValue = !(checkboxView?.isChecked ?: false)
                checkboxView?.isChecked = nextValue
                onCheckedChange(nextValue)
            }

            checkboxView?.setOnCheckedChangeListener { _, isChecked ->
                onCheckedChange(isChecked)
            }
        }
    }
}
