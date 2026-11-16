package com.witherboymcw.superbattery.awareness

import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Test

class ChinAwarenessTest {

    @Test
    fun testMiniSquareChinPaddingCalculation() {
        val density = 2.0f
        val resultTouchesTop = ChinAwareness.calculateChinPadding(
            density = density,
            rotation = Surface.ROTATION_0,
            level = 0,
            isNight = true,
            savedTheme = "dark",
            isFoldable = true,
            isMiniSquare = true,
            touchesTop = true
        )

        // 12dp * 2 = 24px for left, right, bottom; 20dp * 2 = 40px for top when touchesTop is true
        assertEquals(24, resultTouchesTop.dashboardPadding.left)
        assertEquals(40, resultTouchesTop.dashboardPadding.top)
        assertEquals(24, resultTouchesTop.dashboardPadding.right)
        assertEquals(24, resultTouchesTop.dashboardPadding.bottom)

        val resultNotTouchesTop = ChinAwareness.calculateChinPadding(
            density = density,
            rotation = Surface.ROTATION_0,
            level = 0,
            isNight = true,
            savedTheme = "dark",
            isFoldable = true,
            isMiniSquare = true,
            touchesTop = false
        )

        // 12dp * 2 = 24px for left, right, bottom; 8dp * 2 = 16px for top when touchesTop is false
        assertEquals(24, resultNotTouchesTop.dashboardPadding.left)
        assertEquals(16, resultNotTouchesTop.dashboardPadding.top)
        assertEquals(24, resultNotTouchesTop.dashboardPadding.right)
        assertEquals(24, resultNotTouchesTop.dashboardPadding.bottom)
    }
}
