package com.witherboymcw.superbattery
import android.os.Build

import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.google.android.material.checkbox.MaterialCheckBox

internal class SettingsViewController {
    fun bind(activity: MainActivity, savedInstanceState: Bundle? = null) {
        with(activity) {
        val swipeContainer = findViewById<SwipeBackFrameLayout>(R.id.settings_container) ?: return
        swipeContainer.isClickable = true
        swipeContainer.isFocusable = true

        // Top-left back button
        findViewById<View>(R.id.settings_back_button)?.setOnClickListener {
            closeSettingsScreen()
        }

        // Swipe right callback from SwipeBackFrameLayout
        swipeContainer.swipeListener = object : SwipeBackFrameLayout.SwipeListener {
            override fun onSwipeProgress(translationX: Float) {
                swipeContainer.translationX = translationX
                val widthVal = swipeContainer.width.toFloat().takeIf { it > 0 } ?: 1080f
                val progress = (1f - (translationX / widthVal)).coerceIn(0f, 1f)
                btnSettings?.rotation = progress * 90f
            }

            override fun onSwipeEnd(shouldClose: Boolean) {
                if (shouldClose) {
                    closeSettingsScreen()
                } else {
                    swipeContainer.animate()
                        .translationX(0f)
                        .setDuration(200L)
                        .setInterpolator(FastOutSlowInInterpolator())
                        .withLayer()
                        .start()
                    btnSettings?.animate()
                        ?.rotation(90f)
                        ?.setDuration(200L)
                        ?.setInterpolator(FastOutSlowInInterpolator())
                        ?.start()
                }
            }
        }

        // 1. Language selector dropdown
        val headerLanguage = findViewById<View>(R.id.header_language)
        val dropdownLanguage = findViewById<View>(R.id.dropdown_language)
        val arrowLanguage = findViewById<ImageView>(R.id.arrow_language)
        val selectedLanguageText = findViewById<TextView>(R.id.selected_language_text)

        headerLanguage?.setOnClickListener {
            val isExpanded = dropdownLanguage?.visibility == View.VISIBLE
            dropdownLanguage?.visibility = if (isExpanded) View.GONE else View.VISIBLE
            arrowLanguage?.animate()?.rotation(if (isExpanded) 0f else 180f)?.setDuration(200L)?.start()
        }

        if (savedInstanceState?.getBoolean(KEY_LANGUAGE_DROPDOWN_OPEN) == true) {
            dropdownLanguage?.visibility = View.VISIBLE
            arrowLanguage?.rotation = 180f
        }

        val langGroup = findViewById<RadioGroup>(R.id.language_radio_group)

        val savedLang = getSharedPreferences(BatteryWidget.SETTINGS_PREFS, android.content.Context.MODE_PRIVATE)
            .getString(BatteryWidget.APP_LANGUAGE_KEY, "system") ?: "system"

        when (savedLang) {
            "vi" -> {
                langGroup?.check(R.id.lang_vietnamese)
                selectedLanguageText?.text = getString(R.string.language_vietnamese)
            }
            "en" -> {
                langGroup?.check(R.id.lang_english)
                selectedLanguageText?.text = getString(R.string.language_english)
            }
            "art", "en-0rot", "en-x-brainrot", "en-brainrot", "en-BR" -> {
                langGroup?.check(R.id.lang_brainrot)
                selectedLanguageText?.text = getString(R.string.language_brainrot)
            }
            else -> {
                langGroup?.check(R.id.lang_system)
                selectedLanguageText?.text = getString(R.string.at_system)
            }
        }

        langGroup?.setOnCheckedChangeListener { _, checkedId ->
            val targetLang = when (checkedId) {
                R.id.lang_vietnamese -> "vi"
                R.id.lang_english -> "en"
                R.id.lang_brainrot -> "art"
                else -> "system"
            }
            selectedLanguageText?.text = when (targetLang) {
                "vi" -> getString(R.string.language_vietnamese)
                "en" -> getString(R.string.language_english)
                "art" -> getString(R.string.language_brainrot)
                else -> getString(R.string.at_system)
            }
            setAppLanguage(targetLang)
        }

        // 2. Allow notifications
        findViewById<View>(R.id.card_allow_notifications)?.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST)
            } else {
                Toast.makeText(activity, R.string.notification_access_enabled, Toast.LENGTH_SHORT).show()
            }
        }

        // 3. Manage notification access
        findViewById<View>(R.id.card_notification_access)?.setOnClickListener {
            openNotificationAccessSettings()
        }

        // 4. Update frequency dropdown
        val headerFrequency = findViewById<View>(R.id.header_update_frequency)
        val dropdownFrequency = findViewById<View>(R.id.dropdown_update_frequency)
        val arrowFrequency = findViewById<ImageView>(R.id.arrow_update_frequency)
        val selectedFrequencyText = findViewById<TextView>(R.id.selected_frequency_text)

        headerFrequency?.setOnClickListener {
            val isExpanded = dropdownFrequency?.visibility == View.VISIBLE
            dropdownFrequency?.visibility = if (isExpanded) View.GONE else View.VISIBLE
            arrowFrequency?.animate()?.rotation(if (isExpanded) 0f else 180f)?.setDuration(200L)?.start()
        }

        if (savedInstanceState?.getBoolean(KEY_FREQUENCY_DROPDOWN_OPEN) == true) {
            dropdownFrequency?.visibility = View.VISIBLE
            arrowFrequency?.rotation = 180f
        }

        val freqGroup = findViewById<RadioGroup>(R.id.frequency_radio_group)
        val initialFreqRes = when (refreshIntervalMs) {
            500L -> {
                freqGroup?.check(R.id.freq_500)
                R.string.update_every_half_second
            }
            1_000L -> {
                freqGroup?.check(R.id.freq_1000)
                R.string.update_every_second
            }
            else -> {
                freqGroup?.check(R.id.freq_2000)
                R.string.update_every_two_seconds
            }
        }
        selectedFrequencyText?.text = getString(initialFreqRes)

        freqGroup?.setOnCheckedChangeListener { _, checkedId ->
            val selectedMs = when (checkedId) {
                R.id.freq_500 -> 500L
                R.id.freq_1000 -> 1_000L
                R.id.freq_2000 -> 2_000L
                else -> 2_000L
            }
            val textRes = when (selectedMs) {
                500L -> R.string.update_every_half_second
                1_000L -> R.string.update_every_second
                else -> R.string.update_every_two_seconds
            }
            selectedFrequencyText?.text = getString(textRes)
            refreshIntervalMs = selectedMs
            getSharedPreferences(SETTINGS_PREFS, android.content.Context.MODE_PRIVATE).edit()
                .putLong(REFRESH_INTERVAL_KEY, selectedMs)
                .apply()
            handler.removeCallbacks(refreshDashboard)
            handler.post(refreshDashboard)
        }

        // 5. Theme dropdown
        val headerTheme = findViewById<View>(R.id.header_theme)
        val dropdownTheme = findViewById<View>(R.id.dropdown_theme)
        val arrowTheme = findViewById<ImageView>(R.id.arrow_theme)
        val selectedThemeText = findViewById<TextView>(R.id.selected_theme_text)

        headerTheme?.setOnClickListener {
            val isExpanded = dropdownTheme?.visibility == View.VISIBLE
            dropdownTheme?.visibility = if (isExpanded) View.GONE else View.VISIBLE
            arrowTheme?.animate()?.rotation(if (isExpanded) 0f else 180f)?.setDuration(200L)?.start()
        }

        if (savedInstanceState?.getBoolean(KEY_THEME_DROPDOWN_OPEN) == true) {
            dropdownTheme?.visibility = View.VISIBLE
            arrowTheme?.rotation = 180f
        }

        val themeGroup = findViewById<RadioGroup>(R.id.theme_radio_group)

        val prefs = getSharedPreferences(SETTINGS_PREFS, android.content.Context.MODE_PRIVATE)
        val currentTheme = prefs.getString(APP_THEME_KEY, "system") ?: "system"

        when (currentTheme) {
            "light" -> {
                themeGroup?.check(R.id.theme_light)
                selectedThemeText?.text = getString(R.string.theme_light)
            }
            "dark" -> {
                themeGroup?.check(R.id.theme_dark)
                selectedThemeText?.text = getString(R.string.theme_dark)
            }
            "oled" -> {
                themeGroup?.check(R.id.theme_oled)
                selectedThemeText?.text = getString(R.string.theme_oled)
            }
            else -> {
                themeGroup?.check(R.id.theme_system)
                selectedThemeText?.text = getString(R.string.at_system)
            }
        }

        themeGroup?.setOnCheckedChangeListener { _, checkedId ->
            val targetTheme = when (checkedId) {
                R.id.theme_light -> "light"
                R.id.theme_dark -> "dark"
                R.id.theme_oled -> "oled"
                else -> "system"
            }
            val textRes = when (targetTheme) {
                "light" -> R.string.theme_light
                "dark" -> R.string.theme_dark
                "oled" -> R.string.theme_oled
                else -> R.string.at_system
            }
            selectedThemeText?.text = getString(textRes)

            val activeTheme = prefs.getString(APP_THEME_KEY, "system") ?: "system"
            if (activeTheme != targetTheme) {
                prefs.edit().putString(APP_THEME_KEY, targetTheme).apply()
                val mode = when (targetTheme) {
                    "light" -> AppCompatDelegate.MODE_NIGHT_NO
                    "dark", "oled" -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                AppCompatDelegate.setDefaultNightMode(mode)
                activity.recreate()
            }
        }

        // 6. Use system battery counter
        val checkboxSystemBattery = findViewById<MaterialCheckBox>(R.id.checkbox_use_system_battery)
        val cardSystemBattery = findViewById<View>(R.id.card_use_system_battery)

        checkboxSystemBattery?.isChecked = SettingsHelper.isUseSystemBatteryCounter(activity)

        cardSystemBattery?.setOnClickListener {
            val newValue = !(checkboxSystemBattery?.isChecked ?: false)
            checkboxSystemBattery?.isChecked = newValue
            getSharedPreferences(SETTINGS_PREFS, android.content.Context.MODE_PRIVATE).edit()
                .putBoolean(BatteryWidget.USE_SYSTEM_BATTERY_KEY, newValue)
                .apply()
            updateDashboard()
            refreshWidgets()
        }

        // 7. Voltage unit dropdown
        val headerVoltageUnit = findViewById<View>(R.id.header_voltage_unit)
        val dropdownVoltageUnit = findViewById<View>(R.id.dropdown_voltage_unit)
        val arrowVoltageUnit = findViewById<ImageView>(R.id.arrow_voltage_unit)
        val selectedVoltageUnitText = findViewById<TextView>(R.id.selected_voltage_unit_text)

        headerVoltageUnit?.setOnClickListener {
            val isExpanded = dropdownVoltageUnit?.visibility == View.VISIBLE
            dropdownVoltageUnit?.visibility = if (isExpanded) View.GONE else View.VISIBLE
            arrowVoltageUnit?.animate()?.rotation(if (isExpanded) 0f else 180f)?.setDuration(200L)?.start()
        }

        if (savedInstanceState?.getBoolean(KEY_VOLTAGE_UNIT_DROPDOWN_OPEN) == true) {
            dropdownVoltageUnit?.visibility = View.VISIBLE
            arrowVoltageUnit?.rotation = 180f
        }

        val voltageUnitGroup = findViewById<RadioGroup>(R.id.voltage_unit_radio_group)

        val currentVoltageUnit = SettingsHelper.getVoltageUnit(activity)
        when (currentVoltageUnit) {
            VoltageUnit.V -> {
                voltageUnitGroup?.check(R.id.voltage_unit_v)
                selectedVoltageUnitText?.text = getString(R.string.voltage_unit_v)
            }
            else -> {
                voltageUnitGroup?.check(R.id.voltage_unit_mv)
                selectedVoltageUnitText?.text = getString(R.string.voltage_unit_mv)
            }
        }

        voltageUnitGroup?.setOnCheckedChangeListener { _, checkedId ->
            val targetUnit = when (checkedId) {
                R.id.voltage_unit_v -> VoltageUnit.V
                else -> VoltageUnit.MV
            }
            val textRes = when (targetUnit) {
                VoltageUnit.V -> R.string.voltage_unit_v
                else -> R.string.voltage_unit_mv
            }
            selectedVoltageUnitText?.text = getString(textRes)

            getSharedPreferences(SETTINGS_PREFS, android.content.Context.MODE_PRIVATE).edit()
                .putString(BatteryWidget.VOLTAGE_UNIT_KEY, targetUnit.code)
                .apply()
            updateDashboard()
            refreshWidgets()
        }

        // 8. RAM unit dropdown
        val headerRamUnit = findViewById<View>(R.id.header_ram_unit)
        val dropdownRamUnit = findViewById<View>(R.id.dropdown_ram_unit)
        val arrowRamUnit = findViewById<ImageView>(R.id.arrow_ram_unit)
        val selectedRamUnitText = findViewById<TextView>(R.id.selected_ram_unit_text)

        headerRamUnit?.setOnClickListener {
            val isExpanded = dropdownRamUnit?.visibility == View.VISIBLE
            dropdownRamUnit?.visibility = if (isExpanded) View.GONE else View.VISIBLE
            arrowRamUnit?.animate()?.rotation(if (isExpanded) 0f else 180f)?.setDuration(200L)?.start()
        }

        if (savedInstanceState?.getBoolean(KEY_RAM_UNIT_DROPDOWN_OPEN) == true) {
            dropdownRamUnit?.visibility = View.VISIBLE
            arrowRamUnit?.rotation = 180f
        }

        val ramUnitGroup = findViewById<RadioGroup>(R.id.ram_unit_radio_group)

        val currentRamUnit = SettingsHelper.getRamUnit(activity)
        when (currentRamUnit) {
            RamUnit.GB -> {
                ramUnitGroup?.check(R.id.ram_unit_gb)
                selectedRamUnitText?.text = getString(R.string.ram_unit_gb)
            }
            else -> {
                ramUnitGroup?.check(R.id.ram_unit_mb)
                selectedRamUnitText?.text = getString(R.string.ram_unit_mb)
            }
        }

        ramUnitGroup?.setOnCheckedChangeListener { _, checkedId ->
            val targetUnit = when (checkedId) {
                R.id.ram_unit_gb -> RamUnit.GB
                else -> RamUnit.MB
            }
            val textRes = when (targetUnit) {
                RamUnit.GB -> R.string.ram_unit_gb
                else -> R.string.ram_unit_mb
            }
            selectedRamUnitText?.text = getString(textRes)

            getSharedPreferences(SETTINGS_PREFS, android.content.Context.MODE_PRIVATE).edit()
                .putString(BatteryWidget.RAM_UNIT_KEY, targetUnit.code)
                .apply()
            updateDashboard()
            refreshWidgets()
        }

        // 9. Temperature unit dropdown
        val headerTempUnit = findViewById<View>(R.id.header_temp_unit)
        val dropdownTempUnit = findViewById<View>(R.id.dropdown_temp_unit)
        val arrowTempUnit = findViewById<ImageView>(R.id.arrow_temp_unit)
        val selectedTempUnitText = findViewById<TextView>(R.id.selected_temp_unit_text)

        headerTempUnit?.setOnClickListener {
            val isExpanded = dropdownTempUnit?.visibility == View.VISIBLE
            dropdownTempUnit?.visibility = if (isExpanded) View.GONE else View.VISIBLE
            arrowTempUnit?.animate()?.rotation(if (isExpanded) 0f else 180f)?.setDuration(200L)?.start()
        }

        if (savedInstanceState?.getBoolean(KEY_TEMP_UNIT_DROPDOWN_OPEN) == true) {
            dropdownTempUnit?.visibility = View.VISIBLE
            arrowTempUnit?.rotation = 180f
        }

        val tempUnitGroup = findViewById<RadioGroup>(R.id.temp_unit_radio_group)

        val currentTempUnit = SettingsHelper.getTemperatureUnit(activity)
        when (currentTempUnit) {
            TempUnit.FAHRENHEIT -> {
                tempUnitGroup?.check(R.id.temp_unit_fahrenheit)
                selectedTempUnitText?.text = getString(R.string.temp_unit_fahrenheit)
            }
            TempUnit.KELVIN -> {
                tempUnitGroup?.check(R.id.temp_unit_kelvin)
                selectedTempUnitText?.text = getString(R.string.temp_unit_kelvin)
            }
            else -> {
                tempUnitGroup?.check(R.id.temp_unit_celsius)
                selectedTempUnitText?.text = getString(R.string.temp_unit_celsius)
            }
        }

        tempUnitGroup?.setOnCheckedChangeListener { _, checkedId ->
            val targetUnit = when (checkedId) {
                R.id.temp_unit_fahrenheit -> TempUnit.FAHRENHEIT
                R.id.temp_unit_kelvin -> TempUnit.KELVIN
                else -> TempUnit.CELSIUS
            }
            val textRes = when (targetUnit) {
                TempUnit.FAHRENHEIT -> R.string.temp_unit_fahrenheit
                TempUnit.KELVIN -> R.string.temp_unit_kelvin
                else -> R.string.temp_unit_celsius
            }
            selectedTempUnitText?.text = getString(textRes)

            getSharedPreferences(SETTINGS_PREFS, android.content.Context.MODE_PRIVATE).edit()
                .putString(BatteryWidget.TEMP_UNIT_KEY, targetUnit.code)
                .apply()
            updateDashboard()
            refreshWidgets()
        }

        // 10. Thick chin mode dropdown
        val headerThickChin = findViewById<View>(R.id.header_thick_chin)
        val dropdownThickChin = findViewById<View>(R.id.dropdown_thick_chin)
        val arrowThickChin = findViewById<ImageView>(R.id.arrow_thick_chin)
        val selectedThickChinText = findViewById<TextView>(R.id.selected_thick_chin_text)
        val thickChinSeekBar = findViewById<SeekBar>(R.id.thick_chin_seekbar)
        val btnMinus10 = findViewById<View>(R.id.btn_thick_chin_minus_10)
        val btnMinus1 = findViewById<View>(R.id.btn_thick_chin_minus_1)
        val btnPlus1 = findViewById<View>(R.id.btn_thick_chin_plus_1)
        val btnPlus10 = findViewById<View>(R.id.btn_thick_chin_plus_10)
        val btnQuick0 = findViewById<View>(R.id.btn_thick_chin_quick_0)
        val btnQuick256 = findViewById<View>(R.id.btn_thick_chin_quick_256)

        headerThickChin?.setOnClickListener {
            val isExpanded = dropdownThickChin?.visibility == View.VISIBLE
            dropdownThickChin?.visibility = if (isExpanded) View.GONE else View.VISIBLE
            arrowThickChin?.animate()?.rotation(if (isExpanded) 0f else 180f)?.setDuration(200L)?.start()
        }

        if (savedInstanceState?.getBoolean(KEY_THICK_CHIN_DROPDOWN_OPEN) == true) {
            dropdownThickChin?.visibility = View.VISIBLE
            arrowThickChin?.rotation = 180f
        }

        fun updateThickChin(newLevel: Int, updateSeekBar: Boolean = true) {
            val level = newLevel.coerceIn(0, 256)
            selectedThickChinText?.text = level.toString()
            if (updateSeekBar && thickChinSeekBar?.progress != level) {
                thickChinSeekBar?.progress = level
            }
            prefs.edit().putInt(THICK_CHIN_LEVEL_KEY, level).apply()
            applyThickChinPadding()
        }

        val savedThickChinLevel = prefs.getInt(THICK_CHIN_LEVEL_KEY, 0).coerceIn(0, 256)
        updateThickChin(savedThickChinLevel, updateSeekBar = true)

        fun setupRepeatButton(button: View?, delta: Int) {
            button ?: return
            var repeatCount = 0
            val repeatRunnable = object : Runnable {
                override fun run() {
                    if (!button.isAttachedToWindow) {
                        handler.removeCallbacks(this)
                        return
                    }
                    repeatCount++
                    val current = prefs.getInt(THICK_CHIN_LEVEL_KEY, 0)
                    updateThickChin(current + delta)
                    val nextDelay = if (repeatCount < 3) 500L else 250L
                    handler.postDelayed(this, nextDelay)
                }
            }

            button.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {}
                override fun onViewDetachedFromWindow(v: View) {
                    handler.removeCallbacks(repeatRunnable)
                }
            })

            button.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                        repeatCount = 0
                        val current = prefs.getInt(THICK_CHIN_LEVEL_KEY, 0)
                        updateThickChin(current + delta)
                        handler.postDelayed(repeatRunnable, 500L)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (event.x < 0 || event.x > v.width || event.y < 0 || event.y > v.height) {
                            handler.removeCallbacks(repeatRunnable)
                            v.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                        false
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        handler.removeCallbacks(repeatRunnable)
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                        if (event.action == MotionEvent.ACTION_UP) {
                            v.performClick()
                        }
                        true
                    }
                    else -> false
                }
            }
        }

        setupRepeatButton(btnMinus10, -10)
        setupRepeatButton(btnMinus1, -1)
        setupRepeatButton(btnPlus1, 1)
        setupRepeatButton(btnPlus10, 10)

        btnQuick0?.setOnClickListener {
            updateThickChin(0)
        }
        btnQuick256?.setOnClickListener {
            updateThickChin(256)
        }

        thickChinSeekBar?.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> v.parent?.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.parent?.requestDisallowInterceptTouchEvent(false)
            }
            false
        }

        thickChinSeekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    updateThickChin(progress, updateSeekBar = false)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 12. Assume OLED dropdown
        val headerAssumeOled = findViewById<View>(R.id.header_assume_oled)
        val dropdownAssumeOled = findViewById<View>(R.id.dropdown_assume_oled)
        val arrowAssumeOled = findViewById<ImageView>(R.id.arrow_assume_oled)
        val selectedAssumeOledText = findViewById<TextView>(R.id.selected_assume_oled_text)

        headerAssumeOled?.setOnClickListener {
            val isExpanded = dropdownAssumeOled?.visibility == View.VISIBLE
            dropdownAssumeOled?.visibility = if (isExpanded) View.GONE else View.VISIBLE
            arrowAssumeOled?.animate()?.rotation(if (isExpanded) 0f else 180f)?.setDuration(200L)?.start()
        }

        if (savedInstanceState?.getBoolean(KEY_ASSUME_OLED_DROPDOWN_OPEN) == true) {
            dropdownAssumeOled?.visibility = View.VISIBLE
            arrowAssumeOled?.rotation = 180f
        }

        val assumeOledGroup = findViewById<RadioGroup>(R.id.assume_oled_radio_group)
        val currentAssumeOled = prefs.getString(ASSUME_OLED_KEY, "none") ?: "none"

        when (currentAssumeOled) {
            "oled" -> {
                assumeOledGroup?.check(R.id.assume_oled_force_oled)
                selectedAssumeOledText?.text = getString(R.string.assume_oled_opt_oled)
            }
            "not_oled" -> {
                assumeOledGroup?.check(R.id.assume_oled_not_oled)
                selectedAssumeOledText?.text = getString(R.string.assume_oled_opt_not_oled)
            }
            else -> {
                assumeOledGroup?.check(R.id.assume_oled_none)
                selectedAssumeOledText?.text = getString(R.string.assume_oled_opt_none)
            }
        }

        assumeOledGroup?.setOnCheckedChangeListener { _, checkedId ->
            val targetValue = when (checkedId) {
                R.id.assume_oled_force_oled -> "oled"
                R.id.assume_oled_not_oled -> "not_oled"
                else -> "none"
            }
            val textRes = when (targetValue) {
                "oled" -> R.string.assume_oled_opt_oled
                "not_oled" -> R.string.assume_oled_opt_not_oled
                else -> R.string.assume_oled_opt_none
            }
            selectedAssumeOledText?.text = getString(textRes)

            val activeValue = prefs.getString(ASSUME_OLED_KEY, "none") ?: "none"
            if (activeValue != targetValue) {
                prefs.edit().putString(ASSUME_OLED_KEY, targetValue).apply()
                activity.recreate()
            }
        }

        // Landscape two-column settings options setup
        val settingsDetailTitle = findViewById<TextView>(R.id.settings_detail_title)
        if (settingsDetailTitle != null) {
            val dropdowns = listOf(
                dropdownLanguage,
                dropdownFrequency,
                dropdownTheme,
                dropdownVoltageUnit,
                dropdownRamUnit,
                dropdownTempUnit,
                dropdownThickChin,
                dropdownAssumeOled
            )
            val arrows = listOf(
                arrowLanguage,
                arrowFrequency,
                arrowTheme,
                arrowVoltageUnit,
                arrowRamUnit,
                arrowTempUnit,
                arrowThickChin,
                arrowAssumeOled
            )

            fun selectSection(activeDropdown: View?, activeArrow: ImageView?, titleRes: Int) {
                dropdowns.forEach { it?.visibility = View.GONE }
                arrows.forEach { it?.rotation = 0f }
                activeDropdown?.visibility = View.VISIBLE
                activeArrow?.rotation = 180f
                settingsDetailTitle.setText(titleRes)
            }

            headerLanguage?.setOnClickListener {
                selectSection(dropdownLanguage, arrowLanguage, R.string.language)
            }
            headerFrequency?.setOnClickListener {
                selectSection(dropdownFrequency, arrowFrequency, R.string.update_frequency)
            }
            headerTheme?.setOnClickListener {
                selectSection(dropdownTheme, arrowTheme, R.string.theme_header)
            }
            headerVoltageUnit?.setOnClickListener {
                selectSection(dropdownVoltageUnit, arrowVoltageUnit, R.string.voltage_unit_header)
            }
            headerRamUnit?.setOnClickListener {
                selectSection(dropdownRamUnit, arrowRamUnit, R.string.ram_unit_header)
            }
            headerTempUnit?.setOnClickListener {
                selectSection(dropdownTempUnit, arrowTempUnit, R.string.temp_unit_header)
            }
            headerThickChin?.setOnClickListener {
                selectSection(dropdownThickChin, arrowThickChin, R.string.thick_chin_header)
            }
            headerAssumeOled?.setOnClickListener {
                selectSection(dropdownAssumeOled, arrowAssumeOled, R.string.assume_oled_header)
            }

            // Initially show restored section or default to Language
            val restoredSection = when {
                savedInstanceState?.getBoolean(KEY_FREQUENCY_DROPDOWN_OPEN) == true ->
                    Triple(dropdownFrequency, arrowFrequency, R.string.update_frequency)
                savedInstanceState?.getBoolean(KEY_THEME_DROPDOWN_OPEN) == true ->
                    Triple(dropdownTheme, arrowTheme, R.string.theme_header)
                savedInstanceState?.getBoolean(KEY_VOLTAGE_UNIT_DROPDOWN_OPEN) == true ->
                    Triple(dropdownVoltageUnit, arrowVoltageUnit, R.string.voltage_unit_header)
                savedInstanceState?.getBoolean(KEY_RAM_UNIT_DROPDOWN_OPEN) == true ->
                    Triple(dropdownRamUnit, arrowRamUnit, R.string.ram_unit_header)
                savedInstanceState?.getBoolean(KEY_TEMP_UNIT_DROPDOWN_OPEN) == true ->
                    Triple(dropdownTempUnit, arrowTempUnit, R.string.temp_unit_header)
                savedInstanceState?.getBoolean(KEY_THICK_CHIN_DROPDOWN_OPEN) == true ->
                    Triple(dropdownThickChin, arrowThickChin, R.string.thick_chin_header)
                savedInstanceState?.getBoolean(KEY_ASSUME_OLED_DROPDOWN_OPEN) == true ->
                    Triple(dropdownAssumeOled, arrowAssumeOled, R.string.assume_oled_header)
                else ->
                    Triple(dropdownLanguage, arrowLanguage, R.string.language)
            }
            selectSection(restoredSection.first, restoredSection.second, restoredSection.third)
        }

        val savedScrollY = savedInstanceState?.getInt(KEY_SETTINGS_SCROLL_Y, -1) ?: -1
        if (savedScrollY >= 0) {
            val scrollView = findViewById<android.widget.ScrollView>(R.id.settings_scroll_view)
                ?: findViewById<android.widget.ScrollView>(R.id.settings_scroll_left)
            scrollView?.post {
                scrollView.scrollTo(0, savedScrollY)
            }
        }
        val savedRightScrollY = savedInstanceState?.getInt(KEY_SETTINGS_SCROLL_RIGHT_Y, -1) ?: -1
        if (savedRightScrollY >= 0) {
            val rightScrollView = findViewById<android.widget.ScrollView>(R.id.settings_scroll_right)
            rightScrollView?.post {
                rightScrollView.scrollTo(0, savedRightScrollY)
            }
        }

        updateNotificationPermissionStatuses()
            }
    }
    companion object {
        const val KEY_LANGUAGE_DROPDOWN_OPEN = "key_language_dropdown_open"
        const val KEY_FREQUENCY_DROPDOWN_OPEN = "key_frequency_dropdown_open"
        const val KEY_THEME_DROPDOWN_OPEN = "key_theme_dropdown_open"
        const val KEY_TEMP_UNIT_DROPDOWN_OPEN = "key_temp_unit_dropdown_open"
        const val KEY_VOLTAGE_UNIT_DROPDOWN_OPEN = "key_voltage_unit_dropdown_open"
        const val KEY_RAM_UNIT_DROPDOWN_OPEN = "key_ram_unit_dropdown_open"
        const val KEY_THICK_CHIN_DROPDOWN_OPEN = "key_thick_chin_dropdown_open"
        const val KEY_ASSUME_OLED_DROPDOWN_OPEN = "key_assume_oled_dropdown_open"
        const val KEY_SETTINGS_SCROLL_Y = "key_settings_scroll_y"
        const val KEY_SETTINGS_SCROLL_RIGHT_Y = "key_settings_scroll_right_y"
        const val SETTINGS_PREFS = BatteryWidget.SETTINGS_PREFS
        const val REFRESH_INTERVAL_KEY = "refresh_interval_ms"
        const val APP_THEME_KEY = "app_theme"
        const val ASSUME_OLED_KEY = "assume_oled"
        const val THICK_CHIN_LEVEL_KEY = "thick_chin_level"
        const val NOTIFICATION_PERMISSION_REQUEST = 1001
    }
}
