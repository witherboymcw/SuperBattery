package com.witherboymcw.superbattery.screens.hotspot

import android.content.res.Configuration
import android.os.Bundle
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.RadioButton
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.witherboymcw.superbattery.MainActivity
import com.witherboymcw.superbattery.R
import com.witherboymcw.superbattery.SwipeBackFrameLayout
import com.witherboymcw.superbattery.awareness.FoldableAwareness
import com.witherboymcw.superbattery.awareness.NotchAwareFrameLayout
import com.witherboymcw.superbattery.awareness.NotchAwareness
import com.witherboymcw.superbattery.ui.DrawerScreen
import com.witherboymcw.superbattery.ui.Motion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class HotspotViewController : DrawerScreen(
    containerId = R.id.hotspot_container,
    slideDirection = SlideDirection.FROM_LEFT
) {
    private var selectedDeviceMac: String? = null
    private var activeDetailTextWatcher: TextWatcher? = null
    private var clientsJob: kotlinx.coroutines.Job? = null

    override fun onPostOpen(activity: ComponentActivity) {
        (activity as? MainActivity)?.let {
            NotchAwareness.updateHotspotHeaderLayout(it)
            it.hideSystemBars()
        }
    }

    override fun onPostClose(activity: ComponentActivity) {
        (activity as? MainActivity)?.hideSystemBars()
    }

    fun bind(activity: ComponentActivity, savedInstanceState: Bundle? = null) {
        this.activityRef = activity
        activity.onBackPressedDispatcher.addCallback(activity, backCallback)

        val hotspotContainer = activity.findViewById<View>(R.id.hotspot_container)
        if (hotspotContainer != null) {
            com.witherboymcw.superbattery.OledThemeAllInOne.applyThemeToAllCards(hotspotContainer, activity)
        }

        val swipeContainer = hotspotContainer as? SwipeBackFrameLayout ?: return
        swipeContainer.isClickable = true
        swipeContainer.isFocusable = true
        swipeContainer.swipeDirection = SwipeBackFrameLayout.SWIPE_DIRECTION_LEFT

        // Back button tap
        activity.findViewById<View>(R.id.hotspot_back_button)?.setOnClickListener {
            closeScreen(activity)
        }

        // Swipe left callback to close hotspot container
        swipeContainer.swipeListener = object : SwipeBackFrameLayout.SwipeListener {
            override fun onSwipeProgress(translationX: Float) {
                swipeContainer.translationX = translationX
            }

            override fun onSwipeEnd(shouldClose: Boolean) {
                if (shouldClose) {
                    closeScreen(activity)
                } else {
                    swipeContainer.animate()?.cancel()
                    val widthVal = swipeContainer.width.toFloat().takeIf { it > 0 } ?: Motion.FALLBACK_WIDTH
                    val remainingDistance = Math.abs(swipeContainer.translationX)
                    val ratio = (remainingDistance / widthVal).coerceIn(0f, 1f)
                    val duration = (ratio * 250L).toLong().coerceIn(100L, 250L)

                    swipeContainer.animate()
                        .translationX(0f)
                        .setDuration(duration)
                        .setInterpolator(Motion.interpolator)
                        .withLayer()
                        .start()
                }
            }
        }

        val isMiniSquare = FoldableAwareness.isMiniSquareMode(activity)
        val isLandscape = !isMiniSquare && activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val recyclerView = activity.findViewById<RecyclerView>(R.id.hotspot_recycler_view)

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
        recyclerView?.itemAnimator = null
        recyclerView?.adapter = adapter
        recyclerView?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                (hotspotContainer as? NotchAwareFrameLayout)?.updateChildOffsets()
            }
        })

        clientsJob?.cancel()
        clientsJob = activity.lifecycleScope.launch {
            val appContext = activity.applicationContext
            withContext(Dispatchers.IO) {
                HotspotManager.loadPersistedClients(appContext)
            }
            HotspotManager.clientsState.collect { clients ->
                val sortedList = clients.sortedWith(
                    compareByDescending<HotspotClient> { it.isCurrentlyOnline }
                        .thenByDescending { it.lastConnectedTimeMs }
                )
                adapter.submitList(sortedList) {
                    recyclerView?.post {
                        (hotspotContainer as? NotchAwareFrameLayout)?.updateChildOffsets()
                    }
                }

                if (isLandscape && sortedList.isNotEmpty()) {
                    val currentSelected = sortedList.firstOrNull { it.mac.equals(selectedDeviceMac, ignoreCase = true) }
                        ?: sortedList.first()
                    selectedDeviceMac = currentSelected.mac
                    bindLandscapeDetailCard(activity, currentSelected)
                }
            }
        }

        val savedPosition = savedInstanceState?.getInt(KEY_HOTSPOT_SCROLL_POSITION, -1) ?: -1
        val savedOffset = savedInstanceState?.getInt(KEY_HOTSPOT_SCROLL_OFFSET, 0) ?: 0
        if (savedPosition >= 0) {
            recyclerView?.post {
                (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(savedPosition, savedOffset)
            }
        }

        (activity as? MainActivity)?.let { NotchAwareness.updateHotspotHeaderLayout(it) }
    }

    private fun bindLandscapeDetailCard(activity: ComponentActivity, client: HotspotClient) {
        val detailCardTitle = activity.findViewById<TextView>(R.id.hotspot_detail_title)
        val detailCardMac = activity.findViewById<TextView>(R.id.hotspot_detail_mac)
        val detailRadioAllowed = activity.findViewById<RadioButton>(R.id.hotspot_detail_radio_allowed)
        val detailRadioDenied = activity.findViewById<RadioButton>(R.id.hotspot_detail_radio_denied)

        val detailIp = activity.findViewById<TextView>(R.id.hotspot_detail_ip)
        val detailData = activity.findViewById<TextView>(R.id.hotspot_detail_data)
        val detailTime = activity.findViewById<TextView>(R.id.hotspot_detail_time)

        val detailEtAlias = activity.findViewById<EditText>(R.id.hotspot_detail_et_alias)
        val detailBtnRemoveAlias = activity.findViewById<View>(R.id.hotspot_detail_btn_remove_alias)

        detailCardTitle?.text = client.deviceName
        detailCardMac?.text = client.mac
        detailIp?.text = activity.getString(R.string.hotspot_ip_address, client.ipAddress)
        detailData?.text = activity.getString(R.string.hotspot_data_used, com.witherboymcw.superbattery.utils.Formatters.formatByteSize(client.totalDataBytes))
        detailTime?.text = activity.getString(R.string.hotspot_connected_time, com.witherboymcw.superbattery.utils.Formatters.formatConnectedTime(activity, client.lastConnectedTimeMs, client.isCurrentlyOnline))

        if (client.isDenied) {
            detailRadioDenied?.isChecked = true
            detailRadioAllowed?.isChecked = false
        } else {
            detailRadioAllowed?.isChecked = true
            detailRadioDenied?.isChecked = false
        }

        val container = activity.findViewById<View>(R.id.hotspot_container) as? NotchAwareFrameLayout

        detailRadioAllowed?.setOnClickListener {
            if (client.isDenied) {
                detailRadioAllowed.isChecked = true
                detailRadioDenied?.isChecked = false
                HotspotManager.updateClientStatus(activity, client.mac, false)
                container?.updateChildOffsets()
            } else {
                detailRadioAllowed.isChecked = true
                detailRadioDenied?.isChecked = false
            }
        }

        detailRadioDenied?.setOnClickListener {
            if (!client.isDenied) {
                detailRadioDenied.isChecked = true
                detailRadioAllowed?.isChecked = false
                HotspotManager.updateClientStatus(activity, client.mac, true)
                container?.updateChildOffsets()
            } else {
                detailRadioDenied.isChecked = true
                detailRadioAllowed?.isChecked = false
            }
        }

        // Remove previous TextWatcher before attaching a new one
        activeDetailTextWatcher?.let { detailEtAlias?.removeTextChangedListener(it) }

        if (detailEtAlias != null && detailBtnRemoveAlias != null) {
            activeDetailTextWatcher = com.witherboymcw.superbattery.utils.Formatters.bindAliasTextWatcher(
                etAlias = detailEtAlias,
                btnRemoveAlias = detailBtnRemoveAlias,
                deviceName = client.deviceName,
                currentAlias = client.customAlias,
                onAliasChanged = { newAlias -> HotspotManager.updateClientAlias(activity, client.mac, newAlias) }
            )
        }
    }

    override fun unbind() {
        clientsJob?.cancel()
        clientsJob = null
        super.unbind()
    }

    companion object {
        const val KEY_HOTSPOT_SCROLL_POSITION = "key_hotspot_scroll_position"
        const val KEY_HOTSPOT_SCROLL_OFFSET = "key_hotspot_scroll_offset"
        const val KEY_HOTSPOT_SCROLL_Y = "key_hotspot_scroll_y"
    }
}
