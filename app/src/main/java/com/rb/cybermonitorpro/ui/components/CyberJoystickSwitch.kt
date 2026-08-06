package com.rb.cybermonitorpro.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rb.cybermonitorpro.HapticUtils
import com.rb.cybermonitorpro.ui.theme.*
import kotlinx.coroutines.launch          // ★ 修复 2026-08-06: scope.launch 需显式 import, cb8b186 漏配导致编译失败
import kotlin.math.PI
import kotlin.math.sin

// ════════════════════════════════════════════════════════════════
//  CyberJoystickSwitch — 赛博摇杆式开关
//
//  功能: 标准 on/off 二值开关 (替换 Material3 Switch)
//  动画: 弧线轨迹 + 3D 倾斜 + 弹簧过冲 + 光晕拖尾 + 比例脉冲
//  轨道: Komi Store 风格斜切平行四边形
//
//  初审修正 (2026-08-05):
//  • trackHeight 28→32dp, thumbR 0.42→0.40 (与 M3 同尺寸, 上下边距 3.2dp)
//  • arcHeight 6→4dp, maxTilt 18→24° (弧线更收敛, 倾斜更夸张)
//  • skewRatio 0.22→0.15 (轻度斜切, 不与 app 圆形语言冲突)
//  • 48dp 最小触控热区 (无障碍)
//  • 关态轨道可见性提升 (描边 α 0.55→0.65)
//  • ON 态内部色 → 深粉 (NeonDeepPink)
//
//  性能架构: State<Float> progress 仅在 lambda 中读 .value → 零重组
//  ════════════════════════════════════════════════════════════════

/**
 * 赛博摇杆式开关
 *
 * @param checked 当前开关状态
 * @param onCheckedChange 状态变化回调
 * @param modifier 外部修饰符
 * @param trackWidth 轨道宽度 (默认 52dp, 与 M3 Switch 接近)
 * @param trackHeight 轨道高度 (默认 32dp, 与 M3 Switch 一致)
 * @param checkedTrackColor 开启时轨道色 (默认深粉)
 * @param uncheckedTrackColor 关闭时轨道色
 * @param checkedThumbColor 开启时拇指色 (默认深粉)
 * @param uncheckedThumbColor 关闭时拇指色
 * @param arcHeight 弧线高度 (thumb 上浮量, 默认 4dp)
 * @param maxTilt 最大倾斜角度 (默认 24°)
 * @param skewRatio 轨道斜切比例 (0=矩形, 0.15=轻度, 默认 0.15)
 */
@Composable
fun CyberJoystickSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    trackWidth: Dp = 52.dp,
    trackHeight: Dp = 32.dp,
    checkedTrackColor: Color = NeonDeepPink,
    uncheckedTrackColor: Color = NeonSteelBlue.copy(alpha = 0.35f),
    checkedThumbColor: Color = NeonDeepPink,
    uncheckedThumbColor: Color = NeonSteelBlue,
    arcHeight: Dp = 4.dp,
    maxTilt: Float = 24f,
    skewRatio: Float = 0.15f,
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scope = rememberCoroutineScope()

    // ▸ 进度引擎: Animatable — 点击经 LaunchedEffect 弹簧; 拖拽经 snapTo 接管
    //   (drag 松手后 animateTo 从拖拽位置回弹, 无视觉跳变)
    val springSpec = remember {
        spring<Float>(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        )
    }
    val progress = remember { Animatable(if (checked) 1f else 0f) }
    LaunchedEffect(checked) {
        progress.animateTo(if (checked) 1f else 0f, springSpec)
    }

    // ▸ pointerInput 闭包捕获的 checked/onCheckedChange 会陈旧 — 用 updatedState
    val currentChecked by rememberUpdatedState(checked)
    val currentOnCheckedChange by rememberUpdatedState(onCheckedChange)

    // ▸ 按压缩放 — 瞬时反馈
    val pressScale = animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessHigh,
        ),
        label = "press_scale",
    )

    val tiltSign = if (checked) 1f else -1f

    // ▸ 像素常量 (基于 32dp 轨道)
    val trackWPx = with(density) { trackWidth.toPx() }
    val trackHPx = with(density) { trackHeight.toPx() }
    val thumbR = trackHPx * 0.40f          // 初审: 0.42→0.40
    val skewPx = trackHPx * skewRatio      // 初审: 0.22→0.15
    val thumbStartX = skewPx * 0.5f + thumbR * 1.1f
    val thumbEndX = trackWPx - skewPx * 0.5f - thumbR * 1.1f
    val arcPx = with(density) { arcHeight.toPx() }
    val thumbDiameter = with(density) { (thumbR * 2f).toDp() }

    // ══ 外层: 48dp 触控热区 + clickable(点击) + pointerInput(拖拽) ══
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = trackWidth, minHeight = 48.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Switch,
                onClick = {
                    try { HapticUtils.standardTap(context) } catch (_: Exception) {}
                    currentOnCheckedChange(!currentChecked)
                },
            )
            // ▸ 水平拖拽: thumb 跟随手指, 松手过半即切换, 未过半弹回原位
            .pointerInput(thumbStartX, thumbEndX) {
                var dragBase = 0f
                var accumulatedPx = 0f
                val rangePx = (thumbEndX - thumbStartX).coerceAtLeast(1f)
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragBase = progress.value
                        accumulatedPx = 0f
                        scope.launch { progress.stop() }   // 停弹簧, drag 接管
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        accumulatedPx += dragAmount
                        val p = (dragBase + accumulatedPx / rangePx).coerceIn(0f, 1f)
                        scope.launch { progress.snapTo(p) }
                    },
                    onDragEnd = {
                        val p = progress.value
                        val newChecked = p > 0.5f
                        if (newChecked != currentChecked) {
                            try { HapticUtils.standardTap(context) } catch (_: Exception) {}
                            currentOnCheckedChange(newChecked)  // LaunchedEffect 从拖拽位置弹到目标
                        } else {
                            // 未过半: 从拖拽位置弹回原态
                            scope.launch { progress.animateTo(if (currentChecked) 1f else 0f, springSpec) }
                        }
                    },
                    onDragCancel = {
                        scope.launch { progress.animateTo(if (currentChecked) 1f else 0f, springSpec) }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        // ══ 内层: 52×32 内容区 (轨道 + 拖尾 + 拇指) ══
        Box(
            modifier = Modifier
                .size(width = trackWidth, height = trackHeight)
                // ══ 轨道层 (drawBehind) ══
                .drawBehind {
                    drawJoystickTrack(
                        width = size.width,
                        height = size.height,
                        progress = progress.value,
                        checkedTrackColor = checkedTrackColor,
                        uncheckedTrackColor = uncheckedTrackColor,
                        skewPx = skewPx,
                    )
                }
                // ══ 光晕拖尾层 (drawBehind, 在 thumb 后方) ══
                .drawBehind {
                    val p = progress.value
                    val trailAlpha = sin(p * PI).toFloat() * 0.35f
                    if (trailAlpha > 0.01f) {
                        val thumbX = thumbStartX + (thumbEndX - thumbStartX) * p
                        val thumbY = size.height / 2f - sin(p * PI).toFloat() * arcPx
                        val dirSign = if (checked) -1f else 1f
                        val trailEndX = thumbX + dirSign * thumbR * 1.5f
                        val trailColor = lerpColor(uncheckedThumbColor, checkedThumbColor, p)

                        drawLine(
                            brush = Brush.linearGradient(
                                colors = listOf(trailColor.copy(alpha = trailAlpha), Color.Transparent),
                                start = Offset(thumbX, thumbY),
                                end = Offset(trailEndX, thumbY),
                            ),
                            start = Offset(thumbX, thumbY),
                            end = Offset(trailEndX, thumbY),
                            strokeWidth = thumbR * 0.6f,
                            cap = StrokeCap.Round,
                        )
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            // ══ Thumb: 玻璃球, graphicsLayer lambda 读 progress ══
            Canvas(
                modifier = Modifier
                    .size(thumbDiameter)
                    .graphicsLayer {
                        val p = progress.value        // layer 阶段读
                        val sinP = sin(p * PI).toFloat()
                        translationX = (thumbStartX - thumbR) + (thumbEndX - thumbStartX) * p
                        translationY = -sinP * arcPx
                        rotationY = sinP * maxTilt * tiltSign
                        val pulse = 1f + sinP * 0.06f
                        scaleX = pulse * pressScale.value
                        scaleY = pulse * pressScale.value
                        cameraDistance = 8f * density.density
                    },
            ) {
                drawJoystickThumb(
                    radius = thumbR,
                    progress = progress.value,
                    checkedColor = checkedThumbColor,
                    uncheckedColor = uncheckedThumbColor,
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════
//  轨道绘制 — 斜切平行四边形 (Komi Store 风格)
// ════════════════════════════════════════════════════════════════

private fun DrawScope.drawJoystickTrack(
    width: Float,
    height: Float,
    progress: Float,
    checkedTrackColor: Color,
    uncheckedTrackColor: Color,
    skewPx: Float,
) {
    val trackColor = lerpColor(uncheckedTrackColor, checkedTrackColor, progress)

    val path = Path().apply {
        moveTo(skewPx, 0f)
        lineTo(width, 0f)
        lineTo(width - skewPx, height)
        lineTo(0f, height)
        close()
    }

    // T1: 轨道底色 (初审修正: 关态 alpha 提升, 0.30→0.45 / 0.15→0.25)
    drawPath(
        path = path,
        brush = Brush.linearGradient(
            colors = listOf(
                trackColor.copy(alpha = 0.45f),
                trackColor.copy(alpha = 0.25f),
                trackColor.copy(alpha = 0.45f),
            ),
            start = Offset(0f, 0f),
            end = Offset(width, height),
        ),
    )

    // T2: 内阴影 (顶部暗带)
    drawPath(
        path = path,
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.Black.copy(alpha = 0.12f),
                Color.Transparent,
                Color.Black.copy(alpha = 0.06f),
            ),
            startY = 0f,
            endY = height,
        ),
    )

    // T3: 边缘描边 (初审修正: 0.55→0.65, 关态更可见)
    drawPath(
        path = path,
        color = trackColor.copy(alpha = 0.65f),
        style = Stroke(width = 1.dp.toPx()),
    )

    // T4: 方向标记点 (左右各一)
    val markR = height * 0.06f
    val cY = height / 2f
    drawCircle(
        color = uncheckedTrackColor.copy(alpha = 0.5f * (1f - progress)),
        radius = markR,
        center = Offset(skewPx * 0.5f + height * 0.12f, cY),
    )
    drawCircle(
        color = checkedTrackColor.copy(alpha = 0.5f * progress),
        radius = markR,
        center = Offset(width - skewPx * 0.5f - height * 0.12f, cY),
    )
}

// ════════════════════════════════════════════════════════════════
//  拇指绘制 — 玻璃球 (6 层)
// ════════════════════════════════════════════════════════════════

private fun DrawScope.drawJoystickThumb(
    radius: Float,
    progress: Float,
    checkedColor: Color,
    uncheckedColor: Color,
) {
    val thumbColor = lerpColor(uncheckedColor, checkedColor, progress)
    val glowColor = lerpColor(uncheckedColor, checkedColor, progress)
    val r = radius
    val c = r

    // L1: 外光晕
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(glowColor.copy(alpha = 0.3f), Color.Transparent),
            center = Offset(c, c),
            radius = r * 1.4f,
        ),
        radius = r * 1.4f,
        center = Offset(c, c),
    )

    // L2: 主体 (玻璃球径向渐变)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                thumbColor.copy(alpha = 0.95f),
                thumbColor.copy(alpha = 0.6f),
                thumbColor.copy(alpha = 0.85f),
            ),
            center = Offset(c * 0.75f, c * 0.7f),
            radius = r * 1.1f,
        ),
        radius = r,
        center = Offset(c, c),
    )

    // L3: 高光点 (左上 — 模拟光源)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.6f),
                Color.White.copy(alpha = 0.15f),
                Color.Transparent,
            ),
            center = Offset(c * 0.68f, c * 0.62f),
            radius = r * 0.5f,
        ),
        radius = r * 0.5f,
        center = Offset(c * 0.68f, c * 0.62f),
    )

    // L4: 底部暗角
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.Black.copy(alpha = 0.25f),
                Color.Transparent,
            ),
            center = Offset(c, c * 1.3f),
            radius = r * 0.7f,
        ),
        radius = r * 0.7f,
        center = Offset(c, c * 1.3f),
    )

    // L5: 边缘描边
    drawCircle(
        color = thumbColor.copy(alpha = 0.4f),
        radius = r,
        center = Offset(c, c),
        style = Stroke(width = 0.5.dp.toPx()),
    )

    // L6: 顶部反光弧
    drawArc(
        color = Color.White.copy(alpha = 0.25f),
        startAngle = 200f,
        sweepAngle = 140f,
        useCenter = false,
        style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round),
        topLeft = Offset(r * 0.1f, r * 0.1f),
        size = Size(r * 1.8f, r * 1.8f),
    )
}

// ════════════════════════════════════════════════════════════════
//  工具
// ════════════════════════════════════════════════════════════════

private fun lerpColor(start: Color, stop: Color, fraction: Float): Color {
    return Color(
        red = start.red + (stop.red - start.red) * fraction,
        green = start.green + (stop.green - start.green) * fraction,
        blue = start.blue + (stop.blue - start.blue) * fraction,
        alpha = start.alpha + (stop.alpha - start.alpha) * fraction,
    )
}
