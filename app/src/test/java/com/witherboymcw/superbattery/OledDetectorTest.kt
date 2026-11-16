package com.witherboymcw.superbattery

import org.junit.Test
import org.junit.Assert.*

class OledDetectorTest {
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
}
