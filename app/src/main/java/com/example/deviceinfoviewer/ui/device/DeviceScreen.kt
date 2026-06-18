package com.example.deviceinfoviewer.ui.device

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import android.os.Build
import com.example.deviceinfoviewer.data.model.CameraSensorInfo
import com.example.deviceinfoviewer.data.model.DeviceDetailInfo
import com.example.deviceinfoviewer.ui.components.hdrHighlight
import com.example.deviceinfoviewer.ui.oem.OemViewModel
import com.example.deviceinfoviewer.ui.theme.*
import org.koin.androidx.compose.koinViewModel
import com.example.deviceinfoviewer.R

@Composable
fun DeviceScreen(
    viewModel: DeviceViewModel = koinViewModel(),
    oemViewModel: OemViewModel = koinViewModel()
) {
    val detail by viewModel.detail.observeAsState()
    val oem by oemViewModel.oemInfo.observeAsState()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.device_title), fontSize = 18.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface)

        // ═══════ 1. 芯片 SoC ═══════
        SectionCard(stringResource(R.string.device_section_soc)) {
            if (oem != null && oem!!.socModel.isNotEmpty()) {
                RowItem(stringResource(R.string.device_soc_model), "${oem!!.socManufacturer} ${oem!!.socModel}")
            }
            if (oem != null && oem!!.boardPlatform.isNotEmpty()) {
                RowItem(stringResource(R.string.device_platform_codename), oem!!.boardPlatform)
            }
            RowItem(stringResource(R.string.device_cpu_arch), detail?.cpuArchitecture?.takeIf { it.isNotEmpty() } ?: "-")
            if (detail?.cpuImplementer?.isNotEmpty() == true)
                RowItem("CPU Implementer", detail!!.cpuImplementer)
            if (detail?.cpuPart?.isNotEmpty() == true)
                RowItem("CPU Part", detail!!.cpuPart)
            if (detail?.bigLITTLE?.isNotEmpty() == true)
                RowItem(stringResource(R.string.device_core_topology), detail!!.bigLITTLE)
            RowItem(stringResource(R.string.device_build_id), oem?.buildDisplayId?.takeIf { it.isNotEmpty() } ?: "-")
            RowItem(stringResource(R.string.device_security_patch), oem?.securityPatch?.takeIf { it.isNotEmpty() } ?: "-")
            RowItem("API Level", "${oem?.sdkLevel ?: Build.VERSION.SDK_INT} (Android ${oem?.androidVersion ?: Build.VERSION.RELEASE})")

            // SoC 制程工艺
            if (detail?.socProcessNode?.isNotEmpty() == true) {
                RowItem(stringResource(R.string.device_process_node), detail!!.socProcessNode,
                    valueColor = NeonCyan)
            }
        }

        // ═══════ 2. CPU 缓存架构 (新增) ═══════
        val hasCacheInfo = detail?.let {
            it.cpuCacheL1iKb > 0 || it.cpuCacheL1dKb > 0 || it.cpuCacheL2Kb > 0
        } == true
        if (hasCacheInfo) {
            SectionCard(stringResource(R.string.device_section_cpu_cache)) {
                if (detail!!.cpuCacheL1iKb > 0)
                    RowItem(stringResource(R.string.device_l1_instruction_cache), "${detail!!.cpuCacheL1iKb} KB")
                if (detail!!.cpuCacheL1dKb > 0)
                    RowItem(stringResource(R.string.device_l1_data_cache), "${detail!!.cpuCacheL1dKb} KB")
                if (detail!!.cpuCacheL2Kb > 0)
                    RowItem(stringResource(R.string.device_l2_cache), if (detail!!.cpuCacheL2Kb >= 1024) "${detail!!.cpuCacheL2Kb / 1024} MB" else "${detail!!.cpuCacheL2Kb} KB")
                if (detail!!.cpuCacheL3Kb > 0)
                    RowItem(stringResource(R.string.device_l3_cache), if (detail!!.cpuCacheL3Kb >= 1024) "${detail!!.cpuCacheL3Kb / 1024} MB" else "${detail!!.cpuCacheL3Kb} KB")
                if (detail!!.cpuCacheSource.isNotEmpty())
                    RowItem(stringResource(R.string.device_data_source), detail!!.cpuCacheSource, valueColor = NeonPurple.copy(alpha = 0.6f))
            }
        }

        // ═══════ 3. GPU 图形 ═══════
        val hasRealGpu = detail?.glRenderer?.isNotEmpty() == true && detail?.glRenderer != "0"
        SectionCard(if (hasRealGpu) "${stringResource(R.string.device_section_gpu)} · ${detail!!.glRenderer}" else stringResource(R.string.device_section_gpu)) {
            if (hasRealGpu) {
                RowItem(stringResource(R.string.device_gpu_model), detail!!.glRenderer)
                RowItem(stringResource(R.string.device_gpu_vendor), detail!!.glVendor)
            } else {
                RowItem(stringResource(R.string.device_gpu_model), stringResource(R.string.common_detecting))
            }
            RowItem("OpenGL ES", detail?.glEsVersion ?: "")
            detail?.gpuDriverVersion?.takeIf { it.isNotEmpty() }?.let {
                RowItem(stringResource(R.string.device_driver_version), it)
            }
            // GL 关键扩展
            val keyExts = detail?.glKeyExtensions
            if (keyExts != null && keyExts.isNotEmpty()) {
                RowItem(stringResource(R.string.device_gl_extensions), keyExts.take(6).joinToString(" · ") +
                    if (keyExts.size > 6) " " + stringResource(R.string.device_gl_extensions_more, keyExts.size - 6) else "")
            } else {
                RowItem(stringResource(R.string.device_gl_extensions_count), "${detail?.glExtensions?.size ?: 0}")
            }
            if (detail?.gpuLocalMemoryKb?.compareTo(0) == 1) {
                val gmem = detail!!.gpuLocalMemoryKb
                RowItem(stringResource(R.string.device_gpu_vram), if (gmem >= 1024) "${gmem / 1024} MB" else "$gmem KB")
            }
        }

        // ═══════ 4. Vulkan 全面拆解 ═══════
        val vkVersion = detail?.vulkanVersion?.takeIf { it.isNotEmpty() }
        val vkLevel = detail?.vulkanApiLevel?.takeIf { it.isNotEmpty() }
        val vkExts = detail?.vulkanExtensions?.takeIf { it.isNotEmpty() }
        val vkDevCount = detail?.vulkanDeviceCount?.takeIf { it > 0 }
        if (vkVersion != null || vkLevel != null || vkExts != null || vkDevCount != null) {
            SectionCard("Vulkan") {
                if (vkVersion != null) {
                    // 细化至小数点后
                    val parts = vkVersion.split(".")
                    val detailed = when {
                        parts.size >= 3 -> "Vulkan ${parts[0]}.${parts[1]}.${parts[2]}"
                        parts.size == 2 -> "Vulkan ${parts[0]}.${parts[1]}"
                        else -> "Vulkan $vkVersion"
                    }
                    RowItem(stringResource(R.string.device_api_version_label), detailed)
                }
                if (vkLevel != null) RowItem(stringResource(R.string.device_hardware_level_label), vkLevel)
                if (vkDevCount != null) RowItem(stringResource(R.string.device_physical_devices_label), stringResource(R.string.device_physical_devices_value_format, vkDevCount))
                if (vkExts != null) {
                    val keyExts = listOf(
                        "VK_KHR_ray_tracing_pipeline", "VK_KHR_ray_query",
                        "VK_KHR_acceleration_structure", "VK_KHR_deferred_host_operations",
                        "VK_KHR_fragment_shading_rate", "VK_EXT_descriptor_indexing",
                        "VK_KHR_push_descriptor", "VK_KHR_timeline_semaphore",
                        "VK_KHR_buffer_device_address", "VK_EXT_mesh_shader",
                        "VK_KHR_dynamic_rendering", "VK_KHR_synchronization2",
                        "VK_EXT_shader_viewport_index_layer",
                    )
                    val found = keyExts.filter { ext -> vkExts.any { it.contains(ext.substringAfter("VK_")) } }
                    val total = vkExts.size
                    val summary = if (found.isNotEmpty()) {
                        stringResource(R.string.device_ext_enabled_summary, found.size, total)
                    } else {
                        stringResource(R.string.device_ext_total_summary, total)
                    }
                    RowItem(stringResource(R.string.device_gl_extensions), summary)
                    // 列出关键扩展
                    found.take(8).forEach { ext ->
                        val shortName = ext.substringAfter("VK_KHR_").takeIf { it.isNotEmpty() }
                            ?: ext.substringAfter("VK_EXT_").takeIf { it.isNotEmpty() }
                            ?: ext
                        RowItem("  $shortName", "✓",
                            if (ext.contains("ray_tracing") || ext.contains("mesh_shader") || ext.contains("fragment_shading_rate"))
                                SuccessNeon else NeonPurpleBright)
                    }
                }
                RowItem(stringResource(R.string.device_ray_tracing), if (detail?.rayTracingSupported == true) stringResource(R.string.device_supported) else stringResource(R.string.device_not_supported),
                    if (detail?.rayTracingSupported == true) SuccessNeon else WarningNeon)
            }
        }

        // ═══════ 5. 显示 ═══════
        SectionCard(stringResource(R.string.device_section_display)) {
            RowItem(stringResource(R.string.device_resolution), detail?.resolution ?: "")
            RowItem(stringResource(R.string.device_density), "${detail?.densityDpi ?: 0} dpi (${detail?.density?.let { "%.1f".format(it) } ?: "-"}×)")
            RowItem(stringResource(R.string.device_refresh_rate), detail?.refreshRateHz?.takeIf { it > 0 }?.let { "%.1f Hz".format(it) } ?: "-")
            RowItem(stringResource(R.string.device_physical_size), detail?.physicalSizeInches?.takeIf { it > 0 }?.let { "%.1f\"".format(it) } ?: "-")
            detail?.displayTechnology?.takeIf { it.isNotEmpty() }?.let { RowItem(stringResource(R.string.device_panel_tech), it) }
            detail?.colorDepth?.takeIf { it.isNotEmpty() }?.let { RowItem(stringResource(R.string.device_color_depth), it) }
            detail?.colorGamut?.takeIf { it.isNotEmpty() }?.let { RowItem(stringResource(R.string.device_color_gamut), it) }
            detail?.hdrCapabilities?.takeIf { it.isNotEmpty() }?.let {
                RowItem("HDR", it.joinToString(", "))
            }
            detail?.maxBrightnessNits?.takeIf { it > 0 }?.let { RowItem(stringResource(R.string.device_peak_brightness), "${it} nits") }
            RowItem(stringResource(R.string.device_touch), detail?.touchscreenType ?: "")
        }

        // ═══════ 6. 内存 (新增) ═══════
        val hasMemType = detail?.memoryType?.isNotEmpty() == true
        if (hasMemType || detail?.memorySpeedMhz?.compareTo(0) == 1) {
            SectionCard(stringResource(R.string.device_section_memory_spec)) {
                if (detail?.memoryType?.isNotEmpty() == true)
                    RowItem(stringResource(R.string.device_memory_type), detail!!.memoryType)
                if (detail?.memorySpeedMhz?.compareTo(0) == 1)
                    RowItem(stringResource(R.string.device_memory_speed), "${detail!!.memorySpeedMhz} MHz")
                if (detail?.memoryTypeSource?.isNotEmpty() == true)
                    RowItem(stringResource(R.string.device_data_source), detail!!.memoryTypeSource, valueColor = NeonPurple.copy(alpha = 0.6f))
            }
        }

        // ═══════ 7. 存储 (新增) ═══════
        val hasStorage = detail?.storageType?.isNotEmpty() == true
        if (hasStorage) {
            SectionCard(stringResource(R.string.device_section_storage_spec)) {
                RowItem(stringResource(R.string.device_storage_type), detail!!.storageType)
                if (detail?.storageProtocol?.isNotEmpty() == true)
                    RowItem(stringResource(R.string.device_protocol), detail!!.storageProtocol)
                if (detail?.storageTypeSource?.isNotEmpty() == true)
                    RowItem(stringResource(R.string.device_data_source), detail!!.storageTypeSource, valueColor = NeonPurple.copy(alpha = 0.6f))
            }
        }

        // ═══════ 8. 相机 ═══════
        val sensors = detail?.cameraSensors
        if (sensors != null && sensors.isNotEmpty()) {
            SectionCard(stringResource(R.string.device_section_camera)) {
                sensors.forEach { sensor ->
                    CameraRow(sensor)
                }
            }
        } else {
            SectionCard(stringResource(R.string.device_section_camera)) {
                RowItem(stringResource(R.string.device_camera_facing_suffix), detail?.cameraIds?.joinToString(", ").orEmpty().ifEmpty { stringResource(R.string.common_not_detected) })
            }
        }

        // ═══════ 9. 音频 ═══════
        SectionCard(stringResource(R.string.device_section_audio)) {
            RowItem(stringResource(R.string.device_speaker), if (detail?.stereoSpeakers == true) stringResource(R.string.device_stereo) else stringResource(R.string.device_mono))
            RowItem(stringResource(R.string.device_output_sample_rate), detail?.audioSampleRate?.takeIf { it != "-" } ?: "-")
            RowItem(stringResource(R.string.device_hires_audio), if (detail?.supportsHiResAudio == true) stringResource(R.string.common_yes) else "-")
            detail?.audioFormats?.takeIf { it.isNotEmpty() }?.let {
                RowItem(stringResource(R.string.device_audio_formats), it.joinToString(", "))
            }
        }

        // ═══════ 10. SIM / 通讯 ═══════
        SectionCard(stringResource(R.string.device_section_sim)) {
            RowItem(stringResource(R.string.device_operator_label), detail?.simOperator?.takeIf { it.isNotEmpty() } ?: "-")
            RowItem(stringResource(R.string.device_mcc_mnc), detail?.simMccMnc?.takeIf { it != "0" } ?: "-")
            RowItem(stringResource(R.string.device_network_standard), detail?.phoneType ?: "")
            RowItem(stringResource(R.string.device_dual_sim), if (detail?.isDualSim == true) stringResource(R.string.common_yes) else stringResource(R.string.device_not_supported))
        }

        // ═══════ 11. 连接 (增强) ═══════
        SectionCard(stringResource(R.string.device_section_connection)) {
            RowItem(stringResource(R.string.device_bluetooth), buildString {
                if (detail?.bluetoothSupported == true) {
                    append(stringResource(R.string.common_yes))
                    if (detail?.bluetoothVersion?.isNotEmpty() == true) append(" · ${detail!!.bluetoothVersion}")
                    if (detail?.bleSupported == true) append(" · BLE")
                    if (detail?.bluetoothLeAudio == true) append(" · LE Audio")
                } else {
                    append(stringResource(R.string.device_not_supported))
                }
            })
            RowItem(stringResource(R.string.device_wifi), buildString {
                append(detail?.wifiStandard?.takeIf { it.isNotEmpty() } ?: stringResource(R.string.common_yes))
                if (detail?.wifi6EEnabled == true) append(" · 6GHz")
                if (detail?.wifiAware == true) append(" · Aware")
            })
            RowItem(stringResource(R.string.device_nfc), if (detail?.hasNfc == true) stringResource(R.string.common_yes) else stringResource(R.string.device_not_supported))
            RowItem(stringResource(R.string.device_usb), buildString {
                append(detail?.usbVersion?.takeIf { it.isNotEmpty() } ?: "USB")
                if (detail?.usbTypeC == true) append(" · Type-C")
                if (detail?.usbHostMode == true) append(" · Host")
            })
            if (detail?.hasInfrared == true) RowItem(stringResource(R.string.device_infrared), stringResource(R.string.common_yes))
            if (detail?.hasUwb == true) RowItem(stringResource(R.string.device_uwb), stringResource(R.string.common_yes))
            if (detail?.hasWirelessCharging == true) RowItem(stringResource(R.string.device_wireless_charging), stringResource(R.string.common_yes))
        }

        // ═══════ 12. 多媒体解码器 ═══════
        SectionCard(stringResource(R.string.device_section_codecs)) {
            val vCount = detail?.videoCodecs?.size ?: 0
            val aCount = detail?.audioCodecs?.size ?: 0
            val hwCount = detail?.hwAcceleratedCodecs?.size ?: 0
            val v = detail?.videoCodecs?.take(8)?.joinToString(", ").orEmpty()
            if (v.isNotBlank()) RowItem(stringResource(R.string.device_video_decode), v + if (vCount > 8) " (+${vCount - 8})" else "")
            val a = detail?.audioCodecs?.take(8)?.joinToString(", ").orEmpty()
            if (a.isNotBlank()) RowItem(stringResource(R.string.device_audio_decode), a + if (aCount > 8) " (+${aCount - 8})" else "")
            if (hwCount > 0) {
                val hw = detail?.hwAcceleratedCodecs?.take(8)?.joinToString(", ").orEmpty()
                RowItem(stringResource(R.string.device_hw_accel), hw + if (hwCount > 8) " (+${hwCount - 8})" else "")
            }
        }

        // ═══════ 13. 热管理 (新增) ═══════
        if (detail?.thermalZoneCount?.compareTo(0) == 1) {
            SectionCard(stringResource(R.string.device_section_thermal)) {
                RowItem(stringResource(R.string.device_thermal_zones), "${detail!!.thermalZoneCount}")
                val types = detail!!.thermalZoneTypes
                if (types.isNotEmpty()) {
                    // 去重并取前 8 个
                    val uniqueTypes = types.distinct().take(8)
                    RowItem(stringResource(R.string.device_thermal_zone_types), uniqueTypes.joinToString(", ") +
                        if (types.distinct().size > 8) "…" else "")
                }
            }
        }

        // ═══════ 14. DRM ═══════
        SectionCard(stringResource(R.string.device_section_drm)) {
            RowItem(stringResource(R.string.device_widevine_level), detail?.widevineLevel ?: stringResource(R.string.common_detecting))
            RowItem(stringResource(R.string.device_drm_schemes), detail?.drmSchemes?.joinToString(", ").orEmpty())
        }

        // ═══════ 15. 安全 ═══════
        SectionCard(stringResource(R.string.device_section_security)) {
            RowItem(stringResource(R.string.device_tee), if (detail?.teeSupported == true) stringResource(R.string.common_yes) else stringResource(R.string.common_not_detected),
                if (detail?.teeSupported == true) SuccessNeon else WarningNeon)
            RowItem(stringResource(R.string.device_verified_boot), if (detail?.secureBootEnabled == true) stringResource(R.string.device_activated) else stringResource(R.string.device_not_activated),
                if (detail?.secureBootEnabled == true) SuccessNeon else WarningNeon)
            RowItem(stringResource(R.string.device_file_encryption), detail?.fileEncryption?.takeIf { it.isNotEmpty() } ?: "-")
            RowItem(stringResource(R.string.device_selinux), if (detail?.selinuxEnforcing == true) "Enforcing" else "Permissive",
                if (detail?.selinuxEnforcing == true) SuccessNeon else WarningNeon)
        }

        // ═══════ 16. 设备标识符 (新增) ═══════
        val hasAndroidId = detail?.androidId?.isNotEmpty() == true
        val hasSerial = detail?.serialNumber?.isNotEmpty() == true
        val hasHwSerial = detail?.hardwareSerial?.isNotEmpty() == true
        val hasFingerprint = detail?.deviceFingerprint?.isNotEmpty() == true
        if (hasAndroidId || hasSerial || hasHwSerial || hasFingerprint) {
            SectionCard(stringResource(R.string.device_section_identifiers)) {
                if (hasFingerprint) {
                    RowItem(stringResource(R.string.device_fingerprint), detail!!.deviceFingerprint,
                        valueColor = NeonCyan)
                }
                if (hasAndroidId) {
                    RowItem("Android ID", detail!!.androidId,
                        valueColor = NeonCyan)
                }
                if (hasSerial) {
                    RowItem(stringResource(R.string.device_serial_number), detail!!.serialNumber,
                        valueColor = NeonCyan)
                }
                if (hasHwSerial) {
                    RowItem(stringResource(R.string.device_hardware_serial), detail!!.hardwareSerial,
                        valueColor = NeonCyan)
                }
            }
        }

        // ═══════ 16.2 Bootloader 状态 ═══════
        val bootloaderVersion = try { android.os.Build.BOOTLOADER.takeIf { it.isNotEmpty() } } catch (_: Throwable) { null }
        val bootUnlocked = detail?.bootloaderUnlocked
        if (bootloaderVersion != null || bootUnlocked != null) {
            SectionCard(stringResource(R.string.device_section_bootloader)) {
                if (bootloaderVersion != null) {
                    RowItem(stringResource(R.string.device_version), bootloaderVersion)
                }
                if (bootUnlocked != null) {
                    val statusText = if (bootUnlocked) stringResource(R.string.device_unlocked) else stringResource(R.string.device_locked)
                    RowItem(stringResource(R.string.device_unlock_status), statusText,
                        valueColor = if (bootUnlocked) Color(0xFFEF5350) else SuccessNeon)
                }
                detail?.secureBootEnabled?.let {
                    RowItem(stringResource(R.string.device_verify_boot), if (it) stringResource(R.string.device_enabled) else stringResource(R.string.device_not_enabled))
                }
            }
        }

        // ═══════ 16.5 运行环境 (新增) ═══════
        val javaRuntime = detail?.javaRuntimeVersion?.takeIf { it.isNotEmpty() }
        val javaVm = detail?.javaVmName?.takeIf { it.isNotEmpty() }
        val openssl = detail?.opensslVersion?.takeIf { it.isNotEmpty() }
        val buildTime = detail?.buildTimestamp?.takeIf { it.isNotEmpty() && it != "未知" }
        if (javaRuntime != null || openssl != null || buildTime != null) {
            SectionCard(stringResource(R.string.device_section_runtime)) {
                if (javaRuntime != null) {
                    RowItem(stringResource(R.string.device_java_runtime), javaRuntime,
                        valueColor = NeonPurpleBright)
                } else if (javaVm != null) {
                    RowItem(stringResource(R.string.device_runtime), javaVm,
                        valueColor = NeonPurpleBright)
                }
                if (openssl != null) {
                    RowItem(stringResource(R.string.device_openssl_version), openssl,
                        valueColor = NeonPurpleBright)
                }
                if (buildTime != null) {
                    RowItem(stringResource(R.string.device_build_time), buildTime,
                        valueColor = NeonPurpleBright)
                }
            }
        }

        // ═══════ OEM 专区 ═══════
        if (oem != null && oem!!.oem != "AOSP") {
            Text(stringResource(R.string.device_system_recognition), fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp))

            SectionCard("${oem!!.osName} · ${oem!!.oem}") {
                RowItem(stringResource(R.string.device_version), oem!!.osVersion.ifEmpty { stringResource(R.string.common_detecting) })
                RowItem(stringResource(R.string.device_build_id), oem!!.buildDisplayId.ifEmpty { "-" })
                RowItem(stringResource(R.string.device_security_patch), oem!!.securityPatch.ifEmpty { "-" })
            }

            when (oem!!.oem) {
                "Xiaomi" -> SectionCard(stringResource(R.string.device_section_xiaomi)) {
                    RowItem(stringResource(R.string.device_version), oem!!.miuiVersion.ifEmpty { "-" })
                    RowItem(stringResource(R.string.device_region), oem!!.miuiRegion.ifEmpty { "-" })
                    RowItem(stringResource(R.string.device_hardware_model), oem!!.miuiHardware.ifEmpty { "-" })
                    oem!!.miuiFeatures.takeIf { it.isNotBlank() }?.let {
                        RowItem(stringResource(R.string.device_features), it.trim())
                    }
                }
                "OPPO" -> SectionCard(stringResource(R.string.device_section_oppo)) {
                    RowItem(stringResource(R.string.device_version), oem!!.oppoVersion.ifEmpty { "-" })
                    RowItem(stringResource(R.string.device_screen_ratio), oem!!.oppoScreenRatio.ifEmpty { "-" })
                    oem!!.oplusCharging.takeIf { it.isNotBlank() }?.let {
                        RowItem(stringResource(R.string.device_charging_solution), it.trim())
                    }
                }
                "Vivo" -> SectionCard(stringResource(R.string.device_section_vivo)) {
                    RowItem(stringResource(R.string.device_version), oem!!.vivoOsVersion.ifEmpty { "-" })
                    RowItem(stringResource(R.string.device_solution), oem!!.vivoProductSolution.ifEmpty { "-" })
                    RowItem(stringResource(R.string.device_model), oem!!.vivoModel.ifEmpty { "-" })
                }
                "Samsung" -> SectionCard(stringResource(R.string.device_section_samsung)) {
                    RowItem(stringResource(R.string.device_oneui_version), oem!!.osVersion.ifEmpty { "-" })
                    oem!!.buildDisplayId.takeIf { it.isNotBlank() }?.let {
                        RowItem("Build", it)
                    }
                }
            }

            // 性能模式
            SectionCard(stringResource(R.string.device_section_perf_mode)) {
                RowItem(stringResource(R.string.device_game_mode), if (oem!!.gameModeSupported) stringResource(R.string.common_yes) else stringResource(R.string.device_not_activated))
                RowItem(stringResource(R.string.device_current_scheduler), oem!!.powerModeCurrent.ifEmpty { stringResource(R.string.device_balanced_mode) })
            }

            // 厂商子系统
            val subsystems = buildList {
                oem!!.aiEngineInfo.takeIf { it.isNotBlank() }?.let { add(stringResource(R.string.device_ai_engine) to it) }
                oem!!.memoryFusion.takeIf { it.isNotBlank() }?.let { add(stringResource(R.string.device_memory_expansion) to it) }
                oem!!.thermalSolution.takeIf { it.isNotBlank() }?.let { add(stringResource(R.string.device_thermal_solution) to it) }
                oem!!.storageBoost.takeIf { it.isNotBlank() }?.let { add(stringResource(R.string.device_storage_boost) to it) }
                oem!!.displayFeatures.takeIf { it.isNotBlank() }?.let { add(stringResource(R.string.device_display_features) to it) }
            }
            if (subsystems.isNotEmpty()) {
                SectionCard(stringResource(R.string.device_section_vendor_subsystems)) {
                    subsystems.forEach { (k, v) -> RowItem(k, v) }
                }
            }

            // 原始属性
            val props = oem!!.rawProperties
            if (props.isNotEmpty()) {
                SectionCard(stringResource(R.string.device_section_vendor_props, props.size)) {
                    props.forEach { (k, v) ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                            Text(k, fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(v, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = true)
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
            Text("${sensor.facing}${stringResource(R.string.device_camera_facing_suffix)}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = NeonPurple)
        }
        if (sensor.resolution.isNotEmpty())
            RowItem(stringResource(R.string.device_resolution), sensor.resolution)
        if (sensor.aperture.isNotEmpty())
            RowItem(stringResource(R.string.device_camera_aperture), sensor.aperture)
        if (sensor.focalLength.isNotEmpty())
            RowItem(stringResource(R.string.device_camera_focal_length), sensor.focalLength)
        if (sensor.pixelSize.isNotEmpty())
            RowItem(stringResource(R.string.device_camera_pixel_size), sensor.pixelSize)
        val features = buildList {
            if (sensor.oisSupported) add("OIS")
            if (sensor.eisSupported) add("EIS")
            if (sensor.flashSupported) add(stringResource(R.string.device_camera_flash))
        }
        if (features.isNotEmpty())
            RowItem(stringResource(R.string.device_features), features.joinToString(" · "))
    }
}

// ═══════ 共享组件 ═══════

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
