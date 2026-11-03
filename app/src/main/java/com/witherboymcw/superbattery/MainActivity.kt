package com.witherboymcw.superbattery

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
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
        super.onCreate(savedInstanceState)
        startService(Intent(this, BatteryMonitorService::class.java))
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
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
    }

    override fun onResume() {
        super.onResume()
        loadRefreshInterval()
        refreshWidgets()
        updateNotificationPermissionStatuses()
        handler.removeCallbacks(refreshDashboard)
        handler.post(refreshDashboard)
    }

    override fun onUserLeaveHint() {
        refreshWidgets()
        super.onUserLeaveHint()
    }

    override fun onPause() {
        handler.removeCallbacks(refreshDashboard)
        super.onPause()
    }

    private fun setupSettingsScreen(savedInstanceState: Bundle? = null) {
        val swipeContainer = findViewById<SwipeBackFrameLayout>(R.id.settings_container) ?: return

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
        val rbVietnamese = findViewById<RadioButton>(R.id.lang_vietnamese)
        val rbEnglish = findViewById<RadioButton>(R.id.lang_english)

        val savedLang = getSharedPreferences(BatteryWidget.SETTINGS_PREFS, MODE_PRIVATE)
            .getString(BatteryWidget.APP_LANGUAGE_KEY, null)
        val currentLocales = AppCompatDelegate.getApplicationLocales()
        val currentTag = savedLang ?: if (!currentLocales.isEmpty) currentLocales[0]?.language else Locale.getDefault().language
        if (currentTag == "vi") {
            rbVietnamese?.isChecked = true
            selectedLanguageText?.text = getString(R.string.language_vietnamese)
        } else {
            rbEnglish?.isChecked = true
            selectedLanguageText?.text = getString(R.string.language_english)
        }

        langGroup?.setOnCheckedChangeListener { _, checkedId ->
            val targetLang = if (checkedId == R.id.lang_vietnamese) "vi" else "en"
            selectedLanguageText?.text = if (checkedId == R.id.lang_vietnamese) {
                getString(R.string.language_vietnamese)
            } else {
                getString(R.string.language_english)
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

        // 5. Use system battery counter
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

    private fun setAppLanguage(langCode: String) {
        val prefs = getSharedPreferences(BatteryWidget.SETTINGS_PREFS, MODE_PRIVATE)
        val currentLang = prefs.getString(BatteryWidget.APP_LANGUAGE_KEY, null)
        if (currentLang != langCode) {
            prefs.edit().putString(BatteryWidget.APP_LANGUAGE_KEY, langCode).commit()
            val locale = Locale.forLanguageTag(langCode)
            Locale.setDefault(locale)
            val appLocales = LocaleListCompat.forLanguageTags(langCode)
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
        val touchSlop = 20f

        dashboardView.setOnTouchListener { _, event ->
            val container = findViewById<View>(R.id.settings_container) ?: return@setOnTouchListener false
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
                    } else false
                }
                else -> false
            }
        }
    }

    private fun initializeMetricCards() {
        setCard(R.id.card_battery, R.string.metric_battery, "—", "")
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
            BatteryWidget.getBatteryPercentage(this, battery, smoothedVoltageMv)
        } else null
        val batteryValue = batteryPercent?.let { "$it%" } ?: "—"
        setCard(
            R.id.card_battery,
            R.string.metric_battery,
            batteryValue,
            ""
        )
        findViewById<TextView>(R.id.voltage_current).text = if (voltageMv > 0) {
            getString(R.string.current_voltage_format, voltageMv)
        } else "—"
        findViewById<TextView>(R.id.temperature_current).text = getString(
            R.string.current_temperature_format,
            temperatureC
        )
        if (voltageMv > 0) addSample(voltageHistory, voltageMv.toFloat(), sampleTime)
        if (battery != null) addSample(temperatureHistory, temperatureC, sampleTime)

        val memory = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        memory.getMemoryInfo(memoryInfo)
        val totalMb = memoryInfo.totalMem / (1024L * 1024L)
        val availableMb = memoryInfo.availMem / (1024L * 1024L)
        val usedMb = (totalMb - availableMb).coerceAtLeast(0L)
        val usedPercent = if (totalMb > 0) (usedMb * 100 / totalMb).toInt() else 0
        addSample(ramHistory, usedMb.toFloat(), sampleTime)
        findViewById<TextView>(R.id.ram_current).text = getString(R.string.current_ram_format, usedPercent)

        updateNetworkStats()
        findViewById<HistoryGraphView>(R.id.voltage_graph).setHistory(
            voltageHistory.toList(), 3300f, 4300f, " mV", sampleTime
        ) { voltage ->
            val percent = BatteryWidget.getBatteryPercentage(this, battery, voltage.toInt())
            BatteryWidget.percentageColor(percent)
        }
        findViewById<HistoryGraphView>(R.id.ram_graph).setHistory(
            ramHistory.toList(), 0f, totalMb.toFloat().coerceAtLeast(1f), " MB", sampleTime
        ) { percent ->
            val usedPercent = if (totalMb > 0) percent * 100f / totalMb else 0f
            when {
                usedPercent >= 90f -> Color.rgb(255, 82, 82)
                usedPercent >= 80f -> Color.rgb(255, 179, 0)
                else -> Color.rgb(0, 230, 118)
            }
        }
        findViewById<HistoryGraphView>(R.id.temperature_graph).setHistory(
            temperatureHistory.toList(), 0f, 60f, "°C", sampleTime
        ) { temperature -> BatteryDetailsWidget.temperatureColor(temperature) }
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
        findViewById<TextView>(R.id.network_rx).text = if (rx == TrafficStats.UNSUPPORTED.toLong()) {
            getString(R.string.network_unavailable)
        } else getString(R.string.network_receiving_format, formatBytes(rx), formatBytes(rxRate))
        findViewById<TextView>(R.id.network_tx).text = if (tx == TrafficStats.UNSUPPORTED.toLong()) {
            getString(R.string.network_unavailable)
        } else getString(R.string.network_sending_format, formatBytes(tx), formatBytes(txRate))
    }

    private fun setCard(cardId: Int, titleId: Int, value: String, detail: String) {
        val card = findViewById<View>(cardId)
        card.findViewById<TextView>(R.id.metric_title).text = getString(titleId)
        card.findViewById<TextView>(R.id.metric_value).text = value
        card.findViewById<TextView>(R.id.metric_detail).text = detail
    }

    private fun addSample(history: ArrayDeque<HistoryGraphView.Sample>, value: Float, timestampMs: Long) {
        val earliest = timestampMs - HISTORY_WINDOW_MS
        while (history.isNotEmpty() && history.first().timestampMs < earliest) history.removeFirst()
        if (history.size == MAX_HISTORY_POINTS) history.removeFirst()
        history.addLast(HistoryGraphView.Sample(timestampMs, value))
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unit = -1
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024
            unit++
        }
        return String.format(Locale.getDefault(), "%.1f %s", value, units[unit])
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
        private const val HISTORY_WINDOW_MS = 60_000L
        private const val MAX_HISTORY_POINTS = 240
        private const val SETTINGS_PREFS = BatteryWidget.SETTINGS_PREFS
        private const val REFRESH_INTERVAL_KEY = "refresh_interval_ms"
        private const val DEFAULT_REFRESH_INTERVAL_MS = 2_000L
        private const val NOTIFICATION_PERMISSION_REQUEST = 1001
        private const val KEY_SETTINGS_OPEN = "key_settings_open"
        private const val KEY_LANGUAGE_DROPDOWN_OPEN = "key_language_dropdown_open"
        private const val KEY_FREQUENCY_DROPDOWN_OPEN = "key_frequency_dropdown_open"
    }
}