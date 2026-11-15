package com.witherboymcw.superbattery

import android.view.View
import android.view.ViewGroup
import com.google.android.material.card.MaterialCardView

object ViewTreeUtils {
    fun walkMaterialCardViews(root: View, action: (MaterialCardView) -> Unit) {
        if (root is MaterialCardView) {
            action(root)
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                walkMaterialCardViews(root.getChildAt(i), action)
            }
        }
    }
}
