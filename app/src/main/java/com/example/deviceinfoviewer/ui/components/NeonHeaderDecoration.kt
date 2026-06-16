package com.example.deviceinfoviewer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.deviceinfoviewer.ui.theme.*

/**
 * 顶部标题栏暗玻璃动效装饰 — 安全版
 *
 * [修复] 移除 Brush.radialGradient(radius=600f/800f) 大半径径向渐变
 * 某些 GPU 驱动无法处理超大半径 radialGradient，导致进程直接被杀
 *
 * 替代方案: 水平渐变 + 短半径径向渐变(≤50f) + 纯色铺底
 * 动画: 仅使用 Alpha 脉冲（已验证 infiniteTransition 本身安全）
 */
@Composable
fun NeonHeaderDecoration(
    modifier: Modifier = Modifier,
    showParticles: Boolean = true,
    showFlowLine: Boolean = true,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "headerFx")

    // ── 呼吸脉冲 (仅透明度，不和 radialGradient 组合) ──
    val breathe by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    // ── 边框脉冲 ──
    val borderPulse by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "borderPulse"
    )

    Box(
        modifier
            .fillMaxWidth()
            .clipToBounds()
    ) {
        // ═══ 层级 1: 纯色铺底 + 水平渐变薄层（代替径向渐变）═══
        Box(
            Modifier
                .matchParentSize()
                .background(CyberCardStart)  // 纯色底，安全
        )
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            NeonPurple.copy(alpha = breathe * 0.15f),
                            Color.Transparent
                        )
                    )
                )
        )

        // ═══ 层级 2: 内发光边框 (Canvas 安全绘制) ═══
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val cornerRadius = CornerRadius(h * 0.48f)

            val borderPath = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(
                            left = 0.5.dp.toPx(),
                            top = 0.5.dp.toPx(),
                            right = w - 0.5.dp.toPx(),
                            bottom = h - 0.5.dp.toPx()
                        ),
                        cornerRadius = cornerRadius
                    )
                )
            }

            // 主描边
            drawPath(
                path = borderPath,
                color = NeonSteelBlue.copy(alpha = borderPulse * 0.55f),
                style = Stroke(width = 1f.dp.toPx())
            )

            // 外层柔和光晕
            drawPath(
                path = borderPath,
                color = NeonPurple.copy(alpha = borderPulse * 0.12f),
                style = Stroke(width = 2.5f.dp.toPx())
            )
        }

        // ═══ 层级 3: 底部流光带 (简化版，无内联画笔创建) ═══
        if (showFlowLine) {
            val flowPhase by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(3500, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "flowPhase"
            )

            Canvas(Modifier.matchParentSize().height(2.dp)) {
                val w = size.width
                val h = size.height
                val centerX = w * flowPhase
                val glowWidth = w * 0.25f

                drawCircle(
                    color = NeonPurple.copy(alpha = breathe * 0.25f),
                    radius = 1.5f.dp.toPx(),
                    center = Offset(centerX, h / 2)
                )
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            NeonPurpleBright.copy(alpha = breathe * 0.18f),
                            Color.Transparent
                        ),
                        startX = centerX - glowWidth * 0.5f,
                        endX = centerX + glowWidth * 0.8f
                    ),
                    start = Offset(centerX - glowWidth * 0.5f, h / 2),
                    end = Offset(centerX + glowWidth * 0.8f, h / 2),
                    strokeWidth = 1.2f.dp.toPx()
                )
            }
        }
    }
}

/**
 * 霓虹动效分割线
 */
@Composable
fun NeonDivider(
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "dividerFx")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.20f,
        targetValue = 0.50f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dividerPulse"
    )

    Canvas(modifier.fillMaxWidth().height(1.5.dp)) {
        val w = size.width

        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    NeonPurpleDeep.copy(alpha = pulseAlpha * 0.6f),
                    NeonPurple.copy(alpha = pulseAlpha),
                    NeonPurpleDeep.copy(alpha = pulseAlpha * 0.6f),
                    Color.Transparent
                )
            ),
            start = Offset(0f, 0f),
            end = Offset(w, 0f),
            strokeWidth = 0.8f.dp.toPx()
        )

        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    NeonPurple.copy(alpha = pulseAlpha * 0.12f),
                    NeonPurple.copy(alpha = pulseAlpha * 0.08f),
                    Color.Transparent
                )
            ),
            start = Offset(w * 0.15f, 1.2f.dp.toPx()),
            end = Offset(w * 0.85f, 1.2f.dp.toPx()),
            strokeWidth = 2.5f.dp.toPx()
        )
    }
}
