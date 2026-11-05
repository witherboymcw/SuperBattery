package com.witherboymcw.superbattery

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

internal object CriticalEventNotification {
    private const val CHANNEL_ID = "critical_battery_events"
    private const val PREFS = "critical_event_state"

    fun update(context: Context, key: String, active: Boolean, title: String, message: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stateKey = "active_$key"
        val wasActive = prefs.getBoolean(stateKey, false)
        if (!active) {
            if (wasActive) {
                try {
                    NotificationManagerCompat.from(context).cancel(key.hashCode())
                } catch (_: SecurityException) { }
            }
            prefs.edit().putBoolean(stateKey, false).apply()
            return
        }
        if (wasActive || (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)) return

        createChannel(context)
        val intent = PendingIntent.getActivity(
            context, key.hashCode(), Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(key.hashCode(), notification)
            prefs.edit().putBoolean(stateKey, true).apply()
        } catch (_: SecurityException) { }
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.critical_battery_channel), NotificationManager.IMPORTANCE_HIGH).apply {
            enableVibration(true)
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
