package com.example.deviceinfoviewer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
import kotlin.math.sin
import kotlin.math.sin

/**
 * 顶部标题栏暗玻璃动效装饰 — 对齐参考图 sys.jpg
 *
 * 视觉特征 (参照 sys.jpg 暗色药丸容器):
 * 1. 深色径向渐变底衬 — 模拟暗玻璃/磨砂质感
 * 2. 呼吸光晕层 — 微妙的紫色辉光从中心向边缘扩散
 * 3. 内发光边框 — 药丸形容器边缘的微亮描边线 (动态透明度)
 * 4. 极弱粒子氛围 — 3~4 颗微弱闪烁光点 (非主导视觉)
 * 5. 底部流光带 — 细微水平流动的高光线 (替代波形)
 *
 * 用法: 包裹在 TabRow 外层 Box 的底层, 作为 matchParentSize() 背景装饰
 */
@Composable
fun NeonHeaderDecoration(
    modifier: Modifier = Modifier,
    showParticles: Boolean = true,
    showFlowLine: Boolean = true,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "headerFx")

    // ── 光晕呼吸 (整体亮度缓慢起伏) ──
    val breathe by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    // ── 边框脉冲 (内发光描边线的透明度, 对齐 sys.jpg 的浅灰内发光) ──
    val borderPulse by infiniteTransition.animateFloat(
        initialValue = 0.20f,
        targetValue = 0.50f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "borderPulse"
    )

    // ── 流光相位 (底部高光线的水平偏移) ──
    val flowPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "flowPhase"
    )

    // ── 粒子闪烁相位 ──
    val sparklePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sparklePhase"
    )

    Box(
        modifier
            .fillMaxWidth()
            .clipToBounds()
    ) {
        // ═══ 层级 1: 深色玻璃渐变底衬 (对齐 sys.jpg 中心亮→边缘暗) ═══
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            CyberCardStart.copy(alpha = breathe * 0.7f),   // 中心稍亮
                            CyberBackground.copy(alpha = 0.92f),           // 边缘实深
                            CyberBackground.copy(alpha = 0.98f)            // 最边缘
                        ),
                        center = Offset(0.5f, 0.45f),
                        radius = 600f
                    )
                )
        )

        // ═══ 层级 2: 紫色辉光晕染 (中心辐射) ═══
        // 微弱的紫色光从上方中心向外扩散, 营造"霓虹环境光"
        Box(
            Modifier
                .matchParentSize()
                .alpha(breathe * 0.12f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            NeonPurpleDeep.copy(alpha = 0.6f),
                            NeonPurpleDeep.copy(alpha = 0.15f),
                            Color.Transparent
                        ),
                        center = Offset(0.5f, 0.2f),   // 偏上中心
                        radius = 800f
                    )
                )
        )

        // ═══ 层级 3: 内发光边框 (Canvas 绘制药丸形描边, 对齐 sys.jpg 浅灰内发光 #888) ═══
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val cornerRadius = CornerRadius(h * 0.48f) // 48% 高度 = 药丸半圆

            // 主边框路径
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

            // 主描边 (浅紫灰, 模拟 sys.jpg 的 #888 内发光, 动态透明度)
            drawPath(
                path = borderPath,
                color = NeonSteelBlue.copy(alpha = borderPulse * 0.55f),
                style = Stroke(width = 1.0f.dp.toPx())
            )

            // 外层柔和光晕 (模拟内发光扩散)
            drawPath(
                path = borderPath,
                color = NeonPurple.copy(alpha = borderPulse * 0.12f),
                style = Stroke(width = 2.5f.dp.toPx())
            )
        }

        // ═══ 层级 4: 底部流光带 ═══
        if (showFlowLine) {
            Canvas(Modifier.matchParentSize().height(2.dp)) {
                val w = size.width
                val h = size.height

                // 流动高光线: 从左到右移动的光斑
                for (i in 0..2) {
                    // 每个光斑有不同的相位偏移
                    val phaseOffset = (flowPhase + i * 0.33f) % 1f
                    val centerX = w * phaseOffset
                    val glowWidth = w * 0.25f

                    drawCircle(
                        color = NeonPurple.copy(alpha = breathe * 0.25f),
                        radius = 1.5f.dp.toPx(),
                        center = Offset(centerX, h / 2)
                    )
                    // 光斑尾部拖影
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

        // ═══ 层级 5: 极弱粒子氛围 ═══
        if (showParticles) {
            Canvas(Modifier.matchParentSize()) {
                val w = size.width
                val h = size.height
                val particleCount = 4

                for (i in 0 until particleCount) {
                    val baseX = w * (i + 0.6f) / (particleCount + 0.6f)
                    val baseY = h * (0.2f + (sin(i * 2.3) * 0.5f + 0.5f) * 0.4f).toFloat()

                    // 各粒子独立闪烁
                    val sparkle = sin(
                        Math.toRadians((sparklePhase + i * 90f).toDouble())
                    ).toFloat().let { (it + 1f) / 2f }

                    // 只绘制足够亮的粒子 (产生随机闪烁感)
                    if (sparkle > 0.6f) {
                        val alpha = ((sparkle - 0.6f) / 0.4f) * breathe * 0.35f
                        val radius = 1.2f + sparkle * 0.8f

                        // 微弱外光晕
                        drawCircle(
                            color = NeonPurple.copy(alpha = alpha * 0.2f),
                            radius = radius * 2.8f,
                            center = Offset(baseX, baseY)
                        )
                        // 内核
                        drawCircle(
                            color = NeonPurpleBright.copy(alpha = alpha * 0.8f),
                            radius = radius,
                            center = Offset(baseX, baseY)
                        )
                    }
                }
            }
        }

        // ═══ 层级 6: 气泡滚动 (从左至右漂浮, 透明度随高度上升递减) ═══
        // BubbleScrollingLayer(flowPhase)
    }
}

/**
 * 气泡滚动层 — TAB 栏底部持续从左至右浮动的微小气泡
 *
 * 气泡从左侧生成，向右漂浮至消失后再从左侧重新进入。
 * 大小随机 (4-12dp)、透明度随靠近顶部递减 (底部最亮→顶部最淡)，
 * 确保不遮挡 TabRow 文字交互区域。
 */
@Composable
private fun BubbleScrollingLayer(flowPhase: Float) {
    val infiniteTransition = rememberInfiniteTransition(label = "bubbleFx")

    Canvas(Modifier.fillMaxSize().padding(bottom = 8.dp)) {
        val w = size.width
        val h = size.height
        val bubbleCount = 10

        for (i in 0 until bubbleCount) {
            // 气泡间距均匀分布, 各气泡有独立的相位偏移 (产生错落感)
            val phaseOffset = (i.toFloat() / bubbleCount * 0.7f) % 1f
            // 水平位置: 流动相位 + 各气泡独立偏移
            val baseX = ((flowPhase + phaseOffset) % 1f) * w

            // 垂直位置: 在底部 60% 区域内随机分布
            val baseY = h * (0.4f + (sin(i * 1.7f) * 0.5f + 0.5f) * 0.55f).toFloat()

            // 气泡大小: 随机 4-12dp, 用确定性函数产生伪随机
            val sizeFactor = (sin(i * 2.71f + 1.3f) * 0.5f + 0.5f)
            val radius = (4f + sizeFactor * 8f).dp.toPx()

            // 透明度: 越靠近顶部(小Y值)越淡 — 底部 35% → 顶部 5%
            val heightRatio = (baseY / h).coerceIn(0.15f, 0.95f)
            val alpha = (0.06f + (1f - heightRatio) * 0.28f).coerceIn(0.04f, 0.28f)

            // 颜色: 以青色为主调, 大号气泡偏紫, 小号偏青
            val hue = if (sizeFactor > 0.5f) NeonPurple.copy(alpha = alpha * 0.7f)
            else NeonCyan.copy(alpha = alpha * 0.85f)

            // 气泡光晕 (外层柔光)
            drawCircle(
                color = hue.copy(alpha = alpha * 0.15f),
                radius = radius * 2.2f,
                center = Offset(baseX, baseY)
            )

            // 气泡本体 (半透明填充 + 微亮描边)
            drawCircle(
                color = hue.copy(alpha = alpha * 0.4f),
                radius = radius,
                center = Offset(baseX, baseY)
            )

            // 高光点 (气泡受光面, 偏左上)
            drawCircle(
                color = Color.White.copy(alpha = alpha * 0.35f),
                radius = radius * 0.22f,
                center = Offset(baseX - radius * 0.3f, baseY - radius * 0.3f)
            )

            // 底边反光弧线 (气泡柔光边缘)
            drawCircle(
                color = hue.copy(alpha = alpha * 0.08f),
                radius = radius * 1.15f,
                center = Offset(baseX + radius * 0.1f, baseY + radius * 0.15f)
            )
        }
    }
}

/**
 * 霓虹动效分割线 — 替代 HorizontalDivider
 *
 * 特征: 渐变透明两端 → 中心紫亮, 带微妙脉冲呼吸
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

        // 主体: 中间亮两端淡出的细线
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

        // 下层柔光 (更粗更淡)
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
