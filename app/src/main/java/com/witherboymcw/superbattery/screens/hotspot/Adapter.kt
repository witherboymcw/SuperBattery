package com.witherboymcw.superbattery.screens.hotspot

import android.content.Context
import android.text.Editable
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
import com.witherboymcw.superbattery.OledThemeAllInOne
import com.witherboymcw.superbattery.R

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
 */
class HotspotClientAdapter(
    private val isLandscape: Boolean,
    private val onClientClick: (client: HotspotClient) -> Unit,
    private val onStatusChanged: (client: HotspotClient, isDenied: Boolean) -> Unit,
    private val onAliasChanged: (client: HotspotClient, newAlias: String?) -> Unit
) : ListAdapter<HotspotClient, HotspotClientAdapter.ViewHolder>(HotspotClientDiffCallback()) {

    var selectedMac: String? = null
    private val expandedMacs = mutableSetOf<String>()

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.card_hotspot_device, parent, false)
        OledThemeAllInOne.applyCardTheme(view, parent.context)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val client = getItem(position)
        val context = holder.itemView.context

        holder.tvMac.text = client.mac
        holder.tvStatus.text = getLocalizedStatus(context, client)

        if (isLandscape) {
            holder.dropdownView.visibility = View.GONE
            holder.tvName.text = client.displayName

            holder.headerView.setOnClickListener {
                selectedMac = client.mac
                onClientClick(client)
            }
        } else {
            val macKey = client.mac.lowercase()
            val isExpanded = expandedMacs.contains(macKey)
            holder.dropdownView.visibility = if (isExpanded) View.VISIBLE else View.GONE
            holder.ivArrow.rotation = if (isExpanded) 180f else 0f
            holder.tvName.text = if (isExpanded) client.deviceName else client.displayName

            holder.tvIp.text = context.getString(R.string.hotspot_ip_address, client.ipAddress)
            holder.tvData.text = context.getString(R.string.hotspot_data_used, formatByteSize(client.totalDataBytes))
            holder.tvTime.text = context.getString(R.string.hotspot_connected_time, formatConnectedTime(client.lastConnectedTimeMs, client.isCurrentlyOnline))

            if (client.isDenied) {
                holder.radioDenied.isChecked = true
                holder.radioAllowed.isChecked = false
            } else {
                holder.radioAllowed.isChecked = true
                holder.radioDenied.isChecked = false
            }

            holder.radioAllowed.setOnClickListener {
                if (client.isDenied) {
                    onStatusChanged(client, false)
                }
            }

            holder.radioDenied.setOnClickListener {
                if (!client.isDenied) {
                    onStatusChanged(client, true)
                }
            }

            holder.bindTextWatcher(client)

            holder.headerView.setOnClickListener {
                val expanded = expandedMacs.contains(macKey)
                if (expanded) {
                    expandedMacs.remove(macKey)
                    holder.dropdownView.visibility = View.GONE
                    holder.ivArrow.animate().rotation(0f).setDuration(200L).start()
                    holder.tvName.text = client.displayName
                } else {
                    expandedMacs.add(macKey)
                    holder.dropdownView.visibility = View.VISIBLE
                    holder.ivArrow.animate().rotation(180f).setDuration(200L).start()
                    holder.tvName.text = client.deviceName
                }
                onClientClick(client)
            }
        }
    }

    private fun ViewHolder.bindTextWatcher(client: HotspotClient) {
        textWatcher?.let { etAlias.removeTextChangedListener(it) }
        etAlias.setText(client.customAlias ?: client.deviceName)
        btnRemoveAlias.visibility = if (client.customAlias != null) View.VISIBLE else View.GONE

        var isUpdating = false
        val newWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isUpdating) return
                val inputStr = s?.toString()?.trim() ?: ""
                val newAlias = if (inputStr.isNotBlank() && inputStr != client.deviceName) inputStr else null
                if (client.customAlias != newAlias) {
                    onAliasChanged(client, newAlias)
                    btnRemoveAlias.visibility = if (newAlias != null) View.VISIBLE else View.GONE
                    if (dropdownView.visibility == View.GONE) {
                        tvName.text = newAlias ?: client.deviceName
                    }
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        textWatcher = newWatcher
        etAlias.addTextChangedListener(newWatcher)

        btnRemoveAlias.setOnClickListener {
            isUpdating = true
            etAlias.setText(client.deviceName)
            isUpdating = false
            btnRemoveAlias.visibility = View.GONE
            onAliasChanged(client, null)
            if (dropdownView.visibility == View.GONE) {
                tvName.text = client.deviceName
            }
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
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.lastIndex)
        return String.format(java.util.Locale.getDefault(), "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    private fun formatConnectedTime(lastMs: Long, isOnline: Boolean): String {
        if (isOnline) return "Online Now"
        if (lastMs <= 0) return "Never"
        val diffMs = System.currentTimeMillis() - lastMs
        val mins = (diffMs / (1000 * 60)).toInt()
        val hours = mins / 60
        return if (hours > 0) "${hours}h ${mins % 60}m" else "${mins}m"
    }
}
