package com.witherboymcw.superbattery.screens.hotspot

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.witherboymcw.superbattery.MainActivity
import com.witherboymcw.superbattery.OledThemeAllInOne
import com.witherboymcw.superbattery.R
import com.witherboymcw.superbattery.SwipeBackFrameLayout
import com.witherboymcw.superbattery.awareness.NotchAwareness
import java.util.Locale

internal class HotspotViewController {

    private var activityRef: MainActivity? = null
    private var selectedDeviceMac: String? = null
    var isOpen: Boolean = false

    val backCallback = object : OnBackPressedCallback(false) {
        private var initialWidth = 0f

        override fun handleOnBackStarted(backEvent: BackEventCompat) {
            val container = activityRef?.findViewById<View>(R.id.hotspot_container) ?: return
            initialWidth = container.width.toFloat().takeIf { it > 0f } ?: 1080f
        }

        override fun handleOnBackProgressed(backEvent: BackEventCompat) {
            val container = activityRef?.findViewById<View>(R.id.hotspot_container) ?: return
            val progress = backEvent.progress
            container.translationX = -initialWidth * progress
        }

        override fun handleOnBackCancelled() {
            val container = activityRef?.findViewById<View>(R.id.hotspot_container) ?: return
            container.animate()
                .translationX(0f)
                .setDuration(150L)
                .setInterpolator(FastOutSlowInInterpolator())
                .start()
        }

        override fun handleOnBackPressed() {
            val act = activityRef ?: return
            closeScreen(act)
        }
    }

    fun openScreen(activity: MainActivity) {
        val container = activity.findViewById<View>(R.id.hotspot_container) ?: return
        isOpen = true
        container.visibility = View.VISIBLE
        val width = container.width.toFloat().takeIf { it > 0 } ?: 1080f
        container.translationX = -width
        container.animate()
            .translationX(0f)
            .setDuration(280L)
            .setInterpolator(FastOutSlowInInterpolator())
            .withLayer()
            .start()
        backCallback.isEnabled = true
        NotchAwareness.updateHotspotHeaderLayout(activity)
    }

    fun closeScreen(activity: MainActivity, animate: Boolean = true) {
        val container = activity.findViewById<View>(R.id.hotspot_container) ?: return
        if (container.visibility != View.VISIBLE) return
        isOpen = false
        backCallback.isEnabled = false
        if (animate) {
            val width = container.width.toFloat().takeIf { it > 0 } ?: 1080f
            container.animate()
                .translationX(-width)
                .setDuration(280L)
                .setInterpolator(FastOutSlowInInterpolator())
                .withLayer()
                .withEndAction {
                    container.visibility = View.GONE
                }
                .start()
        } else {
            container.animate().cancel()
            container.visibility = View.GONE
            container.translationX = 0f
        }
    }

    fun restoreOpenState(activity: MainActivity) {
        val container = activity.findViewById<View>(R.id.hotspot_container) ?: return
        container.visibility = View.VISIBLE
        container.translationX = 0f
        backCallback.isEnabled = true
        isOpen = true
    }

    private fun getTargetLayoutRes(): Int = R.layout.layout_hotspot

    fun bind(activity: MainActivity, savedInstanceState: Bundle? = null) {
        this.activityRef = activity
        activity.onBackPressedDispatcher.addCallback(activity, backCallback)

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
                closeScreen(this)
            }

            // Swipe left callback to close hotspot container
            swipeContainer.swipeListener = object : SwipeBackFrameLayout.SwipeListener {
                override fun onSwipeProgress(translationX: Float) {
                    swipeContainer.translationX = translationX
                }

                override fun onSwipeEnd(shouldClose: Boolean) {
                    if (shouldClose) {
                        closeScreen(this@with)
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

                    val radioAllowed = cardView.findViewById<RadioButton>(R.id.radio_status_allowed)
                    val radioDenied = cardView.findViewById<RadioButton>(R.id.radio_status_denied)

                    val tvIp = cardView.findViewById<TextView>(R.id.tv_device_ip)
                    val tvData = cardView.findViewById<TextView>(R.id.tv_device_data)
                    val tvTime = cardView.findViewById<TextView>(R.id.tv_device_time)

                    val etAlias = cardView.findViewById<EditText>(R.id.et_device_alias)
                    val btnRemoveAlias = cardView.findViewById<View>(R.id.btn_remove_alias)

                    // In Portrait mode: collapsed 1 shows Alias (default = original name); expanded 1 shows Original Name
                    val isInitiallyExpanded = dropdownView.visibility == View.VISIBLE
                    tvName.text = if (isInitiallyExpanded) client.deviceName else client.displayName
                    tvMac.text = client.mac
                    tvStatus.text = getLocalizedStatus(activity, client)

                    tvIp.text = getString(R.string.hotspot_ip_address, client.ipAddress)
                    tvData.text = getString(R.string.hotspot_data_used, formatByteSize(client.totalDataBytes))
                    tvTime.text = getString(R.string.hotspot_connected_time, formatConnectedTime(client.lastConnectedTimeMs, client.isCurrentlyOnline))

                    // 2 Radio Buttons: Allowed and Denied
                    if (client.isDenied) {
                        radioDenied.isChecked = true
                        radioAllowed.isChecked = false
                    } else {
                        radioAllowed.isChecked = true
                        radioDenied.isChecked = false
                    }

                    radioAllowed.setOnClickListener {
                        if (client.isDenied) {
                            client.isDenied = false
                            radioAllowed.isChecked = true
                            radioDenied.isChecked = false
                            HotspotManager.updateClientStatus(activity, client.mac, false)
                            tvStatus.text = getLocalizedStatus(activity, client)
                        }
                    }

                    radioDenied.setOnClickListener {
                        if (!client.isDenied) {
                            client.isDenied = true
                            radioDenied.isChecked = true
                            radioAllowed.isChecked = false
                            HotspotManager.updateClientStatus(activity, client.mac, true)
                            tvStatus.text = getLocalizedStatus(activity, client)
                        }
                    }

                    // Default alias is original name. Show remove alias button if customized.
                    etAlias.setText(client.customAlias ?: client.deviceName)
                    btnRemoveAlias.visibility = if (client.customAlias != null) View.VISIBLE else View.GONE

                    var isUpdatingText = false
                    etAlias.addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                            if (isUpdatingText) return
                            val inputStr = s?.toString()?.trim() ?: ""
                            val newAlias = if (inputStr.isNotBlank() && inputStr != client.deviceName) inputStr else null
                            if (client.customAlias != newAlias) {
                                client.customAlias = newAlias
                                HotspotManager.updateClientAlias(activity, client.mac, newAlias)
                                btnRemoveAlias.visibility = if (newAlias != null) View.VISIBLE else View.GONE
                                if (dropdownView.visibility == View.GONE) {
                                    tvName.text = client.displayName
                                }
                            }
                        }
                        override fun afterTextChanged(s: Editable?) {}
                    })

                    btnRemoveAlias.setOnClickListener {
                        client.customAlias = null
                        HotspotManager.updateClientAlias(activity, client.mac, null)
                        isUpdatingText = true
                        etAlias.setText(client.deviceName)
                        isUpdatingText = false
                        btnRemoveAlias.visibility = View.GONE
                        if (dropdownView.visibility == View.GONE) {
                            tvName.text = client.displayName
                        }
                    }

                    headerView.setOnClickListener {
                        val isExpanded = dropdownView.visibility == View.VISIBLE
                        if (isExpanded) {
                            dropdownView.visibility = View.GONE
                            ivArrow.animate().rotation(0f).setDuration(200L).start()
                            tvName.text = client.displayName // Collapsed: show Alias / displayName
                        } else {
                            dropdownView.visibility = View.VISIBLE
                            ivArrow.animate().rotation(180f).setDuration(200L).start()
                            tvName.text = client.deviceName // Expanded: original name replaces alias in 1
                        }
                    }

                    portraitCardsContainer.addView(cardView)
                }
            } else if (landscapeLeftContainer != null) {
                // Landscape mode: Dual-column Master-Detail layout
                landscapeLeftContainer.removeAllViews()
                val inflater = LayoutInflater.from(activity)

                val detailCardTitle = findViewById<TextView>(R.id.hotspot_detail_title)
                val detailCardMac = findViewById<TextView>(R.id.hotspot_detail_mac)
                val detailRadioAllowed = findViewById<RadioButton>(R.id.hotspot_detail_radio_allowed)
                val detailRadioDenied = findViewById<RadioButton>(R.id.hotspot_detail_radio_denied)

                val detailIp = findViewById<TextView>(R.id.hotspot_detail_ip)
                val detailData = findViewById<TextView>(R.id.hotspot_detail_data)
                val detailTime = findViewById<TextView>(R.id.hotspot_detail_time)

                val detailEtAlias = findViewById<EditText>(R.id.hotspot_detail_et_alias)
                val detailBtnRemoveAlias = findViewById<View>(R.id.hotspot_detail_btn_remove_alias)

                // Store card tuple: headerView, tvName, tvStatus
                val cardHeaderViews = mutableListOf<Triple<View, TextView, TextView>>()
                var activeTextWatcher: TextWatcher? = null

                fun selectDevice(targetClient: HotspotClient, selectedArrow: ImageView) {
                    selectedDeviceMac = targetClient.mac

                    // Item 1 of the detail card shows Original Device Name
                    detailCardTitle?.text = targetClient.deviceName
                    detailCardMac?.text = targetClient.mac
                    detailIp?.text = getString(R.string.hotspot_ip_address, targetClient.ipAddress)
                    detailData?.text = getString(R.string.hotspot_data_used, formatByteSize(targetClient.totalDataBytes))
                    detailTime?.text = getString(R.string.hotspot_connected_time, formatConnectedTime(targetClient.lastConnectedTimeMs, targetClient.isCurrentlyOnline))

                    if (targetClient.isDenied) {
                        detailRadioDenied?.isChecked = true
                        detailRadioAllowed?.isChecked = false
                    } else {
                        detailRadioAllowed?.isChecked = true
                        detailRadioDenied?.isChecked = false
                    }

                    detailRadioAllowed?.setOnClickListener {
                        if (targetClient.isDenied) {
                            targetClient.isDenied = false
                            detailRadioAllowed?.isChecked = true
                            detailRadioDenied?.isChecked = false
                            HotspotManager.updateClientStatus(activity, targetClient.mac, false)
                            val matchingTuple = cardHeaderViews.firstOrNull { it.first.tag == targetClient.mac }
                            matchingTuple?.third?.text = getLocalizedStatus(activity, targetClient)
                        }
                    }

                    detailRadioDenied?.setOnClickListener {
                        if (!targetClient.isDenied) {
                            targetClient.isDenied = true
                            detailRadioDenied?.isChecked = true
                            detailRadioAllowed?.isChecked = false
                            HotspotManager.updateClientStatus(activity, targetClient.mac, true)
                            val matchingTuple = cardHeaderViews.firstOrNull { it.first.tag == targetClient.mac }
                            matchingTuple?.third?.text = getLocalizedStatus(activity, targetClient)
                        }
                    }

                    // Alias Textbox and Remove Alias button binding
                    activeTextWatcher?.let { detailEtAlias?.removeTextChangedListener(it) }
                    detailEtAlias?.setText(targetClient.customAlias ?: targetClient.deviceName)
                    detailBtnRemoveAlias?.visibility = if (targetClient.customAlias != null) View.VISIBLE else View.GONE

                    var isUpdatingDetailText = false
                    val newWatcher = object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                            if (isUpdatingDetailText) return
                            val inputStr = s?.toString()?.trim() ?: ""
                            val newAlias = if (inputStr.isNotBlank() && inputStr != targetClient.deviceName) inputStr else null
                            if (targetClient.customAlias != newAlias) {
                                targetClient.customAlias = newAlias
                                HotspotManager.updateClientAlias(activity, targetClient.mac, newAlias)
                                detailBtnRemoveAlias?.visibility = if (newAlias != null) View.VISIBLE else View.GONE
                                // Item 1 in left client cards updates immediately on every character typed!
                                val matchingTuple = cardHeaderViews.firstOrNull { it.first.tag == targetClient.mac }
                                matchingTuple?.second?.text = targetClient.displayName
                            }
                        }
                        override fun afterTextChanged(s: Editable?) {}
                    }
                    activeTextWatcher = newWatcher
                    detailEtAlias?.addTextChangedListener(newWatcher)

                    detailBtnRemoveAlias?.setOnClickListener {
                        targetClient.customAlias = null
                        HotspotManager.updateClientAlias(activity, targetClient.mac, null)
                        isUpdatingDetailText = true
                        detailEtAlias?.setText(targetClient.deviceName)
                        isUpdatingDetailText = false
                        detailBtnRemoveAlias?.visibility = View.GONE
                        val matchingTuple = cardHeaderViews.firstOrNull { it.first.tag == targetClient.mac }
                        matchingTuple?.second?.text = targetClient.displayName
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

                    // Item 1 in landscape left client cards NEVER changes when selecting
                    tvName.text = client.displayName
                    tvMac.text = client.mac
                    tvStatus.text = getLocalizedStatus(activity, client)

                    headerView.tag = client.mac
                    cardHeaderViews.add(Triple(headerView, tvName, tvStatus))

                    headerView.setOnClickListener {
                        selectDevice(client, ivArrow)
                    }

                    landscapeLeftContainer.addView(cardView)
                }

                // Initial selection
                if (sortedClients.isNotEmpty()) {
                    val initialClient = sortedClients.firstOrNull { it.mac == selectedDeviceMac } ?: sortedClients.first()
                    val initialView = landscapeLeftContainer.findViewWithTag<View>(initialClient.mac)
                    val initialArrow = initialView?.findViewById<ImageView>(R.id.iv_device_arrow) ?: ImageView(activity)
                    selectDevice(initialClient, initialArrow)
                }
            }

            val savedScrollY = savedInstanceState?.getInt(KEY_HOTSPOT_SCROLL_Y, -1) ?: -1
            if (savedScrollY >= 0) {
                val scrollView = findViewById<ScrollView>(R.id.hotspot_scroll_view)
                    ?: findViewById<ScrollView>(R.id.hotspot_scroll_left)
                scrollView?.post { scrollView.scrollTo(0, savedScrollY) }
            }

            NotchAwareness.updateHotspotHeaderLayout(activity)
        }
    }

    private fun applyCardTheme(cardView: View) {
        val act = activityRef ?: return
        OledThemeAllInOne.applyCardTheme(cardView, act)
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
