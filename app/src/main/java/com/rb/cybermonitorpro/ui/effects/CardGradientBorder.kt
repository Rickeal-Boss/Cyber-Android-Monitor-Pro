package com.rb.cybermonitorpro.ui.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rb.cybermonitorpro.ui.effects.CyberNightlightSwitch
import com.rb.cybermonitorpro.ui.nightlight.hdrCardBorderPatch
import com.rb.cybermonitorpro.ui.theme.DeepRedAlert
import com.rb.cybermonitorpro.ui.theme.NeonPurple
import com.rb.cybermonitorpro.ui.theme.NeonSteelBlue
import com.rb.cybermonitorpro.ui.theme.TitaniumGold
import java.util.UUID

/**
 * 卡片渐变描边光晕（静态，一次绘制，无动画开销）。
 *
 * 绘制区域为卡片最外缘向内的一条环带（containerColor = Color.Transparent 的隔断区）：
 * - 靠外 1/2：紫 → 蓝对角渐变描边（左上 → 右下），圆角贴合卡片边框；
 * - 靠内 1/2：静态阴影效果色（深暗描边，营造内凹层次）。
 *
 * 强度经调校（外环 alpha 0.65/0.55），契合淡淡的软件背景光晕，不喧宾夺主。
 *
 * @param cornerDp     卡片圆角（与 Card shape 保持一致）
 * @param bandWidth    环带总宽度（内外两半各占 1/2）
 * @param dynamicColor 动态描边色；非空时替代紫→蓝渐变（如电池温度语义变色），null 用默认渐变
 * @param hdrHighlight 是否在同一次 draw 中合并绘制 HDR 白色细高光（替代独立的 hdrHighlight
 *                     Modifier，每卡少一层 drawWithContent）。位置与旧实现像素级一致
 *                     （内层 Box 填满 Card，坐标系重合），z-order 保持旧序：高光在最底层
 */
@Composable
fun Modifier.cardGradientBorder(
    cornerDp: Dp = 20.dp,
    bandWidth: Dp = 4.dp,
    dynamicColor: Color? = null,
    hdrHighlight: Boolean = false,
): Modifier {
    val key = remember { "card:" + UUID.randomUUID().toString() }
    val cornerPx = with(LocalDensity.current) { cornerDp.toPx() }
    val strokePx = with(LocalDensity.current) { 2.dp.toPx() }
    return this
        // 行业首创：TurboXDR 开启时把卡片描边本体上报为 HDR 贴片（SDF 圆角描边），叠加增亮
        .then(if (CyberNightlightSwitch.enabled) Modifier.hdrCardBorderPatch(key, cornerPx, strokePx) else Modifier)
        // 层缓存：静态描边参数与默认渐变 Brush 只随 size 失效重建一次，重放零分配；
        // dynamicColor 保持在 onDrawWithContent 内逐帧读取，防止电池温度语义色被缓存冻结
        .drawWithCache {
    val bandPx = bandWidth.toPx()
    val halfPx = bandPx / 2f
    val cornerPx = cornerDp.toPx()

    // ── 默认描边光晕 Brush（缓存区只放静态渐变，dynamicColor 分支不进缓存） ──
    val glowBrush: Brush = Brush.linearGradient(
        colors = listOf(
            NeonPurple.copy(alpha = 0.65f),
            NeonSteelBlue.copy(alpha = 0.55f)
        ),
        start = Offset.Zero,                    // 左上
        end = Offset(size.width, size.height)   // 右下
    )

    onDrawWithContent {
            drawContent()

    // ── HDR 细高亮反光（合并绘制，z-order 最底层，参数与旧 hdrHighlight 一致：0.22α / 1.0dp） ──
    if (hdrHighlight) {
        drawRoundRect(
            color = Color.White.copy(alpha = 0.22f),
            topLeft = Offset(0.5f, 0.5f),
            size = size.copy(width = size.width - 1f, height = size.height - 1f),
            cornerRadius = CornerRadius(cornerPx),
            style = Stroke(width = 1.dp.toPx())
        )
    }

    // ── 靠外 1/2：描边光晕（默认紫→蓝对角渐变；dynamicColor 非空时为纯色，逐帧求值） ──
    val brush = if (dynamicColor != null) SolidColor(dynamicColor) else glowBrush
    // 描边中线内缩 halfPx/2，使外缘恰好贴卡片边缘（覆盖 0 .. halfPx）
    drawRoundRect(
        brush = brush,
        topLeft = Offset(halfPx / 2f, halfPx / 2f),
        size = size.copy(width = size.width - halfPx, height = size.height - halfPx),
        cornerRadius = CornerRadius(cornerPx - halfPx / 2f),
        style = Stroke(width = halfPx)
    )

    // ── 靠内 1/2：静态阴影效果色（覆盖 halfPx .. bandPx），弧度递减贴合 ──
    drawRoundRect(
        color = BorderInnerShadow,
        topLeft = Offset(halfPx + halfPx / 2f, halfPx + halfPx / 2f),
        size = size.copy(
            width = size.width - halfPx * 3f,
            height = size.height - halfPx * 3f
        ),
        cornerRadius = CornerRadius((cornerPx - halfPx * 1.5f).coerceAtLeast(0f)),
        style = Stroke(width = halfPx)
    )
    }
}
}

// 内半环静态阴影色：近黑的深紫黑，低透明度，只做层次不做存在
private val BorderInnerShadow = Color(0xFF06030E).copy(alpha = 0.35f)

/**
 * 电池温度 → 描边语义色（仅限概览页与电池页的电池温度卡片使用）：
 * - > 44.0℃：深红色边（过热告警）
 * - > 40.0℃：钛金色边（高温提醒）
 * - ≤ 40.0℃ / 无数据：null（与其他卡片一致的蓝紫渐变）
 */
fun batteryTempBorderColor(tempCelsius: Float?): Color? = when {
    tempCelsius == null -> null
    tempCelsius > 44.0f -> DeepRedAlert
    tempCelsius > 40.0f -> TitaniumGold
    else -> null
}
