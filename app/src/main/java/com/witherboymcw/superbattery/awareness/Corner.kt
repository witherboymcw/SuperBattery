package com.witherboymcw.superbattery.awareness

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.view.RoundedCorner
import android.view.View
import androidx.core.view.WindowInsetsCompat
import androidx.window.layout.WindowMetricsCalculator
import com.google.android.material.card.MaterialCardView
import com.witherboymcw.superbattery.R

/**
 * Utility helper for calculating Android 12+ dynamic screen rounded corners
 * and adjusting asymmetric shapeAppearanceModel corner sizes for dashboard cards.
 */
object CornerAwareness {

    private const val TAG = "CornerAwareness"

    const val DEFAULT_CORNER_RADIUS_DP = 12f
    const val CORNER_OFFSET_DP = 16f

    private fun Context?.findActivity(): Activity? {
        var context = this
        while (context is ContextWrapper) {
            if (context is Activity) {
                return context
            }
            context = context.baseContext
        }
        return null
    }

    /**
     * Pure geometry logic: checks if current window coordinates touch physical display corners for Mini Square Mode.
     * Returns Pair(touchesBottomLeft, touchesBottomRight).
     */
    @Suppress("UNUSED_PARAMETER")
    fun checkWindowScreenCornerTouches(
        currentLeft: Int,
        currentTop: Int,
        currentRight: Int,
        currentBottom: Int,
        maxLeft: Int,
        maxTop: Int,
        maxRight: Int,
        maxBottom: Int,
    ): Pair<Boolean, Boolean> {
        val touchesBottom = currentBottom >= (maxBottom - 2)
        val touchesLeft = currentLeft <= (maxLeft + 2)
        val touchesRight = currentRight >= (maxRight - 2)

        val touchesBottomLeft = touchesBottom && touchesLeft
        val touchesBottomRight = touchesBottom && touchesRight

        return Pair(touchesBottomLeft, touchesBottomRight)
    }

    /**
     * Checks if current window bounds touch physical display corners for Mini Square Mode.
     * Returns Pair(touchesBottomLeft, touchesBottomRight).
     */
    fun checkWindowScreenCornerTouches(currentBounds: Rect, maxBounds: Rect): Pair<Boolean, Boolean> {
        return checkWindowScreenCornerTouches(
            currentLeft = currentBounds.left,
            currentTop = currentBounds.top,
            currentRight = currentBounds.right,
            currentBottom = currentBounds.bottom,
            maxLeft = maxBounds.left,
            maxTop = maxBounds.top,
            maxRight = maxBounds.right,
            maxBottom = maxBounds.bottom,
        )
    }

    /**
     * Checks if Activity's current window touches physical display corners for Mini Square Mode.
     */
    fun checkWindowScreenCornerTouches(activity: Activity): Pair<Boolean, Boolean> {
        return try {
            val calculator = WindowMetricsCalculator.getOrCreate()
            val currentBounds = calculator.computeCurrentWindowMetrics(activity).bounds
            val maxBounds = calculator.computeMaximumWindowMetrics(activity).bounds
            checkWindowScreenCornerTouches(currentBounds, maxBounds)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to calculate screen corner touches", e)
            Pair(true, true)
        }
    }

    /**
     * Calculates dynamic rounded corner radius in pixels from WindowInsetsCompat on Android 12+ (API 31+).
     * Returns default radius (12dp * density) if dynamic corner insets are unavailable or smaller than default.
     */
    fun calculateDynamicCornerRadius(insets: WindowInsetsCompat?, density: Float): Float {
        val defaultRadius = DEFAULT_CORNER_RADIUS_DP * density
        var dynamicRadiusPx = defaultRadius

        if (insets != null) {
            val windowInsets = try { insets.toWindowInsets() } catch (_: Throwable) { null }
            if (windowInsets != null) {
                val corner = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S || Build.VERSION.SDK_INT == 0) {
                        windowInsets.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)
                    } else null
                } catch (_: Throwable) { null }
                if (corner != null) {
                    val screenRadiusPx = corner.radius.toFloat()
                    val calculatedRadius = screenRadiusPx - (CORNER_OFFSET_DP * density)
                    if (calculatedRadius >= defaultRadius) {
                        dynamicRadiusPx = calculatedRadius
                    }
                }
            }
        }
        return dynamicRadiusPx
    }

    /**
     * Applies asymmetric corner radius formatting to dashboard cards contained inside a root View.
     */
    fun applyAsymmetricCorners(rootView: View, insets: WindowInsetsCompat) {
        val cardBattery = rootView.findViewById<MaterialCardView>(R.id.card_battery)
        val cardNetwork = rootView.findViewById<MaterialCardView>(R.id.card_network)
        val cardVoltage = rootView.findViewById<MaterialCardView>(R.id.card_voltage_graph) ?: rootView.findViewById<MaterialCardView>(R.id.card_voltage_summary)
        val cardRam = rootView.findViewById<MaterialCardView>(R.id.card_ram_graph) ?: rootView.findViewById<MaterialCardView>(R.id.card_ram_summary)
        val cardTemp = rootView.findViewById<MaterialCardView>(R.id.card_temperature_graph) ?: rootView.findViewById<MaterialCardView>(R.id.card_temp_summary)
        val cardMiniSquareGraph = rootView.findViewById<MaterialCardView>(R.id.card_mini_square_graph)

        val density = rootView.resources.displayMetrics.density
        val isPortrait = rootView.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

        val activity = rootView.context.findActivity()
        val isMiniSquare = activity?.let { FoldableAwareness.isMiniSquareMode(it) } ?: false

        var touchesBottomLeft = true
        var touchesBottomRight = true
        if ((activity != null) && isMiniSquare) {
            val (bLeft, bRight) = checkWindowScreenCornerTouches(activity)
            touchesBottomLeft = bLeft
            touchesBottomRight = bRight
        }

        applyAsymmetricCornersToCards(
            cardBattery = cardBattery,
            cardNetwork = cardNetwork,
            cardVoltage = cardVoltage,
            cardRam = cardRam,
            cardTemp = cardTemp,
            insets = insets,
            density = density,
            isPortrait = isPortrait,
            isMiniSquare = isMiniSquare,
            touchesBottomLeft = touchesBottomLeft,
            touchesBottomRight = touchesBottomRight,
            cardMiniSquareGraph = cardMiniSquareGraph,
        )
    }

    /**
     * Applies asymmetric corner radius formatting to dashboard cards inside an Activity.
     */
    fun applyAsymmetricCorners(activity: Activity, insets: WindowInsetsCompat) {
        val cardBattery = activity.findViewById<MaterialCardView>(R.id.card_battery)
        val cardNetwork = activity.findViewById<MaterialCardView>(R.id.card_network)
        val cardVoltage = activity.findViewById<MaterialCardView>(R.id.card_voltage_graph) ?: activity.findViewById<MaterialCardView>(R.id.card_voltage_summary)
        val cardRam = activity.findViewById<MaterialCardView>(R.id.card_ram_graph) ?: activity.findViewById<MaterialCardView>(R.id.card_ram_summary)
        val cardTemp = activity.findViewById<MaterialCardView>(R.id.card_temperature_graph) ?: activity.findViewById<MaterialCardView>(R.id.card_temp_summary)
        val cardMiniSquareGraph = activity.findViewById<MaterialCardView>(R.id.card_mini_square_graph)

        val density = activity.resources.displayMetrics.density
        val isPortrait = activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        val isMiniSquare = FoldableAwareness.isMiniSquareMode(activity)

        var touchesBottomLeft = true
        var touchesBottomRight = true
        if (isMiniSquare) {
            val (bLeft, bRight) = checkWindowScreenCornerTouches(activity)
            touchesBottomLeft = bLeft
            touchesBottomRight = bRight
        }

        applyAsymmetricCornersToCards(
            cardBattery = cardBattery,
            cardNetwork = cardNetwork,
            cardVoltage = cardVoltage,
            cardRam = cardRam,
            cardTemp = cardTemp,
            insets = insets,
            density = density,
            isPortrait = isPortrait,
            isMiniSquare = isMiniSquare,
            touchesBottomLeft = touchesBottomLeft,
            touchesBottomRight = touchesBottomRight,
            cardMiniSquareGraph = cardMiniSquareGraph,
        )
    }

    /**
     * Core logic for applying asymmetric corners to cards based on orientation, mini-square mode, and dynamic corner radius.
     */
    fun applyAsymmetricCornersToCards(
        cardBattery: MaterialCardView?,
        cardNetwork: MaterialCardView?,
        cardVoltage: MaterialCardView?,
        cardRam: MaterialCardView?,
        cardTemp: MaterialCardView?,
        insets: WindowInsetsCompat?,
        density: Float,
        isPortrait: Boolean,
        isMiniSquare: Boolean = false,
        touchesBottomLeft: Boolean = true,
        touchesBottomRight: Boolean = true,
        cardMiniSquareGraph: MaterialCardView? = null,
    ) {
        val defaultRadius = DEFAULT_CORNER_RADIUS_DP * density
        val dynamicRadiusPx = calculateDynamicCornerRadius(insets, density)

        if (isMiniSquare) {
            val tempBl = if (touchesBottomLeft) dynamicRadiusPx else defaultRadius
            val graphBr = if (touchesBottomRight) dynamicRadiusPx else defaultRadius

            setCorners(cardBattery, defaultRadius, defaultRadius, defaultRadius, defaultRadius)
            setCorners(cardNetwork, defaultRadius, defaultRadius, defaultRadius, defaultRadius)
            setCorners(cardVoltage, defaultRadius, defaultRadius, defaultRadius, defaultRadius)
            setCorners(cardRam, defaultRadius, defaultRadius, defaultRadius, defaultRadius)
            setCorners(cardTemp, defaultRadius, defaultRadius, defaultRadius, tempBl)

            val targetGraphCard = cardMiniSquareGraph ?: cardVoltage
            setCorners(targetGraphCard, defaultRadius, defaultRadius, graphBr, defaultRadius)
        } else if (isPortrait) {
            setCorners(cardBattery, defaultRadius, defaultRadius, defaultRadius, defaultRadius)
            setCorners(cardNetwork, defaultRadius, defaultRadius, defaultRadius, defaultRadius)
            setCorners(cardVoltage, defaultRadius, defaultRadius, defaultRadius, defaultRadius)
            setCorners(cardRam, defaultRadius, defaultRadius, defaultRadius, defaultRadius)
            setCorners(cardTemp, defaultRadius, defaultRadius, dynamicRadiusPx, dynamicRadiusPx)
        } else {
            setCorners(cardBattery, defaultRadius, defaultRadius, defaultRadius, defaultRadius)
            setCorners(cardNetwork, defaultRadius, defaultRadius, defaultRadius, defaultRadius)
            setCorners(cardVoltage, defaultRadius, defaultRadius, defaultRadius, dynamicRadiusPx)
            setCorners(cardRam, defaultRadius, dynamicRadiusPx, defaultRadius, defaultRadius)
            setCorners(cardTemp, defaultRadius, defaultRadius, dynamicRadiusPx, defaultRadius)
        }
    }

    private fun setCorners(
        card: MaterialCardView?,
        tl: Float,
        tr: Float,
        br: Float,
        bl: Float,
    ) {
        card?.let {
            it.shapeAppearanceModel = it.shapeAppearanceModel.toBuilder()
                .setTopLeftCornerSize(tl)
                .setTopRightCornerSize(tr)
                .setBottomRightCornerSize(br)
                .setBottomLeftCornerSize(bl)
                .build()
        }
    }
}

