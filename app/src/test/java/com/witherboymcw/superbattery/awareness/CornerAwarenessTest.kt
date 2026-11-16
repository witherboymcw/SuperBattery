package com.witherboymcw.superbattery.awareness

import org.junit.Test
import org.junit.Assert.*

class CornerAwarenessTest {
    @Test
    fun testCornerAwarenessHelperDynamicRadiusCalculation() {
        val density = 2.0f
        val expectedDefaultRadiusPx = 12f * density

        val radiusWithNullInsets = CornerAwareness.calculateDynamicCornerRadius(null, density)
        assertEquals(expectedDefaultRadiusPx, radiusWithNullInsets, 0.001f)
    }

    @Test
    fun testMiniSquareTopEdgeTouchRotatedWindowMetrics() {
        // Max bounds in portrait orientation (0, 0, 1080, 2400)
        val maxL = 0
        val maxT = 0
        val maxR = 1080
        val maxB = 2400

        // 1. Top half window in portrait: currentTop = 0 -> touches top edge of screen
        val topHalfPortrait = CornerAwareness.checkWindowScreenTouches(
            currentLeft = 0, currentTop = 0, currentRight = 1080, currentBottom = 1200,
            maxLeft = maxL, maxTop = maxT, maxRight = maxR, maxBottom = maxB
        )
        assertTrue(topHalfPortrait.touchesTopEdge)

        // 2. Bottom half window in portrait: currentTop = 1200 > 0 -> does NOT touch top edge of screen
        val bottomHalfPortrait = CornerAwareness.checkWindowScreenTouches(
            currentLeft = 0, currentTop = 1200, currentRight = 1080, currentBottom = 2400,
            maxLeft = maxL, maxTop = maxT, maxRight = maxR, maxBottom = maxB
        )
        assertFalse(bottomHalfPortrait.touchesTopEdge)

        // Max bounds in landscape orientation (0, 0, 2400, 1080) - WindowMetrics rotates coordinate space
        val landMaxL = 0
        val landMaxT = 0
        val landMaxR = 2400
        val landMaxB = 1080

        // 3. Left half window in landscape: currentTop = 0 -> touches physical top edge of display
        val leftHalfLandscape = CornerAwareness.checkWindowScreenTouches(
            currentLeft = 0, currentTop = 0, currentRight = 1200, currentBottom = 1080,
            maxLeft = landMaxL, maxTop = landMaxT, maxRight = landMaxR, maxBottom = landMaxB
        )
        assertTrue(leftHalfLandscape.touchesTopEdge)

        // 4. Right half window in landscape: currentTop = 0 -> also touches physical top edge of display
        val rightHalfLandscape = CornerAwareness.checkWindowScreenTouches(
            currentLeft = 1200, currentTop = 0, currentRight = 2400, currentBottom = 1080,
            maxLeft = landMaxL, maxTop = landMaxT, maxRight = landMaxR, maxBottom = landMaxB
        )
        assertTrue(rightHalfLandscape.touchesTopEdge)
    }

    @Test
    fun testMiniSquareCornerTouchesGeometry() {
        // Max bounds portrait (0, 0, 1080, 2400)
        // 1. Nửa dưới màn hình dọc (Bottom half portrait split-screen: 0, 1200, 1080, 2400)
        val touches1 = CornerAwareness.checkWindowScreenTouches(
            currentLeft = 0, currentTop = 1200, currentRight = 1080, currentBottom = 2400,
            maxLeft = 0, maxTop = 0, maxRight = 1080, maxBottom = 2400
        )
        assertTrue(touches1.touchesBottomLeft)
        assertTrue(touches1.touchesBottomRight)
        assertFalse(touches1.touchesTop)

        // 2. Nửa trên màn hình dọc (Top half portrait split-screen: 0, 0, 1080, 1200)
        val touches2 = CornerAwareness.checkWindowScreenTouches(
            currentLeft = 0, currentTop = 0, currentRight = 1080, currentBottom = 1200,
            maxLeft = 0, maxTop = 0, maxRight = 1080, maxBottom = 2400
        )
        assertFalse(touches2.touchesBottomLeft)
        assertFalse(touches2.touchesBottomRight)
        assertTrue(touches2.touchesTop)

        // Max bounds landscape (0, 0, 2400, 1080)
        // 3. Nửa trái màn hình ngang (Left half landscape split-screen: 0, 0, 1200, 1080)
        val touches3 = CornerAwareness.checkWindowScreenTouches(
            currentLeft = 0, currentTop = 0, currentRight = 1200, currentBottom = 1080,
            maxLeft = 0, maxTop = 0, maxRight = 2400, maxBottom = 1080
        )
        assertTrue(touches3.touchesBottomLeft)
        assertFalse(touches3.touchesBottomRight)
        assertTrue(touches3.touchesTop)

        // 4. Nửa phải màn hình ngang (Right half landscape split-screen: 1200, 0, 2400, 1080)
        val touches4 = CornerAwareness.checkWindowScreenTouches(
            currentLeft = 1200, currentTop = 0, currentRight = 2400, currentBottom = 1080,
            maxLeft = 0, maxTop = 0, maxRight = 2400, maxBottom = 1080
        )
        assertFalse(touches4.touchesBottomLeft)
        assertTrue(touches4.touchesBottomRight)
        assertTrue(touches4.touchesTop)
    }

    @Test
    fun testMiniSquareAsymmetricCornersApplication() {
        val density = 2.0f
        val defaultRadiusPx = CornerAwareness.DEFAULT_CORNER_RADIUS_DP * density
        val expectedDynamicRadiusPx = 100f - (CornerAwareness.CORNER_OFFSET_DP * density) // 100 - 32 = 68px

        try {
            val field = android.os.Build.VERSION::class.java.getField("SDK_INT")
            field.isAccessible = true
            field.set(null, 33)
        } catch (_: Exception) {}

        val mockInsets = org.mockito.kotlin.mock<androidx.core.view.WindowInsetsCompat>()
        val mockWindowInsets = org.mockito.kotlin.mock<android.view.WindowInsets>()
        val mockCorner = org.mockito.kotlin.mock<android.view.RoundedCorner>()

        org.mockito.kotlin.whenever(mockCorner.radius).thenReturn(100)
        org.mockito.kotlin.whenever(mockWindowInsets.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_LEFT)).thenReturn(mockCorner)
        org.mockito.kotlin.whenever(mockInsets.toWindowInsets()).thenReturn(mockWindowInsets)

        fun createMockCard(): com.google.android.material.card.MaterialCardView {
            val card = org.mockito.kotlin.mock<com.google.android.material.card.MaterialCardView>()
            val emptyShapeModel = com.google.android.material.shape.ShapeAppearanceModel.builder().build()
            org.mockito.kotlin.whenever(card.shapeAppearanceModel).thenReturn(emptyShapeModel)
            return card
        }

        // Scenario 1: Mini Square Mode - Nửa dưới màn hình dọc (touchesBottomLeft = true, touchesBottomRight = true)
        val cardBattery1 = createMockCard()
        val cardNetwork1 = createMockCard()
        val cardVoltage1 = createMockCard()
        val cardRam1 = createMockCard()
        val cardTemp1 = createMockCard()
        val cardGraph1 = createMockCard()

        CornerAwareness.applyAsymmetricCornersToCards(
            cardBattery = cardBattery1,
            cardNetwork = cardNetwork1,
            cardVoltage = cardVoltage1,
            cardRam = cardRam1,
            cardTemp = cardTemp1,
            insets = mockInsets,
            density = density,
            isPortrait = true,
            isMiniSquare = true,
            touchesBottomLeft = true,
            touchesBottomRight = true,
            cardMiniSquareGraph = cardGraph1
        )

        val tempCaptor1 = org.mockito.kotlin.argumentCaptor<com.google.android.material.shape.ShapeAppearanceModel>()
        org.mockito.kotlin.verify(cardTemp1).shapeAppearanceModel = tempCaptor1.capture()
        val tempModel1 = tempCaptor1.lastValue
        assertEquals(defaultRadiusPx, tempModel1.topLeftCornerSize.getCornerSize(android.graphics.RectF()), 0.001f)
        assertEquals(defaultRadiusPx, tempModel1.topRightCornerSize.getCornerSize(android.graphics.RectF()), 0.001f)
        assertEquals(defaultRadiusPx, tempModel1.bottomRightCornerSize.getCornerSize(android.graphics.RectF()), 0.001f)
        assertEquals(expectedDynamicRadiusPx, tempModel1.bottomLeftCornerSize.getCornerSize(android.graphics.RectF()), 0.001f)
        assertTrue(tempModel1.bottomLeftCornerSize.getCornerSize(android.graphics.RectF()) > defaultRadiusPx)

        val graphCaptor1 = org.mockito.kotlin.argumentCaptor<com.google.android.material.shape.ShapeAppearanceModel>()
        org.mockito.kotlin.verify(cardGraph1).shapeAppearanceModel = graphCaptor1.capture()
        val graphModel1 = graphCaptor1.lastValue
        assertEquals(defaultRadiusPx, graphModel1.topLeftCornerSize.getCornerSize(android.graphics.RectF()), 0.001f)
        assertEquals(defaultRadiusPx, graphModel1.topRightCornerSize.getCornerSize(android.graphics.RectF()), 0.001f)
        assertEquals(defaultRadiusPx, graphModel1.bottomLeftCornerSize.getCornerSize(android.graphics.RectF()), 0.001f)
        assertEquals(expectedDynamicRadiusPx, graphModel1.bottomRightCornerSize.getCornerSize(android.graphics.RectF()), 0.001f)
        assertTrue(graphModel1.bottomRightCornerSize.getCornerSize(android.graphics.RectF()) > defaultRadiusPx)

        // Scenario 2: Mini Square Mode - Nửa trên màn hình dọc (touchesBottomLeft = false, touchesBottomRight = false)
        val cardBattery2 = createMockCard()
        val cardNetwork2 = createMockCard()
        val cardVoltage2 = createMockCard()
        val cardRam2 = createMockCard()
        val cardTemp2 = createMockCard()
        val cardGraph2 = createMockCard()

        CornerAwareness.applyAsymmetricCornersToCards(
            cardBattery = cardBattery2,
            cardNetwork = cardNetwork2,
            cardVoltage = cardVoltage2,
            cardRam = cardRam2,
            cardTemp = cardTemp2,
            insets = mockInsets,
            density = density,
            isPortrait = true,
            isMiniSquare = true,
            touchesBottomLeft = false,
            touchesBottomRight = false,
            cardMiniSquareGraph = cardGraph2
        )

        val tempCaptor2 = org.mockito.kotlin.argumentCaptor<com.google.android.material.shape.ShapeAppearanceModel>()
        org.mockito.kotlin.verify(cardTemp2).shapeAppearanceModel = tempCaptor2.capture()
        val tempModel2 = tempCaptor2.lastValue
        assertEquals(defaultRadiusPx, tempModel2.bottomLeftCornerSize.getCornerSize(android.graphics.RectF()), 0.001f)

        val graphCaptor2 = org.mockito.kotlin.argumentCaptor<com.google.android.material.shape.ShapeAppearanceModel>()
        org.mockito.kotlin.verify(cardGraph2).shapeAppearanceModel = graphCaptor2.capture()
        val graphModel2 = graphCaptor2.lastValue
        assertEquals(defaultRadiusPx, graphModel2.bottomRightCornerSize.getCornerSize(android.graphics.RectF()), 0.001f)

        // Scenario 3: Mini Square Mode - Nửa trái màn hình ngang (touchesBottomLeft = true, touchesBottomRight = false)
        val cardBattery3 = createMockCard()
        val cardNetwork3 = createMockCard()
        val cardVoltage3 = createMockCard()
        val cardRam3 = createMockCard()
        val cardTemp3 = createMockCard()
        val cardGraph3 = createMockCard()

        CornerAwareness.applyAsymmetricCornersToCards(
            cardBattery = cardBattery3,
            cardNetwork = cardNetwork3,
            cardVoltage = cardVoltage3,
            cardRam = cardRam3,
            cardTemp = cardTemp3,
            insets = mockInsets,
            density = density,
            isPortrait = false,
            isMiniSquare = true,
            touchesBottomLeft = true,
            touchesBottomRight = false,
            cardMiniSquareGraph = cardGraph3
        )

        val tempCaptor3 = org.mockito.kotlin.argumentCaptor<com.google.android.material.shape.ShapeAppearanceModel>()
        org.mockito.kotlin.verify(cardTemp3).shapeAppearanceModel = tempCaptor3.capture()
        val tempModel3 = tempCaptor3.lastValue
        assertEquals(expectedDynamicRadiusPx, tempModel3.bottomLeftCornerSize.getCornerSize(android.graphics.RectF()), 0.001f)

        val graphCaptor3 = org.mockito.kotlin.argumentCaptor<com.google.android.material.shape.ShapeAppearanceModel>()
        org.mockito.kotlin.verify(cardGraph3).shapeAppearanceModel = graphCaptor3.capture()
        val graphModel3 = graphCaptor3.lastValue
        assertEquals(defaultRadiusPx, graphModel3.bottomRightCornerSize.getCornerSize(android.graphics.RectF()), 0.001f)

        // Scenario 4: Mini Square Mode - Nửa phải màn hình ngang (touchesBottomLeft = false, touchesBottomRight = true)
        val cardBattery4 = createMockCard()
        val cardNetwork4 = createMockCard()
        val cardVoltage4 = createMockCard()
        val cardRam4 = createMockCard()
        val cardTemp4 = createMockCard()
        val cardGraph4 = createMockCard()

        CornerAwareness.applyAsymmetricCornersToCards(
            cardBattery = cardBattery4,
            cardNetwork = cardNetwork4,
            cardVoltage = cardVoltage4,
            cardRam = cardRam4,
            cardTemp = cardTemp4,
            insets = mockInsets,
            density = density,
            isPortrait = false,
            isMiniSquare = true,
            touchesBottomLeft = false,
            touchesBottomRight = true,
            cardMiniSquareGraph = cardGraph4
        )

        val tempCaptor4 = org.mockito.kotlin.argumentCaptor<com.google.android.material.shape.ShapeAppearanceModel>()
        org.mockito.kotlin.verify(cardTemp4).shapeAppearanceModel = tempCaptor4.capture()
        val tempModel4 = tempCaptor4.lastValue
        assertEquals(defaultRadiusPx, tempModel4.bottomLeftCornerSize.getCornerSize(android.graphics.RectF()), 0.001f)

        val graphCaptor4 = org.mockito.kotlin.argumentCaptor<com.google.android.material.shape.ShapeAppearanceModel>()
        org.mockito.kotlin.verify(cardGraph4).shapeAppearanceModel = graphCaptor4.capture()
        val graphModel4 = graphCaptor4.lastValue
        assertEquals(expectedDynamicRadiusPx, graphModel4.bottomRightCornerSize.getCornerSize(android.graphics.RectF()), 0.001f)
    }

    @Test
    fun testNonMiniSquareAsymmetricCornersCompatibility() {
        val density = 2.0f
        val defaultRadiusPx = CornerAwareness.DEFAULT_CORNER_RADIUS_DP * density

        fun createMockCard(): com.google.android.material.card.MaterialCardView {
            val card = org.mockito.kotlin.mock<com.google.android.material.card.MaterialCardView>()
            val emptyShapeModel = com.google.android.material.shape.ShapeAppearanceModel.builder().build()
            org.mockito.kotlin.whenever(card.shapeAppearanceModel).thenReturn(emptyShapeModel)
            return card
        }

        // Standard Portrait mode
        val cardBatteryP = createMockCard()
        val cardNetworkP = createMockCard()
        val cardVoltageP = createMockCard()
        val cardRamP = createMockCard()
        val cardTempP = createMockCard()

        CornerAwareness.applyAsymmetricCornersToCards(
            cardBattery = cardBatteryP,
            cardNetwork = cardNetworkP,
            cardVoltage = cardVoltageP,
            cardRam = cardRamP,
            cardTemp = cardTempP,
            insets = null,
            density = density,
            isPortrait = true,
            isMiniSquare = false
        )

        val tempCaptorP = org.mockito.kotlin.argumentCaptor<com.google.android.material.shape.ShapeAppearanceModel>()
        org.mockito.kotlin.verify(cardTempP).shapeAppearanceModel = tempCaptorP.capture()
        val tempModelP = tempCaptorP.lastValue
        assertEquals(defaultRadiusPx, tempModelP.bottomLeftCornerSize.getCornerSize(android.graphics.RectF()), 0.001f)
        assertEquals(defaultRadiusPx, tempModelP.bottomRightCornerSize.getCornerSize(android.graphics.RectF()), 0.001f)

        // Standard Landscape mode
        val cardBatteryL = createMockCard()
        val cardNetworkL = createMockCard()
        val cardVoltageL = createMockCard()
        val cardRamL = createMockCard()
        val cardTempL = createMockCard()

        CornerAwareness.applyAsymmetricCornersToCards(
            cardBattery = cardBatteryL,
            cardNetwork = cardNetworkL,
            cardVoltage = cardVoltageL,
            cardRam = cardRamL,
            cardTemp = cardTempL,
            insets = null,
            density = density,
            isPortrait = false,
            isMiniSquare = false
        )

        val voltageCaptorL = org.mockito.kotlin.argumentCaptor<com.google.android.material.shape.ShapeAppearanceModel>()
        org.mockito.kotlin.verify(cardVoltageL).shapeAppearanceModel = voltageCaptorL.capture()
        val voltageModelL = voltageCaptorL.lastValue
        assertEquals(defaultRadiusPx, voltageModelL.bottomLeftCornerSize.getCornerSize(android.graphics.RectF()), 0.001f)

        val ramCaptorL = org.mockito.kotlin.argumentCaptor<com.google.android.material.shape.ShapeAppearanceModel>()
        org.mockito.kotlin.verify(cardRamL).shapeAppearanceModel = ramCaptorL.capture()
        val ramModelL = ramCaptorL.lastValue
        assertEquals(defaultRadiusPx, ramModelL.topRightCornerSize.getCornerSize(android.graphics.RectF()), 0.001f)
    }
}
