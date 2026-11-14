package com.witherboymcw.superbattery.screens.hotspot

import com.witherboymcw.superbattery.screens.hotspot.HotspotNotificationListener.Companion.HotspotState

/**
 * Compiled rule for hotspot notification parsing with pre-compiled regexes to avoid per-notification re-compilation.
 */
data class CompiledHotspotRule(
    val oem: String,
    val compiledTitleRegexes: List<Regex>,
    val noDeviceRegex: Regex,
    val connectedRegex: Regex
)

/**
 * Pure notification parser independent of Android Context.
 * Evaluates notification title and body text against pre-compiled hotspot rules.
 */
object HotspotNotificationParser {

    private val REQUIRED_KEYWORDS = listOf(
        "wi-fi",
        "wifi",
        "mạng",
        "mang",
        "internet",
        "phát sóng",
        "phat song",
        "truy cập",
        "truy cap",
        "tether",
        "hotspot"
    )

    val DEFAULT_RULES: List<CompiledHotspotRule> = listOf(
        CompiledHotspotRule(
            oem = "Universal / All Brands",
            compiledTitleRegexes = listOf(
                Regex("(?i)^.*(?:tethering|hotspot|chia\\s+sẻ|chia\\s+se|phát\\s+sóng|phat\\s+song|điểm\\s+truy\\s+cập|diem\\s+truy\\s+cap).*", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            ),
            noDeviceRegex = Regex(
                "\\b(?:no\\s+(?:connected\\s+)?(?:devices?|clients?)|no\\s+(?:devices?|clients?)\\s+connected|0\\s+(?:connected\\s+)?(?:devices?|clients?|thiết\\s+bị|thiet_bi)|0\\s+thiết\\s+bị|không\\s+có\\s+thiết\\s+bị(?:\\s+nào)?(?:\\s+(?:được|duoc|đã|da))?(?:\\s+kết\\s+nối|\\s+ket\\s+noi)?|khong\\s+co\\s+thiet\\s+bi(?:\\s+nao)?(?:\\s+(?:duoc|da))?(?:\\s+ket\\s+noi)?|chưa\\s+có\\s+thiết\\s+bị(?:\\s+nào)?(?:\\s+(?:được|duoc|đã|da))?(?:\\s+kết\\s+nối|\\s+ket\\s+noi)?|chua\\s+co\\s+thiet\\s+bi(?:\\s+nao)?(?:\\s+(?:duoc|da))?(?:\\s+ket\\s+noi)?)\\b",
                RegexOption.IGNORE_CASE
            ),
            connectedRegex = Regex(
                "\\b(\\d+)\\s+(?:connected\\s+)?(?:devices?|clients?|thiết\\s+bị(?:\\s+nào)?(?:\\s+(?:được|duoc|đã|da|đang|dang))?(?:\\s+(?:kết\\s+nối|ket\\s+noi))?|thiet\\s+bi)\\b|\\b(?:connected\\s+)?(?:devices?|clients?|thiết\\s+bị)\\s*[:\\-]?\\s*(\\d+)\\b|\\b(\\d+)\\s+connected\\b",
                RegexOption.IGNORE_CASE
            )
        )
    )

    fun parseNotification(
        notificationTitle: String,
        notificationContent: String,
        rules: List<CompiledHotspotRule> = DEFAULT_RULES
    ): HotspotState? {
        if (notificationTitle.isBlank()) return null

        val combinedText = if (notificationContent.contains(notificationTitle, ignoreCase = true)) {
            notificationContent
        } else {
            "$notificationTitle $notificationContent"
        }

        val hasRequiredKeyword = REQUIRED_KEYWORDS.any { keyword ->
            combinedText.contains(keyword, ignoreCase = true)
        }
        if (!hasRequiredKeyword) return null

        val matchingRule = rules.firstOrNull { rule ->
            rule.compiledTitleRegexes.any { regex ->
                try {
                    regex.containsMatchIn(notificationTitle)
                } catch (_: Exception) {
                    notificationTitle.contains("hotspot", ignoreCase = true) ||
                            notificationTitle.contains("tethering", ignoreCase = true) ||
                            notificationTitle.contains("chia sẻ", ignoreCase = true) ||
                            notificationTitle.contains("phát sóng", ignoreCase = true)
                }
            }
        } ?: return null

        if (matchingRule.noDeviceRegex.containsMatchIn(combinedText)) {
            return HotspotState.ON_EMPTY
        }

        val matchResult = matchingRule.connectedRegex.find(combinedText)
            ?: return HotspotState.ON_EMPTY

        val clients = matchResult.groupValues
            .drop(1)
            .firstOrNull { it.isNotBlank() }
            ?.toIntOrNull()
            ?: return HotspotState.ON_EMPTY

        return if (clients <= 0) HotspotState.ON_EMPTY else HotspotState.ON_CONNECTED(clients)
    }
}
