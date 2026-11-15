package com.witherboymcw.superbattery.templates

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.witherboymcw.superbattery.settings.AppPreferences
import java.util.Locale

/**
 * Metadata representation for supported application languages.
 */
enum class SupportedLanguage(
    val tag: String,
    val languageCode: String,
    val batteryRingMaxSweep: Float
) {
    SYSTEM(AppPreferences.DEFAULT_LANGUAGE, "system", 300f),
    VIETNAMESE("vi", "vi", 360f),
    CHINESE_SIMPLIFIED("zh-CN", "zh", 360f),
    ENGLISH_US("en-US", "en", 300f),
    ENGLISH_UK("en-GB", "en", 300f),
    BRAINROT("art", "art", 360f);

    companion object {
        fun fromLanguageCode(code: String?): SupportedLanguage {
            if (code.isNullOrEmpty()) return SYSTEM
            val lower = code.lowercase()
            return when {
                lower == "vi" -> VIETNAMESE
                lower.startsWith("zh") -> CHINESE_SIMPLIFIED
                lower == "art" -> BRAINROT
                lower.startsWith("en") -> ENGLISH_US
                else -> SYSTEM
            }
        }
    }
}

/**
 * Shared helper responsible for app language initialization, language switching,
 * locale normalization (e.g. brainrot aliases -> "art"), and localized context generation.
 */
object LocaleManager {

    private val BRAINROT_TAGS = setOf("en-br", "en-brainrot", "en-x-brainrot", "en-0rot")
    private val VALID_LANGS = setOf(
        "system", "vi", "en", "en-us", "en-gb", "en-rus", "en-rgb", "art",
        "en-br", "en-brainrot", "en-x-brainrot", "en-0rot",
        "zh", "zh-cn", "zh-rcn", "zh-hans", "zh-hans-cn"
    )

    fun normalizeLanguageTag(tag: String?): String {
        if (tag == null) return SupportedLanguage.SYSTEM.tag
        val lowerTag = tag.lowercase()
        if (lowerTag !in VALID_LANGS) return SupportedLanguage.SYSTEM.tag
        if (lowerTag in BRAINROT_TAGS) return SupportedLanguage.BRAINROT.tag
        return when (lowerTag) {
            "en", "en-us", "en-rus" -> SupportedLanguage.ENGLISH_US.tag
            "en-gb", "en-rgb" -> SupportedLanguage.ENGLISH_UK.tag
            "zh", "zh-cn", "zh-rcn", "zh-hans", "zh-hans-cn" -> SupportedLanguage.CHINESE_SIMPLIFIED.tag
            else -> lowerTag
        }
    }

    fun ensureInitialLanguage(context: Context) {
        val savedLang = AppPreferences.getAppLanguage(context)
        val normalizedLang = normalizeLanguageTag(savedLang)
        if (normalizedLang == SupportedLanguage.SYSTEM.tag && savedLang != SupportedLanguage.SYSTEM.tag) {
            AppPreferences.setAppLanguage(context, SupportedLanguage.SYSTEM.tag)
        }

        val targetLocales = if (normalizedLang == SupportedLanguage.SYSTEM.tag) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(normalizedLang)
        }

        val currentLocales = AppCompatDelegate.getApplicationLocales()
        if (currentLocales.toLanguageTags() != targetLocales.toLanguageTags()) {
            AppCompatDelegate.setApplicationLocales(targetLocales)
        }
    }

    fun setAppLanguage(context: Context, langCode: String, onLanguageChanged: (() -> Unit)? = null) {
        val currentLang = AppPreferences.getAppLanguage(context)

        val normalizedTarget = normalizeLanguageTag(langCode)
        val normalizedCurrent = normalizeLanguageTag(currentLang)

        if (normalizedCurrent != normalizedTarget) {
            AppPreferences.setAppLanguage(context, normalizedTarget)

            val appLocales = if (normalizedTarget == SupportedLanguage.SYSTEM.tag) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(normalizedTarget)
            }

            AppCompatDelegate.setApplicationLocales(appLocales)
            onLanguageChanged?.invoke()
        }
    }

    fun getSystemPrimaryLanguage(): String {
        val locales = Resources.getSystem().configuration.locales
        return if (!locales.isEmpty) {
            locales[0]?.language ?: "en"
        } else {
            "en"
        }
    }

    fun getAppLocale(context: Context): Locale {
        val savedLang = AppPreferences.getAppLanguage(context)
        val normalized = normalizeLanguageTag(savedLang)
        if (normalized == SupportedLanguage.SYSTEM.tag || normalized.isEmpty()) {
            return when (getSystemPrimaryLanguage()) {
                "vi" -> Locale.forLanguageTag(SupportedLanguage.VIETNAMESE.tag)
                "zh" -> Locale.forLanguageTag(SupportedLanguage.CHINESE_SIMPLIFIED.tag)
                else -> Locale.forLanguageTag(SupportedLanguage.ENGLISH_US.tag)
            }
        }
        return Locale.forLanguageTag(normalized)
    }

    fun getLocalizedContext(context: Context): Context {
        val locale = getAppLocale(context)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
