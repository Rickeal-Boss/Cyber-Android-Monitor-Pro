package com.rb.cybermonitorpro.ui.components.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.rb.cybermonitorpro.ui.theme.ChartAreaPurple
import com.rb.cybermonitorpro.ui.theme.DividerCyber
import com.rb.cybermonitorpro.ui.theme.ChartLinePurple
import com.rb.cybermonitorpro.ui.theme.NeonCyan
import com.rb.cybermonitorpro.ui.theme.NeonPurpleBright
import com.rb.cybermonitorpro.ui.theme.NeonPurple
import com.rb.cybermonitorpro.ui.theme.NeonMagenta

// ═══════ 安全 coerceIn — 防御 minimum > maximum ═══════

/**
 * 安全的范围约束：自动交换 min/max 确保不抛 IllegalArgumentException。
 * inline 避免热路径上的函数调用开销。
 */
internal inline fun Float.safeCoerceIn(min: Float, max: Float): Float {
    if (min > max) return this.coerceIn(max, min)
    if (min == max) return min
    return this.coerceIn(min, max)
}

private inline fun Int.safeCoerceIn(min: Int, max: Int): Int {
    if (min > max) return this.coerceIn(max, min)
    if (min == max) return min
    return this.coerceIn(min, max)
}

// ═══════ 辅助: 坐标数组填充 ═══════
private fun FillX(values: FloatArray, pad: Float, step: Float, count: Int) {
    for (i in 0 until count) values[i] = pad + step * i
}
private fun sanitize(v: Float): Float = if (v.isNaN() || v.isInfinite()) 0f else v

/**
 * 平滑动画折线图 — 贝塞尔曲线 + 面积填充 + 可选渐变线条
 *
 * 性能优化 (2026-06-21 第三轮 — 零分配绘制):
 * - ★ 移除无效 derivedStateOf: remember(data) 已按引用缓存
 * - ★ 移除 Offset 中间对象: 改用 FloatArray 预分配，Canvas 内直接填充
 * - ★ Path 对象复用: remember + reset()，动画期间零 GC
 * - ★ areaBrush 提到 Composition 层: 不再每帧新建 Brush
 * - ★ safeCoerceIn inline: 消除热路径函数调用开销
 */
@Composable
fun LineChart(
    data: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = ChartLinePurple,
    areaColor: Color = ChartAreaPurple,
    showGrid: Boolean = true,
    gridLines: Int = 5,
    useGradient: Boolean = false,
) {
    if (data.isEmpty()) return

    // 入场动画
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(400)) }
    val revealProgress by reveal.asState()

    val visibleCount = remember(data.size, revealProgress) {
        if (data.size < 2) data.size
        else (data.size * revealProgress).toInt().safeCoerceIn(2, data.size)
    }

    // 渐变 Brush — Composition 层缓存
    val gradientBrush = remember(useGradient, lineColor) {
        if (useGradient) Brush.horizontalGradient(listOf(NeonCyan, NeonPurple, NeonPurpleBright))
        else Brush.horizontalGradient(listOf(lineColor, lineColor))
    }
    // ★ 面积渐变 Brush — 同样提到 Composition 层
    val areaBrush = remember(areaColor) {
        Brush.verticalGradient(listOf(areaColor.copy(alpha = 0.16f), areaColor.copy(alpha = 0f)))
    }

    // ★ Path 对象复用 — 动画期间零分配
    val areaPath = remember { Path() }
    val linePath = remember { Path() }

    // ★ FloatArray 预分配 — 替代 List<Offset>
    val xs = remember(data.size) { FloatArray(data.size) }
    val ys = remember(data.size) { FloatArray(data.size) }

    // 网格线
    val gridCache = remember(showGrid, gridLines) {
        if (!showGrid) null else Pair(gridLines, DividerCyber)
    }

    val chartModifier = modifier
        .fillMaxWidth().height(120.dp)
        .graphicsLayer { }

    Canvas(modifier = chartModifier) {
        val w = size.width; val h = size.height; val pad = 8.dp.toPx()
        val cw = w - pad * 2; val ch = h - pad * 2

        // 网格线
        if (gridCache != null) {
            val (lines, divColor) = gridCache
            repeat(lines + 1) { i ->
                val y = pad + (ch / lines) * i
                drawLine(divColor, Offset(pad, y), Offset(w - pad, y), 1f)
            }
        }

        // 单点兜底
        if (data.size == 1) {
            val safeV = sanitize(data[0])
            drawCircle(lineColor, 4f, Offset(pad + cw / 2f, pad + ch - safeV.safeCoerceIn(0f, 1f) * ch))
            return@Canvas
        }

        // ★ 直接在 FloatArray 中计算坐标，零对象分配
        val xStep = cw / (data.size - 1).coerceAtLeast(1).toFloat()
        for (i in 0 until visibleCount) {
            xs[i] = pad + xStep * i
            ys[i] = pad + ch - sanitize(data[i]).safeCoerceIn(0f, 1f) * ch
        }

        // 面积填充
        if (visibleCount > 1) {
            areaPath.reset()
            areaPath.moveTo(xs[0], h - pad)
            areaPath.lineTo(xs[0], ys[0])
            for (i in 1 until visibleCount) {
                val cx = xs[i - 1] + (xs[i] - xs[i - 1]) * 0.5f
                areaPath.cubicTo(cx, ys[i - 1], cx, ys[i], xs[i], ys[i])
            }
            areaPath.lineTo(xs[visibleCount - 1], h - pad)
            areaPath.close()
            drawPath(areaPath, areaBrush)
        }

        // 折线
        if (visibleCount > 1) {
            linePath.reset()
            linePath.moveTo(xs[0], ys[0])
            for (i in 1 until visibleCount) {
                val cx = xs[i - 1] + (xs[i] - xs[i - 1]) * 0.5f
                linePath.cubicTo(cx, ys[i - 1], cx, ys[i], xs[i], ys[i])
            }
            drawPath(linePath, gradientBrush, style = Stroke(3.5f, cap = StrokeCap.Round))
        }

        // 尾点
        drawCircle(lineColor, 4f, Offset(xs[visibleCount - 1], ys[visibleCount - 1]))
    }
}

// ═══════ DualLineChart = 双线折线图 ═══════

/**
 * 双线折线图 — 两条数据线叠加显示，支持独立渐变
 */
@Composable
fun DualLineChart(
    data1: List<Float>, data2: List<Float>,
    modifier: Modifier = Modifier,
    lineColor1: Color = ChartLinePurple,
    lineColor2: Color = NeonMagenta,
    showGrid: Boolean = true,
    gridLines: Int = 5,
    useGradient1: Boolean = false,
    useGradient2: Boolean = false,
) {
    if (data1.isEmpty() || data2.isEmpty()) return

    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(400)) }
    val revealProgress by reveal.asState()

    val maxLen = maxOf(data1.size, data2.size)
    val visibleCount = remember(maxLen, revealProgress) {
        if (maxLen < 2) 1
        else (maxLen * revealProgress).toInt().safeCoerceIn(2, maxLen)
    }

    val brush1 = remember(useGradient1, lineColor1) {
        if (useGradient1) Brush.horizontalGradient(listOf(NeonCyan, lineColor1))
        else Brush.horizontalGradient(listOf(lineColor1, lineColor1))
    }
    val brush2 = remember(useGradient2, lineColor2) {
        if (useGradient2) Brush.horizontalGradient(listOf(lineColor2, NeonPurpleBright))
        else Brush.horizontalGradient(listOf(lineColor2, lineColor2))
    }

    // ★ Path 复用
    val path = remember { Path() }

    // ★ FloatArray 预分配 (两条线各一套)
    val xs = remember(maxLen) { FloatArray(maxLen) }
    val ys = remember(maxLen) { FloatArray(maxLen) }

    val gridCache = remember(showGrid, gridLines) {
        if (!showGrid) null else Pair(gridLines, DividerCyber)
    }

    Canvas(modifier = modifier
        .fillMaxWidth().height(120.dp)
        .graphicsLayer { }
    ) {
        val w = size.width; val h = size.height; val pad = 8.dp.toPx()
        val cw = w - pad * 2; val ch = h - pad * 2

        if (gridCache != null) {
            val (lines, divColor) = gridCache
            repeat(lines + 1) { i ->
                val y = pad + (ch / lines) * i
                drawLine(divColor, Offset(pad, y), Offset(w - pad, y), 1f)
            }
        }

        // ★ 直接两次调用，避免 listOf+Triple+Pair 对象分配
        // xStep is computed by the caller to align dual-line X coordinates
        // 原代码 drawOneLine 内部各自计算 xStep = cw/(pointCount-1)，
        // 两条线数据量不同时 X 分布不同，导致同一时间点坐标错位。
        val xStep = cw / (maxLen - 1).coerceAtLeast(1).toFloat()
        drawOneLine(data1, visibleCount, brush1, lineColor1, xs, ys, pad, cw, ch, path, xStep)
        drawOneLine(data2, visibleCount, brush2, lineColor2, xs, ys, pad, cw, ch, path, xStep)
    }
}

/** 绘制单条折线 — FloatArray 零分配 */
private fun DrawScope.drawOneLine(
    rawData: List<Float>,
    visibleCount: Int,
    brush: Brush,
    color: Color,
    xs: FloatArray,
    ys: FloatArray,
    pad: Float, cw: Float, ch: Float,
    path: Path,
    xStep: Float,  // unified xStep for dual-line alignment
) {
    if (rawData.size == 1) {
        val safeV = sanitize(rawData[0])
        drawCircle(color, 4f, Offset(pad + cw / 2f, pad + ch - safeV.safeCoerceIn(0f, 1f) * ch))
        return
    }

    val pointCount = rawData.size
    val take = visibleCount.safeCoerceIn(1, pointCount)

    for (i in 0 until take) {
        xs[i] = pad + xStep * i
        ys[i] = pad + ch - sanitize(rawData[i]).safeCoerceIn(0f, 1f) * ch
    }

    if (take > 1) {
        path.reset()
        path.moveTo(xs[0], ys[0])
        for (i in 1 until take) {
            val cx = xs[i - 1] + (xs[i] - xs[i - 1]) * 0.5f
            path.cubicTo(cx, ys[i - 1], cx, ys[i], xs[i], ys[i])
        }
        drawPath(path, brush, style = Stroke(2f, cap = StrokeCap.Round))
    }
}
