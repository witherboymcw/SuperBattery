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
import android.os.SystemClock
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
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
import com.witherboymcw.superbattery.awareness.BatteryMath
import com.witherboymcw.superbattery.awareness.ChinAwareness
import com.witherboymcw.superbattery.awareness.CornerAwareness
import com.witherboymcw.superbattery.awareness.FoldableAwareness
import com.witherboymcw.superbattery.awareness.NotchAwareness
import com.witherboymcw.superbattery.awareness.getBatteryPowerInfo
import com.witherboymcw.superbattery.screens.dashboard.DashboardUiState
import com.witherboymcw.superbattery.core.LocaleManager
import com.witherboymcw.superbattery.core.MetricColors
import com.witherboymcw.superbattery.core.getDisplayRotationCompat
import com.witherboymcw.superbattery.ui.MetricGraphBinder
import com.witherboymcw.superbattery.ui.SettingsHost
import com.witherboymcw.superbattery.widgets.BatteryDetailsWidget
import com.witherboymcw.superbattery.widgets.BatteryWidget
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.witherboymcw.superbattery.battery.BatteryMonitorService
import com.witherboymcw.superbattery.battery.BatteryRingView
import com.witherboymcw.superbattery.screens.dashboard.DashboardViewModel
import com.witherboymcw.superbattery.screens.hotspot.HotspotNotificationListener
import com.witherboymcw.superbattery.screens.hotspot.HotspotViewController
import com.witherboymcw.superbattery.screens.settings.SettingsHelper
import com.witherboymcw.superbattery.screens.settings.SettingsViewController
import com.witherboymcw.superbattery.screens.standby.StandbyViewController
import com.witherboymcw.superbattery.settings.HardwarePreferences
import com.witherboymcw.superbattery.settings.Notch
import com.witherboymcw.superbattery.settings.Permissions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), SettingsHost {
    // SettingsHost implementation
    override fun <T : View> findView(id: Int): T? = findViewById(id)
    override val settingsActionButton: View? get() = dashboardViews.btnSettings

    private val settingsViewController = SettingsViewController()
    private val hotspotViewController = HotspotViewController()
    private val standbyViewController = StandbyViewController()
    private val dashboardViewModel by lazy { ViewModelProvider(this)[DashboardViewModel::class.java] }
    private val refreshIntervalState = MutableStateFlow(DEFAULT_REFRESH_INTERVAL_MS)
    internal var refreshIntervalMs = DEFAULT_REFRESH_INTERVAL_MS
        set(value) {
            field = value
            refreshIntervalState.value = value
        }

    internal val dashboardViews by lazy { DashboardViews(this) }
    internal val dashboardRenderer by lazy { DashboardRenderer(dashboardViews, this) }

    internal var thickChinOverrideLevel: Int? = null
    private val memoryInfo = ActivityManager.MemoryInfo()
    private var currentLayoutResId: Int = 0


    private lateinit var systemMonitorManager: SystemMonitorManager

    private val clockFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val secondsFormat = SimpleDateFormat("ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleManager.ensureInitialLanguage(this)
        OledThemeAllInOne.applyActivityTheme(this)

        if (savedInstanceState != null) {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        super.onCreate(savedInstanceState)
        systemMonitorManager = SystemMonitorManager(this)
        try {
            startService(Intent(this, BatteryMonitorService::class.java))
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to start battery monitor service", e)
            // Guard against background service execution limits on Android 8.0+
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        enableEdgeToEdge()
        updateLayoutIfNeeded(savedInstanceState)

        handleWidgetIntent(intent)

        updateFoldableUiState()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    try {
                        WindowInfoTracker.getOrCreate(this@MainActivity)
                            .windowLayoutInfo(this@MainActivity)
                            .collect { layoutInfo ->
                                val hasFoldingFeature = layoutInfo.displayFeatures.any { it is FoldingFeature }
                                if (hasFoldingFeature) {
                                    FoldableAwareness.markFoldingFeatureDetected(true)
                                    applyThickChinPadding()
                                    updateFoldableUiState()
                                }
                                updateLayoutIfNeeded()
                            }
                    } catch (e: Exception) {
                        android.util.Log.w(TAG, "Failed to observe window layout changes", e)
                    }
                }
                launch {
                    refreshIntervalState.collectLatest { intervalMs ->
                        while (true) {
                            updateDashboard()
                            delay(intervalMs)
                        }
                    }
                }
                launch {
                    while (true) {
                        val now = Date()
                        dashboardViews.tvHeaderClock?.text = clockFormat.format(now)
                        dashboardViews.tvHeaderSeconds?.text = secondsFormat.format(now)
                        delay(1_000L)
                    }
                }
                launch { systemMonitorManager.bluetoothState.collect(dashboardRenderer::updateBluetoothStatus) }
                launch { systemMonitorManager.ringerModeState.collect(dashboardRenderer::updateRingerModeIcon) }
                launch { systemMonitorManager.simState.collect(dashboardRenderer::updateSimSignalStatus) }
                launch { systemMonitorManager.batteryInfoState.collect { updateDashboard() } }
                launch { systemMonitorManager.wifiState.collect(dashboardRenderer::updateWifiStatus) }
            }
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
            hotspotViewController.closeScreen(this, animate = false)
            standbyViewController.closeScreen(this, animate = false)
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
        val standbyContainer = findViewById<View>(R.id.standby_container)
        if (standbyViewController.isOpen || (standbyContainer != null && standbyContainer.visibility == View.VISIBLE)) {
            outState.putBoolean(KEY_STANDBY_OPEN, true)
        }
        com.witherboymcw.superbattery.settings.SettingsSection.entries.forEach { section ->
            if (findViewById<View>(section.dropdownId)?.visibility == View.VISIBLE) {
                outState.putBoolean(section.stateKey, true)
            }
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
        val hotspotRv = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.hotspot_recycler_view)
        val hotspotLm = hotspotRv?.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
        if (hotspotLm != null) {
            val firstPos = hotspotLm.findFirstVisibleItemPosition()
            val firstChild = hotspotLm.findViewByPosition(firstPos)
            val topOffset = firstChild?.top ?: 0
            outState.putInt(com.witherboymcw.superbattery.screens.hotspot.HotspotViewController.KEY_HOTSPOT_SCROLL_POSITION, firstPos)
            outState.putInt(com.witherboymcw.superbattery.screens.hotspot.HotspotViewController.KEY_HOTSPOT_SCROLL_OFFSET, topOffset)
        }
    }

    override fun onResume() {
        super.onResume()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()
        loadRefreshInterval()
        updatePermissionStatuses()
        systemMonitorManager.startMonitoring()
        standbyViewController.onResume(this)

        dashboardRenderer.updateRingerModeIcon(systemMonitorManager.getRingerMode())
        dashboardRenderer.updateBluetoothStatus(systemMonitorManager.isBluetoothEnabled())
        dashboardRenderer.updateSimSignalStatus(systemMonitorManager.isSimReady())
        dashboardRenderer.updateWifiStatus(systemMonitorManager.isWifiConnected())
        NotchAwareness.updateAllHeaders(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    private fun bindLayout() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val isMiniSquare = FoldableAwareness.isMiniSquareMode(this)
            if (isMiniSquare) {
                view.setPadding(0, 0, 0, 0)
            } else {
                val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
                view.setPadding(navBars.left, 0, navBars.right, navBars.bottom)
            }
            applyThickChinPadding()
            CornerAwareness.applyAsymmetricCorners(this, insets)
            NotchAwareness.updateAllHeaders(this)
            insets
        }

        dashboardViews.btnSettings?.setOnClickListener {
            settingsViewController.openScreen(this)
        }

        dashboardViews.cardNetwork?.setOnClickListener {
            if (!hotspotViewController.isOpen) {
                hotspotViewController.openScreen(this)
            }
        }

        val clockTapListener = View.OnClickListener {
            if (!standbyViewController.isOpen) {
                standbyViewController.openScreen(this)
            }
        }
        dashboardViews.tvHeaderClock?.setOnClickListener(clockTapListener)
        dashboardViews.tvHeaderSeconds?.setOnClickListener(clockTapListener)

        findViewById<View>(R.id.dashboard)?.let { dashboard ->
            DrawerNavigationHelper.setupDashboardSwipeToOpen(
                this, dashboard, settingsViewController, hotspotViewController, standbyViewController
            )
        }
    }

    internal fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun updateLayoutIfNeeded(savedInstanceState: Bundle? = null): Boolean {
        val isMiniSquare = FoldableAwareness.isMiniSquareMode(this)
        val targetLayout = if (isMiniSquare) R.layout.activity_main_mini_square else R.layout.activity_main

        if (currentLayoutResId == targetLayout) {
            return false
        }

        val isSettingsOpen = settingsViewController.isOpen ||
                (savedInstanceState?.getBoolean(KEY_SETTINGS_OPEN) == true) ||
                (findViewById<View>(R.id.settings_container)?.visibility == View.VISIBLE)
        val isHotspotOpen = hotspotViewController.isOpen ||
                (savedInstanceState?.getBoolean(KEY_HOTSPOT_OPEN) == true) ||
                (findViewById<View>(R.id.hotspot_container)?.visibility == View.VISIBLE)
        val isStandbyOpen = standbyViewController.isOpen ||
                (savedInstanceState?.getBoolean(KEY_STANDBY_OPEN) == true) ||
                (findViewById<View>(R.id.standby_container)?.visibility == View.VISIBLE)

        if (currentLayoutResId != 0) {
            settingsViewController.unbind()
            hotspotViewController.unbind()
            standbyViewController.unbind()
        }

        setContentView(targetLayout)
        currentLayoutResId = targetLayout
        dashboardViews.refresh()

        if (isSettingsOpen) {
            findViewById<View>(R.id.settings_container)?.visibility = View.VISIBLE
        }
        if (isHotspotOpen) {
            findViewById<View>(R.id.hotspot_container)?.visibility = View.VISIBLE
        }
        if (isStandbyOpen) {
            findViewById<View>(R.id.standby_container)?.visibility = View.VISIBLE
        }

        initDashboardViews()
        applyThickChinPadding()
        applyGraphBackgrounds(OledThemeAllInOne.getEffectiveThemeForGraph(this))

        hideSystemBars()
        loadRefreshInterval()
        initializeMetricCards()
        setupSettingsScreen(savedInstanceState)
        setupHotspotScreen(savedInstanceState)
        setupStandbyScreen(savedInstanceState)
        bindLayout()

        if (isSettingsOpen) {
            settingsViewController.restoreOpenState(this)
        }

        if (isHotspotOpen) {
            hotspotViewController.restoreOpenState(this)
        }

        if (isStandbyOpen) {
            standbyViewController.restoreOpenState(this)
        }

        updateDashboard()
        return true
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        updateLayoutIfNeeded()
        NotchAwareness.updateAllHeaders(this)
        applyThickChinPadding()

        val hotspotContainer = findViewById<View>(R.id.hotspot_container)
        if (hotspotViewController.isOpen || (hotspotContainer != null && hotspotContainer.visibility == View.VISIBLE)) {
            hotspotViewController.restoreOpenState(this)
        }
        val standbyContainer = findViewById<View>(R.id.standby_container)
        if (standbyViewController.isOpen || (standbyContainer != null && standbyContainer.visibility == View.VISIBLE)) {
            standbyViewController.restoreOpenState(this)
        }

        applyGraphBackgrounds(OledThemeAllInOne.getEffectiveThemeForGraph(this))

    }

    override fun onUserLeaveHint() {
        refreshWidgets()
        super.onUserLeaveHint()
    }

    override fun onPause() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        NotchAwareness.dismissNotchPositionOverlay()
        settingsViewController.flushThickChinCommit()
        systemMonitorManager.stopMonitoring()
        standbyViewController.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        settingsViewController.unbind()
        hotspotViewController.unbind()
        standbyViewController.unbind()
        (findViewById<android.view.View>(R.id.settings_container) as? SwipeBackFrameLayout)?.swipeListener = null
        (findViewById<android.view.View>(R.id.hotspot_container) as? SwipeBackFrameLayout)?.swipeListener = null
        (findViewById<android.view.View>(R.id.standby_container) as? SwipeBackFrameLayout)?.swipeListener = null
        com.witherboymcw.superbattery.awareness.NotchAwareness.dismissNotchPositionOverlay()
        super.onDestroy()
    }

    private fun setupSettingsScreen(savedInstanceState: Bundle? = null) {
        settingsViewController.bind(this, savedInstanceState)
    }

    private fun setupHotspotScreen(savedInstanceState: Bundle? = null) {
        hotspotViewController.bind(this, savedInstanceState)
    }

    private fun setupStandbyScreen(savedInstanceState: Bundle? = null) {
        standbyViewController.bind(this, savedInstanceState)
    }

    internal fun updatePermissionStatuses() {
        settingsViewController.updatePermissionStatuses()
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
            dashboardRenderer.updateSimSignalStatus(systemMonitorManager.isSimReady())
        }
    }

    private fun initializeMetricCards() {
        dashboardRenderer.setBatteryCard(R.string.metric_battery, "—", 0)
    }

    private fun applyGraphBackgrounds(theme: String) {
        val isOled = theme == "oled"
        OledThemeAllInOne.applyGraphTheme(this, dashboardViews.voltageGraph, dashboardViews.ramGraph, dashboardViews.tempGraph, isOled = isOled)
        OledThemeAllInOne.applyThemeToAllCards(findViewById(R.id.main), this, isOled = isOled)
        OledThemeAllInOne.applySolidCardTheme(findViewById(R.id.card_settings_back), this)
        OledThemeAllInOne.applySolidCardTheme(findViewById(R.id.card_hotspot_back), this)
        updateMiniSquareGraphVisibility()
    }

    private var selectedMiniSquareGraph = 0 // 0: Voltage, 1: RAM, 2: Temp

    private fun initDashboardViews() {
        dashboardViews.ramGraph?.suppressMinYLabel = true
        dashboardViews.tempGraph?.suppressMinYLabel = true
        dashboardRenderer.updateBatteryRingGeometry()

        setupRingerModeControls()
        
        dashboardRenderer.updateWifiStatus(systemMonitorManager.isWifiConnected())
        dashboardRenderer.updateBluetoothStatus(systemMonitorManager.isBluetoothEnabled())
        dashboardRenderer.updateSimSignalStatus(systemMonitorManager.isSimReady())

        val isMiniSquare = FoldableAwareness.isMiniSquareMode(this)
        dashboardViews.voltageGraph?.hideYAxisLabels = isMiniSquare
        dashboardViews.ramGraph?.hideYAxisLabels = isMiniSquare
        dashboardViews.tempGraph?.hideYAxisLabels = isMiniSquare

        if (!isMiniSquare) {
            MetricGraphBinder.bindHeaderOffsets(dashboardViews.voltageGraph, dashboardViews.headerVoltageGraph, dashboardViews.tvVoltagePower)
            MetricGraphBinder.bindHeaderOffsets(dashboardViews.ramGraph, dashboardViews.headerRamGraph, dashboardViews.tvRamCurrent)
            MetricGraphBinder.bindHeaderOffsets(dashboardViews.tempGraph, dashboardViews.headerTemperatureGraph, dashboardViews.tvTemperatureStatus)
        }

        setupMiniSquareGraphSwitching()
    }

    private fun setupMiniSquareGraphSwitching() {
        if (dashboardViews.cardVoltageSummary == null) return

        val cards = listOf(dashboardViews.cardVoltageSummary, dashboardViews.cardRamSummary, dashboardViews.cardTempSummary)
        cards.forEachIndexed { index, card ->
            card?.setOnClickListener {
                selectedMiniSquareGraph = index
                updateMiniSquareGraphVisibility()
            }
        }
        updateMiniSquareGraphVisibility()
    }

    private fun updateMiniSquareGraphVisibility() {
        if (dashboardViews.cardVoltageSummary == null) return

        val primaryColor = com.google.android.material.color.MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorPrimary, Color.BLUE
        )
        val outlineColor = com.google.android.material.color.MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorOutline, Color.GRAY
        )
        val isOled = OledThemeAllInOne.isOledThemeActive(this)
        val defaultStrokeWidth = if (isOled) (1 * resources.displayMetrics.density).toInt() else 0
        val activeStrokeWidth = (2 * resources.displayMetrics.density).toInt()

        val configs = listOf(
            Triple(dashboardViews.voltageGraph, dashboardViews.cardVoltageSummary, 0),
            Triple(dashboardViews.ramGraph, dashboardViews.cardRamSummary, 1),
            Triple(dashboardViews.tempGraph, dashboardViews.cardTempSummary, 2)
        )
        configs.forEach { (graph, card, index) ->
            graph?.visibility = if (selectedMiniSquareGraph == index) View.VISIBLE else View.GONE
            val color = if (selectedMiniSquareGraph == index) primaryColor else outlineColor
            val stroke = if (selectedMiniSquareGraph == index) activeStrokeWidth else defaultStrokeWidth
            if (card != null) {
                setSummaryCardHighlight(card, color, stroke)
            }
        }
    }

    private fun setSummaryCardHighlight(card: com.google.android.material.card.MaterialCardView?, color: Int, strokeWidthPx: Int) {
        card?.let {
            it.strokeColor = color
            it.strokeWidth = strokeWidthPx
        }
    }

    private fun setupRingerModeControls() {
        dashboardViews.btnRingerModeToggle?.setOnClickListener {
            val mode = systemMonitorManager.toggleRingerMode {
                Toast.makeText(this, R.string.grant_notification_access, Toast.LENGTH_SHORT).show()
                try {
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "Failed to open notification policy settings", e)
                }
            }
            dashboardRenderer.updateRingerModeIcon(mode)
        }
        dashboardRenderer.updateRingerModeIcon(systemMonitorManager.getRingerMode())
    }

    internal fun refreshWidgets() {
        BatteryWidget.updateAllWidgets(this, android.appwidget.AppWidgetManager.getInstance(this))
    }

    private var lastDashboardUpdateTime = 0L

    internal fun updateDashboard() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastDashboardUpdateTime < 100L) return
        lastDashboardUpdateTime = now

        dashboardRenderer.updateBatteryRingGeometry()
        systemMonitorManager.updateNetworkSpeed()

        val batteryInfo = systemMonitorManager.getBatteryInfo()
        val powerInfo = getBatteryPowerInfo(this)
        val networkSpeed = systemMonitorManager.networkSpeedState.value

        val memory = getSystemService(ActivityManager::class.java)
        memory?.getMemoryInfo(memoryInfo)

        val uiState = dashboardViewModel.buildUiState(
            batteryInfo = batteryInfo,
            powerInfo = powerInfo,
            networkSpeed = networkSpeed,
            totalMemBytes = memoryInfo.totalMem,
            availMemBytes = memoryInfo.availMem,
            voltageUnit = SettingsHelper.getVoltageUnit(this),
            ramUnit = SettingsHelper.getRamUnit(this),
            tempUnit = SettingsHelper.getTemperatureUnit(this),
            isUseSystemBattery = SettingsHelper.isUseSystemBatteryCounter(this)
        )

        dashboardRenderer.renderDashboardUiState(uiState)
    }

    internal fun applyThickChinPadding(overrideLevel: Int? = thickChinOverrideLevel) {
        ChinAwareness.applyThickChinPadding(this, overrideLevel)
    }

    private fun loadRefreshInterval() {
        val saved = com.witherboymcw.superbattery.settings.AppPreferences.getRefreshIntervalMs(this)
        refreshIntervalMs = saved.takeIf { it == 500L || it == 1_000L || it == 2_000L }
            ?: com.witherboymcw.superbattery.settings.AppPreferences.DEFAULT_REFRESH_INTERVAL_MS
    }


    companion object {
        private const val TAG = "MainActivity"
        private const val DEFAULT_REFRESH_INTERVAL_MS = com.witherboymcw.superbattery.settings.AppPreferences.DEFAULT_REFRESH_INTERVAL_MS
        internal const val NOTIFICATION_PERMISSION_REQUEST = 1001
        internal const val PHONE_STATE_PERMISSION_REQUEST = 1002
        private const val KEY_SETTINGS_OPEN = "key_settings_open"
        private const val KEY_HOTSPOT_OPEN = "key_hotspot_open"
        private const val KEY_STANDBY_OPEN = "key_standby_open"
        private const val KEY_SETTINGS_SCROLL_Y = "key_settings_scroll_y"
        private const val KEY_SETTINGS_SCROLL_RIGHT_Y = "key_settings_scroll_right_y"
    }
}
