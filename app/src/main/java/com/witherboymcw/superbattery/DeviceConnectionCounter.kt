package com.witherboymcw.superbattery

import android.content.Context

internal object DeviceConnectionCounter {
    fun displayText(context: Context): String {
        val localizedContext = SettingsHelper.getLocalizedContext(context)
        return when (val status = HotspotNotificationListener.loadCurrentStatus(context)) {
            is HotspotNotificationListener.Companion.HotspotState.OFF ->
                localizedContext.getString(R.string.hotspot_radio_off)
            is HotspotNotificationListener.Companion.HotspotState.ON_EMPTY ->
                localizedContext.getString(R.string.hotspot_clients_count, 0)
            is HotspotNotificationListener.Companion.HotspotState.ON_CONNECTED ->
                localizedContext.getString(R.string.hotspot_clients_count, status.clients)
        }
    }
}
