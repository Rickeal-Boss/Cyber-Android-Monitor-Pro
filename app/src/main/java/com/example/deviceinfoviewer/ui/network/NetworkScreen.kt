package com.example.deviceinfoviewer.ui.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.deviceinfoviewer.data.model.HistoryDataPoint
import com.example.deviceinfoviewer.ui.components.InfoCard
import com.example.deviceinfoviewer.ui.components.MetricCard
import com.example.deviceinfoviewer.ui.components.charts.DualLineChart
import com.example.deviceinfoviewer.ui.components.charts.LineChart
import com.example.deviceinfoviewer.ui.theme.NeonMagenta
import com.example.deviceinfoviewer.ui.theme.NeonPurple
import com.example.deviceinfoviewer.ui.theme.NeonPurpleBright
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

    val wifiSpeedChart = normalizeChartData(historyData["wifi_speed"], 1000f)
    val signalChart = normalizeChartDataAbs(historyData["signal_strength"])

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val wifiStatus = buildString {
            if (wifiConnected) {
                append("Wi-Fi · 已连接")
                if (wifiSsid != null) append(" ($wifiSsid)")
            } else append("Wi-Fi · 未连接")
        }
        val wifiSubtitle = buildString {
            if (wifiInfo?.linkSpeedMbps?.let { it > 0 } == true)
                append("${wifiInfo!!.linkSpeedMbps} Mbps")
            if (networkType != null) {
                if (this.isNotEmpty()) append(" · ")
                append(networkType)
            }
        }
        InfoCard(title = wifiStatus, subtitle = wifiSubtitle.ifEmpty { "等待数据..." },
            icon = Icons.Default.Share, iconTint = NeonPurple)

        MetricCard(title = "Network activity", value = "${wifiInfo?.linkSpeedMbps ?: 0} Mbps", valueColor = NeonPurpleBright) {
            DualLineChart(data1 = wifiSpeedChart, data2 = signalChart,
                modifier = Modifier.fillMaxWidth(), lineColor1 = NeonPurple, lineColor2 = NeonMagenta)
        }

        if (signalStrength != null) {
            val pct = kotlin.math.min(100, (signalStrength + 120) * 100 / 60).coerceIn(0, 100)
            MetricCard(title = "Signal strength", value = "$signalStrength dBm · $pct%",
                valueColor = NeonPurpleBright) {
                LineChart(data = signalChart, modifier = Modifier.fillMaxWidth())
            }
        }

        // WiFi 频率 / 标准 / 信道宽度 (P3)
        val freqMhz = wifiInfo?.frequencyMHz?.takeIf { it > 0 }
        val wifiStd = wifiInfo?.wifiStandard?.takeIf { it.isNotEmpty() }
        val chWidth = wifiInfo?.channelWidth?.takeIf { it.isNotEmpty() }
        if (freqMhz != null || wifiStd != null || chWidth != null) {
            MetricCard(
                title = "WiFi 详情",
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

        // 详细网络信息
        MetricCard(title = "IP 地址",
            value = wifiInfo?.ipv4?.takeIf { it.isNotEmpty() } ?: "---", valueColor = NeonPurpleBright)

        MetricCard(title = "网关",
            value = wifiInfo?.gateway?.takeIf { it.isNotEmpty() } ?: "---", valueColor = NeonPurpleBright)

        MetricCard(title = "DNS",
            value = wifiInfo?.dns?.takeIf { it.isNotEmpty() } ?: "---", valueColor = NeonPurpleBright)

        MetricCard(title = "MAC",
            value = wifiInfo?.macAddress?.takeIf { it.isNotEmpty() } ?: "---", valueColor = NeonPurpleBright)

        MetricCard(title = "子网掩码",
            value = wifiInfo?.subnetMask?.takeIf { it.isNotEmpty() } ?: "---", valueColor = NeonPurpleBright)

        MetricCard(title = "BSSID",
            value = wifiInfo?.bssid?.takeIf { it.isNotEmpty() } ?: "---", valueColor = NeonPurpleBright)

        // 移动网络
        if (networkType != null) {
            MetricCard(title = "网络类型",
                value = networkType, valueColor = NeonPurpleBright)
        }
        MetricCard(title = "运营商",
            value = mobileNetwork?.operatorName?.takeIf { it.isNotEmpty() } ?: "---", valueColor = NeonPurpleBright)

        // 附近 AP
        val aps = wifiInfo?.nearbyAps ?: emptyList()
        if (aps.isNotEmpty()) {
            MetricCard(title = "附近 AP",
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
    // Signal strength is negative (e.g., -50 dBm to -120 dBm), normalize to 0..1
    return recent.map { ((it.value + 120) / 120f).coerceIn(0f, 1f) }
}
