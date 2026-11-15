package com.witherboymcw.superbattery.monitors

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.telephony.ServiceState
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import com.witherboymcw.superbattery.settings.Permissions

/**
 * Source module responsible for Wi-Fi, Bluetooth, and SIM telephony connectivity state tracking.
 */
class ConnectivityMonitorSource {

    private var telephonyCallback: Any? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    fun isWifiConnected(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
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

    fun registerWifiCallback(context: Context, onWifiChanged: (Boolean) -> Unit) {
        try {
            if (networkCallback != null) return
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    onWifiChanged(true)
                }

                override fun onLost(network: Network) {
                    onWifiChanged(false)
                }
            }
            networkCallback = callback
            cm.registerNetworkCallback(request, callback)
        } catch (_: Exception) {
            onWifiChanged(false)
        }
    }

    fun unregisterWifiCallback(context: Context) {
        networkCallback?.let { callback ->
            try {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                cm?.unregisterNetworkCallback(callback)
            } catch (_: Exception) {}
            networkCallback = null
        }
    }

    fun isBluetoothEnabled(context: Context): Boolean {
        return try {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = bm?.adapter ?: @Suppress("DEPRECATION") BluetoothAdapter.getDefaultAdapter()
            adapter != null && adapter.isEnabled
        } catch (_: Exception) {
            false
        }
    }

    fun isSimReady(context: Context): Boolean {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val hasPermission = Permissions.isPhoneStateEnabled(context)

            if (tm != null && hasPermission) {
                tm.simState == TelephonyManager.SIM_STATE_READY
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun registerTelephonyCallback(context: Context, onSimStateChanged: () -> Unit) {
        try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return
            val callback = object : TelephonyCallback(), TelephonyCallback.ServiceStateListener {
                override fun onServiceStateChanged(serviceState: ServiceState) {
                    onSimStateChanged()
                }
            }
            telephonyCallback = callback
            tm.registerTelephonyCallback(context.mainExecutor, callback)
        } catch (_: Exception) {}
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun unregisterTelephonyCallback(context: Context) {
        try {
            val callback = telephonyCallback as? TelephonyCallback ?: return
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return
            tm.unregisterTelephonyCallback(callback)
            telephonyCallback = null
        } catch (_: Exception) {}
    }
}
