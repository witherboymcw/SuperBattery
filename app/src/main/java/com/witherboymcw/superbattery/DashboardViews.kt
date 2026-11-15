package com.witherboymcw.superbattery

import android.app.Activity
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import com.witherboymcw.superbattery.battery.BatteryRingView

class DashboardViews(val activity: Activity) {
    val tvVoltageTitle: TextView? = activity.findViewById(R.id.tv_voltage_title)
    val tvVoltageCurrent: TextView? = activity.findViewById(R.id.voltage_current)
    val tvTemperatureCurrent: TextView? = activity.findViewById(R.id.temperature_current)
    val tvRamCurrent: TextView? = activity.findViewById(R.id.ram_current)
    val tvVoltagePower: TextView? = activity.findViewById(R.id.tv_voltage_power)
    val tvRamUsageDetail: TextView? = activity.findViewById(R.id.tv_ram_usage_detail)
    val tvTemperatureStatus: TextView? = activity.findViewById(R.id.tv_temperature_status)
    val tvNetTxTotal: TextView? = activity.findViewById(R.id.net_tx_total)
    val tvNetTxSpeed: TextView? = activity.findViewById(R.id.net_tx_speed)
    val tvNetRxTotal: TextView? = activity.findViewById(R.id.net_rx_total)
    val tvNetRxSpeed: TextView? = activity.findViewById(R.id.net_rx_speed)
    val voltageGraph: HistoryGraphView? = activity.findViewById(R.id.voltage_graph)
    val ramGraph: HistoryGraphView? = activity.findViewById(R.id.ram_graph)
    val tempGraph: HistoryGraphView? = activity.findViewById(R.id.temperature_graph)
    val cardBattery: View? = activity.findViewById(R.id.card_battery)
    val cardBatteryTitle: TextView? = cardBattery?.findViewById(R.id.metric_title)
    val cardBatteryValue: TextView? = cardBattery?.findViewById(R.id.metric_value)
    val cardBatteryDetail: TextView? = cardBattery?.findViewById(R.id.metric_detail)
    val batteryRing: BatteryRingView? = cardBattery?.findViewById(R.id.battery_ring)
    val btnSettings: View? = activity.findViewById(R.id.settings_button)
    val tvHeaderClock: TextView? = activity.findViewById(R.id.tv_header_clock)
    val btnRingerModeToggle: ImageButton? = activity.findViewById(R.id.btn_ringer_mode_toggle)
    val ivNetworkWifi: ImageView? = activity.findViewById(R.id.iv_network_wifi)
    val ivNetworkBluetooth: ImageView? = activity.findViewById(R.id.iv_network_bluetooth)
    val ivNetworkSim1: ImageView? = activity.findViewById(R.id.iv_network_sim_1)
    val cardVoltageSummary: MaterialCardView? = activity.findViewById(R.id.card_voltage_summary)
    val cardRamSummary: MaterialCardView? = activity.findViewById(R.id.card_ram_summary)
    val cardTempSummary: MaterialCardView? = activity.findViewById(R.id.card_temp_summary)
    val headerVoltageGraph: View? = activity.findViewById(R.id.header_voltage_graph)
    val headerRamGraph: View? = activity.findViewById(R.id.header_ram_graph)
    val headerTemperatureGraph: View? = activity.findViewById(R.id.header_temperature_graph)
    val cardNetwork: View? = activity.findViewById(R.id.card_network)
}
