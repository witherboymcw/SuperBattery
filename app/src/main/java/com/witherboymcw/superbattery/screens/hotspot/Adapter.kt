package com.witherboymcw.superbattery.screens.hotspot

import android.content.Context
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.witherboymcw.superbattery.MainActivity
import com.witherboymcw.superbattery.OledThemeAllInOne
import com.witherboymcw.superbattery.R
import com.witherboymcw.superbattery.awareness.NotchAwareness

class HotspotClientDiffCallback : DiffUtil.ItemCallback<HotspotClient>() {
    override fun areItemsTheSame(oldItem: HotspotClient, newItem: HotspotClient): Boolean {
        return oldItem.mac.equals(newItem.mac, ignoreCase = true)
    }

    override fun areContentsTheSame(oldItem: HotspotClient, newItem: HotspotClient): Boolean {
        return oldItem == newItem
    }
}

/**
 * Shared RecyclerView.Adapter backed by DiffUtil for rendering hotspot client devices
 * across both Portrait and Landscape master-detail views.
 *
 * In Portrait / Single-Column mode, a header item is prepended at index 0 to contain
 * [R.id.tv_hotspot_title] so it scrolls away naturally with the list.
 */
class HotspotClientAdapter(
    private val isLandscape: Boolean,
    private val onClientClick: (client: HotspotClient) -> Unit,
    private val onStatusChanged: (client: HotspotClient, isDenied: Boolean) -> Unit,
    private val onAliasChanged: (client: HotspotClient, newAlias: String?) -> Unit
) : ListAdapter<HotspotClient, RecyclerView.ViewHolder>(HotspotClientDiffCallback()) {

    var selectedMac: String? = null
    private val expandedMacs = mutableSetOf<String>()

    val hasHeader: Boolean
        get() = !isLandscape

    companion object {
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_CLIENT = 1
    }

    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tv_hotspot_title)
    }

    class ClientViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val headerView: View = itemView.findViewById(R.id.header_hotspot_device)
        val dropdownView: View = itemView.findViewById(R.id.dropdown_hotspot_device)
        val tvName: TextView = itemView.findViewById(R.id.tv_device_name)
        val tvMac: TextView = itemView.findViewById(R.id.tv_device_mac)
        val tvStatus: TextView = itemView.findViewById(R.id.tv_device_status)
        val ivArrow: ImageView = itemView.findViewById(R.id.iv_device_arrow)

        val radioAllowed: RadioButton = itemView.findViewById(R.id.radio_status_allowed)
        val radioDenied: RadioButton = itemView.findViewById(R.id.radio_status_denied)

        val tvIp: TextView = itemView.findViewById(R.id.tv_device_ip)
        val tvData: TextView = itemView.findViewById(R.id.tv_device_data)
        val tvTime: TextView = itemView.findViewById(R.id.tv_device_time)

        val etAlias: EditText = itemView.findViewById(R.id.et_device_alias)
        val btnRemoveAlias: View = itemView.findViewById(R.id.btn_remove_alias)

        var textWatcher: TextWatcher? = null
    }

    override fun getItemViewType(position: Int): Int {
        return if (hasHeader && position == 0) VIEW_TYPE_HEADER else VIEW_TYPE_CLIENT
    }

    override fun getItemCount(): Int {
        val baseCount = super.getItemCount()
        return if (hasHeader) baseCount + 1 else baseCount
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.header_hotspot_title, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.card_hotspot_device, parent, false)
            OledThemeAllInOne.applyCardTheme(view, parent.context)
            ClientViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is HeaderViewHolder) {
            (holder.itemView.context as? MainActivity)?.let { activity ->
                holder.itemView.post { NotchAwareness.updateHotspotHeaderLayout(activity) }
            }
            return
        }

        val clientHolder = holder as? ClientViewHolder ?: return
        val clientPosition = if (hasHeader) position - 1 else position
        val client = getItem(clientPosition)
        val context = clientHolder.itemView.context

        clientHolder.tvMac.text = client.mac
        clientHolder.tvStatus.text = getLocalizedStatus(context, client)

        if (isLandscape) {
            clientHolder.dropdownView.visibility = View.GONE
            clientHolder.tvName.text = client.displayName

            clientHolder.headerView.setOnClickListener {
                selectedMac = client.mac
                onClientClick(client)
            }
        } else {
            val macKey = client.mac.lowercase()
            val isExpanded = expandedMacs.contains(macKey)
            clientHolder.dropdownView.visibility = if (isExpanded) View.VISIBLE else View.GONE
            clientHolder.ivArrow.rotation = if (isExpanded) 180f else 0f
            clientHolder.tvName.text = if (isExpanded) client.deviceName else client.displayName

            clientHolder.tvIp.text = context.getString(R.string.hotspot_ip_address, client.ipAddress)
            clientHolder.tvData.text = context.getString(R.string.hotspot_data_used, com.witherboymcw.superbattery.utils.Formatters.formatByteSize(client.totalDataBytes))
            clientHolder.tvTime.text = context.getString(R.string.hotspot_connected_time, com.witherboymcw.superbattery.utils.Formatters.formatConnectedTime(context, client.lastConnectedTimeMs, client.isCurrentlyOnline))

            if (client.isDenied) {
                clientHolder.radioDenied.isChecked = true
                clientHolder.radioAllowed.isChecked = false
            } else {
                clientHolder.radioAllowed.isChecked = true
                clientHolder.radioDenied.isChecked = false
            }

            clientHolder.radioAllowed.setOnClickListener {
                if (client.isDenied) {
                    clientHolder.radioAllowed.isChecked = true
                    clientHolder.radioDenied.isChecked = false
                    onStatusChanged(client, false)
                } else {
                    clientHolder.radioAllowed.isChecked = true
                    clientHolder.radioDenied.isChecked = false
                }
            }

            clientHolder.radioDenied.setOnClickListener {
                if (!client.isDenied) {
                    clientHolder.radioDenied.isChecked = true
                    clientHolder.radioAllowed.isChecked = false
                    onStatusChanged(client, true)
                } else {
                    clientHolder.radioDenied.isChecked = true
                    clientHolder.radioAllowed.isChecked = false
                }
            }

            clientHolder.bindTextWatcher(client)

            clientHolder.headerView.setOnClickListener {
                val expanded = expandedMacs.contains(macKey)
                if (expanded) {
                    expandedMacs.remove(macKey)
                    clientHolder.dropdownView.visibility = View.GONE
                    clientHolder.ivArrow.animate().rotation(0f).setDuration(200L).start()
                    clientHolder.tvName.text = client.displayName
                } else {
                    expandedMacs.add(macKey)
                    clientHolder.dropdownView.visibility = View.VISIBLE
                    clientHolder.ivArrow.animate().rotation(180f).setDuration(200L).start()
                    clientHolder.tvName.text = client.deviceName
                }
                onClientClick(client)
            }
        }
    }

    private fun ClientViewHolder.bindTextWatcher(client: HotspotClient) {
        textWatcher?.let { etAlias.removeTextChangedListener(it) }
        textWatcher = com.witherboymcw.superbattery.utils.Formatters.bindAliasTextWatcher(
            etAlias = etAlias,
            btnRemoveAlias = btnRemoveAlias,
            deviceName = client.deviceName,
            currentAlias = client.customAlias,
            onAliasChanged = { newAlias -> onAliasChanged(client, newAlias) },
            onUiTextUpdate = { updatedText ->
                if (dropdownView.visibility == View.GONE) {
                    tvName.text = updatedText
                }
            }
        )
    }

    private fun getLocalizedStatus(context: Context, client: HotspotClient): String {
        return when {
            client.isDenied -> context.getString(R.string.status_denied)
            client.isCurrentlyOnline -> context.getString(R.string.status_connected)
            else -> context.getString(R.string.status_allowed)
        }
    }
}
