package com.witherboymcw.superbattery

import android.content.Context

internal object DeviceConnectionCounter {
    fun displayText(context: Context): String {
        return when (val status = HotspotNotificationListener.loadCurrentStatus(context)) {
            is HotspotNotificationListener.Companion.HotspotState.OFF -> "Radio Off"
            is HotspotNotificationListener.Companion.HotspotState.ON_EMPTY -> "Clients: 0"
            is HotspotNotificationListener.Companion.HotspotState.ON_CONNECTED -> "Clients: ${status.clients}"
        }
    }
}
