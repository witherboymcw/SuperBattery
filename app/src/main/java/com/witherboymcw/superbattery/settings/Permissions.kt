package com.witherboymcw.superbattery.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.witherboymcw.superbattery.MainActivity
import com.witherboymcw.superbattery.R
import com.witherboymcw.superbattery.widgets.BatteryWidget

class Permissions(private val activity: MainActivity) {

    companion object {
        const val PREF_ENABLE_POST_NOTIFICATIONS = "enable_post_notifications"
        const val PREF_ENABLE_NOTIFICATION_ACCESS = "enable_notification_access"
        const val PREF_ENABLE_PHONE_STATE = "enable_phone_state"
        const val PREF_ENABLE_OVERLAY = "enable_overlay"
        const val KEY_SUPER_PERMISSIONS_DROPDOWN_OPEN = "super_permissions_dropdown_open"

        fun isPostNotificationsEnabled(context: Context): Boolean {
            val isGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
            val isUserToggleOn = context.getSharedPreferences(BatteryWidget.SETTINGS_PREFS, Context.MODE_PRIVATE)
                .getBoolean(PREF_ENABLE_POST_NOTIFICATIONS, true)
            return isGranted && isUserToggleOn
        }

        fun isNotificationAccessEnabled(context: Context): Boolean {
            val isGranted = hasNotificationAccess(context)
            val isUserToggleOn = context.getSharedPreferences(BatteryWidget.SETTINGS_PREFS, Context.MODE_PRIVATE)
                .getBoolean(PREF_ENABLE_NOTIFICATION_ACCESS, true)
            return isGranted && isUserToggleOn
        }

        fun isPhoneStateEnabled(context: Context): Boolean {
            val isGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
            } else true
            val isUserToggleOn = context.getSharedPreferences(BatteryWidget.SETTINGS_PREFS, Context.MODE_PRIVATE)
                .getBoolean(PREF_ENABLE_PHONE_STATE, true)
            return isGranted && isUserToggleOn
        }

        fun isOverlayEnabled(context: Context): Boolean {
            val isGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else true
            val isUserToggleOn = context.getSharedPreferences(BatteryWidget.SETTINGS_PREFS, Context.MODE_PRIVATE)
                .getBoolean(PREF_ENABLE_OVERLAY, true)
            return isGranted && isUserToggleOn
        }

        private fun hasNotificationAccess(context: Context): Boolean {
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: ""
            val packageName = context.packageName
            return flat.contains(packageName)
        }
    }

    private var headerSuperPermissions: View? = null
    private var dropdownSuperPermissions: View? = null
    private var arrowSuperPermissions: ImageView? = null
    private var selectedSuperPermissionsText: TextView? = null

    private var cbPostNotifications: CheckBox? = null
    private var cbNotificationAccess: CheckBox? = null
    private var cbPhoneState: CheckBox? = null
    private var cbOverlay: CheckBox? = null

    fun bind(savedInstanceState: Bundle?) {
        headerSuperPermissions = activity.findViewById(R.id.header_super_permissions)
        dropdownSuperPermissions = activity.findViewById(R.id.dropdown_super_permissions)
        arrowSuperPermissions = activity.findViewById(R.id.arrow_super_permissions)
        selectedSuperPermissionsText = activity.findViewById(R.id.selected_super_permissions_text)

        cbPostNotifications = activity.findViewById(R.id.cb_permission_post_notifications)
        cbNotificationAccess = activity.findViewById(R.id.cb_permission_notification_access)
        cbPhoneState = activity.findViewById(R.id.cb_permission_phone_state)
        cbOverlay = activity.findViewById(R.id.cb_permission_overlay)

        headerSuperPermissions?.setOnClickListener {
            val isExpanded = dropdownSuperPermissions?.visibility == View.VISIBLE
            dropdownSuperPermissions?.visibility = if (isExpanded) View.GONE else View.VISIBLE
            arrowSuperPermissions?.animate()?.rotation(if (isExpanded) 0f else 180f)?.setDuration(200L)?.start()
        }

        if (savedInstanceState?.getBoolean(KEY_SUPER_PERMISSIONS_DROPDOWN_OPEN) == true) {
            dropdownSuperPermissions?.visibility = View.VISIBLE
            arrowSuperPermissions?.rotation = 180f
        }

        setupCheckboxListeners()
        updateStatuses()
    }

    private fun setupCheckboxListeners() {
        val prefs = activity.getSharedPreferences(BatteryWidget.SETTINGS_PREFS, Context.MODE_PRIVATE)

        cbPostNotifications?.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(PREF_ENABLE_POST_NOTIFICATIONS, isChecked).apply()
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    activity.requestPermissions(
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        MainActivity.NOTIFICATION_PERMISSION_REQUEST
                    )
                }
            }
            updateStatuses()
        }

        cbNotificationAccess?.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(PREF_ENABLE_NOTIFICATION_ACCESS, isChecked).apply()
            if (isChecked && !hasNotificationAccess(activity)) {
                try {
                    activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                } catch (_: Exception) {}
            }
            updateStatuses()
        }

        cbPhoneState?.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(PREF_ENABLE_PHONE_STATE, isChecked).apply()
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    activity.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED
                ) {
                    activity.requestPermissions(
                        arrayOf(Manifest.permission.READ_PHONE_STATE),
                        MainActivity.PHONE_STATE_PERMISSION_REQUEST
                    )
                }
            }
            updateStatuses()
        }

        cbOverlay?.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(PREF_ENABLE_OVERLAY, isChecked).apply()
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(activity)) {
                    try {
                        activity.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${activity.packageName}")
                            )
                        )
                    } catch (_: Exception) {}
                }
            }
            updateStatuses()
        }
    }

    fun updateStatuses() {
        val prefs = activity.getSharedPreferences(BatteryWidget.SETTINGS_PREFS, Context.MODE_PRIVATE)

        val hasPost = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
        val postPref = prefs.getBoolean(PREF_ENABLE_POST_NOTIFICATIONS, true)
        val postActive = hasPost && postPref

        val hasNotifAccess = hasNotificationAccess(activity)
        val notifAccessPref = prefs.getBoolean(PREF_ENABLE_NOTIFICATION_ACCESS, true)
        val notifAccessActive = hasNotifAccess && notifAccessPref

        val hasPhoneState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            activity.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        } else true
        val phoneStatePref = prefs.getBoolean(PREF_ENABLE_PHONE_STATE, true)
        val phoneStateActive = hasPhoneState && phoneStatePref

        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(activity)
        } else true
        val overlayPref = prefs.getBoolean(PREF_ENABLE_OVERLAY, true)
        val overlayActive = hasOverlay && overlayPref

        cbPostNotifications?.setOnCheckedChangeListener(null)
        cbNotificationAccess?.setOnCheckedChangeListener(null)
        cbPhoneState?.setOnCheckedChangeListener(null)
        cbOverlay?.setOnCheckedChangeListener(null)

        cbPostNotifications?.isChecked = postActive
        cbNotificationAccess?.isChecked = notifAccessActive
        cbPhoneState?.isChecked = phoneStateActive
        cbOverlay?.isChecked = overlayActive

        setupCheckboxListeners()

        val activeCount = (if (postActive) 1 else 0) +
                (if (notifAccessActive) 1 else 0) +
                (if (phoneStateActive) 1 else 0) +
                (if (overlayActive) 1 else 0)

        selectedSuperPermissionsText?.text = "$activeCount/4"
    }
}
