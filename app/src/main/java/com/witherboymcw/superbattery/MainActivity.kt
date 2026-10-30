package com.witherboymcw.superbattery

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Menu
import android.view.View
import android.widget.Button
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private val voltageHistory = ArrayDeque<HistoryGraphView.Sample>()
    private val ramHistory = ArrayDeque<HistoryGraphView.Sample>()
    private val temperatureHistory = ArrayDeque<HistoryGraphView.Sample>()
    private var previousRx = -1L
    private var previousTx = -1L
    private var previousNetworkTime = 0L
    private var refreshIntervalMs = DEFAULT_REFRESH_INTERVAL_MS
    private lateinit var permissionPanel: View
    private lateinit var dashboard: View

    private val refreshDashboard = object : Runnable {
        override fun run() {
            if (hasNotificationAccess()) {
                updateDashboard()
                handler.postDelayed(this, refreshIntervalMs)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startService(Intent(this, BatteryMonitorService::class.java))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        permissionPanel = findViewById(R.id.permission_panel)
        dashboard = findViewById(R.id.dashboard)
        loadRefreshInterval()
        findViewById<Button>(R.id.notification_access_button).setOnClickListener {
            openNotificationAccessSettings()
        }
        findViewById<View>(R.id.settings_button).setOnClickListener { showSettingsMenu(it) }
        initializeMetricCards()
    }

    override fun onResume() {
        super.onResume()
        loadRefreshInterval()
        updateScreenForPermission()
        refreshWidgets()
        handler.removeCallbacks(refreshDashboard)
        if (hasNotificationAccess()) handler.post(refreshDashboard)
    }

    override fun onUserLeaveHint() {
        refreshWidgets()
        super.onUserLeaveHint()
    }

    override fun onPause() {
        handler.removeCallbacks(refreshDashboard)
        super.onPause()
    }

    private fun initializeMetricCards() {
        setCard(R.id.card_battery, R.string.metric_battery, "—", "")
    }

    private fun updateScreenForPermission() {
        val enabled = hasNotificationAccess()
        permissionPanel.visibility = if (enabled) View.GONE else View.VISIBLE
        dashboard.visibility = if (enabled) View.VISIBLE else View.GONE
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
        val batteryPercent = if (voltageMv > 0) BatteryWidget.calculatePercentage(smoothedVoltageMv) else null
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
            val percent = BatteryWidget.calculatePercentage(voltage.toInt())
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

    private fun showSettingsMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        val frequencies = popup.menu.addSubMenu(getString(R.string.update_frequency))
        frequencies.setGroupCheckable(Menu.NONE, true, true)
        val options = listOf(
            500L to R.string.update_every_half_second,
            1_000L to R.string.update_every_second,
            2_000L to R.string.update_every_two_seconds
        )
        options.forEachIndexed { index, (interval, label) ->
            frequencies.add(Menu.NONE, interval.toInt(), index, getString(label))
                .setCheckable(true)
                .setChecked(refreshIntervalMs == interval)
        }
        popup.setOnMenuItemClickListener { item ->
            val selected = item.itemId.toLong()
            if (selected in setOf(500L, 1_000L, 2_000L)) {
                refreshIntervalMs = selected
                getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE).edit()
                    .putLong(REFRESH_INTERVAL_KEY, selected)
                    .apply()
                handler.removeCallbacks(refreshDashboard)
                handler.post(refreshDashboard)
                true
            } else false
        }
        popup.show()
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
        private const val SETTINGS_PREFS = "dashboard_settings"
        private const val REFRESH_INTERVAL_KEY = "refresh_interval_ms"
        private const val DEFAULT_REFRESH_INTERVAL_MS = 2_000L
    }
}
