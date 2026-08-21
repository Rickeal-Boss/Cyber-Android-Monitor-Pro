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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.core.graphics.PathParser
import com.rb.cybermonitorpro.ui.effects.CyberNightlightSwitch
import com.rb.cybermonitorpro.ui.theme.NeonCyan
import com.rb.cybermonitorpro.ui.theme.NeonPurple
import com.rb.cybermonitorpro.ui.theme.NeonPurpleBright
import com.rb.cybermonitorpro.ui.theme.DividerCyber
import com.rb.cybermonitorpro.data.model.GpsSatelliteInfo
import com.rb.cybermonitorpro.ui.components.CyberIcons
import com.rb.cybermonitorpro.ui.components.constellationColor
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import java.util.UUID
import kotlin.math.pow

// ── 各贴片「设计峰值」亮度倍率（线性光，相对 SDR 白；真机微调）──
// 作为 HdrPatch.bias 传入渲染器；最终亮度 = 线性光 × effMult(bias, 滑块)，其中
// effMult 在 滑块 1.0× 时=1（恰 SDR 白，真·关闭）、滑块 8.0× 时=bias（保留原设计峰值）。
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

/**
 * 把 sRGB 颜色换算成**线性光**三元组（相对 SDR 白=1.0）。
 *
 * 注意：本函数**不再**做 ST 2084 PQ OETF，也**不再预乘**任何类型倍率 —— 倍率由
 * [HdrPatch.bias] 携带、[PatchRenderer.pqEnc] 按当前 TurboXDR 强度 [CyberNightlightSwitch.intensity]
 * 线性插值施加。这样 1.0× 滑块即输出恰 SDR 白（真·关闭），且亮度倍率始终处于线性光域。
 * 默认 [mult]=1f：仅产出 SDR 白（1.0×）基准线性光；[bias] 字段承载各类型设计峰值。
 */
fun encodePq(color: Color, mult: Float = 1f): FloatArray {
    val s = mult * SDR_WHITE_L
    return floatArrayOf(
        srgbToLinear(color.red) * s,
        srgbToLinear(color.green) * s,
        srgbToLinear(color.blue) * s
    )
}

// ── R2 修复：主题颜色线性光预编码（颜色为编译期常量，encodePq 结果不可变）──
// onGloballyPositioned 每帧调用 encodePq(NeonCyan) 等会 new FloatArray(3)，
// 约 40 贴片 × 60fps × 2 颜色 ≈ 每秒 4800 个短命 FloatArray。预计算后零分配。
// FloatArray 内容只读不写（渲染器仅 pqEnc(color[i], bias) 读取），多贴片共享安全。
// 注：必须声明在 encodePq 函数定义之后（顶层属性初始化按声明顺序执行）。
private val ENCODED_NEON_CYAN = encodePq(NeonCyan)
private val ENCODED_NEON_PURPLE = encodePq(NeonPurple)
private val ENCODED_NEON_PURPLE_BRIGHT = encodePq(NeonPurpleBright)
private val ENCODED_DIVIDER_CYBER = encodePq(DividerCyber)

/**
 * 卡片描边贴片上报。挂在 cardGradientBorder 内部（已 @Composable，可用 remember 生成稳定 key）。
 * 仅当 TurboXDR 开关开启时上报；关闭时该修饰符不做事（SDR 卡片描边保持原样）。
 */
fun Modifier.hdrCardBorderPatch(key: String, cornerPx: Float, strokePx: Float): Modifier = composed {
    // ★ R2 修复：复用 scratch RectF（.set() 原地更新），HdrPatch 构造时防御性拷贝，
    //   消除滚动期间 onGloballyPositioned 每帧 new RectF。
    val scratchRect = remember { android.graphics.RectF() }
    // ★ 修复(残留重叠): 卡片滑出 LazyColumn 时 onGloballyPositioned 不再触发，
    //   必须靠 DisposableEffect 在组合退出时注销，否则幽灵描边持续残留/重叠。
    DisposableEffect(key) {
        onDispose { HdrPatchRegistry.remove(key) }
    }
    this.onGloballyPositioned { coords ->
        // ★ R4 修复：关闭路径仅早退（不 remove）——注销统一交给 DisposableEffect.onDispose；
        //   避免关闭瞬间 remove 与开关翻转竞态导致贴片残留/闪烁。
        if (!CyberNightlightSwitch.enabled) return@onGloballyPositioned
        // ★ 修复(偏低): 改用 localToRoot（内容根坐标），与 PQ surface 像素原点（内容 Box 左上）一致；
        //   localToWindow 为窗口绝对坐标（含状态栏），会使整体下移 ~状态栏高度。
        val pos = coords.localToRoot(Offset.Zero)
        scratchRect.set(
            pos.x, pos.y, pos.x + coords.size.width.toFloat(), pos.y + coords.size.height.toFloat()
        )
        HdrPatchRegistry.upsert(
            HdrPatch(
                id = key,
                type = HdrPatchType.CARD_BORDER,
                bounds = scratchRect,
                color0 = ENCODED_NEON_PURPLE_BRIGHT,
                color1 = ENCODED_NEON_CYAN,
                bias = HDR_CARD_MULT,
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
    // ★ R2 修复：复用 scratch RectF（.set() 原地更新），HdrPatch 构造时防御性拷贝。
    val scratchRect = remember { android.graphics.RectF() }
    // ★ 修复(残留重叠): 指示条随 Tab 行常驻，但组合退出（离开主界面）时仍需注销。
    DisposableEffect(key) {
        onDispose { HdrPatchRegistry.remove(key) }
    }
    this.onGloballyPositioned { coords ->
        // ★ R4 修复：关闭路径仅早退（不 remove）——注销统一交给 DisposableEffect.onDispose；
        //   避免关闭瞬间 remove 与开关翻转竞态导致贴片残留/闪烁。
        if (!CyberNightlightSwitch.enabled) return@onGloballyPositioned
        // ★ 修复(偏低): localToRoot 对齐内容根像素原点。
        val pos = coords.localToRoot(Offset.Zero)
        scratchRect.set(
            pos.x, pos.y, pos.x + coords.size.width.toFloat(), pos.y + coords.size.height.toFloat()
        )
        // topZone=true：指示条位于顶部 Tab 区，两段式渲染时绕过"顶撞裁剪"。
        HdrPatchRegistry.upsert(
            HdrPatch(
                id = key,
                type = HdrPatchType.TAB_INDICATOR,
                bounds = scratchRect,
                color0 = ENCODED_NEON_CYAN,
                bias = HDR_TAB_MULT,
                topZone = true
            )
        )
    }
}

/**
 * 顶部 Tab 栏药丸描边 HDR 贴片上报。挂在 Tab 栏容器 Box 上（与 neonBorderGlow 同款圆角/描边）。
 *
 * 与 hdrCardBorderPatch 的区别：topZone=true —— 药丸位于顶部 Tab 区，
 * 两段式渲染时绕过"顶撞裁剪"（contentClipTop 设在药丸底部，普通 content 贴片会被裁掉）。
 * 仅当 TurboXDR 开启时上报，关闭时移除（SDR 描边保持原样）。
 */
fun Modifier.hdrTabBarBorderPatch(
    key: String,
    cornerDp: Dp = 26.dp,
    strokeDp: Dp = 1.5.dp
): Modifier = composed {
    val density = LocalDensity.current
    // ★ R2 修复：复用 scratch RectF（.set() 原地更新），HdrPatch 构造时防御性拷贝。
    val scratchRect = remember { android.graphics.RectF() }
    DisposableEffect(key) {
        onDispose { HdrPatchRegistry.remove(key) }
    }
    this.onGloballyPositioned { coords ->
        // ★ R4 修复：关闭路径仅早退（不 remove）——注销统一交给 DisposableEffect.onDispose；
        //   避免关闭瞬间 remove 与开关翻转竞态导致贴片残留/闪烁。
        if (!CyberNightlightSwitch.enabled) return@onGloballyPositioned
        // ★ 修复(偏低): localToRoot 对齐内容根像素原点；圆角/描边与 neonBorderGlow 一致。
        val pos = coords.localToRoot(Offset.Zero)
        scratchRect.set(
            pos.x, pos.y, pos.x + coords.size.width.toFloat(), pos.y + coords.size.height.toFloat()
        )
        HdrPatchRegistry.upsert(
            HdrPatch(
                id = key,
                type = HdrPatchType.CARD_BORDER,
                bounds = scratchRect,
                color0 = ENCODED_NEON_PURPLE_BRIGHT,
                color1 = ENCODED_NEON_CYAN,
                bias = HDR_CARD_MULT,
                cornerRadiusPx = with(density) { cornerDp.toPx() },
                strokeWidthPx = with(density) { strokeDp.toPx() },
                topZone = true
            )
        )
    }
}

/**
 * 贴片窗口坐标的普通持有者（非 Compose State）。
 *
 * ★ pre18：位置写入必须避免触发重组。onGloballyPositioned 在垂直滚动时每帧回调，
 *   若位置存进 mutableStateOf，写新值会触发本 Composable 重组（「上下滑动 HDR 贴片重组」）。
 *   改存普通字段后，滚动期间仅直接更新注册表（GL 渲染器重绘），不再触发 Compose 重组。
 */
private class PatchRectHolder {
    var value: android.graphics.RectF? = null
}

/**
 * ★ R1 修复：HdrMetricText 测量宽度持有者（非 State）。
 *   垂直滚动时 onGloballyPositioned 每帧回调，仅当宽度实际变化才写 State，
 *   避免每帧写同一值触发无谓重组（位图重建 DisposableEffect）。
 */
private class IntHolder {
    var value: Int = 0
}

/**
 * 大数字 HDR 字形。始终渲染原 SDR Text（保留布局/测量，且永不消失），
 * 并在 TurboXDR 开启时把字形本体（精确栅格化的位图掩码）上报为 TEXT_GLYPH 贴片，
 * 由 PQ surface 叠加真实 HDR 增亮。
 *
 * 修正（pre6 真机）：不再按原始字体度量现场生成字形再拉伸进 composable 包围盒
 * （导致非均匀缩放→窄平/错位），改用 TextMeasurer 取得与 Compose 完全一致的
 * 布局尺寸与基线/左缘，栅格化到与包围盒等宽高的位图，纹理→quad 1:1 像素级对齐。
 * 采用"叠加"而非"隐藏 SDR"：即便位图因任何原因失败，数字仍是清晰可读的 SDR 文本。
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
    fontWeight: FontWeight = FontWeight.Bold,
    // 以下三项须与下方 Text 完全一致，否则 SDR 与 HDR 测量/换行不同步
    maxLines: Int = Int.MAX_VALUE,
    softWrap: Boolean = true,
    overflow: TextOverflow = TextOverflow.Clip
) {
    val key = remember { "metric:" + UUID.randomUUID().toString() }
    val density = LocalDensity.current
    val enabled = CyberNightlightSwitch.enabled && gate
    val measurer = rememberTextMeasurer()
    // ★ R2 修复：动态颜色 remember 缓存——同一生命周期内 color 极少变化，
    //   避免每次 report() 都 encodePq(color) new FloatArray(3)。
    val encodedColor = remember(color) { encodePq(color) }

    // 字形位图：仅在文本或样式变化时重建（数字每秒刷新→旧位图在 onDispose 回收，避免泄漏）
    val bitmapState = remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    // 关键修复：记录 Text composable 的实际布局宽度，buildTextBitmap 用同宽度约束测量，
    // 保证 HDR 位图与 SDR Text 换行/行数完全一致，避免错位/重影。
    var measuredWidthPx by remember { mutableStateOf(0) }
    // ★ R1 修复：宽度持有者（非 State）。onGloballyPositioned 垂直滚动期间每帧回调，
    //   仅当宽度实际变化才写 measuredWidthPx，避免每帧写同一值触发位图重建重组。
    val widthHolder = remember { IntHolder() }
    DisposableEffect(text, fontSize, fontWeight, monospace, letterSpacing, measuredWidthPx, maxLines, softWrap, overflow) {
        val constraints = if (measuredWidthPx > 0) Constraints(maxWidth = measuredWidthPx) else Constraints()
        val bmp = buildTextBitmap(measurer, density, text, fontSize, fontWeight, monospace, letterSpacing, maxLines, softWrap, overflow, constraints)
        bitmapState.value = bmp
        // ★ pre14-G1：不再手动 recycle 源位图。源位图交 GC 管理，GL 线程经 ensureBitmapTex 的
        //   copy 持有独立副本；手动 recycle 会与 GL 线程 bmp.copy()/texImage2D 竞态
        //   （快速翻页时旧页 dispose recycle 源位图，GL 线程恰在 copy → IllegalStateException → 闪退）。
        // ★ pre15：onDispose 置空——既不 recycle（崩溃），也不置 null（置 null 会让 bitmapState 出现
        //   空窗口，report 看到 null → remove → 重新 upsert → 闪烁）。旧位图由新 effect 覆盖后 GC 回收。
        onDispose { }
    }

    val posHolder = remember { PatchRectHolder() }

    fun report() {
        val bmp = bitmapState.value
        val pos = posHolder.value
        if (!enabled) {
            HdrPatchRegistry.remove(key)
            return
        }
        // ★ pre18c：瞬时 null（位图/坐标尚未就绪）时保留最后上报的贴片，
        //   避免 remove→re-add 触发纹理删除/重传导致贴片闪断（滚动停止瞬间易触发）。
        if (bmp == null || pos == null) return
        HdrPatchRegistry.upsert(
            HdrPatch(
                id = key,
                type = HdrPatchType.TEXT_GLYPH,
                bounds = pos,
                color0 = encodedColor,
                bias = HDR_TEXT_MULT,
                bitmap = bmp,
                topZone = topZone
            )
        )
    }

    Text(
        text = text,
        fontSize = fontSize,
        fontWeight = fontWeight,
        color = color,
        fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
        letterSpacing = letterSpacing,
        maxLines = maxLines,
        overflow = overflow,
        softWrap = softWrap,
        modifier = modifier
            .onGloballyPositioned { coords ->
                // ★ 修复(偏低): localToRoot 对齐内容根像素原点。
                val p = coords.localToRoot(Offset.Zero)
                // ★ R1 修复：仅宽度实际变化才写 State，避免垂直滚动每帧触发位图重建重组。
                val w = coords.size.width
                if (widthHolder.value != w) {
                    widthHolder.value = w
                    measuredWidthPx = w
                }
                // ★ pre18：写普通字段而非 State，垂直滚动时避免每帧重组（贴片"重组"）。
                // ★ R2 修复：RectF 复用（.set() 原地更新），HdrPatch 构造时防御性拷贝，
                //   消除滚动期间每帧 new RectF。
                val r = posHolder.value ?: android.graphics.RectF().also { posHolder.value = it }
                r.set(p.x, p.y, p.x + coords.size.width.toFloat(), p.y + coords.size.height.toFloat())
                report()
            }
    )

    DisposableEffect(key) {
        onDispose { HdrPatchRegistry.remove(key) }
    }
    // 文本变化（位图重建）/ 门控翻转时重新上报或注销，避免残留幽灵标签。
    LaunchedEffect(enabled, bitmapState.value) {
        if (!enabled) HdrPatchRegistry.remove(key) else report()
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
    // ★ R2 修复：整图包围盒 RectF 复用（.set() 原地更新），HdrPatch 构造时防御性拷贝。
    val rect = RectF()
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
    // ★ R2 修复：动态颜色 remember 缓存——避免每次 report()（数据/位置变化）都 encodePq(lineColor) new FloatArray(3)。
    val encodedLineColor = remember(lineColor) { encodePq(lineColor) }

    fun report() {
        if (!enabled || !holder.ready) {
            HdrPatchRegistry.remove(chartKey + ".line")
            HdrPatchRegistry.remove(chartKey + ".grid")
            return
        }
        reportChartPatches(holder, chartKey, data, encodedLineColor, gridLines, showGrid, density)
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
    encodedLineColor: FloatArray,
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
    // ★ R2 修复：复用 ChartPatchHolder.rect（.set() 原地更新），HdrPatch 构造时防御性拷贝。
    holder.rect.set(ox, oy, ox + w, oy + h)
    val bounds = holder.rect

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
                color0 = encodedLineColor,
                bias = HDR_LINE_MULT,
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
                color0 = ENCODED_DIVIDER_CYBER,
                bias = HDR_GRID_MULT,
                points = gpts
            )
        )
    } else {
        HdrPatchRegistry.remove(chartKey + ".grid")
    }
}

// ───────────────────────────────────────────────────────────────────────────
// 线性进度条 HDR 贴片
// ───────────────────────────────────────────────────────────────────────────

private class ProgressPatchHolder {
    var x = 0f
    var y = 0f
    var w = 0f
    var h = 0f
    val ready: Boolean get() = w > 0f && h > 0f
}

/**
 * LinearProgressIndicator 的已填充部分 HDR 贴片上报。挂在 Indicator 的 Modifier 上。
 * 进度变化时通过 LaunchedEffect 重新上报；位置变化时通过 onGloballyPositioned 重新上报。
 * 使用 TAB_INDICATOR 类型（实心圆角矩形），cornerRadius 设为高度一半以匹配 SDR 圆角。
 */
fun Modifier.hdrLinearProgressPatch(
    key: String,
    progress: Float,
    color: Color,
    mult: Float = HDR_CARD_MULT
): Modifier = composed {
    val enabled = CyberNightlightSwitch.enabled
    val holder = remember { ProgressPatchHolder() }
    // ★ R2 修复：动态颜色 remember 缓存——避免每次 report()（进度/位置变化）都 encodePq(color) new FloatArray(3)。
    val encodedColor = remember(color) { encodePq(color) }

    DisposableEffect(key) {
        onDispose { HdrPatchRegistry.remove(key) }
    }

    fun report() {
        if (!enabled || !holder.ready) {
            HdrPatchRegistry.remove(key)
            return
        }
        val p = progress.coerceIn(0f, 1f)
        val fillW = holder.w * p
        // 进度为 0 或极窄时不绘制，避免零宽 bounds 触发 GL 异常
        if (fillW < 1f) {
            HdrPatchRegistry.remove(key)
            return
        }
        val corner = holder.h * 0.5f
        val b = android.graphics.RectF(
            holder.x, holder.y, holder.x + fillW, holder.y + holder.h
        )
        HdrPatchRegistry.upsert(
            HdrPatch(
                id = key,
                type = HdrPatchType.TAB_INDICATOR,
                bounds = b,
                color0 = encodedColor,
                bias = mult,
                cornerRadiusPx = corner,
                strokeWidthPx = corner
            )
        )
    }

    LaunchedEffect(progress, enabled) { report() }

    Modifier.onGloballyPositioned { coords ->
        holder.x = coords.localToRoot(Offset.Zero).x
        holder.y = coords.localToRoot(Offset.Zero).y
        holder.w = coords.size.width.toFloat()
        holder.h = coords.size.height.toFloat()
        report()
    }
}

// ───────────────────────────────────────────────────────────────────────────
// 顶部 Tab 图标 / 卫星天空图图内点 HDR 贴片
// ───────────────────────────────────────────────────────────────────────────

/**
 * 顶部 Tab 选中项矢量图标 HDR 本体。挂在 Icon 的 Modifier 上，仅当 [selected] 时上报，
 * 把图标 Drawable 栅格化为白色掩码位图，由 PQ surface 直接画出 HDR 矢量图标
 *（不再画外圈圆圈光环）。topZone=true 绕过"顶撞裁剪"。
 */
fun Modifier.hdrTabIconPatch(key: String, selected: Boolean, iconRes: Int): Modifier = composed {
    val density = LocalDensity.current
    val context = LocalContext.current
    val enabled = CyberNightlightSwitch.enabled && selected

    // 图标位图：随 iconRes 重建；卸载时回收，避免泄漏。
    val bitmapState = remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    DisposableEffect(key, iconRes) {
        val bmp = buildIconBitmap(context, iconRes, density)
        bitmapState.value = bmp
        // ★ pre14-G1/pre15：不 recycle，交 GC；onDispose 置空（置 null 会引发 remove→upsert 闪烁）。
        onDispose { }
    }

    val posHolder = remember { PatchRectHolder() }

    fun report() {
        val bmp = bitmapState.value
        val pos = posHolder.value
        if (!enabled) {
            HdrPatchRegistry.remove(key)
            return
        }
        // ★ pre18c：瞬时 null（位图/坐标尚未就绪）时保留最后上报的贴片，
        //   避免 remove→re-add 触发纹理删除/重传导致贴片闪断（滚动停止瞬间易触发）。
        if (bmp == null || pos == null) return
        HdrPatchRegistry.upsert(
            HdrPatch(
                id = key,
                type = HdrPatchType.TEXT_GLYPH,
                bounds = pos,
                color0 = encodePq(NeonPurple),
                bias = HDR_TAB_MULT,
                bitmap = bmp,
                topZone = true
            )
        )
    }

    DisposableEffect(key) {
        onDispose { HdrPatchRegistry.remove(key) }
    }
    // 门控翻转（Tab 切换）/ 位图就绪时重新上报或注销，避免残留幽灵图标。
    LaunchedEffect(enabled, bitmapState.value) {
        if (!enabled) HdrPatchRegistry.remove(key) else report()
    }

    this.onGloballyPositioned { coords ->
        // ★ 修复(偏低): localToRoot 对齐内容根像素原点。
        val p = coords.localToRoot(Offset.Zero)
        // ★ pre18：写普通字段而非 State，避免滚动/布局期间触发重组。
        // ★ R2 修复：RectF 复用（.set() 原地更新），HdrPatch 构造时防御性拷贝，
        //   消除滚动期间每帧 new RectF。
        val r = posHolder.value ?: android.graphics.RectF().also { posHolder.value = it }
        r.set(p.x, p.y, p.x + coords.size.width.toFloat(), p.y + coords.size.height.toFloat())
        report()
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
        // ★ R2 修复：循环内复用 scratch RectF（.set() 原地更新），HdrPatch 构造时防御性拷贝，
        //   消除每颗卫星点 new RectF（最多 ~12 点/帧）。
        val scratchRect = android.graphics.RectF()
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
            scratchRect.set(gx - dotR, gy - dotR, gx + dotR, gy + dotR)
            HdrPatchRegistry.upsert(
                HdrPatch(
                    id = id,
                    type = HdrPatchType.CARD_BORDER,
                    bounds = scratchRect,
                    color0 = encodePq(constellationColor(sat.constellationType)),
                    bias = HDR_DOT_MULT,
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

/**
 * 自绘 ImageVector 图标（如 CyberIcons.Home）HDR 本体。与 hdrTabIconPatch 同机制，
 * 但源是 ImageVector 而非 drawable 资源：用 Compose toPixelMap 把矢量栅格化为白色掩码位图，
 * 由 PQ surface 直接画出 HDR 矢量图标。topZone=false（内容区图标）。
 */
fun Modifier.hdrVectorIconPatch(
    key: String,
    imageVector: ImageVector,
    sizeDp: Dp = 22.dp,
    selected: Boolean = true
): Modifier = composed {
    val density = LocalDensity.current
    val enabled = CyberNightlightSwitch.enabled && selected

    // 图标位图：随 imageVector 重建；卸载时回收，避免泄漏。
    val bitmapState = remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    DisposableEffect(key, imageVector) {
        val bmp = buildVectorIconBitmap(imageVector, density, sizeDp)
        bitmapState.value = bmp
        // ★ pre14-G1/pre15：不 recycle，交 GC；onDispose 置空（置 null 会引发 remove→upsert 闪烁）。
        onDispose { }
    }

    val posHolder = remember { PatchRectHolder() }

    fun report() {
        val bmp = bitmapState.value
        val pos = posHolder.value
        if (!enabled) {
            HdrPatchRegistry.remove(key)
            return
        }
        // ★ pre18c：瞬时 null（位图/坐标尚未就绪）时保留最后上报的贴片，
        //   避免 remove→re-add 触发纹理删除/重传导致贴片闪断（滚动停止瞬间易触发）。
        if (bmp == null || pos == null) return
        HdrPatchRegistry.upsert(
            HdrPatch(
                id = key,
                type = HdrPatchType.TEXT_GLYPH,
                bounds = pos,
                color0 = encodePq(NeonPurpleBright),
                bias = HDR_TAB_MULT,
                bitmap = bmp,
                topZone = false
            )
        )
    }

    DisposableEffect(key) {
        onDispose { HdrPatchRegistry.remove(key) }
    }
    LaunchedEffect(enabled, bitmapState.value) {
        if (!enabled) HdrPatchRegistry.remove(key) else report()
    }

    this.onGloballyPositioned { coords ->
        // ★ 修复(偏低): localToRoot 对齐内容根像素原点。
        val p = coords.localToRoot(Offset.Zero)
        // ★ pre18：写普通字段而非 State，避免滚动/布局期间触发重组。
        // ★ R2 修复：RectF 复用（.set() 原地更新），HdrPatch 构造时防御性拷贝，
        //   消除滚动期间每帧 new RectF。
        val r = posHolder.value ?: android.graphics.RectF().also { posHolder.value = it }
        r.set(p.x, p.y, p.x + coords.size.width.toFloat(), p.y + coords.size.height.toFloat())
        report()
    }
}

// ───────────────────────────────────────────────────────────────────────────
// 位图掩码栅格化（文字 / 图标本体）
// ───────────────────────────────────────────────────────────────────────────

/**
 * 用 TextMeasurer 取得与 Compose 完全一致的布局，再把字形栅格化到与包围盒等宽高的位图
 *（2× 超采样，纹理→quad 1:1 下采样，清晰且无宽高变形）。白色字形=不透明掩码，颜色由 shader 注入。
 * 关键：沿用 Compose 同一基线(getLineBaseline)与左缘(getLineLeft)，保证 HDR 字形与 SDR 文本像素级对齐。
 */
private fun buildTextBitmap(
    measurer: TextMeasurer,
    density: Density,
    text: String,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    monospace: Boolean,
    letterSpacing: TextUnit,
    maxLines: Int = Int.MAX_VALUE,
    softWrap: Boolean = true,
    overflow: TextOverflow = TextOverflow.Clip,
    constraints: Constraints = Constraints()
): Bitmap {
    val style = TextStyle(
        fontSize = fontSize,
        fontWeight = fontWeight,
        fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
        letterSpacing = letterSpacing
    )
    val layout = measurer.measure(
        text, style,
        softWrap = softWrap, overflow = overflow, maxLines = maxLines,
        constraints = constraints
    )
    val ss = 2
    val w = (layout.size.width * ss).coerceAtLeast(1)
    val h = (layout.size.height * ss).coerceAtLeast(1)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    c.save()
    c.scale(ss.toFloat(), ss.toFloat())
    val tp = with(density) { fontSize.toPx() }
    val paint = Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = tp
        isFakeBoldText = fontWeight == FontWeight.Bold
        typeface = if (monospace) Typeface.MONOSPACE else Typeface.DEFAULT
        // Paint.letterSpacing 单位为 em（相对 textSize 的比例），故直接用 sp 比值，勿再乘 px
        this.letterSpacing = if (fontSize.value > 0f) letterSpacing.value / fontSize.value else 0f
    }
    // 与 Compose 同款基线/左缘，保证像素级对齐。
    // 关键修复：多行文本须逐行绘制，否则 Canvas.drawText 会把整段文字画成单行，
    // 与 SDR Text 的自动换行布局错位/重影。
    for (line in 0 until layout.lineCount) {
        val start = layout.getLineStart(line)
        val end = layout.getLineEnd(line)
        if (start >= end) continue
        val lineText = text.substring(start, end).trimEnd('\n')
        if (lineText.isEmpty()) continue
        c.drawText(lineText, layout.getLineLeft(line), layout.getLineBaseline(line), paint)
    }
    c.restore()
    return bmp
}

/**
 * 把图标 Drawable 栅格化为白色掩码位图（2× 超采样）；白色=不透明，颜色由 shader 注入为选中色。
 * 这样 HDR 直接画出矢量图标本体，而非外圈圆圈光环。
 */
private fun buildIconBitmap(
    context: android.content.Context,
    iconRes: Int,
    density: Density
): Bitmap? {
    val d = ContextCompat.getDrawable(context, iconRes) ?: return null
    val base = with(density) { 16.dp.toPx() }.toInt().coerceAtLeast(1)
    val ss = 2
    val px = base * ss
    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    d.setBounds(0, 0, px, px)
    try { d.setTint(0xFFFFFFFF.toInt()) } catch (_: Throwable) { /* 旧 API 忽略 */ }
    d.draw(c)
    return bmp
}

/**
 * 把自绘 ImageVector（如 CyberIcons.*）栅格化为白色掩码位图（2× 超采样）。
 * 经 CyberIcons.pathsFor 取得路径源数据（SVG path 字符串），用 AndroidX PathParser
 * 转 android.graphics.Path，再用 Canvas 绘制为白色掩码（颜色由 shader 注入）。
 * 不依赖任何 Compose 私有/内部 API（toPixelMap / VectorGroup.children 均不可用）。
 * 视口 24×24 → 位图 px 整体缩放；描边/填充按 PathSpec 类型判定。
 */
private fun buildVectorIconBitmap(
    imageVector: ImageVector,
    density: Density,
    sizeDp: Dp
): Bitmap? {
    val specs = CyberIcons.pathsFor(imageVector) ?: return null
    val base = with(density) { sizeDp.toPx() }.toInt().coerceAtLeast(1)
    val ss = 2
    val px = base * ss
    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    c.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
    val scale = px / 24f   // CyberIcons 视口恒为 24×24
    c.save()
    c.scale(scale, scale)
    val paint = Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
    }
    return try {
        for (spec in specs) {
            val path = PathParser.createPathFromPathData(spec.data)
            if (spec.fill) {
                paint.style = android.graphics.Paint.Style.FILL
                c.drawPath(path, paint)
            } else {
                paint.style = android.graphics.Paint.Style.STROKE
                paint.strokeWidth = spec.width
                c.drawPath(path, paint)
            }
        }
        bmp
    } catch (_: Throwable) {
        null
    } finally {
        c.restore()
    }
}
