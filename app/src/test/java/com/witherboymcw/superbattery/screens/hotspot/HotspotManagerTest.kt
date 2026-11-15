package com.witherboymcw.superbattery.screens.hotspot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HotspotManagerTest {

    @Test
    fun testMacAddressRegex_ValidMacs() {
        val validMacs = listOf(
            "A4:C3:F0:11:22:33",
            "70:85:C2:AB:CD:01",
            "00:1A:7D:12:34:56",
            "ff:ff:ff:ff:ff:ff",
            "00:00:00:00:00:00"
        )
        for (mac in validMacs) {
            assertTrue("Expected valid MAC: $mac", HotspotManager.MAC_REGEX.matches(mac))
        }
    }

    @Test
    fun testMacAddressRegex_InvalidMacsAndCommandInjections() {
        val invalidMacs = listOf(
            "invalid_mac",
            "A4:C3:F0:11:22",
            "A4:C3:F0:11:22:33:44",
            "A4:C3:F0:11:22:33; rm -rf /",
            "A4:C3:F0:11:22:33\nreboot",
            "A4-C3-F0-11-22-33",
            "A4:C3:F0:11:22:3G",
            ""
        )
        for (mac in invalidMacs) {
            assertFalse("Expected invalid MAC / rejected command injection: $mac", HotspotManager.MAC_REGEX.matches(mac))
        }
    }
}
