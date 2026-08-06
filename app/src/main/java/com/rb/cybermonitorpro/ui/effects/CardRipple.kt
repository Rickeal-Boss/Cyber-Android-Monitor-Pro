package com.rb.cybermonitorpro.ui.effects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Google 原生水波纹（Material3 默认 Ripple，LocalIndication）— 短单击即触发。
 *
 * 实现要点:
 * - 不传 indication → 走 M3 LocalIndication，即 Google 原生 Ripple 表现
 *   （按压扩散 + 松开回缩），颜色跟随主题 RippleConfiguration，零额外依赖。
 * - 先 clip 到卡片圆角再 clickable → 波纹被裁剪在圆角内，不外溢。
 * - ★ 修复 (2026-08-06): 裁剪从 Shape.createOutline(→Outline) 改为 Canvas drawWithContent+clipPath，
 *   绕开 Compose BOM 2025.06.00 + release 编译 classpath 中 Outline 类 Unresolved reference 问题。
 *   RoundRect / CornerRadius / Path 在 release 无此问题，语义等价。
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
    if (inset <= 0.dp) {
        // 无内缩：直接用系统圆角裁剪（Shape 由库内部实现，不暴露 Outline 到我们的编译范围）
        this
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    } else {
        // 内缩裁剪：Canvas 层 Path.addRoundRect + clipPath，绕开 Shape.createOutline→Outline
        val density = LocalDensity.current
        this
            .drawWithContent {
                val insetPx = with(density) { inset.toPx() }
                val cornerPx = with(density) { 20.dp.toPx() }
                val insetCorner = (cornerPx - insetPx).coerceAtLeast(0f)
                val left = insetPx
                val top = insetPx
                val right = size.width - insetPx
                val bottom = size.height - insetPx
                if (right > left && bottom > top) {
                    val clipPath = Path().apply {
                        addRoundRect(
                            RoundRect(
                                left = left,
                                top = top,
                                right = right,
                                bottom = bottom,
                                cornerRadius = CornerRadius(insetCorner, insetCorner),
                            )
                        )
                    }
                    clipPath(clipPath) {
                        drawContent()
                    }
                } else {
                    // 裁剪区域无效（尺寸太小）：无裁剪，波纹铺满
                    drawContent()
                }
            }
            .clickable(onClick = onClick)
    }
}
