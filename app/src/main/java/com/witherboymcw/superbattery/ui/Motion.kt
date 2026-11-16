package com.witherboymcw.superbattery.ui

import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import android.view.animation.Interpolator

/**
 * Shared animation constants used across all drawer and slide animations.
 *
 * - [interpolator]: single shared [FastOutSlowInInterpolator] instance (was instantiated 14 times)
 * - [FALLBACK_WIDTH]: fallback screen width in pixels when the view has not been laid out yet
 *   (was hardcoded as `1080f` in 9 separate places)
 */
object Motion {
    /** Shared FastOutSlowIn interpolator — do NOT create a new instance per animation call. */
    val interpolator: Interpolator = FastOutSlowInInterpolator()

    /**
     * Fallback container width (px) used when `view.width == 0` before first layout.
     * Represents a typical portrait phone screen width; real width is always preferred.
     */
    const val FALLBACK_WIDTH = 1080f
}
