package com.rb.cybermonitorpro.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.Icon
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rb.cybermonitorpro.HapticUtils
import com.rb.cybermonitorpro.R
import com.rb.cybermonitorpro.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

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

/**
 * 最大拉伸系数 (拖拽达到阈值时的 scaleX/Y)
 * 2026-08-25 下调: 45° 纵横比 2.12 (1.80/0.85) 过夸张, 用户反馈视觉不一致;
 * 现 1.48/0.89 ≈ 1.66, 保留方向性但不再夸张。
 */
private val MAX_STRETCH = 1.48f

/**
 * 垂直方向收缩系数 (拖拽达到阈值时); 与 MAX_STRETCH 共同决定椭圆纵横比。
 * 2026-08-25 随 MAX_STRETCH 同步下调, 保持同一套降级幅度。
 */
private val MIN_STRETCH = 0.89f

/**
 * tanh  ️位移饱和初始斜率 (参考 Kyant0/AndroidLiquidGlass, 适配小按钮)。
 * 小拖拽线性跟随, 大拖拽饱和到 maxOffset, 避免按钮无限跑。
 */
private const val TANH_INITIAL_K = 0.15f

/**
 * 浅色圆形返回按钮 — iOS 26 拖拽交互 + 毛玻璃材质 V3.
 *
 * 独立于暗玻璃霓虹风的另一套返回键皮肤（当前活跃实现）。
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

    // 松手回弹 (非取消态时迅速归零; MediumBouncy+StiffnessMedium 快速收敛, 消除松手后椭圆残留)
    val snapBackScale by animateFloatAsState(
        targetValue = if (!isInteracting) 1.0f else pressScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
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
        // 果冻拉伸: 非对称橡皮筋 — 拖拽方向拉长到 1.48x, 垂直方向缩到 0.89x
        val stretchFactor = if (isInteracting && dragProgress > 0.05f) {
            dragProgress.coerceIn(0f, 1f)
        } else 0f

        // ── tanh 饱和位移外提: 供形变层 + 图标层复用, 使箭头跟随拖拽位移 (改动 1) ──
        // 小拖拽线性跟随, 大拖拽饱和到 maxOffset, 避免按钮无限跑。
        val dragTx = if (stretchFactor > 0f) {
            val btnSizePx = with(density) { btnSize.toPx() }
            val maxOffset = btnSizePx * 0.5f
            maxOffset * tanh(TANH_INITIAL_K * dragOffsetX / maxOffset)
        } else 0f
        val dragTy = if (stretchFactor > 0f) {
            val btnSizePx = with(density) { btnSize.toPx() }
            val maxOffset = btnSizePx * 0.5f
            maxOffset * tanh(TANH_INITIAL_K * dragOffsetY / maxOffset)
        } else 0f

        // 拖拽方向角度 (旋转层 + 高光弧共用; 未旋转坐标系的角度)
        val dragAngle = remember(dragOffsetX, dragOffsetY) {
            if (dragOffsetX == 0f && dragOffsetY == 0f) 0f
            else atan2(dragOffsetY, dragOffsetX).toFloat() * (180f / PI.toFloat())
        }

        // ── ① 果冻形变层: 无旋转架构 (采纳 Kyant0 思路) ──
        // 不加 rotationZ — 从根源消除"方向相反" + "rotationZ 与 cos²/sin² 双叠加态"两个功能性 bug。
        // 形变仅用 axis-aligned scaleX/scaleY, 经 cos²/sin² 分解到 X/Y , axis:
        //   水平/垂直拖拽 → 椭圆主轴沿 X/Y (方向正确); 45° → 退化为均匀放大 (无旋转架构已知
        //   trade-off, 接受). 图标层已在形变层外, 箭头永不被拉伸.
        Box(
            modifier = Modifier
                .size(btnSize)
                .graphicsLayer {
                    val n = stretchFactor
                    // ── 无旋转形变: cos²/sin² 分解 (唯一一套形变公式, 无 rotationZ) ──
                    val ang = atan2(dragOffsetY.toDouble(), dragOffsetX.toDouble())
                    val cosA = cos(ang).toFloat()
                    val sinA = sin(ang).toFloat()
                    val stretchAlong = 1f + (MAX_STRETCH - 1f) * n   // 拖拽方向
                    val stretchPerp = 1f + (MIN_STRETCH - 1f) * n    // 垂直方向
                    val sx = stretchAlong * cosA * cosA + stretchPerp * sinA * sinA
                    val sy = stretchAlong * sinA * sinA + stretchPerp * cosA * cosA
                    scaleX = snapBackScale * sx.coerceIn(0.75f, MAX_STRETCH) * cancelScale
                    scaleY = snapBackScale * sy.coerceIn(0.75f, MAX_STRETCH) * cancelScale
                    alpha = cancelAlpha
                    // ── tanh 饱和位移: 直接引用外提的 dragTx/dragTy (改动 1) ──
                    translationX = dragTx
                    translationY = dragTy
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
                            val totalDist = sqrt(dragOffsetX * dragOffsetX + dragOffsetY * dragOffsetY)

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
                }
        )   // ← 形变层结束, 图标移到旋转层之外

        // ── ② 图标层: 旋转层之外, 箭头不旋转、不被各向异性拉伸 ──
        Box(
            Modifier.matchParentSize().graphicsLayer {
                alpha = cancelAlpha
                // 改动 1: 箭头跟随拖拽位移 (与形变层同步), 守住 c4fb83d 无旋转架构 — 不加 scale/rotationZ
                translationX = dragTx
                translationY = dragTy
            },
            contentAlignment = Alignment.Center
        ) {
            // ══ 模糊质感箭头 (三层错位绘制模拟 soft-focus / 景深) ══
            val iconSize = btnSize * 0.44f
            // 图标 alpha: 拖拽时不衰减 (配合 V3 恒定亮度策略)
            val iconAlpha = (0.62f + 0.33f * (1f - dragProgress.coerceAtMost(0.7f))).coerceIn(0.40f, 0.95f)

            // 底层: 大幅模糊光晕
            Icon(
                imageVector = CyberIcons.ArrowBack,
                contentDescription = null,
                tint = Color(0xFF1A1A2E).copy(alpha = iconAlpha * 0.14f),
                modifier = Modifier
                    .size(iconSize * 1.40f)
                    .graphicsLayer { translationX = 0.8f; translationY = 0.8f }
            )
            // 中层: 轻微模糊
            Icon(
                imageVector = CyberIcons.ArrowBack,
                contentDescription = null,
                tint = Color(0xFF1A1A2E).copy(alpha = iconAlpha * 0.30f),
                modifier = Modifier
                    .size(iconSize * 1.12f)
                    .graphicsLayer { translationX = 0.4f; translationY = 0.4f }
            )
            // 主层: 锐利焦点
            Icon(
                imageVector = CyberIcons.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
                tint = Color(0xFF1A1A2E).copy(alpha = iconAlpha),
                modifier = Modifier.size(iconSize)
            )
        }

        // ── ③ 拖拽高光弧线 V2 — 玻璃折射感 (细/冷色调/柔和边缘) ══
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
