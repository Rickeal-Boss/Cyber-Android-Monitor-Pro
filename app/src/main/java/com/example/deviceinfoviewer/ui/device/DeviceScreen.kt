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
import com.example.deviceinfoviewer.ui.oem.OemViewModel
import com.example.deviceinfoviewer.ui.theme.NeonPurple
import com.example.deviceinfoviewer.ui.theme.NeonPurpleBright
import com.example.deviceinfoviewer.ui.theme.SuccessNeon
import com.example.deviceinfoviewer.ui.theme.WarningNeon
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
        Text("设备详情", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

        SectionCard("屏幕") {
            RowItem("分辨率", detail?.resolution ?: "")
            RowItem("密度", "${detail?.densityDpi ?: 0} dpi (${detail?.density?.let { "%.1f".format(it) } ?: "-"}×)")
            RowItem("刷新率", detail?.refreshRateHz?.takeIf { it > 0 }?.let { "%.1f Hz".format(it) } ?: "-")
            RowItem("物理尺寸", detail?.physicalSizeInches?.takeIf { it > 0 }?.let { "%.1f\"".format(it) } ?: "-")
            detail?.hdrCapabilities?.takeIf { it.isNotEmpty() }?.let { RowItem("HDR", it.joinToString(", ")) }
            RowItem("触控", detail?.touchscreenType ?: "")
        }

        SectionCard("图形 (OpenGL ES)") {
            RowItem("版本", detail?.glEsVersion ?: "")
            RowItem("GPU 型号", detail?.glRenderer ?: "")
            RowItem("厂商", detail?.glVendor ?: "")
            RowItem("扩展数量", "${detail?.glExtensions?.size ?: 0}")
        }

        // Vulkan
        val vkVersion = detail?.vulkanVersion?.takeIf { it.isNotEmpty() }
        val vkLevel = detail?.vulkanApiLevel?.takeIf { it.isNotEmpty() }
        if (vkVersion != null || vkLevel != null) {
            SectionCard("Vulkan") {
                if (vkVersion != null) RowItem("API 版本", vkVersion)
                if (vkLevel != null) RowItem("硬件级别", vkLevel)
                RowItem(
                    "光线追踪",
                    if (detail?.rayTracingSupported == true) "支持 ✓" else "不支持",
                    if (detail?.rayTracingSupported == true) SuccessNeon else WarningNeon
                )
            }
        }

        SectionCard("多媒体解码器") {
            val v = detail?.videoCodecs?.joinToString(", ").orEmpty()
            if (v.isNotBlank()) RowItem("视频解码器", v)
            val a = detail?.audioCodecs?.joinToString(", ").orEmpty()
            if (a.isNotBlank()) RowItem("音频解码器", a)
        }

        SectionCard("DRM / Widevine") {
            RowItem("Widevine 等级", detail?.widevineLevel ?: "检测中")
            RowItem("支持方案", detail?.drmSchemes?.joinToString(", ").orEmpty())
        }

        SectionCard("SIM / 通讯") {
            RowItem("运营商", detail?.simOperator?.takeIf { it.isNotEmpty() } ?: "-")
            RowItem("MCC/MNC", detail?.simMccMnc?.takeIf { it != "0" } ?: "-")
            RowItem("网络制式", detail?.phoneType ?: "")
            RowItem("双卡", if (detail?.isDualSim == true) "支持" else "不支持")
        }

        SectionCard("连接") {
            RowItem("蓝牙", if (detail?.bluetoothSupported == true) "支持 \u00b7 ${detail?.bluetoothName}" else "不支持")
            RowItem("NFC", if (detail?.hasNfc == true) "支持" else "不支持")
            RowItem("USB", if (detail?.usbConnected == true) "已连接" else "未连接")
        }

        SectionCard("相机") {
            RowItem("摄像头", detail?.cameraIds?.joinToString(", ").orEmpty().ifEmpty { "未检测" })
        }

        // ── OEM 专区 ──
        if (oem != null && oem!!.oem != "AOSP") {
            Text("系统识别", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp))

            SectionCard("${oem!!.osName} \u00b7 ${oem!!.oem}") {
                RowItem("版本", oem!!.osVersion.ifEmpty { "检测中" })
                RowItem("Build", oem!!.buildDisplayId.ifEmpty { "-" })
                RowItem("安全补丁", oem!!.securityPatch.ifEmpty { "-" })
                RowItem("平台", oem!!.boardPlatform.ifEmpty { "-" })
                if (oem!!.socModel.isNotEmpty())
                    RowItem("SoC", "${oem!!.socManufacturer} ${oem!!.socModel}")
            }

            when (oem!!.oem) {
                "Xiaomi" -> SectionCard("小米 HyperOS/MIUI") {
                    RowItem("版本", oem!!.miuiVersion.ifEmpty { "-" })
                    RowItem("Region", oem!!.miuiRegion.ifEmpty { "-" })
                    RowItem("硬件型号", oem!!.miuiHardware.ifEmpty { "-" })
                    oem!!.miuiFeatures.takeIf { it.isNotBlank() }?.let {
                        RowItem("Features", it.trim())
                    }
                }
                "OPPO" -> SectionCard("OPPO ColorOS") {
                    RowItem("版本", oem!!.oppoVersion.ifEmpty { "-" })
                    RowItem("屏幕比例", oem!!.oppoScreenRatio.ifEmpty { "-" })
                    oem!!.oplusCharging.takeIf { it.isNotBlank() }?.let {
                        RowItem("充电", it.trim())
                    }
                }
                "Vivo" -> SectionCard("Vivo OriginOS") {
                    RowItem("版本", oem!!.vivoOsVersion.ifEmpty { "-" })
                    RowItem("方案", oem!!.vivoProductSolution.ifEmpty { "-" })
                    RowItem("型号", oem!!.vivoModel.ifEmpty { "-" })
                }
            }

            SectionCard("性能模式") {
                RowItem("游戏模式", if (oem!!.gameModeSupported) "支持" else "未激活")
                RowItem("高性能", if (oem!!.highPerformanceMode) "已开启" else "未激活")
            }

            val props = oem!!.rawProperties
            if (props.isNotEmpty()) {
                SectionCard("厂商属性 (${props.size} 条)") {
                    props.forEach { (k, v) ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                            Text(k, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                            Text(v, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
}

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
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.4f))
            Text(value, fontSize = 13.sp, color = valueColor, modifier = Modifier.weight(0.6f))
        }
    }
}
