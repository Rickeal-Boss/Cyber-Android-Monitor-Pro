package com.example.deviceinfoviewer.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.deviceinfoviewer.FormatUtils
import com.example.deviceinfoviewer.data.model.HistoryDataPoint
import com.example.deviceinfoviewer.ui.components.InfoCard
import com.example.deviceinfoviewer.ui.components.MetricCard
import com.example.deviceinfoviewer.ui.components.charts.LineChart
import com.example.deviceinfoviewer.ui.theme.*
import org.koin.androidx.compose.koinViewModel

@Composable
fun DashboardScreen(
    onNavigate: (Int) -> Unit,
    viewModel: DashboardViewModel = koinViewModel()
) {
    val cpuInfo by viewModel.cpuInfo.observeAsState()
    val batteryInfo by viewModel.batteryInfo.observeAsState()
    val memoryInfo by viewModel.memoryInfo.observeAsState()
    val historyData by viewModel.historyData.observeAsState(emptyMap())

    val deviceName = cpuInfo?.architecture?.let { "$it \u00b7 ${cpuInfo?.coreCount ?: 0}\u6838" } ?: "\u68c0\u6d4b\u4e2d..."
    val cpuTemp = cpuInfo?.temperatureCelsius?.let { if (it.isNaN()) "---" else "${it.toInt()}\u00b0C" } ?: "---"
    val batteryLevel = batteryInfo?.levelPercent?.let { "${it}%" } ?: "---"
    val memUsed = memoryInfo?.let { FormatUtils.formatBytes(it.usedKB * 1024) } ?: "---"
    val memTotal = memoryInfo?.let { FormatUtils.formatBytes(it.totalKB * 1024) } ?: "---"

    val cpuTempChart = normalizeChartData(historyData["cpu_temp"], 100f)
    val ramChart = normalizeChartData(historyData["ram_usage"], 100f)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── 设备信息卡片 (带 LIVE 状态) ──
        InfoCard(
            title = deviceName,
            subtitle = buildString {
                if (batteryInfo != null) append("电池 ${batteryLevel}  ")
                if (!cpuTemp.startsWith("---")) append("温度 $cpuTemp")
            },
            icon = Icons.Default.Home, iconTint = NeonPurple
        )

        // ── 分割线 ──
        HorizontalDivider(thickness = 1.dp, color = NeonPurpleDeep.copy(alpha = 0.3f))

        // ── 2×2 实时指标网格 (Ardot 设计稿) ──
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                title = "CPU 温度", value = cpuTemp,
                valueColor = NeonPurpleBright, modifier = Modifier.weight(1f)
            ) { LineChart(data = cpuTempChart, modifier = Modifier.fillMaxWidth()) }
            MetricCard(
                title = "内存用量", value = memUsed,
                valueColor = NeonPurpleBright, modifier = Modifier.weight(1f),
                subtitle = "/ $memTotal"
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                title = "电池电量", value = batteryLevel,
                valueColor = SuccessNeon, modifier = Modifier.weight(1f),
                subtitle = if (batteryInfo?.isCharging == true) "充电中" else "放电中"
            )
            MetricCard(
                title = "GPU 负载", value = gpuLoad(historyData),
                valueColor = NeonPurpleBright, modifier = Modifier.weight(1f)
            ) { LineChart(data = normalizeChartData(historyData["gpu_load"], 100f), modifier = Modifier.fillMaxWidth()) }
        }

        // ── 分割线 ──
        HorizontalDivider(thickness = 1.dp, color = NeonPurpleDeep.copy(alpha = 0.3f))

        // ── 快速访问 ──
        Text("快速访问", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)

        // Row 1: CPU + GPU
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickLinkCard("CPU 处理器", "频率 · 核心 · 温度", Icons.Default.PlayArrow, NeonPurple,
                Modifier.weight(1f).clickable { onNavigate(1) })
            QuickLinkCard("GPU 图形", "负载 · 频率 · 温度", Icons.Default.Info, NeonPurpleBright,
                Modifier.weight(1f).clickable { onNavigate(2) })
        }

        // Row 2: 内存 + 网络
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickLinkCard("内存信息", "$memUsed / $memTotal", Icons.Default.Star, NeonPurple,
                Modifier.weight(1f).clickable { onNavigate(3) })
            QuickLinkCard("网络状态", "WiFi · 信号", Icons.Default.Share, NeonPurpleBright,
                Modifier.weight(1f).clickable { onNavigate(5) })
        }

        // Row 3: GPS + 系统详情
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickLinkCard("GPS 定位", "卫星 · 坐标", Icons.Default.PlayArrow, NeonMagenta,
                Modifier.weight(1f).clickable { onNavigate(6) })
            QuickLinkCard("系统详情", "OEM · 性能模式", Icons.Default.Search, SuccessNeon,
                Modifier.weight(1f).clickable { onNavigate(8) })
        }

        Spacer(Modifier.height(8.dp))
    }
}

// ── 快速访问卡片组件 ──
@Composable
private fun QuickLinkCard(
    title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                .background(Brush.horizontalGradient(listOf(CyberCardStart, CyberCardEnd)))
        ) {
            Row(Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(32.dp).background(CyberMuted, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = iconTint, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text(subtitle, fontSize = 11.sp, color = TextSecondary)
                }
            }
        }
    }
}

private fun gpuLoad(historyData: Map<String, List<HistoryDataPoint>>): String {
    val pts = historyData["gpu_load"]
    if (pts.isNullOrEmpty()) return "---"
    val last = pts.lastOrNull()?.value ?: return "---"
    return "${last.toInt()}%"
}

private fun normalizeChartData(points: List<HistoryDataPoint>?, maxValue: Float): List<Float> {
    if (points.isNullOrEmpty()) return List(15) { 0f }
    val recent = points.takeLast(20)
    return if (maxValue > 0) recent.map { (it.value / maxValue).coerceIn(0f, 1f) } else recent.map { it.value }
}
