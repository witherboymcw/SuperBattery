/*
 * Thuật toán tính Âm lịch Việt Nam (Vietnamese Lunar Calendar)
 * Tác giả thuật toán gốc: Hồ Ngọc Đức (Ho Ngoc Duc)
 * Mã nguồn được chuyển đổi và sử dụng theo các điều khoản sử dụng
 * thuật toán công cộng của tác giả cho mục đích phi thương mại.
 */

package com.witherboymcw.superbattery

import kotlin.math.floor
import kotlin.math.sin
internal object VietnameseLunarCalendar {
    private const val TIME_ZONE = 7
    private const val SYNODIC_MONTH = 29.530588853

    data class LunarDate(
        val day: Int,
        val month: Int,
        val year: Int,
        val isLeapMonth: Boolean
    )

    fun fromSolar(day: Int, month: Int, year: Int): LunarDate {
        val dayNumber = julianDay(day, month, year)
        val k = ((dayNumber - 2415021.076998695) / SYNODIC_MONTH).toInt()
        var monthStart = newMoonDay(k + 1)
        if (monthStart > dayNumber) monthStart = newMoonDay(k)

        var lunarYear: Int
        var month11 = lunarMonth11(year)
        var nextMonth11 = month11
        if (month11 >= monthStart) {
            lunarYear = year
            month11 = lunarMonth11(year - 1)
        } else {
            lunarYear = year + 1
            nextMonth11 = lunarMonth11(year + 1)
        }

        val lunarDay = dayNumber - monthStart + 1
        val diff = (monthStart - month11) / 29
        var lunarMonth = diff + 11
        var isLeapMonth = false
        if (nextMonth11 - month11 > 365) {
            val leapMonthDiff = leapMonthOffset(month11)
            if (diff >= leapMonthDiff) {
                lunarMonth = diff + 10
                isLeapMonth = diff == leapMonthDiff
            }
        }
        if (lunarMonth > 12) lunarMonth -= 12
        if (lunarMonth >= 11 && diff < 4) lunarYear--
        return LunarDate(lunarDay, lunarMonth, lunarYear, isLeapMonth)
    }

    private val BRANCHES = arrayOf(
        "Thân", "Dậu", "Tuất", "Hợi", "Tý", "Sửu",
        "Dần", "Mão", "Thìn", "Tỵ", "Ngọ", "Mùi"
    )
    private val STEMS = arrayOf("Canh", "Tân", "Nhâm", "Quý", "Giáp", "Ất", "Bính", "Đinh", "Mậu", "Kỷ")

    fun canChiYear(year: Int): String {
        return "${STEMS[Math.floorMod(year, STEMS.size)]} ${BRANCHES[Math.floorMod(year, BRANCHES.size)]}"
    }

    private fun julianDay(day: Int, month: Int, year: Int): Int {
        val a = (14 - month) / 12
        val y = year + 4800 - a
        val m = month + 12 * a - 3
        var jd = day + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045
        if (jd < 2299161) jd = day + (153 * m + 2) / 5 + 365 * y + y / 4 - 32083
        return jd
    }

    // TODO: Fix lunar drift around year 2101. If you're reading this, good luck!

    private fun newMoonDay(k: Int): Int {
        val t = k / 1236.85
        val t2 = t * t
        val t3 = t2 * t
        val degreesToRadians = Math.PI / 180.0
        var jd = 2415020.75933 + 29.53058868 * k + 0.0001178 * t2 - 0.000000155 * t3
        jd += 0.00033 * sin((166.56 + 132.87 * t - 0.009173 * t2) * degreesToRadians)
        val sunMeanAnomaly = 359.2242 + 29.10535608 * k - 0.0000333 * t2 - 0.00000347 * t3
        val moonMeanAnomaly = 306.0253 + 385.81691806 * k + 0.0107306 * t2 + 0.00001236 * t3
        val moonLatitudeArgument = 21.2964 + 390.67050646 * k - 0.0016528 * t2 - 0.00000239 * t3
        var correction = (0.1734 - 0.000393 * t) * sin(sunMeanAnomaly * degreesToRadians) +
            0.0021 * sin(2 * degreesToRadians * sunMeanAnomaly)
        correction -= 0.4068 * sin(moonMeanAnomaly * degreesToRadians) -
            0.0161 * sin(2 * degreesToRadians * moonMeanAnomaly)
        correction -= 0.0004 * sin(3 * degreesToRadians * moonMeanAnomaly)
        correction += 0.0104 * sin(2 * degreesToRadians * moonLatitudeArgument) -
            0.0051 * sin(degreesToRadians * (sunMeanAnomaly + moonMeanAnomaly))
        correction -= 0.0074 * sin(degreesToRadians * (sunMeanAnomaly - moonMeanAnomaly)) -
            0.0004 * sin(degreesToRadians * (2 * moonLatitudeArgument + sunMeanAnomaly))
        correction -= 0.0004 * sin(degreesToRadians * (2 * moonLatitudeArgument - sunMeanAnomaly)) +
            0.0006 * sin(degreesToRadians * (2 * moonLatitudeArgument + moonMeanAnomaly))
        correction += 0.0010 * sin(degreesToRadians * (2 * moonLatitudeArgument - moonMeanAnomaly)) +
            0.0005 * sin(degreesToRadians * (2 * moonMeanAnomaly + sunMeanAnomaly))

        val deltaT = if (t < -11) {
            0.001 + 0.000839 * t + 0.0002261 * t2 - 0.00000845 * t3 - 0.000000081 * t * t3
        } else {
            -0.000278 + 0.000265 * t + 0.000262 * t2
        }
        return floor(jd + correction - deltaT + 0.5 + TIME_ZONE / 24.0).toInt()
    }

    private fun lunarMonth11(year: Int): Int {
        val dayNumber = julianDay(31, 12, year) - 2415021
        val k = (dayNumber / SYNODIC_MONTH).toInt()
        var newMoon = newMoonDay(k)
        if (sunLongitude(newMoon) >= 9) newMoon = newMoonDay(k - 1)
        return newMoon
    }

    private fun sunLongitude(julianDay: Int): Int {
        val t = (julianDay - 2451545.5 - TIME_ZONE / 24.0) / 36525.0
        val t2 = t * t
        val degreesToRadians = Math.PI / 180.0
        val meanAnomaly = 357.52910 + 35999.05030 * t - 0.0001559 * t2 - 0.00000048 * t * t2
        val meanLongitude = 280.46645 + 36000.76983 * t + 0.0003032 * t2
        val equationOfCenter =
            (1.914600 - 0.004817 * t - 0.000014 * t2) * sin(degreesToRadians * meanAnomaly) +
                (0.019993 - 0.000101 * t) * sin(2 * degreesToRadians * meanAnomaly) +
                0.000290 * sin(3 * degreesToRadians * meanAnomaly)
        var longitude = (meanLongitude + equationOfCenter) * degreesToRadians
        longitude -= Math.PI * 2 * floor(longitude / (Math.PI * 2))
        return floor(longitude / Math.PI * 6).toInt()
    }

    private fun leapMonthOffset(month11: Int): Int {
        val k = ((month11 - 2415021.076998695) / SYNODIC_MONTH + 0.5).toInt()
        var lastArc = 0
        var i = 1
        var arc = sunLongitude(newMoonDay(k + i))
        do {
            lastArc = arc
            i++
            arc = sunLongitude(newMoonDay(k + i))
        } while (arc != lastArc && i < 14)
        return i - 1
    }
}
