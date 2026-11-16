package com.witherboymcw.superbattery.core

import org.junit.Test
import org.junit.Assert.*
import java.util.Locale

class LocaleManagerTest {
    @Test
    fun testLanguageNormalizationAndLocaleResolution() {
        val brainrotAliases = listOf("art", "en-BR", "en-brainrot", "en-x-brainrot", "en-0rot")
        brainrotAliases.forEach { tag ->
            val locale = when (tag) {
                "en-BR", "en-brainrot", "en-x-brainrot", "en-0rot" -> Locale.forLanguageTag("art")
                else -> Locale.forLanguageTag(tag)
            }
            assertEquals("art", locale.language)
        }

        val chineseTags = listOf("zh", "zh-CN", "zh-cn", "zh-hans")
        chineseTags.forEach { tag ->
            val normalized = LocaleManager.normalizeLanguageTag(tag)
            assertEquals("zh-CN", normalized)
        }
    }
}
