package com.witherboymcw.superbattery.screens.hotspot

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
import com.witherboymcw.superbattery.widgets.BatteryWidget
import org.json.JSONArray
import java.lang.ref.WeakReference

object HotspotRuleLoader {
    private const val RULES_FILE = "hotspot_rules.json"

    @Volatile
    private var cachedRules: List<CompiledHotspotRule>? = null

    fun getRules(context: Context): List<CompiledHotspotRule> {
        cachedRules?.let { return it }
        val loaded = loadFromAssets(context)
        val finalRules = if (loaded.isNotEmpty()) loaded else HotspotNotificationParser.DEFAULT_RULES
        cachedRules = finalRules
        return finalRules
    }

    private fun loadFromAssets(context: Context): List<CompiledHotspotRule> {
        return try {
            val jsonString = context.assets.open(RULES_FILE).use { stream ->
                stream.bufferedReader().use { it.readText() }
            }
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<CompiledHotspotRule>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val oem = obj.optString("oem", "Unknown OEM")
                val patternsArr = obj.optJSONArray("title_patterns")
                val patterns = mutableListOf<String>()
                if (patternsArr != null) {
                    for (j in 0 until patternsArr.length()) {
                        patterns.add(patternsArr.getString(j))
                    }
                }
                val noDevPattern = obj.optString("no_device_regex")
                val connPattern = obj.optString("connected_regex")
                if (patterns.isNotEmpty() && noDevPattern.isNotBlank() && connPattern.isNotBlank()) {
                    val compiledTitles = patterns.map { pattern ->
                        Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                    }
                    list.add(
                        CompiledHotspotRule(
                            oem = oem,
                            compiledTitleRegexes = compiledTitles,
                            noDeviceRegex = Regex(noDevPattern, RegexOption.IGNORE_CASE),
                            connectedRegex = Regex(connPattern, RegexOption.IGNORE_CASE)
                        )
                    )
                }
            }
            list
        } catch (e: Exception) {
            Log.e("HotspotRuleLoader", "Failed to load $RULES_FILE from assets, using default rules", e)
            emptyList()
        }
    }
}

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
            } catch (_: Exception) {
            } finally {
                stateReceiverRegistered = false
            }
        }
        if (dateFormatObserverRegistered) {
            try {
                contentResolver.unregisterContentObserver(dateFormatObserver)
            } catch (_: Exception) {
            } finally {
                dateFormatObserverRegistered = false
            }
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
        val statuses = activeNotifs.asSequence()
            .filterNotNull()
            .filterNot { it.key == excludingKey }
            .mapNotNull(::hotspotStatusFromNotification)
            .toList()

        val newStatus = statuses.maxByOrNull { status ->
            when (status) {
                is HotspotState.ON_CONNECTED -> 2
                is HotspotState.ON_EMPTY -> 1
                is HotspotState.OFF -> 0
            }
        } ?: HotspotState.OFF

        if (setCurrentStatus(this, newStatus)) refreshWidgets()
    }

    private fun refreshStatusFromWidgetTap() {
        checkHotspotNotification()
    }

    private fun hotspotStatusFromNotification(sbn: StatusBarNotification): HotspotState? {
        val notif = sbn.notification ?: return null
        val extras = notif.extras ?: return null

        val titleCharSeq = try { extras.getCharSequence(Notification.EXTRA_TITLE) } catch (_: Exception) { null }
        val bigTitleCharSeq = try { extras.getCharSequence(Notification.EXTRA_TITLE_BIG) } catch (_: Exception) { null }
        val notificationTitle = listOfNotNull(titleCharSeq, bigTitleCharSeq)
            .filter { it.isNotBlank() }
            .joinToString(" ")

        val sb = StringBuilder()
        fun appendText(cs: CharSequence?) {
            if (!cs.isNullOrBlank()) {
                sb.append(cs).append(' ')
            }
        }

        listOf(
            Notification.EXTRA_TITLE,
            Notification.EXTRA_TITLE_BIG,
            Notification.EXTRA_TEXT,
            Notification.EXTRA_BIG_TEXT,
            Notification.EXTRA_SUMMARY_TEXT,
            Notification.EXTRA_SUB_TEXT,
            Notification.EXTRA_INFO_TEXT
        ).forEach { extraKey ->
            try {
                appendText(extras.getCharSequence(extraKey))
            } catch (_: Exception) {
            }
        }

        try {
            appendText(notif.tickerText)
        } catch (_: Exception) {
        }

        try {
            extras.keySet()?.forEach { key ->
                try {
                    when (val value = extras.get(key)) {
                        is CharSequence -> appendText(value)
                        is Array<*> -> value.filterIsInstance<CharSequence>().forEach { appendText(it) }
                        is Iterable<*> -> value.filterIsInstance<CharSequence>().forEach { appendText(it) }
                    }
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }

        val notificationContent = sb.toString()
        val rules = HotspotRuleLoader.getRules(this)

        return HotspotNotificationParser.parseNotification(
            notificationTitle = notificationTitle,
            notificationContent = notificationContent,
            rules = rules
        )
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
