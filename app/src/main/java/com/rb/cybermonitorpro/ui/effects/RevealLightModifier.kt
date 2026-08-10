package com.rb.cybermonitorpro.ui.effects

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rb.cybermonitorpro.ui.theme.NeonPurple

/** 全局光照淡出时长 (ms) — IDLE 时强度渐隐 (原 1200 过长, 指针停下仍红绘 ~2-3s; 收窄到 800 快速回落) */
private const val GLOBAL_LIGHT_FADE_OUT_MS = 800

/**
 * Windows 10 Fluent Design Reveal Light 光照效果 Modifier
 *
 * 性能迁移 (2026-06-21): 移除 `composed { }` 包装，改为直接 `@Composable fun Modifier.revealLight()`。
 * `composed` 会在 modifier 链中注入额外 composition 边界，每个使用 revealLight 的组件 (InfoCard、
 * MetricCard、Dashboard 等多个) 都会产生额外开销。改为 @Composable 函数声明后，组合状态读取
 * 直接发生在调用方的 composition 上下文中，无额外边界。
 *
 * 核心算法 (基于 Microsoft RevealBrush):
 * 1. 通过 CompositionLocal (LocalLightState) 获取全局光照动画位置
 * 2. 通过 onGloballyPositioned 获知元素在窗口中的位置和尺寸
 * 3. 将全局光照坐标映射到元素本地坐标空间
 * 4. 若光照点在元素范围内，绘制径向渐变光斑
 *
 * @param radius 光照半径, 默认 180.dp
 * @param intensity 悬停光照强度, 0-1, 默认 0.25f
 * @param lightColor 光照颜色, 默认 NeonPurple
 * @param touchIntensity 触摸模式额外强度, 默认 0.15f
 * @param useAGSL 是否优先使用 AGSL 着色器 (API 33+), 默认 true
 */
@Composable
fun Modifier.revealLight(
    radius: Dp = 180.dp,
    intensity: Float = 0.25f,
    lightColor: Color = NeonPurple,
    touchIntensity: Float = 0.15f,
    useAGSL: Boolean = true,
): Modifier {
    val lightState = LocalLightState.current
    val animatedPos = rememberAnimatedLightPosition(lightState)
    val density = LocalDensity.current

    // 元素在窗口中的位置和尺寸
    var elementWindowPos by remember { mutableStateOf(Offset.Zero) }
    var elementSize by remember { mutableStateOf(Size.Zero) }

    val targetIntensity = when (lightState.mode) {
        GlobalLightState.Mode.TOUCH -> (intensity + touchIntensity).coerceIn(0f, 0.6f)
        GlobalLightState.Mode.HOVER -> intensity
        GlobalLightState.Mode.IDLE -> 0f
    }

    // ★ 缓慢淡出: IDLE 时强度以慢速 tween 渐隐; 悬停/触摸时快速 spring 响应, 保持跟手
    val effectiveIntensity by animateFloatAsState(
        targetValue = targetIntensity,
        animationSpec = if (lightState.mode == GlobalLightState.Mode.IDLE) {
            tween(durationMillis = GLOBAL_LIGHT_FADE_OUT_MS, easing = FastOutSlowInEasing)
        } else {
            // ★ 原 LowBouncy — 指针快速移动时强度值过冲抖动, 拉长红绘窗口; 改 NoBouncy 视觉差异极小
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
        },
        label = "lightIntensity"
    )

    // ★ 淡出期间 mode 已为 IDLE 但强度仍在渐隐, 故以「动画强度>0」判定活跃, 否则淡出中途被跳过
    // ★ GlobalLightSwitch: 设置页关闭全局光照时 isActive 恒 false, 跳过全部光栅绘制
    val isActive = GlobalLightSwitch.enabled && lightState.visible &&
            animatedPos.x.isFinite() && animatedPos.y.isFinite() &&
            animatedPos.x >= 0 && animatedPos.y >= 0 &&
            effectiveIntensity > 0.001f

    val radiusPx = with(density) { radius.toPx() }

    // ★ 新增：per-element 缓存 RuntimeShader 实例（每个元素生命周期内只编译一次）
    //   不能用模块级单例 — 1) RuntimeShader 是 API 33+ 类，顶层 val 会致 API<33 类加载崩溃
    //   2) 单例跨元素共享 uniform 有串色风险（display list 回放时 uniform 被覆盖）
    // FIX 2026-08-07 (startup crash, Samsung Android 8.0 / API 26, release):
    //   Declared as Any? NOT RuntimeShader? -- Kotlin emits CHECKCAST outside the
    //   SDK_INT guard for a RuntimeShader? type, ART resolves the class unconditionally
    //   -> NoClassDefFoundError on API < 33. Real AGSL impl lives in RevealLightAgsl.kt
    //   (never loaded on old devices).
    val shader: Any? = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            createRevealLightShader()
        } else null
    }

    return this
        .onGloballyPositioned { coordinates ->
            // ★ R4 (2026-08-07): 仅在位置/尺寸真正变化时才写 State,
            //   避免滑动期间 onGloballyPositioned 每帧回调都触发 RevealLight 重组。
            //   注意: window 坐标仍不可或缺, 因为需要将全局光照坐标映射到元素本地空间。
            val newPos = coordinates.positionInWindow()
            val newSize = Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())
            if (newPos != elementWindowPos || newSize != elementSize) {
                elementWindowPos = newPos
                elementSize = newSize
            }
        }
        .then(
            // ★ 改为判空 shader 实例，而非每次 new
            // FIX 2026-08-07: explicit SDK_INT check REQUIRED for lint NewApi
            //   (shader != null alone is enough at runtime, but lint does not treat it as a guard).
            if (useAGSL && shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                revealLightAgslImpl(shader, animatedPos, elementWindowPos, elementSize, radiusPx, effectiveIntensity, lightColor, isActive)
            } else {
                revealLightCanvas(animatedPos, elementWindowPos, elementSize, radiusPx, effectiveIntensity, lightColor, isActive)
            }
        )
}

// ══════════════════════════════════════════════════════════════════════════════
//  Canvas 实现 (API 21+ 全版本兼容)
// ══════════════════════════════════════════════════════════════════════════════════════════

/**
 * Canvas 径向渐变路径，兼容所有 API 级别 (21+)
 *
 * 渐变结构 (4 级 alpha 阶梯):
 *   center → edge
 *   [intensity] → [intensity*0.5] → [intensity*0.15] → [0.0]
 */
private fun Modifier.revealLightCanvas(
    animatedPos: Offset,
    elementWindowPos: Offset,
    elementSize: Size,
    radiusPx: Float,
    intensity: Float,
    lightColor: Color,
    isActive: Boolean,
): Modifier = this.drawWithContent {
    drawContent()

    if (!isActive || intensity < 0.001f) return@drawWithContent

    val localLightCenter = Offset(
        x = animatedPos.x - elementWindowPos.x,
        y = animatedPos.y - elementWindowPos.y
    )

    val buffer = radiusPx * 0.3f
    val nearElement = localLightCenter.x >= -buffer &&
            localLightCenter.x <= elementSize.width + buffer &&
            localLightCenter.y >= -buffer &&
            localLightCenter.y <= elementSize.height + buffer

    if (!nearElement) return@drawWithContent

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                lightColor.copy(alpha = intensity),
                lightColor.copy(alpha = intensity * 0.5f),
                lightColor.copy(alpha = intensity * 0.15f),
                Color.Transparent
            ),
            center = localLightCenter,
            radius = radiusPx
        ),
        // ★ Canvas 巨型圆修复: 光栅半径 = 渐变半径。
        //   原 maxOf(w,h)*1.2f 在 1080p 头部栏产生 ≈2500×2500px/帧 的填充,
        //   其中渐变半径 radiusPx 之外 80%+ 全透明 — 纯浪费。渐变边缘已收敛到 Transparent,
        //   半径收窄视觉零差异, API<33 老设备 (全走此路径) 光栅开销大幅降低。
        radius = radiusPx,
        center = localLightCenter
    )
}
