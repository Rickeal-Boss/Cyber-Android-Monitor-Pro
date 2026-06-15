package com.example.deviceinfoviewer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import android.os.Build
import com.example.deviceinfoviewer.HapticUtils
import com.example.deviceinfoviewer.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ═══════════════════ 赛博网格背景 ═══════════════════

/**
 * 动态赛博网格 — 双层视差透视网格线
 *
 * 使用 Canvas 自绘两层网格（前景+背景），不同滚动速度产生视差流动。
 * 网格线颜色: NeonPurpleDeep 半透明，交点处略亮。
 */
@Composable
fun CyberGridBackground(
    modifier: Modifier = Modifier,
    scrollProgress: Float = 0f,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cyber_grid")
    val flowOffset by infiniteTransition.animateFloat(0f, 60f,
        infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart), label = "gridFlow")

    val nearColor = NeonPurpleDeep.copy(alpha = 0.22f)
    val farColor = NeonPurpleDeep.copy(alpha = 0.10f)
    val dotColor = NeonPurple.copy(alpha = 0.25f)

    Canvas(modifier.fillMaxSize()) {
        val w = size.width; val h = size.height

        // ── 远景层: 慢速移动 (×0.3) ──
        val farY = flowOffset * 0.3f + scrollProgress * 40f
        drawGrid(w, h, 64f, farColor.copy(alpha = 0.08f), dotColor.copy(alpha = 0.08f), farY)
        // ── 近景层: 正常速度 (×1.0) ──
        val nearY = flowOffset * 1.0f + scrollProgress * 120f
        drawGrid(w, h, 40f, nearColor, dotColor, nearY)

        // 竖直线 (仅近景)
        var x = 20f + (flowOffset * 0.5f % 40f)
        while (x < w) {
            drawLine(nearColor.copy(alpha = 0.06f), Offset(x, 0f), Offset(x, h), strokeWidth = 0.5f)
            x += 40f
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGrid(
    w: Float, h: Float, spacing: Float, lineColor: Color, dotColor: Color, yOffset: Float
) {
    var y = -spacing + (yOffset % spacing)
    while (y < h) {
        val density = (y / h).coerceIn(0f, 1f)
        val alpha = lineColor.alpha * (0.6f + density * 0.4f)
        drawLine(lineColor.copy(alpha = alpha), Offset(0f, y), Offset(w, y), strokeWidth = 0.6f + density * 0.3f)
        var x = 0f
        while (x < w) { drawCircle(dotColor.copy(alpha = alpha * 1.3f), 1.2f, Offset(x, y)); x += spacing }
        y += spacing * (1f - density * 0.55f)
    }
}

// ═══════════════════ 霓虹粒子背景 ═══════════════════

private data class Particle(
    val x: Float, val y: Float, val radius: Float,
    val alpha: Float, val speedX: Float, val speedY: Float
)

/**
 * 微型霓虹粒子漂浮 — 轻量级 Canvas 粒子系统
 *
 * 粒子数有限 (≤30) 保证性能，漂浮速度极慢，配合 alpha 渐变产生呼吸感。
 */
@Composable
fun NeonParticleBackground(modifier: Modifier = Modifier, particleColor: Color = NeonPurpleBright) {
    val infiniteTransition = rememberInfiniteTransition(label = "particles")
    val phase by infiniteTransition.animateFloat(0f, 360f, infiniteRepeatable(tween(8000)), label = "pPhase")
    val particles = remember {
        List(25) {
            Particle(
                Random.nextFloat(), Random.nextFloat(),
                Random.nextFloat() * 1.5f + 0.5f,
                Random.nextFloat() * 0.25f + 0.05f,
                (Random.nextFloat() - 0.5f) * 0.08f,
                (Random.nextFloat() - 0.5f) * 0.08f
            )
        }
    }

    Canvas(modifier.fillMaxSize()) {
        val rad = Math.toRadians(phase.toDouble()).toFloat()
        for (p in particles) {
            val px = (p.x * size.width + cos(rad + p.speedX * 60f) * 30f).coerceIn(0f, size.width)
            val py = (p.y * size.height + sin(rad + p.speedY * 60f) * 30f).coerceIn(0f, size.height)
            drawCircle(particleColor.copy(alpha = p.alpha * (0.6f + 0.4f * cos(rad * 2f + p.x * 6f))),
                p.radius, Offset(px, py))
        }
    }
}

// ═══════════════════ 霓虹边框光效 ═══════════════════

/**
 * 卡片 1px 霓虹描边 + 顶部金属反光弧线
 *
 * 静态边框为 NeonPurpleDeep 半透明，hover 时增强为 NeonPurpleBright。
 * 左上角绘制细高光弧线模拟金属反光质感。
 */
fun Modifier.neonBorderGlow(
    cornerDp: Dp = 12.dp,
    isHighlighted: Boolean = false,
): Modifier = this.drawWithContent {
    drawContent()
    val cornerPx = cornerDp.toPx()
    val borderAlpha = if (isHighlighted) 0.6f else 0.2f
    val borderColor = if (isHighlighted) NeonPurpleBright else NeonPurple.copy(alpha = borderAlpha)

    // 边框
    drawRoundRect(
        color = borderColor,
        cornerRadius = CornerRadius(cornerPx),
        style = Stroke(width = if (isHighlighted) 1.2f else 0.8f)
    )

    // 顶部高光弧线 (金属反光)
    drawArc(
        color = Color.White.copy(alpha = 0.08f),
        startAngle = 130f, sweepAngle = 100f,
        useCenter = false,
        topLeft = Offset(2f, 1f),
        size = androidx.compose.ui.geometry.Size(size.width - 4f, size.height * 0.25f),
        style = Stroke(width = 0.7f)
    )

    // 左上角小亮斑
    drawCircle(
        color = Color.White.copy(alpha = if (isHighlighted) 0.12f else 0.04f),
        radius = cornerPx * 0.4f,
        center = Offset(cornerPx * 0.6f, cornerPx * 0.6f)
    )
}

// ═══════════════════ 光感多彩水态风格卡片 (交互增强版) ═══════════════════

/**
 * 光感多彩水态风格卡片 — 交互时动态增强色彩融合强度
 *
 * 按压/hover 时色彩深度增加 40%，配合 GPU 加速 RenderEffect 模糊 (API 31+)。
 * 多层透明渐变叠加模拟光线穿透液态材质的多彩折射效果。
 */
@Composable
fun LiquidColorCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var isPressed by remember { mutableStateOf(false) }
    val colorIntensity by animateFloatAsState(
        targetValue = if (isPressed) 1.4f else 1.0f,
        animationSpec = spring(dampingRatio = 0.6f), label = "liquidIntensity"
    )

    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .pointerInput(Unit) { detectTapGestures(onPress = { isPressed = true; tryAwaitRelease(); isPressed = false }) }
            .graphicsLayer {
                // GPU 加速模糊 (API 31+) — 引用 RenderEffect 确保 compileSdk 35 可访问
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    renderEffect = android.graphics.RenderEffect.createBlurEffect(
                        2f * colorIntensity, 2f * colorIntensity,
                        android.graphics.Shader.TileMode.CLAMP
                    ).takeIf { colorIntensity > 1.0f }
                }
            }
            .background(
                Brush.verticalGradient(listOf(
                    Color.White.copy(alpha = 0.06f * colorIntensity),
                    CyberMuted.copy(alpha = 0.4f * colorIntensity),
                    CyberPill.copy(alpha = 0.5f * colorIntensity)
                ))
            )
            .neonBorderGlow(16.dp, isHighlighted = isPressed)
    ) {
        content()
    }
}

// ═══════════════════ 玻璃态水光按钮 ═══════════════════

/**
 * 光感水态风格按钮 — 半透明液态材质 + 霓虹描边 + 弹簧交互缩放
 *
 * 基于 LiquidColorCard 的交互增强版本，适配按钮尺寸和点击反馈。
 * 点击时触发 HapticUtils 触觉反馈 + 弹簧缩放 0.95x。
 */
@Composable
fun NeonGlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isPressed) 0.95f else 1f, spring(dampingRatio = 0.5f), label = "btnScale")
    val ctx = LocalContext.current

    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { isPressed = true; tryAwaitRelease(); isPressed = false },
                    onTap = {
                        HapticUtils.standardTap(ctx)
                        onClick()
                    }
                )
            }
            .graphicsLayer {
                scaleX = scale; scaleY = scale
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    renderEffect = android.graphics.RenderEffect.createBlurEffect(
                        0.5f, 0.5f, android.graphics.Shader.TileMode.CLAMP
                    ).takeIf { isPressed }
                }
            }
            .background(
                Brush.verticalGradient(listOf(
                    Color.White.copy(alpha = 0.08f), CyberPill.copy(alpha = 0.5f),
                    CyberMuted.copy(alpha = 0.5f)
                ))
            )
            .neonBorderGlow(14.dp, isHighlighted = isPressed),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
    }
}

/**
 * 赛博朋克风格渐变进度条 — 霓虹蓝紫渐变
 *
 * @param progress 0f..1f 进度
 * @param modifier Modifier
 */
@Composable
fun NeonProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "neonProgress"
    )

    Canvas(modifier.clip(RoundedCornerShape(4.dp))) {
        val trackH = size.height
        val cornerR = 4.dp.toPx()

        // 暗色轨道
        drawRoundRect(
            color = CyberMuted,
            cornerRadius = CornerRadius(cornerR)
        )

        // 渐变填充
        val fillW = size.width * animatedProgress
        if (fillW > 0f) {
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    listOf(NeonCyan, NeonPurpleBright, NeonMagenta)
                ),
                topLeft = Offset(0f, 0f),
                size = androidx.compose.ui.geometry.Size(fillW, trackH),
                cornerRadius = CornerRadius(cornerR)
            )
            // 扫光线
            drawRoundRect(
                color = Color.White.copy(alpha = 0.15f),
                topLeft = Offset(0f, 0f),
                size = androidx.compose.ui.geometry.Size(fillW.coerceAtMost(trackH), trackH),
                cornerRadius = CornerRadius(cornerR)
            )
        }

        // 未填充区域的刻度线
        if (fillW < size.width - 8f) {
            for (x in (fillW + 16f).toInt()..(size.width - 8f).toInt() step 24) {
                drawLine(
                    Color.White.copy(alpha = 0.04f),
                    Offset(x.toFloat(), trackH * 0.3f),
                    Offset(x.toFloat(), trackH * 0.7f),
                    strokeWidth = 0.6f
                )
            }
        }
    }
}
