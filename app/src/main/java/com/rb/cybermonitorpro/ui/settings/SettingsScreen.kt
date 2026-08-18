package com.rb.cybermonitorpro.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import com.rb.cybermonitorpro.ui.nightlight.rememberHdrScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rb.cybermonitorpro.R
import com.rb.cybermonitorpro.LocaleManager
import com.rb.cybermonitorpro.ui.components.CyberIcons
import com.rb.cybermonitorpro.ui.components.CyberJoystickSwitch
import com.rb.cybermonitorpro.ui.effects.GlobalLightSwitch
import com.rb.cybermonitorpro.ui.effects.CyberNightlightSwitch
import com.rb.cybermonitorpro.ui.effects.NightlightBarSwitch
import com.rb.cybermonitorpro.ui.theme.NeonPurple
import com.rb.cybermonitorpro.ui.theme.NeonPurpleBright
import com.rb.cybermonitorpro.ui.theme.NeonSteelBlue
import com.rb.cybermonitorpro.ui.theme.TextPrimary
import com.rb.cybermonitorpro.ui.theme.TextSecondary
import com.rb.cybermonitorpro.AppSettings
import com.rb.cybermonitorpro.HapticUtils
import org.koin.androidx.compose.koinViewModel

private val refreshOptions = listOf(500L, 1000L, 2000L, 3000L, 5000L)

private fun msToLabel(ms: Long): String = when {
    ms < 1000 -> "0.5s"
    ms < 60000 -> "${ms / 1000}s"
    else -> "${ms / 60000}min"
}

private data class ModuleIntervalConfig(
    val nameResId: Int,
    val icon: ImageVector,
    val descResId: Int,
    val getMs: (SettingsViewModel) -> Long,
    val setMs: (SettingsViewModel, Long) -> Unit,
)

private val moduleConfigs = listOf(
    ModuleIntervalConfig(R.string.tab_cpu, CyberIcons.PlayArrow,
        R.string.module_cpu_desc,
        { it.getCpuRefreshMs() }, { vm, ms -> vm.setCpuRefreshMs(ms) }),
    ModuleIntervalConfig(R.string.tab_gpu, CyberIcons.Settings,
        R.string.module_gpu_desc,
        { it.getGpuRefreshMs() }, { vm, ms -> vm.setGpuRefreshMs(ms) }),
    ModuleIntervalConfig(R.string.tab_memory, CyberIcons.Star,
        R.string.module_memory_desc,
        { it.getMemoryRefreshMs() }, { vm, ms -> vm.setMemoryRefreshMs(ms) }),
    ModuleIntervalConfig(R.string.tab_battery, CyberIcons.Favorite,
        R.string.module_battery_desc,
        { it.getBatteryRefreshMs() }, { vm, ms -> vm.setBatteryRefreshMs(ms) }),
    // ★ Network/GPS/Sensors 不支持独立频率配置，已移除
)

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = koinViewModel()) {
    val scroll = rememberHdrScrollState()

    Column(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 60.dp, bottom = 16.dp)
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.settings_title),
            fontSize = 18.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface)

        // ═══ 语言切换 ═══
        LanguageSettingsCard()

        Text(stringResource(R.string.settings_module_refresh),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        moduleConfigs.forEach { cfg ->
            ModuleIntervalCard(cfg, viewModel)
        }

        // ═══ 震动反馈 ═══
        HapticSettingsCard()

        // ═══ 全局光照 ═══
        GlobalLightSettingsCard()

        // ═══ CyberNightlight TurboXDR（局部 HDR 增亮）═══
        CyberNightlightTurboXdrSettingsCard()

        // ═══ 顶部夜光条（独立开关，与 TurboXDR 解耦）═══
        CyberNightlightBarSettingsCard()

        // ═══ App 信息 ═══
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.settings_app_name),
                    fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.settings_app_version),
                    fontSize = 13.sp, color = TextSecondary)
                Text(stringResource(R.string.settings_app_tech),
                    fontSize = 12.sp, color = TextSecondary.copy(alpha = 0.6f))
                Text(stringResource(R.string.settings_app_author),
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = NeonPurple)
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

// ═══════ 语言切换卡片 ═══════

@Composable
private fun LanguageSettingsCard() {
    val ctx = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    // ★ 性能优化 (2026-06-20): 用 remember(ctx) 缓存 SP 读取
    //   原方案每次重组都调用 LocaleManager.getSavedLanguage(ctx) 读 SharedPreferences,
    //   设置页滑动/交互时每帧重读 SP → 卡顿。
    //   切换语言后 Activity recreate 会重建 Composable, 缓存自动刷新。
    val currentCode = remember(ctx) { LocaleManager.getSavedLanguage(ctx) }
    val currentName = remember(currentCode) {
        LocaleManager.SUPPORTED_LANGUAGES.find { it.code == currentCode }?.nativeName
            ?: LocaleManager.SUPPORTED_LANGUAGES.first().nativeName
    }

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = NeonPurple.copy(alpha = 0.08f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(CyberIcons.Language, null,
                    tint = NeonPurpleBright, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Column {
                    Text(stringResource(R.string.settings_language_title),
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        color = TextPrimary)
                    Text(stringResource(R.string.settings_language_desc),
                        fontSize = 11.sp, color = TextSecondary)
                }
            }
            TextButton(onClick = {
                HapticUtils.standardTap(ctx)
                showDialog = true
            }) {
                Text(currentName, fontSize = 13.sp, color = NeonPurpleBright,
                    fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (showDialog) {
        LanguagePickerDialog(
            currentCode = currentCode,
            onDismiss = { showDialog = false },
            onSelect = { code ->
                showDialog = false
                // 持久化 + 通过 AppCompatDelegate 触发 recreate
                // MainActivity 是 ComponentActivity（非 AppCompatActivity），
                // API<33 时 AppCompatDelegate 可能不自动 recreate，需手动调用
                val activity = ctx.findActivity()
                LocaleManager.applyLanguage(ctx, code, activity = activity)
                HapticUtils.standardTap(ctx)
            }
        )
    }
}

/**
 * 从 Compose Context 递归找出 Activity（Context 可能被 wrapper 包裹）
 */
private fun android.content.Context.findActivity(): android.app.Activity? {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is android.app.Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * 语言选择对话框 — 列出所有支持的语言，点击即切换。
 * 切换后由 LocaleManager.applyLanguage 触发 Activity recreate，立即生效无需重启 APP。
 */
@Composable
private fun LanguagePickerDialog(
    currentCode: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_language_dialog_title)) },
        text = {
            Column {
                LocaleManager.SUPPORTED_LANGUAGES.forEach { option ->
                    val selected = option.code == currentCode
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp)
                            .clickable { onSelect(option.code) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (selected) CyberIcons.Check else CyberIcons.Language,
                            contentDescription = null,
                            tint = if (selected) NeonPurpleBright else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.size(12.dp))
                        Text(
                            option.nativeName,
                            fontSize = 15.sp,
                            color = if (selected) NeonPurpleBright else TextPrimary,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close))
            }
        }
    )
}

@Composable
private fun ModuleIntervalCard(cfg: ModuleIntervalConfig, viewModel: SettingsViewModel) {
    // ★ 性能优化 (2026-06-20): 缓存 SP 读取 + 延迟写入
    //   原方案:
    //   - `val savedMs = cfg.getMs(viewModel)` 每次重组都读 SP (每帧 7 次)
    //   - Slider `onValueChange` 每帧调用 `cfg.setMs(viewModel, ...)` 写 SP (拖拽时每帧 7 次写)
    //   修复:
    //   - `savedMs` 用 remember(cfg, viewModel) 缓存, 仅首次组合读一次 SP
    //   - Slider `onValueChange` 只更新本地 state, 不写 SP
    //   - `onValueChangeFinished` (手指抬起) 时才一次性写入 SP + snap 到最近档位
    val savedMs = remember(cfg, viewModel) { cfg.getMs(viewModel) }
    val initialMs = if (savedMs > 0) savedMs else 2000L // default 2s
    var currentMs by remember { mutableFloatStateOf(initialMs.toFloat()) }

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
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
                        Text(stringResource(cfg.nameResId), fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                            color = TextPrimary)
                        Text(stringResource(cfg.descResId), fontSize = 11.sp, color = TextSecondary)
                    }
                }
                // ★ 显示 snapped 值 (松手后确认的档位)
                Text(msToLabel(currentMs.toLong().snapToOption()), fontSize = 24.sp,
                    fontWeight = FontWeight.Bold, color = NeonPurpleBright)
            }
            Spacer(Modifier.height(10.dp))

            Slider(
                value = currentMs,
                onValueChange = { currentMs = it },
                onValueChangeFinished = {
                    // ★ 松手时一次性 snap + 写 SP
                    currentMs = currentMs.toLong().snapToOption().toFloat()
                    cfg.setMs(viewModel, currentMs.toLong())
                },
                valueRange = 500f..5000f,
                steps = 0,  // 自由滑动，档次由 onValueChangeFinished snap 处理
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = NeonPurpleBright,
                    activeTrackColor = NeonPurple,
                    inactiveTrackColor = NeonSteelBlue.copy(alpha = 0.3f)
                )
            )

            // ★ Bug 修复 (2026-06-23): 原 Row(Arrangement.SpaceBetween) 将 5 个标签
            //   均匀分布在全宽上，但非均匀离散档位 {500,1000,2000,3000,5000} 在
            //   500..5000 范围上的实际位置占比为 0%/11.1%/33.3%/55.6%/100%，
            //   导致标签与滑条实际档位位置完全不匹配 (如 5s 标签紧贴 3s)。
            //   修复: 用 weight 比例分布，使标签间距与档位在滑条上的真实位置一致。
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(msToLabel(refreshOptions[0]), fontSize = 10.sp, color = NeonSteelBlue.copy(alpha = 0.7f))
                Spacer(Modifier.weight(0.111f))
                Text(msToLabel(refreshOptions[1]), fontSize = 10.sp, color = NeonSteelBlue.copy(alpha = 0.7f))
                Spacer(Modifier.weight(0.222f))
                Text(msToLabel(refreshOptions[2]), fontSize = 10.sp, color = NeonSteelBlue.copy(alpha = 0.7f))
                Spacer(Modifier.weight(0.222f))
                Text(msToLabel(refreshOptions[3]), fontSize = 10.sp, color = NeonSteelBlue.copy(alpha = 0.7f))
                Spacer(Modifier.weight(0.445f))
                Text(msToLabel(refreshOptions[4]), fontSize = 10.sp, color = NeonSteelBlue.copy(alpha = 0.7f))
            }
        }
    }
}

/** ★ 将 ms 值 snap 到最近的 refreshOptions 离散档位 */
private fun Long.snapToOption(): Long = refreshOptions.minByOrNull { kotlin.math.abs(it - this) } ?: this

// ═══════ 全局光照卡片 ═══════

@Composable
private fun GlobalLightSettingsCard() {
    val ctx = LocalContext.current
    val settings = remember { AppSettings.getInstance(ctx) }
    // 运行期真源在 GlobalLightSwitch (Compose State), AppSettings 仅持久化
    var enabled by remember { mutableStateOf(GlobalLightSwitch.enabled) }

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = NeonPurple.copy(alpha = 0.08f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(CyberIcons.Light, null,
                    tint = NeonPurpleBright, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Column {
                    Text(stringResource(R.string.settings_globallight),
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        color = TextPrimary)
                    Text(stringResource(R.string.settings_globallight_desc),
                        fontSize = 11.sp, color = TextSecondary)
                    Text(
                        if (enabled) stringResource(R.string.settings_globallight_on)
                        else stringResource(R.string.settings_globallight_off),
                        fontSize = 11.sp, color = TextSecondary)
                }
            }
            // 开关自带震动反馈 (CyberJoystickSwitch 内部 standardTap), 无需额外触发
            CyberJoystickSwitch(
                checked = enabled,
                onCheckedChange = { v ->
                    enabled = v
                    settings.globalLightEnabled = v
                    GlobalLightSwitch.enabled = v   // 即时生效, 无需重启
                },
            )
        }
    }
}

// ═══════ 震动反馈卡片 ═══════

@Composable
private fun HapticSettingsCard() {
    val ctx = LocalContext.current
    val settings = remember { AppSettings.getInstance(ctx) }

    var enabled by remember { mutableStateOf(settings.hapticEnabled) }
    var intensity by remember { mutableFloatStateOf(settings.hapticIntensity.toFloat()) }

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = NeonPurple.copy(alpha = 0.08f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.settings_haptic), fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                color = TextPrimary)
            Text(stringResource(R.string.settings_haptic_desc),
                fontSize = 12.sp, color = TextSecondary,
                modifier = Modifier.padding(top = 2.dp))

            Spacer(Modifier.height(12.dp))

            // 开关
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(stringResource(R.string.settings_haptic_switch), fontSize = 14.sp, color = TextPrimary)
                    Text(if (enabled) stringResource(R.string.settings_haptic_on) else stringResource(R.string.settings_haptic_off), fontSize = 11.sp, color = TextSecondary)
                }
                CyberJoystickSwitch(
                    checked = enabled,
                    onCheckedChange = { v ->
                        enabled = v
                        settings.hapticEnabled = v
                        HapticUtils.refreshSettings(settings)
                        if (v) HapticUtils.standardTap(ctx)
                    },
                )
            }

            if (enabled) {
                Spacer(Modifier.height(12.dp))

                val levelLabel = when (intensity.toInt()) {
                    1 -> stringResource(R.string.settings_haptic_light)
                    2 -> stringResource(R.string.settings_haptic_standard)
                    3 -> stringResource(R.string.settings_haptic_heavy)
                    else -> ""
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_haptic_intensity), fontSize = 14.sp, color = TextPrimary)
                    Text(levelLabel, fontSize = 12.sp, color = NeonPurpleBright,
                        fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(4.dp))

                // ★ 性能优化 (2026-06-20): Slider 拖拽中不触发震动 + 不写 SP
                //   原方案 onValueChange 每帧调用:
                //   - settings.hapticIntensity = v.toInt()  → 写 SP
                //   - HapticUtils.refreshSettings(settings)  → 重置缓存
                //   - HapticUtils.stepTick(ctx)              → 触发震动
                //   拖拽 1 秒内可能触发 60+ 次震动 + 60+ 次 SP 写入 → 严重卡顿 + 震动器过载
                //   修复: onValueChange 只更新本地 state + 实时显示 label,
                //         onValueChangeFinished (手指抬起) 时一次性写入 SP + 触发一次震动
                Slider(
                    value = intensity,
                    onValueChange = { v ->
                        intensity = v
                    },
                    onValueChangeFinished = {
                        settings.hapticIntensity = intensity.toInt()
                        HapticUtils.refreshSettings(settings)
                        HapticUtils.stepTick(ctx)
                    },
                    valueRange = 1f..3f,
                    steps = 1,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = NeonPurpleBright,
                        activeTrackColor = NeonPurple,
                        inactiveTrackColor = NeonSteelBlue.copy(alpha = 0.3f)
                    )
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf(
                        stringResource(R.string.settings_haptic_weak),
                        stringResource(R.string.settings_haptic_medium),
                        stringResource(R.string.settings_haptic_strong)
                    ).forEach {
                        Text(it, fontSize = 10.sp, color = NeonSteelBlue.copy(alpha = 0.7f))
                    }
                }
            }
        }
    }
}

// ═══════ CyberNightlight TurboXDR 卡片 ═══════
/**
 * TurboXDR 局部 HDR 增亮总开关：卡片描边 / 文字 / 图标 / 折线等贴片。
 * 复用 CyberJoystickSwitch 样式；默认关闭。即时生效：写 AppSettings + 同步 CyberNightlightSwitch。
 *
 * HDR 强度滑条同时映射到 HdrLumeSurfaceView.setDesiredHdrHeadroom(1.0..8.0)，
 * 控制顶部夜光条亮度；在 HDR 实验室页也提供同款滑条以便边测边调。
 */
@Composable
private fun CyberNightlightTurboXdrSettingsCard() {
    val ctx = LocalContext.current
    val settings = remember { AppSettings.getInstance(ctx) }
    var enabled by remember { mutableStateOf(CyberNightlightSwitch.enabled) }
    var intensity by remember { mutableFloatStateOf(CyberNightlightSwitch.intensity) }

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = NeonPurple.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(CyberIcons.Light, null, tint = NeonPurpleBright, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                    Column {
                        Text(stringResource(R.string.settings_turboxdr), fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text(stringResource(R.string.settings_turboxdr_desc), fontSize = 11.sp, color = TextSecondary)
                        Text(
                            if (enabled) stringResource(R.string.settings_turboxdr_on)
                            else stringResource(R.string.settings_turboxdr_off),
                            fontSize = 11.sp, color = TextSecondary)
                    }
                }
                CyberJoystickSwitch(
                    checked = enabled,
                    onCheckedChange = { v ->
                        enabled = v
                        settings.cyberNightlightTurboXdrEnabled = v
                        CyberNightlightSwitch.enabled = v   // 即时生效，无需重启
                    },
                )
            }

            // ═══ HDR 强度滑条（1.0×..8.0×，对应 setDesiredHdrHeadroom）═══
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(stringResource(R.string.device_hdr_intensity), fontSize = 14.sp, color = TextPrimary)
                    Text(stringResource(R.string.device_hdr_intensity_hint), fontSize = 11.sp, color = TextSecondary)
                }
                Text(String.format("%.1fx", intensity), fontSize = 14.sp,
                    color = NeonPurpleBright, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(4.dp))
            Slider(
                value = intensity,
                onValueChange = { intensity = it },
                onValueChangeFinished = {
                    settings.cyberNightlightTurboXdrIntensity = intensity
                    CyberNightlightSwitch.intensity = intensity
                },
                valueRange = 1f..8f,
                steps = 69,  // 0.1× 步长 → (8-1)/0.1 - 1 = 69 个中间挡
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = NeonPurpleBright,
                    activeTrackColor = NeonPurple,
                    inactiveTrackColor = NeonSteelBlue.copy(alpha = 0.3f)
                )
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("1.0×", fontSize = 10.sp, color = NeonSteelBlue.copy(alpha = 0.7f))
                Text("8.0×", fontSize = 10.sp, color = NeonSteelBlue.copy(alpha = 0.7f))
            }
            if (!enabled) {
                Text(stringResource(R.string.device_hdr_intensity_off),
                    fontSize = 11.sp, color = NeonSteelBlue.copy(alpha = 0.7f))
            }
        }
    }
}

// ═══════ 顶部夜光条独立开关卡片 ═══════
/**
 * 顶部夜光条（CyberNightlightHost / HdrLumeSurfaceView 全屏边缘闪光）独立开关。
 * 与 TurboXDR 解耦：关闭夜光条不影响局部 HDR 贴片；夜光条亮度仍由 TurboXDR 强度滑块控制。
 */
@Composable
private fun CyberNightlightBarSettingsCard() {
    val ctx = LocalContext.current
    val settings = remember { AppSettings.getInstance(ctx) }
    var enabled by remember { mutableStateOf(NightlightBarSwitch.enabled) }

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = NeonPurple.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(CyberIcons.Light, null, tint = NeonPurpleBright, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Column {
                    Text(stringResource(R.string.settings_nightlight_bar), fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text(stringResource(R.string.settings_nightlight_bar_desc), fontSize = 11.sp, color = TextSecondary)
                    Text(
                        if (enabled) stringResource(R.string.settings_nightlight_bar_on)
                        else stringResource(R.string.settings_nightlight_bar_off),
                        fontSize = 11.sp, color = TextSecondary)
                }
            }
            CyberJoystickSwitch(
                checked = enabled,
                onCheckedChange = { v ->
                    enabled = v
                    settings.cyberNightlightBarEnabled = v
                    NightlightBarSwitch.enabled = v
                },
            )
        }
    }
}
