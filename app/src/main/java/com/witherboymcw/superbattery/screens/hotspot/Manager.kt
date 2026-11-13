package com.witherboymcw.superbattery.screens.hotspot

import android.content.Context
import org.json.JSONArray

object HotspotManager {
    private const val PREFS_HOTSPOT = "hotspot_manager_prefs"
    private const val KEY_PERSISTED_CLIENTS = "persisted_hotspot_clients"

    private val clientMap = LinkedHashMap<String, HotspotClient>()

    @Synchronized
    fun loadPersistedClients(context: Context): List<HotspotClient> {
        if (clientMap.isNotEmpty()) {
            return clientMap.values.toList()
        }

        val prefs = context.getSharedPreferences(PREFS_HOTSPOT, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_PERSISTED_CLIENTS, null)

        if (!jsonString.isNullOrBlank()) {
            try {
                val array = JSONArray(jsonString)
                clientMap.clear()
                for (i in 0 until array.length()) {
                    val jsonObj = array.getJSONObject(i)
                    val client = HotspotClient.fromJson(jsonObj)
                    if (client.mac.isNotBlank()) {
                        clientMap[client.mac.lowercase()] = client
                    }
                }
            } catch (_: Exception) {
                clientMap.clear()
            }
        }

        if (clientMap.isEmpty()) {
            val defaults = getInitialDemoClients()
            defaults.forEach { client ->
                clientMap[client.mac.lowercase()] = client
            }
            savePersistedClients(context)
        }

        return clientMap.values.toList()
    }

    @Synchronized
    fun savePersistedClients(context: Context) {
        val array = JSONArray()
        clientMap.values.forEach { client ->
            array.put(client.toJson())
        }
        val prefs = context.getSharedPreferences(PREFS_HOTSPOT, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PERSISTED_CLIENTS, array.toString()).apply()
    }

    @Synchronized
    fun updateClientStatus(context: Context, mac: String, isDenied: Boolean) {
        val key = mac.lowercase()
        val client = clientMap[key] ?: run {
            loadPersistedClients(context)
            clientMap[key]
        } ?: return

        client.isDenied = isDenied

        if (isDenied) {
            val cmdIptables = "iptables -I FORWARD -m mac --mac-source ${client.mac} -j DROP"
            val cmdDeauth = "hostapd_cli -i wlan0 deauthenticate ${client.mac}"
            executeRootCommands(listOf(cmdIptables, cmdDeauth))
        } else {
            val cmdIptables = "iptables -D FORWARD -m mac --mac-source ${client.mac} -j DROP"
            executeRootCommands(listOf(cmdIptables))
        }

        savePersistedClients(context)
    }

    @Synchronized
    fun updateClientAlias(context: Context, mac: String, customAlias: String?) {
        val key = mac.lowercase()
        val client = clientMap[key] ?: run {
            loadPersistedClients(context)
            clientMap[key]
        } ?: return

        client.customAlias = customAlias?.trim()?.takeIf { it.isNotBlank() }
        savePersistedClients(context)
    }

    private fun executeRootCommands(commands: List<String>): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = process.outputStream.bufferedWriter()
            for (cmd in commands) {
                os.write(cmd)
                os.newLine()
            }
            os.write("exit")
            os.newLine()
            os.flush()
            process.waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }

    fun getInitialDemoClients(): List<HotspotClient> {
        val now = System.currentTimeMillis()
        return listOf(
            HotspotClient("A4:C3:F0:11:22:33", "Galaxy S23 Ultra", null, "192.168.43.101", isDenied = false, totalDataBytes = 474_066_176L, lastConnectedTimeMs = now - 1000 * 60 * 15, isCurrentlyOnline = true),
            HotspotClient("70:85:C2:AB:CD:01", "MacBook Pro M3", null, "192.168.43.102", isDenied = false, totalDataBytes = 1_288_490_188L, lastConnectedTimeMs = now - 1000 * 60 * 30, isCurrentlyOnline = true),
            HotspotClient("BC:D1:D3:44:55:66", "iPhone 15 Pro", null, "192.168.43.103", isDenied = false, totalDataBytes = 93_742_694L, lastConnectedTimeMs = now - 1000 * 60 * 45, isCurrentlyOnline = true),
            HotspotClient("E8:68:E7:77:88:99", "iPad Air", null, "192.168.43.104", isDenied = false, totalDataBytes = 325_058_560L, lastConnectedTimeMs = now - 1000 * 60 * 120, isCurrentlyOnline = false),
            HotspotClient("34:E6:D7:AA:BB:CC", "Dell XPS 15", null, "192.168.43.105", isDenied = false, totalDataBytes = 892_038_348L, lastConnectedTimeMs = now - 1000 * 60 * 90, isCurrentlyOnline = true),
            HotspotClient("18:C0:4D:DD:EE:FF", "Google Pixel 8", null, "192.168.43.106", isDenied = false, totalDataBytes = 126_143_692L, lastConnectedTimeMs = now - 1000 * 60 * 190, isCurrentlyOnline = true),
            HotspotClient("00:1A:7D:12:34:56", "Sony Bravia Smart TV", null, "192.168.43.107", isDenied = false, totalDataBytes = 2_576_980_377L, lastConnectedTimeMs = now - 1000 * 60 * 300, isCurrentlyOnline = false),
            HotspotClient("98:B6:E9:88:77:66", "Nintendo Switch", null, "—", isDenied = true, totalDataBytes = 0L, lastConnectedTimeMs = now - 1000 * 60 * 600, isCurrentlyOnline = false),
            HotspotClient("70:66:55:44:33:22", "PlayStation 5", null, "192.168.43.109", isDenied = false, totalDataBytes = 5_476_083_302L, lastConnectedTimeMs = now - 1000 * 60 * 400, isCurrentlyOnline = false),
            HotspotClient("00:21:CC:11:22:33", "ThinkPad X1 Carbon", null, "192.168.43.110", isDenied = false, totalDataBytes = 671_319_654L, lastConnectedTimeMs = now - 1000 * 60 * 350, isCurrentlyOnline = true),
            HotspotClient("24:4B:03:AA:11:22", "Asus ROG Phone 7", null, "192.168.43.111", isDenied = false, totalDataBytes = 99_614_720L, lastConnectedTimeMs = now - 1000 * 60 * 65, isCurrentlyOnline = true),
            HotspotClient("58:82:A8:33:44:55", "Surface Laptop Studio", null, "192.168.43.112", isDenied = false, totalDataBytes = 189_267_968L, lastConnectedTimeMs = now - 1000 * 60 * 500, isCurrentlyOnline = false),
            HotspotClient("F0:D2:F1:66:77:88", "Kindle Paperwhite", null, "192.168.43.113", isDenied = false, totalDataBytes = 12_687_769L, lastConnectedTimeMs = now - 1000 * 60 * 720, isCurrentlyOnline = false),
            HotspotClient("8C:85:90:99:AA:BB", "Galaxy Watch 6", null, "192.168.43.114", isDenied = false, totalDataBytes = 8_808_038L, lastConnectedTimeMs = now - 1000 * 60 * 372, isCurrentlyOnline = true),
            HotspotClient("D8:3A:DD:CC:BB:AA", "Raspberry Pi 5", null, "192.168.43.115", isDenied = false, totalDataBytes = 357_459_558L, lastConnectedTimeMs = now - 1000 * 60 * 760, isCurrentlyOnline = true),
            HotspotClient("2C:AA:8E:12:34:78", "LG Smart Fridge", null, "—", isDenied = true, totalDataBytes = 0L, lastConnectedTimeMs = now - 1000 * 60 * 900, isCurrentlyOnline = false),
            HotspotClient("48:A6:B8:99:88:77", "Sonos Era 300", null, "192.168.43.117", isDenied = false, totalDataBytes = 649_986_048L, lastConnectedTimeMs = now - 1000 * 60 * 800, isCurrentlyOnline = false),
            HotspotClient("A0:6C:65:44:33:11", "Tesla Model 3", null, "192.168.43.118", isDenied = false, totalDataBytes = 1_932_735_283L, lastConnectedTimeMs = now - 1000 * 60 * 20, isCurrentlyOnline = true),
            HotspotClient("38:F9:D3:55:66:77", "Apple TV 4K", null, "192.168.43.119", isDenied = false, totalDataBytes = 3_758_096_384L, lastConnectedTimeMs = now - 1000 * 60 * 1000, isCurrentlyOnline = false),
            HotspotClient("2C:26:17:88:99:00", "Meta Quest 3", null, "—", isDenied = true, totalDataBytes = 0L, lastConnectedTimeMs = now - 1000 * 60 * 1200, isCurrentlyOnline = false)
        )
    }
}
