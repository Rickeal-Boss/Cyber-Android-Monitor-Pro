package com.example.deviceinfoviewer.ui.components.charts

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import com.example.deviceinfoviewer.ui.theme.ChartAreaPurple
import com.example.deviceinfoviewer.ui.theme.DividerCyber
import com.example.deviceinfoviewer.ui.theme.ChartLinePurple
import com.example.deviceinfoviewer.ui.theme.NeonCyan
import com.example.deviceinfoviewer.ui.theme.NeonPurpleBright
import com.example.deviceinfoviewer.ui.theme.NeonPurple

/**
 * 平滑动画折线图 — 贝塞尔曲线 + 面积填充 + 可选渐变线条
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

    val transition = updateTransition(targetState = data, label = "chart")
    val smoothed = remember(data) {
        data.mapIndexed { i, _ ->
            val target = data.getOrElse(i) { 0f }
            val anim by transition.animateFloat(label = "val$i", transitionSpec = { tween(600) }) { target }
            anim
        }
    }

    // 渐变 Brush 定义在 Composition 层（而非 Canvas lambda 内）
    val gradientBrush = remember(useGradient) {
        if (useGradient) Brush.horizontalGradient(listOf(NeonCyan, NeonPurple, NeonPurpleBright))
        else Brush.linearGradient(listOf(lineColor), listOf(lineColor))
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

        val points = smoothed.mapIndexed { i, v ->
            Offset(pad + cw / (smoothed.size - 1).coerceAtLeast(1) * i,
                pad + ch - (v.coerceIn(0f, 1f) * ch))
        }

        // 面积填充
        if (points.size > 1) {
            val areaPath = Path().apply {
                moveTo(points.first().x, h - pad)
                lineTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    val prev = points[i - 1]; val curr = points[i]
                    val cx = prev.x + (curr.x - prev.x) * 0.5f
                    cubicTo(cx, prev.y, cx, curr.y, curr.x, curr.y)
                }
                lineTo(points.last().x, h - pad); close()
            }
            drawPath(areaPath, Brush.verticalGradient(
                listOf(areaColor.copy(alpha = 0.3f), areaColor.copy(alpha = 0.05f)),
                startY = pad, endY = h - pad))
        }

        // 折线 (渐变或纯色)
        if (points.size > 1) {
            val linePath = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    val prev = points[i - 1]; val curr = points[i]
                    val cx = prev.x + (curr.x - prev.x) * 0.5f
                    cubicTo(cx, prev.y, cx, curr.y, curr.x, curr.y)
                }
            }
            drawPath(linePath, gradientBrush, style = Stroke(3.5f, cap = StrokeCap.Round))
        }

        // 尾点
        points.lastOrNull()?.let { drawCircle(lineColor, 4f, it) }
    }
}

/**
 * 双线折线图 — 两条数据线叠加显示，支持独立渐变
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

    val t1 = updateTransition(data1, "d1")
    val t2 = updateTransition(data2, "d2")

    val s1 = remember(data1) { data1.mapIndexed { i, _ ->
        val target = data1.getOrElse(i) { 0f }
        val anim by t1.animateFloat(label = "d1_$i", transitionSpec = { tween(600) }) { target }
        anim
    } }
    val s2 = remember(data2) { data2.mapIndexed { i, _ ->
        val target = data2.getOrElse(i) { 0f }
        val anim by t2.animateFloat(label = "d2_$i", transitionSpec = { tween(600) }) { target }
        anim
    } }

    val brush1 = remember(useGradient1) {
        if (useGradient1) Brush.horizontalGradient(listOf(NeonCyan, lineColor1))
        else Brush.linearGradient(listOf(lineColor1), listOf(lineColor1))
    }
    val brush2 = remember(useGradient2) {
        if (useGradient2) Brush.horizontalGradient(listOf(lineColor2, NeonPurpleBright))
        else Brush.linearGradient(listOf(lineColor2), listOf(lineColor2))
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

        listOf(Triple(s1, brush1, lineColor1), Triple(s2, brush2, lineColor2)).forEach { (data, brush, color) ->
            val points = data.mapIndexed { i, v ->
                Offset(pad + cw / (data.size - 1).coerceAtLeast(1) * i,
                    pad + ch - (v.coerceIn(0f, 1f) * ch))
            }
            if (points.size > 1) {
                val path = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    for (i in 1 until points.size) {
                        val cx = points[i - 1].x + (points[i].x - points[i - 1].x) * 0.5f
                        cubicTo(cx, points[i - 1].y, cx, points[i].y, points[i].x, points[i].y)
                    }
                }
                drawPath(path, brush, style = Stroke(2f, cap = StrokeCap.Round))
            }
        }
    }
}
