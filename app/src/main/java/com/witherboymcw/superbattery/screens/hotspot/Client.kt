package com.witherboymcw.superbattery.screens.hotspot

import org.json.JSONObject

/**
 * Immutable data model representing a connected or persisted hotspot client device.
 */
data class HotspotClient(
    val mac: String,
    val deviceName: String,
    val customAlias: String? = null,
    val ipAddress: String,
    val isDenied: Boolean = false,
    val totalDataBytes: Long = 0L,
    val lastConnectedTimeMs: Long = 0L,
    val isCurrentlyOnline: Boolean = false
) {
    val displayName: String
        get() = customAlias?.takeIf { it.isNotBlank() } ?: deviceName

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("mac", mac)
            put("deviceName", deviceName)
            put("customAlias", customAlias ?: JSONObject.NULL)
            put("ipAddress", ipAddress)
            put("isDenied", isDenied)
            put("totalDataBytes", totalDataBytes)
            put("lastConnectedTimeMs", lastConnectedTimeMs)
            put("isCurrentlyOnline", isCurrentlyOnline)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): HotspotClient {
            return HotspotClient(
                mac = json.optString("mac"),
                deviceName = json.optString("deviceName", "Unknown Device"),
                customAlias = if (json.isNull("customAlias")) null else json.optString("customAlias").takeIf { it.isNotBlank() && it != "null" },
                ipAddress = json.optString("ipAddress", "—"),
                isDenied = json.optBoolean("isDenied", false),
                totalDataBytes = json.optLong("totalDataBytes", 0L),
                lastConnectedTimeMs = json.optLong("lastConnectedTimeMs", 0L),
                isCurrentlyOnline = json.optBoolean("isCurrentlyOnline", false)
            )
        }
    }
}
