package com.witherboymcw.superbattery

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import java.util.Locale

internal class HotspotViewController {

    private var activityRef: MainActivity? = null
    private var selectedDeviceMac: String? = null

    private fun getTargetLayoutRes(): Int = R.layout.layout_hotspot

    fun bind(activity: MainActivity, savedInstanceState: Bundle? = null) {
        this.activityRef = activity
        val targetLayout = getTargetLayoutRes()
        val currentContainer = activity.findViewById<ViewGroup>(R.id.hotspot_container) ?: return
        val parentView = currentContainer.parent as? ViewGroup

        if (parentView != null) {
            val currentTag = currentContainer.getTag(R.id.hotspot_container) as? Int
            if (currentTag != targetLayout) {
                val index = parentView.indexOfChild(currentContainer)
                val visibility = currentContainer.visibility
                parentView.removeView(currentContainer)
                val newView = activity.layoutInflater.inflate(targetLayout, parentView, false)
                newView.id = R.id.hotspot_container
                newView.visibility = visibility
                newView.setTag(R.id.hotspot_container, targetLayout)
                parentView.addView(newView, index)
            }
        }

        activity.applyHotspotCardTheme()

        with(activity) {
            val swipeContainer = findViewById<ViewGroup>(R.id.hotspot_container) as? SwipeBackFrameLayout ?: return
            swipeContainer.isClickable = true
            swipeContainer.isFocusable = true
            swipeContainer.swipeDirection = SwipeBackFrameLayout.SWIPE_DIRECTION_LEFT

            // Back button tap
            findViewById<View>(R.id.hotspot_back_button)?.setOnClickListener {
                closeHotspotScreen()
            }

            // Swipe left callback to close hotspot container
            swipeContainer.swipeListener = object : SwipeBackFrameLayout.SwipeListener {
                override fun onSwipeProgress(translationX: Float) {
                    swipeContainer.translationX = translationX
                }

                override fun onSwipeEnd(shouldClose: Boolean) {
                    if (shouldClose) {
                        closeHotspotScreen()
                    } else {
                        swipeContainer.animate()
                            .translationX(0f)
                            .setDuration(200L)
                            .setInterpolator(FastOutSlowInInterpolator())
                            .withLayer()
                            .start()
                    }
                }
            }

            // Load persisted clients from HotspotManager and sort:
            // 1. Devices with isCurrentlyOnline == true go first
            // 2. Then sorted by lastConnectedTimeMs descending (most recently connected at top)
            val rawClients = HotspotManager.loadPersistedClients(activity)
            val sortedClients = rawClients.sortedWith(
                compareByDescending<HotspotClient> { it.isCurrentlyOnline }
                    .thenByDescending { it.lastConnectedTimeMs }
            )

            val portraitCardsContainer = findViewById<ViewGroup>(R.id.hotspot_cards_container)
            val landscapeLeftContainer = findViewById<ViewGroup>(R.id.hotspot_cards_left_container)

            if (portraitCardsContainer != null) {
                // Portrait mode: Single column layout with expandable cards
                portraitCardsContainer.removeAllViews()
                val inflater = LayoutInflater.from(activity)

                sortedClients.forEach { client ->
                    val cardView = inflater.inflate(R.layout.card_hotspot_device, portraitCardsContainer, false)
                    applyCardTheme(cardView)
                    val headerView = cardView.findViewById<View>(R.id.header_hotspot_device)
                    val dropdownView = cardView.findViewById<View>(R.id.dropdown_hotspot_device)
                    val tvName = cardView.findViewById<TextView>(R.id.tv_device_name)
                    val tvMac = cardView.findViewById<TextView>(R.id.tv_device_mac)
                    val tvStatus = cardView.findViewById<TextView>(R.id.tv_device_status)
                    val ivArrow = cardView.findViewById<ImageView>(R.id.iv_device_arrow)

                    val radioGroup = cardView.findViewById<RadioGroup>(R.id.device_status_radio_group)
                    val radioAllowed = cardView.findViewById<RadioButton>(R.id.radio_status_allowed)
                    val radioDenied = cardView.findViewById<RadioButton>(R.id.radio_status_denied)

                    val tvIp = cardView.findViewById<TextView>(R.id.tv_device_ip)
                    val tvData = cardView.findViewById<TextView>(R.id.tv_device_data)
                    val tvTime = cardView.findViewById<TextView>(R.id.tv_device_time)
                    val tvOriginalName = cardView.findViewById<TextView>(R.id.tv_device_original_name)
                    val btnRename = cardView.findViewById<View>(R.id.btn_rename_alias)
                    val btnRemoveAlias = cardView.findViewById<View>(R.id.btn_remove_alias)

                    tvName.text = client.displayName
                    tvMac.text = client.mac
                    tvStatus.text = getLocalizedStatus(activity, client)

                    if (!client.customAlias.isNullOrBlank()) {
                        tvOriginalName.visibility = View.VISIBLE
                        tvOriginalName.text = getString(R.string.hotspot_original_name, client.deviceName)
                        btnRemoveAlias.visibility = View.VISIBLE
                    } else {
                        tvOriginalName.visibility = View.GONE
                        btnRemoveAlias.visibility = View.GONE
                    }

                    tvIp.text = getString(R.string.hotspot_ip_address, client.ipAddress)
                    tvData.text = getString(R.string.hotspot_data_used, formatByteSize(client.totalDataBytes))
                    tvTime.text = getString(R.string.hotspot_connected_time, formatConnectedTime(client.lastConnectedTimeMs, client.isCurrentlyOnline))

                    // 2 Radio Buttons: Allowed and Denied
                    if (client.isDenied) {
                        radioDenied.isChecked = true
                    } else {
                        radioAllowed.isChecked = true
                    }

                    radioGroup.setOnCheckedChangeListener { _, checkedId ->
                        val newDenied = checkedId == R.id.radio_status_denied
                        if (client.isDenied != newDenied) {
                            client.isDenied = newDenied
                            HotspotManager.updateClientStatus(activity, client.mac, newDenied)
                            tvStatus.text = getLocalizedStatus(activity, client)
                        }
                    }

                    btnRename.setOnClickListener {
                        showRenameDialog(activity, client) {
                            bind(activity)
                        }
                    }

                    btnRemoveAlias.setOnClickListener {
                        client.customAlias = null
                        HotspotManager.updateClientAlias(activity, client.mac, null)
                        bind(activity)
                    }

                    headerView.setOnClickListener {
                        val isExpanded = dropdownView.visibility == View.VISIBLE
                        dropdownView.visibility = if (isExpanded) View.GONE else View.VISIBLE
                        ivArrow.animate().rotation(if (isExpanded) 0f else 180f).setDuration(200L).start()
                    }

                    portraitCardsContainer.addView(cardView)
                }
            } else if (landscapeLeftContainer != null) {
                // Landscape mode: Dual-column Master-Detail layout
                landscapeLeftContainer.removeAllViews()
                val inflater = LayoutInflater.from(activity)

                val detailCardTitle = findViewById<TextView>(R.id.hotspot_detail_title)
                val detailCardMac = findViewById<TextView>(R.id.hotspot_detail_mac)
                val detailRadioGroup = findViewById<RadioGroup>(R.id.hotspot_detail_status_radio_group)
                val detailRadioAllowed = findViewById<RadioButton>(R.id.hotspot_detail_radio_allowed)
                val detailRadioDenied = findViewById<RadioButton>(R.id.hotspot_detail_radio_denied)

                val detailIp = findViewById<TextView>(R.id.hotspot_detail_ip)
                val detailData = findViewById<TextView>(R.id.hotspot_detail_data)
                val detailTime = findViewById<TextView>(R.id.hotspot_detail_time)
                val detailOriginalName = findViewById<TextView>(R.id.hotspot_detail_original_name)
                val detailBtnRename = findViewById<View>(R.id.hotspot_detail_btn_rename)
                val detailBtnRemoveAlias = findViewById<View>(R.id.hotspot_detail_btn_remove_alias)

                val cardHeaderViews = mutableListOf<Triple<View, ImageView, TextView>>()

                fun selectDevice(targetClient: HotspotClient, selectedArrow: ImageView) {
                    selectedDeviceMac = targetClient.mac
                    cardHeaderViews.forEach { (_, arrow, _) -> arrow.rotation = 0f }
                    selectedArrow.rotation = 180f

                    detailCardTitle?.text = targetClient.displayName
                    detailCardMac?.text = targetClient.mac
                    detailIp?.text = getString(R.string.hotspot_ip_address, targetClient.ipAddress)
                    detailData?.text = getString(R.string.hotspot_data_used, formatByteSize(targetClient.totalDataBytes))
                    detailTime?.text = getString(R.string.hotspot_connected_time, formatConnectedTime(targetClient.lastConnectedTimeMs, targetClient.isCurrentlyOnline))

                    if (!targetClient.customAlias.isNullOrBlank()) {
                        detailOriginalName?.visibility = View.VISIBLE
                        detailOriginalName?.text = getString(R.string.hotspot_original_name, targetClient.deviceName)
                        detailBtnRemoveAlias?.visibility = View.VISIBLE
                    } else {
                        detailOriginalName?.visibility = View.GONE
                        detailBtnRemoveAlias?.visibility = View.GONE
                    }

                    detailRadioGroup?.setOnCheckedChangeListener(null)
                    if (targetClient.isDenied) {
                        detailRadioDenied?.isChecked = true
                    } else {
                        detailRadioAllowed?.isChecked = true
                    }

                    detailRadioGroup?.setOnCheckedChangeListener { _, checkedId ->
                        val newDenied = checkedId == R.id.hotspot_detail_radio_denied
                        if (targetClient.isDenied != newDenied) {
                            targetClient.isDenied = newDenied
                            HotspotManager.updateClientStatus(activity, targetClient.mac, newDenied)
                            val matchingTuple = cardHeaderViews.firstOrNull { it.first.tag == targetClient.mac }
                            matchingTuple?.third?.text = getLocalizedStatus(activity, targetClient)
                        }
                    }

                    detailBtnRename?.setOnClickListener {
                        showRenameDialog(activity, targetClient) {
                            bind(activity)
                        }
                    }

                    detailBtnRemoveAlias?.setOnClickListener {
                        targetClient.customAlias = null
                        HotspotManager.updateClientAlias(activity, targetClient.mac, null)
                        bind(activity)
                    }
                }

                sortedClients.forEach { client ->
                    val cardView = inflater.inflate(R.layout.card_hotspot_device, landscapeLeftContainer, false)
                    applyCardTheme(cardView)
                    val headerView = cardView.findViewById<View>(R.id.header_hotspot_device)
                    val dropdownView = cardView.findViewById<View>(R.id.dropdown_hotspot_device)
                    val tvName = cardView.findViewById<TextView>(R.id.tv_device_name)
                    val tvMac = cardView.findViewById<TextView>(R.id.tv_device_mac)
                    val tvStatus = cardView.findViewById<TextView>(R.id.tv_device_status)
                    val ivArrow = cardView.findViewById<ImageView>(R.id.iv_device_arrow)

                    dropdownView.visibility = View.GONE

                    tvName.text = client.displayName
                    tvMac.text = if (!client.customAlias.isNullOrBlank()) {
                        "${client.mac} (${client.deviceName})"
                    } else {
                        client.mac
                    }
                    tvStatus.text = getLocalizedStatus(activity, client)

                    headerView.tag = client.mac
                    cardHeaderViews.add(Triple(headerView, ivArrow, tvStatus))

                    headerView.setOnClickListener {
                        selectDevice(client, ivArrow)
                    }

                    landscapeLeftContainer.addView(cardView)
                }

                // Initial selection
                if (sortedClients.isNotEmpty()) {
                    val initialClient = sortedClients.firstOrNull { it.mac == selectedDeviceMac } ?: sortedClients.first()
                    val initialTuple = cardHeaderViews.firstOrNull { it.first.tag == initialClient.mac } ?: cardHeaderViews.first()
                    selectDevice(initialClient, initialTuple.second)
                }
            }

            val savedScrollY = savedInstanceState?.getInt(KEY_HOTSPOT_SCROLL_Y, -1) ?: -1
            if (savedScrollY >= 0) {
                val scrollView = findViewById<ScrollView>(R.id.hotspot_scroll_view)
                    ?: findViewById<ScrollView>(R.id.hotspot_scroll_left)
                scrollView?.post { scrollView.scrollTo(0, savedScrollY) }
            }

            NotchHeaderHelper.updateHotspotHeaderLayout(activity)
        }
    }

    private fun showRenameDialog(activity: MainActivity, client: HotspotClient, onUpdated: () -> Unit) {
        val builder = AlertDialog.Builder(activity)
        builder.setTitle(R.string.dialog_rename_title)
        builder.setMessage(activity.getString(R.string.dialog_rename_message, client.deviceName))

        val input = EditText(activity).apply {
            setText(client.customAlias ?: client.deviceName)
            setSingleLine(true)
            selectAll()
        }

        val container = FrameLayout(activity).apply {
            val paddingPx = (16 * activity.resources.displayMetrics.density).toInt()
            setPadding(paddingPx, paddingPx / 2, paddingPx, paddingPx / 2)
            addView(input)
        }
        builder.setView(container)

        builder.setPositiveButton(R.string.btn_save) { dialog, _ ->
            val newAlias = input.text.toString().trim()
            val targetAlias = if (newAlias.isNotBlank() && newAlias != client.deviceName) newAlias else null
            client.customAlias = targetAlias
            HotspotManager.updateClientAlias(activity, client.mac, targetAlias)
            onUpdated()
            dialog.dismiss()
        }

        builder.setNegativeButton(R.string.btn_cancel) { dialog, _ ->
            dialog.dismiss()
        }

        builder.show()
    }

    private fun applyCardTheme(cardView: View) {
        val card = cardView as? com.google.android.material.card.MaterialCardView ?: return
        val act = activityRef ?: return
        val prefs = act.getSharedPreferences(SettingsViewController.SETTINGS_PREFS, Context.MODE_PRIVATE)
        val savedTheme = prefs.getString(SettingsViewController.APP_THEME_KEY, "system") ?: "system"
        val assumeOled = prefs.getString(SettingsViewController.ASSUME_OLED_KEY, "none") ?: "none"
        val isEffectiveOled = when (assumeOled) {
            "oled" -> true
            "not_oled" -> false
            else -> OledDetector.isGuaranteedOled(act)
        }
        val isOled = savedTheme == "oled" || (savedTheme == "system" && isEffectiveOled && ((act.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES))

        val strokeWidthPx = (act.resources.displayMetrics.density * 1f).toInt()
        val defaultSurface = com.google.android.material.color.MaterialColors.getColor(
            act, com.google.android.material.R.attr.colorSurface, Color.TRANSPARENT
        )
        val outlineColor = com.google.android.material.color.MaterialColors.getColor(
            act, com.google.android.material.R.attr.colorOutline, Color.GRAY
        )

        card.strokeColor = outlineColor
        if (isOled) {
            card.setCardBackgroundColor(Color.TRANSPARENT)
            card.strokeWidth = strokeWidthPx
        } else {
            card.setCardBackgroundColor(defaultSurface)
            card.strokeWidth = 0
        }
    }

    private fun getLocalizedStatus(context: Context, client: HotspotClient): String {
        return when {
            client.isDenied -> context.getString(R.string.status_denied)
            client.isCurrentlyOnline -> context.getString(R.string.status_connected)
            else -> context.getString(R.string.status_allowed)
        }
    }

    private fun formatByteSize(bytes: Long): String {
        if (bytes <= 0L) return "0 MB"
        val kb = bytes / 1024f
        val mb = kb / 1024f
        val gb = mb / 1024f
        return when {
            gb >= 1.0f -> String.format(Locale.US, "%.1f GB", gb)
            mb >= 1.0f -> String.format(Locale.US, "%.1f MB", mb)
            else -> String.format(Locale.US, "%.1f KB", kb)
        }
    }

    private fun formatConnectedTime(timeMs: Long, isOnline: Boolean): String {
        if (timeMs <= 0L) return "Offline"
        if (isOnline) {
            val diffMinutes = ((System.currentTimeMillis() - timeMs) / 60_000L).coerceAtLeast(1)
            val hours = diffMinutes / 60
            val mins = diffMinutes % 60
            return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
        } else {
            return "Offline"
        }
    }

    fun unbind() {
        activityRef = null
    }

    companion object {
        const val KEY_HOTSPOT_SCROLL_Y = "key_hotspot_scroll_y"
        const val KEY_HOTSPOT_SCROLL_RIGHT_Y = "key_hotspot_scroll_right_y"
    }
}
