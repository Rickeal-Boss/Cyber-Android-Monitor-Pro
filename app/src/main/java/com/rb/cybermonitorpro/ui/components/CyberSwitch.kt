package com.rb.cybermonitorpro.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rb.cybermonitorpro.HapticUtils
import com.rb.cybermonitorpro.ui.effects.revealLight
import com.rb.cybermonitorpro.ui.theme.*

// ═══════════════════════════════════════════════════════════════
//  常量 — 4 维度参数（与 GlowBackButton.drawFrostedGlassV3 同款约定）
// ═══════════════════════════════════════════════════════════════
private const val THUMB_HIGHLIGHT_CX = 0.30f
private const val THUMB_HIGHLIGHT_CY = 0.25f
private const val THUMB_SPECULAR_CX = 0.35f
private const val THUMB_SPECULAR_CY = 0.30f

private val SWITCH_WIDTH = 52.dp
private val SWITCH_HEIGHT = 32.dp
private val THUMB_SIZE = 26.dp
private val TRACK_CORNER = 16.dp
private val TRACK_PADDING = 3.dp          // thumb 与 track 内壁的间隙（左右对称）

// L4 底部暗线色 — 与 CardGradientBorder.BorderInnerShadow 同值，但该域 private 无法跨文件
private val BorderShadow = Color(0xFF06030E).copy(alpha = 0.75f)

/**
 * 赛博拟物开关 — 4 维度立体感（统一光源 + 凸凹材质 + 多层高光 + 精细边缘）
 *
 * 与 Material3 Switch 同签名，可直接替换调用点：
 *   Switch(checked=..., onCheckedChange=...) → CyberSwitch(checked=..., onCheckedChange=...)
 *
 * @param checked 当前开关状态
 * @param onCheckedChange 状态变化回调（null 则禁用）
 * @param modifier 外部修饰符（可挂 revealLight / entranceReveal）
 */
@Composable
fun CyberSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = onCheckedChange != null,
) {
    val ctx = LocalContext.current

    // ── 静态 Track 渐变：不随状态变化，remember 避免每帧重建 ──
    val trackGradient = remember {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f  to NeonPurpleDeep.copy(alpha = 0.55f),
                0.15f to NeonPurpleDeep.copy(alpha = 0.85f),
                0.5f  to CyberBackground.copy(alpha = 0.90f),
                1.0f  to NeonPurpleDeep.copy(alpha = 0.70f)
            )
        )
    }

    // ── 状态切换动画：thumb 平移（graphicsLayer 绘制层，不触发 layout） ──
    val targetOffset = if (checked) SWITCH_WIDTH - THUMB_SIZE - TRACK_PADDING
                       else TRACK_PADDING
    val offsetX by animateDpAsState(
        targetValue = targetOffset,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,   // 略带回弹，开关有"咔哒"感
            stiffness = Spring.StiffnessMedium
        ),
        label = "thumbOffsetX"
    )

    // ── 颜色过渡 ──
    val thumbColor by animateColorAsState(
        targetValue = if (checked) NeonPurpleBright else NeonSteelBlue.copy(alpha = 0.85f),
        animationSpec = tween(200), label = "thumbColor"
    )
    val trackActiveAlpha by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(200), label = "trackActive"
    )

    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(SWITCH_WIDTH, SWITCH_HEIGHT)
            .graphicsLayer { alpha = if (enabled) 1f else 0.38f }   // 禁用态视觉降级
            .revealLight(radius = 100.dp, intensity = 0.18f)   // 悬停光照响应
            .toggleable(
                value = checked,
                onValueChange = {
                    onCheckedChange?.invoke(it)
                    HapticUtils.lightTap(ctx)
                },
                enabled = enabled,
                interactionSource = interaction
                // indication 省略 → LocalIndication 默认 ripple，按压有反馈
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        // ── Track 层（内凹容器） ──
        Canvas(
            Modifier.fillMaxSize().clip(RoundedCornerShape(TRACK_CORNER))
        ) {
            drawTrack(trackGradient, trackActiveAlpha)
        }
        // ── Thumb 层（凸起实体）— graphicsLayer 平移，绘制层不触发 layout ──
        Box(
            Modifier
                .graphicsLayer { translationX = offsetX.toPx() }
                .size(THUMB_SIZE)
                .shadow(
                    elevation = 2.dp, shape = CircleShape,
                    ambientColor = PurpleGlow.copy(alpha = 0.50f),
                    spotColor = PurpleGlowStrong
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawThumb(thumbColor, trackActiveAlpha)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  Track 绘制 — 维度 1（4-stop 内凹渐变）+ 维度 4（上下沿双线）
// ═══════════════════════════════════════════════════════════════
private fun DrawScope.drawTrack(gradient: Brush, activeAlpha: Float) {
    val w = size.width; val h = size.height
    val r = TRACK_CORNER.toPx()

    // 基底 4-stop 线性渐变（上亮下暗，内凹槽）— brush 由调用方 remember 缓存
    drawRoundRect(brush = gradient, cornerRadius = CornerRadius(r))

    // 激活态叠加紫色填充
    if (activeAlpha > 0.01f) {
        drawRoundRect(
            color = NeonPurple.copy(alpha = 0.35f * activeAlpha),
            cornerRadius = CornerRadius(r)
        )
    }

    // ── 维度 4：上下沿双 1px 线（明暗夹缝=内凹错觉主信号） ──
    val lineY = 0.5f
    drawLine(  // 上沿亮线（顶边反光，亮 1 级）
        color = NeonPurpleBright.copy(alpha = 0.60f),
        start = Offset(r, lineY), end = Offset(w - r, lineY), strokeWidth = 1f
    )
    drawLine(  // 下沿暗线（底边阴影）
        color = BorderShadow,
        start = Offset(r, h - lineY), end = Offset(w - r, h - lineY), strokeWidth = 1f
    )
}

// ═══════════════════════════════════════════════════════════════
//  Thumb 绘制 — 维度 1（径向偏移）+ 维度 2（三层高光）+ 维度 3（凸出）+ 维度 4（底部暗线）
// ═══════════════════════════════════════════════════════════════
private fun DrawScope.drawThumb(baseColor: Color, activeAlpha: Float) {
    val w = size.width; val h = size.height
    val cx = w * THUMB_HIGHLIGHT_CX; val cy = h * THUMB_HIGHLIGHT_CY
    val rimRadius = size.minDimension / 2f - 0.5.dp.toPx()   // rim 内缩半描边宽，防溢出

    // L1 主体径向渐变（左上亮右下暗，135° 受光）
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                baseColor.copy(alpha = 1.0f),
                baseColor.copy(alpha = 0.75f),
                baseColor.copy(alpha = 0.45f).compositeOver(Color.Black)
            ),
            center = Offset(cx, cy),
            radius = w * 0.8f
        )
    )

    // L2 点高光（镜面反射亮点）
    drawCircle(
        color = Color.White.copy(alpha = 0.45f),
        radius = w * 0.09f,
        center = Offset(w * THUMB_SPECULAR_CX, h * THUMB_SPECULAR_CY)
    )

    // L3 顶部弧形主高光（替代大椭圆漫反射 — 金属/玻璃感）
    drawArc(
        color = NeonPurpleBright.copy(alpha = 0.35f),
        startAngle = 200f, sweepAngle = 140f, useCenter = false,
        style = Stroke(width = 1.5.dp.toPx()),
        topLeft = Offset(w * 0.12f, h * 0.12f),
        size = Size(w * 0.76f, h * 0.76f)
    )

    // L4 Rim Light（外侧青色描边，凸出信号）— 半径内缩 0.5dp 防溢出
    drawCircle(
        color = NeonCyan.copy(alpha = 0.40f * (0.5f + activeAlpha * 0.5f)),
        radius = rimRadius,
        style = Stroke(width = 1.dp.toPx())
    )

    // L5 底部暗线（呼应 track 下沿，光源一致）
    drawArc(
        color = Color(0xFF1A3A6E).copy(alpha = 0.70f),
        startAngle = 20f, sweepAngle = 140f, useCenter = false,
        style = Stroke(width = 1.dp.toPx()),
        topLeft = Offset(w * 0.12f, h * 0.12f),
        size = Size(w * 0.76f, h * 0.76f)
    )
}
