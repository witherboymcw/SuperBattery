package com.witherboymcw.superbattery.monitors

object HotspotParser {
    fun parseHostapdLine(line: String): Pair<String, String>? {
        if (line.contains("AP-STA-CONNECTED")) {
            val mac = line.substringAfter("AP-STA-CONNECTED").trim().substringBefore(" ")
            if (mac.matches(Regex("^([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}$"))) return Pair("CONNECTED", mac)
        } else if (line.contains("AP-STA-DISCONNECTED")) {
            val mac = line.substringAfter("AP-STA-DISCONNECTED").trim().substringBefore(" ")
            if (mac.matches(Regex("^([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}$"))) return Pair("DISCONNECTED", mac)
        }
        return null
    }

    fun parseDnsmasqLeaseLine(line: String): Triple<String, String, String>? {
        // <expiry> <mac> <ip> <hostname> <client_id>
        if (line.matches(Regex("^[0-9]+\\s+([0-9a-fA-F:]+)\\s+([0-9.]+)\\s+(.+)\\s+(.+)$"))) {
            val parts = line.split("\\s+".toRegex())
            if (parts.size >= 4) {
                return Triple(parts[1], parts[2], parts[3])
            }
        }
        return null
    }

    fun parseIpNeighLine(line: String): Pair<String, String>? {
        val parts = line.trim().split("\\s+".toRegex())
        if (parts.size >= 4 && parts.contains("lladdr")) {
            val ip = parts[0]
            if (!ip.contains(".") || ip.contains(":")) return null
            val macIndex = parts.indexOf("lladdr") + 1
            if (macIndex < parts.size) {
                val mac = parts[macIndex]
                val state = parts.last().uppercase()
                val validStates = setOf("REACHABLE", "DELAY", "PROBE", "PERMANENT")
                if (validStates.contains(state) && mac.matches(Regex("^([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}$"))) {
                    return Pair(mac, ip)
                }
            }
        }
        return null
    }

    fun parseArpLine(line: String, targetIface: String? = null): Pair<String, String>? {
        // IP address HW type Flags HW address Mask Device
        // 192.168.228.247 0x1 0x2 8e:23:55:4f:58:4a * wlan1
        val parts = line.trim().split("\\s+".toRegex())
        if (parts.size >= 6) {
            val ip = parts[0]
            val flags = parts[2]
            val mac = parts[3]
            val device = parts[5]

            if (flags != "0x0" && mac.matches(Regex("^([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}$"))) {
                if (targetIface == null || device.equals(targetIface, ignoreCase = true)) {
                    return Pair(mac, ip)
                }
            }
        }
        return null
    }

    private val DUMPSYS_CLIENT_REGEX = Regex("client:\\s*/?([0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+)\\s*\\(([0-9a-fA-F:]{17})\\)")

    fun parseDumpsysTetheringClients(line: String): List<Pair<String, String>> {
        return DUMPSYS_CLIENT_REGEX.findAll(line).map { match ->
            val ip = match.groupValues[1]
            val mac = match.groupValues[2]
            Pair(mac, ip)
        }.toList()
    }

    fun parseDumpsysTetheringClient(line: String): Pair<String, String>? {
        return parseDumpsysTetheringClients(line).firstOrNull()
    }

    private val NETWORK_STACK_DHCP_REGEX = Regex(
        "hwAddr:\\s*([0-9a-fA-F:]{17}).*?netAddr:\\s*([0-9.]+)(?:/\\d+)?.*?hostname:\\s*([^,\\s]+)",
        RegexOption.IGNORE_CASE
    )

    fun parseNetworkStackDhcpLine(line: String): Triple<String, String, String>? {
        val match = NETWORK_STACK_DHCP_REGEX.find(line) ?: return null
        val mac = match.groupValues[1]
        val ip = match.groupValues[2]
        val rawHostname = match.groupValues[3].trim().replace("-", " ")
        val hostname = if (rawHostname.equals("null", ignoreCase = true) || rawHostname.isBlank()) {
            "Unknown Device"
        } else {
            rawHostname
        }
        return Triple(mac, ip, hostname)
    }
}
