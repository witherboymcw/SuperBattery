package com.witherboymcw.superbattery.screens.dashboard

import org.junit.Test
import org.junit.Assert.*

class MiniSquareLayoutTest {
    @Test
    fun testMiniSquareCardHeaderLayoutSizingAndSpacingLogic() {
        val measureH3: (Float) -> Int = { testSp -> (24 * (testSp / 14f)).toInt() }

        // 1. Ample height: 100px. h1=24, h2=24. Line 3 fits at default 14sp.
        val state1 = MiniSquareCardHeaderLayout.calculateLayoutState(
            availHeight = 100,
            h1 = 24,
            h2 = 24,
            measureH3ForSp = measureH3,
            density = 2.0f
        )
        assertTrue(state1.line3Fits)
        assertEquals(14f, state1.selectedSp, 0.001f)

        // 2. Constrained height: 85px. h1=24, h2=24. minTotalGaps=16 (8*2).
        // At 14sp: 24+24+24+16=88 > 85.
        // At 12sp: h3=20, 24+24+20+16=84 <= 85. Fits with font size reduction.
        val state2 = MiniSquareCardHeaderLayout.calculateLayoutState(
            availHeight = 85,
            h1 = 24,
            h2 = 24,
            measureH3ForSp = measureH3,
            density = 2.0f
        )
        assertTrue(state2.line3Fits)
        assertTrue(state2.selectedSp < 14f)
        assertTrue(state2.selectedSp >= 9.5f)

        // 3. Extremely small height: 40px. h1=24, h2=24. h1+h2=48 > 40.
        // Line 3 cannot fit even at min font size, so it drops (line3Fits=false).
        val state3 = MiniSquareCardHeaderLayout.calculateLayoutState(
            availHeight = 40,
            h1 = 24,
            h2 = 24,
            measureH3ForSp = measureH3,
            density = 2.0f
        )
        assertFalse(state3.line3Fits)
    }
}
