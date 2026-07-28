package com.rb.cybermonitorpro.ui.battery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rb.cybermonitorpro.R
import com.rb.cybermonitorpro.AppSettings
import com.rb.cybermonitorpro.FormatUtils
import com.rb.cybermonitorpro.HapticUtils
import com.rb.cybermonitorpro.ui.components.charts.ChartUtils
import com.rb.cybermonitorpro.data.model.HistoryDataPoint
import com.rb.cybermonitorpro.ui.components.InfoCard
import com.rb.cybermonitorpro.ui.components.MetricCard
import com.rb.cybermonitorpro.ui.components.charts.LineChart
import com.rb.cybermonitorpro.ui.components.CardGradient
import com.rb.cybermonitorpro.ui.components.hdrHighlight
import com.rb.cybermonitorpro.ui.effects.revealLight
import com.rb.cybermonitorpro.ui.theme.NeonPurple
import com.rb.cybermonitorpro.ui.theme.NeonPurpleBright
import com.rb.cybermonitorpro.ui.theme.NeonSteelBlue
import com.rb.cybermonitorpro.ui.theme.PurpleGlowLight
import com.rb.cybermonitorpro.ui.theme.SuccessNeon
import com.rb.cybermonitorpro.ui.theme.TextPrimary
import com.rb.cybermonitorpro.ui.theme.TextSecondary
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * 电池屏幕 — 增强版：展示循环次数、容量来源、充放电功率 + 实时图表
 * 卡片支持与概览页一致的手动拖拽重排 (dashboardReorderEnabled 总开关控制)。
 */
@Composable
fun BatteryScreen(
    viewModel: BatteryViewModel = koinViewModel()
) {
    val batteryInfo by viewModel.batteryInfo.observeAsState()
    val historyData by viewModel.historyData.observeAsState(emptyMap())

    // 双电芯手动开关 — 自动检测不可靠时的用户覆盖项
    val context = LocalContext.current
    val appSettings = AppSettings.getInstance(context)
    var dualCellEnabled by remember { mutableStateOf(appSettings.dualCellBattery) }

    val level = batteryInfo?.levelPercent?.takeIf { it >= 0 }
    val isCharging = batteryInfo?.isCharging ?: false
    val temp = batteryInfo?.temperatureCelsius?.takeIf { !it.isNaN() }
    val voltage = batteryInfo?.voltage?.takeIf { it > 0 }
    val effVoltage = batteryInfo?.effectiveVoltage?.takeIf { it > 0 }
    val current = batteryInfo?.currentNowUA?.takeIf { it != 0L }
    val currentSource = batteryInfo?.currentNowSource?.takeIf { it.isNotEmpty() }
    val power = if (isCharging) batteryInfo?.chargingPowerMw else batteryInfo?.dischargingPowerMw
    // 双电芯：容量随开关翻倍（effective getter，依赖 batteryInfo.dualCell，刷新后生效）
    val designCap = batteryInfo?.effectiveChargeFullDesignMAh?.takeIf { it > 0 }
    val nowCap = batteryInfo?.effectiveChargeFullMAh?.takeIf { it > 0 }
    val capSource = batteryInfo?.chargeFullSource?.takeIf { it.isNotEmpty() && it != "无法获取" }
    val counter = batteryInfo?.chargeCounterUAh?.takeIf { it > 0 }
    // 通过容量预估的电量百分比 (chargeCounter ÷ chargeFull)
    val estLevel = batteryInfo?.capacityEstimatedLevelPercent?.takeIf { it >= 0 }
    val cycleCount = batteryInfo?.cycleCount?.takeIf { it >= 0 }
    val cycleSource = batteryInfo?.cycleCountSource?.takeIf { it.isNotEmpty() && it != "无法获取" }
    val health = batteryInfo?.health?.takeIf { it.isNotEmpty() } ?: stringResource(R.string.battery_health_unknown)
    val apiSohPercent = batteryInfo?.healthPercent?.takeIf { it in 1..100 }
    val technology = batteryInfo?.technology?.takeIf { it.isNotEmpty() }
    val chargerType = batteryInfo?.chargerType?.takeIf { it.isNotEmpty() }
    val chargerFromPlug = batteryInfo?.chargerTypeFromPlugged?.takeIf { it.isNotEmpty() }
    // 充电类型语义 key → 翻译文本 (charger_ac/charger_usb/charger_wireless/charger_unknown)
    val chargerFromPlugText = when (chargerFromPlug) {
        "charger_ac" -> stringResource(R.string.charger_ac)
        "charger_usb" -> stringResource(R.string.charger_usb)
        "charger_wireless" -> stringResource(R.string.charger_wireless)
        "charger_unknown" -> stringResource(R.string.charger_unknown)
        else -> chargerFromPlug ?: ""  // fallback: 可能是原始值或空
    }
    val isPlugged = batteryInfo?.isPlugged ?: false
    val internalR = batteryInfo?.internalResistanceMOhm?.takeIf { !it.isNaN() && it > 0 }
    val protocolDetected = batteryInfo?.protocolDetected?.takeIf { it.isNotEmpty() }
    val powerSourceLabel = batteryInfo?.powerSourceLabel?.takeIf { it.isNotEmpty() }
    val wattageNow = batteryInfo?.wattageNow?.takeIf { !it.isNaN() && it > 0 }
    val currentNormalizedMa = batteryInfo?.currentNormalizedMa?.takeIf { it != 0 }

    // 健康度 (SOH): charge_full 计算 + 标准 API 双源 — 上提到作用域顶部，供可见性判断与卡片渲染共用
    val healthPercent = if (designCap != null && nowCap != null && designCap > 0) {
        (nowCap * 100 / designCap).toInt()
    } else apiSohPercent

    val battTempChart by remember(historyData) { derivedStateOf { ChartUtils.normalizeChartData(historyData["battery_temp"], 60f) } }
    val battLevelChart by remember(historyData) { derivedStateOf { ChartUtils.normalizeChartData(historyData["battery_level"], 100f) } }
    val battPowerChart by remember(historyData) { derivedStateOf { ChartUtils.normalizeChartData(historyData["battery_power"], 30000f) } }

    // ── 卡片排序 (可拖拽重排, 与概览页一致) ──
    val reorderEnabled = appSettings.dashboardReorderEnabled
    var cardOrder by remember { mutableStateOf(resolveBatteryCardOrder(appSettings.batteryCardOrder)) }

    val onReorder: (List<String>) -> Unit = { newOrder ->
        cardOrder = newOrder
        appSettings.batteryCardOrder = newOrder.joinToString(",")
    }

    // 电流校准倍率 controlled state
    var currentMultiplier by remember { mutableStateOf(appSettings.batteryCurrentMultiplier) }
    val onMultiplierChange: (Double) -> Unit = { value ->
        currentMultiplier = value
        appSettings.batteryCurrentMultiplier = value
    }
    val onDualCellChange: (Boolean) -> Unit = { enabled ->
        dualCellEnabled = enabled
        appSettings.dualCellBattery = enabled
        viewModel.refreshDualCell()
    }

    // 卡片可见性判定 (闭包读取最新数据快照；条件与下方 CardContent 的 if 守卫一一对应)
    val isCardVisible: (String) -> Boolean = { id ->
        when (id) {
            "current_multiplier" -> true
            "power_save" -> batteryInfo?.isPowerSaveMode == true
            "soh" -> healthPercent != null
            "design_capacity" -> designCap != null
            "rated_capacity" -> nowCap != null
            "cycle_count" -> true          // 无数据时也渲染 (not detected 占位)
            "protocol" -> protocolDetected != null
            "power_source" -> powerSourceLabel != null
            "wattage" -> wattageNow != null
            "internal_r" -> internalR != null
            "level_chart" -> level != null
            "power" -> power != null && power > 0
            "current" -> current != null
            "realtime_power" -> effVoltage != null && current != null
            "voltage" -> voltage != null
            "charge_counter" -> counter != null
            "temperature" -> temp != null
            "health_status" -> true
            "dual_cell" -> true
            else -> false
        }
    }
    val getVisibleItems = { cardOrder.filter { isCardVisible(it) } }

    // === 状态概览 (固定头部, 不参与重排) ===
    val chargingStr = stringResource(R.string.battery_status_charging)
    val pluggedNotChargingStr = stringResource(R.string.battery_status_not_charging)
    val dischargingStr = stringResource(R.string.battery_status_discharging)
    val statusText = buildString {
        if (isPlugged && isCharging) append(chargingStr)
        else if (isPlugged) append(pluggedNotChargingStr)
        else append(dischargingStr)
        if (level != null) append(" · ${level}%")
    }
    val techText = FormatUtils.joinNonBlank("  |  ",
        technology,
        if (chargerFromPlugText.isNotEmpty() && isPlugged) chargerFromPlugText else null,
        if (chargerType != null && chargerType != chargerFromPlug) chargerType else null
    )

    // 按 ID 渲染对应卡片 (条件项外层 if 守卫与 isCardVisible 一一对应，保证智能转换 + 渲染安全)
    @Composable
    fun CardContent(id: String) {
        when (id) {
            "current_multiplier" -> BatteryCurrentMultiplierCard(
                multiplier = currentMultiplier,
                onMultiplierChange = onMultiplierChange,
                title = stringResource(R.string.battery_current_multiplier_title),
                subtitle = stringResource(R.string.battery_current_multiplier_subtitle),
                resetLabel = stringResource(R.string.battery_current_multiplier_reset)
            )
            "power_save" -> {
                if (batteryInfo?.isPowerSaveMode == true) {
                    MetricCard(
                        title = stringResource(R.string.battery_card_power_save_mode),
                        value = "\uD83D\uDD0B ON",  // 电池图标
                        valueColor = Color(0xFFFFA726),  // 橙色警示
                        subtitle = "System performance throttled — refresh rate auto-reduced"
                    ) { }
                }
            }
            "soh" -> {
                if (healthPercent != null) {
                    val sohSource = if (designCap != null && nowCap != null && designCap > 0) stringResource(R.string.battery_soh_source_capacity_ratio) else stringResource(R.string.battery_soh_source_standard_api)
                    MetricCard(
                        title = stringResource(R.string.battery_health_title),
                        value = "$healthPercent%",
                        valueColor = when {
                            healthPercent >= 90 -> NeonPurpleBright
                            healthPercent >= 75 -> Color(0xFFFFA726)
                            else -> Color(0xFFEF5350)
                        },
                        subtitle = "${nowCap ?: "?"} / ${designCap} mAh  ·  $sohSource"
                    ) { }
                }
            }
            "design_capacity" -> {
                if (designCap != null) {
                    MetricCard(
                        title = stringResource(R.string.battery_design_capacity_title),
                        value = "$designCap mAh",
                        valueColor = NeonPurpleBright,
                        subtitle = capSource ?: ""
                    ) { }
                }
            }
            "rated_capacity" -> {
                if (nowCap != null) {
                    MetricCard(
                        title = stringResource(R.string.battery_rated_capacity_title),
                        value = "$nowCap mAh",
                        valueColor = NeonPurpleBright,
                        subtitle = buildString {
                            if (estLevel != null) append(stringResource(R.string.battery_capacity_estimate_format, estLevel))
                            if (capSource != null) {
                                if (estLevel != null) append("  ·  ")
                                append(capSource)
                            }
                        }
                    ) { }
                }
            }
            "cycle_count" -> {
                if (cycleCount != null) {
                    // 基于循环次数估算电池健康度（业界通用预估: 500次≈80%健康度）
                    val estHealth = when {
                        cycleCount == 0 -> 100
                        cycleCount <= 200 -> (100 - cycleCount / 10).coerceIn(85, 100)
                        cycleCount <= 500 -> (100 - cycleCount / 20).coerceIn(75, 90)
                        cycleCount <= 1000 -> (80 - (cycleCount - 500) / 25).coerceAtLeast(60)
                        else -> (60 - (cycleCount - 1000) / 50).coerceAtLeast(30)
                    }
                    MetricCard(
                        title = stringResource(R.string.battery_cycle_count_title),
                        value = stringResource(R.string.battery_cycle_value, cycleCount),
                        valueColor = NeonPurpleBright,
                        subtitle = buildString {
                            append(stringResource(R.string.battery_estimated_health_format, estHealth))
                            if (cycleSource != null) append("  |  $cycleSource")
                        }
                    ) { }
                } else {
                    // 循环次数不可用时给出提示
                    MetricCard(
                        title = stringResource(R.string.battery_cycle_count_title),
                        value = stringResource(R.string.battery_cycle_not_detected),
                        valueColor = Color(0xFFFFA726),
                        subtitle = stringResource(R.string.battery_cycle_no_data)
                    ) { }
                }
            }
            "protocol" -> {
                if (protocolDetected != null) {
                    MetricCard(
                        title = stringResource(R.string.battery_charging_protocol_title),
                        value = protocolDetected,
                        valueColor = SuccessNeon
                    ) { }
                }
            }
            "power_source" -> {
                if (powerSourceLabel != null) {
                    val psText = when (powerSourceLabel) {
                        "ps_ac" -> stringResource(R.string.ps_ac)
                        "ps_usb" -> stringResource(R.string.ps_usb)
                        "ps_wireless" -> stringResource(R.string.ps_wireless)
                        "ps_external" -> stringResource(R.string.ps_external)
                        "ps_battery" -> stringResource(R.string.ps_battery)
                        else -> powerSourceLabel  // fallback: 原样显示
                    }
                    val psColor = when (powerSourceLabel) {
                        "ps_ac", "charger_ac" -> SuccessNeon
                        "ps_usb", "ps_wireless", "charger_usb", "charger_wireless" -> NeonPurpleBright
                        else -> Color(0xFFFFA726)
                    }
                    MetricCard(
                        title = stringResource(R.string.battery_card_power_source),
                        value = psText,
                        valueColor = psColor
                    ) { }
                }
            }
            "wattage" -> {
                if (wattageNow != null) {
                    MetricCard(
                        title = stringResource(R.string.battery_card_real_wattage),
                        value = "%.2f W".format(wattageNow),
                        valueColor = if (isCharging) SuccessNeon else NeonPurpleBright
                    ) { }
                }
            }
            "internal_r" -> {
                if (internalR != null) {
                    MetricCard(
                        title = stringResource(R.string.battery_internal_resistance_title),
                        value = "%.0f mΩ".format(internalR),
                        valueColor = NeonPurpleBright,
                        subtitle = if (internalR < 100) stringResource(R.string.battery_resistance_excellent) else if (internalR < 200) stringResource(R.string.battery_resistance_good) else stringResource(R.string.battery_resistance_average)
                    ) { }
                }
            }
            "level_chart" -> {
                if (level != null) {
                    MetricCard(
                        title = stringResource(R.string.battery_level_title),
                        value = "${level}%",
                        valueColor = NeonPurpleBright
                    ) {
                        LineChart(data = battLevelChart, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            "power" -> {
                if (power != null && power > 0) {
                    MetricCard(
                        title = if (isCharging) stringResource(R.string.battery_charging_power_title) else stringResource(R.string.battery_discharge_power_title),
                        value = "${(power / 1000f).let { "%.1f".format(it) }} W",
                        valueColor = NeonPurpleBright
                    ) {
                        LineChart(data = battPowerChart, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            "current" -> {
                if (current != null) {
                    val normalizedInfo = if (currentNormalizedMa != null && currentNormalizedMa != 0) {
                        stringResource(R.string.battery_current_normalized_format, kotlin.math.abs(currentNormalizedMa))
                    } else null
                    MetricCard(
                        title = if (isCharging) stringResource(R.string.battery_charging_current_title) else stringResource(R.string.battery_discharge_current_title),
                        value = "${kotlin.math.abs(current / 1000)} mA",
                        valueColor = NeonPurpleBright,
                        subtitle = listOfNotNull(currentSource, normalizedInfo).joinToString("  ·  ")
                    ) { }
                }
            }
            "realtime_power" -> {
                if (effVoltage != null && current != null) {
                    val voltageV = effVoltage / 1000f
                    val currentA = kotlin.math.abs(current) / 1_000_000f
                    val realTimePowerW = voltageV * currentA
                    MetricCard(
                        title = stringResource(R.string.battery_realtime_power_title),
                        value = "%.2f W".format(realTimePowerW),
                        valueColor = if (isCharging) SuccessNeon else NeonPurpleBright,
                        subtitle = "%.3fV × %.0fmA = %.0fmW".format(voltageV, currentA * 1000, realTimePowerW * 1000)
                    ) { }
                }
            }
            "voltage" -> {
                if (voltage != null) {
                    MetricCard(
                        title = stringResource(R.string.battery_voltage_title),
                        value = "%.3f V".format(voltage / 1000f),
                        valueColor = NeonPurpleBright
                    ) { }
                }
            }
            "charge_counter" -> {
                if (counter != null) {
                    MetricCard(
                        title = stringResource(R.string.battery_charge_counter_title),
                        value = "${counter / 1000} mAh",
                        valueColor = NeonPurpleBright
                    ) { }
                }
            }
            "temperature" -> {
                if (temp != null) {
                    MetricCard(
                        title = stringResource(R.string.battery_temperature_title),
                        value = "%.1f°C".format(temp),
                        valueColor = NeonPurpleBright
                    ) {
                        LineChart(data = battTempChart, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            "health_status" -> MetricCard(
                title = stringResource(R.string.battery_health_status_title),
                value = health,
                valueColor = NeonPurpleBright
            ) { }
            "dual_cell" -> DualCellToggleCard(
                checked = dualCellEnabled,
                onCheckedChange = onDualCellChange,
                title = stringResource(R.string.battery_dual_cell_title),
                subtitle = stringResource(R.string.battery_dual_cell_subtitle)
            )
        }
    }

    val listState = rememberLazyListState()
    val visibleCards = getVisibleItems()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        // onMove 索引基于内层 LazyColumn 的 items 列表 (== visibleCards, 不含外层固定头部)，
        // 故 from/to 与 getVisibleItems() 天然对齐，无需偏移。
        val visible = getVisibleItems()
        if (visible.isEmpty()) return@rememberReorderableLazyListState
        // 防御：拖拽期间数据轮询可能改变可见集，clamp 避免 IndexOutOfBounds (原崩溃即此类越界)
        val fromIdx = from.index.coerceIn(0, visible.lastIndex)
        val toIdx = to.index.coerceIn(0, visible.size)
        if (fromIdx !in visible.indices) return@rememberReorderableLazyListState
        val newVisible = visible.toMutableList()
        newVisible.add(toIdx, newVisible.removeAt(fromIdx))
        // 合并回全量顺序：隐藏卡片保持原相对位置，仅可见卡片被重排
        val visibleSet = visible.toSet()
        val it = newVisible.iterator()
        val newFull = cardOrder.map { id -> if (id in visibleSet) it.next() else id }
        onReorder(newFull)
    }

    // 外层可滚动 Column：固定头部 + 可重排卡片列表（与概览页 ReorderableCardGrid 同构）。
    // 内层 LazyColumn 用 userScrollEnabled=false + heightIn(max) 上限，规避 infinity-max-height 崩溃。
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── 状态概览 (固定头部, 不参与重排) ──
        InfoCard(
            title = statusText,
            subtitle = techText.ifEmpty { batteryInfo?.chargeStatus?.takeIf { it.isNotEmpty() } ?: "" },
            icon = Icons.Filled.Favorite, iconTint = NeonPurple
        )

        // ── 可重排卡片区 (内层 LazyColumn, items 列表 == getVisibleItems()，索引与 onMove 对齐) ──
        val itemCount = visibleCards.size
        // 安全上限：按 item 数估算 (每卡 400dp 上限 + 12dp 间距)，仅作为有限高度约束防崩溃，
        // 实际高度由内容决定 (heightIn 为 max 约束，不会拉伸/留白)
        val listMaxHeight = (itemCount * 400 + (itemCount - 1) * 12).dp
        LazyColumn(
            state = listState,
            userScrollEnabled = false,
            modifier = Modifier.fillMaxWidth().heightIn(max = listMaxHeight),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(visibleCards, key = { it }, contentType = { it }) { id ->
                if (reorderEnabled) {
                    ReorderableItem(reorderState, key = id) {
                        // 拖拽手柄 Modifier 必须在 ReorderableItem 作用域内计算
                        val ctx = LocalContext.current
                        val handleModifier = Modifier.draggableHandle(
                            onDragStarted = { HapticUtils.dragStart(ctx) },
                            onDragStopped = { HapticUtils.dragEnd(ctx) }
                        )
                        Box(Modifier.fillMaxWidth()) {
                            CardContent(id)
                            ReorderHandle(Modifier.align(Alignment.TopEnd).padding(2.dp), handleModifier)
                        }
                    }
                } else {
                    CardContent(id)
                }
            }
        }
    }
}

/** 拖拽手柄（仅 enabled 时显示），绑定拾起/落下震动反馈。
 *  handleModifier 由 ReorderableItem 作用域内计算的 draggableHandle 传入。 */
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

/** 电池页卡片全量 ID 列表 (默认序 = 与原布局一致；状态概览为固定头部不参与)。 */
private val BATTERY_CARD_IDS = listOf(
    "current_multiplier",
    "power_save",
    "soh",
    "design_capacity",
    "rated_capacity",
    "cycle_count",
    "protocol",
    "power_source",
    "wattage",
    "internal_r",
    "level_chart",
    "power",
    "current",
    "realtime_power",
    "voltage",
    "charge_counter",
    "temperature",
    "health_status",
    "dual_cell"
)

/**
 * 将持久化的逗号分隔顺序解析为有效卡片有序列表：
 * - 保留已存顺序中的已知 ID
 * - 追加存储中缺失的新 ID（版本增减卡片时不丢、不崩）
 * - 剔除未知 ID
 * - 存储为空/非法时回落默认序
 */
private fun resolveBatteryCardOrder(stored: String): List<String> {
    val storedList = stored.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    val kept = storedList.filter { it in BATTERY_CARD_IDS }
    val missing = BATTERY_CARD_IDS.filter { it !in kept }
    return if (kept.isEmpty()) BATTERY_CARD_IDS else kept + missing
}

/**
 * 双电芯手动开关卡片 — 复用全局霓虹卡片容器 (revealLight + hdrHighlight + CardGradient)。
 * 开 = 测量电压翻倍 (effectiveVoltage)，用于双电芯机型准确计算功率。
 */
@Composable
private fun DualCellToggleCard(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    subtitle: String
) {
    Card(
        modifier = Modifier.fillMaxWidth()
            .revealLight(radius = 160.dp, intensity = 0.22f)
            .shadow(elevation = 10.dp, shape = RoundedCornerShape(12.dp), ambientColor = PurpleGlowLight),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CardGradient)
                .hdrHighlight(12.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.padding(end = 12.dp)) {
                    Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    if (subtitle.isNotBlank()) {
                        Text(
                            subtitle,
                            fontSize = 12.sp,
                            color = TextSecondary.copy(alpha = 0.75f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TextPrimary,
                        checkedTrackColor = NeonPurpleBright,
                        uncheckedThumbColor = NeonSteelBlue,
                        uncheckedTrackColor = NeonSteelBlue.copy(alpha = 0.2f),
                    )
                )
            }
        }
    }
}

/**
 * 电池电流校准倍率卡片 (PlusPlusBattery 思路: 用户校准则准)。
 * 默认 1.0× = 不修正；ColorOS 等 OEM ROM 的 oplus_chg / property 读数常因单位或增益偏差
 * 偏大/偏小，由用户在此按真实值校正。范围由 AppSettings 钳制 [0.1, 10.0]。
 * Slider 步进 0.1×；预设 0.5×/1.0×/2.0× 覆盖常见校正场景；Reset 归位 1.0×。
 */
@Composable
private fun BatteryCurrentMultiplierCard(
    multiplier: Double,
    onMultiplierChange: (Double) -> Unit,
    title: String,
    subtitle: String,
    resetLabel: String
) {
    Card(
        modifier = Modifier.fillMaxWidth()
            .revealLight(radius = 160.dp, intensity = 0.22f)
            .shadow(elevation = 10.dp, shape = RoundedCornerShape(12.dp), ambientColor = PurpleGlowLight),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardGradient).hdrHighlight(12.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.padding(end = 12.dp).weight(1f)) {
                        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        if (subtitle.isNotBlank()) {
                            Text(
                                subtitle,
                                fontSize = 12.sp,
                                color = TextSecondary.copy(alpha = 0.75f),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                    Text(
                        "×${"%.1f".format(multiplier)}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonPurpleBright
                    )
                }
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = multiplier.toFloat(),
                    onValueChange = { onMultiplierChange(it.toDouble()) },
                    valueRange = 0.1f..10.0f,
                    steps = 99,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = NeonPurpleBright,
                        activeTrackColor = NeonPurpleBright,
                        inactiveTrackColor = NeonSteelBlue.copy(alpha = 0.3f)
                    )
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(0.5, 1.0, 2.0).forEach { preset ->
                        TextButton(onClick = { onMultiplierChange(preset) }) {
                            Text("${"%.1f".format(preset)}×", color = TextSecondary)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { onMultiplierChange(1.0) }) {
                        Text(resetLabel, color = NeonPurpleBright)
                    }
                }
            }
        }
    }
}

// normalizeChartData 已迁移到 ChartUtils.kt — 全局共享，消除 6 份重复定义
