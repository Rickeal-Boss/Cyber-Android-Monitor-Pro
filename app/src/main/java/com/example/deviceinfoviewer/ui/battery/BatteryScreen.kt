package com.example.deviceinfoviewer.ui.battery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.deviceinfoviewer.R
import com.example.deviceinfoviewer.data.model.HistoryDataPoint
import com.example.deviceinfoviewer.ui.components.InfoCard
import com.example.deviceinfoviewer.ui.components.MetricCard
import com.example.deviceinfoviewer.ui.components.charts.LineChart
import com.example.deviceinfoviewer.ui.theme.NeonPurple
import com.example.deviceinfoviewer.ui.theme.NeonPurpleBright
import com.example.deviceinfoviewer.ui.theme.SuccessNeon
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

    val level = batteryInfo?.levelPercent?.takeIf { it >= 0 }
    val isCharging = batteryInfo?.isCharging ?: false
    val temp = batteryInfo?.temperatureCelsius?.takeIf { !it.isNaN() }
    val voltage = batteryInfo?.voltage?.takeIf { it > 0 }
    val effVoltage = batteryInfo?.effectiveVoltage?.takeIf { it > 0 }
    val current = batteryInfo?.currentNowUA?.takeIf { it != 0L }
    val currentSource = batteryInfo?.currentNowSource?.takeIf { it.isNotEmpty() }
    val power = if (isCharging) batteryInfo?.chargingPowerMw else batteryInfo?.dischargingPowerMw
    val designCap = batteryInfo?.chargeFullDesignMAh?.takeIf { it > 0 }
    val nowCap = batteryInfo?.chargeFullMAh?.takeIf { it > 0 }
    val capSource = batteryInfo?.chargeFullSource?.takeIf { it.isNotEmpty() && it != "无法获取" }
    val counter = batteryInfo?.chargeCounterUAh?.takeIf { it > 0 }
    val cycleCount = batteryInfo?.cycleCount?.takeIf { it >= 0 }
    val cycleSource = batteryInfo?.cycleCountSource?.takeIf { it.isNotEmpty() && it != "无法获取" }
    val health = batteryInfo?.health?.takeIf { it.isNotEmpty() } ?: stringResource(R.string.battery_health_unknown)
    val apiSohPercent = batteryInfo?.healthPercent?.takeIf { it in 1..100 }
    val technology = batteryInfo?.technology?.takeIf { it.isNotEmpty() }
    val chargerType = batteryInfo?.chargerType?.takeIf { it.isNotEmpty() }
    val chargerFromPlug = batteryInfo?.chargerTypeFromPlugged?.takeIf { it.isNotEmpty() }
    val isPlugged = batteryInfo?.isPlugged ?: false
    val internalR = batteryInfo?.internalResistanceMOhm?.takeIf { !it.isNaN() && it > 0 }
    val protocolDetected = batteryInfo?.protocolDetected?.takeIf { it.isNotEmpty() }
    val powerSourceLabel = batteryInfo?.powerSourceLabel?.takeIf { it.isNotEmpty() }
    val wattageNow = batteryInfo?.wattageNow?.takeIf { !it.isNaN() && it > 0 }
    val currentNormalizedMa = batteryInfo?.currentNormalizedMa?.takeIf { it != 0 }

    val battTempChart = normalizeChartData(historyData["battery_temp"], 60f)
    val battLevelChart = normalizeChartData(historyData["battery_level"], 100f)
    val battPowerChart = normalizeChartData(historyData["battery_power"], 30000f)

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
        val techText = buildString {
            if (technology != null) append(technology)
            if (chargerFromPlug != null && isPlugged) append("  |  $chargerFromPlug")
            if (chargerType != null && chargerType != chargerFromPlug) append("  |  $chargerType")
        }

        InfoCard(
            title = statusText,
            subtitle = techText.ifEmpty { batteryInfo?.chargeStatus?.takeIf { it.isNotEmpty() } ?: "" },
            icon = Icons.Filled.Favorite, iconTint = NeonPurple
        )

        // === 电池健康度 (SOH: charge_full 计算 + 标准 API 双源) ===
        val healthPercent = if (designCap != null && nowCap != null && designCap > 0) {
            (nowCap * 100 / designCap).toInt()
        } else apiSohPercent

        if (healthPercent != null) {
            val sohSource = if (designCap != null && nowCap != null && designCap > 0) stringResource(R.string.battery_soh_source_capacity_ratio) else stringResource(R.string.battery_soh_source_standard_api)
            MetricCard(
                title = "Battery health",
                value = "$healthPercent%",
                valueColor = when {
                    healthPercent >= 90 -> NeonPurpleBright
                    healthPercent >= 75 -> Color(0xFFFFA726)
                    else -> Color(0xFFEF5350)
                },
                subtitle = "${nowCap ?: "?"} / ${designCap} mAh  ·  $sohSource"
            ) { }
        }

        // === 电池容量详情 ===
        if (nowCap != null || designCap != null) {
            MetricCard(
                title = "Battery capacity",
                value = "${nowCap ?: "?"} mAh",
                valueColor = NeonPurpleBright,
                subtitle = buildString {
                    if (designCap != null) append(stringResource(R.string.battery_design_capacity_format, designCap))
                    if (capSource != null) append("  ·  $capSource")
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
                title = "Cycle count",
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
                title = "Cycle count",
                value = stringResource(R.string.battery_cycle_not_detected),
                valueColor = Color(0xFFFFA726),
                subtitle = stringResource(R.string.battery_cycle_no_data)
            ) { }
        }

        // === 充电协议检测 (P1) ===
        if (protocolDetected != null) {
            MetricCard(
                title = "Charging protocol",
                value = protocolDetected,
                valueColor = SuccessNeon
            ) { }
        }

        // === 电源来源标签 (2026-06-18) ===
        if (powerSourceLabel != null) {
            val psColor = when {
                powerSourceLabel.contains("AC") -> SuccessNeon
                powerSourceLabel.contains("USB") || powerSourceLabel.contains("无线") -> NeonPurpleBright
                else -> Color(0xFFFFA726)
            }
            MetricCard(
                title = "Power source",
                value = powerSourceLabel,
                valueColor = psColor
            ) { }
        }

        // === 预计算实时瓦特数 (2026-06-18) ===
        if (wattageNow != null) {
            MetricCard(
                title = "Real wattage",
                value = "%.2f W".format(wattageNow),
                valueColor = if (isCharging) SuccessNeon else NeonPurpleBright
            ) { }
        }

        // === 电池内阻 (P2) ===
        if (internalR != null) {
            MetricCard(
                title = "Internal resistance",
                value = "%.0f mΩ".format(internalR),
                valueColor = NeonPurpleBright,
                subtitle = if (internalR < 100) stringResource(R.string.battery_resistance_excellent) else if (internalR < 200) stringResource(R.string.battery_resistance_good) else stringResource(R.string.battery_resistance_average)
            ) { }
        }

        // === 电量趋势图 ===
        if (level != null) {
            MetricCard(
                title = "Battery level",
                value = "${level}%",
                valueColor = NeonPurpleBright
            ) {
                LineChart(data = battLevelChart, modifier = Modifier.fillMaxWidth())
            }
        }

        // === 充放电功率 ===
        if (power != null && power > 0) {
            MetricCard(
                title = if (isCharging) "Charging power" else "Discharge power",
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
                title = if (isCharging) "Charging current" else "Discharge current",
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
                title = "Real-time power",
                value = "%.2f W".format(realTimePowerW),
                valueColor = if (isCharging) SuccessNeon else NeonPurpleBright,
                subtitle = "%.3fV × %.0fmA = %.0fmW".format(voltageV, currentA * 1000, realTimePowerW * 1000)
            ) { }
        }

        // === 电压 ===
        if (voltage != null) {
            MetricCard(
                title = "Battery voltage",
                value = "%.3f V".format(voltage / 1000f),
                valueColor = NeonPurpleBright
            ) { }
        }

        // === 已充电量 ===
        if (counter != null) {
            MetricCard(
                title = "Charge counter",
                value = "${counter / 1000} mAh",
                valueColor = NeonPurpleBright
            ) { }
        }

        // === 电池温度 ===
        if (temp != null) {
            MetricCard(
                title = "Battery temperature",
                value = "${temp.toInt()}°C",
                valueColor = NeonPurpleBright
            ) {
                LineChart(data = battTempChart, modifier = Modifier.fillMaxWidth())
            }
        }

        // === 电池状态 ===
        MetricCard(
            title = "Health",
            value = health,
            valueColor = NeonPurpleBright
        ) { }
    }
}

private fun normalizeChartData(points: List<HistoryDataPoint>?, maxValue: Float): List<Float> {
    if (points.isNullOrEmpty()) return List(15) { 0f }
    val recent = points.takeLast(20)
    return if (maxValue > 0) recent.map { (it.value / maxValue).coerceIn(0f, 1f) } else recent.map { it.value }
}
