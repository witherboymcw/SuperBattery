package com.witherboymcw.superbattery

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.interpolator.view.animation.FastOutSlowInInterpolator

internal class HotspotViewController {

    data class HotspotDevice(
        val id: String,
        val name: String,
        val mac: String,
        var status: String, // "Connected", "Allowed", or "Denied"
        val ip: String,
        val dataUsed: String,
        val duration: String
    )

    private var activityRef: MainActivity? = null
    private var selectedDeviceId: String? = "dev_1"

    val devices = listOf(
        HotspotDevice("dev_1", "Galaxy S23 Ultra", "A4:C3:F0:11:22:33", "Connected", "192.168.43.101", "452.1 MB", "2h 15m"),
        HotspotDevice("dev_2", "MacBook Pro M3", "70:85:C2:AB:CD:01", "Connected", "192.168.43.102", "1.2 GB", "4h 05m"),
        HotspotDevice("dev_3", "iPhone 15 Pro", "BC:D1:D3:44:55:66", "Connected", "192.168.43.103", "89.4 MB", "45m"),
        HotspotDevice("dev_4", "iPad Air", "E8:68:E7:77:88:99", "Allowed", "192.168.43.104", "310.0 MB", "Offline"),
        HotspotDevice("dev_5", "Dell XPS 15", "34:E6:D7:AA:BB:CC", "Connected", "192.168.43.105", "850.7 MB", "1h 30m"),
        HotspotDevice("dev_6", "Google Pixel 8", "18:C0:4D:DD:EE:FF", "Connected", "192.168.43.106", "120.3 MB", "3h 10m"),
        HotspotDevice("dev_7", "Sony Bravia Smart TV", "00:1A:7D:12:34:56", "Allowed", "192.168.43.107", "2.4 GB", "Offline"),
        HotspotDevice("dev_8", "Nintendo Switch", "98:B6:E9:88:77:66", "Denied", "—", "0 MB", "Blocked"),
        HotspotDevice("dev_9", "PlayStation 5", "70:66:55:44:33:22", "Allowed", "192.168.43.109", "5.1 GB", "Offline"),
        HotspotDevice("dev_10", "ThinkPad X1 Carbon", "00:21:CC:11:22:33", "Connected", "192.168.43.110", "640.2 MB", "5h 50m"),
        HotspotDevice("dev_11", "Asus ROG Phone 7", "24:4B:03:AA:11:22", "Connected", "192.168.43.111", "95.0 MB", "1h 05m"),
        HotspotDevice("dev_12", "Surface Laptop Studio", "58:82:A8:33:44:55", "Allowed", "192.168.43.112", "180.5 MB", "Offline"),
        HotspotDevice("dev_13", "Kindle Paperwhite", "F0:D2:F1:66:77:88", "Allowed", "192.168.43.113", "12.1 MB", "Offline"),
        HotspotDevice("dev_14", "Galaxy Watch 6", "8C:85:90:99:AA:BB", "Connected", "192.168.43.114", "8.4 MB", "6h 12m"),
        HotspotDevice("dev_15", "Raspberry Pi 5", "D8:3A:DD:CC:BB:AA", "Connected", "192.168.43.115", "340.9 MB", "12h 40m"),
        HotspotDevice("dev_16", "LG Smart Fridge", "2C:AA:8E:12:34:78", "Denied", "—", "0 MB", "Blocked"),
        HotspotDevice("dev_17", "Sonos Era 300", "48:A6:B8:99:88:77", "Allowed", "192.168.43.117", "620.0 MB", "Offline"),
        HotspotDevice("dev_18", "Tesla Model 3", "A0:6C:65:44:33:11", "Connected", "192.168.43.118", "1.8 GB", "20m"),
        HotspotDevice("dev_19", "Apple TV 4K", "38:F9:D3:55:66:77", "Allowed", "192.168.43.119", "3.5 GB", "Offline"),
        HotspotDevice("dev_20", "Meta Quest 3", "2C:26:17:88:99:00", "Denied", "—", "0 MB", "Blocked")
    )

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

            val portraitCardsContainer = findViewById<ViewGroup>(R.id.hotspot_cards_container)
            val landscapeLeftContainer = findViewById<ViewGroup>(R.id.hotspot_cards_left_container)

            if (portraitCardsContainer != null) {
                // Portrait mode: Single column layout with 20 expandable cards
                portraitCardsContainer.removeAllViews()
                val inflater = LayoutInflater.from(activity)

                devices.forEach { device ->
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

                    tvName.text = device.name
                    tvMac.text = device.mac
                    tvStatus.text = getLocalizedStatus(device.status)

                    tvIp.text = getString(R.string.hotspot_ip_address, device.ip)
                    tvData.text = getString(R.string.hotspot_data_used, device.dataUsed)
                    tvTime.text = getString(R.string.hotspot_connected_time, device.duration)

                    // 2 Radio Buttons: Allowed and Denied
                    if (device.status == "Denied") {
                        radioDenied.isChecked = true
                    } else {
                        radioAllowed.isChecked = true
                    }

                    radioGroup.setOnCheckedChangeListener { _, checkedId ->
                        if (checkedId == R.id.radio_status_denied) {
                            device.status = "Denied"
                        } else {
                            device.status = "Allowed"
                        }
                        tvStatus.text = getLocalizedStatus(device.status)
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

                val cardHeaderViews = mutableListOf<Triple<View, ImageView, TextView>>()

                fun selectDevice(targetDevice: HotspotDevice, selectedArrow: ImageView) {
                    selectedDeviceId = targetDevice.id
                    cardHeaderViews.forEach { (_, arrow, _) -> arrow.rotation = 0f }
                    selectedArrow.rotation = 180f

                    detailCardTitle?.text = targetDevice.name
                    detailCardMac?.text = targetDevice.mac
                    detailIp?.text = getString(R.string.hotspot_ip_address, targetDevice.ip)
                    detailData?.text = getString(R.string.hotspot_data_used, targetDevice.dataUsed)
                    detailTime?.text = getString(R.string.hotspot_connected_time, targetDevice.duration)

                    detailRadioGroup?.setOnCheckedChangeListener(null)
                    if (targetDevice.status == "Denied") {
                        detailRadioDenied?.isChecked = true
                    } else {
                        detailRadioAllowed?.isChecked = true
                    }

                    detailRadioGroup?.setOnCheckedChangeListener { _, checkedId ->
                        if (checkedId == R.id.hotspot_detail_radio_denied) {
                            targetDevice.status = "Denied"
                        } else {
                            targetDevice.status = "Allowed"
                        }
                        val matchingTuple = cardHeaderViews.firstOrNull { it.first.tag == targetDevice.id }
                        matchingTuple?.third?.text = getLocalizedStatus(targetDevice.status)
                    }
                }

                devices.forEach { device ->
                    val cardView = inflater.inflate(R.layout.card_hotspot_device, landscapeLeftContainer, false)
                    applyCardTheme(cardView)
                    val headerView = cardView.findViewById<View>(R.id.header_hotspot_device)
                    val dropdownView = cardView.findViewById<View>(R.id.dropdown_hotspot_device)
                    val tvName = cardView.findViewById<TextView>(R.id.tv_device_name)
                    val tvMac = cardView.findViewById<TextView>(R.id.tv_device_mac)
                    val tvStatus = cardView.findViewById<TextView>(R.id.tv_device_status)
                    val ivArrow = cardView.findViewById<ImageView>(R.id.iv_device_arrow)

                    dropdownView.visibility = View.GONE

                    tvName.text = device.name
                    tvMac.text = device.mac
                    tvStatus.text = getLocalizedStatus(device.status)

                    headerView.tag = device.id
                    cardHeaderViews.add(Triple(headerView, ivArrow, tvStatus))

                    headerView.setOnClickListener {
                        selectDevice(device, ivArrow)
                    }

                    landscapeLeftContainer.addView(cardView)
                }

                // Initial selection
                val initialDev = devices.firstOrNull { it.id == selectedDeviceId } ?: devices.first()
                val initialTuple = cardHeaderViews.firstOrNull { it.first.tag == initialDev.id } ?: cardHeaderViews.first()
                selectDevice(initialDev, initialTuple.second)
            }

            val savedScrollY = savedInstanceState?.getInt(KEY_HOTSPOT_SCROLL_Y, -1) ?: -1
            if (savedScrollY >= 0) {
                val scrollView = findViewById<ScrollView>(R.id.hotspot_scroll_view)
                    ?: findViewById<ScrollView>(R.id.hotspot_scroll_left)
                scrollView?.post { scrollView.scrollTo(0, savedScrollY) }
            }
            val savedRightScrollY = savedInstanceState?.getInt(KEY_HOTSPOT_SCROLL_RIGHT_Y, -1) ?: -1
            if (savedRightScrollY >= 0) {
                val rightScrollView = findViewById<ScrollView>(R.id.hotspot_scroll_right)
                rightScrollView?.post { rightScrollView.scrollTo(0, savedRightScrollY) }
            }

            NotchHeaderHelper.updateHotspotHeaderLayout(activity)
        }
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

    private fun getLocalizedStatus(status: String): String {
        val act = activityRef ?: return status
        return when (status) {
            "Denied" -> act.getString(R.string.status_denied)
            "Allowed" -> act.getString(R.string.status_allowed)
            else -> act.getString(R.string.status_connected)
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
