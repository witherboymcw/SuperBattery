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
import com.witherboymcw.superbattery.ui.CheckboxItemSpec
import com.witherboymcw.superbattery.ui.SimpleCheckboxCard

class Permissions(private val activity: MainActivity) {

    companion object {
        const val KEY_SUPER_PERMISSIONS_DROPDOWN_OPEN = "super_permissions_dropdown_open"

        fun isPostNotificationsEnabled(context: Context): Boolean {
            val isGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
            val isUserToggleOn = AppPreferences.isPostNotificationsEnabled(context)
            return isGranted && isUserToggleOn
        }

        fun isNotificationAccessEnabled(context: Context): Boolean {
            val isGranted = hasNotificationAccess(context)
            val isUserToggleOn = AppPreferences.isNotificationAccessEnabled(context)
            return isGranted && isUserToggleOn
        }

        fun isPhoneStateEnabled(context: Context): Boolean {
            val isGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
            } else true
            val isUserToggleOn = AppPreferences.isPhoneStateEnabled(context)
            return isGranted && isUserToggleOn
        }

        fun isOverlayEnabled(context: Context): Boolean {
            val isGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else true
            val isUserToggleOn = AppPreferences.isOverlayEnabled(context)
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

        val itemSpecs = listOf(
            CheckboxItemSpec(
                checkBox = cbPostNotifications,
                initialChecked = isPostNotificationsEnabled(activity),
                onCheckedChange = { isChecked ->
                    AppPreferences.setPostNotificationsEnabled(activity, isChecked)
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
            ),
            CheckboxItemSpec(
                checkBox = cbNotificationAccess,
                initialChecked = isNotificationAccessEnabled(activity),
                onCheckedChange = { isChecked ->
                    AppPreferences.setNotificationAccessEnabled(activity, isChecked)
                    if (isChecked && !hasNotificationAccess(activity)) {
                        try {
                            activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        } catch (_: Exception) {}
                    }
                    updateStatuses()
                }
            ),
            CheckboxItemSpec(
                checkBox = cbPhoneState,
                initialChecked = isPhoneStateEnabled(activity),
                onCheckedChange = { isChecked ->
                    AppPreferences.setPhoneStateEnabled(activity, isChecked)
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
            ),
            CheckboxItemSpec(
                checkBox = cbOverlay,
                initialChecked = isOverlayEnabled(activity),
                onCheckedChange = { isChecked ->
                    AppPreferences.setOverlayEnabled(activity, isChecked)
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
            )
        )

        SimpleCheckboxCard.bind(
            headerView = headerSuperPermissions,
            dropdownView = dropdownSuperPermissions,
            arrowView = arrowSuperPermissions,
            summaryTextView = selectedSuperPermissionsText,
            savedInstanceStateKey = KEY_SUPER_PERMISSIONS_DROPDOWN_OPEN,
            savedInstanceState = savedInstanceState,
            items = itemSpecs
        )

        updateStatuses()
    }

    fun updateStatuses() {
        val hasPost = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
        val postPref = AppPreferences.isPostNotificationsEnabled(activity)
        val postActive = hasPost && postPref

        val hasNotifAccess = hasNotificationAccess(activity)
        val notifAccessPref = AppPreferences.isNotificationAccessEnabled(activity)
        val notifAccessActive = hasNotifAccess && notifAccessPref

        val hasPhoneState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            activity.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        } else true
        val phoneStatePref = AppPreferences.isPhoneStateEnabled(activity)
        val phoneStateActive = hasPhoneState && phoneStatePref

        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(activity)
        } else true
        val overlayPref = AppPreferences.isOverlayEnabled(activity)
        val overlayActive = hasOverlay && overlayPref

        cbPostNotifications?.isChecked = postActive
        cbNotificationAccess?.isChecked = notifAccessActive
        cbPhoneState?.isChecked = phoneStateActive
        cbOverlay?.isChecked = overlayActive

        SimpleCheckboxCard.updateSummary(
            selectedSuperPermissionsText,
            listOf(cbPostNotifications, cbNotificationAccess, cbPhoneState, cbOverlay)
        )
    }
}
