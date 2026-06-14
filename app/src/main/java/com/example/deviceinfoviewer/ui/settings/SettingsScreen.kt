package com.example.deviceinfoviewer.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.deviceinfoviewer.ui.theme.NeonPurple
import com.example.deviceinfoviewer.ui.theme.NeonPurpleBright
import com.example.deviceinfoviewer.ui.theme.NeonSteelBlue
import com.example.deviceinfoviewer.ui.theme.TextPrimary
import com.example.deviceinfoviewer.ui.theme.TextSecondary
import org.koin.androidx.compose.koinViewModel

private val refreshOptions = listOf(200L, 500L, 1000L, 2000L, 5000L, 10000L, 30000L)

private fun msToLabel(ms: Long): String = when {
    ms < 1000 -> "${(ms / 100)}.${(ms % 100 / 10)}s"
    ms < 60000 -> "${ms / 1000}s"
    else -> "${ms / 60000}min"
}

private data class ModuleIntervalConfig(
    val name: String,
    val icon: ImageVector,
    val desc: String,
    val getMs: (SettingsViewModel) -> Long,
    val setMs: (SettingsViewModel, Long) -> Unit,
)

private val moduleConfigs = listOf(
    ModuleIntervalConfig("CPU", Icons.Default.Speed,
        "CPU 频率、核心、温度刷新频率",
        { it.getCpuRefreshMs() }, { vm, ms -> vm.setCpuRefreshMs(ms) }),
    ModuleIntervalConfig("GPU", Icons.Default.GridView,
        "GPU 负载、频率、温度刷新频率",
        { it.getGpuRefreshMs() }, { vm, ms -> vm.setGpuRefreshMs(ms) }),
    ModuleIntervalConfig("内存", Icons.Default.Memory,
        "内存 / ZRAM 使用数据刷新频率",
        { it.getMemoryRefreshMs() }, { vm, ms -> vm.setMemoryRefreshMs(ms) }),
    ModuleIntervalConfig("电池", Icons.Default.BatteryChargingFull,
        "电池容量、充放电、温度刷新频率",
        { it.getBatteryRefreshMs() }, { vm, ms -> vm.setBatteryRefreshMs(ms) }),
    ModuleIntervalConfig("网络", Icons.Default.SignalWifi4Bar,
        "WiFi / 信号 / IP / 流量刷新频率",
        { it.getNetworkRefreshMs() }, { vm, ms -> vm.setNetworkRefreshMs(ms) }),
    ModuleIntervalConfig("GPS", Icons.Default.GpsFixed,
        "GPS 卫星列表与坐标刷新频率",
        { it.getGpsRefreshMs() }, { vm, ms -> vm.setGpsRefreshMs(ms) }),
    ModuleIntervalConfig("传感器", Icons.Default.Sensors,
        "传感器数据采集刷新频率",
        { it.getSensorsRefreshMs() }, { vm, ms -> vm.setSensorsRefreshMs(ms) }),
)

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = koinViewModel()) {
    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 60.dp, bottom = 16.dp)
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("设置", fontSize = 18.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface)

        // ═══ 全局刷新频率 ═══
        GlobalIntervalCard(viewModel)

        // ═══ 分模块刷新频率 ═══
        Text("分模块刷新频率", fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        moduleConfigs.forEach { cfg ->
            ModuleIntervalCard(cfg, viewModel)
        }

        // ═══ App 信息 ═══
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Cyber Android Monitor Pro", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("v2.0.202.0", fontSize = 13.sp, color = TextSecondary)
                Text("Kotlin 2.1.0 · Compose · Batman Theme",
                    fontSize = 12.sp, color = TextSecondary.copy(alpha = 0.6f))
                Text("by Rickeal-Boss", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = NeonPurple)
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun GlobalIntervalCard(viewModel: SettingsViewModel) {
    var currentMs by remember { mutableFloatStateOf(viewModel.getIntervalMs().toFloat()) }
    val curLabel = msToLabel(currentMs.toLong())

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = NeonPurple.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("全局刷新频率",
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(curLabel, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = NeonPurpleBright)
            }
            Text("默认刷新间隔，所有模块生效",
                fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.height(8.dp))

            Slider(
                value = currentMs,
                onValueChange = { currentMs = it },
                onValueChangeFinished = { viewModel.setIntervalMs(currentMs.toLong()) },
                valueRange = 200f..30000f,
                steps = refreshOptions.size - 2,  // endpoints excluded
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = NeonPurpleBright,
                    activeTrackColor = NeonPurple,
                    inactiveTrackColor = NeonSteelBlue.copy(alpha = 0.3f)
                )
            )
            // 刻度标签
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                refreshOptions.forEach { opt ->
                    Text(msToLabel(opt), fontSize = 9.sp,
                        color = if ((currentMs - opt).absoluteValue < 300f) NeonPurpleBright
                                else TextSecondary.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
private fun ModuleIntervalCard(cfg: ModuleIntervalConfig, viewModel: SettingsViewModel) {
    val initialMs = cfg.getMs(viewModel)
    val effectiveMs = if (initialMs > 0) initialMs else viewModel.getIntervalMs()
    var currentMs by remember { mutableFloatStateOf(effectiveMs.toFloat()) }

    val curLabel = msToLabel(currentMs.toLong())
    val isUsingGlobal = cfg.getMs(viewModel) == 0L

    val accentColor by animateColorAsState(
        targetValue = if (isUsingGlobal) NeonSteelBlue else NeonPurple,
        animationSpec = tween(200),
        label = "accent"
    )

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = accentColor.copy(alpha = if (isUsingGlobal) 0.05f else 0.08f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(cfg.icon, null, tint = accentColor, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(cfg.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                            color = TextPrimary)
                        Text(curLabel, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                            color = accentColor)
                    }
                    Text(cfg.desc, fontSize = 12.sp, color = TextSecondary,
                        modifier = Modifier.padding(top = 2.dp))
                }
            }
            Spacer(Modifier.height(8.dp))

            Slider(
                value = currentMs,
                onValueChange = { newVal ->
                    currentMs = newVal
                    cfg.setMs(viewModel, newVal.toLong())
                },
                valueRange = 200f..30000f,
                steps = refreshOptions.size - 2,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = accentColor,
                    activeTrackColor = accentColor,
                    inactiveTrackColor = NeonSteelBlue.copy(alpha = 0.3f)
                )
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                refreshOptions.forEach { opt ->
                    Text(msToLabel(opt), fontSize = 9.sp,
                        color = if ((currentMs - opt).absoluteValue < 300f) accentColor
                                else TextSecondary.copy(alpha = 0.5f))
                }
            }
        }
    }
}
