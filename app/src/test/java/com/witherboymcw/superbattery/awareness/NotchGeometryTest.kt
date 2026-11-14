package com.witherboymcw.superbattery.awareness

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class NotchGeometryTest {

    private val margin = 16f
    private val clearance = 12f
    private val gap = 4f

    @Test
    fun testComputeHomeHeaderShift_NonePosition() {
        val totalWidth = 1080f
        val cutoutInfo = NotchGeometry.CutoutInfo(NotchGeometry.CutoutPosition.NONE)

        val leftControlsWidth = 100f
        val settingsButtonWidth = 40f
        val titleWidth = 200f

        val defaultLeftControlsX = 16f
        val defaultSettingsX = 120f
        val defaultTitleCenterX = 540f

        val result = NotchGeometry.computeHeaderShift(
            headerType = NotchGeometry.HeaderType.HOME,
            totalWidth = totalWidth,
            cutoutInfo = cutoutInfo,
            leftControlsWidth = leftControlsWidth,
            settingsButtonWidth = settingsButtonWidth,
            gap = gap,
            titleWidth = titleWidth,
            defaultLeftControlsX = defaultLeftControlsX,
            defaultSettingsX = defaultSettingsX,
            defaultTitleCenterX = defaultTitleCenterX,
            margin = margin,
            clearance = clearance
        )

        assertNotNull(result)
        assertEquals(0f, result.leftControlsTranslationX, 0.01f)
        assertEquals(0f, result.rightControlsTranslationX, 0.01f)
    }

    @Test
    fun testComputeHomeHeaderShift_CenterPosition() {
        val totalWidth = 1080f
        val cutoutInfo = NotchGeometry.CutoutInfo(NotchGeometry.CutoutPosition.CENTER, rectLeft = 490f, rectRight = 590f)

        val leftControlsWidth = 100f
        val settingsButtonWidth = 40f
        val titleWidth = 200f

        val defaultLeftControlsX = 16f
        val defaultSettingsX = 120f
        val defaultTitleCenterX = 540f

        val result = NotchGeometry.computeHeaderShift(
            headerType = NotchGeometry.HeaderType.HOME,
            totalWidth = totalWidth,
            cutoutInfo = cutoutInfo,
            leftControlsWidth = leftControlsWidth,
            settingsButtonWidth = settingsButtonWidth,
            gap = gap,
            titleWidth = titleWidth,
            defaultLeftControlsX = defaultLeftControlsX,
            defaultSettingsX = defaultSettingsX,
            defaultTitleCenterX = defaultTitleCenterX,
            margin = margin,
            clearance = clearance
        )

        assertNotNull(result)
        val expectedTitleShift = 810f - defaultTitleCenterX
        assertEquals(expectedTitleShift, result.titleTranslationX, 0.01f)
    }

    @Test
    fun testComputeHomeHeaderShift_TopLeftPosition() {
        val totalWidth = 1080f
        val cutoutInfo = NotchGeometry.CutoutInfo(NotchGeometry.CutoutPosition.TOP_LEFT, rectLeft = 0f, rectRight = 200f)

        val leftControlsWidth = 100f
        val settingsButtonWidth = 40f
        val titleWidth = 200f

        val defaultLeftControlsX = 16f
        val defaultSettingsX = 120f
        val defaultTitleCenterX = 540f

        val result = NotchGeometry.computeHeaderShift(
            headerType = NotchGeometry.HeaderType.HOME,
            totalWidth = totalWidth,
            cutoutInfo = cutoutInfo,
            leftControlsWidth = leftControlsWidth,
            settingsButtonWidth = settingsButtonWidth,
            gap = gap,
            titleWidth = titleWidth,
            defaultLeftControlsX = defaultLeftControlsX,
            defaultSettingsX = defaultSettingsX,
            defaultTitleCenterX = defaultTitleCenterX,
            margin = margin,
            clearance = clearance
        )

        assertNotNull(result)
        assertEquals(722f, result.leftControlsTranslationX, 0.01f)
        assertEquals(722f, result.rightControlsTranslationX, 0.01f)
        assertEquals(-168f, result.titleTranslationX, 0.01f)
    }

    @Test
    fun testComputeHomeHeaderShift_TopRightPosition() {
        val totalWidth = 1080f
        val cutoutInfo = NotchGeometry.CutoutInfo(NotchGeometry.CutoutPosition.TOP_RIGHT, rectLeft = 880f, rectRight = 1080f)

        val leftControlsWidth = 100f
        val settingsButtonWidth = 40f
        val titleWidth = 200f

        val defaultLeftControlsX = 16f
        val defaultSettingsX = 120f
        val defaultTitleCenterX = 540f

        val result = NotchGeometry.computeHeaderShift(
            headerType = NotchGeometry.HeaderType.HOME,
            totalWidth = totalWidth,
            cutoutInfo = cutoutInfo,
            leftControlsWidth = leftControlsWidth,
            settingsButtonWidth = settingsButtonWidth,
            gap = gap,
            titleWidth = titleWidth,
            defaultLeftControlsX = defaultLeftControlsX,
            defaultSettingsX = defaultSettingsX,
            defaultTitleCenterX = defaultTitleCenterX,
            margin = margin,
            clearance = clearance
        )

        assertNotNull(result)
        assertEquals(0f, result.leftControlsTranslationX, 0.01f)
        assertEquals(0f, result.rightControlsTranslationX, 0.01f)
        assertEquals(-20f, result.titleTranslationX, 0.01f)
    }

    @Test
    fun testComputeSettingsHeaderShift_NoneAndTopRightPosition() {
        val totalWidth = 1080f
        val cutoutNone = NotchGeometry.CutoutInfo(NotchGeometry.CutoutPosition.NONE)
        val cutoutTopRight = NotchGeometry.CutoutInfo(NotchGeometry.CutoutPosition.TOP_RIGHT, rectLeft = 880f, rectRight = 1080f)

        val resultNone = NotchGeometry.computeHeaderShift(
            headerType = NotchGeometry.HeaderType.SETTINGS,
            totalWidth = totalWidth,
            cutoutInfo = cutoutNone
        )
        val resultTopRight = NotchGeometry.computeHeaderShift(
            headerType = NotchGeometry.HeaderType.SETTINGS,
            totalWidth = totalWidth,
            cutoutInfo = cutoutTopRight
        )

        assertEquals(0f, resultNone.leftControlsTranslationX, 0.01f)
        assertEquals(0f, resultNone.titleTranslationX, 0.01f)
        assertEquals(0f, resultTopRight.leftControlsTranslationX, 0.01f)
        assertEquals(0f, resultTopRight.titleTranslationX, 0.01f)
    }

    @Test
    fun testComputeSettingsHeaderShift_CenterPosition() {
        val totalWidth = 1080f
        val cutoutInfo = NotchGeometry.CutoutInfo(NotchGeometry.CutoutPosition.CENTER, rectLeft = 490f, rectRight = 590f)

        val defaultTitleCenterX = 540f

        val result = NotchGeometry.computeHeaderShift(
            headerType = NotchGeometry.HeaderType.SETTINGS,
            totalWidth = totalWidth,
            cutoutInfo = cutoutInfo,
            defaultTitleCenterX = defaultTitleCenterX
        )

        assertNotNull(result)
        assertEquals(0f, result.leftControlsTranslationX, 0.01f)
        assertEquals(270f, result.titleTranslationX, 0.01f)
    }

    @Test
    fun testComputeSettingsHeaderShift_TopLeftPosition() {
        val totalWidth = 1080f
        val cutoutInfo = NotchGeometry.CutoutInfo(NotchGeometry.CutoutPosition.TOP_LEFT, rectLeft = 0f, rectRight = 200f)

        val backWidth = 48f
        val titleWidth = 180f
        val defaultBackLeft = 16f
        val defaultTitleCenterX = 540f

        val result = NotchGeometry.computeHeaderShift(
            headerType = NotchGeometry.HeaderType.SETTINGS,
            totalWidth = totalWidth,
            cutoutInfo = cutoutInfo,
            leftControlsWidth = backWidth,
            titleWidth = titleWidth,
            defaultBackLeft = defaultBackLeft,
            defaultTitleCenterX = defaultTitleCenterX,
            margin = margin,
            clearance = clearance
        )

        assertNotNull(result)
        assertEquals(200f, result.leftControlsTranslationX, 0.01f)
    }

    @Test
    fun testComputeHotspotHeaderShift_NoneAndTopLeftPosition() {
        val totalWidth = 1080f
        val cutoutNone = NotchGeometry.CutoutInfo(NotchGeometry.CutoutPosition.NONE)
        val cutoutTopLeft = NotchGeometry.CutoutInfo(NotchGeometry.CutoutPosition.TOP_LEFT, rectLeft = 0f, rectRight = 200f)

        val resultNone = NotchGeometry.computeHeaderShift(
            headerType = NotchGeometry.HeaderType.HOTSPOT,
            totalWidth = totalWidth,
            cutoutInfo = cutoutNone
        )
        val resultTopLeft = NotchGeometry.computeHeaderShift(
            headerType = NotchGeometry.HeaderType.HOTSPOT,
            totalWidth = totalWidth,
            cutoutInfo = cutoutTopLeft
        )

        assertEquals(0f, resultNone.rightControlsTranslationX, 0.01f)
        assertEquals(0f, resultNone.titleTranslationX, 0.01f)
        assertEquals(0f, resultTopLeft.rightControlsTranslationX, 0.01f)
        assertEquals(0f, resultTopLeft.titleTranslationX, 0.01f)
    }

    @Test
    fun testComputeHotspotHeaderShift_CenterPosition() {
        val totalWidth = 1080f
        val cutoutInfo = NotchGeometry.CutoutInfo(NotchGeometry.CutoutPosition.CENTER, rectLeft = 490f, rectRight = 590f)

        val defaultTitleCenterX = 540f

        val result = NotchGeometry.computeHeaderShift(
            headerType = NotchGeometry.HeaderType.HOTSPOT,
            totalWidth = totalWidth,
            cutoutInfo = cutoutInfo,
            defaultTitleCenterX = defaultTitleCenterX
        )

        assertNotNull(result)
        assertEquals(0f, result.rightControlsTranslationX, 0.01f)
        assertEquals(-270f, result.titleTranslationX, 0.01f)
    }

    @Test
    fun testComputeHotspotHeaderShift_TopRightPosition() {
        val totalWidth = 1080f
        val cutoutInfo = NotchGeometry.CutoutInfo(NotchGeometry.CutoutPosition.TOP_RIGHT, rectLeft = 880f, rectRight = 1080f)

        val backWidth = 48f
        val titleWidth = 180f
        val defaultBackLeft = 1016f
        val defaultTitleCenterX = 540f

        val result = NotchGeometry.computeHeaderShift(
            headerType = NotchGeometry.HeaderType.HOTSPOT,
            totalWidth = totalWidth,
            cutoutInfo = cutoutInfo,
            leftControlsWidth = backWidth,
            titleWidth = titleWidth,
            defaultBackLeft = defaultBackLeft,
            defaultTitleCenterX = defaultTitleCenterX,
            margin = margin,
            clearance = clearance
        )

        assertNotNull(result)
        assertEquals(-200f, result.rightControlsTranslationX, 0.01f)
    }

    @Test
    fun testComputeHomeHeaderShift_CenterPosition_Near35PercentBoundary_NoException() {
        val totalWidth = 1080f
        // cX near 35% boundary (0.35 * 1080 = 378f), cutoutLeft = 306f
        val cutoutInfo = NotchGeometry.CutoutInfo(NotchGeometry.CutoutPosition.CENTER, rectLeft = 306f, rectRight = 450f)

        val leftControlsWidth = 300f
        val settingsButtonWidth = 50f
        val titleWidth = 200f

        val defaultLeftControlsX = 16f
        val defaultSettingsX = 320f
        val defaultTitleCenterX = 540f

        // cutoutLeft (306f) - groupWidth (354f) = -48f < margin (16f)
        val result = NotchGeometry.computeHeaderShift(
            headerType = NotchGeometry.HeaderType.HOME,
            totalWidth = totalWidth,
            cutoutInfo = cutoutInfo,
            leftControlsWidth = leftControlsWidth,
            settingsButtonWidth = settingsButtonWidth,
            gap = gap,
            titleWidth = titleWidth,
            defaultLeftControlsX = defaultLeftControlsX,
            defaultSettingsX = defaultSettingsX,
            defaultTitleCenterX = defaultTitleCenterX,
            margin = margin,
            clearance = clearance
        )

        assertNotNull(result)
        val expectedLeftTransX = margin - defaultLeftControlsX
        assertEquals(expectedLeftTransX, result.leftControlsTranslationX, 0.01f)
    }

    @Test
    fun testNotchGeometryConstantsAndEnums() {
        assertEquals(0, NotchGeometry.MODE_SYSTEM)
        assertEquals(1, NotchGeometry.MODE_FORCE)
        assertEquals(2, NotchGeometry.MODE_IGNORE)

        assertEquals(24f, NotchGeometry.DEFAULT_NOTCH_RADIUS, 0.001f)
        assertEquals(0f, NotchGeometry.DEFAULT_NOTCH_PILL_WIDTH, 0.001f)

        assertEquals(4, NotchGeometry.CutoutPosition.entries.size)
        assertEquals(3, NotchGeometry.HeaderType.entries.size)
    }

    @Test
    fun testGetCutoutInfo_LandscapeWithLiveCutoutOverride() {
        val context = mock<Context>()
        val rootViewGroup = mock<View>()
        val resources = mock<Resources>()
        val configuration = Configuration().apply {
            orientation = Configuration.ORIENTATION_LANDSCAPE
        }

        whenever(context.resources).thenReturn(resources)
        whenever(resources.configuration).thenReturn(configuration)
        whenever(rootViewGroup.context).thenReturn(context)

        val liveCutoutOverride = NotchGeometry.CutoutInfo(
            position = NotchGeometry.CutoutPosition.CENTER,
            rectLeft = 400f,
            rectRight = 600f
        )

        val result = NotchGeometry.getCutoutInfo(
            context = context,
            rootViewGroup = rootViewGroup,
            liveCutoutOverride = liveCutoutOverride
        )

        assertEquals(NotchGeometry.CutoutPosition.NONE, result.position)
        assertEquals(0f, result.rectLeft, 0.001f)
        assertEquals(0f, result.rectRight, 0.001f)
    }

    @Test
    fun testGetCutoutInfo_PortraitWithLiveCutoutOverride() {
        val context = mock<Context>()
        val rootViewGroup = mock<View>()
        val resources = mock<Resources>()
        val configuration = Configuration().apply {
            orientation = Configuration.ORIENTATION_PORTRAIT
        }

        whenever(context.resources).thenReturn(resources)
        whenever(resources.configuration).thenReturn(configuration)
        whenever(rootViewGroup.context).thenReturn(context)

        val liveCutoutOverride = NotchGeometry.CutoutInfo(
            position = NotchGeometry.CutoutPosition.CENTER,
            rectLeft = 400f,
            rectRight = 600f
        )

        val result = NotchGeometry.getCutoutInfo(
            context = context,
            rootViewGroup = rootViewGroup,
            liveCutoutOverride = liveCutoutOverride
        )

        assertEquals(NotchGeometry.CutoutPosition.CENTER, result.position)
        assertEquals(400f, result.rectLeft, 0.001f)
        assertEquals(600f, result.rectRight, 0.001f)
    }
}
