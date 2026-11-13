package com.witherboymcw.superbattery

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.witherboymcw.superbattery.monitors.AudioRingerSource
import com.witherboymcw.superbattery.monitors.BatteryMonitorSource
import com.witherboymcw.superbattery.monitors.ConnectivityMonitorSource
import com.witherboymcw.superbattery.monitors.NetworkTrafficSource
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
 * SystemMonitorManager is an independent coordinator responsible for orchestrating dedicated
 * source modules ([BatteryMonitorSource], [NetworkTrafficSource], [ConnectivityMonitorSource], [AudioRingerSource])
 * and exposing reactive Kotlin StateFlows for UI state collection without memory leaks.
 */
class SystemMonitorManager(context: Context) {

    private val appContext: Context = context.applicationContext

    private val batterySource = BatteryMonitorSource()
    private val networkTrafficSource = NetworkTrafficSource()
    private val connectivitySource = ConnectivityMonitorSource()
    private val ringerSource = AudioRingerSource()

    private var isMonitoring = false

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

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val isEnabled = isBluetoothEnabled()
                    if (_bluetoothState.value != isEnabled) {
                        _bluetoothState.value = isEnabled
                    }
                }
                AudioManager.RINGER_MODE_CHANGED_ACTION -> {
                    val mode = getRingerMode()
                    if (_ringerModeState.value != mode) {
                        _ringerModeState.value = mode
                    }
                }
                ACTION_SIM_STATE_CHANGED,
                ACTION_SIM_CARD_STATE_CHANGED -> {
                    val isReady = isSimReady()
                    if (_simState.value != isReady) {
                        _simState.value = isReady
                    }
                }
                Intent.ACTION_BATTERY_CHANGED,
                Intent.ACTION_POWER_CONNECTED,
                Intent.ACTION_POWER_DISCONNECTED -> {
                    val newInfo = getBatteryInfo()
                    if (_batteryInfoState.value != newInfo) {
                        _batteryInfoState.value = newInfo
                    }
                }
            }
        }
    }

    fun startMonitoring() {
        if (isMonitoring) return

        networkTrafficSource.resetNetworkStats()

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
        } catch (_: Exception) {}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            connectivitySource.registerTelephonyCallback(appContext) {
                val isReady = isSimReady()
                if (_simState.value != isReady) {
                    _simState.value = isReady
                }
            }
        }

        connectivitySource.registerWifiCallback(appContext) { isConnected ->
            if (_wifiState.value != isConnected) {
                _wifiState.value = isConnected
            }
        }

        updateNetworkSpeed()
    }

    fun stopMonitoring() {
        if (!isMonitoring) return
        try {
            appContext.unregisterReceiver(receiver)
        } catch (_: Exception) {}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            connectivitySource.unregisterTelephonyCallback(appContext)
        }

        connectivitySource.unregisterWifiCallback(appContext)
        networkTrafficSource.resetNetworkStats()

        isMonitoring = false
    }

    fun isWifiConnected(): Boolean = connectivitySource.isWifiConnected(appContext)

    fun updateNetworkSpeed(): NetworkSpeed {
        val speed = networkTrafficSource.updateNetworkSpeed()
        _networkSpeedState.value = speed
        return speed
    }

    fun isBluetoothEnabled(): Boolean = connectivitySource.isBluetoothEnabled(appContext)

    fun isSimReady(): Boolean = connectivitySource.isSimReady(appContext)

    fun getRingerMode(): Int = ringerSource.getRingerMode(appContext)

    fun toggleRingerMode(onPermissionRequired: (() -> Unit)? = null): Int {
        val updated = ringerSource.toggleRingerMode(appContext, onPermissionRequired)
        _ringerModeState.value = updated
        return updated
    }

    fun getBatteryIntent(): Intent? = batterySource.getBatteryIntent(appContext)

    fun getBatteryInfo(): BatteryInfo = batterySource.getBatteryInfo(appContext)

    companion object {
        private const val ACTION_SIM_STATE_CHANGED = "android.intent.action.SIM_STATE_CHANGED"
        private const val ACTION_SIM_CARD_STATE_CHANGED = "android.telephony.action.SIM_CARD_STATE_CHANGED"
    }
}
