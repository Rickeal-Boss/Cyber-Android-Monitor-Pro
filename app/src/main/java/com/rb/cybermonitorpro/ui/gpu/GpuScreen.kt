package com.rb.cybermonitorpro.ui.gpu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rb.cybermonitorpro.data.model.HistoryDataPoint
import com.rb.cybermonitorpro.R
import com.rb.cybermonitorpro.FormatUtils
import com.rb.cybermonitorpro.ui.components.charts.ChartUtils
import com.rb.cybermonitorpro.ui.components.CyberIcons
import com.rb.cybermonitorpro.ui.components.InfoCard
import com.rb.cybermonitorpro.ui.components.MetricCard
import com.rb.cybermonitorpro.ui.components.charts.LineChart
import com.rb.cybermonitorpro.ui.theme.NeonPurple
import com.rb.cybermonitorpro.ui.theme.NeonPurpleBright
import com.rb.cybermonitorpro.ui.theme.WarningNeon
import com.rb.cybermonitorpro.ui.effects.staggeredSwipe
import com.rb.cybermonitorpro.ui.theme.SuccessNeon
import org.koin.androidx.compose.koinViewModel

/**
 * GPU 屏幕 — DVFS 感知版：频率/负载/温度/节流/调速器
 */
@Composable
fun GpuScreen(
    viewModel: GpuViewModel = koinViewModel()
) {
    val gpuInfo by viewModel.gpuInfo.observeAsState()
    val historyData by viewModel.historyData.observeAsState(emptyMap())

    val model = gpuInfo?.model ?: stringResource(R.string.common_detecting)
    val renderer = gpuInfo?.renderer?.takeIf { it.isNotEmpty() }
    val frequency = gpuInfo?.frequencyKHz?.let { if (it > 0) "${it / 1000} MHz" else null } ?: "---"
    val maxFreq = gpuInfo?.maxFreqKHz?.let { if (it > 0) "${it / 1000} MHz" else null }
    val load = gpuInfo?.loadPercentage?.let { if (!it.isNaN()) "${it.toInt()}%" else null } ?: "---"
    val loadSource = gpuInfo?.loadSource?.takeIf { it.isNotEmpty() }
    val temp = gpuInfo?.temperatureCelsius?.let { if (!it.isNaN()) "%.1f°C".format(it) else null } ?: "---"
    val governor = gpuInfo?.governor?.takeIf { it.isNotEmpty() }
    val isThrottled = gpuInfo?.isThrottled ?: false
    val effectiveUtil = gpuInfo?.effectiveUtilization
    val vulkanApi = gpuInfo?.vulkanApiVersion?.takeIf { it.isNotEmpty() }
    val vulkanDriver = gpuInfo?.vulkanDriverVersion?.takeIf { it.isNotEmpty() }
    val vulkanDeviceType = gpuInfo?.vulkanDeviceType?.takeIf { it.isNotEmpty() }
    val vulkanSource = gpuInfo?.vulkanSource?.takeIf { it.isNotEmpty() }

    val gpuLoadChart by remember(historyData) { derivedStateOf { ChartUtils.normalizeChartData(historyData["gpu_load"], 100f) } }
    val gpuTempChart by remember(historyData) { derivedStateOf { ChartUtils.normalizeChartData(historyData["gpu_temp"], 100f) } }
    val gpuFreqChart by remember(historyData) { derivedStateOf { ChartUtils.normalizeChartData(historyData["gpu_freq"], 100f) } }

    // 滑动交错索引：自上而下递增，条件块卡片均基于此累加
    var cardIdx = 0

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        val subtitle = FormatUtils.joinNonBlank(" · ",
            frequency,
            maxFreq?.let { "/ $it" },
            governor
        )
        InfoCard(
            modifier = Modifier.staggeredSwipe(cardIdx++),
            title = model,
            subtitle = subtitle,
            icon = CyberIcons.Info,
            iconTint = if (isThrottled) WarningNeon else NeonPurple
        )

        // DVFS 节流警告
        if (isThrottled) {
            Text(
                stringResource(R.string.gpu_throttle_warning),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = WarningNeon
            )
        }

        // GPU 负载 + 有效利用率
        MetricCard(modifier = Modifier.staggeredSwipe(cardIdx++), title = "GPU load", value = load, valueColor = NeonPurpleBright,
            subtitle = loadSource ?: "") {
            if (effectiveUtil != null && !effectiveUtil.isNaN()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.gpu_effective_util_label), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("%.0f%%".format(effectiveUtil), fontSize = 12.sp, color = SuccessNeon)
                }
            }
            LineChart(data = gpuLoadChart, modifier = Modifier.fillMaxWidth())
        }

        // GPU 频率 (DVFS 感知)
        MetricCard(
            modifier = Modifier.staggeredSwipe(cardIdx++),
            title = "GPU frequency (DVFS)",
            value = frequency,
            valueColor = if (isThrottled) WarningNeon else NeonPurpleBright,
            subtitle = maxFreq?.let {
                if (isThrottled) stringResource(R.string.gpu_subtitle_max_throttled, it)
                else stringResource(R.string.gpu_subtitle_max_normal, it)
            } ?: ""
        ) {
            LineChart(data = gpuFreqChart, modifier = Modifier.fillMaxWidth())
        }

        // GPU 温度
        MetricCard(modifier = Modifier.staggeredSwipe(cardIdx++), title = "GPU temperature", value = temp, valueColor = NeonPurpleBright) {
            LineChart(data = gpuTempChart, modifier = Modifier.fillMaxWidth())
        }

        // 调速器信息
        if (governor != null) {
            MetricCard(
                modifier = Modifier.staggeredSwipe(cardIdx++),
                title = "Governor",
                value = governor,
                valueColor = NeonPurpleBright,
                subtitle = gpuInfo?.availableGovernors?.takeIf { it.isNotEmpty() } ?: ""
            )
        }

        // 渲染器
        if (renderer != null) {
            MetricCard(
                modifier = Modifier.staggeredSwipe(cardIdx++),
                title = "Renderer",
                value = renderer,
                valueColor = NeonPurpleBright
            )
        }

        // Vulkan 驱动版本信息
        if (vulkanDriver != null) {
            val vulkanVerParts = vulkanDriver.split(".")
            val displayVer = when {
                vulkanVerParts.size >= 4 -> "${vulkanVerParts[0]}.${vulkanVerParts[1]}.${vulkanVerParts[2]} (build ${vulkanVerParts.subList(3, vulkanVerParts.size).joinToString(".")})"
                vulkanVerParts.size == 3 -> stringResource(R.string.gpu_vulkan_ver_3parts, vulkanVerParts[0].toIntOrNull() ?: 0, vulkanVerParts[1].toIntOrNull() ?: 0, vulkanVerParts[2].toIntOrNull() ?: 0)
                else -> vulkanDriver
            }
            MetricCard(
                modifier = Modifier.staggeredSwipe(cardIdx++),
                title = "Vulkan Driver Version",
                value = displayVer,
                valueColor = SuccessNeon,
                subtitle = vulkanDeviceType?.let { key ->
                    when (key) {
                        "gpu_integrated" -> stringResource(R.string.gpu_integrated)
                        "gpu_discrete" -> stringResource(R.string.gpu_discrete)
                        else -> key
                    }
                } ?: ""
            )
        }
    }
}

// normalizeChartData 已迁移到 ChartUtils.kt — 全局共享，消除 6 份重复定义
