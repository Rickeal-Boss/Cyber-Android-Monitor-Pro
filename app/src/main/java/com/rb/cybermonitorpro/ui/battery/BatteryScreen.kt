package com.rb.cybermonitorpro.ui.battery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
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
import com.rb.cybermonitorpro.ui.components.CyberIcons
import com.rb.cybermonitorpro.ui.components.FancySlider
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableFloatStateOf
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
import com.rb.cybermonitorpro.ui.components.CyberJoystickSwitch
import com.rb.cybermonitorpro.ui.effects.batteryTempBorderColor
import com.rb.cybermonitorpro.ui.effects.staggeredSwipe
import com.rb.cybermonitorpro.ui.theme.NeonPurple
import com.rb.cybermonitorpro.ui.theme.NeonPurpleBright
import com.rb.cybermonitorpro.ui.theme.NeonSteelBlue
import com.rb.cybermonitorpro.ui.theme.AmbientShadow
import com.rb.cybermonitorpro.ui.theme.SpotShadow
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
    val capSource = batteryInfo?.chargeFullSource?.takeIf { it.isNotEmpty() && it != "source_unavailable" }
    val counter = batteryInfo?.chargeCounterUAh?.takeIf { it > 0 }
    // 通过容量预估的电量百分比 (chargeCounter ÷ chargeFull)
    val estLevel = batteryInfo?.capacityEstimatedLevelPercent?.takeIf { it >= 0 }
    val cycleCount = batteryInfo?.cycleCount?.takeIf { it >= 0 }
    val cycleSource = batteryInfo?.cycleCountSource?.takeIf { it.isNotEmpty() && it != "source_unavailable" }
    val health = batteryInfo?.health?.takeIf { it.isNotEmpty() }?.let { key ->
        when (key) {
            "battery_health_good" -> stringResource(R.string.battery_health_good)
            "battery_health_overheat" -> stringResource(R.string.battery_health_overheat)
            "battery_health_dead" -> stringResource(R.string.battery_health_dead)
            "battery_health_overvoltage" -> stringResource(R.string.battery_health_overvoltage)
            "battery_health_failure" -> stringResource(R.string.battery_health_failure)
            "battery_health_cold" -> stringResource(R.string.battery_health_cold)
            "battery_health_unknown" -> stringResource(R.string.battery_health_unknown)
            else -> key
        }
    } ?: stringResource(R.string.battery_health_unknown)
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
    // 电流校准倍率总开关 state (关闭=不修正 1.0×, 开启=应用所选挡位)
    var multiplierEnabled by remember { mutableStateOf(appSettings.batteryCurrentMultiplierEnabled) }
    val onMultiplierEnabledChange: (Boolean) -> Unit = { value ->
        multiplierEnabled = value
        appSettings.batteryCurrentMultiplierEnabled = value
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
                enabled = multiplierEnabled,
                onEnabledChange = onMultiplierEnabledChange,
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
                        valueColor = NeonPurpleBright,
                        borderColor = batteryTempBorderColor(temp)
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
    // 滑动交错索引映射：固定状态概览 InfoCard 占 0，列表卡片依可视化顺序(含拖拽重排)从 1 递增
    val cardIndexById = visibleCards.mapIndexed { index, id -> id to (index + 1) }.toMap()
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
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // ── 状态概览 (固定头部, 不参与重排) ──
        InfoCard(
            modifier = Modifier.staggeredSwipe(0),
            title = statusText,
            subtitle = techText.ifEmpty {
                batteryInfo?.chargeStatus?.takeIf { it.isNotEmpty() }?.let { key ->
                    when (key) {
                        "battery_status_charging" -> stringResource(R.string.battery_status_charging)
                        "battery_status_discharging" -> stringResource(R.string.battery_status_discharging)
                        "battery_status_full" -> stringResource(R.string.battery_status_full)
                        "battery_status_not_charging" -> stringResource(R.string.battery_status_not_charging)
                        "battery_status_unknown" -> stringResource(R.string.battery_status_unknown)
                        else -> key
                    }
                } ?: ""
            },
            icon = CyberIcons.Favorite, iconTint = NeonPurple
        )

        // ── 可重排卡片区 (内层 LazyColumn, items 列表 == getVisibleItems()，索引与 onMove 对齐) ──
        val itemCount = visibleCards.size
        // 安全上限：按 item 数估算 (每卡 400dp 上限 + 18dp 间距)，仅作为有限高度约束防崩溃，
        // 实际高度由内容决定 (heightIn 为 max 约束，不会拉伸/留白)
        val listMaxHeight = (itemCount * 400 + (itemCount - 1) * 18).dp
        LazyColumn(
            state = listState,
            userScrollEnabled = false,
            modifier = Modifier.fillMaxWidth().heightIn(max = listMaxHeight),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            items(visibleCards, key = { it }, contentType = { it }) { id ->
                // 按可视化顺序（含拖拽重排后的顺序）取交错索引
                // 防御: id 不在映射时退回 (visibleCards.size+1) 而非 0, 避免与概览 InfoCard(0) 重复索引
                val staggerModifier = Modifier.staggeredSwipe(cardIndexById[id] ?: (visibleCards.size + 1))
                if (reorderEnabled) {
                    ReorderableItem(reorderState, key = id) {
                        // 拖拽手柄 Modifier 必须在 ReorderableItem 作用域内计算
                        val ctx = LocalContext.current
                        val handleModifier = Modifier.draggableHandle(
                            onDragStarted = { HapticUtils.dragStart(ctx) },
                            onDragStopped = { HapticUtils.dragEnd(ctx) }
                        )
                        Box(Modifier.fillMaxWidth().then(staggerModifier)) {
                            CardContent(id)
                            ReorderHandle(Modifier.align(Alignment.TopEnd).padding(2.dp), handleModifier)
                        }
                    }
                } else {
                    Box(staggerModifier) {
                        CardContent(id)
                    }
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
            CyberIcons.DragHandle,
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
 * 双电芯手动开关卡片 — 复用全局霓虹卡片容器 (釉影 + CardGradient)。
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

            .shadow(elevation = 2.dp, shape = RoundedCornerShape(20.dp), ambientColor = AmbientShadow, spotColor = SpotShadow),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(CardGradient)
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
                CyberJoystickSwitch(
                    checked = checked,
                    onCheckedChange = onCheckedChange
                )
            }
        }
    }
}

/** 电池电流校准固定挡位 — 原 0.1 步进滑块改 4 挡 (2026-08-06) */
private val CURRENT_MULTIPLIER_TIERS = listOf(1.0, 10.0, 100.0, 1000.0)

/**
 * ★ 倍率值 → 最近挡位索引 (2026-08-06)。
 * 用「最近」而非「精确相等」，兼容旧版本 0.1 步进遗留的非挡位值（如 2.5×），
 * 避免 indexOfFirst 返回 -1 后一律落到索引 0 造成滑块位置失真。
 */
private fun multiplierToTierIndex(value: Double): Int {
    var best = 0
    for (i in CURRENT_MULTIPLIER_TIERS.indices) {
        if (kotlin.math.abs(CURRENT_MULTIPLIER_TIERS[i] - value) <
            kotlin.math.abs(CURRENT_MULTIPLIER_TIERS[best] - value)
        ) best = i
    }
    return best
}

/**
 * ★ 滑块浮点索引 → 最近整数挡位索引 (2026-08-06)。
 * 用 (raw + 0.5f).toInt() 实现四舍五入：raw 恒 >= 0，截断即等价 round-half-up，
 * 避免引入 kotlin.math.roundToInt 顶层导入（本项目历史上顶层 math 导入曾致 release 编译失败）。
 */
private fun snapTierIndex(raw: Float): Int =
    (raw + 0.5f).toInt().coerceIn(0, CURRENT_MULTIPLIER_TIERS.size - 1)

/**
 * 电池电流校准倍率卡片 (PlusPlusBattery 思路: 用户校准则准)。
 * 默认 1.0× = 不修正；ColorOS 等 OEM ROM 的 oplus_chg / property 读数常因单位或增益偏差
 * 偏大/偏小，由用户在此按真实值校正。范围由 AppSettings 钳制 [1.0, 1000.0]。
 * ★ Slider 改固定挡位吸附（1.0×/10.0×/100.0×/1000.0×），预设按钮同 4 挡；Reset 归位 1.0×。
 * ★ 2026-08-06 交互对齐设置页 ModuleIntervalCard：
 *   原方案 steps=2 硬吸附 + onValueChange 每帧回写 AppSettings（无自由滑动手感、拖拽期高频写 SP）；
 *   现方案 steps=0 自由滑动 → 本地 state 跟手 → onValueChangeFinished 松手才 snap + 一次性写入。
 */
@Composable
private fun BatteryCurrentMultiplierCard(
    multiplier: Double,
    onMultiplierChange: (Double) -> Unit,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    title: String,
    subtitle: String,
    resetLabel: String
) {
    // ★ 本地滑块索引（浮点，自由滑动）。key = multiplier：预设/重置按钮改值时自动重新对齐滑块位置；
    //   拖拽期间不写 AppSettings，multiplier 不变 → remember 不重建 → 手感连续无回弹。
    var sliderIndex by remember(multiplier) { mutableFloatStateOf(multiplierToTierIndex(multiplier).toFloat()) }
    var isDragging by remember { mutableStateOf(false) }
    // 拖拽中头部显示「待确认挡位」实时预览，松手后回到实际生效值（兼容旧版遗留非挡位值）
    // ★ 总开关关闭时强制显示 1.0× (不修正); 拖拽实时预览仅在开启时生效
    val displayMultiplier = if (!enabled) {
        1.0
    } else if (isDragging) {
        CURRENT_MULTIPLIER_TIERS[snapTierIndex(sliderIndex)]
    } else {
        multiplier
    }

    Card(
        modifier = Modifier.fillMaxWidth()
            .shadow(elevation = 2.dp, shape = RoundedCornerShape(20.dp), ambientColor = AmbientShadow, spotColor = SpotShadow),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardGradient)
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
                    // ★ 右分组: 当前生效倍率 + 赛博摇杆开关 (复用 CyberJoystickSwitch 样式)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "×${"%.1f".format(displayMultiplier)}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (enabled) NeonPurpleBright else TextSecondary.copy(alpha = 0.5f)
                        )
                        CyberJoystickSwitch(
                            checked = enabled,
                            onCheckedChange = onEnabledChange
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                // ★ 自由滑动（steps=0）：valueRange=0..3 映射 4 挡，松手时 snap 到最近挡位
                FancySlider(
                    value = sliderIndex,
                    onValueChange = {
                        isDragging = true
                        sliderIndex = it
                    },
                    onValueChangeFinished = {
                        // ★ 松手时 snap + 一次性写入 AppSettings（拖拽全程零 SP 写入）
                        val idx = snapTierIndex(sliderIndex)
                        sliderIndex = idx.toFloat()
                        isDragging = false
                        onMultiplierChange(CURRENT_MULTIPLIER_TIERS[idx])
                    },
                    valueRange = 0f..(CURRENT_MULTIPLIER_TIERS.size - 1).toFloat(),
                    steps = 0,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                    // 原版已选段用 NeonPurpleBright 高亮，保持一致
                    activeTrackColor = NeonPurpleBright,
                )
                // ★ 2026-08-06 字号缩小：14sp (M3 TextButton 默认 labelLarge) → 12sp。
                //   同时预设按钮改 weight(1f) 均分剩余宽度 —— M3 Button 内部 Row 带
                //   defaultMinSize(ButtonDefaults.MinWidth = 58.dp)，4 个预设(含 "1000.0×")
                //   + Reset("Reset 1.0×" 英文更长) 在 300dp 可用行宽下会溢出被裁剪；
                //   weight 给出固定宽约束 (hasFixedWidth) 后内部 min-width 自动失效，
                //   布局层面杜绝溢出，极窄屏最坏只是 maxLines=1 省略号。
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CURRENT_MULTIPLIER_TIERS.forEach { preset ->
                        TextButton(
                            onClick = { onMultiplierChange(preset) },
                            enabled = enabled,
                            modifier = Modifier.weight(1f).defaultMinSize(minWidth = 1.dp, minHeight = 32.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)
                        ) {
                            Text(
                                "${"%.1f".format(preset)}×",
                                fontSize = 12.sp,
                                maxLines = 1,
                                color = if (displayMultiplier == preset) NeonPurpleBright else TextSecondary
                            )
                        }
                    }
                    TextButton(
                        onClick = { onMultiplierChange(1.0) },
                        enabled = enabled,
                        modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 32.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                    ) {
                        Text(resetLabel, fontSize = 12.sp, maxLines = 1, color = NeonPurpleBright)
                    }
                }
            }
        }
    }
}

// normalizeChartData 已迁移到 ChartUtils.kt — 全局共享，消除 6 份重复定义
