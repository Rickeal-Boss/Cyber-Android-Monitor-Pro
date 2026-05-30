package com.example.deviceinfoviewer.ui.cpu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.deviceinfoviewer.data.model.CpuCoreInfo
import com.example.deviceinfoviewer.data.model.HistoryDataPoint
import com.example.deviceinfoviewer.ui.components.InfoCard
import com.example.deviceinfoviewer.ui.components.MetricCard
import com.example.deviceinfoviewer.ui.components.charts.LineChart
import com.example.deviceinfoviewer.ui.theme.NeonPurple
import com.example.deviceinfoviewer.ui.theme.NeonPurpleBright
import org.koin.androidx.compose.koinViewModel

/**
 * CPU 屏幕 - 连接真实数据 + 实时图表
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CpuScreen(
    viewModel: CpuViewModel = koinViewModel()
) {
    val cpuInfo by viewModel.cpuInfo.observeAsState()
    val histData by viewModel.historyData.observeAsState(emptyMap())
    var selectedView by remember { mutableIntStateOf(0) }

    val arch = cpuInfo?.architecture ?: "检测中..."
    val coreCount = cpuInfo?.coreCount ?: 0
    val temp = cpuInfo?.temperatureCelsius?.let { if (it.isNaN()) "---" else "${it.toInt()}°C" } ?: "---"
    val tempSource = cpuInfo?.temperatureSource?.takeIf { it.isNotEmpty() }
    val cores = cpuInfo?.cores ?: emptyList()
    val coreGroups = cores.groupBy { it.maxFreqKHz / 100_000 } // 按频率分组cluster
    val cacheL1 = cpuInfo?.cacheL1?.takeIf { it.isNotBlank() }
    val cacheL2 = cpuInfo?.cacheL2?.takeIf { it.isNotBlank() }
    val cacheL3 = cpuInfo?.cacheL3?.takeIf { it.isNotBlank() }

    val cpuTempChart = normalizeChartData(histData["cpu_temp"], 100f)
    val cpuFreqChart = normalizeChartData(histData["cpu_freq"], 3500f)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        InfoCard(
            title = arch,
            subtitle = "$coreCount cores · ARMv8",
            icon = Icons.Default.PlayArrow,
            iconTint = NeonPurple
        )

        Text("温度状态: ${if (temp.startsWith("---")) "检测中" else "正常"}", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = NeonPurple)

        MetricCard(
            title = "CPU temperature",
            value = temp,
            valueColor = NeonPurpleBright,
            subtitle = tempSource ?: ""
        ) {
            LineChart(data = cpuTempChart, modifier = Modifier.fillMaxWidth())
        }

        // CPU 缓存信息
        if (cacheL1 != null || cacheL2 != null || cacheL3 != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (cacheL1 != null) MetricCard(
                    title = "L1 Cache", value = cacheL1,
                    valueColor = NeonPurpleBright, modifier = Modifier.weight(1f)
                )
                if (cacheL2 != null) MetricCard(
                    title = "L2 Cache", value = cacheL2,
                    valueColor = NeonPurpleBright, modifier = Modifier.weight(1f)
                )
                if (cacheL3 != null) MetricCard(
                    title = "L3 Cache", value = cacheL3,
                    valueColor = NeonPurpleBright, modifier = Modifier.weight(1f)
                )
            }
        }

        // CPU 各核心实时频率
        if (cores.isNotEmpty()) {
            Text("核心频率", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            cores.take(8).forEach { core ->
                val freqMhz = core.currentFreqKHz / 1000f
                val maxMhz = core.maxFreqKHz / 1000f
                val pct = if (maxMhz > 0) (core.currentFreqKHz.toFloat() / core.maxFreqKHz).coerceIn(0f, 1f) else 0f
                MetricCard(
                    title = "Core ${core.coreIndex}",
                    value = "%.0f MHz".format(freqMhz),
                    valueColor = NeonPurpleBright,
                    subtitle = "最大 %.0f MHz".format(maxMhz),
                    progress = pct,
                    showProgress = true
                )
            }
        }

        // Per cluster / Per core 切换
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            FilterChip(selected = selectedView == 0, onClick = { selectedView = 0 },
                label = { Text("Per cluster") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NeonPurple.copy(alpha = 0.2f), selectedLabelColor = NeonPurple))
            Spacer(Modifier.width(8.dp))
            FilterChip(selected = selectedView == 1, onClick = { selectedView = 1 },
                label = { Text("Per core") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NeonPurple.copy(alpha = 0.2f), selectedLabelColor = NeonPurple))
        }

        if (cores.isEmpty()) {
            Text("等待数据...", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (selectedView == 0) {
            // Per cluster — 按 maxFreq 分组，使用真实频率历史
            coreGroups.values.sortedByDescending { it.first().maxFreqKHz }.forEach { group ->
                val maxFreq = group.first().maxFreqKHz / 1000
                val clusterMaxFreq = group.first().maxFreqKHz.toFloat()
                ClusterCard(
                    name = when { maxFreq > 2500 -> "Prime"; maxFreq > 1800 -> "Performance"; else -> "Efficiency" },
                    subtitle = "${group.size} cores · max ${maxFreq} MHz",
                    frequency = "${group.first().currentFreqKHz / 1000} MHz",
                    freqData = normalizeChartData(histData["cpu_freq"], clusterMaxFreq / 1000f)
                )
            }
        } else {
            // Per core view
            coreGroups.values.sortedByDescending { it.first().maxFreqKHz }.forEachIndexed { groupIdx, group ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Cluster ${groupIdx + 1} · ${group.size} cores", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            group.forEach { core ->
                                CoreItem(core = core)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClusterCard(name: String, subtitle: String, frequency: String, freqData: List<Float>) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(subtitle, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(frequency, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = NeonPurpleBright)
            }
            Spacer(Modifier.height(12.dp))
            LineChart(data = freqData, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun CoreItem(core: CpuCoreInfo) {
    Card(Modifier.width(160.dp), shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("CPU ${core.coreIndex}", fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text("${core.currentFreqKHz / 1000} MHz", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NeonPurpleBright)
            }
            Spacer(Modifier.height(8.dp))
            Text("max: ${core.maxFreqKHz / 1000} MHz", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun normalizeChartData(points: List<HistoryDataPoint>?, maxValue: Float): List<Float> {
    if (points.isNullOrEmpty()) return List(15) { 0f }
    val recent = points.takeLast(20)
    return if (maxValue > 0) recent.map { (it.value / maxValue).coerceIn(0f, 1f) } else recent.map { it.value }
}
