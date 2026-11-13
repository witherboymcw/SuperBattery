package com.witherboymcw.superbattery.templates

import android.content.Context
import android.os.Build
import android.util.TypedValue
import android.view.Surface
import android.view.View
import android.view.WindowManager

/**
 * Common extension functions for DP conversions and Display Rotation compatibility checks.
 */
fun Context.dp(value: Float): Float {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value,
        resources.displayMetrics
    )
}

fun Context.dp(value: Int): Float = dp(value.toFloat())

fun View.dp(value: Float): Float {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value,
        resources.displayMetrics
    )
}

fun View.dp(value: Int): Float = dp(value.toFloat())

fun Context.getDisplayRotationCompat(): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        display?.rotation ?: Surface.ROTATION_0
    } else {
        @Suppress("DEPRECATION")
        (getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay?.rotation ?: Surface.ROTATION_0
    }
}

fun View.getDisplayRotationCompat(): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        display?.rotation ?: context.getDisplayRotationCompat()
    } else {
        context.getDisplayRotationCompat()
    }
}
