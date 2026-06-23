package com.example.deviceinfoviewer.ui.floatwindow

import android.content.Context
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlin.math.abs

// ═══════ 刷新间隔选项 ═══════
// ★ 简化后 5 档: 0.5s / 1s / 2s / 3s / 5s
private val refreshStepOptions = listOf(500L, 1000L, 2000L, 3000L, 5000L)

private fun formatMs(ms: Long): String = when (ms) {
    500L -> "0.5s"
    1000L -> "1s"
    2000L -> "2s"
    3000L -> "3s"
    5000L -> "5s"
    else -> "${ms}ms"
}
private data class MetricToggle(val key: String, val nameResId: Int)

private val metricToggles = listOf(
    MetricToggle("gpu_usage", R.string.float_gpu_usage),
    MetricToggle("cpu_temp", R.string.float_cpu_temp),
    MetricToggle("gpu_temp", R.string.float_gpu_temp),
    MetricToggle("cpu_freq", R.string.float_cpu_freq),
    MetricToggle("ram", R.string.float_ram),
    MetricToggle("battery_temp", R.string.float_battery_temp),
    MetricToggle("battery_cur", R.string.float_battery_current),
    MetricToggle("battery_pow", R.string.float_battery_power),
    MetricToggle("fps", R.string.float_fps),
)

@Composable
fun FloatingWindowScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val enabled by FloatingWindowConfig.enabledFlow.collectAsState()

    // ★ 每个指标独立 state（从 FloatingWindowConfig 读取初始值）
    val toggles = metricToggles.map { mt ->
        mt.key to remember { mutableStateOf(FloatingWindowConfig.isVisible(mt.key)) }
    }.toMap()

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
                                FloatingWindowConfig.enabled = true
                                ctx.startService(Intent(ctx, FloatingWindowService::class.java))
                            } else {
                                Toast.makeText(ctx, ctx.getString(R.string.float_permission_toast), Toast.LENGTH_LONG).show()
                                ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${ctx.packageName}")).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                })
                            }
                        } else {
                            FloatingWindowConfig.enabled = false
                            ctx.stopService(Intent(ctx, FloatingWindowService::class.java))
                        }
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = NeonPurple)
                )
            }
        }

        if (enabled) {
            // ★ 刷新频率设置
            RefreshIntervalCard()

            metricToggles.forEach { mt ->
                val checked = toggles[mt.key]?.value ?: false
                CheckItem(stringResource(mt.nameResId), checked) { v ->
                    toggles[mt.key]?.value = v
                    FloatingWindowConfig.setVisible(mt.key, v)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.float_overlay_hint),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
    }
}

@Composable
private fun RefreshIntervalCard() {
    // Observe StateFlow for bidirectional real-time sync
    val configuredMs by FloatingWindowConfig.refreshIntervalFlow.collectAsState()

    // Drag-tracking value; snaps to nearest discrete step on release
    var dragValue by remember { mutableFloatStateOf(configuredMs.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }

    // Sync drag value when external config changes (only when not dragging)
    LaunchedEffect(configuredMs) {
        if (!isDragging) {
            dragValue = configuredMs.toFloat()
        }
    }

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Schedule, null, tint = NeonPurple, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.float_refresh_interval), fontSize = 15.sp,
                        color = TextPrimary)
                }
                // ★ 显示当前已确认的配置值 (非拖拽中的中间态)
                Text(formatMs(configuredMs), fontSize = 18.sp,
                    fontWeight = FontWeight.Bold, color = NeonPurpleBright)
            }

            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.float_refresh_status) + ": " +
                stringResource(if (configuredMs >= 1000) R.string.float_refresh_powersave else R.string.float_refresh_realtime),
                fontSize = 12.sp, color = TextSecondary)

            // Free sliding + snap to nearest discrete step on release
            Slider(
                value = dragValue,
                onValueChange = {
                    dragValue = it
                    if (!isDragging) isDragging = true
                },
                onValueChangeFinished = {
                    isDragging = false
                    val snapped = refreshStepOptions.minByOrNull {
                        abs(it - dragValue.toLong())
                    } ?: dragValue.toLong()
                    dragValue = snapped.toFloat()
                    FloatingWindowConfig.refreshIntervalMs = snapped
                },
                valueRange = refreshStepOptions.first().toFloat()..refreshStepOptions.last().toFloat(),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                colors = SliderDefaults.colors(
                    thumbColor = NeonPurpleBright,
                    activeTrackColor = NeonPurple,
                    inactiveTrackColor = NeonSteelBlue.copy(alpha = 0.3f)
                )
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                refreshStepOptions.forEach { opt ->
                    Text(formatMs(opt), fontSize = 9.sp, color = NeonSteelBlue.copy(alpha = 0.5f))
                }
            }
        }
    }

    Spacer(Modifier.height(6.dp))
    Text(stringResource(R.string.float_section_realtime), fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
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

private fun canDrawOverlays(ctx: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Settings.canDrawOverlays(ctx)
    } else true
}
