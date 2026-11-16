package com.witherboymcw.superbattery.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import com.witherboymcw.superbattery.MainActivity
import com.witherboymcw.superbattery.screens.hotspot.HotspotNotificationListener

/**
 * Transparent trampoline Activity invoked by widget click PendingIntents.
 * Serves as a foreground touch target to bypass Android 12-15 and OEM (MIUI/HyperOS)
 * background activity start restrictions when double-tapping to launch the Dashboard.
 */
class WidgetTapTrampolineActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        val appWidgetManager = AppWidgetManager.getInstance(this)
        BatteryWidget.updateAllWidgets(this, appWidgetManager)
        HotspotNotificationListener.refreshFromWidgetTap()

        val nowMs = SystemClock.elapsedRealtime()
        val lastTapMs = BatteryWidget.lastWidgetTapTimestampMs

        if (BatteryWidget.isDoubleTap(nowMs, lastTapMs)) {
            BatteryWidget.lastWidgetTapTimestampMs = 0L
            val mainIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(BatteryWidget.EXTRA_CLOSE_SETTINGS, true)
            }
            startActivity(mainIntent)
        } else {
            BatteryWidget.lastWidgetTapTimestampMs = nowMs
        }

        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
