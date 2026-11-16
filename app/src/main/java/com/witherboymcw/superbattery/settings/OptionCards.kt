package com.witherboymcw.superbattery.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.witherboymcw.superbattery.MainActivity
import com.witherboymcw.superbattery.OledThemeAllInOne
import com.witherboymcw.superbattery.R
import com.witherboymcw.superbattery.core.LocaleManager
import com.witherboymcw.superbattery.core.SupportedLanguage

val optionCards: List<OptionCardSpec<*>> = listOf(
    OptionCardSpec(
        section = SettingsSection.LANGUAGE,
        radioGroupId = R.id.language_radio_group,
        selectedTextId = R.id.selected_language_text,
        options = listOf(
            OptionSpec(R.id.lang_system, SupportedLanguage.SYSTEM.tag, R.string.at_system),
            OptionSpec(R.id.lang_vietnamese, SupportedLanguage.VIETNAMESE.tag, R.string.language_vietnamese),
            OptionSpec(R.id.lang_chinese_simplified, SupportedLanguage.CHINESE_SIMPLIFIED.tag, R.string.language_simplified_chinese),
            OptionSpec(R.id.lang_english, SupportedLanguage.ENGLISH_US.tag, R.string.language_english_us),
            OptionSpec(R.id.lang_english_uk, SupportedLanguage.ENGLISH_UK.tag, R.string.language_english_uk),
            OptionSpec(R.id.lang_brainrot, SupportedLanguage.BRAINROT.tag, R.string.language_brainrot)
        ),
        read = { context -> LocaleManager.normalizeLanguageTag(AppPreferences.getAppLanguage(context)) },
        write = { context, value ->
            if (context is MainActivity) {
                LocaleManager.setAppLanguage(context, value as String)
            }
        }
    ),
    OptionCardSpec(
        section = SettingsSection.FREQUENCY,
        radioGroupId = R.id.frequency_radio_group,
        selectedTextId = R.id.selected_frequency_text,
        options = listOf(
            OptionSpec(R.id.freq_500, 500L, R.string.update_every_half_second),
            OptionSpec(R.id.freq_1000, 1_000L, R.string.update_every_second),
            OptionSpec(R.id.freq_2000, 2_000L, R.string.update_every_two_seconds)
        ),
        read = { context -> AppPreferences.getRefreshIntervalMs(context) },
        write = { context, value ->
            AppPreferences.setRefreshIntervalMs(context, value as Long)
            if (context is MainActivity) {
                context.refreshIntervalMs = value
            }
        }
    ),
    OptionCardSpec(
        section = SettingsSection.THEME,
        radioGroupId = R.id.theme_radio_group,
        selectedTextId = R.id.selected_theme_text,
        options = listOf(
            OptionSpec(R.id.theme_system, "system", R.string.at_system),
            OptionSpec(R.id.theme_light, "light", R.string.theme_light),
            OptionSpec(R.id.theme_dark, "dark", R.string.theme_dark),
            OptionSpec(R.id.theme_oled, "oled", R.string.theme_oled)
        ),
        read = { context -> AppPreferences.getAppTheme(context) },
        write = { context, targetMode ->
            AppPreferences.setAppTheme(context, targetMode as String)
            val mode = when (targetMode) {
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                "dark", "oled" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            AppCompatDelegate.setDefaultNightMode(mode)
            if (context is MainActivity) context.recreate()
        }
    ),
    OptionCardSpec(
        section = SettingsSection.VOLTAGE_UNIT,
        radioGroupId = R.id.voltage_unit_radio_group,
        selectedTextId = R.id.selected_voltage_unit_text,
        options = listOf(
            OptionSpec(R.id.voltage_unit_v, "v", R.string.voltage_unit_v),
            OptionSpec(R.id.voltage_unit_mv, "mv", R.string.voltage_unit_mv)
        ),
        read = { context -> AppPreferences.getVoltageUnitCode(context) },
        write = { context, value ->
            AppPreferences.setVoltageUnitCode(context, value as String)
            if (context is MainActivity) {
                context.updateDashboard()
                context.refreshWidgets()
            }
        }
    ),
    OptionCardSpec(
        section = SettingsSection.RAM_UNIT,
        radioGroupId = R.id.ram_unit_radio_group,
        selectedTextId = R.id.selected_ram_unit_text,
        options = listOf(
            OptionSpec(R.id.ram_unit_mb, "mb", R.string.ram_unit_mb),
            OptionSpec(R.id.ram_unit_gb, "gb", R.string.ram_unit_gb)
        ),
        read = { context -> AppPreferences.getRamUnitCode(context) },
        write = { context, value ->
            AppPreferences.setRamUnitCode(context, value as String)
            if (context is MainActivity) {
                context.updateDashboard()
                context.refreshWidgets()
            }
        }
    ),
    OptionCardSpec(
        section = SettingsSection.TEMP_UNIT,
        radioGroupId = R.id.temp_unit_radio_group,
        selectedTextId = R.id.selected_temp_unit_text,
        options = listOf(
            OptionSpec(R.id.temp_unit_celsius, "celsius", R.string.temp_unit_celsius),
            OptionSpec(R.id.temp_unit_fahrenheit, "fahrenheit", R.string.temp_unit_fahrenheit),
            OptionSpec(R.id.temp_unit_kelvin, "kelvin", R.string.temp_unit_kelvin)
        ),
        read = { context -> AppPreferences.getTempUnitCode(context) },
        write = { context, value ->
            AppPreferences.setTempUnitCode(context, value as String)
            if (context is MainActivity) {
                context.updateDashboard()
                context.refreshWidgets()
            }
        }
    ),
    OptionCardSpec(
        section = SettingsSection.ASSUME_OLED,
        radioGroupId = R.id.assume_oled_radio_group,
        selectedTextId = R.id.selected_assume_oled_text,
        options = listOf(
            OptionSpec(R.id.assume_oled_none, OledThemeAllInOne.OPTION_NONE, R.string.assume_oled_opt_none),
            OptionSpec(R.id.assume_oled_force_oled, OledThemeAllInOne.OPTION_OLED, R.string.assume_oled_opt_oled),
            OptionSpec(R.id.assume_oled_not_oled, OledThemeAllInOne.OPTION_NOT_OLED, R.string.assume_oled_opt_not_oled)
        ),
        read = { context -> AppPreferences.getAssumeOled(context) },
        write = { context, value ->
            AppPreferences.setAssumeOled(context, value as String)
            if (context is MainActivity) context.recreate()
        }
    )
)
