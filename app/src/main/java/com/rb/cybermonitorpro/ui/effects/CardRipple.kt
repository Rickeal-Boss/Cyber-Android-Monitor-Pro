package com.rb.cybermonitorpro.ui.effects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Outline
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.LayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Google 原生水波纹（Material3 默认 Ripple，LocalIndication）— 短单击即触发。
 *
 * 实现要点:
 * - 不传 indication → 走 M3 LocalIndication，即 Google 原生 Ripple 表现
 *   （按压扩散 + 松开回缩），颜色跟随主题 RippleConfiguration，零额外依赖。
 * - 先 clip 到卡片圆角再 clickable → 波纹被裁剪在圆角内，不外溢。
 * - 与 cardGradientBorder 配合：inset 将波纹裁剪区向内收缩「边框环带宽度(默认 4dp)」，
 *   使波纹扩散严格限制在描边内侧，避免半透明渐变描边在按压时被波纹透色冲淡
 *   （波纹与描边层级同步：描边压住波纹外缘，波纹限制在环带内侧）。
 * - onClick 默认为空: 信息类卡片仅需"点击出波纹"的反馈，不承担导航。
 *   已有导航的卡片（QuickLinkCard / 传感器卡）自带 clickable，无需本修饰符。
 *
 * @param onClick 点击回调；默认空实现（仅波纹反馈）
 * @param inset   波纹裁剪内缩量（默认 4dp = 边框环带宽度），使波纹不进入描边区
 */
@Stable
fun Modifier.cardRipple(
    onClick: () -> Unit = {},
    inset: Dp = 4.dp,
): Modifier = composed {
    val shape: Shape = if (inset > 0.dp) InsetRoundedShape(inset, 20.dp) else RoundedCornerShape(20.dp)
    this.clip(shape).clickable(onClick = onClick)
}

/**
 * 向内收缩的圆角矩形裁剪形状：在距四边 inset 处生成圆角矩形，
 * 圆角半径同步收缩 (corner - inset)，保持边框视觉连续。
 * 用于把水波纹限制在与 cardGradientBorder 同宽的环带内侧。
 */
private data class InsetRoundedShape(
    val inset: Dp,
    val corner: Dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val insetPx = with(density) { inset.toPx() }
        val cornerPx = with(density) { corner.toPx() }
        val left = insetPx
        val top = insetPx
        val right = size.width - insetPx
        val bottom = size.height - insetPx
        if (right <= left || bottom <= top) return Outline.Rectangle
        val radius = (cornerPx - insetPx).coerceAtLeast(0f)
        return Outline.Rounded(
            RoundRect(
                left = left,
                top = top,
                right = right,
                bottom = bottom,
                cornerRadius = CornerRadius(radius, radius),
            )
        )
    }
}
