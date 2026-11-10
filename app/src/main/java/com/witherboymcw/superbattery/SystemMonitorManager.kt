package com.witherboymcw.superbattery

import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.telephony.ServiceState
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Data class representing current battery status snapshot.
 */
data class BatteryInfo(
    val voltageMv: Int = 0,
    val temperatureC: Float = 0f,
    val level: Int = -1,
    val scale: Int = -1,
    val status: Int = -1,
    val isCharging: Boolean = false,
    @Transient val rawIntent: Intent? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BatteryInfo) return false
        return voltageMv == other.voltageMv &&
                temperatureC == other.temperatureC &&
                level == other.level &&
                scale == other.scale &&
                status == other.status &&
                isCharging == other.isCharging
    }

    override fun hashCode(): Int {
        var result = voltageMv
        result = 31 * result + temperatureC.hashCode()
        result = 31 * result + level
        result = 31 * result + scale
        result = 31 * result + status
        result = 31 * result + isCharging.hashCode()
        return result
    }
}

/**
 * Data class representing current network speed snapshot.
 */
data class NetworkSpeed(
    val rxRate: Long = 0L,
    val txRate: Long = 0L,
    val totalRxBytes: Long = -1L,
    val totalTxBytes: Long = -1L,
    val isSupported: Boolean = true
)

/**
 * SystemMonitorManager is an independent manager responsible for:
 * 1. Listening to system state changes (Bluetooth, SIM, Ringer Mode, Battery, Wi-Fi).
 * 2. Monitoring network traffic (TrafficStats) and calculating network download/upload rates.
 * 3. Exposing Kotlin StateFlow / Flow for reactive UI state collection without memory leaks.
 * 4. Handling SIM State via TelephonyCallback (API 31+) and BroadcastReceiver fallback.
 * 5. Throttling and deduplicating updates to minimize CPU/GPU overhead.
 */
class SystemMonitorManager(context: Context) {

    // Prevent Activity Context memory leaks by converting to applicationContext immediately
    private val appContext: Context = context.applicationContext

    interface Listener {
        fun onBluetoothStateChanged(isEnabled: Boolean) {}
        fun onRingerModeChanged(mode: Int) {}
        fun onSimStateChanged(isReady: Boolean) {}
        fun onBatteryChanged(batteryInfo: BatteryInfo) {}
        fun onWifiStateChanged(isConnected: Boolean) {}
        fun onNetworkSpeedChanged(networkSpeed: NetworkSpeed) {}
    }

    private var listener: Listener? = null
    private var isMonitoring = false

    // Reactive StateFlows for Kotlin Coroutines / Flow UI layer consumption
    private val _bluetoothState = MutableStateFlow(isBluetoothEnabled())
    val bluetoothState: StateFlow<Boolean> = _bluetoothState.asStateFlow()

    private val _ringerModeState = MutableStateFlow(getRingerMode())
    val ringerModeState: StateFlow<Int> = _ringerModeState.asStateFlow()

    private val _simState = MutableStateFlow(isSimReady())
    val simState: StateFlow<Boolean> = _simState.asStateFlow()

    private val _batteryInfoState = MutableStateFlow(getBatteryInfo())
    val batteryInfoState: StateFlow<BatteryInfo> = _batteryInfoState.asStateFlow()

    private val _wifiState = MutableStateFlow(isWifiConnected())
    val wifiState: StateFlow<Boolean> = _wifiState.asStateFlow()

    private val _networkSpeedState = MutableStateFlow(NetworkSpeed())
    val networkSpeedState: StateFlow<NetworkSpeed> = _networkSpeedState.asStateFlow()

    private var telephonyCallback: Any? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private var previousRx = -1L
    private var previousTx = -1L
    private var previousNetworkTime = 0L
    private var lastNonZeroRxRate: Long? = null
    private var lastNonZeroTxRate: Long? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val isEnabled = isBluetoothEnabled()
                    if (_bluetoothState.value != isEnabled) {
                        _bluetoothState.value = isEnabled
                        listener?.onBluetoothStateChanged(isEnabled)
                    }
                }
                AudioManager.RINGER_MODE_CHANGED_ACTION -> {
                    val mode = getRingerMode()
                    if (_ringerModeState.value != mode) {
                        _ringerModeState.value = mode
                        listener?.onRingerModeChanged(mode)
                    }
                }
                ACTION_SIM_STATE_CHANGED,
                ACTION_SIM_CARD_STATE_CHANGED -> {
                    val isReady = isSimReady()
                    if (_simState.value != isReady) {
                        _simState.value = isReady
                        listener?.onSimStateChanged(isReady)
                    }
                }
                Intent.ACTION_BATTERY_CHANGED,
                Intent.ACTION_POWER_CONNECTED,
                Intent.ACTION_POWER_DISCONNECTED -> {
                    val newInfo = getBatteryInfo()
                    if (_batteryInfoState.value != newInfo) {
                        _batteryInfoState.value = newInfo
                        listener?.onBatteryChanged(newInfo)
                    }
                }
            }
        }
    }

    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    private fun resetNetworkStats() {
        previousRx = -1L
        previousTx = -1L
        previousNetworkTime = 0L
        lastNonZeroRxRate = null
        lastNonZeroTxRate = null
    }

    fun startMonitoring() {
        if (isMonitoring) return

        resetNetworkStats()

        _bluetoothState.value = isBluetoothEnabled()
        _ringerModeState.value = getRingerMode()
        _simState.value = isSimReady()
        _batteryInfoState.value = getBatteryInfo()
        _wifiState.value = isWifiConnected()

        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(ACTION_SIM_STATE_CHANGED)
            addAction(ACTION_SIM_CARD_STATE_CHANGED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }

        try {
            ContextCompat.registerReceiver(
                appContext,
                receiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
            isMonitoring = true
        } catch (_: Exception) {
            // Fallback or ignore if registration fails
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            registerTelephonyCallback()
        }

        registerWifiCallback()
        updateNetworkSpeed()
    }

    fun stopMonitoring() {
        if (!isMonitoring) return
        try {
            appContext.unregisterReceiver(receiver)
        } catch (_: Exception) {
            // Receiver was already unregistered or not registered
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            unregisterTelephonyCallback()
        }

        unregisterWifiCallback()

        resetNetworkStats()

        isMonitoring = false
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun registerTelephonyCallback() {
        try {
            val tm = appContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return
            val callback = object : TelephonyCallback(), TelephonyCallback.ServiceStateListener {
                override fun onServiceStateChanged(serviceState: ServiceState) {
                    val isReady = isSimReady()
                    if (_simState.value != isReady) {
                        _simState.value = isReady
                        listener?.onSimStateChanged(isReady)
                    }
                }
            }
            telephonyCallback = callback
            tm.registerTelephonyCallback(appContext.mainExecutor, callback)
        } catch (_: Exception) {}
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun unregisterTelephonyCallback() {
        try {
            val callback = telephonyCallback as? TelephonyCallback ?: return
            val tm = appContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return
            tm.unregisterTelephonyCallback(callback)
            telephonyCallback = null
        } catch (_: Exception) {}
    }

    // --- Wi-Fi Monitoring ---

    fun isWifiConnected(): Boolean {
        return try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = cm?.activeNetwork ?: return false
                val capabilities = cm.getNetworkCapabilities(network) ?: return false
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            } else {
                @Suppress("DEPRECATION")
                val info = cm?.activeNetworkInfo
                @Suppress("DEPRECATION")
                info != null && info.isConnected && info.type == ConnectivityManager.TYPE_WIFI
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun registerWifiCallback() {
        try {
            if (networkCallback != null) return
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (!_wifiState.value) {
                        _wifiState.value = true
                        listener?.onWifiStateChanged(true)
                    }
                }

                override fun onLost(network: Network) {
                    if (_wifiState.value) {
                        _wifiState.value = false
                        listener?.onWifiStateChanged(false)
                    }
                }
            }
            networkCallback = callback
            cm.registerNetworkCallback(request, callback)
        } catch (_: Exception) {
            _wifiState.value = false
        }
    }

    private fun unregisterWifiCallback() {
        networkCallback?.let { callback ->
            try {
                val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                cm?.unregisterNetworkCallback(callback)
            } catch (_: Exception) {}
            networkCallback = null
        }
    }

    // --- Network Traffic Monitoring ---

    fun updateNetworkSpeed(): NetworkSpeed {
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        val now = SystemClock.elapsedRealtime()

        if (rx == TrafficStats.UNSUPPORTED.toLong() || tx == TrafficStats.UNSUPPORTED.toLong()) {
            val speed = NetworkSpeed(rxRate = 0L, txRate = 0L, totalRxBytes = rx, totalTxBytes = tx, isSupported = false)
            _networkSpeedState.value = speed
            listener?.onNetworkSpeedChanged(speed)
            return speed
        }

        val elapsedSeconds = if (previousNetworkTime > 0L) {
            ((now - previousNetworkTime).coerceAtLeast(1L)) / 1000f
        } else 1f

        val rxRate = if (previousRx >= 0 && rx >= previousRx) ((rx - previousRx) / elapsedSeconds).toLong() else 0L
        val txRate = if (previousTx >= 0 && tx >= previousTx) ((tx - previousTx) / elapsedSeconds).toLong() else 0L

        previousRx = rx
        previousTx = tx
        previousNetworkTime = now

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

        val speed = NetworkSpeed(
            rxRate = displayedRxRate,
            txRate = displayedTxRate,
            totalRxBytes = rx,
            totalTxBytes = tx,
            isSupported = true
        )
        _networkSpeedState.value = speed
        listener?.onNetworkSpeedChanged(speed)
        return speed
    }

    // --- Bluetooth Monitoring ---

    fun isBluetoothEnabled(): Boolean {
        return try {
            val bm = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = bm?.adapter ?: @Suppress("DEPRECATION") BluetoothAdapter.getDefaultAdapter()
            adapter != null && adapter.isEnabled
        } catch (_: Exception) {
            false
        }
    }

    // --- SIM Card Monitoring ---

    fun isSimReady(): Boolean {
        return try {
            val tm = appContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                ContextCompat.checkSelfPermission(
                    appContext,
                    android.Manifest.permission.READ_PHONE_STATE
                ) == PackageManager.PERMISSION_GRANTED
            } else true

            if (tm != null && hasPermission) {
                tm.simState == TelephonyManager.SIM_STATE_READY
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    // --- Ringer Mode Monitoring & Operations ---

    fun getRingerMode(): Int {
        return try {
            val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            am?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
        } catch (_: Exception) {
            AudioManager.RINGER_MODE_NORMAL
        }
    }

    fun toggleRingerMode(onPermissionRequired: (() -> Unit)? = null): Int {
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return AudioManager.RINGER_MODE_NORMAL

        val currentMode = am.ringerMode
        val nextMode = when (currentMode) {
            AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
            AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
            else -> AudioManager.RINGER_MODE_NORMAL
        }

        if (nextMode == AudioManager.RINGER_MODE_SILENT && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (notificationManager?.isNotificationPolicyAccessGranted == false) {
                onPermissionRequired?.invoke()
                try {
                    am.ringerMode = AudioManager.RINGER_MODE_NORMAL
                } catch (_: Exception) {}
                val normal = AudioManager.RINGER_MODE_NORMAL
                _ringerModeState.value = normal
                return normal
            }
        }

        try {
            am.ringerMode = nextMode
        } catch (_: Exception) {
            // Guard against missing permissions
        }

        val updated = am.ringerMode
        _ringerModeState.value = updated
        return updated
    }

    // --- Battery Monitoring ---

    fun getBatteryIntent(): Intent? {
        return try {
            ContextCompat.registerReceiver(
                appContext,
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_EXPORTED
            )
        } catch (_: Exception) {
            null
        }
    }

    fun getBatteryInfo(): BatteryInfo {
        val batteryIntent = getBatteryIntent()
        val voltageMv = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val tempRaw = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val temperatureC = tempRaw / 10f
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        return BatteryInfo(
            voltageMv = voltageMv,
            temperatureC = temperatureC,
            level = level,
            scale = scale,
            status = status,
            isCharging = isCharging,
            rawIntent = batteryIntent
        )
    }

    companion object {
        private const val ACTION_SIM_STATE_CHANGED = "android.intent.action.SIM_STATE_CHANGED"
        private const val ACTION_SIM_CARD_STATE_CHANGED = "android.telephony.action.SIM_CARD_STATE_CHANGED"
    }
}
