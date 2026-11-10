package com.witherboymcw.superbattery

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private val settingsViewController = SettingsViewController()
    private val hotspotViewController = HotspotViewController()
    private val dashboardViewModel by lazy { ViewModelProvider(this)[DashboardViewModel::class.java] }
    internal val handler = Handler(Looper.getMainLooper())
    internal var refreshIntervalMs = DEFAULT_REFRESH_INTERVAL_MS

    private var tvVoltageCurrent: TextView? = null
    private var tvTemperatureCurrent: TextView? = null
    private var tvRamCurrent: TextView? = null
    private var tvVoltagePower: TextView? = null
    private var tvRamUsageDetail: TextView? = null
    private var tvTemperatureStatus: TextView? = null
    private var tvNetTxTotal: TextView? = null
    private var tvNetTxSpeed: TextView? = null
    private var tvNetRxTotal: TextView? = null
    private var tvNetRxSpeed: TextView? = null
    private var voltageGraph: HistoryGraphView? = null
    private var ramGraph: HistoryGraphView? = null
    private var tempGraph: HistoryGraphView? = null
    private var cardBatteryTitle: TextView? = null
    private var cardBatteryValue: TextView? = null
    private var cardBatteryDetail: TextView? = null
    private var batteryRing: BatteryRingView? = null
    internal var btnSettings: View? = null
    internal var thickChinOverrideLevel: Int? = null
    private val memoryInfo = ActivityManager.MemoryInfo()
    private var lastNetworkCardTapTime = 0L

    private var tvHeaderClock: TextView? = null
    private var btnRingerModeToggle: ImageButton? = null
    private var ivNetworkWifi: ImageView? = null
    private var ivNetworkBluetooth: ImageView? = null
    private var ivNetworkSim1: ImageView? = null

    private lateinit var systemMonitorManager: SystemMonitorManager

    private val clockFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val clockRunnable = object : Runnable {
        override fun run() {
            tvHeaderClock?.text = clockFormat.format(Date())
            handler.postDelayed(this, 1_000L)
        }
    }

    internal val refreshDashboard = object : Runnable {
        override fun run() {
            updateDashboard()
            handler.postDelayed(this, refreshIntervalMs)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ensureInitialLanguage()
        OledThemeAllInOne.applyActivityTheme(this)

        super.onCreate(savedInstanceState)
        systemMonitorManager = SystemMonitorManager(this)
        try {
            startService(Intent(this, BatteryMonitorService::class.java))
        } catch (_: Exception) {
            // Guard against background service execution limits on Android 8.0+
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        initDashboardViews()
        applyThickChinPadding()
        applyGraphBackgrounds(OledThemeAllInOne.getEffectiveThemeForGraph(this))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
            windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.setPadding(navBars.left, 0, navBars.right, navBars.bottom)
            applyThickChinPadding()
            CornerAwareness.applyAsymmetricCorners(this, insets)
            NotchAwareness.updateAllHeaders(this)
            insets
        }
        loadRefreshInterval()
        initializeMetricCards()
        setupSettingsScreen(savedInstanceState)
        setupHotspotScreen(savedInstanceState)

        btnSettings = findViewById(R.id.settings_button)
        btnSettings?.setOnClickListener {
            settingsViewController.openScreen(this)
        }

        val cardNetwork = findViewById<View>(R.id.card_network)
        cardNetwork?.setOnClickListener {
            val now = SystemClock.elapsedRealtime()
            val diff = now - lastNetworkCardTapTime
            if (diff in 50L..400L) {
                lastNetworkCardTapTime = 0L
                if (!hotspotViewController.isOpen) {
                    hotspotViewController.openScreen(this)
                }
            } else {
                lastNetworkCardTapTime = now
            }
        }

        findViewById<View>(R.id.dashboard)?.let { dashboard ->
            DrawerNavigationHelper.setupDashboardSwipeToOpen(
                this, dashboard, settingsViewController, hotspotViewController
            )
        }

        if (savedInstanceState?.getBoolean(KEY_SETTINGS_OPEN) == true) {
            settingsViewController.restoreOpenState(this)
        }

        if (savedInstanceState?.getBoolean(KEY_HOTSPOT_OPEN) == true) {
            hotspotViewController.restoreOpenState(this)
        }

        if (savedInstanceState == null) {
            handleWidgetIntent(intent)
        }

        updateFoldableUiState()
        lifecycleScope.launch {
            try {
                WindowInfoTracker.getOrCreate(this@MainActivity)
                    .windowLayoutInfo(this@MainActivity)
                    .collect { layoutInfo ->
                        val hasFoldingFeature = layoutInfo.displayFeatures.any { it is FoldingFeature }
                        if (hasFoldingFeature) {
                            FoldableDetector.markFoldingFeatureDetected(true)
                            applyThickChinPadding()
                            updateFoldableUiState()
                        }
                    }
            } catch (_: Exception) {}
        }
    }

    internal fun updateFoldableUiState() {
        ChinAwareness.checkFoldableChinVisibility(this)
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
            settingsViewController.closeScreen(this, animate = false)
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            val currentFocusedView = currentFocus
            if (currentFocusedView is EditText) {
                val outRect = Rect()
                currentFocusedView.getGlobalVisibleRect(outRect)
                if (!outRect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                    currentFocusedView.clearFocus()
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.hideSoftInputFromWindow(currentFocusedView.windowToken, 0)
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val container = findViewById<View>(R.id.settings_container)
        if (settingsViewController.isOpen || (container != null && container.visibility == View.VISIBLE)) {
            outState.putBoolean(KEY_SETTINGS_OPEN, true)
        }
        val hotspotContainer = findViewById<View>(R.id.hotspot_container)
        if (hotspotViewController.isOpen || (hotspotContainer != null && hotspotContainer.visibility == View.VISIBLE)) {
            outState.putBoolean(KEY_HOTSPOT_OPEN, true)
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
        val dropdownThickChin = findViewById<View>(R.id.dropdown_thick_chin)
        if (dropdownThickChin?.visibility == View.VISIBLE) {
            outState.putBoolean(ChinAwareness.KEY_THICK_CHIN_DROPDOWN_OPEN, true)
        }
        val dropdownAssumeOled = findViewById<View>(R.id.dropdown_assume_oled)
        if (dropdownAssumeOled?.visibility == View.VISIBLE) {
            outState.putBoolean(OledThemeAllInOne.KEY_ASSUME_OLED_DROPDOWN_OPEN, true)
        }
        val dropdownNotch = findViewById<View>(R.id.dropdown_notch)
        if (dropdownNotch?.visibility == View.VISIBLE) {
            outState.putBoolean(SettingsViewController.KEY_NOTCH_DROPDOWN_OPEN, true)
        }
        val scrollView = findViewById<android.widget.ScrollView>(R.id.settings_scroll_view)
            ?: findViewById<android.widget.ScrollView>(R.id.settings_scroll_left)
        scrollView?.let {
            outState.putInt(KEY_SETTINGS_SCROLL_Y, it.scrollY)
        }
        val rightScrollView = findViewById<android.widget.ScrollView>(R.id.settings_scroll_right)
        rightScrollView?.let {
            outState.putInt(KEY_SETTINGS_SCROLL_RIGHT_Y, it.scrollY)
        }
    }

    override fun onResume() {
        super.onResume()
        loadRefreshInterval()
        updatePermissionStatuses()
        handler.removeCallbacks(refreshDashboard)
        handler.post(refreshDashboard)
        handler.removeCallbacks(clockRunnable)
        handler.post(clockRunnable)

        systemMonitorManager.setListener(object : SystemMonitorManager.Listener {
            override fun onBluetoothStateChanged(isEnabled: Boolean) {
                updateBluetoothStatus(isEnabled)
            }

            override fun onRingerModeChanged(mode: Int) {
                updateRingerModeIcon(mode)
            }

            override fun onSimStateChanged(isReady: Boolean) {
                updateSimSignalStatus(isReady)
            }

            override fun onBatteryChanged(batteryInfo: BatteryInfo) {
                updateDashboard()
            }

            override fun onWifiStateChanged(isConnected: Boolean) {
                updateWifiStatus(isConnected)
            }

            override fun onNetworkSpeedChanged(networkSpeed: NetworkSpeed) {
                updateNetworkSpeedUi(networkSpeed)
            }
        })
        systemMonitorManager.startMonitoring()

        updateRingerModeIcon()
        updateBluetoothStatus()
        updateSimSignalStatus()
        updateWifiStatus()
        NotchAwareness.updateAllHeaders(this)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        NotchAwareness.updateAllHeaders(this)
        applyThickChinPadding()

        val hotspotContainer = findViewById<View>(R.id.hotspot_container)
        if (hotspotViewController.isOpen || (hotspotContainer != null && hotspotContainer.visibility == View.VISIBLE)) {
            hotspotViewController.restoreOpenState(this)
        }

        applyGraphBackgrounds(OledThemeAllInOne.getEffectiveThemeForGraph(this))
        applySettingsCardTheme()
        applyHotspotCardTheme()
    }

    override fun onUserLeaveHint() {
        handler.post { refreshWidgets() }
        super.onUserLeaveHint()
    }

    override fun onPause() {
        settingsViewController.flushThickChinCommit()
        handler.removeCallbacks(refreshDashboard)
        handler.removeCallbacks(clockRunnable)
        systemMonitorManager.stopMonitoring()
        systemMonitorManager.setListener(null)
        super.onPause()
    }

    override fun onDestroy() {
        settingsViewController.unbind()
        handler.removeCallbacksAndMessages(null)
        (findViewById<android.view.View>(R.id.settings_container) as? SwipeBackFrameLayout)?.swipeListener = null
        super.onDestroy()
    }

    private fun setupSettingsScreen(savedInstanceState: Bundle? = null) {
        settingsViewController.bind(this, savedInstanceState)
    }

    private fun setupHotspotScreen(savedInstanceState: Bundle? = null) {
        hotspotViewController.bind(this, savedInstanceState)
    }

    internal fun updatePermissionStatuses() {
        val statusAllowNotifications = findViewById<TextView>(R.id.status_allow_notifications)
        val statusNotificationAccess = findViewById<TextView>(R.id.status_notification_access)
        val statusPhoneState = findViewById<TextView>(R.id.status_phone_state_permission)

        val hasPostNotificationPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        statusAllowNotifications?.text = if (hasPostNotificationPermission) "✅" else "❌"

        val hasAccess = hasNotificationAccess()
        statusNotificationAccess?.text = if (hasAccess) "✅" else "❌"

        val hasPhoneState = checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        statusPhoneState?.text = if (hasPhoneState) "✅" else "❌"
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST) {
            updatePermissionStatuses()
        } else if (requestCode == PHONE_STATE_PERMISSION_REQUEST) {
            updatePermissionStatuses()
            updateSimSignalStatus()
        }
    }

    private fun ensureInitialLanguage() {
        val prefs = getSharedPreferences(BatteryWidget.SETTINGS_PREFS, MODE_PRIVATE)
        var savedLang = prefs.getString(BatteryWidget.APP_LANGUAGE_KEY, "system") ?: "system"
        val validLangs = setOf("system", "vi", "en", "art", "vi-slang")

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

    internal fun setAppLanguage(langCode: String) {
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

    private fun initializeMetricCards() {
        setBatteryCard(R.string.metric_battery, "—", 0)
    }

    private fun applyGraphBackgrounds(theme: String) {
        val isOled = theme == "oled"
        val voltageGraph = findViewById<HistoryGraphView>(R.id.voltage_graph)
        val ramGraph = findViewById<HistoryGraphView>(R.id.ram_graph)
        val tempGraph = findViewById<HistoryGraphView>(R.id.temperature_graph)

        OledThemeAllInOne.applyGraphTheme(this, voltageGraph, ramGraph, tempGraph, isOled = isOled)

        val cardIds = intArrayOf(
            R.id.card_battery,
            R.id.card_network,
            R.id.card_voltage_graph,
            R.id.card_ram_graph,
            R.id.card_temperature_graph
        )
        cardIds.forEach { id ->
            OledThemeAllInOne.applyCardTheme(findViewById<com.google.android.material.card.MaterialCardView>(id), this, isOled = isOled)
        }

        applySettingsCardTheme()
    }

    internal fun applySettingsCardTheme() {
        val isOled = OledThemeAllInOne.isOledThemeActive(this)

        val settingsCardIds = intArrayOf(
            R.id.card_language,
            R.id.card_allow_notifications,
            R.id.card_notification_access,
            R.id.card_phone_state_permission,
            R.id.card_update_frequency,
            R.id.card_theme,
            R.id.card_use_system_battery,
            R.id.card_voltage_unit,
            R.id.card_ram_unit,
            R.id.card_temp_unit,
            R.id.card_thick_chin,
            R.id.card_notch,
            R.id.card_assume_oled,
            R.id.settings_detail_card
        )

        settingsCardIds.forEach { cardId ->
            OledThemeAllInOne.applyCardTheme(findViewById<com.google.android.material.card.MaterialCardView>(cardId), this, isOled = isOled)
        }

        OledThemeAllInOne.applySolidCardTheme(findViewById(R.id.card_settings_back), this)
        applyHotspotCardTheme()
    }

    internal fun applyHotspotCardTheme() {
        val isOled = OledThemeAllInOne.isOledThemeActive(this)

        OledThemeAllInOne.applyCardThemeToContainer(findViewById(R.id.hotspot_cards_container), this, isOled = isOled)
        OledThemeAllInOne.applyCardThemeToContainer(findViewById(R.id.hotspot_cards_left_container), this, isOled = isOled)
        OledThemeAllInOne.applyCardTheme(findViewById<com.google.android.material.card.MaterialCardView>(R.id.hotspot_detail_card), this, isOled = isOled)
        OledThemeAllInOne.applySolidCardTheme(findViewById(R.id.card_hotspot_back), this)
    }

    private fun initDashboardViews() {
        tvVoltageCurrent = findViewById(R.id.voltage_current)
        tvTemperatureCurrent = findViewById(R.id.temperature_current)
        tvRamCurrent = findViewById(R.id.ram_current)
        tvNetTxTotal = findViewById(R.id.net_tx_total)
        tvNetTxSpeed = findViewById(R.id.net_tx_speed)
        tvNetRxTotal = findViewById(R.id.net_rx_total)
        tvNetRxSpeed = findViewById(R.id.net_rx_speed)
        voltageGraph = findViewById(R.id.voltage_graph)
        ramGraph = findViewById(R.id.ram_graph)
        tempGraph = findViewById(R.id.temperature_graph)
        ramGraph?.suppressMinYLabel = true
        tempGraph?.suppressMinYLabel = true
        val cardBattery = findViewById<View>(R.id.card_battery)
        cardBatteryTitle = cardBattery?.findViewById(R.id.metric_title)
        cardBatteryValue = cardBattery?.findViewById(R.id.metric_value)
        cardBatteryDetail = cardBattery?.findViewById(R.id.metric_detail)
        batteryRing = cardBattery?.findViewById(R.id.battery_ring)
        updateBatteryRingGeometry()

        tvHeaderClock = findViewById(R.id.tv_header_clock)
        btnRingerModeToggle = findViewById(R.id.btn_ringer_mode_toggle)
        ivNetworkWifi = findViewById(R.id.iv_network_wifi)
        ivNetworkBluetooth = findViewById(R.id.iv_network_bluetooth)
        ivNetworkSim1 = findViewById(R.id.iv_network_sim_1)

        tvVoltagePower = findViewById(R.id.tv_voltage_power)
        tvRamUsageDetail = findViewById(R.id.tv_ram_usage_detail)
        tvTemperatureStatus = findViewById(R.id.tv_temperature_status)

        setupRingerModeControls()
        setupNetworkCardIndicators()

        val headerVoltage: View? = findViewById(R.id.header_voltage_graph)
        voltageGraph?.onAxisOffsetsChanged = { leftOffset, rightOffset, plotWidth ->
            headerVoltage?.setPaddingRelative(
                leftOffset.toInt(),
                headerVoltage.paddingTop,
                rightOffset.toInt(),
                headerVoltage.paddingBottom
            )
            val margin20s = (plotWidth / 3f).toInt()
            (tvVoltagePower?.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                if (lp.marginEnd != margin20s) {
                    lp.marginEnd = margin20s
                    tvVoltagePower?.layoutParams = lp
                }
            }
        }

        val headerRam: View? = findViewById(R.id.header_ram_graph)
        ramGraph?.onAxisOffsetsChanged = { leftOffset, rightOffset, plotWidth ->
            headerRam?.setPaddingRelative(
                leftOffset.toInt(),
                headerRam.paddingTop,
                rightOffset.toInt(),
                headerRam.paddingBottom
            )
            val margin20s = (plotWidth / 3f).toInt()
            (tvRamCurrent?.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                if (lp.marginEnd != margin20s) {
                    lp.marginEnd = margin20s
                    tvRamCurrent?.layoutParams = lp
                }
            }
        }

        val headerTemp: View? = findViewById(R.id.header_temperature_graph)
        tempGraph?.onAxisOffsetsChanged = { leftOffset, rightOffset, plotWidth ->
            headerTemp?.setPaddingRelative(
                leftOffset.toInt(),
                headerTemp.paddingTop,
                rightOffset.toInt(),
                headerTemp.paddingBottom
            )
            val margin20s = (plotWidth / 3f).toInt()
            (tvTemperatureStatus?.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                if (lp.marginEnd != margin20s) {
                    lp.marginEnd = margin20s
                    tvTemperatureStatus?.layoutParams = lp
                }
            }
        }
    }

    private fun setupRingerModeControls() {
        btnRingerModeToggle?.setOnClickListener {
            val mode = systemMonitorManager.toggleRingerMode {
                Toast.makeText(this, R.string.grant_notification_access, Toast.LENGTH_SHORT).show()
                try {
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                } catch (_: Exception) {}
            }
            updateRingerModeIcon(mode)
        }
        updateRingerModeIcon()
    }

    private fun updateRingerModeIcon(mode: Int = systemMonitorManager.getRingerMode()) {
        when (mode) {
            AudioManager.RINGER_MODE_NORMAL -> {
                btnRingerModeToggle?.setImageResource(R.drawable.ic_ringer_sound)
                btnRingerModeToggle?.alpha = 1.0f
            }
            AudioManager.RINGER_MODE_VIBRATE -> {
                btnRingerModeToggle?.setImageResource(R.drawable.ic_ringer_vibrate)
                btnRingerModeToggle?.alpha = 1.0f
            }
            AudioManager.RINGER_MODE_SILENT -> {
                btnRingerModeToggle?.setImageResource(R.drawable.ic_ringer_silent)
                btnRingerModeToggle?.alpha = 1.0f
            }
            else -> {
                btnRingerModeToggle?.setImageResource(R.drawable.ic_ringer_sound)
                btnRingerModeToggle?.alpha = 1.0f
            }
        }
    }

    private fun setupNetworkCardIndicators() {
        updateWifiStatus()
        updateBluetoothStatus()
        updateSimSignalStatus()
    }

    private fun updateWifiStatus(isConnected: Boolean = systemMonitorManager.isWifiConnected()) {
        ivNetworkWifi?.alpha = if (isConnected) 1.0f else 0.3f
    }

    private fun updateBluetoothStatus(isEnabled: Boolean = systemMonitorManager.isBluetoothEnabled()) {
        ivNetworkBluetooth?.alpha = if (isEnabled) 1.0f else 0.3f
    }

    private fun updateSimSignalStatus(isReady: Boolean = systemMonitorManager.isSimReady()) {
        ivNetworkSim1?.alpha = if (isReady) 1.0f else 0.3f
    }

    private fun setBatteryCard(titleId: Int, value: String, percent: Int = 0, color: Int = Color.rgb(0, 230, 118)) {
        cardBatteryTitle?.text = getString(titleId)
        cardBatteryValue?.text = value
        batteryRing?.setProgressAndColor(percent, color)
    }

    private fun updateBatteryRingGeometry() {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val lang = SettingsHelper.getAppLocale(this).language

        if (isLandscape && (lang == "vi" || lang == "art")) {
            batteryRing?.startAngle = 270f
            batteryRing?.maxSweep = 360f
        } else if (isLandscape) {
            batteryRing?.startAngle = 270f
            batteryRing?.maxSweep = 300f
        } else {
            batteryRing?.startAngle = 210f
            batteryRing?.maxSweep = -240f
        }
    }

    internal fun refreshWidgets() {
        BatteryWidget.updateAllWidgets(this, android.appwidget.AppWidgetManager.getInstance(this))
    }

    private fun hasNotificationAccess(): Boolean {
        val component = ComponentName(this, HotspotNotificationListener::class.java)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            getSystemService(NotificationManager::class.java)
                ?.isNotificationListenerAccessGranted(component) == true
        } else {
            val enabledListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            enabledListeners?.contains(component.flattenToString()) == true || enabledListeners?.contains(component.flattenToShortString()) == true
        }
    }

    internal fun updateDashboard() {
        updateBatteryRingGeometry()
        val batteryInfo = systemMonitorManager.getBatteryInfo()
        val battery = batteryInfo.rawIntent
        val voltageMv = batteryInfo.voltageMv
        val temperatureC = batteryInfo.temperatureC
        val sampleTime = SystemClock.elapsedRealtime()
        val smoothedVoltageMv = BatteryMath.getSmoothedVoltage(voltageMv, sampleTime)
        val isUseSystemBattery = SettingsHelper.isUseSystemBatteryCounter(this)
        val batteryPercent = if (voltageMv > 0 || isUseSystemBattery) {
            BatteryMath.getBatteryPercentage(isUseSystemBattery, battery, smoothedVoltageMv)
        } else null
        val dash = getString(R.string.placeholder_dash)
        val batteryValue = batteryPercent?.let { "$it%" } ?: dash
        val batteryColor = BatteryMath.percentageColor(batteryPercent ?: 0)
        setBatteryCard(R.string.metric_battery, batteryValue, batteryPercent ?: 0, batteryColor)

        val voltageUnit = SettingsHelper.getVoltageUnit(this)
        val ramUnit = SettingsHelper.getRamUnit(this)

        tvVoltageCurrent?.text = if (voltageMv > 0) {
            SettingsHelper.formatVoltage(voltageMv, voltageUnit)
        } else dash
        tvTemperatureCurrent?.text =
            SettingsHelper.formatDashboardTemperature(this, temperatureC)

        // 1. Voltage power
        val powerInfo = getBatteryPowerInfo(this)
        tvVoltagePower?.text = powerInfo?.getCurrentPowerString(this) ?: dash

        if (voltageMv > 0) dashboardViewModel.addVoltageSample(voltageMv.toFloat(), sampleTime)
        if (battery != null) dashboardViewModel.addTemperatureSample(temperatureC, sampleTime)

        val memory = getSystemService(ActivityManager::class.java)
        memory?.getMemoryInfo(memoryInfo)
        val totalMb = memoryInfo.totalMem / (1024L * 1024L)
        val availableMb = memoryInfo.availMem / (1024L * 1024L)
        val usedMb = (totalMb - availableMb).coerceAtLeast(0L)
        val usedPercent = if (totalMb > 0) (usedMb * 100 / totalMb).toInt() else 0
        dashboardViewModel.addRamSample(usedMb.toFloat(), sampleTime)
        tvRamCurrent?.text = getString(R.string.current_ram_format, usedPercent)

        // 2. RAM usage detail according to Settings unit
        val locale = SettingsHelper.getAppLocale(this)
        val ramValConverted = SettingsHelper.convertRam(usedMb.toFloat(), ramUnit)
        val ramSuffixStr = getString(if (ramUnit == RamUnit.GB) R.string.unit_gb else R.string.unit_mb)
        tvRamUsageDetail?.text = if (ramUnit == RamUnit.GB) {
            String.format(locale, "%.1f %s", ramValConverted, ramSuffixStr)
        } else {
            String.format(locale, "%.0f %s", ramValConverted, ramSuffixStr)
        }

        // 3. Temperature status
        val statusRes = when {
            temperatureC <= 40f -> R.string.temp_status_nominal
            temperatureC < 45f -> R.string.temp_status_hot
            else -> R.string.temp_status_critical
        }
        tvTemperatureStatus?.text = getString(statusRes)

        systemMonitorManager.updateNetworkSpeed()

        val (minVoltageMv, maxVoltageMv) = dashboardViewModel.getVoltageBoundsMv()

        val (minVoltage, maxVoltage, voltageSuffix) = when (voltageUnit) {
            VoltageUnit.MV -> Triple(minVoltageMv, maxVoltageMv, " " + getString(R.string.unit_mv))
            VoltageUnit.V -> Triple(minVoltageMv / 1000f, maxVoltageMv / 1000f, " " + getString(R.string.unit_v))
        }
        val voltageDecimalPlaces = if (voltageUnit == VoltageUnit.V) 2 else 0
        voltageGraph?.setHistory(
            dashboardViewModel.voltageHistory, minVoltage, maxVoltage, voltageSuffix, sampleTime,
            decimalPlaces = voltageDecimalPlaces,
            valueTransform = { rawMv -> SettingsHelper.convertVoltage(rawMv, voltageUnit) }
        ) { voltage ->
            val rawMv = SettingsHelper.convertToVoltageMv(voltage, voltageUnit).toInt()
            val percent = BatteryMath.getBatteryPercentage(isUseSystemBattery, battery, rawMv)
            BatteryMath.percentageColor(percent)
        }

        val maxRam = SettingsHelper.convertRam(totalMb.toFloat(), ramUnit).coerceAtLeast(0.1f)
        val ramSuffix = " " + getString(if (ramUnit == RamUnit.GB) R.string.unit_gb else R.string.unit_mb)
        val ramDecimalPlaces = if (ramUnit == RamUnit.GB) 1 else 0

        ramGraph?.setHistory(
            dashboardViewModel.ramHistory, 0f, maxRam, ramSuffix, sampleTime,
            decimalPlaces = ramDecimalPlaces,
            valueTransform = { rawMb -> SettingsHelper.convertRam(rawMb, ramUnit) }
        ) { ramVal ->
            val ramMb = SettingsHelper.convertToRamMb(ramVal, ramUnit)
            val ramUsedPercent = if (totalMb > 0) ramMb * 100f / totalMb else 0f
            when {
                ramUsedPercent >= 90f -> Color.rgb(255, 82, 82)
                ramUsedPercent >= 80f -> Color.rgb(255, 179, 0)
                else -> Color.rgb(0, 230, 118)
            }
        }
        val tempUnit = SettingsHelper.getTemperatureUnit(this)
        val (minTemp, maxTemp, tempSuffix) = when (tempUnit) {
            TempUnit.CELSIUS -> Triple(0f, 60f, getString(R.string.unit_celsius))
            TempUnit.FAHRENHEIT -> Triple(32f, 140f, getString(R.string.unit_fahrenheit))
            TempUnit.KELVIN -> Triple(273f, 333f, " " + getString(R.string.unit_kelvin))
        }
        tempGraph?.setHistory(
            dashboardViewModel.temperatureHistory, minTemp, maxTemp, tempSuffix, sampleTime,
            decimalPlaces = 0,
            valueTransform = { rawC -> SettingsHelper.convertTemperature(rawC, tempUnit) }
        ) { tempVal ->
            val celsius = SettingsHelper.convertToCelsius(tempVal, tempUnit)
            BatteryDetailsWidget.temperatureColor(celsius)
        }
    }

    private fun updateNetworkSpeedUi(speed: NetworkSpeed = systemMonitorManager.networkSpeedState.value) {
        if (!speed.isSupported) {
            val unavail = getString(R.string.network_unavailable)
            tvNetTxTotal?.text = unavail
            tvNetTxSpeed?.text = "—"
            tvNetRxTotal?.text = unavail
            tvNetRxSpeed?.text = "—"
        } else {
            tvNetTxTotal?.text = formatBytes(speed.totalTxBytes)
            tvNetTxSpeed?.text = formatBytes(speed.txRate) + "/s"
            tvNetRxTotal?.text = formatBytes(speed.totalRxBytes)
            tvNetRxSpeed?.text = formatBytes(speed.rxRate) + "/s"
        }
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

    internal fun openNotificationAccessSettings() {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.notification_access_settings_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    internal fun applyThickChinPadding(overrideLevel: Int? = thickChinOverrideLevel) {
        ChinAwareness.applyThickChinPadding(this, overrideLevel)
    }

    private fun loadRefreshInterval() {
        val saved = getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE)
            .getLong(REFRESH_INTERVAL_KEY, DEFAULT_REFRESH_INTERVAL_MS)
        refreshIntervalMs = saved.takeIf { it == 500L || it == 1_000L || it == 2_000L }
            ?: DEFAULT_REFRESH_INTERVAL_MS
    }


    companion object {
        private val BYTE_UNITS = arrayOf("KB", "MB", "GB", "TB")
        private const val SETTINGS_PREFS = BatteryWidget.SETTINGS_PREFS
        private const val REFRESH_INTERVAL_KEY = "refresh_interval_ms"
        private const val DEFAULT_REFRESH_INTERVAL_MS = 1_000L
        private const val NOTIFICATION_PERMISSION_REQUEST = 1001
        private const val PHONE_STATE_PERMISSION_REQUEST = 1002
        private const val KEY_SETTINGS_OPEN = "key_settings_open"
        private const val KEY_HOTSPOT_OPEN = "key_hotspot_open"
        private const val KEY_LANGUAGE_DROPDOWN_OPEN = "key_language_dropdown_open"
        private const val KEY_FREQUENCY_DROPDOWN_OPEN = "key_frequency_dropdown_open"
        private const val KEY_THEME_DROPDOWN_OPEN = "key_theme_dropdown_open"
        private const val KEY_TEMP_UNIT_DROPDOWN_OPEN = "key_temp_unit_dropdown_open"
        private const val KEY_VOLTAGE_UNIT_DROPDOWN_OPEN = "key_voltage_unit_dropdown_open"
        private const val KEY_RAM_UNIT_DROPDOWN_OPEN = "key_ram_unit_dropdown_open"
        private const val KEY_SETTINGS_SCROLL_Y = "key_settings_scroll_y"
        private const val KEY_SETTINGS_SCROLL_RIGHT_Y = "key_settings_scroll_right_y"
    }
}
