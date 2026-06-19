package com.example.deviceinfoviewer.ui.components.charts

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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.deviceinfoviewer.ui.theme.ChartAreaPurple
import com.example.deviceinfoviewer.ui.theme.DividerCyber
import com.example.deviceinfoviewer.ui.theme.ChartLinePurple
import com.example.deviceinfoviewer.ui.theme.NeonCyan
import com.example.deviceinfoviewer.ui.theme.NeonPurpleBright
import com.example.deviceinfoviewer.ui.theme.NeonPurple

/**
 * 平滑动画折线图 — 贝塞尔曲线 + 面积填充 + 可选渐变线条
 *
 * 性能优化 (2026-06-19):
 * - 移除每个数据点独立的 animateFloat（原方案 80 点 = 80 个 State，每帧重组，严重掉帧）
 * - 改为单个 Animatable 控制"入场揭开"进度（0→1），仅首次组合触发一次
 * - 数据更新时图表直接刷新（监控图表 2s 一帧，逐点过渡动画反而卡顿且无体感价值）
 * - 视觉一致：保留贝塞尔平滑 + 面积渐变 + 渐变描边 + 尾点指示
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

    // ★ 单进度入场动画：首次组合从 0→1，数据更新时不重置（避免每帧 O(n) 重组）
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        reveal.animateTo(1f, tween(400))
    }
    val revealProgress = reveal.value

    // 渐变 Brush 定义在 Composition 层（而非 Canvas lambda 内），避免每帧重建
    val gradientBrush = remember(useGradient, lineColor) {
        if (useGradient) Brush.horizontalGradient(listOf(NeonCyan, NeonPurple, NeonPurpleBright))
        else Brush.horizontalGradient(listOf(lineColor, lineColor)) // 2色相同 = 实心
    }

    Canvas(modifier = modifier.fillMaxWidth().height(120.dp)) {
        val w = size.width; val h = size.height; val pad = 8.dp.toPx()
        val cw = w - pad * 2; val ch = h - pad * 2

        if (showGrid) {
            repeat(gridLines + 1) { i ->
                val y = pad + (ch / gridLines) * i
                drawLine(DividerCyber, Offset(pad, y), Offset(w - pad, y), 1f)
            }
        }

        // 单点兜底
        if (data.size == 1) {
            val v = data[0].coerceIn(0f, 1f)
            drawCircle(lineColor, 4f, Offset(pad + cw / 2f, pad + ch - v * ch))
            return@Canvas
        }

        // 计算所有点坐标（静态，无逐点动画）
        val points = data.mapIndexed { i, v ->
            Offset(
                pad + cw / (data.size - 1).coerceAtLeast(1) * i,
                pad + ch - (v.coerceIn(0f, 1f) * ch)
            )
        }

        // 揭开进度：只绘制前 revealProgress 比例的点（入场动画）
        val visibleCount = (points.size * revealProgress).toInt().coerceIn(2, points.size)
        val visiblePoints = points.take(visibleCount)

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
 */
@Composable
fun DualLineChart(
    data1: List<Float>, data2: List<Float>,
    modifier: Modifier = Modifier,
    lineColor1: Color = ChartLinePurple,
    lineColor2: Color = Color(0xFFFF00E5),
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
    val revealProgress = reveal.value

    val brush1 = remember(useGradient1, lineColor1) {
        if (useGradient1) Brush.horizontalGradient(listOf(NeonCyan, lineColor1))
        else Brush.horizontalGradient(listOf(lineColor1, lineColor1)) // 2色相同 = 实心
    }
    val brush2 = remember(useGradient2, lineColor2) {
        if (useGradient2) Brush.horizontalGradient(listOf(lineColor2, NeonPurpleBright))
        else Brush.horizontalGradient(listOf(lineColor2, lineColor2)) // 2色相同 = 实心
    }

    Canvas(modifier = modifier.fillMaxWidth().height(120.dp)) {
        val w = size.width; val h = size.height; val pad = 8.dp.toPx()
        val cw = w - pad * 2; val ch = h - pad * 2

        if (showGrid) {
            repeat(gridLines + 1) { i ->
                val y = pad + (ch / gridLines) * i
                drawLine(DividerCyber, Offset(pad, y), Offset(w - pad, y), 1f)
            }
        }

        // 揭开进度（基于较长序列）
        val maxLen = maxOf(data1.size, data2.size)
        val visibleCount = (maxLen * revealProgress).toInt().coerceIn(2, maxLen)

        listOf(Triple(data1, brush1, lineColor1), Triple(data2, brush2, lineColor2)).forEach { (data, brush, color) ->
            if (data.size == 1) {
                val v = data[0].coerceIn(0f, 1f)
                drawCircle(color, 4f, Offset(pad + cw / 2f, pad + ch - v * ch))
                return@forEach
            }
            val points = data.mapIndexed { i, v ->
                Offset(pad + cw / (data.size - 1).coerceAtLeast(1) * i,
                    pad + ch - (v.coerceIn(0f, 1f) * ch))
            }
            val vis = points.take(visibleCount.coerceAtMost(points.size))
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
