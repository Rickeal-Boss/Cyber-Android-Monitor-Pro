package com.example.deviceinfoviewer.ui.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.deviceinfoviewer.FormatUtils
import com.example.deviceinfoviewer.R
import com.example.deviceinfoviewer.data.model.HistoryDataPoint
import com.example.deviceinfoviewer.ui.components.MemoryDistributionCard
import com.example.deviceinfoviewer.ui.components.MetricCard
import com.example.deviceinfoviewer.ui.components.charts.LineChart
import com.example.deviceinfoviewer.ui.theme.NeonPurpleBright
import org.koin.androidx.compose.koinViewModel

@Composable
fun MemoryScreen(
    viewModel: MemoryViewModel = koinViewModel()
) {
    val memoryInfo by viewModel.memoryInfo.observeAsState()
    val historyData by viewModel.historyData.observeAsState(emptyMap())

    val totalKB = memoryInfo?.totalKB ?: -1L
    val usedKB = memoryInfo?.usedKB ?: -1L
    val availableKB = memoryInfo?.availableKB ?: -1L
    val swapTotalKB = memoryInfo?.swapTotalKB ?: -1L
    val swapUsedKB = memoryInfo?.swapUsedKB ?: -1L
    val zramUsed = memoryInfo?.zramMemUsedTotalKB ?: -1L
    val progress = if (totalKB > 0) usedKB.toFloat() / totalKB else 0f

    val ramChart = normalizeChartData(historyData["ram_usage"], 100f)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MetricCard(
            title = stringResource(R.string.memory_title),
            value = "${FormatUtils.formatBytes(availableKB * 1024)} ${stringResource(R.string.memory_available_suffix)}",
            valueColor = NeonPurpleBright,
            subtitle = "${FormatUtils.formatBytes(usedKB * 1024)} ${stringResource(R.string.memory_used_suffix)} / ${FormatUtils.formatBytes(totalKB * 1024)} ${stringResource(R.string.memory_total_prefix)}",
            progress = progress,
            showProgress = true
        )

        if (swapTotalKB > 0) {
            MetricCard(
                title = "SWAP / ZRAM",
                value = "${FormatUtils.formatBytes(swapUsedKB * 1024)} in use",
                valueColor = NeonPurpleBright,
                subtitle = "${stringResource(R.string.memory_total_prefix)}: ${FormatUtils.formatBytes(swapTotalKB * 1024)}",
                progress = swapUsedKB.toFloat() / swapTotalKB.coerceAtLeast(1),
                showProgress = true
            )
        }

        if (zramUsed > 0) {
            MetricCard(title = "ZRAM used", value = FormatUtils.formatBytes(zramUsed * 1024), valueColor = NeonPurpleBright) {
                LineChart(data = ramChart, modifier = Modifier.fillMaxWidth())
            }
        }

        // === 内存分布 (Memory Distribution) ===
        val mem = memoryInfo
        if (mem != null && mem.totalKB > 0) {
            MemoryDistributionCard(
                totalKB = mem.totalKB,
                appKB = mem.appMemoryKB,
                cachedKB = mem.cachedMemoryKB,
                systemKB = mem.systemMemoryKB,
                freeKB = mem.freeMemoryKB,
                otherKB = mem.otherMemoryKB
            )
        }

        MetricCard(title = "Memory available", value = FormatUtils.formatBytes(availableKB * 1024), valueColor = NeonPurpleBright) {
            LineChart(data = ramChart, modifier = Modifier.fillMaxWidth())
        }

        MetricCard(title = "Memory used", value = FormatUtils.formatBytes(usedKB * 1024), valueColor = NeonPurpleBright) {
            LineChart(data = ramChart, modifier = Modifier.fillMaxWidth())
        }

        // === P2: 进程统计 Top 5 ===
        val processes = memoryInfo?.topProcesses?.takeIf { it.isNotEmpty() }
        if (processes != null) {
            MetricCard(
                title = "Top processes",
                value = processes.joinToString("\n"),
                valueColor = NeonPurpleBright
            )
        }
    }
}

private fun normalizeChartData(points: List<HistoryDataPoint>?, maxValue: Float): List<Float> {
    if (points.isNullOrEmpty()) return List(15) { 0.5f }
    val recent = points.takeLast(20)
    return if (maxValue > 0) {
        recent.map { (it.value / maxValue).coerceIn(0f, 1f) }
    } else {
        recent.map { it.value }
    }
}
