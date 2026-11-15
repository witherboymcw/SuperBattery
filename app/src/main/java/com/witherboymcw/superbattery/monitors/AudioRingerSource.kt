package com.witherboymcw.superbattery.monitors

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Build

/**
 * Source module responsible for querying and toggling system audio ringer mode (Normal, Vibrate, Silent).
 */
class AudioRingerSource {

    fun getRingerMode(context: Context): Int {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            am?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
        } catch (_: Exception) {
            AudioManager.RINGER_MODE_NORMAL
        }
    }

    fun toggleRingerMode(context: Context, onPermissionRequired: (() -> Unit)? = null): Int {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return AudioManager.RINGER_MODE_NORMAL

        val currentMode = am.ringerMode
        val nextMode = when (currentMode) {
            AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
            AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
            else -> AudioManager.RINGER_MODE_NORMAL
        }

        if (nextMode == AudioManager.RINGER_MODE_SILENT && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (notificationManager?.isNotificationPolicyAccessGranted == false) {
                onPermissionRequired?.invoke()
                try {
                    am.ringerMode = AudioManager.RINGER_MODE_NORMAL
                } catch (_: Exception) {}
                return AudioManager.RINGER_MODE_NORMAL
            }
        }

        try {
            am.ringerMode = nextMode
        } catch (_: Exception) {
            // Guard against missing permissions
        }

        return am.ringerMode
    }
}
