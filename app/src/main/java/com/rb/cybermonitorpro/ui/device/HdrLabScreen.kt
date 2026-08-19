package com.rb.cybermonitorpro.ui.device

import android.content.Context
import android.os.Build
import android.view.Display
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import com.rb.cybermonitorpro.ui.nightlight.rememberHdrScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.rb.cybermonitorpro.AppSettings
import com.rb.cybermonitorpro.R
import com.rb.cybermonitorpro.ui.components.CyberIcons
import com.rb.cybermonitorpro.ui.components.CyberJoystickSwitch
import com.rb.cybermonitorpro.ui.components.FancySlider
import com.rb.cybermonitorpro.ui.effects.CyberNightlightSwitch
import com.rb.cybermonitorpro.ui.effects.cardGradientBorder
import com.rb.cybermonitorpro.ui.theme.CyberCardEnd
import com.rb.cybermonitorpro.ui.theme.CyberCardStart
import com.rb.cybermonitorpro.ui.theme.NeonCyan
import com.rb.cybermonitorpro.ui.theme.NeonPurple
import com.rb.cybermonitorpro.ui.theme.NeonPurpleBright
import com.rb.cybermonitorpro.ui.theme.NeonSteelBlue
import com.rb.cybermonitorpro.ui.theme.SuccessNeon
import com.rb.cybermonitorpro.ui.theme.TextPrimary
import com.rb.cybermonitorpro.ui.theme.TextSecondary
import com.rb.cybermonitorpro.ui.theme.WarningNeon
import kotlinx.coroutines.delay

private val HdrLabCardGradient = Brush.linearGradient(listOf(CyberCardStart, CyberCardEnd))

/**
 * ★ 2026-08-16 HDR 实验室 — 局部 EDR（HDR headroom）真机验证页（详情页二层覆盖层）。
 *
 * 对齐参考截图：潜在 EDR 亮度余量读数 + SDR/HDR 对比块 + headroom 滑条 + 实际比值诊断 + 全屏验证。
 * 这是 CyberNightlight TurboXDR 落地前的保险验证步骤，只做局部测试，不改全局渲染。
 *
 * 数据口径（全部经 runCatching / SDK_INT 守卫，低版本静默降级）：
 *  - 潜在余量：Display.getHighestHdrSdrRatio()（API 36+，compileSdk 36 直接调用）；
 *  - 滑条控制：SurfaceView.setDesiredHdrHeadroom()（API 35+，0=系统自动，>1 请求提亮）；
 *  - 实际生效：Display.getHdrSdrRatio()（API 34+，>1.01 判定 HDR 图层真正点亮）。
 *
 * ⚠️ 策略修正 v2 关键认知：
 *  - PQ 图层创建成功 ≠ HDR 已点亮。ratio>1.01 需 HDR 内容覆盖足够屏幕面积（约 50%，
 *    AOSP minimumHdrPercentOfScreen 默认阈值）以触发面板高亮（HBM）。小块对比块远小于该阈值，
 *    即便 PQ 真正生效，ratio 也会停在 ~1.0，造成"未点亮"误报。
 *  - 因此新增「全屏 HDR 验证」：让 PQ 图层覆盖整屏越过面积阈值，ratio>1.01 且肉眼明显更亮
 *    才是真正点亮的确凿证据。
 *
 * 注意：截图会被系统色调映射到 SDR，亮度差异只能真机肉眼 + ratio 双确认，勿凭截图判断。
 */
@Composable
fun HdrLabScreen(onBack: () -> Unit = {}, surfaceVisible: Boolean = true) {
    val ctx = LocalContext.current

    // Display 句柄：API 30+ 用 Context.display；低版本回退 WindowManager.defaultDisplay（deprecated 但可用）
    val display: Display? = remember {
        if (Build.VERSION.SDK_INT >= 30) {
            runCatching { ctx.display }.getOrNull()
        } else {
            runCatching {
                @Suppress("DEPRECATION")
                (ctx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay
            }.getOrNull()
        }
    }

    // 潜在 EDR 亮度余量（API 36+；低版本 0f 标记不可查询 → 显示 "—"）
    val potentialHeadroom: Float = remember {
        if (Build.VERSION.SDK_INT >= 36 && display != null) {
            runCatching { display.highestHdrSdrRatio }.getOrDefault(1f)
        } else 0f
    }

    // 滑条默认值：>1 主动请求 HDR 提亮（1.0 = 明确无 HDR，0 = 交还系统自动）
    var desired by remember { mutableFloatStateOf(2f) }
    var actualRatio by remember { mutableFloatStateOf(1f) }
    var ratioAvailable by remember { mutableStateOf(false) }
    var pqActive by remember { mutableStateOf(false) }
    var eglSummary by remember { mutableStateOf("pending") }
    val hdrView = remember { mutableStateOf<HdrTestSurfaceView?>(null) }

    // 全屏 HDR 验证（越过面积阈值，给出确凿点亮证据）
    var fullScreenHdr by remember { mutableStateOf(false) }
    val fsView = remember { mutableStateOf<HdrTestSurfaceView?>(null) }
    val fsHeadroom = if (potentialHeadroom > 1f) potentialHeadroom.coerceAtMost(4f) else 3f

    // 500ms 轮询诊断：实际 HDR/SDR 比（API 34+）+ PQ surface / EGL 状态（含全屏实例）
    LaunchedEffect(display) {
        while (true) {
            if (Build.VERSION.SDK_INT >= 34 && display != null) {
                ratioAvailable = runCatching { display.isHdrSdrRatioAvailable }.getOrDefault(false)
                actualRatio = runCatching { display.hdrSdrRatio }.getOrDefault(1f)
            }
            hdrView.value?.let {
                pqActive = it.pqSurfaceActive
                eglSummary = it.eglSummary
            }
            fsView.value?.let {
                // 全屏实例优先上报其 PQ / EGL 状态（更权威，覆盖整屏）
                pqActive = it.pqSurfaceActive
                eglSummary = it.eglSummary
            }
            delay(500)
        }
    }

    // 滑条上限：API 36 用真实最高比；不可知时给 4× 演示上限
    // （setDesiredHdrHeadroom 允许 0.0~10000.0；0=系统自动，实际生效由系统裁定，此处仅定 UI 范围）
    val sliderMax = if (potentialHeadroom > 1f) potentialHeadroom else 4f
    val canControl = Build.VERSION.SDK_INT >= 35

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberHdrScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 顶部让位宿主叠加的返回圆钮（与 SensorDetailScreen 同款 LightCircleBackButton 对齐）
            Spacer(Modifier.height(40.dp))
            Text(
                stringResource(R.string.hdr_lab_title),
                fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // ═══════ 主测试卡片（对齐参考截图布局）═══════
            Card(
                Modifier
                    .fillMaxWidth()
                    .cardGradientBorder(20.dp, hdrHighlight = true),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Box(Modifier.fillMaxWidth().background(HdrLabCardGradient)) {
                    Column(Modifier.padding(16.dp)) {
                        // ── 潜在 EDR 亮度余量 ──
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.hdr_lab_potential_headroom),
                                fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                if (potentialHeadroom > 1f) "%.1f".format(potentialHeadroom) else "—",
                                fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                color = NeonPurpleBright
                            )
                        }
                        if (potentialHeadroom <= 1f) {
                            Text(
                                stringResource(R.string.hdr_lab_headroom_unavailable),
                                fontSize = 11.sp, color = TextSecondary
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        // ── SDR / HDR 对比块（标签置块下方：SurfaceView punch-hole 会挖掉其上窗口像素）──
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(140.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color.White)
                                )
                                Text(
                                    stringResource(R.string.hdr_lab_sdr),
                                    Modifier.fillMaxWidth().padding(top = 6.dp),
                                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(140.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                ) {
                                    // CAMP 修复: SurfaceView 延迟挂载门控 — 进入动画完成前画静态占位, 避免 punch-through 突跳
                                    if (surfaceVisible) {
                                        AndroidView(
                                            factory = { c ->
                                                HdrTestSurfaceView(c).also {
                                                    hdrView.value = it
                                                    it.applyRequestedHeadroom(desired)   // 初始即请求 HDR 余量
                                                }
                                            },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        // 占位块: 避免空块布局跳动, 动画结束后无缝切换到 SurfaceView
                                        Box(Modifier.fillMaxSize().background(CyberCardStart.copy(alpha = 0.6f)))
                                    }
                                }
                                Row(
                                    Modifier.fillMaxWidth().padding(top = 6.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        stringResource(R.string.hdr_lab_hdr),
                                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.size(6.dp))
                                    // PQ surface 状态点：绿 = PQ 激活；琥珀 = 已回退 SDR 8-bit
                                    Box(
                                        Modifier
                                            .size(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(if (pqActive) SuccessNeon else WarningNeon)
                                    )
                                    Spacer(Modifier.size(4.dp))
                                    Text(
                                        if (pqActive) "PQ" else "8-bit",
                                        fontSize = 10.sp,
                                        color = if (pqActive) SuccessNeon else WarningNeon
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // ── headroom 滑条（两端光照图标：左暗右亮）──
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                CyberIcons.Light, null,
                                tint = NeonSteelBlue.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                            FancySlider(
                                value = desired,
                                onValueChange = { v ->
                                    desired = v
                                    hdrView.value?.applyRequestedHeadroom(v)
                                },
                                valueRange = 0f..sliderMax,
                                enabled = canControl,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp),
                                // SLIDER-06: disabled 态用 if(canControl) 外部选色，与原三色降透明度视觉一致
                                thumbColor = if (canControl) NeonPurpleBright
                                    else NeonSteelBlue.copy(alpha = 0.4f),
                                activeTrackColor = if (canControl) NeonPurple
                                    else NeonSteelBlue.copy(alpha = 0.3f),
                                inactiveTrackColor = if (canControl) NeonSteelBlue.copy(alpha = 0.3f)
                                    else NeonSteelBlue.copy(alpha = 0.2f),
                            )
                            Icon(
                                CyberIcons.Light, null,
                                tint = NeonPurpleBright,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Text(
                            if (desired <= 0.001f) stringResource(R.string.hdr_lab_headroom_auto)
                            else stringResource(R.string.hdr_lab_brightness, desired),
                            Modifier.fillMaxWidth(),
                            fontSize = 13.sp, color = NeonCyan,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            stringResource(R.string.hdr_lab_headroom_scale_hint),
                            Modifier.fillMaxWidth().padding(top = 2.dp),
                            fontSize = 11.sp, color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                        if (!canControl) {
                            Text(
                                stringResource(R.string.hdr_lab_needs_api35),
                                Modifier.fillMaxWidth().padding(top = 4.dp),
                                fontSize = 11.sp, color = WarningNeon,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // ═══════ 诊断卡片（双信号核验：调用成功不算点亮，ratio>1.01 才算）═══════
            Card(
                Modifier
                    .fillMaxWidth()
                    .cardGradientBorder(20.dp, hdrHighlight = true),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Box(Modifier.fillMaxWidth().background(HdrLabCardGradient)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.hdr_lab_diag_title),
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = NeonPurple
                        )
                        Column(Modifier.padding(top = 8.dp)) {
                            HdrLabDiagRow(
                                stringResource(R.string.hdr_lab_actual_ratio),
                                if (ratioAvailable) "%.2f".format(actualRatio) else "—"
                            )
                            HdrLabDiagRow(
                                stringResource(R.string.hdr_lab_layer_state),
                                if (ratioAvailable && actualRatio > 1.01f)
                                    stringResource(R.string.hdr_lab_ratio_lit)
                                else
                                    stringResource(R.string.hdr_lab_ratio_unlit),
                                valueColor = if (ratioAvailable && actualRatio > 1.01f)
                                    SuccessNeon else TextSecondary
                            )
                            HdrLabDiagRow(
                                "PQ surface",
                                if (pqActive) stringResource(R.string.hdr_lab_pq_active)
                                else stringResource(R.string.hdr_lab_pq_fallback),
                                valueColor = if (pqActive) SuccessNeon else WarningNeon
                            )
                            HdrLabDiagRow("EGL", eglSummary)
                            HdrLabDiagRow("SDK", "${Build.VERSION.SDK_INT}")
                        }

                        // ── 全屏 HDR 验证入口 ──
                        Spacer(Modifier.height(12.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { fullScreenHdr = true }
                                .background(NeonPurple.copy(alpha = 0.14f))
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                stringResource(R.string.hdr_lab_fullscreen_btn),
                                fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                color = NeonPurpleBright
                            )
                        }
                        Text(
                            stringResource(R.string.hdr_lab_coverage_note),
                            Modifier.fillMaxWidth().padding(top = 8.dp),
                            fontSize = 11.sp, color = TextSecondary
                        )
                    }
                }
            }

            // ═══════ TurboXDR 局部 HDR 控制（与设置页同款：边测边调）═══════
            // 强度滑条同时映射到 HdrLumeSurfaceView.setDesiredHdrHeadroom(1.0..8.0) 控制夜光条亮度；
            // 此处提供同款控件，便于在 HDR 实验室一边验证 headroom 一边微调局部 HDR 增亮强度。
            HdrLabTurboXdrCard()

            // ── 截图失真提示 ──
            Text(
                stringResource(R.string.hdr_lab_hint_screenshot),
                fontSize = 11.sp, color = TextSecondary
            )
        }

        // ═══════ 全屏 HDR 验证覆盖层（PQ 图层覆盖整屏，越过面积阈值）═══════
        if (fullScreenHdr) {
            // SurfaceView 默认置于窗口内容之下，Compose 控件绘制于其上（可点击）；
            // 整屏 PQ 白场会触发面板高亮，ratio>1.01 即确凿点亮。
            Box(Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { c ->
                        HdrTestSurfaceView(c).also {
                            fsView.value = it
                            it.applyRequestedHeadroom(fsHeadroom)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                // 顶部控制条（半透明背景，确保白底上可读）
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(16.dp)
                        .align(Alignment.TopCenter)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.hdr_lab_fullscreen_hint_title),
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { fullScreenHdr = false; fsView.value = null }
                                .background(NeonPurpleBright.copy(alpha = 0.9f))
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                stringResource(R.string.hdr_lab_fullscreen_exit),
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                color = Color.Black
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (ratioAvailable && actualRatio > 1.01f)
                            stringResource(R.string.hdr_lab_fullscreen_lit, actualRatio)
                        else
                            stringResource(R.string.hdr_lab_fullscreen_notlit),
                        fontSize = 13.sp,
                        color = if (ratioAvailable && actualRatio > 1.01f) SuccessNeon else Color.White
                    )
                    Text(
                        stringResource(R.string.hdr_lab_fullscreen_hint),
                        fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

/**
 * ★ 2026-08-16 HDR 实验室 — TurboXDR 局部 HDR 控制卡片。
 *
 * 与设置页 CyberNightlightTurboXdrSettingsCard 同款控件：总开关 + 强度滑条（1.0×–8.0×）。
 * 目的：在 HDR 实验室边验证 headroom 边微调局部 HDR 增亮强度，即时生效（写 AppSettings + 同步 CyberNightlightSwitch）。
 * 强度滑条同时映射到 HdrLumeSurfaceView.setDesiredHdrHeadroom 控制顶部夜光条亮度。
 */
@Composable
private fun HdrLabTurboXdrCard() {
    val ctx = LocalContext.current
    val settings = remember { AppSettings.getInstance(ctx) }
    var enabled by remember { mutableStateOf(CyberNightlightSwitch.enabled) }
    var intensity by remember { mutableFloatStateOf(CyberNightlightSwitch.intensity) }

    Card(
        Modifier
            .fillMaxWidth()
            .cardGradientBorder(20.dp, hdrHighlight = true),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(Modifier.fillMaxWidth().background(HdrLabCardGradient)) {
            Column(Modifier.padding(16.dp)) {
                // ── 总开关行 ──
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

                // ── HDR 强度滑条（1.0×..8.0×，对应 setDesiredHdrHeadroom）──
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
                FancySlider(
                    value = intensity,
                    onValueChange = { intensity = it },
                    onValueChangeFinished = {
                        settings.cyberNightlightTurboXdrIntensity = intensity
                        CyberNightlightSwitch.intensity = intensity
                    },
                    valueRange = 1f..8f,
                    steps = 69,  // 0.1× 步长 → (8-1)/0.1 - 1 = 69 个中间挡
                    modifier = Modifier.fillMaxWidth(),
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
}

/** 诊断行 — 与 DeviceScreen.RowItem 同款左右布局 */
@Composable
private fun HdrLabDiagRow(
    label: String,
    value: String,
    valueColor: Color = NeonPurpleBright
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label, fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
            maxLines = 2, softWrap = true
        )
        Text(
            value, fontSize = 13.sp,
            color = valueColor,
            modifier = Modifier.weight(0.6f),
            maxLines = 3, softWrap = true
        )
    }
}
