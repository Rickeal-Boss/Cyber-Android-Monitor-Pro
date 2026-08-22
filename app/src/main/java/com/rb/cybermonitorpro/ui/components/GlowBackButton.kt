package com.rb.cybermonitorpro.ui.components

import android.content.Context
import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.Icon
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.rb.cybermonitorpro.HapticUtils
import com.rb.cybermonitorpro.R
import com.rb.cybermonitorpro.ui.effects.createLiquidHighlightShader
import com.rb.cybermonitorpro.ui.effects.liquidHighlightAgslOverlay
import com.rb.cybermonitorpro.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * 暗玻璃霓虹返回按钮 — 液态玻璃升级版（灵感来自 AndroidLiquidGlass / Kyant0）。
 *
 * 视觉与交互特征（对齐赛博朋克 HUD 暗紫霓虹）：
 * - 深色径向渐变底座 + 左上玻璃反光弧（drawLiquidNeonBase, drawBehind）
 * - 按压 Animatable + spring(0.5, 300) 弹簧反馈（替代旧 animateFloatAsState）
 * - 拖拽 tanh 有界位移 + 旋转感知定向拉伸（果冻形变层 rotationZ 对齐拖拽角、沿拖拽轴非对称 scaleX，graphicsLayer lambda 内读 State，零重组）
 * - 从【触点】发出的 3 层错峰霓虹涟漪（替代旧的单层圆心涟漪）
 * - 按压霓虹描边脉冲（SteelBlue → PurpleBright → Magenta 取消态）
 * - 图标微缩 + 拖拽方向微位移 + 取消态淡出缩小（不旋转）
 * - 静态呼吸：graphicsLayer.alpha 无限重复（State 在 lambda 内读，不触发 Canvas 重绘）
 * - API 33+ AGSL 触点径向高光（独立文件隔离），低版本 Canvas 径向渐变降级（均 BlendMode.Plus）
 *
 * 仅本函数被改动；LightCircleBackButton / GlassCircleButton / drawFrostedGlassV3 保持原样。
 */

/** 最大位移上限 (tanh 饱和, 约按钮直径 30%) */
private const val MAX_TRANSLATE_DP = 12f

/** tanh 初始灵敏度 (适配 40dp 小按钮, 比参考项目胶囊按钮的 0.05 更跟手) */
private const val TANH_INITIAL_DERIVATIVE = 0.08f

/** 拖拽方向最大额外缩放 */
private const val MAX_DRAG_SCALE = 0.12f

/** 松手后延迟触发 onClick (ms)，让用户看到弹簧回弹起始帧 */
private const val ONCLICK_DELAY_MS = 30L

/** 颜色线性插值（逐通道）。androidx.compose.ui.util.lerp 在此 BOM 无 Color 重载，故自建（与 CyberJoystickSwitch.lerpColor 同思路）。 */
private fun lerpColor(start: Color, stop: Color, fraction: Float): Color {
    return Color(
        red = start.red + (stop.red - start.red) * fraction,
        green = start.green + (stop.green - start.green) * fraction,
        blue = start.blue + (stop.blue - start.blue) * fraction,
        alpha = start.alpha + (stop.alpha - start.alpha) * fraction,
    )
}

/**
 * 液态高光交互状态 — 按压进度 + 触点位置双 Animatable。
 *
 * 沿用项目 CircularRevealModifier / StaggeredPageTransition 的模式：
 * Animatable 值在 graphicsLayer / draw lambda 内读取 → 零重组、GPU 友好。
 */
class LiquidHighlightState(private val scope: CoroutineScope) {
    val pressProgress = Animatable(0f, 0.001f)
    val touchPosition = Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)

    private val pressSpring = spring<Float>(dampingRatio = 0.5f, stiffness = 300f, visibilityThreshold = 0.001f)
    private val posSpring = spring<Offset>(dampingRatio = 0.5f, stiffness = 300f, visibilityThreshold = Offset.VisibilityThreshold)

    private var startPos = Offset.Zero
    val offset: Offset get() = touchPosition.value - startPos

    fun onPressDown(pos: Offset) {
        startPos = pos
        scope.launch {
            launch { pressProgress.animateTo(1f, pressSpring) }
            launch { touchPosition.snapTo(pos) }
        }
    }

    fun onDrag(pos: Offset) {
        scope.launch { touchPosition.snapTo(pos) }
    }

    fun onRelease() {
        scope.launch {
            launch { pressProgress.animateTo(0f, pressSpring) }
            launch { touchPosition.animateTo(startPos, posSpring) }
        }
    }
}

/**
 * 统一手势处理（私有）。按下 → 拖拽跟踪 → 松手判定（拖距阈值取消）。
 *
 * @param onDown 在按下瞬间回调（用于从触点生成多层涟漪）
 * @param onConfirm 拖距 < 阈值松手时触发（延迟 ONCLICK_DELAY_MS 后，避免吞掉回弹起始帧）
 * @param onCancel 拖距 ≥ 阈值松手时回调（已触发 lightTap 触觉）
 */
private fun Modifier.liquidHighlightGesture(
    state: LiquidHighlightState,
    scope: CoroutineScope,
    cancelThresholdPx: Float,
    hapticContext: Context?,
    onDown: (Offset) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit = {},
): Modifier = this.pointerInput(state, cancelThresholdPx) {
    forEachGesture {
        awaitPointerEventScope {
            val down = awaitFirstDown()
            state.onPressDown(down.position)
            onDown(down.position)
            hapticContext?.let { ctx ->
                try { HapticUtils.standardTap(ctx) } catch (_: Exception) {}
            }

            val pointerId = down.id
            var released = false
            do {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == pointerId }
                if (change != null) {
                    state.onDrag(change.position)
                    if (!change.pressed) released = true
                }
            } while (!released)

            val totalDist = with(state.offset) { sqrt(x * x + y * y) }
            state.onRelease()

            if (totalDist < cancelThresholdPx) {
                // 用外部 scope 而非 AwaitPointerEventScope 自身协程，
                // 确保 delay 不会因手势块结束而被取消（对齐 LightCircleBackButton 模式）
                scope.launch { delay(ONCLICK_DELAY_MS); onConfirm() }
            } else {
                hapticContext?.let { ctx ->
                    try { HapticUtils.lightTap(ctx) } catch (_: Exception) {}
                }
                onCancel()
            }
        }
    }
}

/**
 * 暗霓虹液态底座（drawBehind）。整合原 .background(径向渐变) + 已删除的 drawWithGlassReflection()：
 * L1 深色径向渐变底座（按压提亮）、L2 左上玻璃反光弧。
 * 三层液态边缘折射 + 霓虹描边脉冲在内容 Canvas 中绘制（可随拖拽反方向偏移）。
 */
private fun Modifier.drawLiquidNeonBase(
    btnSize: Dp,
    pressProgress: Animatable<Float, *>,
): Modifier = this.drawBehind {
    val s = btnSize.toPx()
    val cx = s / 2f
    val cy = s / 2f
    val r = minOf(cx, cy)
    val p = pressProgress.value

    // L1: 深色径向渐变底座（按压时提亮）
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                CyberElevated.copy(alpha = 0.95f + p * 0.15f),
                CyberBackground.copy(alpha = 0.98f)
            ),
            center = Offset(s * 0.35f, s * 0.35f)
        ),
        radius = r, center = Offset(cx, cy)
    )

    // L2: 左上角玻璃反光弧
    drawArc(
        color = Color.White.copy(alpha = 0.06f + p * 0.04f),
        startAngle = 200f, sweepAngle = 110f, useCenter = false,
        style = Stroke(width = s * 0.10f),
        topLeft = Offset(s * 0.14f, s * 0.14f),
        size = Size(s * 0.52f, s * 0.52f)
    )
}

@Composable
fun GlowBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    btnSize: Dp = 40.dp,
    enableDrag: Boolean = true,
    enableAgslHighlight: Boolean = true,
    contentDescription: String? = null,
) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val density = LocalDensity.current

    val highlightState = remember(scope) { LiquidHighlightState(scope) }

    // 静态呼吸 alpha — 保持 State<Float> 引用，在 graphicsLayer lambda 内读 .value，
    // 不触发重组与 onDraw（对齐 StaggeredPageTransition 模式）。
    val breathAlphaState = rememberInfiniteTransition(label = "glowBreath").animateFloat(
        initialValue = 0.88f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowBreathAlpha"
    )

    // 涟漪队列 — 使用 snapshot 安全写入，避免 Canvas draw 中直接写状态导致 IllegalStateException
    var ripples by remember { mutableStateOf(listOf<RippleData>()) }
    // ★ snapshotFlow: 单长期协程替代 LaunchedEffect(ripples) 会随每次点击不断重启的问题
    //   最长层延迟 120ms + 动画 380ms + 20ms 缓冲 = 520ms 后清理
    LaunchedEffect(Unit) {
        snapshotFlow { ripples }
            .filter { it.isNotEmpty() }
            .collect {
                delay(520)
                ripples = ripples.filter { r -> System.currentTimeMillis() - r.startTime < 520 }
            }
    }

    // 从触点生成 3 层错峰霓虹涟漪（层延迟 0 / 60 / 120ms）
    val spawnRipples: (Offset) -> Unit = { pos ->
        val now = System.currentTimeMillis()
        ripples = ripples + listOf(
            RippleData(now, pos.x, pos.y, 0),
            RippleData(now, pos.x, pos.y, 1),
            RippleData(now, pos.x, pos.y, 2)
        )
    }

    // AGSL 句柄（Any?，仅 API 33+ 且启用时非空）。显式 SDK_INT check 是 lint NewApi 要求。
    val shaderHandle: Any? = remember(enableAgslHighlight) {
        if (enableAgslHighlight && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            createLiquidHighlightShader()
        } else null
    }

    val btnSizePx = with(density) { btnSize.toPx() }
    val cancelThresholdPx = with(density) { CANCEL_THRESHOLD_DP.dp.toPx() }
    val maxTranslatePx = with(density) { MAX_TRANSLATE_DP.dp.toPx() }

    // 高光层修饰符：overlay-only —— API 33+ 走 AGSL，低版本 Canvas 径向渐变降级（均 BlendMode.Plus 加法发光）；
    //   置于独立高光层（z-order 最高、clip 到圆），不再 drawWithContent 包裹内容。
    val highlightOverlayModifier = if (
        enableAgslHighlight &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        shaderHandle != null
    ) {
        Modifier.liquidHighlightAgslOverlay(
            shaderHandle = shaderHandle,
            touchPosProvider = { highlightState.touchPosition.value },
            radiusPx = btnSizePx * 1.2f,
            progressProvider = { highlightState.pressProgress.value },
            lightColor = NeonPurpleBright
        )
    } else {
        // ★ 方案A：降级路径同步改 overlay-only（drawBehind，不再 drawWithContent 包裹）
        Modifier.drawBehind {
            val p = highlightState.pressProgress.value
            if (p > 0.01f) {
                val tp = highlightState.touchPosition.value
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            NeonPurpleBright.copy(alpha = 0.30f * p),
                            NeonPurpleBright.copy(alpha = 0.10f * p),
                            Color.Transparent
                        ),
                        center = tp,
                        radius = btnSizePx * 1.2f
                    ),
                    radius = btnSizePx * 1.2f,
                    center = tp,
                    blendMode = BlendMode.Plus
                )
            }
        }
    }

    Box(
        modifier = modifier
            .size(btnSize)
            // ★ 方案A 外层：仅位移 + 呼吸 alpha。不旋转、不缩放——
            //   位移必须在未旋转坐标系中进行，否则方向被旋转偏转。
            .graphicsLayer {
                alpha = breathAlphaState.value
                val off = highlightState.offset
                translationX = maxTranslatePx * tanh(TANH_INITIAL_DERIVATIVE * off.x / maxTranslatePx)
                translationY = maxTranslatePx * tanh(TANH_INITIAL_DERIVATIVE * off.y / maxTranslatePx)
            }
            // 手势（不绘制）
            .then(
                if (enableDrag) {
                    Modifier.liquidHighlightGesture(
                        state = highlightState,
                        scope = scope,
                        cancelThresholdPx = cancelThresholdPx,
                        hapticContext = ctx,
                        onDown = spawnRipples,
                        onConfirm = onClick,
                        onCancel = {}
                    )
                } else {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(onPress = { offset ->
                            spawnRipples(offset)
                            highlightState.onPressDown(offset)
                            tryAwaitRelease()
                            highlightState.onRelease()
                            scope.launch {
                                delay(ONCLICK_DELAY_MS)
                                try { HapticUtils.standardTap(ctx) } catch (_: Exception) {}
                                onClick()
                            }
                        })
                    }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        // ── ① 果冻形变层：旋转至拖拽方向 + 非对称缩放 ──
        // 圆形内容（径向渐变/环/描边）对旋转不敏感 → rotationZ 无视觉副作用；
        // 旋转后局部 +X 恒指向拖拽方向，scaleX 即"沿拖拽轴拉伸"，scaleY 真·垂直轴不变。
        // 夹角抖动安全：θ 剧烈变化只发生在 |offset|→0 附近，而那里 n→0 → scaleX≈scaleY
        // （均匀缩放），旋转对圆形内容不可见，故无需角度死区。
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    val pressP = highlightState.pressProgress.value
                    val off = highlightState.offset
                    val dist = sqrt(off.x * off.x + off.y * off.y)
                    val n = (dist / cancelThresholdPx).coerceIn(0f, 1f)
                    val base = lerp(1f, 0.90f, pressP)
                    // Compose rotationZ 正值=顺时针（y 向下），与 atan2(y,x) 同向，直接换算
                    rotationZ = Math.toDegrees(atan2(off.y, off.x).toDouble()).toFloat()
                    scaleX = base + MAX_DRAG_SCALE * n
                    scaleY = base
                }
                .shadow(
                    elevation = 4.dp,
                    shape = CircleShape,
                    ambientColor = Color.Black.copy(alpha = 0.5f),
                    spotColor = Color.Black.copy(alpha = 0.3f)
                )
                .clip(CircleShape)
                .drawLiquidNeonBase(btnSize = btnSize, pressProgress = highlightState.pressProgress)
        ) {
            // 液态边缘折射 + 霓虹描边脉冲（随形变层旋转拉伸）
            Canvas(Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val baseR = minOf(cx, cy)

                val pressP = highlightState.pressProgress.value
                val off = highlightState.offset
                val dist = sqrt(off.x * off.x + off.y * off.y)
                val n = (dist / cancelThresholdPx).coerceIn(0f, 1f)

                val isCancelling = n > 0.85f
                val cancelProgress = if (isCancelling) ((n - 0.85f) / 0.15f).coerceIn(0f, 1f) else 0f

                // ★ 方案A：旋转后局部坐标系中拖拽方向恒为 +X，反向偏移即 −X。
                //   淘汰旧 dirX/dirY sign() 判断（其在 offset 分量恰为 0 时方向错误）。
                //   旧第三层外晕环（radius=baseR+1.5dp）整体位于 clip 之外恒不可见，已删除。
                val edgeShift = 0.5f.dp.toPx() * n
                drawCircle(
                    color = NeonPurpleBright.copy(alpha = 0.30f + 0.10f * pressP),
                    radius = baseR - 1f.dp.toPx(),
                    center = Offset(cx - edgeShift, cy),
                    style = Stroke(width = 1f.dp.toPx())
                )
                drawCircle(
                    color = NeonCyan.copy(alpha = 0.15f + 0.10f * pressP),
                    radius = baseR - 2f.dp.toPx(),
                    center = Offset(cx - edgeShift, cy),
                    style = Stroke(width = 0.6f.dp.toPx())
                )

                // 霓虹描边脉冲：SteelBlue → PurpleBright，取消态偏移到 Magenta（逻辑不变）
                val pressColor = lerpColor(NeonSteelBlue, NeonPurpleBright, pressP)
                val borderColor = if (isCancelling) lerpColor(pressColor, NeonMagenta, cancelProgress) else pressColor
                val borderWidth = lerp(0.8f.dp.toPx(), 1.4f.dp.toPx(), pressP)
                val borderAlpha = lerp(0.40f, 0.90f, pressP)
                drawCircle(
                    color = borderColor.copy(alpha = borderAlpha),
                    radius = baseR - borderWidth / 2f,
                    center = Offset(cx, cy),
                    style = Stroke(width = borderWidth)
                )
            }
        }

        // ── ② 涟漪层：圆环旋转不变，移出旋转层免去触点坐标逆旋 ──
        Canvas(Modifier.matchParentSize().clip(CircleShape)) {
            // 涟漪绘制代码与原实现完全一致（三层错峰 0/60/120ms，center=ripple.touchX/Y）
            val now = System.currentTimeMillis()
            val baseR = minOf(size.width, size.height) / 2f
            ripples.forEach { ripple ->
                val delayMs = when (ripple.layer) {
                    0 -> 0L
                    1 -> 60L
                    else -> 120L
                }
                val elapsed = now - ripple.startTime - delayMs
                if (elapsed < 0) return@forEach
                val progress = (elapsed / 380f).coerceIn(0f, 1f)
                if (progress >= 1f) return@forEach

                val (startR, maxR, layerAlpha, layerColor, layerWidth) = when (ripple.layer) {
                    0 -> RippleLayer(4.dp.toPx(), baseR * 1.2f, 0.40f, NeonPurpleBright, 2.dp.toPx())
                    1 -> RippleLayer(2.dp.toPx(), baseR * 1.5f, 0.25f, NeonCyan, 1.2f.dp.toPx())
                    else -> RippleLayer(0f, baseR * 2.0f, 0.12f, NeonPurple, 3.dp.toPx())
                }
                val rippleR = startR + (maxR - startR) * progress
                val rippleAlpha = (1f - progress).coerceAtLeast(0f) * layerAlpha
                drawCircle(
                    color = layerColor.copy(alpha = rippleAlpha),
                    radius = rippleR.coerceAtMost(maxR),
                    center = Offset(ripple.touchX, ripple.touchY),
                    style = Stroke(width = layerWidth * (1f - progress))
                )
            }
        }

        // ── ③ 图标层：位于旋转层之外 → 任何拖拽方向下箭头都不旋转、不被各向异性拉伸 ──
        // 独立 clip 防止图标微位移滑出按钮可视圆。
        Box(Modifier.matchParentSize().clip(CircleShape), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = CyberIcons.ArrowBack,
                contentDescription = contentDescription ?: stringResource(R.string.common_back),
                // ★ 方案A：tint 恒定，取消态淡化移入 graphicsLayer.alpha ——
                //   消除组合期读 highlightState.offset 导致的每次拖拽移动重组（治"零重组"破功）
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier
                    .size(btnSize * 0.45f)
                    .graphicsLayer {
                        val pressP = highlightState.pressProgress.value
                        val off = highlightState.offset
                        val dist = sqrt(off.x * off.x + off.y * off.y)
                        val n = (dist / cancelThresholdPx).coerceIn(0f, 1f)
                        val cancelling = n > 0.85f
                        val cancelP = if (cancelling) ((n - 0.85f) / 0.15f).coerceIn(0f, 1f) else 0f
                        val iconScale = lerp(1f, 0.85f, pressP) *
                                if (cancelling) lerp(1f, 0.75f, cancelP) else 1f
                        scaleX = iconScale
                        scaleY = iconScale
                        // 取消态淡出：0.92→0.35 等效为层 alpha 1→0.38（tint 已恒定 0.92）
                        alpha = if (cancelling) lerp(1f, 0.38f, cancelP) else 1f
                        // 图标微位移保留（方向跟随拖拽，位于旋转层外，无需旋转变换）
                        translationX = off.x * 0.15f * n
                        translationY = off.y * 0.15f * n
                    }
            )
        }

        // ── ④ 高光层：z-order 最高（对齐原 drawWithContent 后绘制语义），clip 到圆 ──
        Box(Modifier.matchParentSize().clip(CircleShape).then(highlightOverlayModifier))
    }
}

// ── 涟漪数据（触点坐标 + 层索引；private data class，全项目仅此文件定义，修改字段安全）──
private data class RippleData(
    val startTime: Long,
    val touchX: Float,   // 触点 X 像素坐标（相对按钮左上角，与 Canvas 坐标系一致）
    val touchY: Float,
    val layer: Int,      // 0=主涟漪, 1=延迟环, 2=外晕
)

// 局部五元组（仅 Canvas 涟漪层参数打包用：起始半径 / 终止半径 / α / 颜色 / 线宽）
private data class RippleLayer(
    val startR: Float,
    val maxR: Float,
    val alpha: Float,
    val color: Color,
    val width: Float,
)

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
