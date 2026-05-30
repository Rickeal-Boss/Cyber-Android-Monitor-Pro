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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.deviceinfoviewer.service.FloatingWindowConfig
import com.example.deviceinfoviewer.service.FloatingWindowService
import com.example.deviceinfoviewer.ui.theme.NeonPurple
import com.example.deviceinfoviewer.ui.theme.NeonPurpleBright

@Composable
fun FloatingWindowScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var enabled by remember { mutableStateOf(FloatingWindowConfig.enabled) }
    var showCpu by remember { mutableStateOf(FloatingWindowConfig.showCpu) }
    var showGpu by remember { mutableStateOf(FloatingWindowConfig.showGpu) }
    var showBattery by remember { mutableStateOf(FloatingWindowConfig.showBattery) }
    var showMemory by remember { mutableStateOf(FloatingWindowConfig.showMemory) }
    var showTemp by remember { mutableStateOf(FloatingWindowConfig.showTemp) }
    var showNetwork by remember { mutableStateOf(FloatingWindowConfig.showNetwork) }
    var showRefresh by remember { mutableStateOf(FloatingWindowConfig.showRefreshRate) }
    var showFps by remember { mutableStateOf(FloatingWindowConfig.showFps) }

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("悬浮窗设置", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

        // 开关
        Card(
            Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("启用悬浮窗", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                Switch(
                    checked = enabled,
                    onCheckedChange = { v ->
                        if (v) {
                            if (canDrawOverlays(ctx)) {
                                enabled = true
                                FloatingWindowConfig.enabled = true
                                ctx.startService(Intent(ctx, FloatingWindowService::class.java))
                            } else {
                                Toast.makeText(ctx, "请授予悬浮窗权限", Toast.LENGTH_LONG).show()
                                ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${ctx.packageName}")).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                })
                            }
                        } else {
                            enabled = false
                            FloatingWindowConfig.enabled = false
                            ctx.stopService(Intent(ctx, FloatingWindowService::class.java))
                        }
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = NeonPurple)
                )
            }
        }

        if (enabled) {
            Text("显示内容", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            CheckItem("CPU 概要", showCpu, onToggle = { showCpu = it; FloatingWindowConfig.showCpu = it })
            CheckItem("GPU 型号", showGpu, onToggle = { showGpu = it; FloatingWindowConfig.showGpu = it })
            CheckItem("电池电量", showBattery, onToggle = { showBattery = it; FloatingWindowConfig.showBattery = it })
            CheckItem("内存使用", showMemory, onToggle = { showMemory = it; FloatingWindowConfig.showMemory = it })
            CheckItem("温度", showTemp, onToggle = { showTemp = it; FloatingWindowConfig.showTemp = it })
            CheckItem("网络", showNetwork, onToggle = { showNetwork = it; FloatingWindowConfig.showNetwork = it })
            CheckItem("刷新率", showRefresh, onToggle = { showRefresh = it; FloatingWindowConfig.showRefreshRate = it })
            CheckItem("实时帧率", showFps, onToggle = { showFps = it; FloatingWindowConfig.showFps = it })
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "提示: Android 14+ 需手动授予\"显示在其他应用上层\"权限",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun CheckItem(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = checked, onCheckedChange = onToggle,
            colors = CheckboxDefaults.colors(checkedColor = NeonPurple, checkmarkColor = NeonPurpleBright)
        )
        Text(label, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun canDrawOverlays(ctx: android.content.Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Settings.canDrawOverlays(ctx)
    } else true
}
