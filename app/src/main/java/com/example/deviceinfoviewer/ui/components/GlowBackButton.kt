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
import androidx.compose.ui.draw.drawBehind
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

    // 按压缩小 (弹性反馈)
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
                radius = borderR - 0.6f.dp.toPx(),
                center = Offset(cx, cy),
                style = Stroke(width = 0.8f.dp.toPx())
            )
            // 外层极淡光晕
            drawCircle(
                color = NeonPurple.copy(alpha = pressBorderAlpha * 0.15f),
                radius = borderR + 1f.dp.toPx(),
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
//  视觉特征 (V3):
//  - 9层毛玻璃底座 (恒定亮度+底部黑色强调色 L8)
//  - 三层模糊箭头图标 (soft-focus 景深)
//  - 非对称果冻拉伸 (drag方向 1.8x, 垂直 0.85x)
//  - 5层连续重叠高光弧 (无条纹平滑渐变)
// ═════════════════════════════════════════════════════

/** 拖拽超过此距离视为"取消", 不触发返回 */
private val CANCEL_THRESHOLD_DP = 40f

/** 最大拉伸系数 (拖拽达到阈值时的 scaleX/Y) */
private val MAX_STRETCH = 1.80f

/**
 * 浅色圆形返回按钮 — iOS 26 拖拽交互 + 毛玻璃材质 V3.
 *
 * 与 [GlowBackButton] (暗玻璃霓虹风) 并列为两种可选返回键皮肤.
 *
 * V3 变更 (基于视频帧 f_003/f_015/f_018/f_022/f_030 分析):
 * ① [P0] 拖拽时按钮消失 → 所有层 alpha 不再随 dragProgress 衰减, 反而增强
 *     根因: L2/L5/L6 全部线性衰减 → 拖到一半所有"亮部"减半 → 只剩底色=灰圆圈
 * ② [P0] 高光弧条纹 → 5层连续重叠 drawArc (无分段, 平滑 Gaussian 衰减)
 * ③ [P0] 果冻拉伸不够 → MAX_STRETCH=1.8 + 非对称拉伸公式 (拖向1.8x, 垂直0.85x)
 * ④ [新增] 底部黑色强调色 L8 → 径向渐变 黑(0.55α)→透明
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

    // 取消态淡出 — 仅在极接近阈值(>0.95)时才开始极快衰减
    val cancelAlpha by animateFloatAsState(
        targetValue = if (dragProgress > 0.95f) (1f - (dragProgress - 0.95f) / 0.05f).coerceIn(0.3f, 1f) else 1.0f,
        animationSpec = tween(100, easing = EaseOutCubic),
        label = "cancelAlpha"
    )

    // 取消态缩小 (仅在非常接近阈值时才缩小)
    val cancelScale by animateFloatAsState(
        targetValue = if (dragProgress > 0.90f) 0.80f else 1.0f,
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
        // 果冻拉伸: 非对称橡皮筋 — 拖拽方向拉长到 1.8x, 垂直方向缩到 0.85x
        val stretchFactor = if (isInteracting && dragProgress > 0.05f) {
            dragProgress.coerceIn(0f, 1f)
        } else 0f
        val dragAngleRad = kotlin.math.atan2(dragOffsetY.toDouble(), dragOffsetX.toDouble()).toFloat()
        val cosA = kotlin.math.cos(dragAngleRad.toDouble()).toFloat()
        val sinA = kotlin.math.sin(dragAngleRad.toDouble()).toFloat()
        // 非对称: 拖拽方向 stretch 到 1.8, 垂直方向 shrink 到 0.85
        val stretchX = 1f + (MAX_STRETCH - 1f) * stretchFactor * cosA * cosA + (0.85f - 1f) * stretchFactor * sinA * sinA
        val stretchY = 1f + (MAX_STRETCH - 1f) * stretchFactor * sinA * sinA + (0.85f - 1f) * stretchFactor * cosA * cosA

        // 综合缩放 = 按压 × 拉伸 × 取消 × 回弹
        val finalScaleX = snapBackScale * stretchX.coerceIn(0.75f, MAX_STRETCH) * cancelScale
        val finalScaleY = snapBackScale * stretchY.coerceIn(0.75f, MAX_STRETCH) * cancelScale

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
                    elevation = if (isInteracting) 10.dp else 4.dp,
                    shape = CircleShape,
                    ambientColor = Color.Black.copy(alpha = 0.10f),
                    spotColor = Color.Black.copy(alpha = 0.06f)
                )
                .clip(CircleShape)
                // ══ 9层毛玻璃底座 V3 (恒定亮度+底部黑色强调) ══
                .drawFrostedGlassV3(btnSize, isInteracting, dragProgress)
                .pointerInput(btnSize) {
                    forEachGesture {
                        awaitPointerEventScope {
                            val down = awaitFirstDown()
                            isInteracting = true
                            dragOffsetX = 0f
                            dragOffsetY = 0f

                            scope.launch {
                                try { HapticUtils.standardTap(ctx) } catch (_: Exception) {}
                            }

                            // 拖拽循环 — 跟踪特定 pointerId, 移出边界不退出
                            val pointerId = down.id
                            var released = false
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == pointerId }
                                if (change != null) {
                                    dragOffsetX = (change.position.x - down.position.x)
                                    dragOffsetY = (change.position.y - down.position.y)
                                    if (!change.pressed) released = true
                                }
                            } while (!released)

                            // 松手判定
                            isInteracting = false
                            val totalDist = kotlin.math.sqrt(dragOffsetX * dragOffsetX + dragOffsetY * dragOffsetY)

                            dragOffsetX = 0f
                            dragOffsetY = 0f

                            if (totalDist < thresholdPx) {
                                scope.launch {
                                    delay(20)
                                    onClick()
                                }
                            } else {
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
            // 图标 alpha: 拖拽时不衰减 (配合 V3 恒定亮度策略)
            val iconAlpha = (0.62f + 0.33f * (1f - dragProgress.coerceAtMost(0.7f))).coerceIn(0.40f, 0.95f)

            // 底层: 大幅模糊光晕
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = Color(0xFF1A1A2E).copy(alpha = iconAlpha * 0.14f),
                modifier = Modifier
                    .size(iconSize * 1.40f)
                    .graphicsLayer { translationX = 0.8f; translationY = 0.8f }
            )
            // 中层: 轻微模糊
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = Color(0xFF1A1A2E).copy(alpha = iconAlpha * 0.30f),
                modifier = Modifier
                    .size(iconSize * 1.12f)
                    .graphicsLayer { translationX = 0.4f; translationY = 0.4f }
            )
            // 主层: 锐利焦点
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
                tint = Color(0xFF1A1A2E).copy(alpha = iconAlpha),
                modifier = Modifier.size(iconSize)
            )
        }

        // ══ 拖拽高光弧线 V2 — 玻璃折射感 (细/冷色调/柔和边缘) ══
        // 设计原则: 真玻璃高光特征 = 极细(1~3px) + 低α(0.05~0.40) + 冷色调偏移 + 边缘柔化
        // 旧版错误: 5层纯白/峰值0.95α/最宽28dp → 像粗白漆条/塑料环
        if (isInteracting && dragProgress > 0.08f) {
            Canvas(Modifier.matchParentSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val baseR = minOf(cx, cy) * 0.96f

                val highlightAngle = (dragAngle + 180f) % 360f
                val arcSweep = 80f * dragProgress.coerceAtMost(1f)   // 收窄: 110°→80° 避免包圈
                val intensity = dragProgress.coerceAtMost(1f)

                // 玻璃冷色调 (微青白, 模拟光学折射色散)
                val hlCore = Color(0xFFE8F4FC)  // 冷白 — 核心反光
                val hlGlow = Color(0xFFD0EFFF)  // 微青 — 散射光晕

                // ── Layer 1: 核心极细镜面线 (1.2dp, peak α=0.40) ──
                drawArc(
                    color = hlCore.copy(alpha = 0.40f * intensity),
                    startAngle = highlightAngle - arcSweep / 2f,
                    sweepAngle = arcSweep,
                    useCenter = false,
                    style = Stroke(width = 1.2f.dp.toPx()),
                    topLeft = Offset(cx - baseR, cy - baseR),
                    size = Size(baseR * 2f, baseR * 2f)
                )

                // ── Layer 2: 柔和散射过渡 (3.0dp, α=0.14) ──
                drawArc(
                    color = hlGlow.copy(alpha = 0.14f * intensity),
                    startAngle = highlightAngle - arcSweep / 2f - 5f,
                    sweepAngle = arcSweep + 10f,
                    useCenter = false,
                    style = Stroke(width = 3.0f.dp.toPx()),
                    topLeft = Offset(cx - baseR, cy - baseR),
                    size = Size(baseR * 2f, baseR * 2f)
                )

                // ── Layer 3: 极外环境光晕 (7dp, α=0.04) ──
                drawArc(
                    color = Color.White.copy(alpha = 0.04f * intensity),
                    startAngle = highlightAngle - arcSweep / 2f - 10f,
                    sweepAngle = arcSweep + 20f,
                    useCenter = false,
                    style = Stroke(width = 7.0f.dp.toPx()),
                    topLeft = Offset(cx - baseR, cy - baseR),
                    size = Size(baseR * 2f, baseR * 2f)
                )
            }
        }
    }
}

// ═════════════════════════════════════════════════════
//  毛玻璃底座 V3 — 恒定亮度 + 底部黑色强调色
//
//  核心设计决策: 所有层 alpha 为恒定值, 不随 dragProgress 衰减.
//  拖拽时的视觉变化仅由 graphicsLayer 的 scaleX/Y/translationX/Y 提供.
// ═════════════════════════════════════════════════════

/**
 * 9 层毛玻璃绘制 V3 — 恒定亮度 + 底部黑色强调色.
 *
 * ⚠️ 关键: 使用 drawBehind 而非 drawWithContent, 让玻璃层绘制在
 * 图标内容【之下】(玻璃底座 → 箭头在上), 否则半透明白玻璃会盖住深色
 * 箭头, 导致静态态"太灰/太实/箭头糊" — 这是此前视觉不达标的根因。
 */
private fun Modifier.drawFrostedGlassV3(
    btnSize: Dp,
    isInteracting: Boolean,
    dragProgress: Float,
): Modifier = this.drawBehind {
    val s = btnSize.toPx()
    val cX = s * 0.5f
    val cY = s * 0.5f
    val r = s * 0.5f

    // ══ L0: 内凹阴影 (恒定 14%α) ══
    drawCircle(
        color = Color.Black.copy(alpha = 0.14f),
        radius = r - 0.4f.dp.toPx(), center = Offset(cX, cY),
        style = Stroke(width = 2.8f.dp.toPx())
    )
    drawCircle(
        color = Color.Black.copy(alpha = 0.04f),
        radius = r - 1.0f.dp.toPx(), center = Offset(cX, cY),
        style = Stroke(width = 4.5f.dp.toPx())
    )

    // ══ L1: 玻璃基面 — 冷调半透明 0.34α (拖拽时 +10% 提亮) ══
    // 降低透明度 + 加入冷色调 (F2F6FA), 去除纯白带来的"金属珍珠球"观感
    val baseAlpha = 0.34f + if (isInteracting) dragProgress * 0.10f else 0f
    drawCircle(color = Color(0xFFF2F6FA).copy(alpha = baseAlpha), radius = r, center = Offset(cX, cY))

    // ══ L2: 主光斑 — 左上径向渐变 (冷调, 52%/18%) ══
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFEAF3FB).copy(alpha = 0.52f), Color(0xFFEAF3FB).copy(alpha = 0.18f), Color.Transparent),
            center = Offset(s * 0.28f, s * 0.26f), radius = s * 0.62f
        ), radius = r, center = Offset(cX, cY)
    )

    // ══ L3: 副光斑 — 右侧补光 (恒定 18%) ══
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.18f), Color.Transparent),
            center = Offset(s * 0.72f, s * 0.60f), radius = s * 0.38f
        ), radius = r, center = Offset(cX, cY)
    )

    // ══ L4: 冷白色调叠加 (恒定 7%) ══
    drawCircle(color = Color(0xFFFAFCFF).copy(alpha = 0.07f), radius = r, center = Offset(cX, cY))

    // ══ L5: 顶部镜面高光 (冷调, 30% + 10% 光晕) ══
    // 旧版纯白 72%/22% 过亮 → 按钮呈"金属珍珠球"观感; 改冷调 + 大幅降透明
    drawArc(
        color = Color(0xFFE8F4FC).copy(alpha = 0.30f), startAngle = 200f, sweepAngle = 150f,
        useCenter = false, style = Stroke(width = 0.8f.dp.toPx()),
        topLeft = Offset(s * 0.05f, s * 0.04f), size = Size(s * 0.90f, s * 0.90f)
    )
    drawArc(
        color = Color(0xFFD0EFFF).copy(alpha = 0.10f), startAngle = 195f, sweepAngle = 165f,
        useCenter = false, style = Stroke(width = 2.0f.dp.toPx()),
        topLeft = Offset(s * 0.02f, s * 0.01f), size = Size(s * 0.96f, s * 0.96f)
    )

    // ══ L6: 渐变描边 rim light (恒定 60%/32% 上, 14% 下) ══
    drawArc(
        brush = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.60f), Color.White.copy(alpha = 0.32f)),
            start = Offset(cX, 0f), end = Offset(cX, s)),
        startAngle = 145f, sweepAngle = 150f, useCenter = false,
        style = Stroke(width = 0.6f.dp.toPx()),
        topLeft = Offset(s * 0.03f, s * 0.03f), size = Size(s * 0.94f, s * 0.94f)
    )
    drawArc(
        color = Color.White.copy(alpha = 0.14f), startAngle = 295f, sweepAngle = 130f,
        useCenter = false, style = Stroke(width = 0.6f.dp.toPx()),
        topLeft = Offset(s * 0.03f, s * 0.03f), size = Size(s * 0.94f, s * 0.94f)
    )

    // ══ L7: 底部暗角弧 (恒定 8%/3%) ══
    drawArc(
        color = Color.Black.copy(alpha = 0.08f), startAngle = 100f, sweepAngle = 140f,
        useCenter = false, style = Stroke(width = 3.5f.dp.toPx()),
        topLeft = Offset(s * 0.04f, s * 0.04f), size = Size(s * 0.92f, s * 0.92f)
    )
    drawArc(
        color = Color.Black.copy(alpha = 0.03f), startAngle = 90f, sweepAngle = 160f,
        useCenter = false, style = Stroke(width = 6.0f.dp.toPx()),
        topLeft = Offset(0f, 0f), size = Size(s, s)
    )

    // ══ L8: 底部黑色强调色区域 (新增!) ══
    // 从底部中心向上径向渐变: 黑(55%α)→灰(20%α)→透明
    // 营造深色托底效果, 让按钮有明确的"底部重量感"
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.Black.copy(alpha = 0.55f),
                Color.Black.copy(alpha = 0.20f),
                Color.Transparent,
            ),
            center = Offset(cX, s * 0.82f),
            radius = s * 0.50f
        ),
        radius = r,
        center = Offset(cX, cY)
    )
}

// ═════════════════════════════════════════════════════
//  通用玻璃圆底按钮 — Tab栏/工具栏用
//
//  复用 drawFrostedGlassV3 的 9 层毛玻璃底座,
//  但无拖拽/果冻交互 — 纯点击 + 弹簧缩放反馈.
//  与 LightCircleBackButton 视觉一致, 用于非返回键场景.
// ═════════════════════════════════════════════════════

/**
 * 通用玻璃圆底按钮.
 *
 * 视觉特征 (与 LightCircleBackButton V3 一致):
 * - 9 层毛玻璃底座 (drawFrostedGlassV3, 恒定亮度 + 底部黑色强调)
 * - 冷调高光 (非金属感)
 * - 点击弹簧缩放 0.88x + 触觉反馈
 *
 * @param content 按钮图标内容 (居中绘制在玻璃底座之上)
 * @param onClick 点击回调
 * @param modifier 外部 Modifier
 * @param btnSize 按钮直径, 默认 36.dp (Tab 栏场景)
 * @param contentDescription 无障碍描述
 */
@Composable
fun GlassCircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    btnSize: Dp = 36.dp,
    contentDescription: String? = null,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    var isPressed by remember { mutableStateOf(false) }

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "glassBtnPress"
    )

    Box(
        modifier = modifier
            .size(btnSize)
            .scale(pressScale)
            .shadow(
                elevation = 4.dp,
                shape = CircleShape,
                ambientColor = Color.Black.copy(alpha = 0.40f),
                spotColor = Color.Black.copy(alpha = 0.25f)
            )
            .clip(CircleShape)
            // ══ 9 层毛玻璃底座 (复用 V3, 静态态) ══
            .drawFrostedGlassV3(btnSize, isInteracting = false, dragProgress = 0f)
            .pointerInput(Unit) {
                forEachGesture {
                    awaitPointerEventScope {
                        // 1) 按下: 仅记录起点 + 视觉反馈, 不触发任何逻辑
                        val down = awaitFirstDown()
                        isPressed = true

                        // 2) 跟踪指针直至松手: 拖动过程完全不响应 (即使移出按钮边界)
                        val pointerId = down.id
                        var released = false
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId }
                            if (change != null && !change.pressed) released = true
                        } while (!released)

                        // 3) 松开: 这是唯一触发 onClick 的时机
                        isPressed = false
                        scope.launch {
                            try { HapticUtils.standardTap(ctx) } catch (_: Exception) {}
                            onClick()
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // 图标内容 (绘制在玻璃底座之上)
        content()
    }
}
