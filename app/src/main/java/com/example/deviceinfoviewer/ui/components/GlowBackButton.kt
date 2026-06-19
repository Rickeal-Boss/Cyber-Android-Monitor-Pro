package com.example.deviceinfoviewer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Icon
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.deviceinfoviewer.HapticUtils
import com.example.deviceinfoviewer.R
import com.example.deviceinfoviewer.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 暗玻璃质感返回按钮 — 对齐参考图 箭头.jpg
 *
 * 视觉特征:
 * - 深色圆形底座 (#18182A → #0A0A0F 径向渐变, 模拟 iOS 风格暗玻璃)
 * - 极细浅色描边圈 (呼吸脉冲, 0.5dp ~ 1dp 动态宽度)
 * - 白色粗体 Chevron 图标 (<), 非 Unicode 文字
 * - 点击: 弹簧缩放 0.88x + 涟漪扩散 + 描边增亮
 * - 左上角微弱高光弧线 (玻璃反光感)
 */
@Composable
fun GlowBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    btnSize: Dp = 40.dp,
) {
    // ── 动画状态 ──
    var isPressed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    // 微妙呼吸: 描边透明度缓慢起伏 (idle 状态下的"活着"提示)
    val infiniteTransition = rememberInfiniteTransition(label = "breath")
    val breathAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathAlpha"
    )
    // 描边微微扩张/收缩
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathScale"
    )

    // 按压缩放 (弹性反馈)
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1.0f,
        animationSpec = spring(
            dampingRatio = 0.5f,    // Spring.DampingRatioMediumBouncy
            stiffness = 1500f       // Spring.StiffnessMediumHigh
        ),
        label = "pressScale"
    )

    // 按压时描边增亮
    val pressBorderAlpha by animateFloatAsState(
        targetValue = if (isPressed) 1.0f else breathAlpha,
        animationSpec = tween(120, easing = EaseOutCubic),
        label = "pressBorder"
    )

    // 按压时背景提亮
    val pressBgLighten by animateFloatAsState(
        targetValue = if (isPressed) 0.15f else 0f,
        animationSpec = tween(100),
        label = "pressBg"
    )

    // 涟漪队列 — 使用 snapshot 安全写入，避免 Canvas draw 中直接写状态导致 IllegalStateException
    var ripples by remember { mutableStateOf(listOf<RippleData>()) }
    // 每次重组时清理过期涟漪（取代在 draw lambda 中写状态）
    LaunchedEffect(ripples) {
        if (ripples.isEmpty()) return@LaunchedEffect
        delay(400)
        ripples = ripples.filter { System.currentTimeMillis() - it.startTime < 400 }
    }

    // ── Chevron 矢量图标 (白色粗体 <) ──
    val chevronIcon = remember {
        ImageVector.Builder(
            name = "ChevronLeft",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = androidx.compose.ui.graphics.SolidColor(Color.White),
                stroke = null,
                strokeLineWidth = 0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
                pathFillType = androidx.compose.ui.graphics.PathFillType.NonZero
            ) {
                moveTo(15.41f, 7.41f)
                lineTo(14f, 6f)
                lineTo(8f, 12f)
                lineTo(14f, 18f)
                lineTo(15.41f, 16.59f)
                lineTo(10.83f, 12f)
                close()
            }
        }.build()
    }

    Box(
        modifier = modifier
            .size(btnSize)
            .scale(pressScale)
            .shadow(
                elevation = 4.dp,
                shape = CircleShape,
                ambientColor = Color.Black.copy(alpha = 0.5f),
                spotColor = Color.Black.copy(alpha = 0.3f)
            )
            .clip(CircleShape)
            // 深色径向渐变底座 (模拟暗玻璃质感)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        CyberElevated.copy(alpha = 0.95f + pressBgLighten),
                        CyberBackground.copy(alpha = 0.98f)
                    ),
                    center = Offset(0.35f, 0.35f)
                )
            )
            .drawWithGlassReflection(btnSize)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        ripples = ripples + RippleData(System.currentTimeMillis())
                        tryAwaitRelease()
                        isPressed = false
                        scope.launch {
                            delay(40)
                            try { HapticUtils.standardTap(ctx) } catch (_: Exception) {}
                            onClick()
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // ── Canvas 层: 描边 + 涟漪 ──
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val baseR = minOf(cx, cy)

            // ── 主描边圈 (浅紫灰, 呼吸效果) ──
            val borderR = baseR * breathScale
            drawCircle(
                color = NeonSteelBlue.copy(alpha = pressBorderAlpha * 0.6f),
                radius = borderR - 0.6.dp.toPx(),
                center = Offset(cx, cy),
                style = Stroke(width = 0.8f.dp.toPx())
            )
            // 外层极淡光晕
            drawCircle(
                color = NeonPurple.copy(alpha = pressBorderAlpha * 0.15f),
                radius = borderR + 1.dp.toPx(),
                center = Offset(cx, cy),
                style = Stroke(width = 0.5f.dp.toPx())
            )

            // ── 点击涟漪扩散 (从中心向外扩散后消失) ──
            val now = System.currentTimeMillis()
            ripples.forEach { ripple ->
                val elapsed = now - ripple.startTime
                val progress = (elapsed / 350f).coerceIn(0f, 1f)
                if (progress < 1f) {
                    // 涟漪圆环: 从中心扩散到边缘外
                    val rippleR = baseR * 0.3f + baseR * 1.8f * progress
                    val rippleAlpha = (1f - progress).coerceAtLeast(0f) * 0.35f
                    drawCircle(
                        color = NeonPurpleBright.copy(alpha = rippleAlpha),
                        radius = rippleR.coerceAtMost(baseR * 2f),
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.8f.dp.toPx() * (1f - progress))
                    )
                }
            }
        }

        // ── Chevron 图标 ──
        Icon(
            painter = rememberVectorPainter(chevronIcon),
            contentDescription = stringResource(R.string.common_back),
            tint = Color.White.copy(alpha = if (isPressed) 0.5f else 0.92f),
            modifier = Modifier.size(btnSize * 0.45f)
        )
    }
}

// ── 玻璃反光: 左上角微弱白色弧线 ──
private fun Modifier.drawWithGlassReflection(btnSize: Dp): Modifier =
    this.drawWithContent {
        drawContent()
        val s = btnSize.toPx()
        // 高光弧 (左上角, 模拟玻璃曲面反光)
        drawArc(
            color = Color.White.copy(alpha = 0.06f),
            startAngle = 200f,
            sweepAngle = 110f,
            useCenter = false,
            style = Stroke(width = s * 0.10f),
            topLeft = Offset(s * 0.14f, s * 0.14f),
            size = Size(s * 0.52f, s * 0.52f)
        )
    }

// ── 涟漪数据 ──
private data class RippleData(val startTime: Long)
