package com.witherboymcw.superbattery.monitors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import com.witherboymcw.superbattery.screens.hotspot.HotspotManager
import kotlinx.coroutines.*

object HotspotRootMonitor {

    private const val TAG = "HotspotRootMonitor"
    const val ACTION_WIFI_AP_STATE_CHANGED = "android.net.wifi.WIFI_AP_STATE_CHANGED"
    const val EXTRA_WIFI_AP_STATE = "wifi_state"
    const val WIFI_AP_STATE_DISABLED = 11
    const val WIFI_AP_STATE_ENABLED = 13

    private var monitorJob: Job? = null
    private var process: Process? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRegistered = false
    private var appContext: Context? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == ACTION_WIFI_AP_STATE_CHANGED) {
                val state = intent.getIntExtra(EXTRA_WIFI_AP_STATE, 0)
                Log.d(TAG, "Hotspot state changed: $state")
                when (state) {
                    WIFI_AP_STATE_ENABLED -> {
                        updateNotificationListenerState(true)
                        startMonitoringProcess()
                    }
                    WIFI_AP_STATE_DISABLED -> {
                        updateNotificationListenerState(false)
                        stopMonitoringProcess()
                    }
                }
            }
        }
    }

    private fun updateNotificationListenerState(isEnabled: Boolean) {
        val ctx = appContext ?: return
        if (!isEnabled) {
            com.witherboymcw.superbattery.screens.hotspot.HotspotNotificationListener.setCurrentStatus(
                ctx,
                com.witherboymcw.superbattery.screens.hotspot.HotspotNotificationListener.Companion.HotspotState.OFF
            )
        } else {
            val count = HotspotManager.getActiveClientCount()
            val state = if (count > 0) {
                com.witherboymcw.superbattery.screens.hotspot.HotspotNotificationListener.Companion.HotspotState.ON_CONNECTED(count)
            } else {
                com.witherboymcw.superbattery.screens.hotspot.HotspotNotificationListener.Companion.HotspotState.ON_EMPTY
            }
            com.witherboymcw.superbattery.screens.hotspot.HotspotNotificationListener.setCurrentStatus(ctx, state)
        }
        com.witherboymcw.superbattery.widgets.BatteryWidget.updateAllWidgets(
            ctx,
            android.appwidget.AppWidgetManager.getInstance(ctx)
        )
    }

    fun startMonitoring(context: Context) {
        appContext = context.applicationContext
        evaluateSettings()
    }

    fun evaluateSettings() {
        val ctx = appContext ?: return
        val isRootEnabled = com.witherboymcw.superbattery.settings.AppPreferences.isRootHotspotMonitorEnabled(ctx)

        if (isRootEnabled) {
            if (!isRegistered) {
                val filter = IntentFilter(ACTION_WIFI_AP_STATE_CHANGED)
                ContextCompat.registerReceiver(
                    ctx,
                    receiver,
                    filter,
                    ContextCompat.RECEIVER_EXPORTED
                )
                isRegistered = true
            }

            startMonitoringProcess()
        } else {
            stopMonitoring()
        }

        HotspotManager.loadPersistedClients(ctx)
    }

    fun stopMonitoring() {
        if (isRegistered) {
            try {
                appContext?.unregisterReceiver(receiver)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering receiver", e)
            }
            isRegistered = false
        }
        stopMonitoringProcess()
    }

    private fun startMonitoringProcess() {
        if (monitorJob?.isActive == true) return

        monitorJob = scope.launch {
            try {
                var iface = detectHotspotInterface()
                Log.d(TAG, "Detected hotspot interface: $iface")

                // Perform continuous polling loop every 2.5 seconds while hotspot is active
                while (isActive) {
                    if (iface.isNullOrEmpty()) {
                        iface = detectHotspotInterface()
                    }

                    val isStillActive = syncAndCheckHotspotState(iface)
                    if (!isStillActive) {
                        Log.d(TAG, "Hotspot is disabled via shell, stopping monitor process")
                        updateNotificationListenerState(false)
                        break
                    }

                    delay(2500L)
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Monitor job cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Error in monitoring process", e)
            } finally {
                stopProcess()
            }
        }
    }

    private fun stopMonitoringProcess() {
        monitorJob?.cancel()
        monitorJob = null
        stopProcess()
    }

    private fun stopProcess() {
        try {
            process?.destroyForcibly()
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying process", e)
        }
        process = null
    }

    private suspend fun runShellCommand(cmd: String): List<String> = withContext(Dispatchers.IO) {
        var proc: Process? = null
        val lines = mutableListOf<String>()
        try {
            proc = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            proc.inputStream.bufferedReader().use { reader ->
                var line = runInterruptible { reader.readLine() }
                while (line != null) {
                    lines.add(line)
                    line = runInterruptible { reader.readLine() }
                }
            }
            runInterruptible { proc.waitFor() }
        } catch (e: Exception) {
            Log.w(TAG, "Shell command failed: $cmd", e)
        } finally {
            try { proc?.inputStream?.close() } catch (_: Exception) {}
            try { proc?.errorStream?.close() } catch (_: Exception) {}
            try { proc?.outputStream?.close() } catch (_: Exception) {}
            try { proc?.destroyForcibly() } catch (_: Exception) {}
        }
        lines
    }

    private suspend fun detectHotspotInterface(): String? = withContext(Dispatchers.IO) {
        val cmds = listOf(
            "dumpsys tethering | grep -E -o '[a-zA-Z0-9_-]+ - TetheredState' | awk '{print \$1}'",
            "iw dev | awk '\\$1==\"Interface\"{iface=\\$2} \\$1==\"type\" && (\\$2==\"AP\" || \\$2==\"P2P-GO\"){print iface}'",
            "ip addr | grep -E 'softap.*(UP|UNKNOWN)|swlan.*(UP|UNKNOWN)|ap0.*(UP|UNKNOWN)' | awk '{print \\$2}' | tr -d ':'",
            "dumpsys wifi | grep -E 'mSoftApInterfaceName=|apIfaceInstance=' | cut -d '=' -f 2"
        )
        for (cmd in cmds) {
            val lines = runShellCommand(cmd)
            val iface = lines.firstOrNull()?.trim()
            if (!iface.isNullOrEmpty() && iface.length > 1) {
                return@withContext iface
            }
        }
        null
    }

    private suspend fun syncAndCheckHotspotState(iface: String?): Boolean = withContext(Dispatchers.IO) {
        try {
            val neighCmd = if (!iface.isNullOrEmpty()) "ip neigh show dev $iface 2>/dev/null" else "ip neigh 2>/dev/null"
            val cmd = "dumpsys tethering 2>/dev/null | grep -E -q '[a-zA-Z0-9_-]+ - TetheredState' && echo 'HOTSPOT_ON' || (iw dev 2>/dev/null | grep -q 'type AP' && echo 'HOTSPOT_ON' || (ip addr 2>/dev/null | grep -E -q '(softap|swlan|ap0|ap_br).*(UP|UNKNOWN)' && echo 'HOTSPOT_ON' || echo 'HOTSPOT_OFF')); echo '---MARKER---'; dumpsys tethering 2>/dev/null | grep \"client:\"; cat /proc/net/arp 2>/dev/null; $neighCmd; cat /data/misc/apexdata/com.android.tethering/misc/dhcp/dnsmasq.leases /data/misc/dhcp/dnsmasq.leases 2>/dev/null; dumpsys network_stack 2>/dev/null"
            val lines = runShellCommand(cmd)

            if (lines.isEmpty()) return@withContext false

            val markerIndex = lines.indexOf("---MARKER---")
            val statusHeader = if (markerIndex != -1) lines.subList(0, markerIndex) else lines
            val isHotspotActive = statusHeader.any { it.trim() == "HOTSPOT_ON" }

            if (!isHotspotActive || iface.isNullOrEmpty()) {
                appContext?.let { ctx ->
                    HotspotManager.syncClients(ctx, emptyMap())
                    updateNotificationListenerState(false)
                }
                return@withContext false
            }

            val dataLines = if (markerIndex != -1 && markerIndex + 1 < lines.size) {
                lines.subList(markerIndex + 1, lines.size)
            } else {
                emptyList()
            }

            val activeClients = mutableMapOf<String, Pair<String, String>>()
            val hostnameMap = mutableMapOf<String, String>()
            val onlineMacs = mutableSetOf<String>()

            fun isIpv4(addr: String?): Boolean = addr != null && addr.contains(".") && !addr.contains(":")

            for (line in dataLines) {
                // 1. Dumpsys Tethering allocated IP leases: client: /IP (MAC)
                val dumpsysMatches = HotspotParser.parseDumpsysTetheringClients(line)
                for (match in dumpsysMatches) {
                    val mac = match.first.lowercase()
                    val ip = match.second
                    activeClients[mac] = Pair(ip, "Unknown Device")
                }

                // 2. ARP table live clients (flags != 0x0)
                val arpMatch = HotspotParser.parseArpLine(line, iface)
                if (arpMatch != null) {
                    val mac = arpMatch.first.lowercase()
                    val ip = arpMatch.second
                    onlineMacs.add(mac)
                    val existing = activeClients[mac]
                    val bestIp = if (existing != null && isIpv4(existing.first)) existing.first else ip
                    activeClients[mac] = Pair(bestIp, existing?.second ?: "Unknown Device")
                }

                // 3. IP Neigh live clients (state REACHABLE, DELAY, STALE, PROBE, PERMANENT)
                val neighMatch = HotspotParser.parseIpNeighLine(line)
                if (neighMatch != null) {
                    val mac = neighMatch.first.lowercase()
                    val ip = neighMatch.second
                    onlineMacs.add(mac)
                    val existing = activeClients[mac]
                    val bestIp = if (existing != null && isIpv4(existing.first)) existing.first else ip
                    activeClients[mac] = Pair(bestIp, existing?.second ?: "Unknown Device")
                }

                // 4. Legacy dnsmasq lease active clients
                val leaseMatch = HotspotParser.parseDnsmasqLeaseLine(line)
                if (leaseMatch != null) {
                    val mac = leaseMatch.first.lowercase()
                    val ip = leaseMatch.second
                    val hostname = leaseMatch.third
                    onlineMacs.add(mac)
                    activeClients[mac] = Pair(ip, hostname)
                    if (hostname != "Unknown Device" && hostname != "Unknown") {
                        hostnameMap[mac] = hostname
                    }
                }

                // 5. NetworkStack DHCP logs: used for hostname resolution!
                val netStackMatch = HotspotParser.parseNetworkStackDhcpLine(line)
                if (netStackMatch != null) {
                    val mac = netStackMatch.first.lowercase()
                    val hostname = netStackMatch.third
                    if (hostname != "Unknown Device" && hostname != "Unknown") {
                        hostnameMap[mac] = hostname
                    }
                }
            }

            // Enrich ONLY genuinely online L2/L3 clients with resolved hostnames
            val finalDiscoveredClients = mutableMapOf<String, Pair<String, String>>()
            onlineMacs.forEach { mac ->
                val ipAndHost = activeClients[mac]
                val ip = ipAndHost?.first ?: "—"
                val currentHost = ipAndHost?.second ?: "Unknown Device"
                val resolvedHost = if (currentHost != "Unknown Device" && currentHost != "Unknown") {
                    currentHost
                } else {
                    hostnameMap[mac] ?: "Unknown Device"
                }
                finalDiscoveredClients[mac] = Pair(ip, resolvedHost)
            }

            appContext?.let { ctx ->
                HotspotManager.syncClients(ctx, finalDiscoveredClients)
                updateNotificationListenerState(true)
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Error syncing clients from sources", e)
            false
        }
    }
}
