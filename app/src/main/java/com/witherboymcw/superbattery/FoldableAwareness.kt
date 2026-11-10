package com.witherboymcw.superbattery

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build

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
}
