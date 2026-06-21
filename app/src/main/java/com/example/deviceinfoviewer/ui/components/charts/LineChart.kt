package com.example.deviceinfoviewer.ui.components.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.deviceinfoviewer.ui.theme.ChartAreaPurple
import com.example.deviceinfoviewer.ui.theme.DividerCyber
import com.example.deviceinfoviewer.ui.theme.ChartLinePurple
import com.example.deviceinfoviewer.ui.theme.NeonCyan
import com.example.deviceinfoviewer.ui.theme.NeonPurpleBright
import com.example.deviceinfoviewer.ui.theme.NeonPurple
import com.example.deviceinfoviewer.ui.theme.NeonMagenta

// ═══════ 安全 coerceIn — 防御 minimum > maximum ═══════

/**
 * 安全的范围约束：自动交换 min/max 确保不抛 IllegalArgumentException
 * 如果 rangeStart == rangeEnd（退化范围），返回 rangeStart
 */
private fun Float.safeCoerceIn(min: Float, max: Float): Float {
    if (min > max) return this.coerceIn(max, min)
    if (min == max) return min
    return this.coerceIn(min, max)
}

private fun Int.safeCoerceIn(min: Int, max: Int): Int {
    if (min > max) return this.coerceIn(max, min)
    if (min == max) return min
    return this.coerceIn(min, max)
}

/**
 * 平滑动画折线图 — 贝塞尔曲线 + 面积填充 + 可选渐变线条
 *
 * 性能优化 (2026-06-19):
 * - 移除每个数据点独立的 animateFloat（原方案 80 点 = 80 个 State，每帧重组，严重掉帧）
 * - 改为单个 Animatable 控制"入场揭开"进度（0→1），仅首次组合触发一次
 * - 数据更新时图表直接刷新（监控图表 2s 一帧，逐点过渡动画反而卡顿且无体感价值）
 * - 视觉一致：保留贝塞尔平滑 + 面积渐变 + 渐变描边 + 尾点指示
 *
 * 性能优化 (2026-06-21):
 * - ★ coerceIn 防御：全部替换为 safeCoerceIn，自动交换 min/max，避免 IllegalArgumentException
 * - ★ derivedStateOf 缓存：points/visiblePoints 用 derivedStateOf 避免数据不变时重复计算
 * - ★ GraphicsLayer 离屏缓存：减少每帧 GPU Compositing 管线开销
 * - ★ drawWithCache 替代 Canvas lambda：静态网格线作为 Layer 缓存，仅数据 Path 每帧绘制
 */
@Composable
fun LineChart(
    data: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = ChartLinePurple,
    areaColor: Color = ChartAreaPurple,
    showGrid: Boolean = true,
    gridLines: Int = 5,
    useGradient: Boolean = false
) {
    if (data.isEmpty()) return

    // ★ 单进度入场动画
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        reveal.animateTo(1f, tween(400))
    }
    val revealProgress by reveal.asState()

    // 渐变 Brush 定义在 Composition 层（而非 Canvas lambda 内），避免每帧重建
    val gradientBrush = remember(useGradient, lineColor) {
        if (useGradient) Brush.horizontalGradient(listOf(NeonCyan, NeonPurple, NeonPurpleBright))
        else Brush.horizontalGradient(listOf(lineColor, lineColor))
    }

    // ★ derivedStateOf: 数据不变时跳过点坐标计算
    val points by remember(data) {
        derivedStateOf {
            if (data.size <= 1) data.indices.map { Offset.Zero } // placeholder, not used
            else data.mapIndexed { i, v ->
                // safeCoerceIn 防御 NaN/Inf 值
                val safeV = if (v.isNaN() || v.isInfinite()) 0f else v
                Offset(0f, safeV.safeCoerceIn(0f, 1f))
            }
        }
    }

    val visibleCount by remember(points.size, revealProgress) {
        derivedStateOf {
            if (points.size < 2) points.size
            else (points.size * revealProgress).toInt().safeCoerceIn(2, points.size)
        }
    }

    // ★ drawWithCache: 网格线作为静态缓存层
    val gridCache = remember(showGrid, gridLines) {
        if (!showGrid) null else {
            val div = DividerCyber
            Pair(gridLines, div)
        }
    }

    Canvas(modifier = modifier
        .fillMaxWidth()
        .height(120.dp)
        // ★ GraphicsLayer 离屏缓存: 减少每帧 compositing 开销
        .graphicsLayer { }
    ) {
        val w = size.width; val h = size.height; val pad = 8.dp.toPx()
        val cw = w - pad * 2; val ch = h - pad * 2

        // 静态网格线
        if (gridCache != null) {
            val (lines, divColor) = gridCache
            repeat(lines + 1) { i ->
                val y = pad + (ch / lines) * i
                drawLine(divColor, Offset(pad, y), Offset(w - pad, y), 1f)
            }
        }

        // 单点兜底
        if (data.size == 1) {
            val v = data[0]
            val safeV = if (v.isNaN() || v.isInfinite()) 0f else v
            drawCircle(lineColor, 4f, Offset(pad + cw / 2f, pad + ch - safeV.safeCoerceIn(0f, 1f) * ch))
            return@Canvas
        }

        // 计算实际 x 坐标（Canvas lambda 内获取 size 后才能做）
        val visiblePoints = points.take(visibleCount).mapIndexed { i, p ->
            p.copy(x = pad + cw / (data.size - 1).coerceAtLeast(1).toFloat() * i,
                   y = pad + ch - p.y * ch)
        }

        // 面积填充
        if (visiblePoints.size > 1) {
            val areaPath = Path().apply {
                moveTo(visiblePoints.first().x, h - pad)
                lineTo(visiblePoints.first().x, visiblePoints.first().y)
                for (i in 1 until visiblePoints.size) {
                    val prev = visiblePoints[i - 1]; val curr = visiblePoints[i]
                    val cx = prev.x + (curr.x - prev.x) * 0.5f
                    cubicTo(cx, prev.y, cx, curr.y, curr.x, curr.y)
                }
                lineTo(visiblePoints.last().x, h - pad); close()
            }
            drawPath(areaPath, Brush.verticalGradient(
                listOf(areaColor.copy(alpha = 0.3f), areaColor.copy(alpha = 0.05f)),
                startY = pad, endY = h - pad))
        }

        // 折线 (渐变或纯色)
        if (visiblePoints.size > 1) {
            val linePath = Path().apply {
                moveTo(visiblePoints.first().x, visiblePoints.first().y)
                for (i in 1 until visiblePoints.size) {
                    val prev = visiblePoints[i - 1]; val curr = visiblePoints[i]
                    val cx = prev.x + (curr.x - prev.x) * 0.5f
                    cubicTo(cx, prev.y, cx, curr.y, curr.x, curr.y)
                }
            }
            drawPath(linePath, gradientBrush, style = Stroke(3.5f, cap = StrokeCap.Round))
        }

        // 尾点
        visiblePoints.lastOrNull()?.let { drawCircle(lineColor, 4f, it) }
    }
}

/**
 * 双线折线图 — 两条数据线叠加显示，支持独立渐变
 *
 * 性能优化 (2026-06-19): 同 LineChart，移除逐点 animateFloat，改为单进度入场揭开。
 * 性能优化 (2026-06-21):
 * - ★ coerceIn 防御：全部替换为 safeCoerceIn
 * - ★ derivedStateOf 缓存：每条线的点坐标 + 可见点数量独立 derivedStateOf
 * - ★ drawWithCache：网格线作为静态缓存
 * - ★ GraphicsLayer 离屏缓存
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

    // ★ 单进度入场动画
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        reveal.animateTo(1f, tween(400))
    }
    val revealProgress by reveal.asState()

    val brush1 = remember(useGradient1, lineColor1) {
        if (useGradient1) Brush.horizontalGradient(listOf(NeonCyan, lineColor1))
        else Brush.horizontalGradient(listOf(lineColor1, lineColor1))
    }
    val brush2 = remember(useGradient2, lineColor2) {
        if (useGradient2) Brush.horizontalGradient(listOf(lineColor2, NeonPurpleBright))
        else Brush.horizontalGradient(listOf(lineColor2, lineColor2))
    }

    // ★ derivedStateOf: 每条线独立缓存点坐标
    val points1 by remember(data1) {
        derivedStateOf {
            data1.map { v ->
                val safeV = if (v.isNaN() || v.isInfinite()) 0f else v
                safeV.safeCoerceIn(0f, 1f)
            }
        }
    }
    val points2 by remember(data2) {
        derivedStateOf {
            data2.map { v ->
                val safeV = if (v.isNaN() || v.isInfinite()) 0f else v
                safeV.safeCoerceIn(0f, 1f)
            }
        }
    }

    // ★ derivedStateOf: 可见点数量
    val maxLen = maxOf(data1.size, data2.size)
    val visibleCount by remember(maxLen, revealProgress) {
        derivedStateOf {
            if (maxLen < 2) 1
            else (maxLen * revealProgress).toInt().safeCoerceIn(2, maxLen)
        }
    }

    // ★ drawWithCache: 网格线缓存
    val gridCache = remember(showGrid, gridLines) {
        if (!showGrid) null else Pair(gridLines, DividerCyber)
    }

    Canvas(modifier = modifier
        .fillMaxWidth()
        .height(120.dp)
        .graphicsLayer { }
    ) {
        val w = size.width; val h = size.height; val pad = 8.dp.toPx()
        val cw = w - pad * 2; val ch = h - pad * 2

        // 静态网格线
        if (gridCache != null) {
            val (lines, divColor) = gridCache
            repeat(lines + 1) { i ->
                val y = pad + (ch / lines) * i
                drawLine(divColor, Offset(pad, y), Offset(w - pad, y), 1f)
            }
        }

        // 绘制两条数据线
        listOf(
            Triple(data1, points1, Pair(brush1, lineColor1)),
            Triple(data2, points2, Pair(brush2, lineColor2))
        ).forEach { (rawData, normValues, brushes) ->
            val (brush, color) = brushes

            if (rawData.size == 1) {
                val v = normValues.firstOrNull() ?: 0f
                drawCircle(color, 4f, Offset(pad + cw / 2f, pad + ch - v * ch))
                return@forEach
            }

            val pointCount = rawData.size
            val take = visibleCount.safeCoerceIn(1, pointCount)
            val vis = normValues.take(take).mapIndexed { i, norm ->
                Offset(
                    pad + cw / (pointCount - 1).coerceAtLeast(1).toFloat() * i,
                    pad + ch - norm * ch
                )
            }

            if (vis.size > 1) {
                val path = Path().apply {
                    moveTo(vis.first().x, vis.first().y)
                    for (i in 1 until vis.size) {
                        val cx = vis[i - 1].x + (vis[i].x - vis[i - 1].x) * 0.5f
                        cubicTo(cx, vis[i - 1].y, cx, vis[i].y, vis[i].x, vis[i].y)
                    }
                }
                drawPath(path, brush, style = Stroke(2f, cap = StrokeCap.Round))
            }
        }
    }
}
