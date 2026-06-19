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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.deviceinfoviewer.data.model.CpuCoreInfo
import com.example.deviceinfoviewer.data.model.HistoryDataPoint
import com.example.deviceinfoviewer.R
import com.example.deviceinfoviewer.ui.components.InfoCard
import com.example.deviceinfoviewer.ui.components.MetricCard
import com.example.deviceinfoviewer.ui.components.charts.LineChart
import com.example.deviceinfoviewer.ui.theme.NeonCyan
import com.example.deviceinfoviewer.ui.theme.NeonMagenta
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
    val ctx = LocalContext.current

    val arch = cpuInfo?.architecture ?: stringResource(R.string.common_detecting)
    val coreCount = cpuInfo?.coreCount ?: 0
    val temp = cpuInfo?.temperatureCelsius?.let { if (it.isNaN()) "---" else "${it.toInt()}°C" } ?: "---"
    val tempSource = cpuInfo?.temperatureSource?.takeIf { it.isNotEmpty() }
    val cores = cpuInfo?.cores ?: emptyList()
    val coreGroups = cores.groupBy { it.maxFreqKHz / 100_000 } // 按频率分组cluster
    val cacheL1 = cpuInfo?.cacheL1?.takeIf { it.isNotBlank() }
    val cacheL2 = cpuInfo?.cacheL2?.takeIf { it.isNotBlank() }
    val cacheL3 = cpuInfo?.cacheL3?.takeIf { it.isNotBlank() }
    val deepSleepPct = cpuInfo?.deepSleepPercent
    val cStates = cpuInfo?.cStates ?: emptyList()
    val cpuidleSource = cpuInfo?.cpuidleSource?.takeIf { it.isNotEmpty() }
    val supportedAbis = cpuInfo?.supportedAbis ?: emptyList()

    val cpuTempChart = normalizeChartData(histData["cpu_temp"], 100f)
    val cpuFreqChart = normalizeChartData(histData["cpu_freq"], 3500f)
    val deepSleepChart = normalizeChartData(histData["cpu_deep_sleep"], 100f)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        InfoCard(
            title = arch,
            subtitle = "$coreCount cores · ARMv8",
            icon = Icons.Filled.PlayArrow,
            iconTint = NeonPurple
        )

        val tempStatus = if (temp.startsWith("---")) stringResource(R.string.common_detecting_short) else stringResource(R.string.cpu_temp_status_normal)
        Text(stringResource(R.string.cpu_temp_status_label, tempStatus), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = NeonPurple)

        MetricCard(
            title = "CPU temperature",
            value = temp,
            valueColor = NeonPurpleBright,
            subtitle = tempSource ?: ""
        ) {
            LineChart(data = cpuTempChart, modifier = Modifier.fillMaxWidth())
        }

        // CPU 深度睡眠 (C-States)
        if (deepSleepPct != null && !deepSleepPct.isNaN() && cStates.isNotEmpty()) {
            Text(stringResource(R.string.cpu_title_deep_sleep), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            MetricCard(
                title = stringResource(R.string.cpu_title_c_states),
                value = "${deepSleepPct.toInt()}%",
                valueColor = NeonPurpleBright,
                subtitle = cpuidleSource ?: ""
            ) {
                LineChart(data = deepSleepChart, modifier = Modifier.fillMaxWidth())
            }

            // 各 C-State 详情
            cStates.forEach { state ->
                val totalTime = cStates.sumOf { it.timeUs }
                val pct = if (totalTime > 0) (state.timeUs.toFloat() / totalTime * 100f).coerceIn(0f, 100f) else 0f
                val color = when {
                    state.level >= 2 -> NeonPurpleBright
                    state.level == 1 -> NeonCyan
                    else -> NeonPurple.copy(alpha = 0.6f)
                }
                MetricCard(
                    title = "${state.name} (C${state.level}·${cStateDesc(ctx, state.name, state.level)})",
                    value = "${pct.toInt()}%",
                    valueColor = color,
                    subtitle = stringResource(R.string.cpu_c_state_subtitle, state.latencyUs, state.usage),
                    progress = pct / 100f,
                    showProgress = true
                )
            }
        }

        // CPU 缓存信息 — 完整展开, 不压缩
        if (cacheL1 != null || cacheL2 != null || cacheL3 != null) {
            if (cacheL1 != null) MetricCard(
                title = "L1 Cache", value = cacheL1,
                valueColor = NeonPurpleBright
            )
            if (cacheL2 != null) MetricCard(
                title = "L2 Cache", value = cacheL2,
                valueColor = NeonPurpleBright
            )
            if (cacheL3 != null) MetricCard(
                title = "L3 Cache", value = cacheL3,
                valueColor = NeonPurpleBright
            )
        }

        // 支持的 ABI
        if (supportedAbis.isNotEmpty()) {
            Text(stringResource(R.string.cpu_title_supported_abis), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            supportedAbis.forEach { abi ->
                val abiLabel = when {
                    abi.contains("arm64") -> "ARM 64-bit (arm64-v8a)"
                    abi.contains("armeabi-v7a") -> "ARM 32-bit (armeabi-v7a)"
                    abi.contains("x86_64") -> "x86 64-bit"
                    abi.contains("x86") -> "x86 32-bit"
                    abi.contains("riscv64") -> "RISC-V 64-bit"
                    else -> abi
                }
                MetricCard(
                    title = if (supportedAbis.indexOf(abi) == 0) stringResource(R.string.cpu_abi_primary) else stringResource(R.string.cpu_abi_compatible),
                    value = abiLabel,
                    valueColor = NeonPurpleBright,
                    subtitle = abi
                )
            }
        }

        // CPU 各核心实时频率
        if (cores.isNotEmpty()) {
            Text(stringResource(R.string.cpu_title_core_freq), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            cores.take(8).forEach { core ->
                val freqMhz = core.currentFreqKHz / 1000f
                val maxMhz = core.maxFreqKHz / 1000f
                val pct = if (maxMhz > 0) (core.currentFreqKHz.toFloat() / core.maxFreqKHz).coerceIn(0f, 1f) else 0f
                MetricCard(
                    title = "Core ${core.coreIndex}",
                    value = "%.0f MHz".format(freqMhz),
                    valueColor = NeonPurpleBright,
                    subtitle = stringResource(R.string.cpu_subtitle_max_freq, maxMhz),
                    progress = pct,
                    showProgress = true
                )
            }
        }

        // Per cluster / Per core 切换
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            FilterChip(selected = selectedView == 0, onClick = { selectedView = 0 },
                label = { Text(stringResource(R.string.cpu_chip_per_cluster)) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NeonPurple.copy(alpha = 0.2f), selectedLabelColor = NeonPurple))
            Spacer(Modifier.width(8.dp))
            FilterChip(selected = selectedView == 1, onClick = { selectedView = 1 },
                label = { Text(stringResource(R.string.cpu_chip_per_core)) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NeonPurple.copy(alpha = 0.2f), selectedLabelColor = NeonPurple))
        }

        if (cores.isEmpty()) {
            Text(stringResource(R.string.common_waiting_data), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (selectedView == 0) {
            // Per cluster — 按 maxFreq 分组，使用真实频率历史
            coreGroups.values.sortedByDescending { it.first().maxFreqKHz }.forEach { group ->
                val maxFreq = group.first().maxFreqKHz / 1000
                val clusterMaxFreq = group.first().maxFreqKHz.toFloat()
                val clusterType = group.first().coreType.ifEmpty { null }
                ClusterCard(
                    name = group.first().coreCluster.ifEmpty {
                        when { maxFreq > 2500 -> "Prime"; maxFreq > 1800 -> "Performance"; else -> "Efficiency" }
                    },
                    subtitle = "${group.size} cores · max ${maxFreq} MHz${if (clusterType != null) " · $clusterType" else ""}",
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
                        Text(stringResource(R.string.cpu_cluster_card_title, groupIdx + 1, group.size), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
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
    val freqColor = if (!core.online) NeonMagenta.copy(alpha = 0.5f) else NeonPurpleBright
    Card(Modifier.fillMaxWidth().padding(horizontal = 2.dp), shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.cpu_core_title, core.coreIndex), fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                if (!core.online) Text(stringResource(R.string.cpu_core_off), fontSize = 11.sp, color = NeonMagenta)
                else if (!core.usagePercent.isNaN())
                    Text("%.0f%%".format(core.usagePercent), fontSize = 11.sp, color = NeonCyan)
            }
            Spacer(Modifier.height(4.dp))
            Text("${core.currentFreqKHz / 1000} MHz", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = freqColor)
            if (core.coreType.isNotEmpty()) {
                Text(core.coreType, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(stringResource(R.string.cpu_core_max_governor, core.maxFreqKHz / 1000, core.governor ?: "-"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── C-State descriptions (localized via string resources) ──
private fun cStateDesc(context: android.content.Context, name: String, level: Int): String = when {
    name.contains("WFI", ignoreCase = true) || level == 1 -> context.getString(R.string.cpu_cstate_wfi)
    name.contains("C1", ignoreCase = true) -> context.getString(R.string.cpu_cstate_c1)
    name.contains("C2", ignoreCase = true) -> context.getString(R.string.cpu_cstate_c2)
    name.contains("C3", ignoreCase = true) -> context.getString(R.string.cpu_cstate_c3)
    name.contains("retention", ignoreCase = true) || name.contains("Ret", ignoreCase = true) -> context.getString(R.string.cpu_cstate_retention)
    name.contains("power collapse", ignoreCase = true) || name.contains("Collapse", ignoreCase = true) -> context.getString(R.string.cpu_cstate_power_collapse)
    name.contains("Deep", ignoreCase = true) || name.contains("deep", ignoreCase = true) -> context.getString(R.string.cpu_cstate_deep)
    name.contains("cluster", ignoreCase = true) -> context.getString(R.string.cpu_cstate_cluster)
    name.contains("idle", ignoreCase = true) -> context.getString(R.string.cpu_cstate_idle)
    else -> when (level) {
        0 -> context.getString(R.string.cpu_cstate_default_0)
        1 -> context.getString(R.string.cpu_cstate_default_1)
        2 -> context.getString(R.string.cpu_cstate_default_2)
        3 -> context.getString(R.string.cpu_cstate_default_3)
        4 -> context.getString(R.string.cpu_cstate_default_4)
        else -> context.getString(R.string.cpu_cstate_default_n, level)
    }
}

private fun normalizeChartData(points: List<HistoryDataPoint>?, maxValue: Float): List<Float> {
    if (points.isNullOrEmpty()) return List(15) { 0f }
    val recent = points.takeLast(20)
    return if (maxValue > 0) recent.map { (it.value / maxValue).coerceIn(0f, 1f) } else recent.map { it.value }
}
