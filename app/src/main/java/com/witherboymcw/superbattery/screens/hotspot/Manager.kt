package com.witherboymcw.superbattery.screens.hotspot

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.witherboymcw.superbattery.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.concurrent.TimeUnit

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
object HotspotManager {
    private const val TAG = "HotspotManager"
    private const val PREFS_HOTSPOT = "hotspot_manager_prefs"
    private const val KEY_PERSISTED_CLIENTS = "persisted_hotspot_clients"
    val MAC_REGEX = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")

    private val persistenceDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val scope = CoroutineScope(persistenceDispatcher + SupervisorJob())
    private val clientMap = LinkedHashMap<String, HotspotClient>()
    private val _clientsState = MutableStateFlow<List<HotspotClient>>(emptyList())
    val clientsState: StateFlow<List<HotspotClient>> = _clientsState.asStateFlow()

    @Synchronized
    fun loadPersistedClients(context: Context): List<HotspotClient> {
        val appContext = context.applicationContext
        val isRootEnabled = com.witherboymcw.superbattery.settings.AppPreferences.isRootHotspotMonitorEnabled(appContext)

        if (clientMap.isEmpty()) {
            val prefs = appContext.getSharedPreferences(PREFS_HOTSPOT, Context.MODE_PRIVATE)
            val jsonString = prefs.getString(KEY_PERSISTED_CLIENTS, null)
            if (!jsonString.isNullOrBlank()) {
                try {
                    val array = JSONArray(jsonString)
                    for (i in 0 until array.length()) {
                        val jsonObj = array.getJSONObject(i)
                        val client = HotspotClient.fromJson(jsonObj)
                        val macLower = client.mac.lowercase()
                        if (macLower.isNotBlank()) {
                            clientMap[macLower] = client
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load saved hotspot clients; resetting client list", e)
                    clientMap.clear()
                }
            }
        }

        _clientsState.value = getActiveClientsList(isRootEnabled)
        return _clientsState.value
    }

    private fun getActiveClientsList(isRootEnabled: Boolean): List<HotspotClient> {
        if (isRootEnabled) {
            return clientMap.values.toList()
        }
        val demoClients = getInitialDemoClients()
        return demoClients.map { demo ->
            clientMap[demo.mac.lowercase()] ?: demo
        }
    }

    @Synchronized
    fun savePersistedClients(context: Context) {
        val appContext = context.applicationContext
        val array = JSONArray()
        clientMap.values.forEach { client ->
            array.put(client.toJson())
        }
        val prefs = appContext.getSharedPreferences(PREFS_HOTSPOT, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PERSISTED_CLIENTS, array.toString()).apply()

        val isRootEnabled = com.witherboymcw.superbattery.settings.AppPreferences.isRootHotspotMonitorEnabled(appContext)
        _clientsState.value = getActiveClientsList(isRootEnabled)
    }

    fun updateClientStatus(context: Context, mac: String, isDenied: Boolean) {
        val appContext = context.applicationContext
        if (!MAC_REGEX.matches(mac)) {
            Log.w(TAG, "Invalid MAC address format: $mac")
            return
        }

        scope.launch {
            val (client, commands) = synchronized(this@HotspotManager) {
                val key = mac.lowercase()
                val existingClient = clientMap[key]
                    ?: getInitialDemoClients().firstOrNull { it.mac.equals(mac, ignoreCase = true) }
                    ?: run {
                        loadPersistedClients(appContext)
                        clientMap[key]
                    } ?: return@synchronized null to null

                if (!MAC_REGEX.matches(existingClient.mac)) {
                    Log.w(TAG, "Invalid client MAC address format: ${existingClient.mac}")
                    return@synchronized null to null
                }

                val cmds = if (isDenied) {
                    val cmdCleanup = "iptables -D FORWARD -m mac --mac-source ${existingClient.mac} -j DROP 2>/dev/null || true"
                    val cmdIptables = "iptables -I FORWARD -m mac --mac-source ${existingClient.mac} -j DROP"
                    val cmdDeauth = "hostapd_cli deauthenticate ${existingClient.mac} 2>/dev/null || hostapd_cli -i wlan0 deauthenticate ${existingClient.mac} 2>/dev/null || true"
                    listOf(cmdCleanup, cmdIptables, cmdDeauth)
                } else {
                    val cmdIptables = "iptables -D FORWARD -m mac --mac-source ${existingClient.mac} -j DROP 2>/dev/null || true"
                    listOf(cmdIptables)
                }

                existingClient to cmds
            }

            if (client == null || commands.isNullOrEmpty()) return@launch

            val success = executeRootCommands(commands)
            if (success) {
                synchronized(this@HotspotManager) {
                    val key = mac.lowercase()
                    clientMap[key] = client.copy(isDenied = isDenied)
                    savePersistedClients(appContext)
                }
            } else {
                Log.w(TAG, "Failed to execute root commands for ${client.mac}. Reverting UI state.")
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        appContext,
                        appContext.getString(R.string.hotspot_root_required),
                        Toast.LENGTH_SHORT
                    ).show()
                    _clientsState.value = synchronized(this@HotspotManager) { clientMap.values.toList() }
                }
            }
        }
    }

    @Synchronized
    fun updateClientAlias(context: Context, mac: String, customAlias: String?) {
        val appContext = context.applicationContext
        val key = mac.lowercase()
        val client = clientMap[key]
            ?: getInitialDemoClients().firstOrNull { it.mac.equals(mac, ignoreCase = true) }
            ?: run {
                loadPersistedClients(appContext)
                clientMap[key]
            } ?: return

        val trimmedAlias = customAlias?.trim()?.takeIf { it.isNotBlank() }
        clientMap[key] = client.copy(customAlias = trimmedAlias)
        savePersistedClients(appContext)
    }

    @Synchronized
    fun syncClients(context: Context, discoveredClients: Map<String, Pair<String, String>>) {
        val appContext = context.applicationContext
        loadPersistedClients(appContext)

        var hasChanges = false
        val now = System.currentTimeMillis()

        discoveredClients.forEach { (macLower, info) ->
            val existing = clientMap[macLower]
            val ip = info.first
            val hostname = info.second

            if (existing == null) {
                val newClient = HotspotClient(
                    mac = macLower,
                    deviceName = if (hostname != "Unknown" && hostname != "Unknown Device") hostname else "Unknown Device",
                    customAlias = null,
                    ipAddress = ip,
                    isDenied = false,
                    totalDataBytes = 0L,
                    lastConnectedTimeMs = now,
                    isCurrentlyOnline = true
                )
                clientMap[macLower] = newClient
                hasChanges = true
            } else {
                var updated = existing
                if (!existing.isCurrentlyOnline) {
                    updated = updated.copy(isCurrentlyOnline = true, lastConnectedTimeMs = now)
                    hasChanges = true
                }
                if (ip != "—" && ip != existing.ipAddress) {
                    updated = updated.copy(ipAddress = ip)
                    hasChanges = true
                }
                if (hostname != "Unknown" && hostname != "Unknown Device" && hostname != existing.deviceName) {
                    updated = updated.copy(deviceName = hostname)
                    hasChanges = true
                }
                if (updated !== existing) {
                    clientMap[macLower] = updated
                }
            }
        }

        clientMap.values.forEach { client ->
            if (client.isCurrentlyOnline && !discoveredClients.containsKey(client.mac.lowercase())) {
                clientMap[client.mac.lowercase()] = client.copy(isCurrentlyOnline = false)
                hasChanges = true
            }
        }

        if (hasChanges) {
            savePersistedClientsAsync(appContext)
        }
    }

    private fun savePersistedClientsAsync(context: Context) {
        val appContext = context.applicationContext
        val currentClients = synchronized(this) { clientMap.values.toList() }
        val isRootEnabled = com.witherboymcw.superbattery.settings.AppPreferences.isRootHotspotMonitorEnabled(appContext)
        _clientsState.value = if (isRootEnabled) currentClients else if (currentClients.isNotEmpty()) currentClients else getInitialDemoClients()

        scope.launch(Dispatchers.IO) {
            val array = JSONArray()
            currentClients.forEach { client ->
                array.put(client.toJson())
            }
            val prefs = appContext.getSharedPreferences(PREFS_HOTSPOT, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_PERSISTED_CLIENTS, array.toString()).apply()
        }
    }

    @Synchronized
    fun onClientConnected(context: Context, mac: String, ip: String? = null, hostname: String? = null) {
        val appContext = context.applicationContext
        loadPersistedClients(appContext)
        val key = mac.lowercase()
        val existing = clientMap[key]
        val updated = existing?.copy(
            isCurrentlyOnline = true,
            ipAddress = ip ?: existing.ipAddress,
            deviceName = hostname ?: existing.deviceName,
            lastConnectedTimeMs = System.currentTimeMillis()
        ) ?: HotspotClient(
            mac = key,
            deviceName = hostname ?: "Unknown Device",
            customAlias = null,
            ipAddress = ip ?: "—",
            isDenied = false,
            totalDataBytes = 0L,
            lastConnectedTimeMs = System.currentTimeMillis(),
            isCurrentlyOnline = true
        )
        clientMap[key] = updated
        savePersistedClients(appContext)
    }

    @Synchronized
    fun onClientDisconnected(context: Context, mac: String) {
        val appContext = context.applicationContext
        loadPersistedClients(appContext)
        val key = mac.lowercase()
        val existing = clientMap[key]
        if (existing != null) {
            clientMap[key] = existing.copy(isCurrentlyOnline = false)
            savePersistedClients(appContext)
        }
    }

    @Synchronized
    fun getActiveClientCount(): Int {
        return clientMap.values.count { it.isCurrentlyOnline }
    }

    private suspend fun executeRootCommands(commands: List<String>): Boolean = withContext(Dispatchers.IO) {
        var process: Process? = null
        try {
            process = Runtime.getRuntime().exec("su")

            process.outputStream.bufferedWriter().use { writer ->
                for (cmd in commands) {
                    writer.write(cmd)
                    writer.newLine()
                }
                writer.write("exit")
                writer.newLine()
                writer.flush()
            }

            val completed = process.waitFor(5, TimeUnit.SECONDS)
            completed && process.exitValue() == 0
        } catch (e: Exception) {
            Log.e(TAG, "Error executing root commands", e)
            false
        } finally {
            try { process?.inputStream?.close() } catch (_: Exception) {}
            try { process?.errorStream?.close() } catch (_: Exception) {}
            try { process?.outputStream?.close() } catch (_: Exception) {}
            try { process?.destroyForcibly() } catch (_: Exception) {}
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
