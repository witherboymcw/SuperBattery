package com.witherboymcw.superbattery.awareness

import android.app.Activity
import android.content.res.Configuration
import android.os.Build
import android.view.RoundedCorner
import android.view.View
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.card.MaterialCardView
import com.witherboymcw.superbattery.R

/**
 * Utility helper for calculating Android 12+ dynamic screen rounded corners
 * and adjusting asymmetric shapeAppearanceModel corner sizes for dashboard cards.
 */
object CornerAwareness {

    const val DEFAULT_CORNER_RADIUS_DP = 12f
    const val CORNER_OFFSET_DP = 16f

    /**
     * Calculates dynamic rounded corner radius in pixels from WindowInsetsCompat on Android 12+ (API 31+).
     * Returns default radius (12dp * density) if dynamic corner insets are unavailable or smaller than default.
     */
    fun calculateDynamicCornerRadius(insets: WindowInsetsCompat?, density: Float): Float {
        val defaultRadius = DEFAULT_CORNER_RADIUS_DP * density
        var dynamicRadiusPx = defaultRadius

        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) && (insets != null)) {
            val windowInsets = insets.toWindowInsets()
            if (windowInsets != null) {
                val corner = windowInsets.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)
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
        val cardVoltage = rootView.findViewById<MaterialCardView>(R.id.card_voltage_graph)
        val cardRam = rootView.findViewById<MaterialCardView>(R.id.card_ram_graph)
        val cardTemp = rootView.findViewById<MaterialCardView>(R.id.card_temperature_graph)

        val density = rootView.resources.displayMetrics.density
        val isPortrait = rootView.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

        applyAsymmetricCornersToCards(
            cardBattery = cardBattery,
            cardNetwork = cardNetwork,
            cardVoltage = cardVoltage,
            cardRam = cardRam,
            cardTemp = cardTemp,
            insets = insets,
            density = density,
            isPortrait = isPortrait,
        )
    }

    /**
     * Applies asymmetric corner radius formatting to dashboard cards inside an Activity.
     */
    fun applyAsymmetricCorners(activity: Activity, insets: WindowInsetsCompat) {
        val cardBattery = activity.findViewById<MaterialCardView>(R.id.card_battery)
        val cardNetwork = activity.findViewById<MaterialCardView>(R.id.card_network)
        val cardVoltage = activity.findViewById<MaterialCardView>(R.id.card_voltage_graph)
        val cardRam = activity.findViewById<MaterialCardView>(R.id.card_ram_graph)
        val cardTemp = activity.findViewById<MaterialCardView>(R.id.card_temperature_graph)

        val density = activity.resources.displayMetrics.density
        val isPortrait = activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

        applyAsymmetricCornersToCards(
            cardBattery = cardBattery,
            cardNetwork = cardNetwork,
            cardVoltage = cardVoltage,
            cardRam = cardRam,
            cardTemp = cardTemp,
            insets = insets,
            density = density,
            isPortrait = isPortrait,
        )
    }

    /**
     * Core logic for applying asymmetric corners to cards based on orientation and dynamic corner radius.
     */
    fun applyAsymmetricCornersToCards(
        cardBattery: MaterialCardView?,
        cardNetwork: MaterialCardView?,
        cardVoltage: MaterialCardView?,
        cardRam: MaterialCardView?,
        cardTemp: MaterialCardView?,
        insets: WindowInsetsCompat?,
        density: Float,
        isPortrait: Boolean
    ) {
        val defaultRadius = DEFAULT_CORNER_RADIUS_DP * density
        val dynamicRadiusPx = calculateDynamicCornerRadius(insets, density)

        if (isPortrait) {
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
        bl: Float
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
