package com.rb.cybermonitorpro.ui.effects

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rb.cybermonitorpro.ui.theme.NeonCyan
import com.rb.cybermonitorpro.ui.theme.NeonPurple

/**
 * HUD 四角括号装饰 — 在卡片描边环带区内绘制 L 形短线。
 * 纯绘制叠加，不改变卡片 shape/裁剪/阴影/HDR。
 * 注意：调用方须将其放在修饰链【最前端】（.fillMaxWidth() 之后），
 *       否则会被 cardRipple 的 inset 4dp 裁剪裁掉而不可见。
 */
fun Modifier.cyberCornerBrackets(
    cornerDp: Dp = 20.dp,
    lengthDp: Dp = 12.dp,
    strokeDp: Dp = 1.5.dp,
    color: Color = NeonCyan.copy(alpha = 0.55f),
    enabled: Boolean = true,
): Modifier = drawWithContent {
    drawContent()
    if (!enabled) return@drawWithContent

    val c = cornerDp.toPx()
    val len = lengthDp.toPx()
    val stroke = strokeDp.toPx()
    val inset = 2.dp.toPx()
    val w = size.width
    val h = size.height

    val start = c + 2.dp.toPx()

    // 左上角
    drawLine(color, Offset(start, inset), Offset(start + len, inset), stroke)
    drawLine(color, Offset(inset, start), Offset(inset, start + len), stroke)
    // 右上角
    drawLine(color, Offset(w - start, inset), Offset(w - start - len, inset), stroke)
    drawLine(color, Offset(w - inset, start), Offset(w - inset, start + len), stroke)
    // 左下角
    drawLine(color, Offset(start, h - inset), Offset(start + len, h - inset), stroke)
    drawLine(color, Offset(inset, h - start), Offset(inset, h - start - len), stroke)
    // 右下角
    drawLine(color, Offset(w - start, h - inset), Offset(w - start - len, h - inset), stroke)
    drawLine(color, Offset(w - inset, h - start), Offset(w - inset, h - start - len), stroke)
}

/**
 * 顶部扫描条装饰 — 卡片顶部渐变细线 + 中央缺口圆点。
 * 位于 6dp 处（描边环带下方、内容区上方）。
 */
fun Modifier.cyberTopBar(
    cornerDp: Dp = 20.dp,
    gapDp: Dp = 24.dp,
    strokeDp: Dp = 1.5.dp,
    color0: Color = NeonPurple.copy(alpha = 0.6f),
    color1: Color = NeonCyan.copy(alpha = 0.6f),
    enabled: Boolean = true,
): Modifier = drawWithContent {
    drawContent()
    if (!enabled) return@drawWithContent

    val c = cornerDp.toPx()
    val gap = gapDp.toPx()
    val stroke = strokeDp.toPx()
    val y = 6.dp.toPx()
    val w = size.width
    val margin = c + 2.dp.toPx()
    val cx = w / 2f

    val brush = Brush.linearGradient(
        colors = listOf(color0, color1),
        start = Offset(0f, y),
        end = Offset(w, y),
    )

    drawLine(brush, Offset(margin, y), Offset(cx - gap / 2f, y), stroke)
    drawLine(brush, Offset(cx + gap / 2f, y), Offset(w - margin, y), stroke)
    drawCircle(color1, radius = stroke * 1.2f, center = Offset(cx, y))
}
