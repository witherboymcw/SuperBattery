package com.witherboymcw.superbattery

import android.app.Activity
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import com.witherboymcw.superbattery.battery.BatteryRingView

class DashboardViews(val activity: Activity) {
    var tvVoltageTitle: TextView? = null
    var tvVoltageCurrent: TextView? = null
    var tvTemperatureCurrent: TextView? = null
    var tvRamCurrent: TextView? = null
    var tvVoltagePower: TextView? = null
    var tvRamUsageDetail: TextView? = null
    var tvTemperatureStatus: TextView? = null
    var tvNetTxTotal: TextView? = null
    var tvNetTxSpeed: TextView? = null
    var tvNetRxTotal: TextView? = null
    var tvNetRxSpeed: TextView? = null
    var voltageGraph: HistoryGraphView? = null
    var ramGraph: HistoryGraphView? = null
    var tempGraph: HistoryGraphView? = null
    var cardBattery: View? = null
    var cardBatteryTitle: TextView? = null
    var cardBatteryValue: TextView? = null
    var cardBatteryDetail: TextView? = null
    var batteryRing: BatteryRingView? = null
    var btnSettings: View? = null
    var tvHeaderClock: TextView? = null
    var tvHeaderSeconds: TextView? = null
    var btnRingerModeToggle: ImageButton? = null
    var ivNetworkWifi: ImageView? = null
    var ivNetworkBluetooth: ImageView? = null
    var ivNetworkSim1: ImageView? = null
    var cardVoltageSummary: MaterialCardView? = null
    var cardRamSummary: MaterialCardView? = null
    var cardTempSummary: MaterialCardView? = null
    var headerVoltageGraph: View? = null
    var headerRamGraph: View? = null
    var headerTemperatureGraph: View? = null
    var cardNetwork: View? = null

    init {
        refresh()
    }

    fun refresh() {
        tvVoltageTitle = activity.findViewById(R.id.tv_voltage_title)
        tvVoltageCurrent = activity.findViewById(R.id.voltage_current)
        tvTemperatureCurrent = activity.findViewById(R.id.temperature_current)
        tvRamCurrent = activity.findViewById(R.id.ram_current)
        tvVoltagePower = activity.findViewById(R.id.tv_voltage_power)
        tvRamUsageDetail = activity.findViewById(R.id.tv_ram_usage_detail)
        tvTemperatureStatus = activity.findViewById(R.id.tv_temperature_status)
        tvNetTxTotal = activity.findViewById(R.id.net_tx_total)
        tvNetTxSpeed = activity.findViewById(R.id.net_tx_speed)
        tvNetRxTotal = activity.findViewById(R.id.net_rx_total)
        tvNetRxSpeed = activity.findViewById(R.id.net_rx_speed)
        voltageGraph = activity.findViewById(R.id.voltage_graph)
        ramGraph = activity.findViewById(R.id.ram_graph)
        tempGraph = activity.findViewById(R.id.temperature_graph)
        cardBattery = activity.findViewById(R.id.card_battery)
        cardBatteryTitle = cardBattery?.findViewById(R.id.metric_title)
        cardBatteryValue = cardBattery?.findViewById(R.id.metric_value)
        cardBatteryDetail = cardBattery?.findViewById(R.id.metric_detail)
        batteryRing = cardBattery?.findViewById(R.id.battery_ring)
        btnSettings = activity.findViewById(R.id.settings_button)
        tvHeaderClock = activity.findViewById(R.id.tv_header_clock)
        tvHeaderSeconds = activity.findViewById(R.id.tv_header_seconds)
        btnRingerModeToggle = activity.findViewById(R.id.btn_ringer_mode_toggle)
        ivNetworkWifi = activity.findViewById(R.id.iv_network_wifi)
        ivNetworkBluetooth = activity.findViewById(R.id.iv_network_bluetooth)
        ivNetworkSim1 = activity.findViewById(R.id.iv_network_sim_1)
        cardVoltageSummary = activity.findViewById(R.id.card_voltage_summary)
        cardRamSummary = activity.findViewById(R.id.card_ram_summary)
        cardTempSummary = activity.findViewById(R.id.card_temp_summary)
        headerVoltageGraph = activity.findViewById(R.id.header_voltage_graph)
        headerRamGraph = activity.findViewById(R.id.header_ram_graph)
        headerTemperatureGraph = activity.findViewById(R.id.header_temperature_graph)
        cardNetwork = activity.findViewById(R.id.card_network)
    }
}
