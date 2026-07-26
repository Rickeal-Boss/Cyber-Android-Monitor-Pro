package com.rb.cybermonitorpro.ui.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rb.cybermonitorpro.FormatUtils
import com.rb.cybermonitorpro.R
import com.rb.cybermonitorpro.ui.components.charts.ChartUtils
import com.rb.cybermonitorpro.data.model.HistoryDataPoint
import com.rb.cybermonitorpro.ui.components.MemoryDistributionCard
import com.rb.cybermonitorpro.ui.components.MetricCard
import com.rb.cybermonitorpro.ui.components.charts.LineChart
import com.rb.cybermonitorpro.ui.theme.NeonPurpleBright
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

    // ★ 空数据填充 0.5f (中点) — 内存使用率默认为 50%，与其他模块的空填 0f 不同
    val ramChart by remember(historyData) { derivedStateOf { ChartUtils.normalizeChartData(historyData["ram_usage"], 100f, emptyFill = 0.5f) } }

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

// normalizeChartData 已迁移到 ChartUtils.kt — 全局共享，消除 6 份重复定义
