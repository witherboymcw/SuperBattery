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
    private val VALID_LANGS = setOf("system", "vi", "en", "art", "en-br", "en-brainrot", "en-x-brainrot", "en-0rot")

    fun normalizeLanguageTag(tag: String?): String {
        if (tag == null) return "system"
        val lowerTag = tag.lowercase()
        if (lowerTag !in VALID_LANGS) return "system"
        return if (lowerTag in BRAINROT_TAGS) "art" else lowerTag
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
            return if (getSystemPrimaryLanguage() == "vi") Locale.forLanguageTag("vi") else Locale.ENGLISH
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
