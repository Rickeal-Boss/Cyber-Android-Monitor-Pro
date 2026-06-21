package com.example.deviceinfoviewer.ui.floatwindow

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.deviceinfoviewer.R
import com.example.deviceinfoviewer.service.FloatingWindowConfig
import com.example.deviceinfoviewer.service.FloatingWindowService
import com.example.deviceinfoviewer.ui.theme.NeonPurple
import com.example.deviceinfoviewer.ui.theme.NeonPurpleBright
import com.example.deviceinfoviewer.ui.theme.NeonSteelBlue
import com.example.deviceinfoviewer.ui.theme.TextPrimary
import com.example.deviceinfoviewer.ui.theme.TextSecondary

// ═══════ 刷新间隔选项 (Step values) ═══════
private val refreshStepOptions = listOf(200L, 500L, 1000L, 2000L, 5000L, 10000L, 30000L)

private fun msToLabel(ms: Long): String = when {
    ms < 1000 -> "0.${ms / 100}s"
    ms < 60000 -> "${ms / 1000}s"
    else -> "${ms / 60000}min"
}

// ═══════ 每指标间隔配置 ═══════
private data class FloatMetricConfig(
    val key: String,
    val nameResId: Int,
    val icon: ImageVector,
    val getMs: () -> Int,
    val setMs: (Int) -> Unit,
)

private val floatMetrics = listOf(
    FloatMetricConfig("gpu_usage", R.string.float_gpu_usage, Icons.Filled.Schedule,
        { FloatingWindowConfig.gpuUsageRefreshMs }, { FloatingWindowConfig.gpuUsageRefreshMs = it }),
    FloatMetricConfig("cpu_temp", R.string.float_cpu_temp, Icons.Filled.Schedule,
        { FloatingWindowConfig.cpuTempRefreshMs }, { FloatingWindowConfig.cpuTempRefreshMs = it }),
    FloatMetricConfig("gpu_temp", R.string.float_gpu_temp, Icons.Filled.Schedule,
        { FloatingWindowConfig.gpuTempRefreshMs }, { FloatingWindowConfig.gpuTempRefreshMs = it }),
    FloatMetricConfig("cpu_freq", R.string.float_cpu_freq, Icons.Filled.Schedule,
        { FloatingWindowConfig.cpuFreqRefreshMs }, { FloatingWindowConfig.cpuFreqRefreshMs = it }),
    FloatMetricConfig("ram", R.string.float_ram, Icons.Filled.Schedule,
        { FloatingWindowConfig.ramRefreshMs }, { FloatingWindowConfig.ramRefreshMs = it }),
    FloatMetricConfig("battery_temp", R.string.float_battery_temp, Icons.Filled.Schedule,
        { FloatingWindowConfig.batteryTempRefreshMs }, { FloatingWindowConfig.batteryTempRefreshMs = it }),
    FloatMetricConfig("battery_cur", R.string.float_battery_current, Icons.Filled.Schedule,
        { FloatingWindowConfig.batteryCurRefreshMs }, { FloatingWindowConfig.batteryCurRefreshMs = it }),
    FloatMetricConfig("battery_pow", R.string.float_battery_power, Icons.Filled.Schedule,
        { FloatingWindowConfig.batteryPowRefreshMs }, { FloatingWindowConfig.batteryPowRefreshMs = it }),
    FloatMetricConfig("fps", R.string.float_fps, Icons.Filled.Schedule,
        { FloatingWindowConfig.fpsRefreshMs }, { FloatingWindowConfig.fpsRefreshMs = it }),
)

@Composable
fun FloatingWindowScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var enabled by remember { mutableStateOf(FloatingWindowConfig.enabled) }
    var showGpuUsage by remember { mutableStateOf(FloatingWindowConfig.showGpuUsage) }
    var showCpuTemp by remember { mutableStateOf(FloatingWindowConfig.showCpuTemp) }
    var showGpuTemp by remember { mutableStateOf(FloatingWindowConfig.showGpuTemp) }
    var showCpuFreq by remember { mutableStateOf(FloatingWindowConfig.showCpuFreq) }
    var showRam by remember { mutableStateOf(FloatingWindowConfig.showRam) }
    var showBatteryTemp by remember { mutableStateOf(FloatingWindowConfig.showBatteryTemp) }
    var showBatteryCur by remember { mutableStateOf(FloatingWindowConfig.showBatteryCurrent) }
    var showBatteryPow by remember { mutableStateOf(FloatingWindowConfig.showBatteryPower) }
    var showFps by remember { mutableStateOf(FloatingWindowConfig.showFps) }

    Column(
        modifier = Modifier.padding(top = 56.dp).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(stringResource(R.string.float_title), fontSize = 20.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface)

        // 总开关
        Card(
            Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.float_enable), fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f))
                Switch(
                    checked = enabled,
                    onCheckedChange = { v ->
                        if (v) {
                            if (canDrawOverlays(ctx)) {
                                enabled = true; FloatingWindowConfig.enabled = true
                                ctx.startService(Intent(ctx, FloatingWindowService::class.java))
                            } else {
                                Toast.makeText(ctx, ctx.getString(R.string.float_permission_toast), Toast.LENGTH_LONG).show()
                                ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${ctx.packageName}")).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                })
                            }
                        } else {
                            enabled = false; FloatingWindowConfig.enabled = false
                            ctx.stopService(Intent(ctx, FloatingWindowService::class.java))
                        }
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = NeonPurple)
                )
            }
        }

        if (enabled) {
            Text(stringResource(R.string.float_section_realtime), fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface)

            CheckItem(stringResource(R.string.float_gpu_usage), showGpuUsage) { showGpuUsage = it; FloatingWindowConfig.showGpuUsage = it }
            if (showGpuUsage) IntervalCard(floatMetrics[0])
            CheckItem(stringResource(R.string.float_cpu_temp), showCpuTemp) { showCpuTemp = it; FloatingWindowConfig.showCpuTemp = it }
            if (showCpuTemp) IntervalCard(floatMetrics[1])
            CheckItem(stringResource(R.string.float_gpu_temp), showGpuTemp) { showGpuTemp = it; FloatingWindowConfig.showGpuTemp = it }
            if (showGpuTemp) IntervalCard(floatMetrics[2])
            CheckItem(stringResource(R.string.float_cpu_freq), showCpuFreq) { showCpuFreq = it; FloatingWindowConfig.showCpuFreq = it }
            if (showCpuFreq) IntervalCard(floatMetrics[3])
            CheckItem(stringResource(R.string.float_ram), showRam) { showRam = it; FloatingWindowConfig.showRam = it }
            if (showRam) IntervalCard(floatMetrics[4])
            CheckItem(stringResource(R.string.float_battery_temp), showBatteryTemp) { showBatteryTemp = it; FloatingWindowConfig.showBatteryTemp = it }
            if (showBatteryTemp) IntervalCard(floatMetrics[5])
            CheckItem(stringResource(R.string.float_battery_current), showBatteryCur) { showBatteryCur = it; FloatingWindowConfig.showBatteryCurrent = it }
            if (showBatteryCur) IntervalCard(floatMetrics[6])
            CheckItem(stringResource(R.string.float_battery_power), showBatteryPow) { showBatteryPow = it; FloatingWindowConfig.showBatteryPower = it }
            if (showBatteryPow) IntervalCard(floatMetrics[7])

            Text(stringResource(R.string.float_section_system), fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp))
            CheckItem(stringResource(R.string.float_fps), showFps) { showFps = it; FloatingWindowConfig.showFps = it }
            if (showFps) IntervalCard(floatMetrics[8])
        }

        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.float_overlay_hint),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
    }
}

// ═══════ 刷新间隔卡片 ═══════
// ★ 使用 onValueChangeFinished 延迟写入 SP，避免拖拽时频繁 IO
@Composable
private fun IntervalCard(cfg: FloatMetricConfig) {
    // 缓存的初始值，避免每次重组读 SP
    val savedMs = remember(cfg) { cfg.getMs() }
    var currentMs by remember { mutableFloatStateOf(savedMs.toFloat()) }

    val curLabel = msToLabel(currentMs.toLong())
    val metricName = stringResource(cfg.nameResId)

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = NeonPurple.copy(alpha = 0.06f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(cfg.icon, null, tint = NeonSteelBlue.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.float_interval_label, metricName), fontSize = 12.sp, color = TextSecondary)
                }
                Text(curLabel, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = NeonPurpleBright)
            }

            Slider(
                value = currentMs,
                onValueChange = { newVal ->
                    // ★ 只更新本地 state，不写 SP — 避免拖拽中频繁 IO
                    currentMs = newVal
                },
                onValueChangeFinished = {
                    // ★ 手指抬起时一次性写入 FloatingWindowConfig（SP + 服务下次循环立即生效）
                    cfg.setMs(currentMs.toInt())
                },
                valueRange = refreshStepOptions.first().toFloat()..refreshStepOptions.last().toFloat(),
                steps = refreshStepOptions.size - 2,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = NeonPurpleBright,
                    activeTrackColor = NeonPurple,
                    inactiveTrackColor = NeonSteelBlue.copy(alpha = 0.3f)
                )
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                refreshStepOptions.forEach { opt ->
                    Text(msToLabel(opt), fontSize = 9.sp,
                        color = NeonSteelBlue.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
private fun CheckItem(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = checked, onCheckedChange = onToggle,
            colors = CheckboxDefaults.colors(
                checkedColor = NeonPurple, checkmarkColor = NeonPurpleBright)
        )
        Text(label, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun canDrawOverlays(ctx: android.content.Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Settings.canDrawOverlays(ctx)
    } else true
}
