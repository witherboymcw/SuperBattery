package com.witherboymcw.superbattery

import android.app.ActivityManager
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.telephony.TelephonyManager
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.google.android.material.checkbox.MaterialCheckBox
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private val settingsViewController = SettingsViewController()
    internal val handler = Handler(Looper.getMainLooper())
    private val voltageHistory = ArrayDeque<HistoryGraphView.Sample>()
    private val ramHistory = ArrayDeque<HistoryGraphView.Sample>()
    private val temperatureHistory = ArrayDeque<HistoryGraphView.Sample>()
    private var previousRx = -1L
    private var previousTx = -1L
    private var previousNetworkTime = 0L
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
    private var isSettingsOpen = false

    private var tvHeaderClock: TextView? = null
    private var btnRingerModeToggle: ImageButton? = null
    private var ivNetworkWifi: ImageView? = null
    private var ivNetworkBluetooth: ImageView? = null
    private var ivNetworkSim1: ImageView? = null

    private var lastNonZeroTxRate: Long? = null
    private var lastNonZeroRxRate: Long? = null
    private var isVoltageShiftedHigh = false

    private var audioManager: AudioManager? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val clockFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val clockRunnable = object : Runnable {
        override fun run() {
            tvHeaderClock?.text = clockFormat.format(Date())
            handler.postDelayed(this, 1_000L)
        }
    }

    private val ringerModeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.RINGER_MODE_CHANGED_ACTION) {
                updateRingerModeIcon()
            }
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                updateBluetoothStatus()
            }
        }
    }

    private val settingsBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            closeSettingsScreen()
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

        val prefs = getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE)
        val assumeOled = prefs.getString(ASSUME_OLED_KEY, "none") ?: "none"
        val isEffectiveOled = when (assumeOled) {
            "oled" -> true
            "not_oled" -> false
            else -> OledDetector.isGuaranteedOled(this)
        }

        if (prefs.getBoolean(IS_FIRST_LAUNCH_KEY, true)) {
            if (isEffectiveOled) {
                prefs.edit()
                    .putString(APP_THEME_KEY, "oled")
                    .putBoolean(IS_FIRST_LAUNCH_KEY, false)
                    .apply()
            } else {
                prefs.edit()
                    .putBoolean(IS_FIRST_LAUNCH_KEY, false)
                    .apply()
            }
        }

        val savedTheme = prefs.getString(APP_THEME_KEY, "system") ?: "system"
        if (savedTheme == "oled") {
            setTheme(R.style.Theme_SuperBattery_Oled)
            if (AppCompatDelegate.getDefaultNightMode() != AppCompatDelegate.MODE_NIGHT_YES) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
        } else if (savedTheme == "system" && isEffectiveOled) {
            val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            if (isNight) {
                setTheme(R.style.Theme_SuperBattery_Oled)
            }
            if (AppCompatDelegate.getDefaultNightMode() != AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        } else {
            val targetNightMode = when (savedTheme) {
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            if (AppCompatDelegate.getDefaultNightMode() != targetNightMode) {
                AppCompatDelegate.setDefaultNightMode(targetNightMode)
            }
        }

        super.onCreate(savedInstanceState)
        try {
            startService(Intent(this, BatteryMonitorService::class.java))
        } catch (_: Exception) {
            // Guard against background service execution limits on Android 8.0+
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        initDashboardViews()
        applyThickChinPadding()
        val effectiveThemeForGraph = if (savedTheme == "oled" || (savedTheme == "system" && isEffectiveOled && ((resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES))) {
            "oled"
        } else {
            savedTheme
        }
        applyGraphBackgrounds(effectiveThemeForGraph)

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.setPadding(navBars.left, 0, navBars.right, navBars.bottom)
            applyThickChinPadding()
            applyAsymmetricCorners(insets)
            NotchHeaderHelper.updateHomeHeaderLayout(this)
            NotchHeaderHelper.updateSettingsHeaderLayout(this)
            insets
        }
        onBackPressedDispatcher.addCallback(this, settingsBackCallback)
        loadRefreshInterval()
        initializeMetricCards()
        setupSettingsScreen(savedInstanceState)

        btnSettings = findViewById(R.id.settings_button)
        btnSettings?.setOnClickListener {
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
                isSettingsOpen = true
                btnSettings?.rotation = 90f
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

    private fun applyAsymmetricCorners(insets: WindowInsetsCompat) {
        val defaultRadius = 12f * resources.displayMetrics.density
        var dynamicRadiusPx = defaultRadius

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val windowInsets = insets.toWindowInsets()
            if (windowInsets != null) {
                val corner = windowInsets.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_LEFT)
                if (corner != null) {
                    val screenRadiusPx = corner.radius.toFloat()
                    val calculatedRadius = screenRadiusPx - (16f * resources.displayMetrics.density)
                    if (calculatedRadius >= defaultRadius) {
                        dynamicRadiusPx = calculatedRadius
                    }
                }
            }
        }

        val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

        val cardBattery = findViewById<com.google.android.material.card.MaterialCardView>(R.id.card_battery)
        val cardNetwork = findViewById<com.google.android.material.card.MaterialCardView>(R.id.card_network)
        val cardVoltage = findViewById<com.google.android.material.card.MaterialCardView>(R.id.card_voltage_graph)
        val cardRam = findViewById<com.google.android.material.card.MaterialCardView>(R.id.card_ram_graph)
        val cardTemp = findViewById<com.google.android.material.card.MaterialCardView>(R.id.card_temperature_graph)

        val setCorners = { card: com.google.android.material.card.MaterialCardView?, tl: Float, tr: Float, br: Float, bl: Float ->
            card?.let {
                it.shapeAppearanceModel = it.shapeAppearanceModel.toBuilder()
                    .setTopLeftCornerSize(tl)
                    .setTopRightCornerSize(tr)
                    .setBottomRightCornerSize(br)
                    .setBottomLeftCornerSize(bl)
                    .build()
            }
        }

        if (isPortrait) {
            setCorners(cardBattery, defaultRadius, defaultRadius, defaultRadius, defaultRadius)
            setCorners(cardNetwork, defaultRadius, defaultRadius, defaultRadius, defaultRadius)
            setCorners(cardVoltage, defaultRadius, defaultRadius, defaultRadius, defaultRadius)
            setCorners(cardRam, defaultRadius, defaultRadius, defaultRadius, defaultRadius)
            setCorners(cardTemp, defaultRadius, defaultRadius, dynamicRadiusPx, dynamicRadiusPx)
        } else {
            setCorners(cardBattery, defaultRadius, defaultRadius, defaultRadius, defaultRadius)
            setCorners(cardNetwork, defaultRadius, defaultRadius, defaultRadius, defaultRadius)
            setCorners(cardVoltage, defaultRadius, defaultRadius, defaultRadius, dynamicRadiusPx)
            setCorners(cardRam, defaultRadius, dynamicRadiusPx, defaultRadius, defaultRadius)
            setCorners(cardTemp, defaultRadius, defaultRadius, dynamicRadiusPx, defaultRadius)
        }
    }

    private fun handleWidgetIntent(intent: Intent?) {
        if (intent == null) return
        val fromWidgetDoubleTap = intent.getBooleanExtra(BatteryWidget.EXTRA_CLOSE_SETTINGS, false)
        if (fromWidgetDoubleTap) {
            intent.removeExtra(BatteryWidget.EXTRA_CLOSE_SETTINGS)
            closeSettingsScreen(animate = false)
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
        val dropdownThickChin = findViewById<View>(R.id.dropdown_thick_chin)
        if (dropdownThickChin?.visibility == View.VISIBLE) {
            outState.putBoolean(KEY_THICK_CHIN_DROPDOWN_OPEN, true)
        }
        val dropdownAssumeOled = findViewById<View>(R.id.dropdown_assume_oled)
        if (dropdownAssumeOled?.visibility == View.VISIBLE) {
            outState.putBoolean(KEY_ASSUME_OLED_DROPDOWN_OPEN, true)
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

        try {
            androidx.core.content.ContextCompat.registerReceiver(
                this,
                ringerModeReceiver,
                IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION),
                androidx.core.content.ContextCompat.RECEIVER_EXPORTED
            )
        } catch (_: Exception) {}

        try {
            androidx.core.content.ContextCompat.registerReceiver(
                this,
                bluetoothReceiver,
                IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
                androidx.core.content.ContextCompat.RECEIVER_EXPORTED
            )
        } catch (_: Exception) {}

        updateRingerModeIcon()
        updateBluetoothStatus()
        updateSimSignalStatus()
        NotchHeaderHelper.updateHomeHeaderLayout(this)
        NotchHeaderHelper.updateSettingsHeaderLayout(this)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        NotchHeaderHelper.updateHomeHeaderLayout(this)
        NotchHeaderHelper.updateSettingsHeaderLayout(this)
        val savedTheme = getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE)
            .getString(APP_THEME_KEY, "system") ?: "system"
        val assumeOled = getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE)
            .getString(ASSUME_OLED_KEY, "none") ?: "none"
        val isEffectiveOled = when (assumeOled) {
            "oled" -> true
            "not_oled" -> false
            else -> OledDetector.isGuaranteedOled(this)
        }
        val effectiveThemeForGraph = if (savedTheme == "oled" || (savedTheme == "system" && isEffectiveOled && ((resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES))) {
            "oled"
        } else {
            savedTheme
        }
        applyGraphBackgrounds(effectiveThemeForGraph)
        applySettingsCardTheme()
    }

    override fun onUserLeaveHint() {
        handler.post { refreshWidgets() }
        super.onUserLeaveHint()
    }

    override fun onPause() {
        settingsViewController.flushThickChinCommit()
        handler.removeCallbacks(refreshDashboard)
        handler.removeCallbacks(clockRunnable)
        try {
            unregisterReceiver(ringerModeReceiver)
        } catch (_: Exception) {}
        try {
            unregisterReceiver(bluetoothReceiver)
        } catch (_: Exception) {}
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        networkCallback?.let { callback ->
            try {
                connectivityManager?.unregisterNetworkCallback(callback)
            } catch (_: Exception) {}
        }
        (findViewById<android.view.View>(R.id.settings_container) as? SwipeBackFrameLayout)?.swipeListener = null
        super.onDestroy()
    }

    private fun setupSettingsScreen(savedInstanceState: Bundle? = null) {
        settingsViewController.bind(this, savedInstanceState)
    }

    internal fun updatePermissionStatuses() {
        val statusAllowNotifications = findViewById<TextView>(R.id.status_allow_notifications)
        val statusNotificationAccess = findViewById<TextView>(R.id.status_notification_access)
        val statusPhoneState = findViewById<TextView>(R.id.status_phone_state_permission)

        val hasPostNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        statusAllowNotifications?.text = if (hasPostNotificationPermission) "✅" else "❌"

        val hasAccess = hasNotificationAccess()
        statusNotificationAccess?.text = if (hasAccess) "✅" else "❌"

        val hasPhoneState = checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED
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

    private fun openSettingsScreen() {
        val container = findViewById<View>(R.id.settings_container) ?: return
        isSettingsOpen = true
        container.visibility = View.VISIBLE
        val width = container.width.toFloat().takeIf { it > 0 } ?: 1080f
        container.translationX = width
        container.animate()
            .translationX(0f)
            .setDuration(280L)
            .setInterpolator(FastOutSlowInInterpolator())
            .withLayer()
            .start()
        btnSettings?.animate()
            ?.rotation(90f)
            ?.setDuration(280L)
            ?.setInterpolator(FastOutSlowInInterpolator())
            ?.start()
        settingsBackCallback.isEnabled = true
        NotchHeaderHelper.updateSettingsHeaderLayout(this)
    }

    internal fun closeSettingsScreen(animate: Boolean = true) {
        settingsViewController.flushThickChinCommit()
        val container = findViewById<View>(R.id.settings_container) ?: return
        if (container.visibility != View.VISIBLE) return
        isSettingsOpen = false
        settingsBackCallback.isEnabled = false
        if (animate) {
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
            btnSettings?.animate()
                ?.rotation(0f)
                ?.setDuration(280L)
                ?.setInterpolator(FastOutSlowInInterpolator())
                ?.start()
        } else {
            container.animate().cancel()
            container.visibility = View.GONE
            container.translationX = 0f
            btnSettings?.animate()?.cancel()
            btnSettings?.rotation = 0f
        }
    }

    private fun setupDashboardSwipeToOpen(dashboardView: View) {
        val container = findViewById<View>(R.id.settings_container) ?: return
        val dashboard = dashboardView as? DashboardLayout

        if (dashboard != null) {
            dashboard.swipeSensitivity = 1f
            dashboard.canSwipe = { !isSettingsOpen }
            dashboard.swipeListener = object : DashboardLayout.SwipeListener {
                override fun onSwipeStart() {
                    val screenWidth = dashboard.width.toFloat().takeIf { it > 0 } ?: 1080f
                    container.visibility = View.VISIBLE
                    container.translationX = screenWidth
                    btnSettings?.rotation = 0f
                }

                override fun onSwipeProgress(dx: Float, targetX: Float) {
                    container.visibility = View.VISIBLE
                    container.translationX = targetX
                    val screenWidth = dashboard.width.toFloat().takeIf { it > 0 } ?: 1080f
                    val progress = (1f - (targetX / screenWidth)).coerceIn(0f, 1f)
                    btnSettings?.rotation = progress * 90f
                }

                override fun onSwipeEnd(shouldOpen: Boolean) {
                    val screenWidth = dashboard.width.toFloat().takeIf { it > 0 } ?: 1080f
                    if (shouldOpen) {
                        isSettingsOpen = true
                        container.animate()
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
                        settingsBackCallback.isEnabled = true
                    } else {
                        isSettingsOpen = false
                        container.animate()
                            .translationX(screenWidth)
                            .setDuration(200L)
                            .setInterpolator(FastOutSlowInInterpolator())
                            .withLayer()
                            .withEndAction { container.visibility = View.GONE }
                            .start()
                        btnSettings?.animate()
                            ?.rotation(0f)
                            ?.setDuration(200L)
                            ?.setInterpolator(FastOutSlowInInterpolator())
                            ?.start()
                    }
                }
            }
            return
        }

        var startX = 0f
        var startY = 0f
        var isSwipingLeft = false
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop.toFloat()

        dashboardView.setOnTouchListener { _, event ->
            if (isSettingsOpen || (container.visibility == View.VISIBLE && container.translationX == 0f)) {
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
                        val progress = (1f - (targetX / screenWidth)).coerceIn(0f, 1f)
                        btnSettings?.rotation = progress * 90f
                        true
                    } else false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isSwipingLeft) {
                        val currentX = container.translationX
                        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                        val threshold = if (isLandscape) 0.09f else 0.20f
                        val shouldOpen = currentX < screenWidth * (1f - threshold)
                        if (shouldOpen) {
                            container.animate()
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
                            settingsBackCallback.isEnabled = true
                        } else {
                            container.animate()
                                .translationX(screenWidth)
                                .setDuration(200L)
                                .setInterpolator(FastOutSlowInInterpolator())
                                .withLayer()
                                .withEndAction { container.visibility = View.GONE }
                                .start()
                            btnSettings?.animate()
                                ?.rotation(0f)
                                ?.setDuration(200L)
                                ?.setInterpolator(FastOutSlowInInterpolator())
                                ?.start()
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
        setBatteryCard(R.string.metric_battery, "—", 0)
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
            if (isOled) {
                card?.setCardBackgroundColor(Color.TRANSPARENT)
                card?.strokeWidth = strokeWidthPx
            } else {
                card?.setCardBackgroundColor(defaultSurface)
                card?.strokeWidth = 0
            }
        }

        applySettingsCardTheme()
    }

    internal fun applySettingsCardTheme() {
        val prefs = getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE)
        val savedTheme = prefs.getString(APP_THEME_KEY, "system") ?: "system"
        val assumeOled = prefs.getString(ASSUME_OLED_KEY, "none") ?: "none"
        val isEffectiveOled = when (assumeOled) {
            "oled" -> true
            "not_oled" -> false
            else -> OledDetector.isGuaranteedOled(this)
        }
        val isOled = savedTheme == "oled" || (savedTheme == "system" && isEffectiveOled && ((resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES))

        val strokeWidthPx = (resources.displayMetrics.density * 1f).toInt()
        val defaultSurface = com.google.android.material.color.MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorSurface, Color.TRANSPARENT
        )
        val outlineColor = com.google.android.material.color.MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorOutline, Color.GRAY
        )

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

        findViewById<com.google.android.material.card.MaterialCardView>(R.id.card_settings_back)?.let { backCard ->
            backCard.setCardBackgroundColor(defaultSurface)
            backCard.strokeWidth = 0
        }
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
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        btnRingerModeToggle?.setOnClickListener {
            toggleRingerMode()
        }
        updateRingerModeIcon()
    }

    private fun updateRingerModeIcon() {
        val mode = audioManager?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
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

    private fun toggleRingerMode() {
        val am = audioManager ?: return
        val currentMode = am.ringerMode
        val nextMode = when (currentMode) {
            AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
            AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
            else -> AudioManager.RINGER_MODE_NORMAL
        }

        if (nextMode == AudioManager.RINGER_MODE_SILENT && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (notificationManager?.isNotificationPolicyAccessGranted == false) {
                Toast.makeText(this, R.string.grant_notification_access, Toast.LENGTH_SHORT).show()
                try {
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                } catch (_: Exception) {}
                am.ringerMode = AudioManager.RINGER_MODE_NORMAL
                updateRingerModeIcon()
                return
            }
        }

        try {
            am.ringerMode = nextMode
        } catch (_: Exception) {
            // Guard against missing permissions
        }
        updateRingerModeIcon()
    }

    private fun setupNetworkCardIndicators() {
        setupWifiMonitoring()
        updateBluetoothStatus()
        updateSimSignalStatus()
    }

    @Suppress("MissingPermission")
    private fun setupWifiMonitoring() {
        try {
            if (networkCallback != null) return
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            connectivityManager = cm
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    runOnUiThread { ivNetworkWifi?.alpha = 1.0f }
                }

                override fun onLost(network: Network) {
                    runOnUiThread { ivNetworkWifi?.alpha = 0.3f }
                }
            }
            networkCallback = callback
            cm.registerNetworkCallback(request, callback)
        } catch (_: Exception) {
            ivNetworkWifi?.alpha = 0.3f
        }
    }

    private fun updateBluetoothStatus() {
        try {
            val adapter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val bm = getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
                bm?.adapter
            } else {
                @Suppress("DEPRECATION")
                BluetoothAdapter.getDefaultAdapter()
            }
            if (adapter != null && adapter.isEnabled) {
                ivNetworkBluetooth?.alpha = 1.0f
            } else {
                ivNetworkBluetooth?.alpha = 0.3f
            }
        } catch (_: Exception) {
            ivNetworkBluetooth?.alpha = 0.3f
        }
    }

    private fun updateSimSignalStatus() {
        try {
            val tm = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
            } else true

            if (tm != null && hasPermission) {
                val state = tm.simState
                if (state == TelephonyManager.SIM_STATE_READY) {
                    ivNetworkSim1?.alpha = 1.0f
                } else {
                    ivNetworkSim1?.alpha = 0.3f
                }
            } else {
                ivNetworkSim1?.alpha = 0.3f
            }
        } catch (_: Exception) {
            ivNetworkSim1?.alpha = 0.3f
        }
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
        return getSystemService(NotificationManager::class.java)
            .isNotificationListenerAccessGranted(component)
    }

    internal fun updateDashboard() {
        updateBatteryRingGeometry()
        val battery = registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val voltageMv = battery?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val temperatureC = (battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
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

        updateNetworkStats()

        val hasPointAbove4250 = voltageHistory.any { it.value > 4250f }
        val hasPointAbove4000 = voltageHistory.any { it.value > 4000f }

        if (!isVoltageShiftedHigh && hasPointAbove4250) {
            isVoltageShiftedHigh = true
        } else if (isVoltageShiftedHigh && !hasPointAbove4000) {
            isVoltageShiftedHigh = false
        }

        val (minVoltageMv, maxVoltageMv) = if (isVoltageShiftedHigh) {
            Pair(3550f, 4550f)
        } else {
            Pair(3300f, 4300f)
        }

        val (minVoltage, maxVoltage, voltageSuffix) = when (voltageUnit) {
            VoltageUnit.MV -> Triple(minVoltageMv, maxVoltageMv, " " + getString(R.string.unit_mv))
            VoltageUnit.V -> Triple(minVoltageMv / 1000f, maxVoltageMv / 1000f, " " + getString(R.string.unit_v))
        }
        val voltageDecimalPlaces = if (voltageUnit == VoltageUnit.V) 2 else 0
        voltageGraph?.setHistory(
            voltageHistory, minVoltage, maxVoltage, voltageSuffix, sampleTime,
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
            ramHistory, 0f, maxRam, ramSuffix, sampleTime,
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
            temperatureHistory, minTemp, maxTemp, tempSuffix, sampleTime,
            decimalPlaces = 0,
            valueTransform = { rawC -> SettingsHelper.convertTemperature(rawC, tempUnit) }
        ) { tempVal ->
            val celsius = SettingsHelper.convertToCelsius(tempVal, tempUnit)
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

        if (rx == TrafficStats.UNSUPPORTED.toLong() || tx == TrafficStats.UNSUPPORTED.toLong()) {
            val unavail = getString(R.string.network_unavailable)
            tvNetTxTotal?.text = unavail
            tvNetTxSpeed?.text = "—"
            tvNetRxTotal?.text = unavail
            tvNetRxSpeed?.text = "—"
        } else {
            val displayedTxRate = if (txRate > 0L) {
                lastNonZeroTxRate = txRate
                txRate
            } else {
                lastNonZeroTxRate ?: 0L
            }

            val displayedRxRate = if (rxRate > 0L) {
                lastNonZeroRxRate = rxRate
                rxRate
            } else {
                lastNonZeroRxRate ?: 0L
            }

            tvNetTxTotal?.text = formatBytes(tx)
            tvNetTxSpeed?.text = formatBytes(displayedTxRate) + "/s"
            tvNetRxTotal?.text = formatBytes(rx)
            tvNetRxSpeed?.text = formatBytes(displayedRxRate) + "/s"
        }
    }

    private val samplePool = ArrayDeque<HistoryGraphView.Sample>()

    private fun addSample(history: ArrayDeque<HistoryGraphView.Sample>, value: Float, timestampMs: Long) {
        val earliest = timestampMs - HISTORY_WINDOW_MS
        while (history.isNotEmpty() && history.first().timestampMs < earliest) {
            samplePool.addLast(history.removeFirst())
        }
        while (history.size >= MAX_HISTORY_POINTS) {
            samplePool.addLast(history.removeFirst())
        }
        val sample = if (samplePool.isNotEmpty()) {
            samplePool.removeLast().apply {
                this.timestampMs = timestampMs
                this.value = value
            }
        } else {
            HistoryGraphView.Sample(timestampMs, value)
        }
        history.addLast(sample)
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
        if (overrideLevel != null) thickChinOverrideLevel = overrideLevel
        val prefs = getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE)
        val level = (overrideLevel ?: thickChinOverrideLevel ?: prefs.getInt(THICK_CHIN_LEVEL_KEY, 0)).coerceIn(0, 256)
        val density = resources.displayMetrics.density
        val reductionDp = level * (1f / 16f)

        val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
        }

        val default16Px = (16f * density).roundToInt()
        val default8Px = (8f * density).roundToInt()
        val default12Px = (12f * density).roundToInt()
        val default24Px = (24f * density).roundToInt()

        val reduced12Px = ((12f - reductionDp).coerceAtLeast(0f) * density).roundToInt()
        val reduced16Px = ((16f - reductionDp).coerceAtLeast(0f) * density).roundToInt()
        val reduced24Px = ((24f - reductionDp).coerceAtLeast(8f) * density).roundToInt()

        // 1. Home Dashboard
        findViewById<View>(R.id.dashboard)?.let { dashboard ->
            val leftPx = if (rotation == Surface.ROTATION_270) reduced16Px else default16Px
            val topPx = if (rotation == Surface.ROTATION_180) reduced16Px else default16Px
            val rightPx = if (rotation == Surface.ROTATION_90) reduced16Px else default16Px
            val bottomPx = if (rotation == Surface.ROTATION_0) reduced16Px else default16Px
            dashboard.setPaddingRelative(leftPx, topPx, rightPx, bottomPx)
        }

        // 2. Settings Screen
        val singleScrollView = findViewById<View>(R.id.settings_scroll_view)
        if (singleScrollView != null) {
            // Single column Settings (Portrait)
            val leftPx = if (rotation == Surface.ROTATION_270) reduced16Px else default16Px
            val topPx = if (rotation == Surface.ROTATION_180) reduced16Px else default16Px
            val rightPx = if (rotation == Surface.ROTATION_90) reduced16Px else default16Px
            val bottomPx = if (rotation == Surface.ROTATION_0) reduced24Px else default24Px
            singleScrollView.setPaddingRelative(leftPx, topPx, rightPx, bottomPx)
        } else {
            // Two-column Settings (Landscape)
            val scrollLeft = findViewById<View>(R.id.settings_scroll_left)
            val scrollRight = findViewById<View>(R.id.settings_scroll_right)

            val leftColStartPx = if (rotation == Surface.ROTATION_270) reduced16Px else default16Px
            val leftColTopPx = if (rotation == Surface.ROTATION_180) reduced12Px else default12Px
            val leftColBottomPx = if (rotation == Surface.ROTATION_0) reduced24Px else default24Px

            scrollLeft?.setPaddingRelative(leftColStartPx, leftColTopPx, default8Px, leftColBottomPx)

            val rightColEndPx = if (rotation == Surface.ROTATION_90) reduced16Px else default16Px
            val rightColTopPx = if (rotation == Surface.ROTATION_180) reduced12Px else default12Px
            val rightColBottomPx = if (rotation == Surface.ROTATION_0) reduced24Px else default24Px

            scrollRight?.setPaddingRelative(default8Px, rightColTopPx, rightColEndPx, rightColBottomPx)
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
        private const val PHONE_STATE_PERMISSION_REQUEST = 1002
        private const val KEY_SETTINGS_OPEN = "key_settings_open"
        private const val KEY_LANGUAGE_DROPDOWN_OPEN = "key_language_dropdown_open"
        private const val KEY_FREQUENCY_DROPDOWN_OPEN = "key_frequency_dropdown_open"
        private const val APP_THEME_KEY = "app_theme"
        private const val ASSUME_OLED_KEY = "assume_oled"
        private const val IS_FIRST_LAUNCH_KEY = "is_first_launch"
        private const val KEY_THEME_DROPDOWN_OPEN = "key_theme_dropdown_open"
        private const val KEY_TEMP_UNIT_DROPDOWN_OPEN = "key_temp_unit_dropdown_open"
        private const val KEY_VOLTAGE_UNIT_DROPDOWN_OPEN = "key_voltage_unit_dropdown_open"
        private const val KEY_RAM_UNIT_DROPDOWN_OPEN = "key_ram_unit_dropdown_open"
        private const val KEY_THICK_CHIN_DROPDOWN_OPEN = "key_thick_chin_dropdown_open"
        private const val THICK_CHIN_LEVEL_KEY = "thick_chin_level"
        private const val KEY_ASSUME_OLED_DROPDOWN_OPEN = "key_assume_oled_dropdown_open"
        private const val KEY_SETTINGS_SCROLL_Y = "key_settings_scroll_y"
        private const val KEY_SETTINGS_SCROLL_RIGHT_Y = "key_settings_scroll_right_y"
    }
}
