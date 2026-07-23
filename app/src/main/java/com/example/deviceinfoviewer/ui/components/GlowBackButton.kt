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
                    elevation = if (isInteracting) 8.dp else 4.dp,
                    shape = CircleShape,
                    ambientColor = Color.Black.copy(alpha = 0.10f),
                    spotColor = Color.Black.copy(alpha = 0.06f)
                )
                .clip(CircleShape)
                // ══ 7层毛玻璃底座 (含手绘内阴影) ══
                .drawFrostedGlassV2(btnSize, isInteracting, dragProgress)
                .pointerInput(btnSize) {
                    forEachGesture {
                        awaitPointerEventScope {
                            // 等待按下
                            val down = awaitFirstDown()
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
            // ══ 模糊质感箭头 (三层错位绘制模拟 soft-focus / 景深) ══
            val iconSize = btnSize * 0.44f
            // 整体图标随拖拽淡出
            val iconAlpha = (0.58f + 0.42f * (1f - dragProgress)).coerceIn(0.30f, 0.95f)

            // 底层: 大幅模糊光晕 (最外层, 模拟透镜散射)
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = Color(0xFF1A1A2E).copy(alpha = iconAlpha * 0.12f),
                modifier = Modifier
                    .size(iconSize * 1.40f)
                    .graphicsLayer { translationX = 0.8f; translationY = 0.8f }
            )
            // 中层: 轻微模糊 (soft-focus 层, 主要体积感来源)
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = Color(0xFF1A1A2E).copy(alpha = iconAlpha * 0.28f),
                modifier = Modifier
                    .size(iconSize * 1.12f)
                    .graphicsLayer { translationX = 0.4f; translationY = 0.4f }
            )
            // 主层: 锐利焦点 (标准尺寸, 清晰可读)
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
                tint = Color(0xFF1A1A2E).copy(alpha = iconAlpha),
                modifier = Modifier.size(iconSize)
            )
        }

        // ══ 拖拽高光弧线 (jelly highlight — 拉伸侧的镜面折射光带) ══
        if (isInteracting && dragProgress > 0.08f) {
            Canvas(Modifier.matchParentSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val baseR = minOf(cx, cy) * 0.96f

                // 高光弧在拖拽反方向 (被"拉开"的一侧玻璃变薄→高光增强)
                val highlightAngle = (dragAngle + 180f) % 360f
                val arcSweep = 75f * dragProgress.coerceAtMost(1f)   // 拖越远弧越长
                val arcIntensity = dragProgress.coerceAtMost(1f)

                // ── 核心高光带 (亮白, 窄) ──
                drawArc(
                    color = Color.White.copy(alpha = 0.72f * arcIntensity),
                    startAngle = highlightAngle - arcSweep / 2f,
                    sweepAngle = arcSweep,
                    useCenter = false,
                    style = Stroke(width = 1.6f.dp.toPx() * (1f + arcIntensity * 0.4f)),
                    topLeft = Offset(cx - baseR, cy - baseR),
                    size = Size(baseR * 2f, baseR * 2f)
                )
                // ── 中层散射 (稍宽, 更淡) ──
                drawArc(
                    color = Color.White.copy(alpha = 0.30f * arcIntensity),
                    startAngle = highlightAngle - arcSweep / 2f - 6f,
                    sweepAngle = arcSweep + 12f,
                    useCenter = false,
                    style = Stroke(width = 3.5f.dp.toPx()),
                    topLeft = Offset(cx - baseR, cy - baseR),
                    size = Size(baseR * 2f, baseR * 2f)
                )
                // ── 外层极淡光晕 (最宽, 营造"发光"感) ──
                drawArc(
                    color = Color.White.copy(alpha = 0.10f * arcIntensity),
                    startAngle = highlightAngle - arcSweep / 2f - 14f,
                    sweepAngle = arcSweep + 28f,
                    useCenter = false,
                    style = Stroke(width = 6.0f.dp.toPx()),
                    topLeft = Offset(cx - baseR, cy - baseR),
                    size = Size(baseR * 2f, baseR * 2f)
                )
            }
        }
    }
}

// ═════════════════════════════════════════════════════
//  毛玻璃底座 V2 — iOS 26 Liquid Glass 多层深度模型
//
//  渲染层级 (drawWithContent 内, drawContent() 之后):
//  L1. 玻璃基面 — 半透明白 (0.42α, 非实心)
//  L2. 主光斑 — 左上角径向渐变 (模拟曲面反光/光源)
//  L3. 副光斑 — 右下角微弱补光 (增加体积感)
//  L4. 色调叠加 — 极淡暖白 tint (统一层间色温)
//  L5. 顶部镜面高光 — 1dp 亮白内沿线 (specular highlight)
//  L6. 渐变描边 — 上亮(55%α) → 下暗(12%α) (rim light)
//  L7. 底部暗角弧 — 微弱深色内弧 (增强凹陷感)
//
//  参考来源:
//  - liquid-glass (NadeemIqbal): blur+tint+sheen+refraction
//  - android-design-system-skills: mesh + innerHighlight + gradient border
//  - CSS Liquid Glass: backdrop-filter + inset box-shadow multi-layer
//  - Apple HIG Materials: reflection/refraction/shadow/highlight
// ═════════════════════════════════════════════════════

/**
 * 7 层深度毛玻璃绘制.
 *
 * @param btnSize 按钮直径
 * @param isInteracting 是否在交互中(按下/拖拽)
 * @param dragProgress 归一化拖距 [0..1], 1=达到取消阈值
 */
private fun Modifier.drawFrostedGlassV2(
    btnSize: Dp,
    isInteracting: Boolean,
    dragProgress: Float,
): Modifier = this.drawWithContent {
    drawContent()

    val s = btnSize.toPx()
    val cX = s * 0.5f
    val cY = s * 0.5f
    val r = s * 0.5f
    // 交互时整体提亮 (按下态玻璃"变薄"更透光)
    val interactLift = if (isInteracting) dragProgress * 0.08f else 0f

    // ══ L1: 玻璃基面 — 半透明白 (核心: 不透明度从 0.88 降至 0.42) ══
    // ══ L0: 内凹阴影 (全周, 模拟玻璃被"压入"表面 — 替代 Modifier.innerShadow) ══
    // 用沿内边缘的深色 Stroke 模拟光线在凹陷处的遮挡
    val innerShadowAlpha = 0.13f * (1f - dragProgress * 0.5f)
    drawCircle(
        color = Color.Black.copy(alpha = innerShadowAlpha),
        radius = r - 0.3f.dp.toPx(),
        center = Offset(cX, cY),
        style = Stroke(width = 2.5f.dp.toPx())
    )
    // 内阴影柔化层 (更宽更淡, 避免硬边)
    drawCircle(
        color = Color.Black.copy(alpha = innerShadowAlpha * 0.35f),
        radius = r - 0.8f.dp.toPx(),
        center = Offset(cX, cY),
        style = Stroke(width = 4.0f.dp.toPx())
    )

    // ══ L1: 玻璃基面 — 半透明白 (核心: 不透明度从 0.88 降至 0.42) ══
    drawCircle(
        color = Color.White.copy(alpha = 0.42f + interactLift),
        radius = r,
        center = Offset(cX, cY)
    )

    // ══ L2: 主光斑 — 左上角径向渐变 (光源模拟, 制造曲面凸起错觉) ══
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.52f - dragProgress * 0.20f),  // 中心最亮
                Color.White.copy(alpha = 0.18f),                          // 中间过渡
                Color.Transparent,                                        // 边缘透明
            ),
            center = Offset(s * 0.30f, s * 0.28f),
            radius = s * 0.60f
        ),
        radius = r,
        center = Offset(cX, cY)
    )

    // ══ L3: 副光斑 — 右侧微弱补光 (打破对称, 增加真实体积感) ══
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.15f),
                Color.Transparent,
            ),
            center = Offset(s * 0.70f, s * 0.58f),
            radius = s * 0.35f
        ),
        radius = r,
        center = Offset(cX, cY)
    )

    // ══ L4: 色调叠加 — 极淡冷白 tint (统一层间, 模拟玻璃厚度) ══
    drawCircle(
        color = Color(0xFFFAFCFF).copy(alpha = 0.06f),  // 极淡蓝白
        radius = r,
        center = Offset(cX, cY)
    )

    // ══ L5: 顶部镜面高光 — 1dp 内沿亮线 (specular highlight, 最关键深度线索) ══
    val highlightAlpha = (0.55f - dragProgress * 0.30f).coerceAtLeast(0.08f)
    // 用弧线模拟顶部边缘 caught light
    drawArc(
        color = Color.White.copy(alpha = highlightAlpha),
        startAngle = 200f,       // 左上起始
        sweepAngle = 160f,      // 覆盖顶部大半圆弧
        useCenter = false,
        style = Stroke(width = 1.0f.dp.toPx()),
        topLeft = Offset(s * 0.05f, s * 0.04f),
        size = Size(s * 0.90f, s * 0.90f)
    )
    // 高光外侧极淡光晕 (光散射)
    drawArc(
        color = Color.White.copy(alpha = highlightAlpha * 0.25f),
        startAngle = 195f,
        sweepAngle = 170f,
        useCenter = false,
        style = Stroke(width = 2.8f.dp.toPx()),
        topLeft = Offset(s * 0.02f, s * 0.01f),
        size = Size(s * 0.96f, s * 0.96f)
    )

    // ══ L6: 渐变描边 — rim light (上亮下暗, 模拟环境光从上方照射) ══
    // 上半圈: 明亮描边
    drawArc(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.55f - dragProgress * 0.25f),   // 顶部最亮
                Color.White.copy(alpha = 0.30f - dragProgress * 0.10f),   // 两侧过渡
            ),
            start = Offset(cX, 0f),
            end = Offset(cX, s)
        ),
        startAngle = 145f,
        sweepAngle = 150f,      // 覆盖顶部到两侧
        useCenter = false,
        style = Stroke(width = 0.6f.dp.toPx()),
        topLeft = Offset(s * 0.03f, s * 0.03f),
        size = Size(s * 0.94f, s * 0.94f)
    )
    // 下半圈: 暗淡描边 (几乎融入背景)
    drawArc(
        color = Color.White.copy(alpha = 0.12f - dragProgress * 0.06f),
        startAngle = 295f,
        sweepAngle = 130f,
        useCenter = false,
        style = Stroke(width = 0.6f.dp.toPx()),
        topLeft = Offset(s * 0.03f, s * 0.03f),
        size = Size(s * 0.94f, s * 0.94f)
    )

    // ══ L7: 底部暗角弧 — 内凹加深 (配合 innerShadow 增强凹陷体积) ══
    if (!isInteracting || dragProgress < 0.6f) {
        val bottomShadowAlpha = 0.07f * (1f - dragProgress)
        drawArc(
            color = Color.Black.copy(alpha = bottomShadowAlpha),
            startAngle = 100f,
            sweepAngle = 140f,     // 覆盖底部大弧
            useCenter = false,
            style = Stroke(width = 3.0f.dp.toPx()),
            topLeft = Offset(s * 0.04f, s * 0.04f),
            size = Size(s * 0.92f, s * 0.92f)
        )
        // 更柔和的外层暗角
        drawArc(
            color = Color.Black.copy(alpha = bottomShadowAlpha * 0.4f),
            startAngle = 90f,
            sweepAngle = 160f,
            useCenter = false,
            style = Stroke(width = 5.0f.dp.toPx()),
            topLeft = Offset(0f, 0f),
            size = Size(s, s)
        )
    }
}
