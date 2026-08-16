package com.rb.cybermonitorpro.ui.device

import android.content.Context
import android.os.Build
import android.view.Display
import android.view.WindowManager
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import com.rb.cybermonitorpro.R
import com.rb.cybermonitorpro.ui.components.CyberIcons
import com.rb.cybermonitorpro.ui.effects.cardGradientBorder
import com.rb.cybermonitorpro.ui.theme.CyberCardEnd
import com.rb.cybermonitorpro.ui.theme.CyberCardStart
import com.rb.cybermonitorpro.ui.theme.NeonCyan
import com.rb.cybermonitorpro.ui.theme.NeonPurple
import com.rb.cybermonitorpro.ui.theme.NeonPurpleBright
import com.rb.cybermonitorpro.ui.theme.NeonSteelBlue
import com.rb.cybermonitorpro.ui.theme.SuccessNeon
import com.rb.cybermonitorpro.ui.theme.TextSecondary
import com.rb.cybermonitorpro.ui.theme.WarningNeon
import kotlinx.coroutines.delay

private val HdrLabCardGradient = Brush.linearGradient(listOf(CyberCardStart, CyberCardEnd))

/**
 * ★ 2026-08-16 HDR 实验室 — 局部 EDR（HDR headroom）真机验证页（详情页二层覆盖层）。
 *
 * 对齐参考截图：潜在 EDR 亮度余量读数 + SDR/HDR 对比块 + headroom 滑条 + 实际比值诊断。
 * 这是 CyberNightlight TurboXDR 落地前的保险验证步骤，只做局部测试，不改全局渲染。
 *
 * 数据口径（全部经 runCatching / SDK_INT 守卫，低版本静默降级）：
 *  - 潜在余量：Display.getHighestHdrSdrRatio()（API 36+，compileSdk 36 直接调用）；
 *  - 滑条控制：SurfaceView.setDesiredHdrHeadroom()（API 35+）；
 *  - 实际生效：Display.getHdrSdrRatio()（API 34+，>1.01 判定 HDR 图层真正点亮）。
 *
 * 注意：截图会被系统色调映射到 SDR，亮度差异只能真机肉眼 + ratio 双确认，勿凭截图判断。
 */
@Composable
fun HdrLabScreen(onBack: () -> Unit = {}) {
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

    var desired by remember { mutableFloatStateOf(1f) }
    var actualRatio by remember { mutableFloatStateOf(1f) }
    var ratioAvailable by remember { mutableStateOf(false) }
    var pqActive by remember { mutableStateOf(false) }
    var eglSummary by remember { mutableStateOf("pending") }
    val hdrView = remember { mutableStateOf<HdrTestSurfaceView?>(null) }

    // 500ms 轮询诊断：实际 HDR/SDR 比（API 34+）+ PQ surface / EGL 状态
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
            delay(500)
        }
    }

    // 滑条上限：API 36 用真实最高比；不可知时给 4× 演示上限
    // （setDesiredHdrHeadroom 允许 1.0~10000.0，实际生效由系统裁定，此处仅定 UI 范围）
    val sliderMax = if (potentialHeadroom > 1f) potentialHeadroom else 4f
    val canControl = Build.VERSION.SDK_INT >= 35

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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
                                AndroidView(
                                    factory = { c ->
                                        HdrTestSurfaceView(c).also { hdrView.value = it }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
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
                        Slider(
                            value = desired,
                            onValueChange = { v ->
                                desired = v
                                hdrView.value?.applyHeadroom(v)
                            },
                            valueRange = 1f..sliderMax,
                            enabled = canControl,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = NeonPurpleBright,
                                activeTrackColor = NeonPurple,
                                inactiveTrackColor = NeonSteelBlue.copy(alpha = 0.3f),
                                disabledThumbColor = NeonSteelBlue.copy(alpha = 0.4f),
                                disabledActiveTrackColor = NeonSteelBlue.copy(alpha = 0.3f),
                                disabledInactiveTrackColor = NeonSteelBlue.copy(alpha = 0.2f)
                            )
                        )
                        Icon(
                            CyberIcons.Light, null,
                            tint = NeonPurpleBright,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(
                        stringResource(R.string.hdr_lab_brightness, desired),
                        Modifier.fillMaxWidth(),
                        fontSize = 13.sp, color = NeonCyan,
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
                }
            }
        }

        // ── 截图失真提示 ──
        Text(
            stringResource(R.string.hdr_lab_hint_screenshot),
            fontSize = 11.sp, color = TextSecondary
        )
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
