package com.witherboymcw.superbattery.screens.hotspot

import com.witherboymcw.superbattery.screens.hotspot.HotspotNotificationListener.Companion.HotspotState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HotspotNotificationParserTest {

    @Test
    fun testParseEnglishNotifications_OnEmpty() {
        val result1 = HotspotNotificationParser.parseNotification(
            notificationTitle = "Mobile Hotspot",
            notificationContent = "No devices connected"
        )
        assertEquals(HotspotState.ON_EMPTY, result1)

        val result2 = HotspotNotificationParser.parseNotification(
            notificationTitle = "Portable Hotspot active",
            notificationContent = "0 connected devices"
        )
        assertEquals(HotspotState.ON_EMPTY, result2)
    }

    @Test
    fun testParseEnglishNotifications_OnConnected() {
        val result1 = HotspotNotificationParser.parseNotification(
            notificationTitle = "Tethering or Mobile Hotspot",
            notificationContent = "3 devices connected"
        )
        assertEquals(HotspotState.ON_CONNECTED(3), result1)

        val result2 = HotspotNotificationParser.parseNotification(
            notificationTitle = "Personal Hotspot",
            notificationContent = "1 client connected"
        )
        assertEquals(HotspotState.ON_CONNECTED(1), result2)
    }

    @Test
    fun testParseVietnameseNotifications_OnEmpty() {
        val result1 = HotspotNotificationParser.parseNotification(
            notificationTitle = "Điểm phát sóng wifi cá nhân",
            notificationContent = "Không có thiết bị nào kết nối"
        )
        assertEquals(HotspotState.ON_EMPTY, result1)

        val result2 = HotspotNotificationParser.parseNotification(
            notificationTitle = "Phát sóng Wifi & Chia sẻ mạng",
            notificationContent = "Chưa có thiết bị kết nối"
        )
        assertEquals(HotspotState.ON_EMPTY, result2)
    }

    @Test
    fun testParseVietnameseNotifications_OnConnected() {
        val result1 = HotspotNotificationParser.parseNotification(
            notificationTitle = "Điểm truy cập cá nhân & chia sẻ mạng",
            notificationContent = "2 thiết bị đã kết nối"
        )
        assertEquals(HotspotState.ON_CONNECTED(2), result1)

        val result2 = HotspotNotificationParser.parseNotification(
            notificationTitle = "Chia sẻ kết nối Internet (Hotspot)",
            notificationContent = "5 thiết bị kết nối"
        )
        assertEquals(HotspotState.ON_CONNECTED(5), result2)
    }

    @Test
    fun testParseNonHotspotNotification() {
        val result = HotspotNotificationParser.parseNotification(
            notificationTitle = "Cập nhật ứng dụng",
            notificationContent = "Tải xuống hoàn tất"
        )
        assertNull(result)
    }
}
