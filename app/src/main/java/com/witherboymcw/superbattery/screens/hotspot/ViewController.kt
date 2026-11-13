package com.witherboymcw.superbattery.screens.hotspot

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.TextView
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.witherboymcw.superbattery.MainActivity
import com.witherboymcw.superbattery.R
import com.witherboymcw.superbattery.SwipeBackFrameLayout
import com.witherboymcw.superbattery.awareness.NotchAwareness
import com.witherboymcw.superbattery.templates.DrawerScreen
import kotlinx.coroutines.launch
import java.util.Locale

internal class HotspotViewController : DrawerScreen(
    containerId = R.id.hotspot_container,
    slideDirection = SlideDirection.FROM_LEFT
) {
    private var selectedDeviceMac: String? = null
    private var activeDetailTextWatcher: TextWatcher? = null

    override fun onPostOpen(activity: MainActivity) {
        NotchAwareness.updateHotspotHeaderLayout(activity)
    }

    fun bind(activity: MainActivity, savedInstanceState: Bundle? = null) {
        this.activityRef = activity
        activity.onBackPressedDispatcher.addCallback(activity, backCallback)

        activity.applyHotspotCardTheme()

        with(activity) {
            val swipeContainer = findViewById<android.view.ViewGroup>(R.id.hotspot_container) as? SwipeBackFrameLayout ?: return
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

            val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val recyclerView = findViewById<RecyclerView>(R.id.hotspot_recycler_view)

            val adapter = HotspotClientAdapter(
                isLandscape = isLandscape,
                onClientClick = { client ->
                    selectedDeviceMac = client.mac
                    if (isLandscape) {
                        bindLandscapeDetailCard(activity, client)
                    }
                },
                onStatusChanged = { client, isDenied ->
                    HotspotManager.updateClientStatus(activity, client.mac, isDenied)
                },
                onAliasChanged = { client, newAlias ->
                    HotspotManager.updateClientAlias(activity, client.mac, newAlias)
                }
            )

            recyclerView?.layoutManager = LinearLayoutManager(activity)
            recyclerView?.adapter = adapter

            HotspotManager.loadPersistedClients(activity)
            activity.lifecycleScope.launch {
                HotspotManager.clientsState.collect { clients ->
                    val sortedList = clients.sortedWith(
                        compareByDescending<HotspotClient> { it.isCurrentlyOnline }
                            .thenByDescending { it.lastConnectedTimeMs }
                    )
                    adapter.submitList(sortedList)

                    if (isLandscape && sortedList.isNotEmpty()) {
                        val currentSelected = sortedList.firstOrNull { it.mac.equals(selectedDeviceMac, ignoreCase = true) }
                            ?: sortedList.first()
                        selectedDeviceMac = currentSelected.mac
                        bindLandscapeDetailCard(activity, currentSelected)
                    }
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

    private fun bindLandscapeDetailCard(activity: MainActivity, client: HotspotClient) {
        with(activity) {
            val detailCardTitle = findViewById<TextView>(R.id.hotspot_detail_title)
            val detailCardMac = findViewById<TextView>(R.id.hotspot_detail_mac)
            val detailRadioAllowed = findViewById<RadioButton>(R.id.hotspot_detail_radio_allowed)
            val detailRadioDenied = findViewById<RadioButton>(R.id.hotspot_detail_radio_denied)

            val detailIp = findViewById<TextView>(R.id.hotspot_detail_ip)
            val detailData = findViewById<TextView>(R.id.hotspot_detail_data)
            val detailTime = findViewById<TextView>(R.id.hotspot_detail_time)

            val detailEtAlias = findViewById<EditText>(R.id.hotspot_detail_et_alias)
            val detailBtnRemoveAlias = findViewById<View>(R.id.hotspot_detail_btn_remove_alias)

            detailCardTitle?.text = client.deviceName
            detailCardMac?.text = client.mac
            detailIp?.text = getString(R.string.hotspot_ip_address, client.ipAddress)
            detailData?.text = getString(R.string.hotspot_data_used, formatByteSize(client.totalDataBytes))
            detailTime?.text = getString(R.string.hotspot_connected_time, formatConnectedTime(client.lastConnectedTimeMs, client.isCurrentlyOnline))

            if (client.isDenied) {
                detailRadioDenied?.isChecked = true
                detailRadioAllowed?.isChecked = false
            } else {
                detailRadioAllowed?.isChecked = true
                detailRadioDenied?.isChecked = false
            }

            detailRadioAllowed?.setOnClickListener {
                if (client.isDenied) {
                    HotspotManager.updateClientStatus(activity, client.mac, false)
                }
            }

            detailRadioDenied?.setOnClickListener {
                if (!client.isDenied) {
                    HotspotManager.updateClientStatus(activity, client.mac, true)
                }
            }

            // Remove previous TextWatcher before attaching a new one
            activeDetailTextWatcher?.let { detailEtAlias?.removeTextChangedListener(it) }

            detailEtAlias?.setText(client.customAlias ?: client.deviceName)
            detailBtnRemoveAlias?.visibility = if (client.customAlias != null) View.VISIBLE else View.GONE

            var isUpdatingText = false
            val newWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (isUpdatingText) return
                    val inputStr = s?.toString()?.trim() ?: ""
                    val newAlias = if (inputStr.isNotBlank() && inputStr != client.deviceName) inputStr else null
                    if (client.customAlias != newAlias) {
                        HotspotManager.updateClientAlias(activity, client.mac, newAlias)
                        detailBtnRemoveAlias?.visibility = if (newAlias != null) View.VISIBLE else View.GONE
                    }
                }
                override fun afterTextChanged(s: Editable?) {}
            }
            activeDetailTextWatcher = newWatcher
            detailEtAlias?.addTextChangedListener(newWatcher)

            detailBtnRemoveAlias?.setOnClickListener {
                isUpdatingText = true
                detailEtAlias?.setText(client.deviceName)
                isUpdatingText = false
                detailBtnRemoveAlias?.visibility = View.GONE
                HotspotManager.updateClientAlias(activity, client.mac, null)
            }
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

    override fun unbind() {
        super.unbind()
    }

    companion object {
        const val KEY_HOTSPOT_SCROLL_Y = "key_hotspot_scroll_y"
        const val KEY_HOTSPOT_SCROLL_RIGHT_Y = "key_hotspot_scroll_right_y"
    }
}
