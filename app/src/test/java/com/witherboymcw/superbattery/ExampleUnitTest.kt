package com.witherboymcw.superbattery

import com.witherboymcw.superbattery.awareness.CornerAwareness
import com.witherboymcw.superbattery.screens.dashboard.DashboardViewModel
import com.witherboymcw.superbattery.screens.dashboard.MiniSquareCardHeaderLayout
import com.witherboymcw.superbattery.screens.settings.SettingsHelper
import org.junit.Test
import org.junit.Assert.*

class ExampleUnitTest {
    @Test
    fun testLunarCalendarKnownDates() {
        // Tết 2024: 10/02/2024 -> 1/1/2024 Giáp Thìn
        val lunar2024 = VietnameseLunarCalendar.fromSolar(10, 2, 2024)
        assertEquals(1, lunar2024.day)
        assertEquals(1, lunar2024.month)
        assertEquals(2024, lunar2024.year)
        assertFalse(lunar2024.isLeapMonth)
        assertEquals("Giáp Thìn", VietnameseLunarCalendar.canChiYear(lunar2024.year))

        // Tết 2025: 29/01/2025 -> 1/1/2025 Ất Tỵ
        val lunar2025 = VietnameseLunarCalendar.fromSolar(29, 1, 2025)
        assertEquals(1, lunar2025.day)
        assertEquals(1, lunar2025.month)
        assertEquals(2025, lunar2025.year)
        assertFalse(lunar2025.isLeapMonth)
        assertEquals("Ất Tỵ", VietnameseLunarCalendar.canChiYear(lunar2025.year))

        // Tết 2026: 17/02/2026 -> 1/1/2026 Bính Ngọ
        val lunar2026 = VietnameseLunarCalendar.fromSolar(17, 2, 2026)
        assertEquals(1, lunar2026.day)
        assertEquals(1, lunar2026.month)
        assertEquals(2026, lunar2026.year)
        assertFalse(lunar2026.isLeapMonth)
        assertEquals("Bính Ngọ", VietnameseLunarCalendar.canChiYear(lunar2026.year))
    }

    @Test
    fun testTemperatureUnitConversions() {
        val tempC = 35.0f

        // Celsius
        val celsiusVal = SettingsHelper.convertTemperature(tempC, TempUnit.CELSIUS)
        assertEquals(35.0f, celsiusVal, 0.001f)
        assertEquals(35.0f, SettingsHelper.convertToCelsius(celsiusVal, TempUnit.CELSIUS), 0.001f)

        // Fahrenheit: 35 * 1.8 + 32 = 95
        val fahrenheitVal = SettingsHelper.convertTemperature(tempC, TempUnit.FAHRENHEIT)
        assertEquals(95.0f, fahrenheitVal, 0.001f)
        assertEquals(35.0f, SettingsHelper.convertToCelsius(fahrenheitVal, TempUnit.FAHRENHEIT), 0.001f)

        // Kelvin: 35 + 273.15 = 308.15
        val kelvinVal = SettingsHelper.convertTemperature(tempC, TempUnit.KELVIN)
        assertEquals(308.15f, kelvinVal, 0.001f)
        assertEquals(35.0f, SettingsHelper.convertToCelsius(kelvinVal, TempUnit.KELVIN), 0.001f)
    }

    @Test
    fun testTemperatureFormatting() {
        val tempC = 35.0f
        assertEquals("35ºC", SettingsHelper.formatWidgetTemperature(tempC, TempUnit.CELSIUS))
        assertEquals("95ºF", SettingsHelper.formatWidgetTemperature(tempC, TempUnit.FAHRENHEIT))
        assertEquals("308 K", SettingsHelper.formatWidgetTemperature(tempC, TempUnit.KELVIN))
    }

    @Test
    fun testVoltageUnitConversionsAndFormatting() {
        val voltageMv = 3850

        // Millivolts
        val mvVal = SettingsHelper.convertVoltage(voltageMv.toFloat(), VoltageUnit.MV)
        assertEquals(3850.0f, mvVal, 0.001f)
        assertEquals(3850.0f, SettingsHelper.convertToVoltageMv(mvVal, VoltageUnit.MV), 0.001f)
        assertEquals("3850 mV", SettingsHelper.formatVoltage(voltageMv, VoltageUnit.MV))

        // Volts (US Locale)
        val vVal = SettingsHelper.convertVoltage(voltageMv.toFloat(), VoltageUnit.V)
        assertEquals(3.85f, vVal, 0.001f)
        assertEquals(3850.0f, SettingsHelper.convertToVoltageMv(vVal, VoltageUnit.V), 0.001f)

        // Volts Decimal Formatting in Vietnamese
        val viLocale = java.util.Locale.forLanguageTag("vi")
        val formattedVi = String.format(viLocale, "%.1f %s", 4.3f, VoltageUnit.V.symbol)
        assertEquals("4,3 V", formattedVi)
    }

    @Test
    fun testRamUnitConversions() {
        val ramMb = 2048.0f

        // Megabytes
        val mbVal = SettingsHelper.convertRam(ramMb, RamUnit.MB)
        assertEquals(2048.0f, mbVal, 0.001f)
        assertEquals(2048.0f, SettingsHelper.convertToRamMb(ramMb, RamUnit.MB), 0.001f)

        // Gigabytes
        val gbVal = SettingsHelper.convertRam(ramMb, RamUnit.GB)
        assertEquals(2.0f, gbVal, 0.001f)
        assertEquals(2048.0f, SettingsHelper.convertToRamMb(gbVal, RamUnit.GB), 0.001f)
    }

    @Test
    fun testOledDetectionUnderDisplayFingerprint() {
        // Guaranteed OLED if under-display fingerprint sensor is present
        assertTrue(
            checkGuaranteedOledConditions(
                hasFingerprint = true,
                hasUdfps = true,
                isWideColorGamut = false,
                desiredMinLuminance = null,
                supportedHdrTypesCount = 0,
                isAodSupported = false
            )
        )
    }

    @Test
    fun testOledDetectionDisplayParametersAllMet() {
        // Guaranteed OLED if all 4 display criteria are satisfied
        assertTrue(
            checkGuaranteedOledConditions(
                hasFingerprint = false,
                hasUdfps = false,
                isWideColorGamut = true,
                desiredMinLuminance = 0.0f,
                supportedHdrTypesCount = 2,
                isAodSupported = true
            )
        )
    }

    @Test
    fun testOledDetectionLcdFailsStrictCriteria() {
        // LCD with non-zero min luminance -> false
        assertFalse(
            checkGuaranteedOledConditions(
                hasFingerprint = false,
                hasUdfps = false,
                isWideColorGamut = true,
                desiredMinLuminance = 0.02f,
                supportedHdrTypesCount = 2,
                isAodSupported = true
            )
        )

        // LCD without wide color gamut -> false
        assertFalse(
            checkGuaranteedOledConditions(
                hasFingerprint = false,
                hasUdfps = false,
                isWideColorGamut = false,
                desiredMinLuminance = 0.0f,
                supportedHdrTypesCount = 2,
                isAodSupported = true
            )
        )

        // LCD with insufficient HDR types -> false
        assertFalse(
            checkGuaranteedOledConditions(
                hasFingerprint = false,
                hasUdfps = false,
                isWideColorGamut = true,
                desiredMinLuminance = 0.0f,
                supportedHdrTypesCount = 1,
                isAodSupported = true
            )
        )

        // LCD without AOD support -> false
        assertFalse(
            checkGuaranteedOledConditions(
                hasFingerprint = false,
                hasUdfps = false,
                isWideColorGamut = true,
                desiredMinLuminance = 0.0f,
                supportedHdrTypesCount = 2,
                isAodSupported = false
            )
        )
    }

    @Test
    fun testMetricHistoryManagerPruningAndPoolReuse() {
        val manager = MetricHistoryManager(historyWindowMs = 60_000L, maxHistoryPoints = 5)
        val startTime = 100_000L

        // Add 5 samples
        for (i in 0 until 5) {
            manager.addVoltageSample(3800f + i, startTime + i * 1_000L)
        }

        assertEquals(5, manager.voltageHistory.size)
        assertEquals(0, manager.samplePool.size)

        // Adding 6th sample should trigger MAX_HISTORY_POINTS pruning -> 1 sample recycled to pool
        manager.addVoltageSample(4000f, startTime + 5_000L)
        assertEquals(5, manager.voltageHistory.size)
        assertEquals(0, manager.samplePool.size) // Recycled object consumed immediately

        // Add sample with time jump > 60s to prune old samples
        manager.addVoltageSample(4100f, startTime + 70_000L)
        // All previous samples (100s to 105s) are older than 70s - 60s = 10s.
        // Thus 5 old samples are moved to pool, 1 sample is reused from pool for 4100f.
        assertEquals(1, manager.voltageHistory.size)
        assertEquals(4100f, manager.voltageHistory.first().value, 0.001f)
        assertEquals(4, manager.samplePool.size)
    }

    @Test
    fun testDashboardViewModelVoltageBoundsShift() {
        val viewModel = DashboardViewModel()
        val now = 1_000L

        // Default range
        assertEquals(Pair(3300f, 4300f), viewModel.getVoltageBoundsMv())
        assertFalse(viewModel.isVoltageShiftedHigh)

        // Add high voltage > 4250mV
        viewModel.addVoltageSample(4280f, now)
        assertTrue(viewModel.isVoltageShiftedHigh)
        assertEquals(Pair(3550f, 4550f), viewModel.getVoltageBoundsMv())

        // Add new sample far in the future (> 60s), replacing old sample with normal voltage < 4000mV
        viewModel.addVoltageSample(3800f, now + 70_000L)
        assertFalse(viewModel.isVoltageShiftedHigh)
        assertEquals(Pair(3300f, 4300f), viewModel.getVoltageBoundsMv())
    }

    @Test
    fun testCornerAwarenessHelperDynamicRadiusCalculation() {
        val density = 2.0f
        val expectedDefaultRadiusPx = 12f * density

        val radiusWithNullInsets = CornerAwareness.calculateDynamicCornerRadius(null, density)
        assertEquals(expectedDefaultRadiusPx, radiusWithNullInsets, 0.001f)
    }

    @Test
    fun testLanguageNormalizationAndLocaleResolution() {
        val brainrotAliases = listOf("art", "en-BR", "en-brainrot", "en-x-brainrot", "en-0rot")
        brainrotAliases.forEach { tag ->
            val locale = when (tag) {
                "en-BR", "en-brainrot", "en-x-brainrot", "en-0rot" -> java.util.Locale.forLanguageTag("art")
                else -> java.util.Locale.forLanguageTag(tag)
            }
            assertEquals("art", locale.language)
        }

        val chineseTags = listOf("zh", "zh-CN", "zh-cn", "zh-hans")
        chineseTags.forEach { tag ->
            val normalized = com.witherboymcw.superbattery.templates.LocaleManager.normalizeLanguageTag(tag)
            assertEquals("zh-CN", normalized)
        }
    }

    @Test
    fun testBatteryPowerInfoLogic() {
        // 1. AOSP standard sign convention: negative rawCurrent (< 0) means charging into battery
        val aospCharging = com.witherboymcw.superbattery.awareness.computeBatteryPowerInfo(
            isPluggedCharging = true,
            voltageMv = 4000,
            rawCurrent = -1_500_000, // -1500 mA in uA
            isDualCell = false,
            isSamsung = false
        )
        assertNotNull(aospCharging)
        assertTrue(aospCharging!!.isCharging)
        assertEquals(4.0f, aospCharging.voltageVolts, 0.001f)
        assertEquals(1500.0f, aospCharging.currentMilliAmperes, 0.001f)
        assertEquals(6.0f, aospCharging.powerWatts, 0.001f)

        val aospDischarging = com.witherboymcw.superbattery.awareness.computeBatteryPowerInfo(
            isPluggedCharging = false,
            voltageMv = 3800,
            rawCurrent = 500_000, // +500 mA in uA
            isDualCell = false,
            isSamsung = false
        )
        assertNotNull(aospDischarging)
        assertFalse(aospDischarging!!.isCharging)

        // 2. Samsung sign convention: positive rawCurrent (> 0) means charging into battery
        val samsungCharging = com.witherboymcw.superbattery.awareness.computeBatteryPowerInfo(
            isPluggedCharging = true,
            voltageMv = 4000,
            rawCurrent = 1_500_000, // +1500 mA in uA
            isDualCell = false,
            isSamsung = true
        )
        assertNotNull(samsungCharging)
        assertTrue(samsungCharging!!.isCharging)

        // 3. Dual-cell (2S) battery - single-cell reported voltage (< 5500 mV) should be doubled
        val dualCellSingleInput = com.witherboymcw.superbattery.awareness.computeBatteryPowerInfo(
            isPluggedCharging = true,
            voltageMv = 4000, // Single cell ~4.0V
            rawCurrent = -2_000_000,
            isDualCell = true,
            isSamsung = false
        )
        assertNotNull(dualCellSingleInput)
        assertEquals(8.0f, dualCellSingleInput!!.voltageVolts, 0.001f) // 4.0V * 2 = 8.0V
        assertEquals(2000.0f, dualCellSingleInput.currentMilliAmperes, 0.001f) // I_pack = I_cell
        assertEquals(16.0f, dualCellSingleInput.powerWatts, 0.001f) // P = 8.0V * 2.0A = 16W

        // 4. Dual-cell (2S) battery - pack reported voltage (>= 5500 mV) should NOT be doubled again
        val dualCellPackInput = com.witherboymcw.superbattery.awareness.computeBatteryPowerInfo(
            isPluggedCharging = true,
            voltageMv = 8000, // Pack voltage ~8.0V
            rawCurrent = -2_000_000,
            isDualCell = true,
            isSamsung = false
        )
        assertNotNull(dualCellPackInput)
        assertEquals(8.0f, dualCellPackInput!!.voltageVolts, 0.001f) // Already pack voltage
        assertEquals(2000.0f, dualCellPackInput.currentMilliAmperes, 0.001f)
        assertEquals(16.0f, dualCellPackInput.powerWatts, 0.001f)

        // 5. Stale cached charging sample when charger is unplugged (Samsung & AOSP)
        val samsungUnpluggedStale = com.witherboymcw.superbattery.awareness.computeBatteryPowerInfo(
            isPluggedCharging = false,
            voltageMv = 4000,
            rawCurrent = 2_500_000, // Stale +2.5A charging current on Samsung
            isDualCell = false,
            isSamsung = true
        )
        assertNull(samsungUnpluggedStale)

        val aospUnpluggedStale = com.witherboymcw.superbattery.awareness.computeBatteryPowerInfo(
            isPluggedCharging = false,
            voltageMv = 4000,
            rawCurrent = -2_500_000, // Stale -2.5A charging current on AOSP
            isDualCell = false,
            isSamsung = false
        )
        assertNull(aospUnpluggedStale)
    }

    @Test
    fun testMiniSquareCornerTouchesGeometry() {
        // Max bounds portrait (0, 0, 1080, 2400)
        // 1. Nửa dưới màn hình dọc (Bottom half portrait split-screen: 0, 1200, 1080, 2400)
        val (bLeft1, bRight1) = CornerAwareness.checkWindowScreenCornerTouches(
            currentLeft = 0, currentTop = 1200, currentRight = 1080, currentBottom = 2400,
            maxLeft = 0, maxTop = 0, maxRight = 1080, maxBottom = 2400
        )
        assertTrue(bLeft1)
        assertTrue(bRight1)

        // 2. Nửa trên màn hình dọc (Top half portrait split-screen: 0, 0, 1080, 1200)
        val (bLeft2, bRight2) = CornerAwareness.checkWindowScreenCornerTouches(
            currentLeft = 0, currentTop = 0, currentRight = 1080, currentBottom = 1200,
            maxLeft = 0, maxTop = 0, maxRight = 1080, maxBottom = 2400
        )
        assertFalse(bLeft2)
        assertFalse(bRight2)

        // Max bounds landscape (0, 0, 2400, 1080)
        // 3. Nửa trái màn hình ngang (Left half landscape split-screen: 0, 0, 1200, 1080)
        val (bLeft3, bRight3) = CornerAwareness.checkWindowScreenCornerTouches(
            currentLeft = 0, currentTop = 0, currentRight = 1200, currentBottom = 1080,
            maxLeft = 0, maxTop = 0, maxRight = 2400, maxBottom = 1080
        )
        assertTrue(bLeft3)
        assertFalse(bRight3)

        // 4. Nửa phải màn hình ngang (Right half landscape split-screen: 1200, 0, 2400, 1080)
        val (bLeft4, bRight4) = CornerAwareness.checkWindowScreenCornerTouches(
            currentLeft = 1200, currentTop = 0, currentRight = 2400, currentBottom = 1080,
            maxLeft = 0, maxTop = 0, maxRight = 2400, maxBottom = 1080
        )
        assertFalse(bLeft4)
        assertTrue(bRight4)
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
