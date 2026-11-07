package com.witherboymcw.superbattery

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.google.android.material.checkbox.MaterialCheckBox
import java.util.Locale
import kotlin.math.abs

class MainActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private val voltageHistory = ArrayDeque<HistoryGraphView.Sample>()
    private val ramHistory = ArrayDeque<HistoryGraphView.Sample>()
    private val temperatureHistory = ArrayDeque<HistoryGraphView.Sample>()
    private var previousRx = -1L
    private var previousTx = -1L
    private var previousNetworkTime = 0L
    private var refreshIntervalMs = DEFAULT_REFRESH_INTERVAL_MS

    private var tvVoltageCurrent: TextView? = null
    private var tvTemperatureCurrent: TextView? = null
    private var tvRamCurrent: TextView? = null
    private var tvNetworkRx: TextView? = null
    private var tvNetworkTx: TextView? = null
    private var voltageGraph: HistoryGraphView? = null
    private var ramGraph: HistoryGraphView? = null
    private var tempGraph: HistoryGraphView? = null
    private var cardBatteryTitle: TextView? = null
    private var cardBatteryValue: TextView? = null
    private var cardBatteryDetail: TextView? = null
    private val memoryInfo = ActivityManager.MemoryInfo()

    private val settingsBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            closeSettingsScreen()
        }
    }

    private val refreshDashboard = object : Runnable {
        override fun run() {
            updateDashboard()
            handler.postDelayed(this, refreshIntervalMs)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ensureInitialLanguage()

        val savedTheme = getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE)
            .getString(APP_THEME_KEY, "system") ?: "system"
        val targetNightMode = if (savedTheme == "oled") {
            setTheme(R.style.Theme_SuperBattery_Oled)
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            when (savedTheme) {
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        }
        if (AppCompatDelegate.getDefaultNightMode() != targetNightMode) {
            AppCompatDelegate.setDefaultNightMode(targetNightMode)
        }

        super.onCreate(savedInstanceState)
        try {
            startService(Intent(this, BatteryMonitorService::class.java))
        } catch (_: Exception) {
            // Guard against background service execution limits on Android 8.0+
        }
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        initDashboardViews()
        applyGraphBackgrounds(savedTheme)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        onBackPressedDispatcher.addCallback(this, settingsBackCallback)
        loadRefreshInterval()
        initializeMetricCards()
        setupSettingsScreen(savedInstanceState)

        findViewById<View>(R.id.settings_button).setOnClickListener {
            openSettingsScreen()
        }

        findViewById<View>(R.id.dashboard)?.let { dashboard ->
            setupDashboardSwipeToOpen(dashboard)
        }

        if (savedInstanceState?.getBoolean(KEY_SETTINGS_OPEN) == true) {
            findViewById<View>(R.id.settings_container)?.let { container ->
                container.visibility = View.VISIBLE
                container.translationX = 0f
                settingsBackCallback.isEnabled = true
            }
        }

        if (savedInstanceState == null) {
            handleWidgetIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWidgetIntent(intent)
        updateDashboard()
    }

    private fun handleWidgetIntent(intent: Intent?) {
        if (intent == null) return
        val fromWidgetDoubleTap = intent.getBooleanExtra(BatteryWidget.EXTRA_CLOSE_SETTINGS, false)
        if (fromWidgetDoubleTap) {
            intent.removeExtra(BatteryWidget.EXTRA_CLOSE_SETTINGS)
            closeSettingsScreen()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val container = findViewById<View>(R.id.settings_container)
        if (container != null && container.visibility == View.VISIBLE) {
            outState.putBoolean(KEY_SETTINGS_OPEN, true)
        }
        val dropdownLanguage = findViewById<View>(R.id.dropdown_language)
        if (dropdownLanguage?.visibility == View.VISIBLE) {
            outState.putBoolean(KEY_LANGUAGE_DROPDOWN_OPEN, true)
        }
        val dropdownFrequency = findViewById<View>(R.id.dropdown_update_frequency)
        if (dropdownFrequency?.visibility == View.VISIBLE) {
            outState.putBoolean(KEY_FREQUENCY_DROPDOWN_OPEN, true)
        }
        val dropdownTheme = findViewById<View>(R.id.dropdown_theme)
        if (dropdownTheme?.visibility == View.VISIBLE) {
            outState.putBoolean(KEY_THEME_DROPDOWN_OPEN, true)
        }
        val dropdownTempUnit = findViewById<View>(R.id.dropdown_temp_unit)
        if (dropdownTempUnit?.visibility == View.VISIBLE) {
            outState.putBoolean(KEY_TEMP_UNIT_DROPDOWN_OPEN, true)
        }
        val dropdownVoltageUnit = findViewById<View>(R.id.dropdown_voltage_unit)
        if (dropdownVoltageUnit?.visibility == View.VISIBLE) {
            outState.putBoolean(KEY_VOLTAGE_UNIT_DROPDOWN_OPEN, true)
        }
        val dropdownRamUnit = findViewById<View>(R.id.dropdown_ram_unit)
        if (dropdownRamUnit?.visibility == View.VISIBLE) {
            outState.putBoolean(KEY_RAM_UNIT_DROPDOWN_OPEN, true)
        }
    }

    override fun onResume() {
        super.onResume()
        loadRefreshInterval()
        updateNotificationPermissionStatuses()
        handler.removeCallbacks(refreshDashboard)
        handler.post(refreshDashboard)
    }

    override fun onUserLeaveHint() {
        handler.post { refreshWidgets() }
        super.onUserLeaveHint()
    }

    override fun onPause() {
        handler.removeCallbacks(refreshDashboard)
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        findViewById<SwipeBackFrameLayout>(R.id.settings_container)?.swipeListener = null
        super.onDestroy()
    }

    private fun setupSettingsScreen(savedInstanceState: Bundle? = null) {
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
        val rbLangSystem = findViewById<RadioButton>(R.id.lang_system)
        val rbVietnamese = findViewById<RadioButton>(R.id.lang_vietnamese)
        val rbEnglish = findViewById<RadioButton>(R.id.lang_english)
        val rbBrainrot = findViewById<RadioButton>(R.id.lang_brainrot)

        val savedLang = getSharedPreferences(BatteryWidget.SETTINGS_PREFS, MODE_PRIVATE)
            .getString(BatteryWidget.APP_LANGUAGE_KEY, "system") ?: "system"

        when (savedLang) {
            "vi" -> {
                rbVietnamese?.isChecked = true
                selectedLanguageText?.text = getString(R.string.language_vietnamese)
            }
            "en" -> {
                rbEnglish?.isChecked = true
                selectedLanguageText?.text = getString(R.string.language_english)
            }
            "art", "en-0rot", "en-x-brainrot", "en-brainrot", "en-BR" -> {
                rbBrainrot?.isChecked = true
                selectedLanguageText?.text = getString(R.string.language_brainrot)
            }
            else -> {
                rbLangSystem?.isChecked = true
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
                Toast.makeText(this, R.string.notification_access_enabled, Toast.LENGTH_SHORT).show()
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
                findViewById<RadioButton>(R.id.freq_500)?.isChecked = true
                R.string.update_every_half_second
            }
            1_000L -> {
                findViewById<RadioButton>(R.id.freq_1000)?.isChecked = true
                R.string.update_every_second
            }
            else -> {
                findViewById<RadioButton>(R.id.freq_2000)?.isChecked = true
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
            getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE).edit()
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
        val rbThemeSystem = findViewById<RadioButton>(R.id.theme_system)
        val rbLight = findViewById<RadioButton>(R.id.theme_light)
        val rbDark = findViewById<RadioButton>(R.id.theme_dark)
        val rbOled = findViewById<RadioButton>(R.id.theme_oled)

        val prefs = getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE)
        val currentTheme = prefs.getString(APP_THEME_KEY, "system") ?: "system"

        when (currentTheme) {
            "light" -> {
                rbLight?.isChecked = true
                selectedThemeText?.text = getString(R.string.theme_light)
            }
            "dark" -> {
                rbDark?.isChecked = true
                selectedThemeText?.text = getString(R.string.theme_dark)
            }
            "oled" -> {
                rbOled?.isChecked = true
                selectedThemeText?.text = getString(R.string.theme_oled)
            }
            else -> {
                rbThemeSystem?.isChecked = true
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
                recreate()
            }
        }

        // 6. Use system battery counter
        val checkboxSystemBattery = findViewById<MaterialCheckBox>(R.id.checkbox_use_system_battery)
        val cardSystemBattery = findViewById<View>(R.id.card_use_system_battery)

        checkboxSystemBattery?.isChecked = BatteryWidget.isUseSystemBatteryCounter(this)

        cardSystemBattery?.setOnClickListener {
            val newValue = !(checkboxSystemBattery?.isChecked ?: false)
            checkboxSystemBattery?.isChecked = newValue
            getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE).edit()
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
        val rbVoltageMv = findViewById<RadioButton>(R.id.voltage_unit_mv)
        val rbVoltageV = findViewById<RadioButton>(R.id.voltage_unit_v)

        val currentVoltageUnit = BatteryWidget.getVoltageUnit(this)
        when (currentVoltageUnit) {
            VoltageUnit.V -> {
                rbVoltageV?.isChecked = true
                selectedVoltageUnitText?.text = getString(R.string.voltage_unit_v)
            }
            else -> {
                rbVoltageMv?.isChecked = true
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

            getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE).edit()
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
        val rbRamMb = findViewById<RadioButton>(R.id.ram_unit_mb)
        val rbRamGb = findViewById<RadioButton>(R.id.ram_unit_gb)

        val currentRamUnit = BatteryWidget.getRamUnit(this)
        when (currentRamUnit) {
            RamUnit.GB -> {
                rbRamGb?.isChecked = true
                selectedRamUnitText?.text = getString(R.string.ram_unit_gb)
            }
            else -> {
                rbRamMb?.isChecked = true
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

            getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE).edit()
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
        val rbCelsius = findViewById<RadioButton>(R.id.temp_unit_celsius)
        val rbFahrenheit = findViewById<RadioButton>(R.id.temp_unit_fahrenheit)
        val rbKelvin = findViewById<RadioButton>(R.id.temp_unit_kelvin)

        val currentTempUnit = BatteryWidget.getTemperatureUnit(this)
        when (currentTempUnit) {
            TempUnit.FAHRENHEIT -> {
                rbFahrenheit?.isChecked = true
                selectedTempUnitText?.text = getString(R.string.temp_unit_fahrenheit)
            }
            TempUnit.KELVIN -> {
                rbKelvin?.isChecked = true
                selectedTempUnitText?.text = getString(R.string.temp_unit_kelvin)
            }
            else -> {
                rbCelsius?.isChecked = true
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

            getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE).edit()
                .putString(BatteryWidget.TEMP_UNIT_KEY, targetUnit.code)
                .apply()
            updateDashboard()
            refreshWidgets()
        }

        updateNotificationPermissionStatuses()
    }

    private fun updateNotificationPermissionStatuses() {
        val statusAllowNotifications = findViewById<TextView>(R.id.status_allow_notifications)
        val statusNotificationAccess = findViewById<TextView>(R.id.status_notification_access)

        val hasPostNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        statusAllowNotifications?.text = if (hasPostNotificationPermission) "✅" else "❌"

        val hasAccess = hasNotificationAccess()
        statusNotificationAccess?.text = if (hasAccess) "✅" else "❌"
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST) {
            updateNotificationPermissionStatuses()
        }
    }

    private fun ensureInitialLanguage() {
        val prefs = getSharedPreferences(BatteryWidget.SETTINGS_PREFS, MODE_PRIVATE)
        var savedLang = prefs.getString(BatteryWidget.APP_LANGUAGE_KEY, "system") ?: "system"
        val validLangs = setOf("system", "vi", "en", "art")

        if (savedLang !in validLangs) {
            savedLang = "system"
            prefs.edit().putString(BatteryWidget.APP_LANGUAGE_KEY, "system").apply()
        }

        val targetLocales = if (savedLang == "system") {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(savedLang)
        }

        val currentLocales = AppCompatDelegate.getApplicationLocales()
        if (currentLocales.toLanguageTags() != targetLocales.toLanguageTags()) {
            AppCompatDelegate.setApplicationLocales(targetLocales)
        }
    }

    private fun setAppLanguage(langCode: String) {
        val prefs = getSharedPreferences(BatteryWidget.SETTINGS_PREFS, MODE_PRIVATE)
        val currentLang = prefs.getString(BatteryWidget.APP_LANGUAGE_KEY, "system") ?: "system"
        if (currentLang != langCode) {
            prefs.edit().putString(BatteryWidget.APP_LANGUAGE_KEY, langCode).apply()

            val appLocales = if (langCode == "system") {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(langCode)
            }

            AppCompatDelegate.setApplicationLocales(appLocales)
            refreshWidgets()
        }
    }

    private fun openSettingsScreen() {
        val container = findViewById<View>(R.id.settings_container) ?: return
        container.visibility = View.VISIBLE
        val width = container.width.toFloat().takeIf { it > 0 } ?: 1080f
        container.translationX = width
        container.animate()
            .translationX(0f)
            .setDuration(280L)
            .setInterpolator(FastOutSlowInInterpolator())
            .withLayer()
            .start()
        settingsBackCallback.isEnabled = true
    }

    private fun closeSettingsScreen() {
        val container = findViewById<View>(R.id.settings_container) ?: return
        if (container.visibility != View.VISIBLE) return
        settingsBackCallback.isEnabled = false
        val width = container.width.toFloat().takeIf { it > 0 } ?: 1080f
        container.animate()
            .translationX(width)
            .setDuration(280L)
            .setInterpolator(FastOutSlowInInterpolator())
            .withLayer()
            .withEndAction {
                container.visibility = View.GONE
            }
            .start()
    }

    private fun setupDashboardSwipeToOpen(dashboardView: View) {
        var startX = 0f
        var startY = 0f
        var isSwipingLeft = false
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop.toFloat()

        dashboardView.setOnTouchListener { _, event ->
            val container = findViewById<View>(R.id.settings_container) ?: return@setOnTouchListener false
            if (container.visibility == View.VISIBLE && container.translationX == 0f) {
                return@setOnTouchListener false
            }
            val screenWidth = dashboardView.width.toFloat().takeIf { it > 0 } ?: 1080f

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    isSwipingLeft = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startX
                    val dy = event.rawY - startY

                    if (!isSwipingLeft && dx < -touchSlop && abs(dx) > abs(dy)) {
                        isSwipingLeft = true
                        container.visibility = View.VISIBLE
                        dashboardView.parent?.requestDisallowInterceptTouchEvent(true)
                    }

                    if (isSwipingLeft) {
                        val targetX = (screenWidth + dx).coerceIn(0f, screenWidth)
                        container.translationX = targetX
                        true
                    } else false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isSwipingLeft) {
                        val currentX = container.translationX
                        if (currentX < screenWidth * 0.75f) {
                            container.animate()
                                .translationX(0f)
                                .setDuration(200L)
                                .setInterpolator(FastOutSlowInInterpolator())
                                .withLayer()
                                .start()
                            settingsBackCallback.isEnabled = true
                        } else {
                            container.animate()
                                .translationX(screenWidth)
                                .setDuration(200L)
                                .setInterpolator(FastOutSlowInInterpolator())
                                .withLayer()
                                .withEndAction { container.visibility = View.GONE }
                                .start()
                        }
                        isSwipingLeft = false
                        true
                    } else if (event.action == MotionEvent.ACTION_UP) {
                        dashboardView.performClick()
                        false
                    } else false
                }
                else -> false
            }
        }
    }

    private fun initializeMetricCards() {
        setBatteryCard(R.string.metric_battery, "—", "")
    }

    private fun applyGraphBackgrounds(theme: String) {
        val isOled = theme == "oled"
        val graphBg = if (isOled) Color.BLACK else Color.TRANSPARENT
        val voltageGraph = findViewById<HistoryGraphView>(R.id.voltage_graph)
        val ramGraph = findViewById<HistoryGraphView>(R.id.ram_graph)
        val tempGraph = findViewById<HistoryGraphView>(R.id.temperature_graph)

        voltageGraph?.setBackgroundColor(graphBg)
        ramGraph?.setBackgroundColor(graphBg)
        tempGraph?.setBackgroundColor(graphBg)

        val plotBg = if (isOled) Color.BLACK else Color.TRANSPARENT
        voltageGraph?.setPlotBackgroundColor(plotBg)
        ramGraph?.setPlotBackgroundColor(plotBg)
        tempGraph?.setPlotBackgroundColor(plotBg)

        val cardBattery = findViewById<com.google.android.material.card.MaterialCardView>(R.id.card_battery)
        val cardNetwork = findViewById<com.google.android.material.card.MaterialCardView>(R.id.card_network)
        val cardVoltage = findViewById<com.google.android.material.card.MaterialCardView>(R.id.card_voltage_graph)
        val cardRam = findViewById<com.google.android.material.card.MaterialCardView>(R.id.card_ram_graph)
        val cardTemp = findViewById<com.google.android.material.card.MaterialCardView>(R.id.card_temperature_graph)

        val strokeWidthPx = (resources.displayMetrics.density * 1f).toInt()
        val defaultSurface = com.google.android.material.color.MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorSurface, Color.TRANSPARENT
        )
        val outlineColor = com.google.android.material.color.MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorOutline, Color.GRAY
        )

        val dashboardCards = arrayOf(cardBattery, cardNetwork, cardVoltage, cardRam, cardTemp)
        dashboardCards.forEach { card ->
            card?.strokeColor = outlineColor
        }

        if (isOled) {
            cardBattery?.setCardBackgroundColor(Color.TRANSPARENT)
            cardNetwork?.setCardBackgroundColor(Color.TRANSPARENT)
            cardVoltage?.setCardBackgroundColor(Color.BLACK)
            cardRam?.setCardBackgroundColor(Color.BLACK)
            cardTemp?.setCardBackgroundColor(Color.BLACK)

            cardBattery?.strokeWidth = strokeWidthPx
            cardNetwork?.strokeWidth = strokeWidthPx
            cardVoltage?.strokeWidth = strokeWidthPx
            cardRam?.strokeWidth = strokeWidthPx
            cardTemp?.strokeWidth = strokeWidthPx
        } else {
            cardBattery?.setCardBackgroundColor(defaultSurface)
            cardNetwork?.setCardBackgroundColor(defaultSurface)
            cardVoltage?.setCardBackgroundColor(defaultSurface)
            cardRam?.setCardBackgroundColor(defaultSurface)
            cardTemp?.setCardBackgroundColor(defaultSurface)

            cardBattery?.strokeWidth = 0
            cardNetwork?.strokeWidth = 0
            cardVoltage?.strokeWidth = 0
            cardRam?.strokeWidth = 0
            cardTemp?.strokeWidth = 0
        }

        val settingsCardIds = intArrayOf(
            R.id.card_language,
            R.id.card_allow_notifications,
            R.id.card_notification_access,
            R.id.card_update_frequency,
            R.id.card_theme,
            R.id.card_use_system_battery,
            R.id.card_voltage_unit,
            R.id.card_ram_unit,
            R.id.card_temp_unit
        )

        settingsCardIds.forEach { cardId ->
            findViewById<com.google.android.material.card.MaterialCardView>(cardId)?.let { card ->
                card.strokeColor = outlineColor
                if (isOled) {
                    card.setCardBackgroundColor(Color.TRANSPARENT)
                    card.strokeWidth = strokeWidthPx
                } else {
                    card.setCardBackgroundColor(defaultSurface)
                    card.strokeWidth = 0
                }
            }
        }
    }

    private fun initDashboardViews() {
        tvVoltageCurrent = findViewById(R.id.voltage_current)
        tvTemperatureCurrent = findViewById(R.id.temperature_current)
        tvRamCurrent = findViewById(R.id.ram_current)
        tvNetworkRx = findViewById(R.id.network_rx)
        tvNetworkTx = findViewById(R.id.network_tx)
        voltageGraph = findViewById(R.id.voltage_graph)
        ramGraph = findViewById(R.id.ram_graph)
        tempGraph = findViewById(R.id.temperature_graph)
        val cardBattery = findViewById<View>(R.id.card_battery)
        cardBatteryTitle = cardBattery?.findViewById(R.id.metric_title)
        cardBatteryValue = cardBattery?.findViewById(R.id.metric_value)
        cardBatteryDetail = cardBattery?.findViewById(R.id.metric_detail)
    }

    private fun setBatteryCard(titleId: Int, value: String, detail: String = "") {
        cardBatteryTitle?.text = getString(titleId)
        cardBatteryValue?.text = value
        cardBatteryDetail?.text = detail
    }

    private fun refreshWidgets() {
        BatteryWidget.updateAllWidgets(this, android.appwidget.AppWidgetManager.getInstance(this))
    }

    private fun hasNotificationAccess(): Boolean {
        val component = ComponentName(this, HotspotNotificationListener::class.java)
        return getSystemService(NotificationManager::class.java)
            .isNotificationListenerAccessGranted(component)
    }

    private fun updateDashboard() {
        val battery = registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val voltageMv = battery?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val temperatureC = (battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val sampleTime = SystemClock.elapsedRealtime()
        val smoothedVoltageMv = BatteryWidget.getSmoothedVoltage(voltageMv, sampleTime)
        val isUseSystemBattery = BatteryWidget.isUseSystemBatteryCounter(this)
        val batteryPercent = if (voltageMv > 0 || isUseSystemBattery) {
            BatteryWidget.getBatteryPercentage(isUseSystemBattery, battery, smoothedVoltageMv)
        } else null
        val dash = getString(R.string.placeholder_dash)
        val batteryValue = batteryPercent?.let { "$it%" } ?: dash
        setBatteryCard(R.string.metric_battery, batteryValue)

        val voltageUnit = BatteryWidget.getVoltageUnit(this)
        val ramUnit = BatteryWidget.getRamUnit(this)

        tvVoltageCurrent?.text = if (voltageMv > 0) {
            BatteryWidget.formatVoltage(voltageMv, voltageUnit)
        } else dash
        tvTemperatureCurrent?.text =
            BatteryWidget.formatDashboardTemperature(this, temperatureC)
        if (voltageMv > 0) addSample(voltageHistory, voltageMv.toFloat(), sampleTime)
        if (battery != null) addSample(temperatureHistory, temperatureC, sampleTime)

        val memory = getSystemService(ActivityManager::class.java)
        memory?.getMemoryInfo(memoryInfo)
        val totalMb = memoryInfo.totalMem / (1024L * 1024L)
        val availableMb = memoryInfo.availMem / (1024L * 1024L)
        val usedMb = (totalMb - availableMb).coerceAtLeast(0L)
        val usedPercent = if (totalMb > 0) (usedMb * 100 / totalMb).toInt() else 0
        addSample(ramHistory, usedMb.toFloat(), sampleTime)
        tvRamCurrent?.text = getString(R.string.current_ram_format, usedPercent)

        updateNetworkStats()

        val (minVoltage, maxVoltage, voltageSuffix) = when (voltageUnit) {
            VoltageUnit.MV -> Triple(3300f, 4300f, " " + getString(R.string.unit_mv))
            VoltageUnit.V -> Triple(3.3f, 4.3f, " " + getString(R.string.unit_v))
        }
        voltageGraph?.setHistory(
            voltageHistory, minVoltage, maxVoltage, voltageSuffix, sampleTime,
            valueTransform = { rawMv -> BatteryWidget.convertVoltage(rawMv, voltageUnit) }
        ) { voltage ->
            val rawMv = BatteryWidget.convertToVoltageMv(voltage, voltageUnit).toInt()
            val percent = BatteryWidget.getBatteryPercentage(isUseSystemBattery, battery, rawMv)
            BatteryWidget.percentageColor(percent)
        }

        val maxRam = BatteryWidget.convertRam(totalMb.toFloat(), ramUnit).coerceAtLeast(0.1f)
        val ramSuffix = " " + getString(if (ramUnit == RamUnit.GB) R.string.unit_gb else R.string.unit_mb)

        ramGraph?.setHistory(
            ramHistory, 0f, maxRam, ramSuffix, sampleTime,
            valueTransform = { rawMb -> BatteryWidget.convertRam(rawMb, ramUnit) }
        ) { ramVal ->
            val ramMb = BatteryWidget.convertToRamMb(ramVal, ramUnit)
            val ramUsedPercent = if (totalMb > 0) ramMb * 100f / totalMb else 0f
            when {
                ramUsedPercent >= 90f -> Color.rgb(255, 82, 82)
                ramUsedPercent >= 80f -> Color.rgb(255, 179, 0)
                else -> Color.rgb(0, 230, 118)
            }
        }
        val tempUnit = BatteryWidget.getTemperatureUnit(this)
        val (minTemp, maxTemp, tempSuffix) = when (tempUnit) {
            TempUnit.CELSIUS -> Triple(0f, 60f, getString(R.string.unit_celsius))
            TempUnit.FAHRENHEIT -> Triple(32f, 140f, getString(R.string.unit_fahrenheit))
            TempUnit.KELVIN -> Triple(273f, 333f, " " + getString(R.string.unit_kelvin))
        }
        tempGraph?.setHistory(
            temperatureHistory, minTemp, maxTemp, tempSuffix, sampleTime,
            valueTransform = { rawC -> BatteryWidget.convertTemperature(rawC, tempUnit) }
        ) { tempVal ->
            val celsius = BatteryWidget.convertToCelsius(tempVal, tempUnit)
            BatteryDetailsWidget.temperatureColor(celsius)
        }
    }

    private fun updateNetworkStats() {
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        val now = SystemClock.elapsedRealtime()
        val elapsedSeconds = ((now - previousNetworkTime).coerceAtLeast(1L)) / 1000f
        val rxRate = if (previousRx >= 0 && rx >= previousRx) ((rx - previousRx) / elapsedSeconds).toLong() else 0L
        val txRate = if (previousTx >= 0 && tx >= previousTx) ((tx - previousTx) / elapsedSeconds).toLong() else 0L
        previousRx = rx
        previousTx = tx
        previousNetworkTime = now
        tvNetworkRx?.text = if (rx == TrafficStats.UNSUPPORTED.toLong()) {
            getString(R.string.network_unavailable)
        } else getString(R.string.network_receiving_format, formatBytes(rx), formatBytes(rxRate))
        tvNetworkTx?.text = if (tx == TrafficStats.UNSUPPORTED.toLong()) {
            getString(R.string.network_unavailable)
        } else getString(R.string.network_sending_format, formatBytes(tx), formatBytes(txRate))
    }

    private fun addSample(history: ArrayDeque<HistoryGraphView.Sample>, value: Float, timestampMs: Long) {
        val earliest = timestampMs - HISTORY_WINDOW_MS
        while (history.isNotEmpty() && history.first().timestampMs < earliest) history.removeFirst()
        if (history.size == MAX_HISTORY_POINTS) history.removeFirst()
        history.addLast(HistoryGraphView.Sample(timestampMs, value))
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        var value = bytes.toDouble()
        var unit = -1
        while (value >= 1024 && unit < BYTE_UNITS.lastIndex) {
            value /= 1024
            unit++
        }
        return String.format(Locale.getDefault(), "%.1f %s", value, BYTE_UNITS[unit])
    }

    private fun openNotificationAccessSettings() {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.notification_access_settings_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private fun loadRefreshInterval() {
        val saved = getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE)
            .getLong(REFRESH_INTERVAL_KEY, DEFAULT_REFRESH_INTERVAL_MS)
        refreshIntervalMs = saved.takeIf { it == 500L || it == 1_000L || it == 2_000L }
            ?: DEFAULT_REFRESH_INTERVAL_MS
    }

    companion object {
        private val BYTE_UNITS = arrayOf("KB", "MB", "GB", "TB")
        private const val HISTORY_WINDOW_MS = 60_000L
        private const val MAX_HISTORY_POINTS = 240
        private const val SETTINGS_PREFS = BatteryWidget.SETTINGS_PREFS
        private const val REFRESH_INTERVAL_KEY = "refresh_interval_ms"
        private const val DEFAULT_REFRESH_INTERVAL_MS = 1_000L
        private const val NOTIFICATION_PERMISSION_REQUEST = 1001
        private const val KEY_SETTINGS_OPEN = "key_settings_open"
        private const val KEY_LANGUAGE_DROPDOWN_OPEN = "key_language_dropdown_open"
        private const val KEY_FREQUENCY_DROPDOWN_OPEN = "key_frequency_dropdown_open"
        private const val APP_THEME_KEY = "app_theme"
        private const val KEY_THEME_DROPDOWN_OPEN = "key_theme_dropdown_open"
        private const val KEY_TEMP_UNIT_DROPDOWN_OPEN = "key_temp_unit_dropdown_open"
        private const val KEY_VOLTAGE_UNIT_DROPDOWN_OPEN = "key_voltage_unit_dropdown_open"
        private const val KEY_RAM_UNIT_DROPDOWN_OPEN = "key_ram_unit_dropdown_open"
    }
}