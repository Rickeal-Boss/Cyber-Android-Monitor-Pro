package com.example.deviceinfoviewer.ui.settings

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
import androidx.compose.material.icons.filled.*
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

private val refreshOptions = listOf(500L, 1000L, 2000L, 3000L, 5000L)

private fun msToLabel(ms: Long): String = when {
    ms < 1000 -> "0.5s"
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
    ModuleIntervalConfig("CPU", Icons.Default.PlayArrow,
        "频率、核心、温度数据刷新",
        { it.getCpuRefreshMs() }, { vm, ms -> vm.setCpuRefreshMs(ms) }),
    ModuleIntervalConfig("GPU", Icons.Default.Settings,
        "负载、频率、温度数据刷新",
        { it.getGpuRefreshMs() }, { vm, ms -> vm.setGpuRefreshMs(ms) }),
    ModuleIntervalConfig("内存", Icons.Default.Star,
        "内存 / ZRAM 数据刷新",
        { it.getMemoryRefreshMs() }, { vm, ms -> vm.setMemoryRefreshMs(ms) }),
    ModuleIntervalConfig("电池", Icons.Default.Favorite,
        "容量、充放电、温度数据刷新",
        { it.getBatteryRefreshMs() }, { vm, ms -> vm.setBatteryRefreshMs(ms) }),
    ModuleIntervalConfig("网络", Icons.Default.Share,
        "WiFi / 信号 / IP / 流量刷新",
        { it.getNetworkRefreshMs() }, { vm, ms -> vm.setNetworkRefreshMs(ms) }),
    ModuleIntervalConfig("GPS", Icons.Default.Info,
        "卫星列表与坐标刷新",
        { it.getGpsRefreshMs() }, { vm, ms -> vm.setGpsRefreshMs(ms) }),
    ModuleIntervalConfig("传感器", Icons.Default.Search,
        "传感器数据采集刷新",
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

        Text("模块刷新频率", fontSize = 14.sp,
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
private fun ModuleIntervalCard(cfg: ModuleIntervalConfig, viewModel: SettingsViewModel) {
    val savedMs = cfg.getMs(viewModel)
    val initialMs = if (savedMs > 0) savedMs else 2000L // default 2s
    var currentMs by remember { mutableFloatStateOf(initialMs.toFloat()) }

    val curLabel = msToLabel(currentMs.toLong())

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = NeonPurple.copy(alpha = 0.08f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(cfg.icon, null, tint = NeonPurpleBright, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                    Column {
                        Text(cfg.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                            color = TextPrimary)
                        Text(cfg.desc, fontSize = 11.sp, color = TextSecondary)
                    }
                }
                Text(curLabel, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                    color = NeonPurpleBright)
            }
            Spacer(Modifier.height(10.dp))

            Slider(
                value = currentMs,
                onValueChange = { newVal ->
                    currentMs = newVal
                    cfg.setMs(viewModel, newVal.toLong())
                },
                valueRange = 500f..5000f,
                steps = refreshOptions.size - 2,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = NeonPurpleBright,
                    activeTrackColor = NeonPurple,
                    inactiveTrackColor = NeonSteelBlue.copy(alpha = 0.3f)
                )
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                refreshOptions.forEach { opt ->
                    Text(msToLabel(opt), fontSize = 10.sp,
                        color = NeonSteelBlue.copy(alpha = 0.7f))
                }
            }
        }
    }
}
