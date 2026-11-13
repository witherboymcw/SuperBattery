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

    val DEFAULT_RULES: List<CompiledHotspotRule> = listOf(
        CompiledHotspotRule(
            oem = "Universal / All Brands",
            compiledTitleRegexes = listOf(
                Regex("(?i)^.*(?:tethering|hotspot|chia\\s+sẻ|chia\\s+se|phát\\s+sóng|phat\\s+song).*", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            ),
            noDeviceRegex = Regex(
                "\\b(?:no\\s+(?:connected\\s+)?(?:devices?|clients?)|no\\s+(?:devices?|clients?)\\s+connected|0\\s+(?:connected\\s+)?(?:devices?|clients?)|không\\s+có\\s+thiết\\s+bị(?:\\s+nào)?(?:\\s+kết\\s+nối)?|khong\\s+co\\s+thiet\\s+bi(?:\\s+nao)?(?:\\s+ket\\s+noi)?|chưa\\s+có\\s+thiết\\s+bị|chua\\s+co\\s+thiet\\s+bi)\\b",
                RegexOption.IGNORE_CASE
            ),
            connectedRegex = Regex(
                "\\b(\\d+)\\s+(?:connected\\s+)?(?:devices?|clients?|thiết\\s+bị(?:\\s+(?:đã|da))?(?:\\s+(?:kết\\s+nối|ket\\s+noi))?|thiet\\s+bi)\\b|\\b(?:connected\\s+)?(?:devices?|clients?|thiết\\s+bị)\\s*[:\\-]?\\s*(\\d+)\\b|\\b(\\d+)\\s+connected\\b",
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

        if (matchingRule.noDeviceRegex.containsMatchIn(notificationContent)) {
            return HotspotState.ON_EMPTY
        }

        val matchResult = matchingRule.connectedRegex.find(notificationContent)
            ?: return HotspotState.ON_EMPTY

        val clients = matchResult.groupValues
            .drop(1)
            .firstOrNull { it.isNotBlank() }
            ?.toIntOrNull()
            ?: return HotspotState.ON_EMPTY

        return if (clients <= 0) HotspotState.ON_EMPTY else HotspotState.ON_CONNECTED(clients)
    }
}
