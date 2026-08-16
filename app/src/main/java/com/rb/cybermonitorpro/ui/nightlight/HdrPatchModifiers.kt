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
import com.rb.cybermonitorpro.data.model.GpsSatelliteInfo
import com.rb.cybermonitorpro.ui.components.constellationColor
import java.util.UUID
import kotlin.math.pow

// ── 各贴片亮度倍率（线性光，相对 SDR 白；真机微调）──
// 建议值见可执行方案 §5.4：描边 3–5×、指示条 5–7×、数字 4–6×、折线 4–6×、网格 1.3–1.8×。
const val HDR_CARD_MULT: Float = 4.0f
const val HDR_TAB_MULT: Float = 6.0f
const val HDR_TEXT_MULT: Float = 5.0f
const val HDR_LINE_MULT: Float = 5.0f
const val HDR_GRID_MULT: Float = 1.5f
// 卫星天空图图内点亮度倍率（小圆点，需要明显跳出）
const val HDR_DOT_MULT: Float = 5.0f

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
fun Modifier.hdrCardBorderPatch(key: String, cornerPx: Float, strokePx: Float): Modifier = composed {
    // ★ 修复(残留重叠): 卡片滑出 LazyColumn 时 onGloballyPositioned 不再触发，
    //   必须靠 DisposableEffect 在组合退出时注销，否则幽灵描边持续残留/重叠。
    DisposableEffect(key) {
        onDispose { HdrPatchRegistry.remove(key) }
    }
    this.onGloballyPositioned { coords ->
        if (!CyberNightlightSwitch.enabled) {
            HdrPatchRegistry.remove(key)
            return@onGloballyPositioned
        }
        // ★ 修复(偏低): 改用 localToRoot（内容根坐标），与 PQ surface 像素原点（内容 Box 左上）一致；
        //   localToWindow 为窗口绝对坐标（含状态栏），会使整体下移 ~状态栏高度。
        val pos = coords.localToRoot(Offset.Zero)
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
}

/**
 * Tab 选中指示条贴片上报。挂在 TabRowDefaults.Indicator 的 Modifier 上。
 */
fun Modifier.hdrTabIndicatorPatch(key: String): Modifier = composed {
    // ★ 修复(残留重叠): 指示条随 Tab 行常驻，但组合退出（离开主界面）时仍需注销。
    DisposableEffect(key) {
        onDispose { HdrPatchRegistry.remove(key) }
    }
    this.onGloballyPositioned { coords ->
        if (!CyberNightlightSwitch.enabled) {
            HdrPatchRegistry.remove(key)
            return@onGloballyPositioned
        }
        // ★ 修复(偏低): localToRoot 对齐内容根像素原点。
        val pos = coords.localToRoot(Offset.Zero)
        val b = android.graphics.RectF(
            pos.x, pos.y, pos.x + coords.size.width.toFloat(), pos.y + coords.size.height.toFloat()
        )
        // topZone=true：指示条位于顶部 Tab 区，两段式渲染时绕过"顶撞裁剪"。
        HdrPatchRegistry.upsert(
            HdrPatch(
                id = key,
                type = HdrPatchType.TAB_INDICATOR,
                bounds = b,
                color0 = encodePq(NeonCyan, HDR_TAB_MULT),
                topZone = true
            )
        )
    }
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
    modifier: Modifier = Modifier,
    // 顶部 Tab 区贴片：绕过"顶撞裁剪"
    topZone: Boolean = false,
    // 门控：false 时不上报（用于仅选中 Tab 才点亮标签）
    gate: Boolean = true,
    // 字形是否等宽（大数字默认等宽；Tab 标签用系统字体）
    monospace: Boolean = true,
    fontWeight: FontWeight = FontWeight.Bold
) {
    val key = remember { "metric:" + UUID.randomUUID().toString() }
    val density = LocalDensity.current
    val enabled = CyberNightlightSwitch.enabled && gate

    Text(
        text = text,
        fontSize = fontSize,
        fontWeight = fontWeight,
        color = color,
        fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
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
                // ★ 修复(偏低): localToRoot 对齐内容根像素原点。
                val pos = coords.localToRoot(Offset.Zero)
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
                        textMonospace = monospace,
                        letterSpacingEm = lsEm,
                        topZone = topZone
                    )
                )
            }
    )

    DisposableEffect(key) {
        onDispose { HdrPatchRegistry.remove(key) }
    }
    // 门控翻转为 false（如 Tab 切换）时立即注销，避免残留幽灵标签。
    LaunchedEffect(enabled) {
        if (!enabled) HdrPatchRegistry.remove(key)
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
        // ★ 修复(偏低): localToRoot 对齐内容根像素原点。
        holder.winX = coords.localToRoot(Offset.Zero).x
        holder.winY = coords.localToRoot(Offset.Zero).y
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

// ───────────────────────────────────────────────────────────────────────────
// 顶部 Tab 图标 / 卫星天空图图内点 HDR 贴片
// ───────────────────────────────────────────────────────────────────────────

/**
 * 顶部 Tab 选中项矢量图标 HDR 光环。挂在 Icon 的 Modifier 上，仅当 [selected] 时上报，
 * 在图标外圈描一圈霓虹圆角描边（复用 CARD_BORDER），topZone=true 绕过"顶撞裁剪"。
 */
fun Modifier.hdrTabIconPatch(key: String, selected: Boolean): Modifier = composed {
    val density = LocalDensity.current
    val enabled = CyberNightlightSwitch.enabled && selected
    DisposableEffect(key) {
        onDispose { HdrPatchRegistry.remove(key) }
    }
    // 门控翻转为 false（Tab 切换）时立即注销，避免残留幽灵光环。
    LaunchedEffect(enabled) {
        if (!enabled) HdrPatchRegistry.remove(key)
    }
    this.onGloballyPositioned { coords ->
        if (!enabled) {
            HdrPatchRegistry.remove(key)
            return@onGloballyPositioned
        }
        // ★ 修复(偏低): localToRoot 对齐内容根像素原点。
        val pos = coords.localToRoot(Offset.Zero)
        val w = coords.size.width.toFloat()
        val h = coords.size.height.toFloat()
        val pad = with(density) { 3.dp.toPx() }
        val half = (if (w < h) w else h) / 2f + pad
        val b = android.graphics.RectF(
            pos.x - pad, pos.y - pad, pos.x + w + pad, pos.y + h + pad
        )
        HdrPatchRegistry.upsert(
            HdrPatch(
                id = key,
                type = HdrPatchType.CARD_BORDER,
                bounds = b,
                color0 = encodePq(NeonPurpleBright, HDR_TAB_MULT),
                color1 = encodePq(NeonCyan, HDR_TAB_MULT),
                cornerRadiusPx = half,
                strokeWidthPx = with(density) { 2.dp.toPx() },
                topZone = true
            )
        )
    }
}

/**
 * 卫星天空图图内点 HDR 贴片上报。挂在 SkyPlot 的 Canvas 上，按与 drawSkyPlot 完全一致的
 * 极坐标公式算出每颗卫星圆点的根坐标，上报为（复用 CARD_BORDER 全填充圆）贴片。
 * 仅当 TurboXDR 开启且卫星有效时上报；卫星列表变化时增量增删，避免残留。
 */
fun Modifier.hdrSatSkyPatch(satellites: List<GpsSatelliteInfo>): Modifier = composed {
    val density = LocalDensity.current
    val enabled = CyberNightlightSwitch.enabled
    val holder = remember { SkyPatchHolder() }

    fun report() {
        if (!enabled || !holder.ready) {
            for (id in holder.activeIds) HdrPatchRegistry.remove(id)
            holder.activeIds.clear()
            return
        }
        val cx = holder.w / 2f
        val cy = holder.h / 2f
        val canvasSize = if (holder.w < holder.h) holder.w else holder.h
        val radius = (canvasSize / 2f) * 0.92f
        val seen = mutableSetOf<String>()
        for (i in satellites.indices) {
            val sat = satellites[i]
            if (sat.elevation.isNaN() || sat.azimuth.isNaN()) continue
            val elev = sat.elevation.coerceIn(0f, 90f)
            val az = sat.azimuth
            val angleRad = Math.toRadians((90.0 - az).toDouble()).toFloat()
            val dist = radius * (1f - elev / 90f)
            val sx = cx + (Math.cos(angleRad.toDouble()).toFloat()) * dist
            val sy = cy - (Math.sin(angleRad.toDouble()).toFloat()) * dist
            val dotR = with(density) { if (sat.usedInFix) 5.dp.toPx() else 3.5.dp.toPx() }
            val gx = holder.x + sx
            val gy = holder.y + sy
            val id = "satsky:$i"
            seen.add(id)
            HdrPatchRegistry.upsert(
                HdrPatch(
                    id = id,
                    type = HdrPatchType.CARD_BORDER,
                    bounds = android.graphics.RectF(gx - dotR, gy - dotR, gx + dotR, gy + dotR),
                    color0 = encodePq(constellationColor(sat.constellationType), HDR_DOT_MULT),
                    cornerRadiusPx = dotR,
                    strokeWidthPx = dotR * 2f
                )
            )
        }
        // 清理已消失的卫星点
        holder.activeIds.removeAll { id ->
            if (id !in seen) {
                HdrPatchRegistry.remove(id)
                true
            } else false
        }
        holder.activeIds.addAll(seen)
    }

    LaunchedEffect(satellites, enabled) { report() }
    DisposableEffect(Unit) {
        onDispose {
            for (id in holder.activeIds) HdrPatchRegistry.remove(id)
            holder.activeIds.clear()
        }
    }
    // ★ 修复(偏低): localToRoot 对齐内容根像素原点。
    Modifier.onGloballyPositioned { coords ->
        holder.x = coords.localToRoot(Offset.Zero).x
        holder.y = coords.localToRoot(Offset.Zero).y
        holder.w = coords.size.width.toFloat()
        holder.h = coords.size.height.toFloat()
        report()
    }
}

private class SkyPatchHolder {
    var x = 0f
    var y = 0f
    var w = 0f
    var h = 0f
    val ready: Boolean get() = w > 0f && h > 0f
    val activeIds = mutableSetOf<String>()
}
