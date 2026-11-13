package com.witherboymcw.superbattery.templates

import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import com.witherboymcw.superbattery.R
import com.witherboymcw.superbattery.screens.hotspot.HotspotClient
import java.util.Locale

class SimpleClientCard {

    companion object {
        fun bind(
            cardView: View,
            client: HotspotClient,
            onAliasSaved: (String?) -> Unit,
            onStatusChanged: (Boolean) -> Unit
        ) {
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

            SimpleOptionCard.setupExpandableHeader(headerView, dropdownView, ivArrow)

            tvName?.text = client.displayName
            tvMac?.text = client.mac

            if (client.isDenied) {
                radioDenied?.isChecked = true
                tvStatus?.text = cardView.context.getString(R.string.status_denied)
                tvStatus?.setTextColor(0xFFFF4444.toInt())
            } else {
                radioAllowed?.isChecked = true
                tvStatus?.text = if (client.isCurrentlyOnline) {
                    cardView.context.getString(R.string.status_connected)
                } else {
                    cardView.context.getString(R.string.status_allowed)
                }
                tvStatus?.setTextColor(if (client.isCurrentlyOnline) 0xFF4CAF50.toInt() else 0xFF888888.toInt())
            }

            tvIp?.text = "IP: ${client.ipAddress}"
            tvData?.text = "Data: ${formatDataBytes(client.totalDataBytes)}"
            tvTime?.text = "Last seen: ${formatLastSeenTime(client.lastConnectedTimeMs)}"

            etAlias?.setText(client.customAlias ?: "")
            btnRemoveAlias?.visibility = if (!client.customAlias.isNullOrBlank()) View.VISIBLE else View.GONE

            radioAllowed?.setOnClickListener {
                if (client.isDenied) {
                    onStatusChanged(false)
                }
            }

            radioDenied?.setOnClickListener {
                if (!client.isDenied) {
                    onStatusChanged(true)
                }
            }

            btnRemoveAlias?.setOnClickListener {
                etAlias?.setText("")
                onAliasSaved(null)
            }
        }

        private fun formatDataBytes(bytes: Long): String {
            if (bytes <= 0L) return "0 B"
            val kb = bytes / 1024f
            if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
            val mb = kb / 1024f
            if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
            val gb = mb / 1024f
            return String.format(Locale.US, "%.2f GB", gb)
        }

        private fun formatLastSeenTime(timeMs: Long): String {
            if (timeMs <= 0L) return "Never"
            val diffMs = System.currentTimeMillis() - timeMs
            if (diffMs < 60_000L) return "Just now"
            val mins = diffMs / 60_000L
            if (mins < 60) return "$mins mins ago"
            val hours = mins / 60
            if (hours < 24) return "$hours hours ago"
            val days = hours / 24
            return "$days days ago"
        }
    }
}
