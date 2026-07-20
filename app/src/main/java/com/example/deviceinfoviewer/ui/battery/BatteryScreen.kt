package com.example.deviceinfoviewer.ui.battery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.example.deviceinfoviewer.R
import com.example.deviceinfoviewer.AppSettings
import com.example.deviceinfoviewer.FormatUtils
import com.example.deviceinfoviewer.ui.components.charts.ChartUtils
import com.example.deviceinfoviewer.data.model.HistoryDataPoint
import com.example.deviceinfoviewer.ui.components.InfoCard
import com.example.deviceinfoviewer.ui.components.MetricCard
import com.example.deviceinfoviewer.ui.components.charts.LineChart
import com.example.deviceinfoviewer.ui.components.CardGradient
import com.example.deviceinfoviewer.ui.components.hdrHighlight
import com.example.deviceinfoviewer.ui.effects.revealLight
import com.example.deviceinfoviewer.ui.theme.NeonPurple
import com.example.deviceinfoviewer.ui.theme.NeonPurpleBright
import com.example.deviceinfoviewer.ui.theme.NeonSteelBlue
import com.example.deviceinfoviewer.ui.theme.PurpleGlowLight
import com.example.deviceinfoviewer.ui.theme.SuccessNeon
import com.example.deviceinfoviewer.ui.theme.TextPrimary
import com.example.deviceinfoviewer.ui.theme.TextSecondary
import org.koin.androidx.compose.koinViewModel

/**
 * 电池屏幕 — 增强版：展示循环次数、容量来源、充放电功率 + 实时图表
 */
@Composable
fun BatteryScreen(
    viewModel: BatteryViewModel = koinViewModel()
) {
    val batteryInfo by viewModel.batteryInfo.observeAsState()
    val historyData by viewModel.historyData.observeAsState(emptyMap())

    // 双电芯手动开关 — 自动检测不可靠时的用户覆盖项
    val context = LocalContext.current
    var dualCellEnabled by remember { mutableStateOf(AppSettings.getInstance(context).dualCellBattery) }

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

    val battTempChart by remember(historyData) { derivedStateOf { ChartUtils.normalizeChartData(historyData["battery_temp"], 60f) } }
    val battLevelChart by remember(historyData) { derivedStateOf { ChartUtils.normalizeChartData(historyData["battery_level"], 100f) } }
    val battPowerChart by remember(historyData) { derivedStateOf { ChartUtils.normalizeChartData(historyData["battery_power"], 30000f) } }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // === 状态概览 ===
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

        InfoCard(
            title = statusText,
            subtitle = techText.ifEmpty { batteryInfo?.chargeStatus?.takeIf { it.isNotEmpty() } ?: "" },
            icon = Icons.Filled.Favorite, iconTint = NeonPurple
        )

        // === 双电芯手动开关 (自动检测不可靠时的用户覆盖项) ===
        DualCellToggleCard(
            checked = dualCellEnabled,
            onCheckedChange = { enabled ->
                dualCellEnabled = enabled
                AppSettings.getInstance(context).dualCellBattery = enabled
                viewModel.refreshDualCell()
            },
            title = stringResource(R.string.battery_dual_cell_title),
            subtitle = stringResource(R.string.battery_dual_cell_subtitle)
        )

        // === 系统省电模式 (PowerManager.isPowerSaveMode, API 21+) ===
        // 省电模式开启时系统会自动降频/限制后台，对游戏和性能影响显著
        if (batteryInfo?.isPowerSaveMode == true) {
            MetricCard(
                title = stringResource(R.string.battery_card_power_save_mode),
                value = "\uD83D\uDD0B ON",  // 电池图标
                valueColor = Color(0xFFFFA726),  // 橙色警示
                subtitle = "System performance throttled — refresh rate auto-reduced"
            ) { }
        }

        // === 电池健康度 (SOH: charge_full 计算 + 标准 API 双源) ===
        val healthPercent = if (designCap != null && nowCap != null && designCap > 0) {
            (nowCap * 100 / designCap).toInt()
        } else apiSohPercent

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

        // === 电池容量详情 (设计容量 / 额定容量) ===
        // 设计容量: charge_full_design (出厂标称)；额定容量: charge_full (当前满充)
        if (designCap != null) {
            MetricCard(
                title = stringResource(R.string.battery_design_capacity_title),
                value = "$designCap mAh",
                valueColor = NeonPurpleBright,
                subtitle = capSource ?: ""
            ) { }
        }

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

        // === 电池循环次数 ===
        if (cycleCount != null) {
            // 基于循环次数估算电池健康度（业界通用预估: 500次≈80%健康度）
            val estHealth = when {
                cycleCount == 0 -> 100
                cycleCount <= 200 -> (100 - cycleCount / 10).coerceIn(85, 100)
                cycleCount <= 500 -> (100 - cycleCount / 20).coerceIn(75, 90)
                cycleCount <= 1000 -> (80 - (cycleCount - 500) / 25).coerceAtLeast(60)
                else -> (60 - (cycleCount - 1000) / 50).coerceAtLeast(30)
            }
            val estHealthColor = when {
                estHealth >= 85 -> NeonPurpleBright
                estHealth >= 70 -> Color(0xFFFFA726)
                else -> Color(0xFFEF5350)
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

        // === 充电协议检测 (P1) ===
        if (protocolDetected != null) {
            MetricCard(
                title = stringResource(R.string.battery_charging_protocol_title),
                value = protocolDetected,
                valueColor = SuccessNeon
            ) { }
        }

        // === 电源来源标签 (2026-06-18) ===
        // 数据层返回语义 key (ps_ac/ps_usb/ps_wireless/ps_external/ps_battery)，UI 层翻译
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

        // === 预计算实时瓦特数 (2026-06-18) ===
        if (wattageNow != null) {
            MetricCard(
                title = stringResource(R.string.battery_card_real_wattage),
                value = "%.2f W".format(wattageNow),
                valueColor = if (isCharging) SuccessNeon else NeonPurpleBright
            ) { }
        }

        // === 电池内阻 (P2) ===
        if (internalR != null) {
            MetricCard(
                title = stringResource(R.string.battery_internal_resistance_title),
                value = "%.0f mΩ".format(internalR),
                valueColor = NeonPurpleBright,
                subtitle = if (internalR < 100) stringResource(R.string.battery_resistance_excellent) else if (internalR < 200) stringResource(R.string.battery_resistance_good) else stringResource(R.string.battery_resistance_average)
            ) { }
        }

        // === 电量趋势图 ===
        if (level != null) {
            MetricCard(
                title = stringResource(R.string.battery_level_title),
                value = "${level}%",
                valueColor = NeonPurpleBright
            ) {
                LineChart(data = battLevelChart, modifier = Modifier.fillMaxWidth())
            }
        }

        // === 充放电功率 ===
        if (power != null && power > 0) {
            MetricCard(
                title = if (isCharging) stringResource(R.string.battery_charging_power_title) else stringResource(R.string.battery_discharge_power_title),
                value = "${(power / 1000f).let { "%.1f".format(it) }} W",
                valueColor = NeonPurpleBright
            ) {
                LineChart(data = battPowerChart, modifier = Modifier.fillMaxWidth())
            }
        }

        // === 电流 ===
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

        // === 实时功率 (V × I) ===
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

        // === 电压 ===
        if (voltage != null) {
            MetricCard(
                title = stringResource(R.string.battery_voltage_title),
                value = "%.3f V".format(voltage / 1000f),
                valueColor = NeonPurpleBright
            ) { }
        }

        // === 已充电量 ===
        if (counter != null) {
            MetricCard(
                title = stringResource(R.string.battery_charge_counter_title),
                value = "${counter / 1000} mAh",
                valueColor = NeonPurpleBright
            ) { }
        }

        // === 电池温度 ===
        if (temp != null) {
            MetricCard(
                title = stringResource(R.string.battery_temperature_title),
                value = "${temp.toInt()}°C",
                valueColor = NeonPurpleBright
            ) {
                LineChart(data = battTempChart, modifier = Modifier.fillMaxWidth())
            }
        }

        // === 电池状态 ===
        MetricCard(
            title = stringResource(R.string.battery_health_status_title),
            value = health,
            valueColor = NeonPurpleBright
        ) { }
    }
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

// normalizeChartData 已迁移到 ChartUtils.kt — 全局共享，消除 6 份重复定义
