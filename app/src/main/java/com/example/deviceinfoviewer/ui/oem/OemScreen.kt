package com.example.deviceinfoviewer.ui.oem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.deviceinfoviewer.ui.components.hdrHighlight
import com.example.deviceinfoviewer.ui.theme.NeonPurple
import com.example.deviceinfoviewer.ui.theme.NeonPurpleBright
import com.example.deviceinfoviewer.ui.theme.CyberCardStart
import com.example.deviceinfoviewer.ui.theme.CyberCardEnd
import com.example.deviceinfoviewer.ui.theme.NeonCyan
import com.example.deviceinfoviewer.ui.theme.NeonMagenta
import com.example.deviceinfoviewer.ui.theme.SuccessNeon
import com.example.deviceinfoviewer.ui.theme.WarningNeon
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OemScreen(viewModel: OemViewModel = koinViewModel()) {
    val oem by viewModel.oemInfo.observeAsState()

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("系统识别", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

        val o = oem

        // ═══ 系统信息 ═══
        SectionCard("${o?.osName ?: "Android"} · ${o?.oem ?: ""}") {
            val androidLabel = o?.androidVersion?.let { v -> "Android $v (API ${o?.sdkLevel})" } ?: "检测中"
            RowItem("Android", androidLabel)
            RowItem("版本", o?.osVersion?.takeIf { it.isNotEmpty() } ?: "检测中")
            RowItem("Build", o?.buildDisplayId?.takeIf { it.isNotEmpty() } ?: "-")
            RowItem("安全补丁", o?.securityPatch?.takeIf { it.isNotEmpty() } ?: "-")
        }

        // ═══ SoC 信息 ═══
        SectionCard("芯片平台") {
            RowItem("平台", o?.boardPlatform?.takeIf { it.isNotEmpty() } ?: "-")
            RowItem("SoC 制造商", o?.socManufacturer?.takeIf { it.isNotEmpty() } ?: "-")
            RowItem("SoC 型号", o?.socModel?.takeIf { it.isNotEmpty() } ?: "-")
        }

        // ═══ 小米 HyperOS 专区 ═══
        if (o?.oem == "Xiaomi") {
            SectionCard("小米 HyperOS/MIUI") {
                RowItem("系统", o.osName.takeIf { it.isNotEmpty() } ?: "-")
                RowItem("版本", o.miuiVersion.takeIf { it.isNotEmpty() } ?: "-")
                RowItem("Region", o.miuiRegion.takeIf { it.isNotEmpty() } ?: "-")
                RowItem("Mod Device", o.miuiHardware.takeIf { it.isNotEmpty() } ?: "-")
            }

            // 自研芯片
            val hasXiaomiChips = o.xiaomiSurgeChip.isNotEmpty()
                || o.xiaomiPengpaiISP.isNotEmpty()
                || o.xiaomiSecurityChip.isNotEmpty()
            if (hasXiaomiChips) {
                SectionCard("自研芯片") {
                    RowItemWithColor("快充", o.xiaomiSurgeChip.ifEmpty { "-" }, if (o.xiaomiSurgeChip.isNotEmpty()) NeonCyan else MaterialTheme.colorScheme.onSurface)
                    RowItemWithColor("影像", o.xiaomiPengpaiISP.ifEmpty { "-" }, if (o.xiaomiPengpaiISP.isNotEmpty()) NeonCyan else MaterialTheme.colorScheme.onSurface)
                    RowItem("安全芯片", o.xiaomiSecurityChip.ifEmpty { "-" })
                }
            }

            // HyperOS 3.0 特性
            val hasHyperFeatures = o.hyperOsAIModel.isNotEmpty() || o.hyperOsCrossDevice.isNotEmpty() || o.hyperOsPerformanceGrade.isNotEmpty()
            if (hasHyperFeatures) {
                SectionCard("HyperOS 特性") {
                    RowItem("AI 大模型", o.hyperOsAIModel.ifEmpty { "-" })
                    RowItem("跨端互联", o.hyperOsCrossDevice.ifEmpty { "-" })
                    RowItemWithColor("性能评级", o.hyperOsPerformanceGrade.ifEmpty { "-" },
                        when {
                            o.hyperOsPerformanceGrade == "性能模式" -> NeonPurpleBright
                            o.hyperOsPerformanceGrade == "高性能模式" -> NeonMagenta
                            o.hyperOsPerformanceGrade == "省电模式" -> NeonCyan
                            o.hyperOsPerformanceGrade == "超级省电模式" -> NeonCyan
                            else -> MaterialTheme.colorScheme.onSurface
                        })
                }
            }

            if (o.miuiFeatures.isNotBlank()) {
                SectionCard("特性列表") {
                    TagFlow(o.miuiFeatures.split(" · "))
                }
            }
        }

        // ═══ OPPO ColorOS 专区 ═══
        if (o?.oem == "OPPO") {
            SectionCard("OPPO ColorOS") {
                RowItem("系统", o.osName.takeIf { it.isNotEmpty() } ?: "-")
                RowItem("版本", o.oppoColorOSVersion.ifEmpty { o.oppoVersion.ifEmpty { "-" } })
                RowItem("屏幕比例", o.oppoScreenRatio.takeIf { it.isNotEmpty() } ?: "-")
            }

            // 自研芯片/引擎
            val hasOppoChips = o.oppoMariSilicon.isNotEmpty()
                || o.oppoDCE.isNotEmpty()
                || o.oppoTrucoEngine.isNotEmpty()
            if (hasOppoChips) {
                SectionCard("自研芯片/引擎") {
                    RowItemWithColor("影像 NPU", o.oppoMariSilicon.ifEmpty { "-" }, if (o.oppoMariSilicon.isNotEmpty()) NeonCyan else MaterialTheme.colorScheme.onSurface)
                    RowItemWithColor("计算引擎", o.oppoDCE.ifEmpty { "-" }, if (o.oppoDCE.isNotEmpty()) NeonCyan else MaterialTheme.colorScheme.onSurface)
                    RowItemWithColor("游戏引擎", o.oppoTrucoEngine.ifEmpty { "-" }, if (o.oppoTrucoEngine.isNotEmpty()) NeonCyan else MaterialTheme.colorScheme.onSurface)
                }
            }

            // 内存融合
            if (o.oppoRAMPlus.isNotEmpty()) {
                SectionCard("内存融合 (RAM+)") {
                    RowItem("状态", o.oppoRAMPlus)
                }
            }

            // 充电信息 (保留)
            if (o.oplusCharging.isNotBlank()) {
                SectionCard("充电信息") {
                    TagFlow(o.oplusCharging.split(" · "))
                }
            }
        }

        // ═══ Vivo OriginOS 专区 ═══
        if (o?.oem == "Vivo") {
            SectionCard("Vivo OriginOS") {
                RowItem("系统", o.osName.takeIf { it.isNotEmpty() } ?: "-")
                RowItem("版本", o.vivoOriginOSVersion.ifEmpty { o.vivoOsVersion.ifEmpty { "-" } })
                RowItem("Solution", o.vivoProductSolution.takeIf { it.isNotEmpty() } ?: "-")
                RowItem("Model", o.vivoModel.takeIf { it.isNotEmpty() } ?: "-")
            }

            // 自研芯片
            if (o.vivoV3Chip.isNotEmpty()) {
                SectionCard("自研芯片") {
                    RowItemWithColor("影像芯片", o.vivoV3Chip, NeonCyan)
                }
            }

            // 内存融合
            if (o.vivoRAMFusion.isNotEmpty()) {
                SectionCard("内存融合") {
                    RowItem("状态", o.vivoRAMFusion)
                }
            }

            // 显示引擎
            if (o.vivoDisplayEngine.isNotEmpty()) {
                SectionCard("显示引擎") {
                    TagFlow(o.vivoDisplayEngine.split(" · "))
                }
            }
        }

        // ═══ 相机传感器 (v3 新增) ═══
        val hasCameraInfo = o?.cameraRearSensors?.isNotEmpty() == true
            || o?.cameraFrontSensor?.isNotEmpty() == true
            || o?.cameraSensorPhysicalSize?.isNotEmpty() == true
        if (hasCameraInfo) {
            SectionCard("相机传感器") {
                RowItem("后置", o?.cameraRearSensors?.ifEmpty { "-" } ?: "-")
                o?.cameraFrontSensor?.takeIf { it.isNotEmpty() }?.let { RowItem("前置", it) }
                RowItem("传感器尺寸", o?.cameraSensorPhysicalSize?.ifEmpty { "-" } ?: "-")
                o?.cameraRearAperture?.takeIf { it.isNotEmpty() }?.let { RowItem("后置光圈", it) }
                o?.cameraFrontAperture?.takeIf { it.isNotEmpty() }?.let { RowItem("前置光圈", it) }
                if (o?.cameraOpticalStabilization == true) {
                    RowItemWithColor("OIS", "光学防抖 ✓", SuccessNeon)
                }
                RowItem("闪光灯", o?.cameraFlashType?.ifEmpty { "无" } ?: "无")
                o?.cameraMaxZoom?.takeIf { it.isNotEmpty() }?.let { RowItem("变焦", it) }
            }
        }

        // ═══ 充电协议 (v3 新增) ═══
        val hasChargingInfo = o?.chargingProtocol?.isNotEmpty() == true
            || o?.chargingMaxWatt?.isNotEmpty() == true
            || o?.chargingBatteryCapacity?.isNotEmpty() == true
        if (hasChargingInfo) {
            SectionCard("充电协议") {
                RowItemWithColor("协议", o?.chargingProtocol?.ifEmpty { "-" } ?: "-",
                    if (o?.chargingProtocol?.isNotEmpty() == true) NeonCyan else MaterialTheme.colorScheme.onSurface)
                RowItemWithColor("最大功率", o?.chargingMaxWatt?.ifEmpty { "-" } ?: "-",
                    if (o?.chargingMaxWatt?.isNotEmpty() == true) SuccessNeon else MaterialTheme.colorScheme.onSurface)
                RowItem("电池容量", o?.chargingBatteryCapacity?.ifEmpty { "-" } ?: "-")
                if (o?.chargingDualCell == true) {
                    RowItemWithColor("电芯", "双电芯", NeonPurple)
                }
                o?.chargingWirelessPower?.takeIf { it.isNotEmpty() }?.let {
                    RowItemWithColor("无线充电", it, NeonCyan)
                }
            }
        }

        // ═══ 显示面板 (v3 新增) ═══
        val hasDisplayInfo = o?.displayPanelType?.isNotEmpty() == true
            || o?.displayPanelManufacturer?.isNotEmpty() == true
            || o?.displayMaxRefreshRate?.isNotEmpty() == true
        if (hasDisplayInfo) {
            SectionCard("显示面板") {
                RowItem("面板类型", o?.displayPanelType?.ifEmpty { "-" } ?: "-")
                RowItem("面板厂商", o?.displayPanelManufacturer?.ifEmpty { "-" } ?: "-")
                if (o?.displayLTPO == true) {
                    RowItemWithColor("变频", "LTPO 变频刷新", NeonCyan)
                }
                val refreshLabel = buildString {
                    o?.displayMinRefreshRate?.let { append(it, " ~ ") }
                    o?.displayMaxRefreshRate?.let { append(it) }
                }
                if (refreshLabel.isNotEmpty()) RowItem("刷新率", refreshLabel)
                RowItem("峰值亮度", o?.displayPeakBrightness?.ifEmpty { "-" } ?: "-")
                RowItem("HDR", o?.displayHDR?.ifEmpty { "-" } ?: "-")
            }
        }

        // ═══ 性能调度器 (v3 新增) ═══
        val hasGovernorInfo = o?.cpuGovernor?.isNotEmpty() == true
            || o?.gpuGovernor?.isNotEmpty() == true
        if (hasGovernorInfo) {
            SectionCard("性能调度器") {
                RowItem("CPU 调度器", o?.cpuGovernor?.ifEmpty { "-" } ?: "-")
                RowItem("GPU 调度器", o?.gpuGovernor?.ifEmpty { "-" } ?: "-")
                RowItem("热管理", o?.thermalGovernor?.ifEmpty { "-" } ?: "-")
            }
        }

        // ═══ 安全芯片 (v3 新增) ═══
        val hasSecurityInfo = o?.securityChip?.isNotEmpty() == true
            || o?.verifiedBootState?.isNotEmpty() == true
        if (hasSecurityInfo) {
            SectionCard("安全信息") {
                RowItem("安全芯片", o?.securityChip?.ifEmpty { "-" } ?: "-")
                if (o?.secureBoot == true) {
                    RowItemWithColor("安全启动", "已启用 ✓", SuccessNeon)
                }
                RowItem("验证启动", o?.verifiedBootState?.ifEmpty { "-" } ?: "-")
            }
        }

        // ═══ 子系统特性 ═══
        val hasSubFeatures = o?.aiEngineInfo?.isNotEmpty() == true
            || o?.memoryFusion?.isNotEmpty() == true
            || o?.thermalSolution?.isNotEmpty() == true
            || o?.storageBoost?.isNotEmpty() == true
            || o?.displayFeatures?.isNotEmpty() == true
        if (hasSubFeatures) {
            SectionCard("子系统特性") {
                o?.aiEngineInfo?.takeIf { it.isNotEmpty() }?.let { RowItem("AI 引擎", it) }
                o?.memoryFusion?.takeIf { it.isNotEmpty() }?.let { RowItem("内存融合", it) }
                o?.thermalSolution?.takeIf { it.isNotEmpty() }?.let { RowItem("散热方案", it) }
                o?.storageBoost?.takeIf { it.isNotEmpty() }?.let { RowItem("存储加速", it) }
                o?.displayFeatures?.takeIf { it.isNotEmpty() }?.let { RowItem("显示特性", it) }
            }
        }

        // ═══ 性能模式 ═══
        SectionCard("性能模式") {
            val gameColor = if (o?.gameModeSupported == true) SuccessNeon else WarningNeon
            RowItemWithColor("游戏模式", if (o?.gameModeSupported == true) "已激活 ✓" else "未激活", gameColor)
            // 当前调度模式 (带等级颜色)
            val modeName = o?.powerModeCurrent?.ifEmpty { "均衡模式" } ?: "均衡模式"
            // 从独立布尔字段推导模式等级
            val modeLevel = when {
                o?.ultraPowerSaveMode == true -> 0
                o?.powerSaveMode == true -> 1
                o?.vivoBoostMode == true -> 2
                o?.highPerformanceMode == true -> 3
                else -> 2  // 默认均衡
            }
            val modeColor = when (modeLevel) {
                0, 1 -> NeonCyan           // 省电类 → 青色
                2 -> WarningNeon           // 均衡 → 橙色 (中性)
                3 -> NeonPurpleBright      // 性能 → 亮紫
                4 -> NeonMagenta           // 高性能 → 品红
                else -> MaterialTheme.colorScheme.onSurface
            }
            RowItemWithColor("当前调度", modeName, modeColor)
        }

        // ═══ 厂商原始属性 ═══
        val props = o?.rawProperties ?: emptyList()
        if (props.isNotEmpty()) {
            SectionCard("厂商属性 (${props.size}条)") {
                props.forEach { (k, v) ->
                    keyValueItem(k, v)
                }
            }
        }
    }
}

// ═══════════════ 通用组件 ═══════════════

private val SectionGradient = Brush.linearGradient(listOf(CyberCardStart, CyberCardEnd))

@Composable
@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(Modifier.fillMaxWidth().background(SectionGradient).hdrHighlight(12.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = NeonPurple)
                Column(Modifier.padding(top = 8.dp)) { content() }
            }
        }
    }
}

@Composable
private fun RowItem(label: String, value: String) {
    if (value.isNotBlank()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(0.35f), maxLines = 2, softWrap = true)
            Text(value, fontSize = 13.sp, color = NeonPurpleBright,
                modifier = Modifier.weight(0.65f), maxLines = 5, softWrap = true)
        }
    }
}

@Composable
private fun RowItemWithColor(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.35f), maxLines = 2, softWrap = true)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = color,
            modifier = Modifier.weight(0.65f), maxLines = 5, softWrap = true)
    }
}

@Composable
private fun keyValueItem(key: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(key, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        Text(value, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagFlow(tags: List<String>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tags.filter { it.isNotBlank() }.forEach { tag ->
            Card(
                shape = RoundedCornerShape(6.dp),
                colors = CardDefaults.cardColors(containerColor = NeonPurple.copy(alpha = 0.15f))
            ) {
                Text(tag, fontSize = 11.sp, color = NeonPurpleBright,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
    }
}
