package com.rb.cybermonitorpro.ui.nightlight

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rb.cybermonitorpro.ui.effects.CyberNightlightSwitch
import com.rb.cybermonitorpro.ui.theme.NeonCyan
import com.rb.cybermonitorpro.ui.theme.NeonPurpleBright
import com.rb.cybermonitorpro.ui.theme.DividerCyber
import java.util.UUID
import kotlin.math.pow

// ── 各贴片亮度倍率（线性光，相对 SDR 白；真机微调）──
// 建议值见可执行方案 §5.4：描边 3–5×、指示条 5–7×、数字 4–6×、折线 4–6×、网格 1.3–1.8×。
const val HDR_CARD_MULT: Float = 4.0f
const val HDR_TAB_MULT: Float = 6.0f
const val HDR_TEXT_MULT: Float = 5.0f
const val HDR_LINE_MULT: Float = 5.0f
const val HDR_GRID_MULT: Float = 1.5f

// SDR 白 ≈ 203 nit；PQ 映射 L = linear * (203/10000) = linear * 0.0203
private const val SDR_WHITE_L = 0.0203f

/** sRGB 电信号 → 线性光（0..1，相对 SDR 白=1.0）。 */
fun srgbToLinear(c: Float): Float =
    if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()

/** ST 2084 PQ OETF：归一化亮度 L（1.0 = 10000 nit）→ PQ 码值（0..1）。 */
fun pqOETF(L: Float): Float {
    val m1 = 0.1593017578125
    val m2 = 78.84375
    val c1 = 0.8359375
    val c2 = 18.8515625
    val c3 = 18.6875
    val Lm = L.toDouble().pow(m1).toFloat()
    return ((c1 + c2 * Lm) / (1f + c3 * Lm)).toDouble().pow(m2).toFloat()
}

/** 把 sRGB 颜色按倍率编码成 PQ 码值三元组（0..1）。 */
fun encodePq(color: Color, mult: Float): FloatArray {
    val s = mult * SDR_WHITE_L
    return floatArrayOf(
        pqOETF(srgbToLinear(color.red) * s),
        pqOETF(srgbToLinear(color.green) * s),
        pqOETF(srgbToLinear(color.blue) * s)
    )
}

/**
 * 卡片描边贴片上报。挂在 cardGradientBorder 内部（已 @Composable，可用 remember 生成稳定 key）。
 * 仅当 TurboXDR 开关开启时上报；关闭时该修饰符不做事（SDR 卡片描边保持原样）。
 */
fun Modifier.hdrCardBorderPatch(key: String, cornerPx: Float, strokePx: Float): Modifier = this
    .onGloballyPositioned { coords ->
        if (!CyberNightlightSwitch.enabled) {
            HdrPatchRegistry.remove(key)
            return@onGloballyPositioned
        }
        val pos = coords.localToWindow(Offset.Zero)
        val b = android.graphics.RectF(
            pos.x, pos.y, pos.x + coords.size.width.toFloat(), pos.y + coords.size.height.toFloat()
        )
        HdrPatchRegistry.upsert(
            HdrPatch(
                id = key,
                type = HdrPatchType.CARD_BORDER,
                bounds = b,
                color0 = encodePq(NeonPurpleBright, HDR_CARD_MULT),
                color1 = encodePq(NeonCyan, HDR_CARD_MULT),
                cornerRadiusPx = cornerPx,
                strokeWidthPx = strokePx
            )
        )
    }

/**
 * Tab 选中指示条贴片上报。挂在 TabRowDefaults.Indicator 的 Modifier 上。
 */
fun Modifier.hdrTabIndicatorPatch(key: String): Modifier = this
    .onGloballyPositioned { coords ->
        if (!CyberNightlightSwitch.enabled) {
            HdrPatchRegistry.remove(key)
            return@onGloballyPositioned
        }
        val pos = coords.localToWindow(Offset.Zero)
        val b = android.graphics.RectF(
            pos.x, pos.y, pos.x + coords.size.width.toFloat(), pos.y + coords.size.height.toFloat()
        )
        HdrPatchRegistry.upsert(
            HdrPatch(
                id = key,
                type = HdrPatchType.TAB_INDICATOR,
                bounds = b,
                color0 = encodePq(NeonCyan, HDR_TAB_MULT)
            )
        )
    }

/**
 * 大数字 HDR 字形。始终渲染原 SDR Text（保留布局/测量，且永不消失），
 * 并在 TurboXDR 开启时把字形本体上报为 TEXT_GLYPH 贴片，由 PQ surface 叠加真实 HDR 增亮。
 *
 * 采用"叠加"而非"隐藏 SDR"：即便 PQ 纹理因任何原因失败，数字仍是清晰可读的 SDR 文本。
 */
@Composable
fun HdrMetricText(
    text: String,
    fontSize: TextUnit,
    color: Color,
    letterSpacing: TextUnit = 1.5.sp,
    modifier: Modifier = Modifier
) {
    val key = remember { "metric:" + UUID.randomUUID().toString() }
    val density = LocalDensity.current
    val enabled = CyberNightlightSwitch.enabled

    Text(
        text = text,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        color = color,
        fontFamily = FontFamily.Monospace,
        letterSpacing = letterSpacing,
        maxLines = 4,
        overflow = TextOverflow.Ellipsis,
        softWrap = true,
        modifier = modifier
            .onGloballyPositioned { coords ->
                if (!enabled) {
                    HdrPatchRegistry.remove(key)
                    return@onGloballyPositioned
                }
                val pos = coords.localToWindow(Offset.Zero)
                val b = android.graphics.RectF(
                    pos.x, pos.y, pos.x + coords.size.width.toFloat(), pos.y + coords.size.height.toFloat()
                )
                val textSizePx = with(density) { fontSize.toPx() }
                val lsEm = if (fontSize.value > 0f) letterSpacing.value / fontSize.value else 0f
                HdrPatchRegistry.upsert(
                    HdrPatch(
                        id = key,
                        type = HdrPatchType.TEXT_GLYPH,
                        bounds = b,
                        color0 = encodePq(color, HDR_TEXT_MULT),
                        text = text,
                        textSizePx = textSizePx,
                        textBold = true,
                        textMonospace = true,
                        letterSpacingEm = lsEm
                    )
                )
            }
    )

    DisposableEffect(key) {
        onDispose { HdrPatchRegistry.remove(key) }
    }
}

// ───────────────────────────────────────────────────────────────────────────
// 折线 + 网格 HDR 贴片
// ───────────────────────────────────────────────────────────────────────────

/**
 * 折线/网格贴片的窗口坐标缓存（非 State，避免写触发重组）。
 * onGloballyPositioned 写入窗口偏移与画布尺寸；LaunchedEffect(data) 复用之做实时重新上报。
 */
private class ChartPatchHolder {
    var winX = 0f
    var winY = 0f
    var w = 0f
    var h = 0f
    val ready: Boolean get() = w > 0f && h > 0f
}

private fun sanitizeChart(v: Float): Float = if (v.isNaN() || v.isInfinite()) 0f else v

/**
 * 折线本体 + 网格线 HDR 贴片上报。挂在 LineChart 的 Canvas 上。
 *
 * 折线点/网格线坐标只能在布局后（已知 size + 窗口偏移）算出，故在 onGloballyPositioned 内
 * 用与 LineChart.kt 内部绘制【完全一致的公式】（pad=8.dp、v∈[0,1]、xStep=cw/(n-1)）计算并上报：
 *  - CHART_LINE：整条折线（窗口坐标点序列）+ 尾点；亮度倍率 HDR_LINE_MULT。
 *  - CHART_GRID：(gridLines+1) 条横线端点（GL_LINES 成对）；亮度倍率 HDR_GRID_MULT。
 * 另用 LaunchedEffect(data) 在数据实时变化时重新上报（实时折线）。
 *
 * 关闭开关时移除两个贴片（避免残留幽灵折线）。
 */
fun Modifier.hdrChartPatches(
    chartKey: String,
    data: List<Float>,
    lineColor: Color,
    gridLines: Int,
    showGrid: Boolean
): Modifier = composed {
    val density = LocalDensity.current
    val enabled = CyberNightlightSwitch.enabled
    val holder = remember { ChartPatchHolder() }

    fun report() {
        if (!enabled || !holder.ready) {
            HdrPatchRegistry.remove(chartKey + ".line")
            HdrPatchRegistry.remove(chartKey + ".grid")
            return
        }
        reportChartPatches(holder, chartKey, data, lineColor, gridLines, showGrid, density)
    }

    // 实时数据变化重新上报
    LaunchedEffect(data, enabled) { report() }

    // 卸载/切页时清理
    DisposableEffect(Unit) {
        onDispose {
            HdrPatchRegistry.remove(chartKey + ".line")
            HdrPatchRegistry.remove(chartKey + ".grid")
        }
    }

    Modifier.onGloballyPositioned { coords ->
        holder.winX = coords.localToWindow(Offset.Zero).x
        holder.winY = coords.localToWindow(Offset.Zero).y
        holder.w = coords.size.width.toFloat()
        holder.h = coords.size.height.toFloat()
        report()
    }
}

/**
 * 在窗口坐标系计算并上报折线 + 网格贴片（公式与 LineChart.kt 内部绘制一致，确保两者像素级对齐）。
 */
private fun reportChartPatches(
    holder: ChartPatchHolder,
    chartKey: String,
    data: List<Float>,
    lineColor: Color,
    gridLines: Int,
    showGrid: Boolean,
    density: Density
) {
    val w = holder.w
    val h = holder.h
    val pad = with(density) { 8.dp.toPx() }
    val cw = w - pad * 2f
    val ch = h - pad * 2f
    val ox = holder.winX
    val oy = holder.winY

    // 整图窗口包围盒（供渲染器屏外裁剪；CHART_LINE/CHART_GRID 均以此判裁剪）
    val bounds = android.graphics.RectF(ox, oy, ox + w, oy + h)

    // ── 折线本体 ──
    if (data.size >= 2) {
        val n = data.size
        val pts = FloatArray(n * 2)
        val xStep = cw / (n - 1).coerceAtLeast(1)
        for (i in 0 until n) {
            val x = pad + xStep * i
            val y = pad + ch - sanitizeChart(data[i]).coerceIn(0f, 1f) * ch
            pts[2 * i] = ox + x
            pts[2 * i + 1] = oy + y
        }
        HdrPatchRegistry.upsert(
            HdrPatch(
                id = chartKey + ".line",
                type = HdrPatchType.CHART_LINE,
                bounds = bounds,
                color0 = encodePq(lineColor, HDR_LINE_MULT),
                points = pts,
                strokeWidthPx = 3.5f,
                tailDotRadiusPx = 4f
            )
        )
    } else {
        HdrPatchRegistry.remove(chartKey + ".line")
    }

    // ── 网格横线（GL_LINES：每段 2 点 × 2 坐标）──
    if (showGrid && gridLines > 0) {
        val segs = gridLines + 1
        val gpts = FloatArray(segs * 4)
        for (i in 0 until segs) {
            val y = pad + (ch / gridLines) * i
            gpts[4 * i] = ox + pad
            gpts[4 * i + 1] = oy + y
            gpts[4 * i + 2] = ox + (w - pad)
            gpts[4 * i + 3] = oy + y
        }
        HdrPatchRegistry.upsert(
            HdrPatch(
                id = chartKey + ".grid",
                type = HdrPatchType.CHART_GRID,
                bounds = bounds,
                color0 = encodePq(DividerCyber, HDR_GRID_MULT),
                points = gpts
            )
        )
    } else {
        HdrPatchRegistry.remove(chartKey + ".grid")
    }
}
