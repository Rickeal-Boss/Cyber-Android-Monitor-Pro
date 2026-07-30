package com.rb.cybermonitorpro.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rb.cybermonitorpro.AppSettings
import com.rb.cybermonitorpro.FormatUtils
import com.rb.cybermonitorpro.ui.components.charts.ChartUtils
import com.rb.cybermonitorpro.HapticUtils
import com.rb.cybermonitorpro.R
import com.rb.cybermonitorpro.data.model.HistoryDataPoint
import com.rb.cybermonitorpro.data.repository.HealthTracker.SourceHealth
import com.rb.cybermonitorpro.ui.components.InfoCard
import com.rb.cybermonitorpro.ui.components.MetricCard
import com.rb.cybermonitorpro.ui.components.charts.LineChart
import com.rb.cybermonitorpro.ui.effects.cardEdgeGlow
import com.rb.cybermonitorpro.ui.effects.entranceReveal
import com.rb.cybermonitorpro.ui.effects.revealLight
import com.rb.cybermonitorpro.ui.effects.staggeredSwipe
import com.rb.cybermonitorpro.ui.theme.*
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

private val DefaultSourceHealth = SourceHealth()

@Composable
fun DashboardScreen(
    onNavigate: (Int) -> Unit,
    viewModel: DashboardViewModel = koinViewModel()
) {
    val cpuInfo by viewModel.cpuInfo.observeAsState()
    val batteryInfo by viewModel.batteryInfo.observeAsState()
    val memoryInfo by viewModel.memoryInfo.observeAsState()
    val historyData by viewModel.historyData.observeAsState(emptyMap())
    val sourceHealth by viewModel.sourceHealth.observeAsState(DefaultSourceHealth)
    val systemInfo by viewModel.systemInfo.observeAsState()

    val deviceName = cpuInfo?.architecture?.let { "$it · ${cpuInfo?.coreCount ?: 0}${stringResource(R.string.dashboard_core_suffix)}" } ?: stringResource(R.string.common_detecting)
    val cpuTemp = cpuInfo?.temperatureCelsius?.let { if (it.isNaN()) "---" else "%.1f°C".format(it) } ?: "---"
    val batteryLevel = batteryInfo?.levelPercent?.let { "${it}%" } ?: "---"
    val batteryTemp = batteryInfo?.temperatureCelsius?.let { if (it.isNaN()) "---" else "%.1f°C".format(it) } ?: "---"
    val memUsed = memoryInfo?.let { FormatUtils.formatBytes(it.usedKB * 1024) } ?: "---"
    val memTotal = memoryInfo?.let { FormatUtils.formatBytes(it.totalKB * 1024) } ?: "---"
    // SWAP/ZRAM 数据（利用内存卡片下部空间）
    val swapUsedKB = memoryInfo?.swapUsedKB?.takeIf { it > 0 } ?: 0L
    val swapTotalKB = memoryInfo?.swapTotalKB?.takeIf { it > 0 } ?: 0L
    val zramUsedKB = memoryInfo?.zramMemUsedTotalKB?.takeIf { it > 0 }
        ?: memoryInfo?.zramCompressedKB?.takeIf { it > 0 } ?: 0L
    val zramOriginalKB = memoryInfo?.zramOriginalKB?.takeIf { it > 0 } ?: 0L
    val hasSwapZram = (swapTotalKB > 0 || zramOriginalKB > 0)
    // 取较大者作�? "SWAP/ZRAM in use" 的主展示值（swap 优先�?
    val swapzramUsedKB = if (swapUsedKB >= zramUsedKB) swapUsedKB else zramUsedKB
    val swapzramTotalKB = if (swapUsedKB >= zramUsedKB) swapTotalKB else zramOriginalKB
    val swapzramPct = if (swapzramTotalKB > 0 && swapzramUsedKB > 0)
        (swapzramUsedKB.toFloat() / swapzramTotalKB).coerceIn(0f, 1f) else -1f
    // 实时开机时�? (每分钟刷�?)
    var liveUptime by remember { mutableStateOf(android.os.SystemClock.elapsedRealtime() / 1000) }
    LaunchedEffect(Unit) {
        while (true) {
            liveUptime = android.os.SystemClock.elapsedRealtime() / 1000
            delay(30_000L)
        }
    }
    val uptimeStr = buildUptimeString(liveUptime)
    // 深度待机累计时长
    val deepSleepSec = systemInfo?.deepSleepSeconds?.takeIf { it > 0 }
    val deepSleepTimeStr = deepSleepSec?.let { buildUptimeString(it) } ?: ""

    val ctx = LocalContext.current

    // ── 卡片排序 (可拖拽重�?) ──
    val appSettings = AppSettings.getInstance(ctx)
    val reorderEnabled = appSettings.dashboardReorderEnabled
    var metricOrder by remember { mutableStateOf(resolveCardOrder(appSettings.metricCardOrder, METRIC_CARD_IDS)) }
    var quickOrder by remember { mutableStateOf(resolveCardOrder(appSettings.quickCardOrder, QUICK_CARD_IDS)) }
    val onMetricReorder: (List<String>) -> Unit = { newOrder ->
        metricOrder = newOrder
        appSettings.metricCardOrder = newOrder.joinToString(",")
    }
    val onQuickReorder: (List<String>) -> Unit = { newOrder ->
        quickOrder = newOrder
        appSettings.quickCardOrder = newOrder.joinToString(",")
    }

    // 预计算：内存进度（供 MetricCardByType �? ID 渲染�?
    val memProgress = if (memoryInfo?.totalKB ?: 0L > 0)
        (memoryInfo!!.usedKB.toFloat() / memoryInfo!!.totalKB).coerceIn(0f, 1f) else -1f


    // �? 图表缓存: 避免每次重组重算 normalizeChartData
    val cpuTempChart by remember(historyData) { derivedStateOf { ChartUtils.normalizeChartData(historyData["cpu_temp"], 100f) } }
    val ramChart by remember(historyData) { derivedStateOf { ChartUtils.normalizeChartData(historyData["ram_usage"], 100f) } }
    val gpuLoadChart by remember(historyData) { derivedStateOf { ChartUtils.normalizeChartData(historyData["gpu_load"], 100f) } }
    val gpuLoadText by remember(historyData) { derivedStateOf { gpuLoad(historyData) } }

    // Pre-compute string resources for use in non-composable lambdas (e.g. buildString)
    val uptimePrefix = stringResource(R.string.dashboard_uptime_prefix)
    val deepSleepPrefix = stringResource(R.string.dashboard_deep_sleep_prefix)
    val chargingStr = stringResource(R.string.battery_status_charging)
    val pluggedNotChargingStr = stringResource(R.string.battery_status_plugged_not_charging)
    val dischargingStr = stringResource(R.string.battery_status_discharging)

    // 电池副标题：按充电状态组合（供电池指标卡 subtitle 使用�?
    val batterySubtitle = buildString {
        when {
            batteryInfo?.isPlugged == true && batteryInfo?.isCharging == true -> append(chargingStr)
            batteryInfo?.isPlugged == true -> append(pluggedNotChargingStr)
            else -> append(dischargingStr)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── 设备信息卡片 (开机时�? + 深度待机) ──
        InfoCard(
            modifier = Modifier.staggeredSwipe(0).entranceReveal(0),
            title = deviceName,
            subtitle = FormatUtils.joinNonBlank("  ·  ",
                "$uptimePrefix $uptimeStr",
                if (deepSleepTimeStr.isNotEmpty()) "$deepSleepPrefix $deepSleepTimeStr" else null
            ),
            icon = Icons.Filled.Home, iconTint = NeonPurple
        )

        // ── 数据源健康指示条 ──
        DataSourceHealthBar(sourceHealth, Modifier.entranceReveal(1))

        // ── 分割�? ──
        HorizontalDivider(thickness = 1.dp, color = NeonPurpleDeep.copy(alpha = 0.3f))

        // ── 2×2 实时指标网格 (可拖拽重�?) ──
        ReorderableCardGrid(
            getItems = { metricOrder },
            onReorder = onMetricReorder,
            enabled = reorderEnabled,
            keyOf = { it }
        ) { id, handleMod ->
            Box(Modifier.fillMaxWidth().staggeredSwipe(1 + metricOrder.indexOf(id).coerceAtLeast(0)).entranceReveal(1 + metricOrder.indexOf(id).coerceAtLeast(0))) {
                MetricCardByType(
                    id = id,
                    cpuTemp = cpuTemp, cpuTempChart = cpuTempChart,
                    memUsed = memUsed, memTotal = memTotal, memProgress = memProgress,
                    swapzramUsedKB = swapzramUsedKB, swapzramTotalKB = swapzramTotalKB, swapzramPct = swapzramPct,
                    hasSwapZram = hasSwapZram,
                    batteryLevel = batteryLevel, batterySubtitle = batterySubtitle, batteryTemp = batteryTemp,
                    gpuLoadText = gpuLoadText, gpuLoadChart = gpuLoadChart
                )
                if (reorderEnabled) {
                    ReorderHandle(Modifier.align(Alignment.TopEnd).padding(2.dp), handleMod)
                }
            }
        }

        // ── 分割�? ──
        HorizontalDivider(thickness = 1.dp, color = NeonPurpleDeep.copy(alpha = 0.3f))

        // ── 快速访�? (可拖拽重�?) ──
        Text(stringResource(R.string.dashboard_quick_access), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)

        ReorderableCardGrid(
            getItems = { quickOrder },
            onReorder = onQuickReorder,
            enabled = reorderEnabled,
            keyOf = { it }
        ) { id, handleMod ->
            Box(Modifier.fillMaxWidth().staggeredSwipe(1 + metricOrder.size + quickOrder.indexOf(id).coerceAtLeast(0)).entranceReveal(1 + metricOrder.size + quickOrder.indexOf(id).coerceAtLeast(0))) {
                QuickLinkByType(id = id, onNavigate = onNavigate, memUsed = memUsed, memTotal = memTotal)
                if (reorderEnabled) {
                    ReorderHandle(Modifier.align(Alignment.TopEnd).padding(2.dp), handleMod)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

// ── 快速访问卡片组�? ──
@Composable
private fun QuickLinkCard(
    title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .revealLight(radius = 140.dp, intensity = 0.18f)
            .cardEdgeGlow()
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

// ── 概览页卡片可拖拽重排：常量与解析 ──
private val METRIC_CARD_IDS = listOf("cpu_temp", "mem_usage", "battery_level", "gpu_load")
private val QUICK_CARD_IDS = listOf("cpu", "gpu", "mem", "net", "gps", "device", "battery", "sensor")
private val QUICK_NAV = mapOf(
    "cpu" to 1, "gpu" to 2, "mem" to 3, "net" to 5,
    "gps" to 6, "device" to 8, "battery" to 4, "sensor" to 7
)

/**
 * 将持久化的逗号分隔顺序解析为有效卡片有序列表：
 * - 保留已存顺序中的已知 ID
 * - 追加存储中缺失的�? ID（版本增减卡片时不丢、不崩）
 * - 剔除未知 ID
 * - 存储为空/非法时回落默认序
 */
private fun resolveCardOrder(stored: String, validIds: List<String>): List<String> {
    val storedList = stored.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    val kept = storedList.filter { it in validIds }
    val missing = validIds.filter { it !in kept }
    return if (kept.isEmpty()) validIds else kept + missing
}

/**
 * 可重�? 2 列网格�?
 * enabled=true：挂 ReorderableItem + 拖拽手柄（闭包内 getItems() 始终读取最新顺序，避免捕获旧快照）�?
 * enabled=false：回落普�? LazyVerticalGrid（静态，便于特性开关一键回滚）�?
 */
@Composable
private fun ReorderableCardGrid(
    getItems: () -> List<String>,
    onReorder: (List<String>) -> Unit,
    enabled: Boolean,
    keyOf: (String) -> String,
    itemContent: @Composable (String, Modifier) -> Unit,
) {
    // �? 崩溃修复 (HCP-5): 静�? 2 列网�?(userScrollEnabled=false)必须处于有限高度约束下�?
    //   外层 DashboardScreen �? Column(Modifier.verticalScroll), 会向子节点传 maxHeight=Infinity;
    //   LazyVerticalGrid 即便禁用滚动也过不了 checkScrollableContainerConstraints �? �?
    //   IllegalStateException("measured with an infinity maximum height")，即�? vivo 真机"启动即闪退"根因�?
    //   这里用基�? item 数的安全上限约束 maxHeight（网格仍按自身内容自适应高度，不拉伸/不截断）�?
    val itemCount = getItems().size
    val rowCount = (itemCount + 1) / 2
    // 避免 maxOf(本构建链曾因 kotlin.math.maxOf 解析失败 CI #568): 手写非负守卫
    val rowSpacing = if (rowCount > 1) (rowCount - 1) * 16 else 0
    val gridMaxHeight = (rowCount * 400 + rowSpacing).dp

    if (enabled) {
        val gridState = rememberLazyGridState()
        val reorderState = rememberReorderableLazyGridState(gridState) { from, to ->
            val list = getItems().toMutableList()
            list.add(to.index, list.removeAt(from.index))
            onReorder(list)
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            userScrollEnabled = false,
            modifier = Modifier.fillMaxWidth().heightIn(max = gridMaxHeight),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(getItems(), key = keyOf) { item ->
                ReorderableItem(reorderState, key = keyOf(item)) {
                    // 拖拽手柄 Modifier 必须�? ReorderableItem 作用域内计算
                    // （draggableHandle �? ReorderableCollectionItemScope 的成员，非顶层函数）
                    val ctx = LocalContext.current
                    val handleModifier = Modifier.draggableHandle(
                        onDragStarted = { HapticUtils.dragStart(ctx) },
                        onDragStopped = { HapticUtils.dragEnd(ctx) }
                    )
                    itemContent(item, handleModifier)
                }
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = rememberLazyGridState(),
            userScrollEnabled = false,
            modifier = Modifier.fillMaxWidth().heightIn(max = gridMaxHeight),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(getItems(), key = keyOf) { item -> itemContent(item, Modifier) }
        }
    }
}

/** 拖拽手柄（仅 enabled 时显示），绑定拾�?/落下震动反馈�?
 *  handleModifier �? ReorderableItem 作用域内计算�? draggableHandle 传入�? */
@Composable
private fun ReorderHandle(modifier: Modifier, handleModifier: Modifier) {
    IconButton(
        onClick = {},
        modifier = modifier
            .size(28.dp)
            .then(handleModifier)
    ) {
        Icon(
            Icons.Filled.DragHandle,
            stringResource(R.string.dashboard_reorder_handle),
            tint = TextSecondary.copy(alpha = 0.7f),
            modifier = Modifier.size(18.dp)
        )
    }
}

/** �? ID 渲染对应�? 2×2 实时指标�? */
@Composable
private fun MetricCardByType(
    id: String,
    cpuTemp: String, cpuTempChart: List<Float>,
    memUsed: String, memTotal: String, memProgress: Float,
    swapzramUsedKB: Long, swapzramTotalKB: Long, swapzramPct: Float, hasSwapZram: Boolean,
    batteryLevel: String, batterySubtitle: String, batteryTemp: String,
    gpuLoadText: String, gpuLoadChart: List<Float>,
) {
    val memValueColor = NeonPurpleBright
    when (id) {
        "cpu_temp" -> MetricCard(
            title = stringResource(R.string.dashboard_metric_cpu_temp), value = cpuTemp,
            valueColor = NeonPurpleBright, modifier = Modifier.fillMaxWidth()
        ) { LineChart(data = cpuTempChart, modifier = Modifier.fillMaxWidth()) }

        "mem_usage" -> MetricCard(
            title = stringResource(R.string.dashboard_metric_mem_usage), value = memUsed,
            valueColor = memValueColor, modifier = Modifier.fillMaxWidth(), subtitle = "/ $memTotal",
            progress = memProgress, showProgress = true
        ) {
            if (hasSwapZram) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(thickness = 0.5.dp, color = CyberMuted.copy(alpha = 0.4f))
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.memory_swap_zram_title),
                    fontSize = 11.sp, color = TextSecondary.copy(alpha = 0.7f), letterSpacing = 0.5.sp)
                Spacer(Modifier.height(3.dp))
                val szText = FormatUtils.formatBytes(swapzramUsedKB * 1024)
                Text("$szText ${stringResource(R.string.common_in_use)}",
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    color = memValueColor, fontFamily = FontFamily.Monospace)
                if (swapzramPct >= 0f) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { swapzramPct },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = memValueColor.copy(alpha = 0.75f), trackColor = CyberMuted
                    )
                }
                val szTotalText = FormatUtils.formatBytes(swapzramTotalKB * 1024)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.memory_swap_total, szTotalText),
                    fontSize = 11.sp, color = TextSecondary.copy(alpha = 0.6f))
            }
        }

        "battery_level" -> MetricCard(
            title = stringResource(R.string.dashboard_metric_battery_level), value = batteryLevel,
            valueColor = SuccessNeon, modifier = Modifier.fillMaxWidth(), subtitle = batterySubtitle
        ) {
            // 利用卡片下部剩余空间显示电池温度（与 mem_usage �? SWAP/ZRAM 区块同构�?
            if (batteryTemp != "---") {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(thickness = 0.5.dp, color = CyberMuted.copy(alpha = 0.4f))
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.dashboard_metric_battery_temp),
                    fontSize = 11.sp, color = TextSecondary.copy(alpha = 0.7f), letterSpacing = 0.5.sp)
                Spacer(Modifier.height(3.dp))
                Text(batteryTemp,
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    color = SuccessNeon, fontFamily = FontFamily.Monospace)
            }
        }

        "gpu_load" -> MetricCard(
            title = stringResource(R.string.dashboard_metric_gpu_load), value = gpuLoadText,
            valueColor = NeonPurpleBright, modifier = Modifier.fillMaxWidth()
        ) { LineChart(data = gpuLoadChart, modifier = Modifier.fillMaxWidth()) }
    }
}

private data class QuickMeta(
    val title: String, val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tint: androidx.compose.ui.graphics.Color, val nav: Int
)

/** �? ID 渲染对应的快速访问卡（点击导航，拖拽手柄独立处理排序�? */
@Composable
private fun QuickLinkByType(
    id: String, onNavigate: (Int) -> Unit, memUsed: String, memTotal: String
) {
    val ctx = LocalContext.current
    val meta = when (id) {
        "cpu" -> QuickMeta(stringResource(R.string.dashboard_quick_cpu_title), stringResource(R.string.dashboard_quick_cpu_desc), Icons.Filled.PlayArrow, NeonPurple, 1)
        "gpu" -> QuickMeta(stringResource(R.string.dashboard_quick_gpu_title), stringResource(R.string.dashboard_quick_gpu_desc), Icons.Filled.Info, NeonPurpleBright, 2)
        "mem" -> QuickMeta(stringResource(R.string.dashboard_quick_mem_title), "$memUsed / $memTotal", Icons.Filled.Star, NeonPurple, 3)
        "net" -> QuickMeta(stringResource(R.string.dashboard_quick_net_title), stringResource(R.string.dashboard_quick_net_desc), Icons.Filled.Share, NeonPurpleBright, 5)
        "gps" -> QuickMeta(stringResource(R.string.dashboard_quick_gps_title), stringResource(R.string.dashboard_quick_gps_desc), Icons.Filled.PlayArrow, NeonMagenta, 6)
        "device" -> QuickMeta(stringResource(R.string.dashboard_quick_device_title), stringResource(R.string.dashboard_quick_device_desc), Icons.Filled.Search, SuccessNeon, 8)
        "battery" -> QuickMeta(stringResource(R.string.dashboard_quick_battery_title), stringResource(R.string.dashboard_quick_battery_desc), Icons.Filled.BatteryFull, NeonPurple, 4)
        "sensor" -> QuickMeta(stringResource(R.string.dashboard_quick_sensor_title), stringResource(R.string.dashboard_quick_sensor_desc), Icons.Filled.Sensors, NeonPurpleBright, 7)
        else -> QuickMeta(id, "", Icons.Filled.Info, NeonPurple, 0)
    }
    QuickLinkCard(
        meta.title, meta.subtitle, meta.icon, meta.tint,
        Modifier.fillMaxWidth().clickable { HapticUtils.standardTap(ctx); onNavigate(meta.nav) }
    )
}

private fun gpuLoad(historyData: Map<String, List<HistoryDataPoint>>): String {
    val pts = historyData["gpu_load"]
    if (pts.isNullOrEmpty()) return "---"
    val last = pts.lastOrNull()?.value ?: return "---"
    return "${last.toInt()}%"
}

// normalizeChartData 已迁移到 ChartUtils.kt �? 全局共享，消�? 6 份重复定�?

// ── 数据源健康状态指示条 ──
@Composable
private fun DataSourceHealthBar(health: SourceHealth, modifier: Modifier = Modifier) {
    if (health.allHealthy) return // 全部正常则不显示

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CyberPill)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("\u26A0", fontSize = 13.sp)
        Text(
            stringResource(R.string.dashboard_source_error, health.errorCount),
            fontSize = 12.sp,
            color = WarningNeon
        )

        Spacer(Modifier.weight(1f))

        // 枚举所有数据源的状态点
        val sources = listOf(
            "CPU" to health.cpu,
            "GPU" to health.gpu,
            "BAT" to health.battery,
            "RAM" to health.memory,
            "IO" to health.storage,
            "WiFi" to health.wifi,
            "4G" to health.mobileNetwork,
            "IF" to health.networkInterface,
            "SYS" to health.system,
            "SNS" to health.sensors,
            "DEV" to health.deviceDetail,
            "OEM" to health.oem
        )
        sources.forEach { (label, h) ->
            val color = when (h) {
                SourceHealth.Health.ERROR -> ErrorNeon
                SourceHealth.Health.WARN -> WarningNeon
                SourceHealth.Health.OK -> SuccessNeon
                else -> SuccessNeon
            }
            Box(
                Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        }
    }
}

private fun buildUptimeString(seconds: Long): String {
    if (seconds <= 0) return ""
    val days = seconds / 86400
    val hours = (seconds % 86400) / 3600
    val minutes = (seconds % 3600) / 60
    return when {
        days > 0 -> "${days}d ${hours}h ${minutes}m"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}
