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
import androidx.compose.ui.res.stringResource
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
import com.example.deviceinfoviewer.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OemScreen(viewModel: OemViewModel = koinViewModel()) {
    val oem by viewModel.oemInfo.observeAsState()

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.oem_title), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

        val o = oem

        // ═══ 系统信息 ═══
        SectionCard("${o?.osName ?: stringResource(R.string.oem_android)} · ${o?.oem ?: ""}") {
            val androidLabel = o?.androidVersion?.let { v -> stringResource(R.string.oem_android_label, v, o?.sdkLevel ?: 0) } ?: stringResource(R.string.common_detecting)
            RowItem(stringResource(R.string.oem_android), androidLabel)
            RowItem(stringResource(R.string.oem_version), o?.osVersion?.takeIf { it.isNotEmpty() } ?: stringResource(R.string.common_detecting))
            RowItem(stringResource(R.string.oem_build), o?.buildDisplayId?.takeIf { it.isNotEmpty() } ?: "-")
            RowItem(stringResource(R.string.oem_security_patch), o?.securityPatch?.takeIf { it.isNotEmpty() } ?: "-")
        }

        // ═══ SoC 信息 ═══
        SectionCard(stringResource(R.string.oem_section_soc_platform)) {
            RowItem(stringResource(R.string.oem_platform), o?.boardPlatform?.takeIf { it.isNotEmpty() } ?: "-")
            RowItem(stringResource(R.string.oem_soc_manufacturer), o?.socManufacturer?.takeIf { it.isNotEmpty() } ?: "-")
            RowItem(stringResource(R.string.oem_soc_model), o?.socModel?.takeIf { it.isNotEmpty() } ?: "-")
        }

        // ═══ 小米 HyperOS 专区 ═══
        if (o?.oem == "Xiaomi") {
            SectionCard(stringResource(R.string.oem_section_xiaomi)) {
                RowItem(stringResource(R.string.oem_system), o.osName.takeIf { it.isNotEmpty() } ?: "-")
                RowItem(stringResource(R.string.oem_version), o.miuiVersion.takeIf { it.isNotEmpty() } ?: "-")
                RowItem(stringResource(R.string.oem_region), o.miuiRegion.takeIf { it.isNotEmpty() } ?: "-")
                RowItem(stringResource(R.string.oem_mod_device), o.miuiHardware.takeIf { it.isNotEmpty() } ?: "-")
            }

            // 自研芯片
            val hasXiaomiChips = o.xiaomiSurgeChip.isNotEmpty()
                || o.xiaomiPengpaiISP.isNotEmpty()
                || o.xiaomiSecurityChip.isNotEmpty()
            if (hasXiaomiChips) {
                SectionCard(stringResource(R.string.oem_section_custom_chip)) {
                    RowItemWithColor(stringResource(R.string.oem_fast_charge), o.xiaomiSurgeChip.ifEmpty { "-" }, if (o.xiaomiSurgeChip.isNotEmpty()) NeonCyan else MaterialTheme.colorScheme.onSurface)
                    RowItemWithColor(stringResource(R.string.oem_imaging), o.xiaomiPengpaiISP.ifEmpty { "-" }, if (o.xiaomiPengpaiISP.isNotEmpty()) NeonCyan else MaterialTheme.colorScheme.onSurface)
                    RowItem(stringResource(R.string.oem_security_chip), o.xiaomiSecurityChip.ifEmpty { "-" })
                }
            }

            // HyperOS 3.0 特性
            val hasHyperFeatures = o.hyperOsAIModel.isNotEmpty() || o.hyperOsCrossDevice.isNotEmpty() || o.hyperOsPerformanceGrade.isNotEmpty()
            if (hasHyperFeatures) {
                SectionCard(stringResource(R.string.oem_section_hyper_features)) {
                    RowItem(stringResource(R.string.oem_ai_model), o.hyperOsAIModel.ifEmpty { "-" })
                    RowItem(stringResource(R.string.oem_cross_device), o.hyperOsCrossDevice.ifEmpty { "-" })
                    RowItemWithColor(stringResource(R.string.oem_perf_grade), o.hyperOsPerformanceGrade.ifEmpty { "-" },
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
                SectionCard(stringResource(R.string.oem_section_features_list)) {
                    TagFlow(o.miuiFeatures.split(" · "))
                }
            }
        }

        // ═══ OPPO ColorOS 专区 ═══
        if (o?.oem == "OPPO") {
            SectionCard(stringResource(R.string.oem_section_oppo)) {
                RowItem(stringResource(R.string.oem_system), o.osName.takeIf { it.isNotEmpty() } ?: "-")
                RowItem(stringResource(R.string.oem_version), o.oppoColorOSVersion.ifEmpty { o.oppoVersion.ifEmpty { "-" } })
                RowItem(stringResource(R.string.oem_screen_ratio), o.oppoScreenRatio.takeIf { it.isNotEmpty() } ?: "-")
            }

            // 自研芯片/引擎
            val hasOppoChips = o.oppoMariSilicon.isNotEmpty()
                || o.oppoDCE.isNotEmpty()
                || o.oppoTrucoEngine.isNotEmpty()
            if (hasOppoChips) {
                SectionCard(stringResource(R.string.oem_section_chip_engine)) {
                    RowItemWithColor(stringResource(R.string.oem_imaging_npu), o.oppoMariSilicon.ifEmpty { "-" }, if (o.oppoMariSilicon.isNotEmpty()) NeonCyan else MaterialTheme.colorScheme.onSurface)
                    RowItemWithColor(stringResource(R.string.oem_compute_engine), o.oppoDCE.ifEmpty { "-" }, if (o.oppoDCE.isNotEmpty()) NeonCyan else MaterialTheme.colorScheme.onSurface)
                    RowItemWithColor(stringResource(R.string.oem_game_engine), o.oppoTrucoEngine.ifEmpty { "-" }, if (o.oppoTrucoEngine.isNotEmpty()) NeonCyan else MaterialTheme.colorScheme.onSurface)
                }
            }

            // 内存融合
            if (o.oppoRAMPlus.isNotEmpty()) {
                SectionCard(stringResource(R.string.oem_section_ram_plus)) {
                    RowItem(stringResource(R.string.oem_status), o.oppoRAMPlus)
                }
            }

            // 充电信息 (保留)
            if (o.oplusCharging.isNotBlank()) {
                SectionCard(stringResource(R.string.oem_section_charging_info)) {
                    TagFlow(o.oplusCharging.split(" · "))
                }
            }
        }

        // ═══ Vivo OriginOS 专区 ═══
        if (o?.oem == "Vivo") {
            SectionCard(stringResource(R.string.oem_section_vivo)) {
                RowItem(stringResource(R.string.oem_system), o.osName.takeIf { it.isNotEmpty() } ?: "-")
                RowItem(stringResource(R.string.oem_version), o.vivoOriginOSVersion.ifEmpty { o.vivoOsVersion.ifEmpty { "-" } })
                RowItem(stringResource(R.string.oem_solution), o.vivoProductSolution.takeIf { it.isNotEmpty() } ?: "-")
                RowItem(stringResource(R.string.oem_model), o.vivoModel.takeIf { it.isNotEmpty() } ?: "-")
            }

            // 自研芯片
            if (o.vivoV3Chip.isNotEmpty()) {
                SectionCard(stringResource(R.string.oem_section_custom_chip)) {
                    RowItemWithColor(stringResource(R.string.oem_imaging_chip), o.vivoV3Chip, NeonCyan)
                }
            }

            // 内存融合
            if (o.vivoRAMFusion.isNotEmpty()) {
                SectionCard(stringResource(R.string.oem_section_memory_fusion)) {
                    RowItem(stringResource(R.string.oem_status), o.vivoRAMFusion)
                }
            }

            // 显示引擎
            if (o.vivoDisplayEngine.isNotEmpty()) {
                SectionCard(stringResource(R.string.oem_section_display_engine)) {
                    TagFlow(o.vivoDisplayEngine.split(" · "))
                }
            }
        }

        // ═══ 相机传感器 (v3 新增) ═══
        val hasCameraInfo = o?.cameraRearSensors?.isNotEmpty() == true
            || o?.cameraFrontSensor?.isNotEmpty() == true
            || o?.cameraSensorPhysicalSize?.isNotEmpty() == true
        if (hasCameraInfo) {
            SectionCard(stringResource(R.string.oem_section_camera_sensor)) {
                RowItem(stringResource(R.string.oem_rear), o?.cameraRearSensors?.ifEmpty { "-" } ?: "-")
                o?.cameraFrontSensor?.takeIf { it.isNotEmpty() }?.let { RowItem(stringResource(R.string.oem_front), it) }
                RowItem(stringResource(R.string.oem_sensor_size), o?.cameraSensorPhysicalSize?.ifEmpty { "-" } ?: "-")
                o?.cameraRearAperture?.takeIf { it.isNotEmpty() }?.let { RowItem(stringResource(R.string.oem_rear_aperture), it) }
                o?.cameraFrontAperture?.takeIf { it.isNotEmpty() }?.let { RowItem(stringResource(R.string.oem_front_aperture), it) }
                if (o?.cameraOpticalStabilization == true) {
                    RowItemWithColor(stringResource(R.string.oem_ois_label), stringResource(R.string.oem_ois_supported), SuccessNeon)
                }
                RowItem(stringResource(R.string.device_camera_flash), o?.cameraFlashType?.ifEmpty { stringResource(R.string.oem_flash_none) } ?: stringResource(R.string.oem_flash_none))
                o?.cameraMaxZoom?.takeIf { it.isNotEmpty() }?.let { RowItem(stringResource(R.string.oem_zoom), it) }
            }
        }

        // ═══ 充电协议 (v3 新增) ═══
        val hasChargingInfo = o?.chargingProtocol?.isNotEmpty() == true
            || o?.chargingMaxWatt?.isNotEmpty() == true
            || o?.chargingBatteryCapacity?.isNotEmpty() == true
        if (hasChargingInfo) {
            SectionCard(stringResource(R.string.oem_section_charging_protocol)) {
                RowItemWithColor(stringResource(R.string.device_protocol), o?.chargingProtocol?.ifEmpty { "-" } ?: "-",
                    if (o?.chargingProtocol?.isNotEmpty() == true) NeonCyan else MaterialTheme.colorScheme.onSurface)
                RowItemWithColor(stringResource(R.string.oem_max_power), o?.chargingMaxWatt?.ifEmpty { "-" } ?: "-",
                    if (o?.chargingMaxWatt?.isNotEmpty() == true) SuccessNeon else MaterialTheme.colorScheme.onSurface)
                RowItem(stringResource(R.string.oem_battery_capacity), o?.chargingBatteryCapacity?.ifEmpty { "-" } ?: "-")
                if (o?.chargingDualCell == true) {
                    RowItemWithColor(stringResource(R.string.oem_cell), stringResource(R.string.oem_dual_cell), NeonPurple)
                }
                o?.chargingWirelessPower?.takeIf { it.isNotEmpty() }?.let {
                    RowItemWithColor(stringResource(R.string.oem_wireless_charging), it, NeonCyan)
                }
            }
        }

        // ═══ 显示面板 (v3 新增) ═══
        val hasDisplayInfo = o?.displayPanelType?.isNotEmpty() == true
            || o?.displayPanelManufacturer?.isNotEmpty() == true
            || o?.displayMaxRefreshRate?.isNotEmpty() == true
        if (hasDisplayInfo) {
            SectionCard(stringResource(R.string.oem_section_display_panel)) {
                RowItem(stringResource(R.string.oem_panel_type), o?.displayPanelType?.ifEmpty { "-" } ?: "-")
                RowItem(stringResource(R.string.oem_panel_manufacturer), o?.displayPanelManufacturer?.ifEmpty { "-" } ?: "-")
                if (o?.displayLTPO == true) {
                    RowItemWithColor(stringResource(R.string.oem_variable_refresh), stringResource(R.string.oem_ltpo_label), NeonCyan)
                }
                val refreshLabel = buildString {
                    o?.displayMinRefreshRate?.let { append(it, " ~ ") }
                    o?.displayMaxRefreshRate?.let { append(it) }
                }
                if (refreshLabel.isNotEmpty()) RowItem(stringResource(R.string.oem_refresh_rate), refreshLabel)
                RowItem(stringResource(R.string.oem_peak_brightness), o?.displayPeakBrightness?.ifEmpty { "-" } ?: "-")
                RowItem(stringResource(R.string.oem_hdr), o?.displayHDR?.ifEmpty { "-" } ?: "-")
            }
        }

        // ═══ 性能调度器 (v3 新增) ═══
        val hasGovernorInfo = o?.cpuGovernor?.isNotEmpty() == true
            || o?.gpuGovernor?.isNotEmpty() == true
        if (hasGovernorInfo) {
            SectionCard(stringResource(R.string.oem_section_governor)) {
                RowItem(stringResource(R.string.oem_cpu_governor), o?.cpuGovernor?.ifEmpty { "-" } ?: "-")
                RowItem(stringResource(R.string.oem_gpu_governor), o?.gpuGovernor?.ifEmpty { "-" } ?: "-")
                RowItem(stringResource(R.string.oem_thermal_governor), o?.thermalGovernor?.ifEmpty { "-" } ?: "-")
            }
        }

        // ═══ 安全芯片 (v3 新增) ═══
        val hasSecurityInfo = o?.securityChip?.isNotEmpty() == true
            || o?.verifiedBootState?.isNotEmpty() == true
        if (hasSecurityInfo) {
            SectionCard(stringResource(R.string.oem_section_security_info)) {
                RowItem(stringResource(R.string.oem_security_chip), o?.securityChip?.ifEmpty { "-" } ?: "-")
                if (o?.secureBoot == true) {
                    RowItemWithColor(stringResource(R.string.oem_secure_boot), stringResource(R.string.oem_secure_boot_enabled), SuccessNeon)
                }
                RowItem(stringResource(R.string.oem_verified_boot_state), o?.verifiedBootState?.ifEmpty { "-" } ?: "-")
            }
        }

        // ═══ 子系统特性 ═══
        val hasSubFeatures = o?.aiEngineInfo?.isNotEmpty() == true
            || o?.memoryFusion?.isNotEmpty() == true
            || o?.thermalSolution?.isNotEmpty() == true
            || o?.storageBoost?.isNotEmpty() == true
            || o?.displayFeatures?.isNotEmpty() == true
        if (hasSubFeatures) {
            SectionCard(stringResource(R.string.oem_section_subsystems)) {
                o?.aiEngineInfo?.takeIf { it.isNotEmpty() }?.let { RowItem(stringResource(R.string.oem_ai_engine), it) }
                o?.memoryFusion?.takeIf { it.isNotEmpty() }?.let { RowItem(stringResource(R.string.oem_section_memory_fusion), it) }
                o?.thermalSolution?.takeIf { it.isNotEmpty() }?.let { RowItem(stringResource(R.string.oem_thermal_solution), it) }
                o?.storageBoost?.takeIf { it.isNotEmpty() }?.let { RowItem(stringResource(R.string.oem_storage_boost), it) }
                o?.displayFeatures?.takeIf { it.isNotEmpty() }?.let { RowItem(stringResource(R.string.oem_display_features), it) }
            }
        }

        // ═══ 性能模式 ═══
        SectionCard(stringResource(R.string.device_section_perf_mode)) {
            val gameColor = if (o?.gameModeSupported == true) SuccessNeon else WarningNeon
            RowItemWithColor(stringResource(R.string.oem_game_mode), if (o?.gameModeSupported == true) stringResource(R.string.oem_game_mode_activated) else stringResource(R.string.oem_game_mode_not_activated), gameColor)
            // 当前调度模式 (带等级颜色)
            val modeName = o?.powerModeCurrent?.ifEmpty { stringResource(R.string.oem_balanced_mode) } ?: stringResource(R.string.oem_balanced_mode)
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
            RowItemWithColor(stringResource(R.string.oem_current_scheduler), modeName, modeColor)
        }

        // ═══ 厂商原始属性 ═══
        val props = o?.rawProperties ?: emptyList()
        if (props.isNotEmpty()) {
            SectionCard(stringResource(R.string.oem_section_vendor_props, props.size)) {
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
