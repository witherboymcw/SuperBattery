package com.witherboymcw.superbattery.utils

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import com.witherboymcw.superbattery.R
import java.util.Locale

object Formatters {

    fun formatByteSize(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.lastIndex)
        return String.format(Locale.getDefault(), "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    fun formatConnectedTime(context: Context, lastMs: Long, isOnline: Boolean): String {
        if (!isOnline || lastMs <= 0L) return context.getString(R.string.status_offline)
        val diffMs = System.currentTimeMillis() - lastMs
        val mins = (diffMs / (1000 * 60)).toInt().coerceAtLeast(1)
        val hours = mins / 60
        return if (hours > 0) "${hours}h ${mins % 60}m" else "${mins}m"
    }

    fun bindAliasTextWatcher(
        etAlias: EditText,
        btnRemoveAlias: View,
        deviceName: String,
        currentAlias: String?,
        onAliasChanged: (newAlias: String?) -> Unit,
        onUiTextUpdate: ((text: String) -> Unit)? = null
    ): TextWatcher {
        etAlias.setText(currentAlias ?: deviceName)
        btnRemoveAlias.visibility = if (currentAlias != null) View.VISIBLE else View.GONE

        var isUpdating = false
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isUpdating) return
                val inputStr = s?.toString()?.trim() ?: ""
                val newAlias = if (inputStr.isNotBlank() && inputStr != deviceName) inputStr else null
                if (currentAlias != newAlias) {
                    onAliasChanged(newAlias)
                    btnRemoveAlias.visibility = if (newAlias != null) View.VISIBLE else View.GONE
                    onUiTextUpdate?.invoke(newAlias ?: deviceName)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        etAlias.addTextChangedListener(watcher)

        btnRemoveAlias.setOnClickListener {
            isUpdating = true
            etAlias.setText(deviceName)
            isUpdating = false
            btnRemoveAlias.visibility = View.GONE
            onAliasChanged(null)
            onUiTextUpdate?.invoke(deviceName)
        }

        return watcher
    }
}
