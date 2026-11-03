package com.witherboymcw.superbattery

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
}
