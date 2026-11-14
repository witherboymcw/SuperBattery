package com.witherboymcw.superbattery.templates

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.res.Configuration
import android.content.res.Resources
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.witherboymcw.superbattery.widgets.BatteryWidget
import java.util.Locale

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
        if (tag == null) return "system"
        val lowerTag = tag.lowercase()
        if (lowerTag !in VALID_LANGS) return "system"
        if (lowerTag in BRAINROT_TAGS) return "art"
        return when (lowerTag) {
            "en", "en-us", "en-rus" -> "en-US"
            "en-gb", "en-rgb" -> "en-GB"
            "zh", "zh-cn", "zh-rcn", "zh-hans", "zh-hans-cn" -> "zh-CN"
            else -> lowerTag
        }
    }

    fun ensureInitialLanguage(context: Context) {
        val prefs = context.getSharedPreferences(BatteryWidget.SETTINGS_PREFS, MODE_PRIVATE)
        val savedLang = prefs.getString(BatteryWidget.APP_LANGUAGE_KEY, "system") ?: "system"
        val normalizedLang = normalizeLanguageTag(savedLang)
        if (normalizedLang == "system" && savedLang != "system") {
            prefs.edit().putString(BatteryWidget.APP_LANGUAGE_KEY, "system").apply()
        }

        val targetLocales = if (normalizedLang == "system") {
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
        val prefs = context.getSharedPreferences(BatteryWidget.SETTINGS_PREFS, MODE_PRIVATE)
        val currentLang = prefs.getString(BatteryWidget.APP_LANGUAGE_KEY, "system") ?: "system"

        val normalizedTarget = normalizeLanguageTag(langCode)
        val normalizedCurrent = normalizeLanguageTag(currentLang)

        if (normalizedCurrent != normalizedTarget) {
            prefs.edit().putString(BatteryWidget.APP_LANGUAGE_KEY, normalizedTarget).apply()

            val appLocales = if (normalizedTarget == "system") {
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
        val prefs = context.getSharedPreferences(BatteryWidget.SETTINGS_PREFS, MODE_PRIVATE)
        val savedLang = prefs.getString(BatteryWidget.APP_LANGUAGE_KEY, "system") ?: "system"
        val normalized = normalizeLanguageTag(savedLang)
        if (normalized == "system" || normalized.isEmpty()) {
            return when (getSystemPrimaryLanguage()) {
                "vi" -> Locale.forLanguageTag("vi")
                "zh" -> Locale.forLanguageTag("zh-CN")
                else -> Locale.forLanguageTag("en-US")
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
