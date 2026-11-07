package com.witherboymcw.superbattery

import android.app.Notification
import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.content.ContextCompat

import java.lang.ref.WeakReference

class HotspotNotificationListener : NotificationListenerService() {

    private var stateReceiverRegistered = false
    private var dateFormatObserverRegistered = false

    private val dateFormatObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            refreshWidgets()
        }
    }

    private val stateChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refreshWidgets()
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(LOG_TAG, "=== CONNECTED ===")
        connectedListenerRef = WeakReference(this)
        registerStateChangeReceiver()
        registerDateFormatObserver()
        checkHotspotNotification()
    }

    override fun onListenerDisconnected() {
        Log.d(LOG_TAG, "=== DISCONNECTED ===")
        cleanup()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun cleanup() {
        if (stateReceiverRegistered) {
            try {
                unregisterReceiver(stateChangeReceiver)
            } catch (_: Exception) { }
            stateReceiverRegistered = false
        }
        if (dateFormatObserverRegistered) {
            try {
                contentResolver.unregisterContentObserver(dateFormatObserver)
            } catch (_: Exception) { }
            dateFormatObserverRegistered = false
        }
        if (connectedListenerRef?.get() === this) {
            connectedListenerRef = null
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val title = sbn?.notification?.extras
            ?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = sbn?.notification?.extras
            ?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        Log.d(
            LOG_TAG,
            "New notification -> Package: ${sbn?.packageName} | Title: '$title' | Text: '$text'"
        )

        val notification = sbn ?: return
        val newStatus = hotspotStatusFromNotification(notification)
        if (newStatus == null) {
            Log.d(LOG_TAG, "Notification not recognized as hotspot status")
            return
        }
        Log.d(LOG_TAG, "Recognized hotspot status: $newStatus")
        if (setCurrentStatus(this, newStatus)) {
            Log.d(LOG_TAG, "Status changed -> requesting widget refresh")
            refreshWidgets()
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val removedNotification = sbn ?: return
        if (hotspotStatusFromNotification(removedNotification) != null) {
            checkHotspotNotification(excludingKey = removedNotification.key)
        }
    }

    private fun checkHotspotNotification(excludingKey: String? = null) {
        val activeNotifs = try { activeNotifications } catch (_: Exception) { return }
        if (activeNotifs == null) return
        val newStatus = activeNotifs.asSequence()
            .filterNotNull()
            .filterNot { it.key == excludingKey }
            .firstNotNullOfOrNull(::hotspotStatusFromNotification)
            ?: HotspotState.OFF

        if (setCurrentStatus(this, newStatus)) refreshWidgets()
    }

    private fun refreshStatusFromWidgetTap() {
        checkHotspotNotification()
    }

    private fun hotspotStatusFromNotification(sbn: StatusBarNotification): HotspotState? {
        val notif = sbn.notification ?: return null
        val extras = notif.extras ?: return null
        val titleAndText = buildList {
            add(extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty())
            add(extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString().orEmpty())
            add(extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty())
            add(extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty())
            add(extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString().orEmpty())
            add(extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty())
            add(extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString().orEmpty())
            add(notif.tickerText?.toString().orEmpty())
            try {
                extras.keySet().forEach { key ->
                    when (val value = extras.get(key)) {
                        is CharSequence -> add(value.toString())
                        is Array<*> -> value.filterIsInstance<CharSequence>().forEach { add(it.toString()) }
                        is Iterable<*> -> value.filterIsInstance<CharSequence>().forEach { add(it.toString()) }
                    }
                }
            } catch (_: Exception) {
                // Ignore BadParcelableException if notification contains unknown 3rd party parcelables
            }
        }.asSequence()
            .map { it.replace(WHITESPACE_REGEX, " ").trim() }
            .filter { it.isNotEmpty() }
            .toList()

        val notificationTitle = listOf(
            extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString().orEmpty()
        ).joinToString(" ").replace(WHITESPACE_REGEX, " ").trim()

        val isHotspotTitle = notificationTitle.contains(HOTSPOT_NOTIFICATION_TITLE, ignoreCase = true) ||
            notificationTitle.contains(HOTSPOT_NOTIFICATION_TITLE_VI, ignoreCase = true)
        if (!isHotspotTitle) return null

        val notificationContent = titleAndText.joinToString(" ")

        // Read the device-count text only; the tethering channel or other notifications
        // are not used to decide whether the hotspot is on.
        if (NO_CONNECTED_DEVICE.containsMatchIn(notificationContent)) return HotspotState.ON_EMPTY
        val clients = CONNECTED_DEVICES.find(notificationContent)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: return HotspotState.ON_EMPTY
        return if (clients == 0) HotspotState.ON_EMPTY else HotspotState.ON_CONNECTED(clients)
    }

    private fun registerStateChangeReceiver() {
        if (stateReceiverRegistered) return

        val filter = IntentFilter().apply {
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(NotificationManager.ACTION_NOTIFICATION_POLICY_CHANGED)
            addAction(Intent.ACTION_CONFIGURATION_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_OKAY)
            addAction(Intent.ACTION_LOCALE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction("android.intent.action.TIME_SET")
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }

        ContextCompat.registerReceiver(
            this,
            stateChangeReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
        stateReceiverRegistered = true
    }

    private fun registerDateFormatObserver() {
        if (dateFormatObserverRegistered) return
        contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.TIME_12_24),
            false,
            dateFormatObserver
        )
        // Retain support for older Android versions that still expose this setting.
        contentResolver.registerContentObserver(
            Settings.System.getUriFor("date_format"),
            false,
            dateFormatObserver
        )
        dateFormatObserverRegistered = true
    }

    private fun refreshWidgets() {
        Log.d(LOG_TAG, "Calling BatteryWidget.updateAllWidgets()")
        BatteryWidget.updateAllWidgets(this, AppWidgetManager.getInstance(this))
    }

    companion object {
        private const val LOG_TAG = "HotspotListener"
        private const val PREFERENCES_NAME = "hotspot_notification_state"
        private const val KEY_STATE = "state"
        private const val KEY_CLIENTS = "clients"
        private const val HOTSPOT_NOTIFICATION_TITLE = "Tethering or hotspot active"
        private const val HOTSPOT_NOTIFICATION_TITLE_VI = "Điểm phát sóng"
        private val WHITESPACE_REGEX = Regex("\\s+")
        private val NO_CONNECTED_DEVICE = Regex("\\b(no\\s+connected\\s+devices?|không\\s+có\\s+thiết\\s+bị)\\b", RegexOption.IGNORE_CASE)
        private val CONNECTED_DEVICES = Regex("\\b(\\d+)\\s+(connected\\s+devices?|thiết\\s+bị\\s+đã\\s+kết\\s+nối|thiết\\s+bị\\s+kết\\s+nối)\\b", RegexOption.IGNORE_CASE)

        sealed class HotspotState {
            data object OFF : HotspotState()
            data object ON_EMPTY : HotspotState()
            data class ON_CONNECTED(val clients: Int) : HotspotState()
        }

        var currentStatus: HotspotState = HotspotState.OFF
            private set

        @Volatile
        private var connectedListenerRef: WeakReference<HotspotNotificationListener>? = null

        internal fun refreshFromWidgetTap() {
            connectedListenerRef?.get()?.refreshStatusFromWidgetTap()
        }

        @Synchronized
        internal fun setCurrentStatus(context: Context, status: HotspotState): Boolean {
            val changed = loadCurrentStatus(context) != status
            currentStatus = status
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_STATE, when (status) {
                    HotspotState.OFF -> "off"
                    HotspotState.ON_EMPTY -> "on_empty"
                    is HotspotState.ON_CONNECTED -> "on_connected"
                })
                .putInt(KEY_CLIENTS, (status as? HotspotState.ON_CONNECTED)?.clients ?: 0)
                .apply()
            return changed
        }

        @Synchronized
        internal fun loadCurrentStatus(context: Context): HotspotState {
            val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            val status = when (preferences.getString(KEY_STATE, "off")) {
                "on_empty" -> HotspotState.ON_EMPTY
                "on_connected" -> HotspotState.ON_CONNECTED(preferences.getInt(KEY_CLIENTS, 0).coerceAtLeast(0))
                else -> HotspotState.OFF
            }
            currentStatus = status
            return status
        }
    }
}
