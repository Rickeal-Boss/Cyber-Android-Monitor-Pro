package com.rb.cybermonitorpro.ui.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rb.cybermonitorpro.R
import com.rb.cybermonitorpro.FormatUtils
import com.rb.cybermonitorpro.data.model.HistoryDataPoint
import com.rb.cybermonitorpro.ui.components.charts.ChartUtils
import com.rb.cybermonitorpro.data.model.MobileNetworkInfo
import com.rb.cybermonitorpro.ui.components.CyberIcons
import com.rb.cybermonitorpro.ui.components.InfoCard
import com.rb.cybermonitorpro.ui.components.MetricCard
import com.rb.cybermonitorpro.ui.components.charts.DualLineChart
import com.rb.cybermonitorpro.ui.components.charts.LineChart
import com.rb.cybermonitorpro.ui.theme.*
import org.koin.androidx.compose.koinViewModel
import com.rb.cybermonitorpro.ui.effects.staggeredSwipe

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

    // ★ derivedStateOf: 缓存图表数据，仅在原始数据变更时重新计算
    val wifiSpeedChart by remember(historyData) {
        derivedStateOf { ChartUtils.normalizeChartData(historyData["wifi_speed"], 1000f) }
    }
    val signalChart by remember(historyData) {
        derivedStateOf { ChartUtils.normalizeSignalStrength(historyData["signal_strength"]) }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        var cardIdx = 0
        val wifiConnectedLabel = stringResource(R.string.network_wifi_connected)
        val wifiDisconnectedLabel = stringResource(R.string.network_wifi_not_connected)
        val wifiStatus = buildString {
            if (wifiConnected) {
                append(wifiConnectedLabel)
                if (wifiSsid != null) append(" ($wifiSsid)")
            } else append(wifiDisconnectedLabel)
        }
        val wifiSubtitle = FormatUtils.joinNonBlank(" · ",
            wifiInfo?.linkSpeedMbps?.takeIf { it > 0 }?.let { "$it Mbps" },
            networkType
        )
        InfoCard(modifier = Modifier.staggeredSwipe(cardIdx++), title = wifiStatus, subtitle = wifiSubtitle.ifEmpty { stringResource(R.string.common_waiting_data) },
            icon = CyberIcons.Share, iconTint = NeonPurple)

        MetricCard(modifier = Modifier.staggeredSwipe(cardIdx++), title = "Network activity", value = "${wifiInfo?.linkSpeedMbps ?: 0} Mbps", valueColor = PorcelainBlueDeep) {
            DualLineChart(data1 = wifiSpeedChart, data2 = signalChart,
                modifier = Modifier.fillMaxWidth(), lineColor1 = PorcelainBlue, lineColor2 = PorcelainRed)
        }

        // WiFi 频率 / 标准 / 信道宽度 (P3)
        val freqMhz = wifiInfo?.frequencyMHz?.takeIf { it > 0 }
        val wifiStd = wifiInfo?.wifiStandard?.takeIf { it.isNotEmpty() }
        val chWidth = wifiInfo?.channelWidth?.takeIf { it.isNotEmpty() }
        if (freqMhz != null || wifiStd != null || chWidth != null) {
            MetricCard(
                modifier = Modifier.staggeredSwipe(cardIdx++),
                title = stringResource(R.string.network_wifi_details_title),
                value = wifiStd ?: "---",
                valueColor = NeonPurpleBright,
                subtitle = FormatUtils.joinNonBlank("  ·  ",
                    freqMhz?.let { "${it} MHz" },
                    chWidth
                )
            )
        }

        // WiFi 芯片温度 (dumpsys wifi)
        val wifiChipTemp = wifiInfo?.chipTemperatureCelsius?.takeIf { !it.isNaN() }
        val wifiPowerSave = wifiInfo?.powerSaveMode?.takeIf { it.isNotEmpty() }
        if (wifiChipTemp != null || wifiPowerSave != null) {
            MetricCard(
                modifier = Modifier.staggeredSwipe(cardIdx++),
                title = stringResource(R.string.network_wifi_chip_title),
                value = FormatUtils.joinNonBlank("  ·  ",
                    wifiChipTemp?.let { "%.1f°C".format(it) },
                    wifiPowerSave?.let { key ->
                        when (key) {
                            "wifi_power_save_on" -> stringResource(R.string.wifi_power_save_on)
                            "wifi_power_save_off" -> stringResource(R.string.wifi_power_save_off)
                            else -> key
                        }
                    }
                ),
                valueColor = NeonPurpleBright
            )
        }

        // 详细网络信息
        MetricCard(modifier = Modifier.staggeredSwipe(cardIdx++), title = stringResource(R.string.network_ip_address_title),
            value = wifiInfo?.ipv4?.takeIf { it.isNotEmpty() } ?: "---", valueColor = NeonPurpleBright)

        MetricCard(modifier = Modifier.staggeredSwipe(cardIdx++), title = stringResource(R.string.network_gateway_title),
            value = wifiInfo?.gateway?.takeIf { it.isNotEmpty() } ?: "---", valueColor = NeonPurpleBright)

        MetricCard(modifier = Modifier.staggeredSwipe(cardIdx++), title = "DNS",
            value = wifiInfo?.dns?.takeIf { it.isNotEmpty() } ?: "---", valueColor = NeonPurpleBright)

        MetricCard(modifier = Modifier.staggeredSwipe(cardIdx++), title = "MAC",
            value = wifiInfo?.macAddress?.takeIf { it.isNotEmpty() } ?: "---", valueColor = NeonPurpleBright)

        MetricCard(modifier = Modifier.staggeredSwipe(cardIdx++), title = stringResource(R.string.network_subnet_mask_title),
            value = wifiInfo?.subnetMask?.takeIf { it.isNotEmpty() } ?: "---", valueColor = NeonPurpleBright)

        MetricCard(modifier = Modifier.staggeredSwipe(cardIdx++), title = "BSSID",
            value = wifiInfo?.bssid?.takeIf { it.isNotEmpty() } ?: "---", valueColor = NeonPurpleBright)

        // 移动网络
        if (networkType != null) {
            MetricCard(modifier = Modifier.staggeredSwipe(cardIdx++), title = stringResource(R.string.network_type_title),
                value = networkType, valueColor = NeonPurpleBright)
        }
        MetricCard(modifier = Modifier.staggeredSwipe(cardIdx++), title = stringResource(R.string.network_operator_title),
            value = mobileNetwork?.operatorName?.takeIf { it.isNotEmpty() } ?: "---", valueColor = NeonPurpleBright)

        // ── NR/LTE 独立信号强度 dBm ──
        if (nrDbm != null) {
            // 5G NR SS-RSRP 阈值 (3GPP TS 38.215: -156~-31 dBm)
            val nrLevel = signalLevelText(nrDbm, -85, -95, -105)
                MetricCard(
                    modifier = Modifier.staggeredSwipe(cardIdx++),
                    title = stringResource(R.string.network_nr_5g_signal_title),
                value = "$nrDbm dBm  ·  $nrLevel",
                valueColor = signalLevelColor(nrDbm, -95, -105)
            )
        }
        if (lteDbm != null) {
            // LTE RSRP 阈值 (3GPP TS 36.133)
            val lteLevel = signalLevelText(lteDbm, -85, -100, -115)
            MetricCard(
                modifier = Modifier.staggeredSwipe(cardIdx++),
                title = stringResource(R.string.network_lte_4g_signal_title),
                value = "$lteDbm dBm  ·  $lteLevel",
                valueColor = signalLevelColor(lteDbm, -100, -115)
            )
        }
        // 通用信号强度卡片（RSRP 兜底）
        if (signalStrength != null && nrDbm == null && lteDbm == null) {
            val pct = kotlin.math.min(100, (signalStrength + 120) * 100 / 60).coerceIn(0, 100)
            MetricCard(modifier = Modifier.staggeredSwipe(cardIdx++), title = stringResource(R.string.network_signal_strength_title), value = "$signalStrength dBm · $pct%",
                valueColor = signalLevelColor(signalStrength, -80, -100)) {
                LineChart(data = signalChart, modifier = Modifier.fillMaxWidth(), lineColor = PorcelainBlue)
            }
        }

        // ── 5G / LTE 小区详情 ──
        val mn = mobileNetwork
        if (mn != null && hasCellInfo(mn)) {
            CellDetailCard(mn, modifier = Modifier.staggeredSwipe(cardIdx++))
        }

        // 附近 AP (始终显示，无数据时给出提示)
        val aps = wifiInfo?.nearbyAps ?: emptyList()
        MetricCard(
            modifier = Modifier.staggeredSwipe(cardIdx++),
            title = stringResource(R.string.network_nearby_aps_title),
            value = if (aps.isNotEmpty()) aps.joinToString("\n")
                    else stringResource(R.string.network_no_aps_found),
            valueColor = if (aps.isNotEmpty()) NeonPurpleBright else TextSecondary
        )
    }
}

// normalizeChartData / normalizeChartDataAbs 已迁移到 ChartUtils.kt — 全局共享，消除 6 份重复定义

// ── 5G / LTE 小区详情 ──

private fun hasCellInfo(info: MobileNetworkInfo): Boolean {
    return info.cellId > 0 || info.arfcn > 0 || info.rsrp > Int.MIN_VALUE
}

@Composable
private fun CellDetailCard(info: MobileNetworkInfo, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
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
        else -> PorcelainPinkDeep
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
