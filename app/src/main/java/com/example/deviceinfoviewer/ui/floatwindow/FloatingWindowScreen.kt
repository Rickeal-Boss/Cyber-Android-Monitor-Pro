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
import com.example.deviceinfoviewer.service.FloatingWindowConfig
import com.example.deviceinfoviewer.service.FloatingWindowService
import com.example.deviceinfoviewer.ui.theme.NeonPurple
import com.example.deviceinfoviewer.ui.theme.NeonPurpleBright

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
            CheckItem(stringResource(R.string.float_cpu_temp), showCpuTemp) { showCpuTemp = it; FloatingWindowConfig.showCpuTemp = it }
            CheckItem(stringResource(R.string.float_gpu_temp), showGpuTemp) { showGpuTemp = it; FloatingWindowConfig.showGpuTemp = it }
            CheckItem(stringResource(R.string.float_cpu_freq), showCpuFreq) { showCpuFreq = it; FloatingWindowConfig.showCpuFreq = it }
            CheckItem(stringResource(R.string.float_ram), showRam) { showRam = it; FloatingWindowConfig.showRam = it }
            CheckItem(stringResource(R.string.float_battery_temp), showBatteryTemp) { showBatteryTemp = it; FloatingWindowConfig.showBatteryTemp = it }
            CheckItem(stringResource(R.string.float_battery_current), showBatteryCur) { showBatteryCur = it; FloatingWindowConfig.showBatteryCurrent = it }
            CheckItem(stringResource(R.string.float_battery_power), showBatteryPow) { showBatteryPow = it; FloatingWindowConfig.showBatteryPower = it }

            Text(stringResource(R.string.float_section_system), fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp))
            CheckItem(stringResource(R.string.float_fps), showFps) { showFps = it; FloatingWindowConfig.showFps = it }
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
