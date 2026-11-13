package com.witherboymcw.superbattery.widgets

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying BatteryWidget.isDoubleTap time interval threshold (50ms..400ms).
 */
class WidgetTapTest {

    @Test
    fun testSingleTap() {
        val isDoubleTap = BatteryWidget.isDoubleTap(1000L, 0L)
        assertFalse(isDoubleTap)
    }

    @Test
    fun testDoubleTapWithinInterval() {
        // 1200L - 1000L = 200ms (within 50ms..400ms)
        val isDoubleTap = BatteryWidget.isDoubleTap(1200L, 1000L)
        assertTrue(isDoubleTap)
    }

    @Test
    fun testDoubleTapTooSlow() {
        // 1500L - 1000L = 500ms (> 400ms)
        val isDoubleTap = BatteryWidget.isDoubleTap(1500L, 1000L)
        assertFalse(isDoubleTap)
    }

    @Test
    fun testDoubleTapTooFast() {
        // 1020L - 1000L = 20ms (< 50ms)
        val isDoubleTap = BatteryWidget.isDoubleTap(1020L, 1000L)
        assertFalse(isDoubleTap)
    }
}
