package com.example.deviceinfoviewer.ui.oem

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
import com.example.deviceinfoviewer.ui.theme.NeonPurple
import com.example.deviceinfoviewer.ui.theme.NeonPurpleBright
import com.example.deviceinfoviewer.ui.theme.SuccessNeon
import com.example.deviceinfoviewer.ui.theme.WarningNeon
import org.koin.androidx.compose.koinViewModel

@Composable
fun OemScreen(viewModel: OemViewModel = koinViewModel()) {
    val oem by viewModel.oemInfo.observeAsState()

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("系统识别", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

        val o = oem

        // 系统信息
        SectionCard("${o?.osName ?: "Android"} · ${o?.oem ?: ""}") {
            val androidLabel = o?.androidVersion?.let { v -> "Android $v (API ${o?.sdkLevel})" } ?: "检测中"
            RowItem("Android", androidLabel)
            RowItem("版本", o?.osVersion?.takeIf { it.isNotEmpty() } ?: "检测中")
            RowItem("Build", o?.buildDisplayId?.takeIf { it.isNotEmpty() } ?: "-")
            RowItem("安全补丁", o?.securityPatch?.takeIf { it.isNotEmpty() } ?: "-")
        }

        // SoC 信息
        SectionCard("芯片平台") {
            RowItem("平台", o?.boardPlatform?.takeIf { it.isNotEmpty() } ?: "-")
            RowItem("SoC 制造商", o?.socManufacturer?.takeIf { it.isNotEmpty() } ?: "-")
            RowItem("SoC 型号", o?.socModel?.takeIf { it.isNotEmpty() } ?: "-")
        }

        // 子系统特性 (Android 16)
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
        if (o?.oem == "Xiaomi") {
            SectionCard("小米 HyperOS/MIUI") {
                RowItem("MIUI 版本", o?.miuiVersion?.takeIf { it.isNotEmpty() } ?: "-")
                RowItem("Region", o?.miuiRegion?.takeIf { it.isNotEmpty() } ?: "-")
                RowItem("Mod Device", o?.miuiHardware?.takeIf { it.isNotEmpty() } ?: "-")
                RowItem("Features", o?.miuiFeatures?.takeIf { it.isNotBlank() } ?: "-")
            }
        }

        // OPPO 专区
        if (o?.oem == "OPPO") {
            SectionCard("OPPO ColorOS") {
                RowItem("ColorOS 版本", o?.oppoVersion?.takeIf { it.isNotEmpty() } ?: "-")
                RowItem("屏幕比例", o?.oppoScreenRatio?.takeIf { it.isNotEmpty() } ?: "-")
                RowItem("充电信息", o?.oplusCharging?.takeIf { it.isNotBlank() } ?: "-")
            }
        }

        // Vivo 专区
        if (o?.oem == "Vivo") {
            SectionCard("Vivo OriginOS") {
                RowItem("OriginOS 版本", o?.vivoOsVersion?.takeIf { it.isNotEmpty() } ?: "-")
                RowItem("Product Solution", o?.vivoProductSolution?.takeIf { it.isNotEmpty() } ?: "-")
                RowItem("Model", o?.vivoModel?.takeIf { it.isNotEmpty() } ?: "-")
            }
        }

        // 通用：性能模式（增强版）
        SectionCard("性能模式") {
            val gameColor = if (o?.gameModeSupported == true) SuccessNeon else WarningNeon
            val perfColor = if (o?.highPerformanceMode == true) SuccessNeon else WarningNeon

            RowItemWithColor(
                "游戏模式",
                if (o?.gameModeSupported == true) "已激活 ✓" else "未激活",
                gameColor
            )
            RowItemWithColor(
                "高性能模式",
                if (o?.highPerformanceMode == true) "已开启 ✓" else "未开启",
                perfColor
            )
        }

        // 厂商原始属性
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
private fun RowItem(label: String, value: String) {
    if (value.isNotBlank()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.4f))
            Text(value, fontSize = 13.sp, color = NeonPurpleBright, modifier = Modifier.weight(0.6f))
        }
    }
}

@Composable
private fun RowItemWithColor(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.4f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = color, modifier = Modifier.weight(0.6f))
    }
}

@Composable
private fun keyValueItem(key: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(key, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        Text(value, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}
