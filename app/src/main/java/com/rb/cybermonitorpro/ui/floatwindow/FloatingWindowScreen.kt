package com.rb.cybermonitorpro.ui.floatwindow

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.rb.cybermonitorpro.ui.nightlight.rememberHdrScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.rb.cybermonitorpro.ui.components.CyberIcons
import com.rb.cybermonitorpro.ui.components.FancySlider
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rb.cybermonitorpro.R
import com.rb.cybermonitorpro.ui.components.CyberJoystickSwitch
import com.rb.cybermonitorpro.service.FloatingWindowConfig
import com.rb.cybermonitorpro.service.FloatingWindowService
import com.rb.cybermonitorpro.ui.theme.NeonPurple
import com.rb.cybermonitorpro.ui.theme.NeonPurpleBright
import com.rb.cybermonitorpro.ui.theme.NeonSteelBlue
import com.rb.cybermonitorpro.ui.theme.TextPrimary
import com.rb.cybermonitorpro.ui.theme.TextSecondary
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
    // ★ 首次打开本页时一次性写入默认值（先写后读：toggles 在组合期读初始值, 必须同步执行, 不能用 LaunchedEffect）
    remember { FloatingWindowConfig.ensureFirstOpenedDefaults(); true }

    val enabled by FloatingWindowConfig.enabledFlow.collectAsState()

    // ★ 每个指标独立 state（从 FloatingWindowConfig 读取初始值）
    val toggles = metricToggles.map { mt ->
        mt.key to remember { mutableStateOf(FloatingWindowConfig.isVisible(mt.key)) }
    }.toMap()

    // ★ 用户主动开启但缺少悬浮窗权限 → 跳转设置页；pending 标记待返回时 resume 复查
    var pendingEnable by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && pendingEnable && canDrawOverlays(ctx)) {
                FloatingWindowConfig.enabled = true
                ctx.startService(Intent(ctx, FloatingWindowService::class.java))
                pendingEnable = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier.padding(top = 56.dp).padding(horizontal = 16.dp).verticalScroll(rememberHdrScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(stringResource(R.string.float_title), fontSize = 20.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface)

        // 总开关
        Card(
            Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.float_enable), fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f))
                CyberJoystickSwitch(
                    checked = enabled,
                    onCheckedChange = { v ->
                        if (v) {
                            if (canDrawOverlays(ctx)) {
                                FloatingWindowConfig.enabled = true
                                ctx.startService(Intent(ctx, FloatingWindowService::class.java))
                            } else {
                                pendingEnable = true
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

            // ★ F1: 外观样式自定义（独立分区, 与刷新间隔卡拉开距离）
            AppearanceCard()
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
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(CyberIcons.Schedule, null, tint = NeonPurple, modifier = Modifier.size(18.dp))
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
            FancySlider(
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

// ═══════ F1: 外观样式自定义 ═══════

/** 颜色预设（ARGB Int + 本地化标签资源） */
private data class ColorPreset(val argb: Int, val labelResId: Int)

/** 文字色 7 预设（默认紫 = 原硬编码 0xFFA05CFF） */
private val textColorPresets = listOf(
    ColorPreset(0xFFA05CFF.toInt(), R.string.float_color_purple),
    ColorPreset(0xFF00D4FF.toInt(), R.string.float_color_cyan),
    ColorPreset(0xFFF43F5E.toInt(), R.string.float_color_magenta),
    ColorPreset(0xFF34C759.toInt(), R.string.float_color_green),
    ColorPreset(0xFFFFAB00.toInt(), R.string.float_color_amber),
    ColorPreset(0xFFFFFFFF.toInt(), R.string.float_color_white),
    ColorPreset(0xFF3D70B8.toInt(), R.string.float_color_steel),
)

/** 背景色 6 预设（默认深灰 = 原硬编码 0xDC0A0A0F） */
private val bgColorPresets = listOf(
    ColorPreset(0xDC0A0A0F.toInt(), R.string.float_bg_dark),
    ColorPreset(0xE6000000.toInt(), R.string.float_bg_black),
    ColorPreset(0xDC1E1035.toInt(), R.string.float_bg_deep_purple),
    ColorPreset(0xDC0A1A2E.toInt(), R.string.float_bg_dark_blue),
    ColorPreset(0xDC0A241A.toInt(), R.string.float_bg_dark_green),
    ColorPreset(0xDC2E0A0A.toInt(), R.string.float_bg_dark_red),
)

/** 选中态对勾颜色: 按预设色亮度取黑/白, 保证任意底色可见 */
private fun contrastCheckTint(argb: Int): Color {
    val r = (argb shr 16 and 0xFF) / 255f
    val g = (argb shr 8 and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val luminance = 0.299f * r + 0.587f * g + 0.114f * b
    return if (luminance > 0.6f) Color(0xFF1A1A2E) else Color.White
}

/**
 * F1 外观卡 — 文字大小/窗口透明度两个 FancySlider（拖拽中预览实时渲染）+ 两组色板。
 * 预览与悬浮窗同构: 背景色自带 alpha × 整体 alpha 乘算（P1-1）。
 */
@Composable
private fun AppearanceCard() {
    val textColor by FloatingWindowConfig.textColorFlow.collectAsState()
    val bgColor by FloatingWindowConfig.bgColorFlow.collectAsState()

    var dragTextSize by remember { mutableFloatStateOf(FloatingWindowConfig.textSizeSp) }
    var isDraggingText by remember { mutableStateOf(false) }
    var dragAlpha by remember { mutableFloatStateOf(FloatingWindowConfig.windowAlpha) }
    var isDraggingAlpha by remember { mutableStateOf(false) }

    // 外部配置变化时同步 drag 预览值（拖拽中不打断）
    LaunchedEffect(FloatingWindowConfig.textSizeSp) { if (!isDraggingText) dragTextSize = FloatingWindowConfig.textSizeSp }
    LaunchedEffect(FloatingWindowConfig.windowAlpha) { if (!isDraggingAlpha) dragAlpha = FloatingWindowConfig.windowAlpha }

    Spacer(Modifier.height(16.dp))
    Text(stringResource(R.string.float_section_style), fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            // ★ P1-1: 预览用 drag 值实时渲染, 与悬浮窗同构(背景色自带 alpha × 整体 alpha)
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(Color(bgColor))
                    .alpha(dragAlpha),
                contentAlignment = Alignment.Center
            ) {
                Text("GPU 45%   CPU 38°C", color = Color(textColor),
                    fontSize = dragTextSize.sp,
                    modifier = Modifier.padding(vertical = 8.dp))
            }

            Spacer(Modifier.height(10.dp))

            // ── 文字大小（FancySlider, 9~22sp, 松手写回）──
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.float_text_size), fontSize = 15.sp, color = TextPrimary)
                Text("${dragTextSize.toInt()}sp", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = NeonPurpleBright)
            }
            FancySlider(
                value = dragTextSize,
                onValueChange = {
                    dragTextSize = it
                    if (!isDraggingText) isDraggingText = true
                },
                onValueChangeFinished = {
                    isDraggingText = false
                    FloatingWindowConfig.textSizeSp = dragTextSize
                },
                valueRange = FloatingWindowConfig.TEXT_SIZE_RANGE.start..FloatingWindowConfig.TEXT_SIZE_RANGE.endInclusive,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(6.dp))

            // ── 窗口透明度（FancySlider, 20%~100%, 松手写回）──
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.float_window_alpha), fontSize = 15.sp, color = TextPrimary)
                Text("${(dragAlpha * 100).toInt()}%", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = NeonPurpleBright)
            }
            FancySlider(
                value = dragAlpha,
                onValueChange = {
                    dragAlpha = it
                    if (!isDraggingAlpha) isDraggingAlpha = true
                },
                onValueChangeFinished = {
                    isDraggingAlpha = false
                    FloatingWindowConfig.windowAlpha = dragAlpha
                },
                valueRange = FloatingWindowConfig.ALPHA_RANGE.start..FloatingWindowConfig.ALPHA_RANGE.endInclusive,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(10.dp))

            // ── 文字颜色（7 色预设）──
            Text(stringResource(R.string.float_text_color), fontSize = 15.sp, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            ColorPresetRow(textColorPresets, textColor) { FloatingWindowConfig.textColor = it }

            Spacer(Modifier.height(10.dp))

            // ── 背景颜色（6 色预设）──
            Text(stringResource(R.string.float_bg_color), fontSize = 15.sp, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            ColorPresetRow(bgColorPresets, bgColor) { FloatingWindowConfig.bgColor = it }
        }
    }
}

/** 色板行 — 外层 weight(1f)+48dp 触控目标, 内层 30dp 视觉圆点（P2-1） */
@Composable
private fun ColorPresetRow(presets: List<ColorPreset>, current: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        presets.forEach { p ->
            val selected = p.argb == current
            val desc = stringResource(p.labelResId)
            Box(
                Modifier.weight(1f).height(48.dp)   // 外层均分宽度, 48dp 触控目标
                    .clip(CircleShape)
                    .clickable { onSelect(p.argb) }
                    .semantics { contentDescription = desc },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.size(30.dp).clip(CircleShape)   // 内层 30dp 视觉圆点
                        .background(Color(p.argb))
                        .then(
                            if (selected) Modifier.border(2.dp, NeonPurpleBright, CircleShape)
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        Icon(CyberIcons.Check, null, tint = contrastCheckTint(p.argb),
                            modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}
