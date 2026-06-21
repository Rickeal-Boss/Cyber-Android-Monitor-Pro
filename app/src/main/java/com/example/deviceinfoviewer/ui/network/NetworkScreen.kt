package com.example.deviceinfoviewer.ui.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.deviceinfoviewer.R
import com.example.deviceinfoviewer.data.model.HistoryDataPoint
import com.example.deviceinfoviewer.data.model.MobileNetworkInfo
import com.example.deviceinfoviewer.ui.components.InfoCard
import com.example.deviceinfoviewer.ui.components.MetricCard
import com.example.deviceinfoviewer.ui.components.charts.DualLineChart
import com.example.deviceinfoviewer.ui.components.charts.LineChart
import com.example.deviceinfoviewer.ui.theme.*
import org.koin.androidx.compose.koinViewModel

@Composable
fun NetworkScreen(viewModel: NetworkViewModel = koinViewModel()) {
    val wifiInfo by viewModel.wifiInfo.observeAsState()
    val mobileNetwork by viewModel.mobileNetworkInfo.observeAsState()
    val historyData by viewModel.historyData.observeAsState(emptyMap())

    val wifiSsid = wifiInfo?.ssid?.takeIf { it.isNotEmpty() }
    val wifiConnected = wifiSsid != null
    val networkType = mobileNetwork?.networkType?.takeIf { it.isNotEmpty() }
    val signalStrength = mobileNetwork?.signalStrengthDbm?.takeIf { it > Int.MIN_VALUE }
    // 分制式 NR/LTE 信号强度
    val nrDbm = mobileNetwork?.nrSignalDbm?.takeIf { it > Int.MIN_VALUE }
    val lteDbm = mobileNetwork?.lteSignalDbm?.takeIf { it > Int.MIN_VALUE }
    val nrRsrp = mobileNetwork?.nrRsrp?.takeIf { it > Int.MIN_VALUE }
    val lteRsrp = mobileNetwork?.lteRsrp?.takeIf { it > Int.MIN_VALUE }

    val wifiSpeedChart = normalizeChartData(historyData["wifi_speed"], 1000f)
    val signalChart = normalizeChartDataAbs(historyData["signal_strength"])

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val wifiConnectedLabel = stringResource(R.string.network_wifi_connected)
        val wifiDisconnectedLabel = stringResource(R.string.network_wifi_not_connected)
        val wifiStatus = buildString {
            if (wifiConnected) {
                append(wifiConnectedLabel)
                if (wifiSsid != null) append(" ($wifiSsid)")
            } else append(wifiDisconnectedLabel)
        }
        val wifiSubtitle = buildString {
            if (wifiInfo?.linkSpeedMbps?.let { it > 0 } == true)
                append("${wifiInfo!!.linkSpeedMbps} Mbps")
            if (networkType != null) {
                if (this.isNotEmpty()) append(" · ")
                append(networkType)
            }
        }
        InfoCard(title = wifiStatus, subtitle = wifiSubtitle.ifEmpty { stringResource(R.string.common_waiting_data) },
            icon = Icons.Filled.Share, iconTint = NeonPurple)

        MetricCard(title = "Network activity", value = "${wifiInfo?.linkSpeedMbps ?: 0} Mbps", valueColor = NeonPurpleBright) {
            DualLineChart(data1 = wifiSpeedChart, data2 = signalChart,
                modifier = Modifier.fillMaxWidth(), lineColor1 = NeonPurple, lineColor2 = NeonMagenta)
        }

        // WiFi 频率 / 标准 / 信道宽度 (P3)
        val freqMhz = wifiInfo?.frequencyMHz?.takeIf { it > 0 }
        val wifiStd = wifiInfo?.wifiStandard?.takeIf { it.isNotEmpty() }
        val chWidth = wifiInfo?.channelWidth?.takeIf { it.isNotEmpty() }
        if (freqMhz != null || wifiStd != null || chWidth != null) {
            MetricCard(
                title = stringResource(R.string.network_wifi_details_title),
                value = wifiStd ?: "---",
                valueColor = NeonPurpleBright,
                subtitle = buildString {
                    if (freqMhz != null) append("${freqMhz} MHz")
                    if (chWidth != null) {
                        if (this.isNotEmpty()) append("  ·  ")
                        append(chWidth)
                    }
                }
            )
        }

        // WiFi 芯片温度 (dumpsys wifi)
        val wifiChipTemp = wifiInfo?.chipTemperatureCelsius?.takeIf { !it.isNaN() }
        val wifiPowerSave = wifiInfo?.powerSaveMode?.takeIf { it.isNotEmpty() }
        if (wifiChipTemp != null || wifiPowerSave != null) {
            MetricCard(
                title = stringResource(R.string.network_wifi_chip_title),
                value = buildString {
                    if (wifiChipTemp != null) append("${wifiChipTemp.toInt()}°C")
                    if (wifiPowerSave != null) {
                        if (this.isNotEmpty()) append("  ·  ")
                        append(wifiPowerSave)
                    }
                },
                valueColor = NeonPurpleBright
            )
        }

        // 详细网络信息
        MetricCard(title = stringResource(R.string.network_ip_address_title),
            value = wifiInfo?.ipv4?.takeIf { it.isNotEmpty() } ?: "---", valueColor = NeonPurpleBright)

        MetricCard(title = stringResource(R.string.network_gateway_title),
            value = wifiInfo?.gateway?.takeIf { it.isNotEmpty() } ?: "---", valueColor = NeonPurpleBright)

        MetricCard(title = "DNS",
            value = wifiInfo?.dns?.takeIf { it.isNotEmpty() } ?: "---", valueColor = NeonPurpleBright)

        MetricCard(title = "MAC",
            value = wifiInfo?.macAddress?.takeIf { it.isNotEmpty() } ?: "---", valueColor = NeonPurpleBright)

        MetricCard(title = stringResource(R.string.network_subnet_mask_title),
            value = wifiInfo?.subnetMask?.takeIf { it.isNotEmpty() } ?: "---", valueColor = NeonPurpleBright)

        MetricCard(title = "BSSID",
            value = wifiInfo?.bssid?.takeIf { it.isNotEmpty() } ?: "---", valueColor = NeonPurpleBright)

        // 移动网络
        if (networkType != null) {
            MetricCard(title = stringResource(R.string.network_type_title),
                value = networkType, valueColor = NeonPurpleBright)
        }
        MetricCard(title = stringResource(R.string.network_operator_title),
            value = mobileNetwork?.operatorName?.takeIf { it.isNotEmpty() } ?: "---", valueColor = NeonPurpleBright)

        // ── NR/LTE 独立信号强度 dBm ──
        if (nrDbm != null) {
            // 5G NR SS-RSRP 阈值 (3GPP TS 38.215: -156~-31 dBm)
            val nrLevel = signalLevelText(nrDbm, -85, -95, -105)
            MetricCard(
                title = stringResource(R.string.network_nr_5g_signal_title),
                value = "$nrDbm dBm  ·  $nrLevel",
                valueColor = signalLevelColor(nrDbm, -95, -105)
            )
        }
        if (lteDbm != null) {
            // LTE RSRP 阈值 (3GPP TS 36.133)
            val lteLevel = signalLevelText(lteDbm, -85, -100, -115)
            MetricCard(
                title = stringResource(R.string.network_lte_4g_signal_title),
                value = "$lteDbm dBm  ·  $lteLevel",
                valueColor = signalLevelColor(lteDbm, -100, -115)
            )
        }
        // 通用信号强度卡片（RSRP 兜底）
        if (signalStrength != null && nrDbm == null && lteDbm == null) {
            val pct = kotlin.math.min(100, (signalStrength + 120) * 100 / 60).coerceIn(0, 100)
            MetricCard(title = stringResource(R.string.network_signal_strength_title), value = "$signalStrength dBm · $pct%",
                valueColor = signalLevelColor(signalStrength, -80, -100)) {
                LineChart(data = signalChart, modifier = Modifier.fillMaxWidth())
            }
        }

        // ── 5G / LTE 小区详情 ──
        val mn = mobileNetwork
        if (mn != null && hasCellInfo(mn)) {
            CellDetailCard(mn)
        }

        // 附近 AP
        val aps = wifiInfo?.nearbyAps ?: emptyList()
        if (aps.isNotEmpty()) {
            MetricCard(title = stringResource(R.string.network_nearby_aps_title),
                value = aps.joinToString("\n"), valueColor = NeonPurpleBright)
        }
    }
}

private fun normalizeChartData(points: List<HistoryDataPoint>?, maxValue: Float): List<Float> {
    if (points.isNullOrEmpty()) return List(15) { 0f }
    val recent = points.takeLast(20)
    return if (maxValue > 0) recent.map { (it.value / maxValue).coerceIn(0f, 1f) } else recent.map { it.value }
}

private fun normalizeChartDataAbs(points: List<HistoryDataPoint>?): List<Float> {
    if (points.isNullOrEmpty()) return List(15) { 0f }
    val recent = points.takeLast(20)
    // Signal strength is negative (e.g., -59 dBm to -120 dBm), normalize to 0..1
    return recent.map { ((it.value + 130) / 100f).coerceIn(0f, 1f) }
}

// ── 5G / LTE 小区详情 ──

private fun hasCellInfo(info: MobileNetworkInfo): Boolean {
    return info.cellId > 0 || info.arfcn > 0 || info.rsrp > Int.MIN_VALUE
}

@Composable
private fun CellDetailCard(info: MobileNetworkInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.network_cell_info_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 12.dp))

            // Cell ID
            if (info.cellId > 0) {
                CellRow("Cell ID", formatCellId(info.cellId))
            }
            // PCI
            if (info.pci >= 0) {
                CellRow("PCI", "${info.pci}")
            }
            // Band
            if (info.band.isNotEmpty()) {
                CellRow(stringResource(R.string.network_band_title), info.band)
            }
            // ARFCN
            if (info.arfcn > 0) {
                CellRow(if (info.networkType.contains("5G")) "NR ARFCN" else "EARFCN",
                    "${info.arfcn}")
            }
            // DL BandWidth
            if (info.dlBandwidth.isNotEmpty()) {
                CellRow(stringResource(R.string.network_dl_bandwidth_title), info.dlBandwidth)
            }
            // UL Configured
            if (info.ulConfigured.isNotEmpty()) {
                CellRow(stringResource(R.string.network_ul_status_title), info.ulConfigured)
            }
            // RSRP
            if (info.rsrp > Int.MIN_VALUE) {
                val rsrpLabel = if (info.networkType.contains("5G")) "SS-RSRP" else "RSRP"
                CellRow(rsrpLabel, "${info.rsrp} dBm",
                    signalLevelColor(info.rsrp, -95, -110))
            }
            // RSRQ
            if (info.rsrq > Int.MIN_VALUE) {
                CellRow("RSRQ", "${info.rsrq} dB",
                    signalLevelColor(info.rsrq, -10, -15))
            }
            // SINR
            if (info.sinr > Int.MIN_VALUE) {
                CellRow("SINR", "${info.sinr} dB",
                    signalLevelColor(info.sinr, 20, 10))
            }
            // RSSI
            if (info.rssi > Int.MIN_VALUE) {
                CellRow("RSSI", "${info.rssi} dBm",
                    signalLevelColor(info.rssi, -60, -80))
            }
        }
    }
}

@Composable
private fun CellRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color = NeonPurpleBright) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, color = TextSecondary)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace, color = valueColor)
    }
}

private fun formatCellId(cellId: Long): String {
    return if (cellId > 0xFFFFFFFFL) {
        String.format("0x%X (%d)", cellId, cellId)
    } else {
        cellId.toString()
    }
}

private fun signalLevelColor(value: Int, good: Int, poor: Int): androidx.compose.ui.graphics.Color {
    return when {
        value >= good -> SuccessNeon
        value >= poor -> WarningNeon
        else -> NeonMagenta
    }
}

@Composable
private fun signalLevelText(dBm: Int, excellent: Int, good: Int, poor: Int): String {
    return when {
        dBm >= excellent -> stringResource(R.string.network_signal_excellent)
        dBm >= good -> stringResource(R.string.network_signal_good)
        dBm >= poor -> stringResource(R.string.network_signal_average)
        else -> stringResource(R.string.network_signal_weak)
    }
}
