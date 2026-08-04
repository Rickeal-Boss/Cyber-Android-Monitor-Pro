package com.rb.cybermonitorpro.ui.effects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Stable
import androidx.compose.runtime.composed
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Google 原生水波纹（Material3 默认 Ripple，LocalIndication）— 短单击即触发。
 *
 * 实现要点:
 * - 不传 indication → 走 M3 LocalIndication，即 Google 原生 Ripple 表现
 *   （按压扩散 + 松开回缩），颜色跟随主题 RippleConfiguration，零额外依赖。
 * - 先 clip 到卡片圆角再 clickable → 波纹被裁剪在圆角内，不外溢。
 * - onClick 默认为空: 信息类卡片仅需"点击出波纹"的反馈，不承担导航。
 *   已有导航的卡片（QuickLinkCard / 传感器卡）自带 clickable，无需本修饰符。
 *
 * @param shape  波纹裁剪形状，默认与卡片圆角一致 (20dp)
 * @param onClick 点击回调；默认空实现（仅波纹反馈）
 */
@Stable
fun Modifier.cardRipple(
    shape: Shape = RoundedCornerShape(20.dp),
    onClick: () -> Unit = {},
): Modifier = composed {
    this.clip(shape).clickable(onClick = onClick)
}
