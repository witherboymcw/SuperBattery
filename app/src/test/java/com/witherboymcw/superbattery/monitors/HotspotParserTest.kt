package com.witherboymcw.superbattery.monitors

import org.junit.Assert.*
import org.junit.Test

class HotspotParserTest {

    @Test
    fun testParseHostapdLine() {
        val connectedLine = "<3>AP-STA-CONNECTED A4:C3:F0:11:22:33"
        val parsedConnected = HotspotParser.parseHostapdLine(connectedLine)
        assertNotNull(parsedConnected)
        assertEquals("CONNECTED", parsedConnected!!.first)
        assertEquals("A4:C3:F0:11:22:33", parsedConnected.second)

        val disconnectedLine = "<3>AP-STA-DISCONNECTED A4:C3:F0:11:22:33"
        val parsedDisconnected = HotspotParser.parseHostapdLine(disconnectedLine)
        assertNotNull(parsedDisconnected)
        assertEquals("DISCONNECTED", parsedDisconnected!!.first)
        assertEquals("A4:C3:F0:11:22:33", parsedDisconnected.second)
        
        val invalidLine = "some other text"
        assertNull(HotspotParser.parseHostapdLine(invalidLine))
    }

    @Test
    fun testParseDnsmasqLeaseLine() {
        val leaseLine = "1699999999 A4:C3:F0:11:22:33 192.168.43.101 Galaxy-S23-Ultra *"
        val parsedLease = HotspotParser.parseDnsmasqLeaseLine(leaseLine)
        assertNotNull(parsedLease)
        assertEquals("A4:C3:F0:11:22:33", parsedLease!!.first)
        assertEquals("192.168.43.101", parsedLease.second)
        assertEquals("Galaxy-S23-Ultra", parsedLease.third)
    }

    @Test
    fun testParseIpNeighLine() {
        val neighLine = "192.168.43.101 dev wlan0 lladdr A4:C3:F0:11:22:33 REACHABLE"
        val parsedNeigh = HotspotParser.parseIpNeighLine(neighLine)
        assertNotNull(parsedNeigh)
        assertEquals("A4:C3:F0:11:22:33", parsedNeigh!!.first)
        assertEquals("192.168.43.101", parsedNeigh.second)
        
        val staleLine = "192.168.43.102 dev wlan0 lladdr BB:C3:F0:11:22:33 STALE"
        val parsedStale = HotspotParser.parseIpNeighLine(staleLine)
        assertNotNull(parsedStale)
        assertEquals("BB:C3:F0:11:22:33", parsedStale!!.first)
        assertEquals("192.168.43.102", parsedStale.second)

        val fourTokenLine = "192.168.228.247 lladdr 8e:23:55:4f:58:4a STALE"
        val parsedFourToken = HotspotParser.parseIpNeighLine(fourTokenLine)
        assertNotNull(parsedFourToken)
        assertEquals("8e:23:55:4f:58:4a", parsedFourToken!!.first)
        assertEquals("192.168.228.247", parsedFourToken.second)

        val failedLine = "192.168.43.103 dev wlan0 lladdr CC:C3:F0:11:22:33 FAILED"
        assertNull(HotspotParser.parseIpNeighLine(failedLine))
    }

    @Test
    fun testParseArpLine() {
        val arpLine = "192.168.228.247 0x1 0x2 8e:23:55:4f:58:4a * wlan1"
        val parsed = HotspotParser.parseArpLine(arpLine, "wlan1")
        assertNotNull(parsed)
        assertEquals("8e:23:55:4f:58:4a", parsed!!.first)
        assertEquals("192.168.228.247", parsed.second)

        val invalidFlagsLine = "192.168.228.247 0x1 0x0 8e:23:55:4f:58:4a * wlan1"
        assertNull(HotspotParser.parseArpLine(invalidFlagsLine, "wlan1"))
    }

    @Test
    fun testParseDumpsysTetheringClient() {
        val clientLine = "client: /192.168.228.247 (8e:23:55:4f:58:4a)"
        val parsed = HotspotParser.parseDumpsysTetheringClient(clientLine)
        assertNotNull(parsed)
        assertEquals("8e:23:55:4f:58:4a", parsed!!.first)
        assertEquals("192.168.228.247", parsed.second)

        val complexLine = "{android.net.ip.IpServer@c61e6c1={/192.168.228.247=downstream: 36 (62:1e:0f:26:ad:d3), client: /192.168.228.247 (8e:23:55:4f:58:4a)}}"
        val parsedComplex = HotspotParser.parseDumpsysTetheringClient(complexLine)
        assertNotNull(parsedComplex)
        assertEquals("8e:23:55:4f:58:4a", parsedComplex!!.first)
        assertEquals("192.168.228.247", parsedComplex.second)
    }

    @Test
    fun testParseNetworkStackDhcpLine() {
        val line = "2026-11-28T20:54:15.719557 - [wlan1.DHCP.Repository] Offering new generated lease clientId: 019E5A913F4CFF, hwAddr: 9e:5a:91:3f:4c:ff, netAddr: 192.168.71.19/24, expTime: 809881545,hostname: Redmi-Note-10S"
        val parsed = HotspotParser.parseNetworkStackDhcpLine(line)
        assertNotNull(parsed)
        assertEquals("9e:5a:91:3f:4c:ff", parsed!!.first)
        assertEquals("192.168.71.19", parsed.second)
        assertEquals("Redmi Note 10S", parsed.third)

        val nullHostLine = "hwAddr: 22:b3:8f:de:8a:99, netAddr: 192.168.71.101/24, expTime: 533398783,hostname: null"
        val parsedNullHost = HotspotParser.parseNetworkStackDhcpLine(nullHostLine)
        assertNotNull(parsedNullHost)
        assertEquals("22:b3:8f:de:8a:99", parsedNullHost!!.first)
        assertEquals("192.168.71.101", parsedNullHost.second)
        assertEquals("Unknown Device", parsedNullHost.third)
    }
}
