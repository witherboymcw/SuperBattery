package com.witherboymcw.superbattery.awareness

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import androidx.window.layout.WindowMetricsCalculator
import kotlin.math.max
import kotlin.math.min

object FoldableAwareness {

    @Volatile
    private var isFoldingFeatureDetected: Boolean = false

    fun markFoldingFeatureDetected(detected: Boolean) {
        if (detected) {
            isFoldingFeatureDetected = true
        }
    }

    fun isFoldableDevice(context: Context): Boolean {
        if (isFoldingFeatureDetected) {
            return true
        }

        val pm = context.packageManager
        val hasHingeFeature = pm.hasSystemFeature("android.hardware.sensor.hinge_angle") ||
                pm.hasSystemFeature("android.hardware.type.foldable")

        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val hasHingeSensor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                sm?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) != null

        return hasHingeFeature || hasHingeSensor
    }

    /**
     * Checks if the activity display window is in Mini Square Mode.
     * Triggers when:
     * 1. Window aspect ratio is near square (max/min <= 1.35)
     * 2. Physical size belongs to small group (maxDp < 600dp and minDp < 520dp)
     * Accurately covers: Galaxy Z Flip cover screen, split-screen window on slab phones,
     * and Galaxy Z Fold in Tent mode splitting outer screen.
     * Excludes: Full-screen inner displays on Galaxy Z Fold and other foldables (where minDp >= 600dp).
     */
    fun isMiniSquareMode(activity: Activity): Boolean {
        return try {
            val metrics = WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(activity)
            val bounds = metrics.bounds
            val density = activity.resources.displayMetrics.density
            if (density <= 0f) return false

            val widthDp = bounds.width() / density
            val heightDp = bounds.height() / density

            if (widthDp <= 0f || heightDp <= 0f) return false

            val minDp = min(widthDp, heightDp)
            val maxDp = max(widthDp, heightDp)

            val aspectRatio = maxDp / minDp
            val isNearSquare = aspectRatio <= 1.35f
            val isSmallSize = maxDp < 600f && minDp < 520f

            isNearSquare && isSmallSize
        } catch (_: Exception) {
            false
        }
    }
}
