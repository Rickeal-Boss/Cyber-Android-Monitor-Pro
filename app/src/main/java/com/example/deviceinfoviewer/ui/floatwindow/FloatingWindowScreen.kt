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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.deviceinfoviewer.RefreshPolicy
import com.example.deviceinfoviewer.service.FloatingWindowConfig
import com.example.deviceinfoviewer.service.FloatingWindowService
import com.example.deviceinfoviewer.ui.theme.NeonPurple
import com.example.deviceinfoviewer.ui.theme.NeonPurpleBright
import com.example.deviceinfoviewer.ui.theme.TextSecondary

// ═══════ 指标可见性配置 ═══════
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
    var enabled by remember { mutableStateOf(FloatingWindowConfig.enabled) }

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
            // ★ 统一刷新频率提示 (来自 RefreshPolicy)
            Text(
                "${stringResource(R.string.float_section_realtime)} — ${stringResource(R.string.float_refresh_hint, RefreshPolicy.Tier.HIGH.defaultMs.toInt())}",
                fontSize = 13.sp, fontWeight = FontWeight.Medium,
                color = TextSecondary
            )

            metricToggles.forEach { mt ->
                val checked = toggles[mt.key]?.value ?: false
                CheckItem(stringResource(mt.nameResId), checked) { v ->
                    toggles[mt.key]?.value = v
                    FloatingWindowConfig.setVisible(mt.key, v)  // ★ StateFlow 事件驱动
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
