package com.example.deviceinfoviewer.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.deviceinfoviewer.FormatUtils
import com.example.deviceinfoviewer.data.model.HistoryDataPoint
import com.example.deviceinfoviewer.ui.components.InfoCard
import com.example.deviceinfoviewer.ui.components.MetricCard
import com.example.deviceinfoviewer.ui.components.charts.LineChart
import com.example.deviceinfoviewer.ui.theme.NeonPurple
import com.example.deviceinfoviewer.ui.theme.NeonPurpleBright
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

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        InfoCard(
            title = deviceName,
            subtitle = buildString {
                if (batteryInfo != null) append("\u7535\u6c60 ${batteryLevel}  ")
                if (!cpuTemp.startsWith("---")) append("\u6e29\u5ea6 $cpuTemp")
            },
            icon = Icons.Default.Home, iconTint = NeonPurple
        )

        MetricCard(title = "CPU temperature", value = cpuTemp, valueColor = NeonPurpleBright) {
            LineChart(data = cpuTempChart, modifier = Modifier.fillMaxWidth())
        }

        MetricCard(title = "\u5185\u5b58", value = memUsed, valueColor = NeonPurpleBright) {
            Text(
                text = "\u603b\u5185\u5b58: $memTotal", fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = "\u5feb\u901f\u8bbf\u95ee", fontSize = 18.sp,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface
        )

        // 点击跳转到对应的 Tab
        InfoCard(
            title = "CPU", subtitle = "\u67e5\u770b\u5904\u7406\u5668\u8be6\u7ec6\u4fe1\u606f",
            icon = Icons.Default.PlayArrow, iconTint = NeonPurple,
            modifier = Modifier.clickable { onNavigate(1) }
        )
        InfoCard(
            title = "GPU", subtitle = "\u67e5\u770b\u56fe\u5f62\u5904\u7406\u5668\u4fe1\u606f",
            icon = Icons.Default.Info, iconTint = NeonPurple,
            modifier = Modifier.clickable { onNavigate(2) }
        )
        InfoCard(
            title = "\u5185\u5b58", subtitle = "\u67e5\u770b\u5185\u5b58\u4f7f\u7528\u60c5\u51b5",
            icon = Icons.Default.Star, iconTint = NeonPurple,
            modifier = Modifier.clickable { onNavigate(3) }
        )
        InfoCard(
            title = "\u7535\u6c60", subtitle = "\u67e5\u770b\u7535\u6c60\u72b6\u6001",
            icon = Icons.Default.Favorite, iconTint = NeonPurple,
            modifier = Modifier.clickable { onNavigate(4) }
        )
        InfoCard(
            title = "\u7f51\u7edc", subtitle = "\u67e5\u770b\u7f51\u7edc\u72b6\u6001",
            icon = Icons.Default.Share, iconTint = NeonPurple,
            modifier = Modifier.clickable { onNavigate(5) }
        )
    }
}

private fun normalizeChartData(points: List<HistoryDataPoint>?, maxValue: Float): List<Float> {
    if (points.isNullOrEmpty()) return List(15) { 0.5f }
    val recent = points.takeLast(20)
    return if (maxValue > 0) recent.map { (it.value / maxValue).coerceIn(0f, 1f) } else recent.map { it.value }
}
