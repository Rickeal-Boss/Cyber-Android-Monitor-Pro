package com.example.deviceinfoviewer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.deviceinfoviewer.HapticUtils
import com.example.deviceinfoviewer.R
import com.example.deviceinfoviewer.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
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
    // ★ snapshotFlow: 单长期协程替代 LaunchedEffect(ripples) 会随每次点击不断重启的问题
    LaunchedEffect(Unit) {
        snapshotFlow { ripples }
            .filter { it.isNotEmpty() }
            .collect {
                delay(400)
                ripples = ripples.filter { r -> System.currentTimeMillis() - r.startTime < 400 }
            }
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

// ═════════════════════════════════════════════════════
//  浅色圆形返回按钮 — WorkBuddy Android / iOS 26 风格
//
//  交互模型 (仿 iOS 26 返回键):
//  - 按下 → 记录起点, 进入交互态
//  - 拖动 → 按钮随拖拽方向"果冻拉长", 显示高光边缘
//  - 拖动超过阈值 (40dp) → 进入"取消态" (按钮淡出 + 缩小)
//  - 松手:
//    · 拖距 < 阈值 → 触发返回 (spring 回弹 + onClick)
//    · 拖距 ≥ 阈值 → 取消 (spring 弹回原位, 不触发)
//
//  视觉特征:
//  - 毛玻璃底座 (多层半透明白 + 内阴影 + 极细亮边)
//  - 模糊质感箭头图标 (双层错位绘制模拟 soft-focus)
//  - 拖拽时非对称拉伸 (drag方向 elongate ~1.35x)
//  - 拉伸侧高光弧线 (jelly highlight)
// ═════════════════════════════════════════════════════

/** 拖拽超过此距离视为"取消", 不触发返回 */
private val CANCEL_THRESHOLD_DP = 40f

/** 最大拉伸系数 (拖拽达到阈值时的 scaleX/Y) */
private val MAX_STRETCH = 1.35f

/**
 * 浅色圆形返回按钮 — iOS 26 拖拽交互 + 毛玻璃材质。
 *
 * 与 [GlowBackButton] (暗玻璃霓虹风) 并列为两种可选返回键皮肤。
 *
 * @param onClick 仅在按下后松手且未拖出阈值时触发
 * @param modifier 外部 Modifier
 * @param btnSize 按钮直径, 默认 40.dp (覆盖层场景传 48.dp)
 */
@Composable
fun LightCircleBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    btnSize: Dp = 40.dp,
) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    // ── 交互状态 ──
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var isInteracting by remember { mutableStateOf(false) }

    // 归一化拖距 [0..1], 1 = 达到取消阈值
    val dragProgress = remember(dragOffsetX, dragOffsetY) {
        kotlin.math.sqrt(dragOffsetX * dragOffsetX + dragOffsetY * dragOffsetY)
    }.let { raw ->
        (raw / CANCEL_THRESHOLD_DP).coerceIn(0f, 1f)
    }

    // ── 动画状态 ──

    // 基础按压缩小 (按下瞬间)
    val pressScale by animateFloatAsState(
        targetValue = if (isInteracting && dragProgress < 0.05f) 0.92f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "pressScale"
    )

    // 取消态淡出 (拖距接近/超过阈值)
    val cancelAlpha by animateFloatAsState(
        targetValue = 1f - dragProgress.coerceAtMost(1f),
        animationSpec = tween(150, easing = EaseOutCubic),
        label = "cancelAlpha"
    )

    // 取消态缩小
    val cancelScale by animateFloatAsState(
        targetValue = if (dragProgress > 0.8f) 0.85f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "cancelScale"
    )

    // 松手回弹 (非取消态时迅速归零)
    val snapBackScale by animateFloatAsState(
        targetValue = if (!isInteracting) 1.0f else pressScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "snapBack"
    )

    val density = LocalDensity.current
    val thresholdPx = remember(density) { with(density) { CANCEL_THRESHOLD_DP.dp.toPx() } }

    Box(
        modifier = modifier.size(btnSize),
        contentAlignment = Alignment.Center
    ) {
        // ── 按钮本体 (受拖拽影响变形) ──
        val stretchX = if (isInteracting && dragProgress > 0.05f) {
            1f + (MAX_STRETCH - 1f) * dragProgress * (kotlin.math.abs(dragOffsetX) /
                kotlin.math.max(1f, kotlin.math.sqrt(dragOffsetX * dragOffsetX + dragOffsetY * dragOffsetY)))
        } else 1f
        val stretchY = if (isInteracting && dragProgress > 0.05f) {
            1f + (MAX_STRETCH - 1f) * dragProgress * (kotlin.math.abs(dragOffsetY) /
                kotlin.math.max(1f, kotlin.math.sqrt(dragOffsetX * dragOffsetX + dragOffsetY * dragOffsetY)))
        } else 1f

        // 综合缩放 = 按压 × 拉伸 × 取消 × 回弹
        val finalScaleX = snapBackScale * stretchX.coerceIn(0.8f, MAX_STRETCH) * cancelScale
        val finalScaleY = snapBackScale * stretchY.coerceIn(0.8f, MAX_STRETCH) * cancelScale

        // 拖拽方向角度 (用于高光弧位置)
        val dragAngle = remember(dragOffsetX, dragOffsetY) {
            if (dragOffsetX == 0f && dragOffsetY == 0f) 0f
            else kotlin.math.atan2(dragOffsetY, dragOffsetX).toFloat() * (180f / kotlin.math.PI.toFloat())
        }

        Box(
            modifier = Modifier
                .size(btnSize)
                .graphicsLayer {
                    scaleX = finalScaleX
                    scaleY = finalScaleY
                    alpha = cancelAlpha
                    translationX = dragOffsetX * 0.25f * dragProgress
                    translationY = dragOffsetY * 0.25f * dragProgress
                }
                .shadow(
                    elevation = if (isInteracting) 6.dp else 3.dp,
                    shape = CircleShape,
                    ambientColor = Color.Black.copy(alpha = 0.12f),
                    spotColor = Color.Black.copy(alpha = 0.08f)
                )
                .clip(CircleShape)
                // ══ 毛玻璃底座 (三层叠加) ══
                .drawFrostedGlassBackground(btnSize, isInteracting, dragProgress)
                .pointerInput(btnSize) {
                    forEachGesture {
                        awaitPointerEventScope {
                            // 等待按下
                            val down = awaitFirstDown(pass = false)
                            isInteracting = true
                            dragOffsetX = 0f
                            dragOffsetY = 0f

                            // 触觉: 轻触反馈
                            scope.launch {
                                try { HapticUtils.standardTap(ctx) } catch (_: Exception) {}
                            }

                            // 拖拽循环
                            do {
                                val event = awaitPointerEvent()
                                val pos = event.changes.firstOrNull()?.position ?: continue
                                dragOffsetX = (pos.x - down.position.x)
                                dragOffsetY = (pos.y - down.position.y)
                            } while (
                                event.changes.any { it.pressed } &&
                                kotlin.math.sqrt(dragOffsetX * dragOffsetX + dragOffsetY * dragOffsetY) < thresholdPx * 2.5f
                            )

                            // 松手判定
                            isInteracting = false
                            val totalDist = kotlin.math.sqrt(dragOffsetX * dragOffsetX + dragOffsetY * dragOffsetY)

                            // 清零偏移 (动画会平滑回弹)
                            dragOffsetX = 0f
                            dragOffsetY = 0f

                            if (totalDist < thresholdPx) {
                                // ✓ 未超出阈值 → 触发返回
                                scope.launch {
                                    delay(20)
                                    onClick()
                                }
                            } else {
                                // ✗ 超出阈值 → 已取消, 触觉提示
                                scope.launch {
                                    try { HapticUtils.lightTap(ctx) } catch (_: Exception) {}
                                }
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // ══ 模糊质感箭头 (双层错位绘制模拟 soft-focus) ══
            val iconSize = btnSize * 0.46f
            val iconAlpha = (0.5f + 0.5f * (1f - dragProgress)).coerceIn(0.35f, 0.9f)

            // 底层: 略大 + 更淡 (光晕/模糊层)
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = Color(0xFF1A1A2E).copy(alpha = iconAlpha * 0.30f),
                modifier = Modifier
                    .size(iconSize * 1.18f)
                    .graphicsLayer { translationX = 0.4f; translationY = 0.4f }
            )
            // 主层: 标准尺寸
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
                tint = Color(0xFF1A1A2E).copy(alpha = iconAlpha),
                modifier = Modifier.size(iconSize)
            )
        }

        // ══ 拖拽高光弧线 (jelly highlight, 按钮上层) ══
        if (isInteracting && dragProgress > 0.10f) {
            Canvas(Modifier.matchParentSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val baseR = minOf(cx, cy) * 0.95f

                // 高光弧在拖拽反方向 (被"拉开"的一侧)
                val highlightAngle = (dragAngle + 180f) % 360f
                val arcSweep = 70f * dragProgress.coerceAtMost(1f)  // 拖越远弧越长
                val arcWidth = 1.8f.dp.toPx() * (1f + dragProgress * 0.5f)

                drawArc(
                    color = Color.White.copy(alpha = 0.65f * dragProgress),
                    startAngle = highlightAngle - arcSweep / 2f,
                    sweepAngle = arcSweep,
                    useCenter = false,
                    style = Stroke(width = arcWidth),
                    topLeft = Offset(cx - baseR, cy - baseR),
                    size = Size(baseR * 2f, baseR * 2f)
                )
                // 外层更淡的光晕
                drawArc(
                    color = Color.White.copy(alpha = 0.25f * dragProgress),
                    startAngle = highlightAngle - arcSweep / 2f - 8f,
                    sweepAngle = arcSweep + 16f,
                    useCenter = false,
                    style = Stroke(width = arcWidth * 2.2f),
                    topLeft = Offset(cx - baseR, cy - baseR),
                    size = Size(baseR * 2f, baseR * 2f)
                )
            }
        }
    }
}

// ═════════════════════════════════════════════════════
//  毛玻璃底座绘制 (Modifier.drawWithContent 扩展)
// ═════════════════════════════════════════════════════

/** 三层毛玻璃: 底色 → 内渐变 → 极细亮边 + 内阴影 */
private fun Modifier.drawFrostedGlassBackground(
    btnSize: Dp,
    isInteracting: Boolean,
    dragProgress: Float
): Modifier = this.drawWithContent {
    drawContent()

    val s = btnSize.toPx()

    // 第1层: 半透明白底 (主背景)
    drawCircle(
        color = Color.White.copy(alpha = 0.88f - dragProgress * 0.15f),
        radius = s * 0.50f,
        center = Offset(s * 0.5f, s * 0.5f)
    )

    // 第2层: 径向微渐变 (左上角稍亮, 模拟曲面反光)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.35f),
                Color.Transparent,
            ),
            center = Offset(s * 0.32f, s * 0.32f),
            radius = s * 0.55f
        ),
        radius = s * 0.50f,
        center = Offset(s * 0.5f, s * 0.5f)
    )

    // 第3层: 极细亮边描边 (1px, 模拟玻璃边缘折射)
    drawCircle(
        color = Color.White.copy(alpha = 0.55f - dragProgress * 0.30f),
        radius = s * 0.50f - 0.5f.dp.toPx(),
        center = Offset(s * 0.5f, s * 0.5f),
        style = Stroke(width = 0.75f.dp.toPx())
    )

    // 内侧微弱阴影 (底部深色弧, 增立体感)
    if (!isInteracting || dragProgress < 0.5f) {
        val innerShadowAlpha = 0.06f * (1f - dragProgress)
        drawArc(
            color = Color.Black.copy(alpha = innerShadowAlpha),
            startAngle = 120f,
            sweepAngle = 120f,
            useCenter = false,
            style = Stroke(width = 2.2f.dp.toPx()),
            topLeft = Offset(s * 0.06f, s * 0.06f),
            size = Size(s * 0.88f, s * 0.88f)
        )
    }
}
