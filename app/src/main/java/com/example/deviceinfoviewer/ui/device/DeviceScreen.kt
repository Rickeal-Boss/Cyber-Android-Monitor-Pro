package com.example.deviceinfoviewer.ui.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.os.Build
import com.example.deviceinfoviewer.data.model.CameraSensorInfo
import com.example.deviceinfoviewer.data.model.DeviceDetailInfo
import com.example.deviceinfoviewer.ui.oem.OemViewModel
import com.example.deviceinfoviewer.ui.theme.*
import org.koin.androidx.compose.koinViewModel

@Composable
fun DeviceScreen(
    viewModel: DeviceViewModel = koinViewModel(),
    oemViewModel: OemViewModel = koinViewModel()
) {
    val detail by viewModel.detail.observeAsState()
    val oem by oemViewModel.oemInfo.observeAsState()

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("设备详情", fontSize = 18.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface)

        // ═══════ 1. 芯片 SoC ═══════
        SectionCard("芯片 (SoC)") {
            if (oem != null && oem!!.socModel.isNotEmpty()) {
                RowItem("SoC 型号", "${oem!!.socManufacturer} ${oem!!.socModel}")
            }
            if (oem != null && oem!!.boardPlatform.isNotEmpty()) {
                RowItem("平台代号", oem!!.boardPlatform)
            }
            RowItem("CPU 架构", detail?.cpuArchitecture?.takeIf { it.isNotEmpty() } ?: "-")
            if (detail?.cpuImplementer?.isNotEmpty() == true)
                RowItem("CPU Implementer", detail!!.cpuImplementer)
            if (detail?.cpuPart?.isNotEmpty() == true)
                RowItem("CPU Part", detail!!.cpuPart)
            if (detail?.bigLITTLE?.isNotEmpty() == true)
                RowItem("核心拓扑", detail!!.bigLITTLE)
            RowItem("构建号", oem?.buildDisplayId?.takeIf { it.isNotEmpty() } ?: "-")
            RowItem("安全补丁", oem?.securityPatch?.takeIf { it.isNotEmpty() } ?: "-")
            RowItem("API Level", "${oem?.sdkLevel ?: Build.VERSION.SDK_INT} (Android ${oem?.androidVersion ?: Build.VERSION.RELEASE})")

            // SoC 制程工艺
            if (detail?.socProcessNode?.isNotEmpty() == true) {
                RowItem("制程工艺", detail!!.socProcessNode,
                    valueColor = NeonCyan)
            }
        }

        // ═══════ 2. CPU 缓存架构 (新增) ═══════
        val hasCacheInfo = detail?.let {
            it.cpuCacheL1iKb > 0 || it.cpuCacheL1dKb > 0 || it.cpuCacheL2Kb > 0
        } == true
        if (hasCacheInfo) {
            SectionCard("CPU 缓存架构") {
                if (detail!!.cpuCacheL1iKb > 0)
                    RowItem("L1 指令缓存", "${detail!!.cpuCacheL1iKb} KB")
                if (detail!!.cpuCacheL1dKb > 0)
                    RowItem("L1 数据缓存", "${detail!!.cpuCacheL1dKb} KB")
                if (detail!!.cpuCacheL2Kb > 0)
                    RowItem("L2 缓存", if (detail!!.cpuCacheL2Kb >= 1024) "${detail!!.cpuCacheL2Kb / 1024} MB" else "${detail!!.cpuCacheL2Kb} KB")
                if (detail!!.cpuCacheL3Kb > 0)
                    RowItem("L3 缓存", if (detail!!.cpuCacheL3Kb >= 1024) "${detail!!.cpuCacheL3Kb / 1024} MB" else "${detail!!.cpuCacheL3Kb} KB")
                if (detail!!.cpuCacheSource.isNotEmpty())
                    RowItem("数据来源", detail!!.cpuCacheSource, valueColor = NeonPurple.copy(alpha = 0.6f))
            }
        }

        // ═══════ 3. GPU 图形 ═══════
        val hasRealGpu = detail?.glRenderer?.isNotEmpty() == true && detail?.glRenderer != "0"
        SectionCard(if (hasRealGpu) "GPU 图形 · ${detail!!.glRenderer}" else "GPU 图形") {
            if (hasRealGpu) {
                RowItem("GPU 型号", detail!!.glRenderer)
                RowItem("GPU 厂商", detail!!.glVendor)
            } else {
                RowItem("GPU 型号", "检测中…")
            }
            RowItem("OpenGL ES", detail?.glEsVersion ?: "")
            detail?.gpuDriverVersion?.takeIf { it.isNotEmpty() }?.let {
                RowItem("驱动版本", it)
            }
            RowItem("GL 扩展数", "${detail?.glExtensions?.size ?: 0}")
            if (detail?.gpuLocalMemoryKb?.compareTo(0) == 1) {
                val gmem = detail!!.gpuLocalMemoryKb
                RowItem("GPU 显存", if (gmem >= 1024) "${gmem / 1024} MB" else "$gmem KB")
            }
        }

        // ═══════ 4. Vulkan ═══════
        val vkVersion = detail?.vulkanVersion?.takeIf { it.isNotEmpty() }
        val vkLevel = detail?.vulkanApiLevel?.takeIf { it.isNotEmpty() }
        if (vkVersion != null || vkLevel != null) {
            SectionCard("Vulkan") {
                if (vkVersion != null) RowItem("API 版本", vkVersion)
                if (vkLevel != null) RowItem("硬件级别", vkLevel)
                RowItem("光线追踪", if (detail?.rayTracingSupported == true) "支持 ✓" else "不支持",
                    if (detail?.rayTracingSupported == true) SuccessNeon else WarningNeon)
            }
        }

        // ═══════ 5. 显示 ═══════
        SectionCard("显示") {
            RowItem("分辨率", detail?.resolution ?: "")
            RowItem("密度", "${detail?.densityDpi ?: 0} dpi (${detail?.density?.let { "%.1f".format(it) } ?: "-"}×)")
            RowItem("刷新率", detail?.refreshRateHz?.takeIf { it > 0 }?.let { "%.1f Hz".format(it) } ?: "-")
            RowItem("物理尺寸", detail?.physicalSizeInches?.takeIf { it > 0 }?.let { "%.1f\"".format(it) } ?: "-")
            detail?.displayTechnology?.takeIf { it.isNotEmpty() }?.let { RowItem("面板技术", it) }
            detail?.colorDepth?.takeIf { it.isNotEmpty() }?.let { RowItem("色深", it) }
            detail?.colorGamut?.takeIf { it.isNotEmpty() }?.let { RowItem("色域", it) }
            detail?.hdrCapabilities?.takeIf { it.isNotEmpty() }?.let {
                RowItem("HDR", it.joinToString(", "))
            }
            detail?.maxBrightnessNits?.takeIf { it > 0 }?.let { RowItem("峰值亮度", "${it} nits") }
            RowItem("触控", detail?.touchscreenType ?: "")
        }

        // ═══════ 6. 内存 (新增) ═══════
        val hasMemType = detail?.memoryType?.isNotEmpty() == true
        if (hasMemType || detail?.memorySpeedMhz?.compareTo(0) == 1) {
            SectionCard("内存规格") {
                if (detail?.memoryType?.isNotEmpty() == true)
                    RowItem("内存类型", detail!!.memoryType)
                if (detail?.memorySpeedMhz?.compareTo(0) == 1)
                    RowItem("内存频率", "${detail!!.memorySpeedMhz} MHz")
                if (detail?.memoryTypeSource?.isNotEmpty() == true)
                    RowItem("数据来源", detail!!.memoryTypeSource, valueColor = NeonPurple.copy(alpha = 0.6f))
            }
        }

        // ═══════ 7. 存储 (新增) ═══════
        val hasStorage = detail?.storageType?.isNotEmpty() == true
        if (hasStorage) {
            SectionCard("存储规格") {
                RowItem("存储类型", detail!!.storageType)
                if (detail?.storageProtocol?.isNotEmpty() == true)
                    RowItem("协议", detail!!.storageProtocol)
                if (detail?.storageTypeSource?.isNotEmpty() == true)
                    RowItem("数据来源", detail!!.storageTypeSource, valueColor = NeonPurple.copy(alpha = 0.6f))
            }
        }

        // ═══════ 8. 相机 ═══════
        val sensors = detail?.cameraSensors
        if (sensors != null && sensors.isNotEmpty()) {
            SectionCard("相机") {
                sensors.forEach { sensor ->
                    CameraRow(sensor)
                }
            }
        } else {
            SectionCard("相机") {
                RowItem("摄像头", detail?.cameraIds?.joinToString(", ").orEmpty().ifEmpty { "未检测" })
            }
        }

        // ═══════ 9. 音频 ═══════
        SectionCard("音频") {
            RowItem("扬声器", if (detail?.stereoSpeakers == true) "立体声" else "单声道")
            RowItem("输出采样率", detail?.audioSampleRate?.takeIf { it != "-" } ?: "-")
            RowItem("Hi-Res 音频", if (detail?.supportsHiResAudio == true) "支持" else "-")
            detail?.audioFormats?.takeIf { it.isNotEmpty() }?.let {
                RowItem("音频格式", it.joinToString(", "))
            }
        }

        // ═══════ 10. SIM / 通讯 ═══════
        SectionCard("SIM / 通讯") {
            RowItem("运营商", detail?.simOperator?.takeIf { it.isNotEmpty() } ?: "-")
            RowItem("MCC/MNC", detail?.simMccMnc?.takeIf { it != "0" } ?: "-")
            RowItem("网络制式", detail?.phoneType ?: "")
            RowItem("双卡", if (detail?.isDualSim == true) "支持" else "不支持")
        }

        // ═══════ 11. 连接 (增强) ═══════
        SectionCard("连接") {
            RowItem("蓝牙", buildString {
                if (detail?.bluetoothSupported == true) {
                    append("支持")
                    if (detail?.bluetoothVersion?.isNotEmpty() == true) append(" · ${detail!!.bluetoothVersion}")
                    if (detail?.bleSupported == true) append(" · BLE")
                    if (detail?.bluetoothLeAudio == true) append(" · LE Audio")
                } else {
                    append("不支持")
                }
            })
            RowItem("Wi-Fi", buildString {
                append(detail?.wifiStandard?.takeIf { it.isNotEmpty() } ?: "支持")
                if (detail?.wifi6EEnabled == true) append(" · 6GHz")
                if (detail?.wifiAware == true) append(" · Aware")
            })
            RowItem("NFC", if (detail?.hasNfc == true) "支持" else "不支持")
            RowItem("USB", buildString {
                append(detail?.usbVersion?.takeIf { it.isNotEmpty() } ?: "USB")
                if (detail?.usbTypeC == true) append(" · Type-C")
                if (detail?.usbHostMode == true) append(" · Host")
            })
            if (detail?.hasInfrared == true) RowItem("红外", "支持")
            if (detail?.hasUwb == true) RowItem("UWB", "支持")
            if (detail?.hasWirelessCharging == true) RowItem("无线充电", "支持")
        }

        // ═══════ 12. 多媒体解码器 ═══════
        SectionCard("多媒体解码器") {
            val v = detail?.videoCodecs?.take(6)?.joinToString(", ").orEmpty()
            if (v.isNotBlank()) RowItem("视频解码", v + if ((detail?.videoCodecs?.size ?: 0) > 6) "…" else "")
            val a = detail?.audioCodecs?.take(6)?.joinToString(", ").orEmpty()
            if (a.isNotBlank()) RowItem("音频解码", a + if ((detail?.audioCodecs?.size ?: 0) > 6) "…" else "")
        }

        // ═══════ 13. 热管理 (新增) ═══════
        if (detail?.thermalZoneCount?.compareTo(0) == 1) {
            SectionCard("热管理") {
                RowItem("热区传感器", "${detail!!.thermalZoneCount} 个")
                val types = detail!!.thermalZoneTypes
                if (types.isNotEmpty()) {
                    // 去重并取前 8 个
                    val uniqueTypes = types.distinct().take(8)
                    RowItem("传感器类型", uniqueTypes.joinToString(", ") +
                        if (types.distinct().size > 8) "…" else "")
                }
            }
        }

        // ═══════ 14. DRM ═══════
        SectionCard("DRM / Widevine") {
            RowItem("Widevine 等级", detail?.widevineLevel ?: "检测中")
            RowItem("支持方案", detail?.drmSchemes?.joinToString(", ").orEmpty())
        }

        // ═══════ 15. 安全 ═══════
        SectionCard("安全") {
            RowItem("TEE / TrustZone", if (detail?.teeSupported == true) "支持" else "未检测",
                if (detail?.teeSupported == true) SuccessNeon else WarningNeon)
            RowItem("Verified Boot", if (detail?.secureBootEnabled == true) "已激活" else "未激活",
                if (detail?.secureBootEnabled == true) SuccessNeon else WarningNeon)
            RowItem("文件加密", detail?.fileEncryption?.takeIf { it.isNotEmpty() } ?: "-")
            RowItem("SELinux", if (detail?.selinuxEnforcing == true) "Enforcing" else "Permissive",
                if (detail?.selinuxEnforcing == true) SuccessNeon else WarningNeon)
        }

        // ═══════ OEM 专区 ═══════
        if (oem != null && oem!!.oem != "AOSP") {
            Text("系统识别", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp))

            SectionCard("${oem!!.osName} · ${oem!!.oem}") {
                RowItem("版本", oem!!.osVersion.ifEmpty { "检测中" })
                RowItem("构建号", oem!!.buildDisplayId.ifEmpty { "-" })
                RowItem("安全补丁", oem!!.securityPatch.ifEmpty { "-" })
            }

            when (oem!!.oem) {
                "Xiaomi" -> SectionCard("小米 HyperOS/MIUI") {
                    RowItem("版本", oem!!.miuiVersion.ifEmpty { "-" })
                    RowItem("Region", oem!!.miuiRegion.ifEmpty { "-" })
                    RowItem("硬件型号", oem!!.miuiHardware.ifEmpty { "-" })
                    oem!!.miuiFeatures.takeIf { it.isNotBlank() }?.let {
                        RowItem("特性", it.trim())
                    }
                }
                "OPPO" -> SectionCard("OPPO ColorOS") {
                    RowItem("版本", oem!!.oppoVersion.ifEmpty { "-" })
                    RowItem("屏幕比例", oem!!.oppoScreenRatio.ifEmpty { "-" })
                    oem!!.oplusCharging.takeIf { it.isNotBlank() }?.let {
                        RowItem("充电方案", it.trim())
                    }
                }
                "Vivo" -> SectionCard("Vivo OriginOS") {
                    RowItem("版本", oem!!.vivoOsVersion.ifEmpty { "-" })
                    RowItem("方案", oem!!.vivoProductSolution.ifEmpty { "-" })
                    RowItem("型号", oem!!.vivoModel.ifEmpty { "-" })
                }
                "Samsung" -> SectionCard("Samsung One UI") {
                    RowItem("One UI 版本", oem!!.osVersion.ifEmpty { "-" })
                    oem!!.buildDisplayId.takeIf { it.isNotBlank() }?.let {
                        RowItem("Build", it)
                    }
                }
            }

            // 性能模式
            SectionCard("性能模式") {
                RowItem("游戏模式", if (oem!!.gameModeSupported) "支持" else "未激活")
                RowItem("高性能", if (oem!!.highPerformanceMode) "已开启" else "未激活")
            }

            // 厂商子系统
            val subsystems = buildList {
                oem!!.aiEngineInfo.takeIf { it.isNotBlank() }?.let { add("AI 引擎" to it) }
                oem!!.memoryFusion.takeIf { it.isNotBlank() }?.let { add("内存扩展" to it) }
                oem!!.thermalSolution.takeIf { it.isNotBlank() }?.let { add("散热方案" to it) }
                oem!!.storageBoost.takeIf { it.isNotBlank() }?.let { add("存储加速" to it) }
                oem!!.displayFeatures.takeIf { it.isNotBlank() }?.let { add("显示特性" to it) }
            }
            if (subsystems.isNotEmpty()) {
                SectionCard("厂商子系统") {
                    subsystems.forEach { (k, v) -> RowItem(k, v) }
                }
            }

            // 原始属性
            val props = oem!!.rawProperties
            if (props.isNotEmpty()) {
                SectionCard("厂商属性 (${props.size} 条)") {
                    props.forEach { (k, v) ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                            Text(k, fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                            Text(v, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
}

// ═══════ 相机传感器行 ═══════
@Composable
private fun CameraRow(sensor: CameraSensorInfo) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${sensor.facing}摄像头", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = NeonPurple)
        }
        if (sensor.resolution.isNotEmpty())
            RowItem("  分辨率", sensor.resolution)
        if (sensor.aperture.isNotEmpty())
            RowItem("  光圈", sensor.aperture)
        if (sensor.focalLength.isNotEmpty())
            RowItem("  焦距", sensor.focalLength)
        if (sensor.pixelSize.isNotEmpty())
            RowItem("  像素尺寸", sensor.pixelSize)
        val features = buildList {
            if (sensor.oisSupported) add("OIS")
            if (sensor.eisSupported) add("EIS")
            if (sensor.flashSupported) add("闪光灯")
        }
        if (features.isNotEmpty())
            RowItem("  特性", features.joinToString(" · "))
    }
}

// ═══════ 共享组件 ═══════
@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = NeonPurple)
            Column(Modifier.padding(top = 8.dp)) { content() }
        }
    }
}

@Composable
private fun RowItem(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color = NeonPurpleBright) {
    if (value.isNotBlank()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                label, fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(0.35f),
                maxLines = 2, softWrap = true
            )
            Text(
                value, fontSize = 13.sp,
                color = valueColor,
                modifier = Modifier.weight(0.65f),
                maxLines = 5, softWrap = true
            )
        }
    }
}
