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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.deviceinfoviewer.ui.theme.ChartAreaPurple
import com.example.deviceinfoviewer.ui.theme.DividerCyber
import com.example.deviceinfoviewer.ui.theme.ChartLinePurple

/**
 * 平滑动画折线图 — 使用 animateFloatAsState 让曲线丝滑过渡
 */
@Composable
fun LineChart(
    data: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = ChartLinePurple,
    areaColor: Color = ChartAreaPurple,
    showGrid: Boolean = true,
    gridLines: Int = 5
) {
    if (data.isEmpty()) return

    // 为每个数据点创建平滑动画
    val transition = updateTransition(targetState = data, label = "chart")
    val animatedData = remember(data) { data.mapIndexed { i, v -> i to v } }
    val smoothed = animatedData.map { (i, _) ->
        val target = data.getOrElse(i) { 0f }
        val anim by transition.animateFloat(label = "val$i", transitionSpec = { tween(600) }) { target }
        anim
    }

    Canvas(
        modifier = modifier.fillMaxWidth().height(120.dp)
    ) {
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

        if (points.size > 1) {
            val linePath = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    val prev = points[i - 1]; val curr = points[i]
                    val cx = prev.x + (curr.x - prev.x) * 0.5f
                    cubicTo(cx, prev.y, cx, curr.y, curr.x, curr.y)
                }
            }
            drawPath(linePath, lineColor, style = Stroke(2.5f, cap = StrokeCap.Round))
        }

        points.lastOrNull()?.let { drawCircle(lineColor, 4f, it) }
    }
}

@Composable
fun DualLineChart(
    data1: List<Float>, data2: List<Float>,
    modifier: Modifier = Modifier,
    lineColor1: Color = ChartLinePurple, lineColor2: Color = Color(0xFFFF00E5),
    showGrid: Boolean = true, gridLines: Int = 5
) {
    if (data1.isEmpty() || data2.isEmpty()) return

    val t1 = updateTransition(data1, "d1")
    val t2 = updateTransition(data2, "d2")

    val s1 = data1.mapIndexed { i, _ ->
        val target = data1.getOrElse(i) { 0f }
        val anim by t1.animateFloat(label = "d1_$i", transitionSpec = { tween(600) }) { target }
        anim
    }
    val s2 = data2.mapIndexed { i, _ ->
        val target = data2.getOrElse(i) { 0f }
        val anim by t2.animateFloat(label = "d2_$i", transitionSpec = { tween(600) }) { target }
        anim
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

        listOf(s1 to lineColor1, s2 to lineColor2).forEach { (data, color) ->
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
                drawPath(path, color, style = Stroke(2f, cap = StrokeCap.Round))
            }
        }
    }
}
