package com.witherboymcw.superbattery.awareness

import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Test

class ChinAwarenessTest {

    @Test
    fun testMiniSquareChinPaddingCalculation() {
        val density = 2.0f
        val result = ChinAwareness.calculateChinPadding(
            density = density,
            rotation = Surface.ROTATION_0,
            level = 0,
            isNight = true,
            savedTheme = "dark",
            isFoldable = true,
            isMiniSquare = true
        )

        // 12dp * 2 = 24px for left, right, bottom; 8dp * 2 = 16px for top
        assertEquals(24, result.dashboardPadding.left)
        assertEquals(16, result.dashboardPadding.top)
        assertEquals(24, result.dashboardPadding.right)
        assertEquals(24, result.dashboardPadding.bottom)
    }
}
