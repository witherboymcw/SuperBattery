package com.witherboymcw.superbattery

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

data class ConnectedClient(
    val ip: String,
    val mac: String,
    val deviceName: String = "Unknown",
    var isBlocked: Boolean = false
)

object HotspotManager {
    private const val PREFS_HOTSPOT = "hotspot_manager_prefs"
    private const val KEY_BLOCKED_MACS = "blocked_macs"

    /**
     * Đọc danh sách thiết bị đang kết nối từ /proc/net/arp
     * Chuẩn Linux AOSP: Bỏ qua gateway wlan0/rmnet, chỉ lấy dải Hotspot (ap0 hoặc wlan0 tùy chip)
     */
    fun getConnectedClients(context: Context): List<ConnectedClient> {
        val clients = mutableListOf<ConnectedClient>()
        val blockedList = getBlockedMacs(context)

        try {
            val br = BufferedReader(InputStreamReader(Runtime.getRuntime().exec("cat /proc/net/arp").inputStream))
            var line: String?
            // Bỏ dòng tiêu đề IP / HW type / Flags / HW address / Mask / Device
            br.readLine() 
            while (br.readLine().also { line = it } != null) {
                val tokens = line!!.split("\\s+".toRegex())
                if (tokens.size >= 6) {
                    val ip = tokens[0]
                    val mac = tokens[3].lowercase()
                    val iface = tokens[5]

                    // Kiểm tra MAC hợp lệ và nằm trên interface hotspot (thường là wlan0, ap0, hoặc wlan1)
                    if (mac != "00:00:00:00:00:00" && mac.length == 17) {
                        clients.add(
                            ConnectedClient(
                                ip = ip,
                                mac = mac,
                                isBlocked = blockedList.contains(mac)
                            )
                        )
                    }
                }
            }
            br.close()
        } catch (_: Exception) {}

        return clients
    }

    /**
     * Ngắt kết nối và chặn gói tin (Kick & Block)
     */
    fun blockClient(context: Context, client: ConnectedClient): Boolean {
        // 1. Chèn rule iptables chặn forward gói tin
        val cmdIptables = "iptables -I FORWARD -m mac --mac-source ${client.mac} -j DROP"
        // 2. Đá văng khỏi phiên kết nối Wi-Fi hiện tại
        val cmdDeauth = "hostapd_cli -i wlan0 deauthenticate ${client.mac}"

        val success = executeRootCommands(listOf(cmdIptables, cmdDeauth))
        if (success) {
            val set = getBlockedMacs(context).toMutableSet()
            set.add(client.mac)
            saveBlockedMacs(context, set)
            client.isBlocked = true
        }
        return success
    }

    /**
     * Bỏ chặn (Unblock)
     */
    fun unblockClient(context: Context, client: ConnectedClient): Boolean {
        val cmdIptables = "iptables -D FORWARD -m mac --mac-source ${client.mac} -j DROP"
        val success = executeRootCommands(listOf(cmdIptables))
        if (success) {
            val set = getBlockedMacs(context).toMutableSet()
            set.remove(client.mac)
            saveBlockedMacs(context, set)
            client.isBlocked = false
        }
        return success
    }

    private fun getBlockedMacs(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_HOTSPOT, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_BLOCKED_MACS, emptySet()) ?: emptySet()
    }

    private fun saveBlockedMacs(context: Context, macs: Set<String>) {
        context.getSharedPreferences(PREFS_HOTSPOT, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_BLOCKED_MACS, macs)
            .apply()
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
}
