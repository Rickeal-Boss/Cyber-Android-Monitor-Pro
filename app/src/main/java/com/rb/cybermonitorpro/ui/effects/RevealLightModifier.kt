package com.rb.cybermonitorpro.ui.effects

import android.os.Build
import android.graphics.RuntimeShader
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
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rb.cybermonitorpro.ui.theme.NeonPurple

/** ȫ�ֹ��յ���ʱ�� (ms) �� IDLE ʱǿ�Ȼ�������, ʵ��ƽ����������˲�� */
private const val GLOBAL_LIGHT_FADE_OUT_MS = 1200

/**
 * Windows 10 Fluent Design �? Reveal Light 光照效果 Modifier
 *
 * 性能迁移 (2026-06-21): 移除 `composed { }` 包装，改为直�? `@Composable fun Modifier.revealLight()`�?
 * `composed` 会在 modifier 链中注入额外 composition 边界，每个使�? revealLight 的组�? (InfoCard�?
 * MetricCard、Dashboard 等多�?) 都会产生额外开销。改�? @Composable 函数声明后，组合状态读�?
 * 直接发生在调用方�? composition 上下文中，无额外边界�?
 *
 * 核心算法 (基于 Microsoft RevealBrush):
 * 1. �? CompositionLocal (LocalLightState) 获取全局光照动画位置
 * 2. 通过 onGloballyPositioned 获知元素在窗口中的位置和尺寸
 * 3. 将全局光照坐标映射到元素本地坐标空�?
 * 4. 若光照点在元素范围内 �? 绘制径向渐变光斑
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

    // �� ��������: IDLE ʱǿ�������� tween ����; ��ͣ/����ʱ���� spring ��Ӧ, ���ָ���
    val effectiveIntensity by animateFloatAsState(
        targetValue = targetIntensity,
        animationSpec = if (lightState.mode == GlobalLightState.Mode.IDLE) {
            tween(durationMillis = GLOBAL_LIGHT_FADE_OUT_MS, easing = FastOutSlowInEasing)
        } else {
            spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)
        },
        label = "lightIntensity"
    )

    // �� �����ڼ� mode ��Ϊ IDLE ��ǿ�����ڽ���, ���ԡ�����ǿ��>0���ж���Ծ, ���򵭳���;������
    val isActive = lightState.visible &&
            animatedPos.x.isFinite() && animatedPos.y.isFinite() &&
            animatedPos.x >= 0 && animatedPos.y >= 0 &&
            effectiveIntensity > 0.001f

    val radiusPx = with(density) { radius.toPx() }

    return this
        .onGloballyPositioned { coordinates ->
            elementWindowPos = coordinates.positionInWindow()
            elementSize = Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())
        }
        .then(
            if (useAGSL && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                revealLightAGSL(animatedPos, elementWindowPos, elementSize, radiusPx, effectiveIntensity, lightColor, isActive)
            } else {
                revealLightCanvas(animatedPos, elementWindowPos, elementSize, radiusPx, effectiveIntensity, lightColor, isActive)
            }
        )
}

// ══════════════════════════════════════════════════════════════�?
//  Canvas 实现 (API 21+ �? 全版本兼�?)
// ══════════════════════════════════════════════════════════════�?

/**
 * Canvas 径向渐变路径 �? 兼容所�? API 级别 (21+)
 *
 * 渐变结构 (4 �? alpha 阶梯):
 *   center �? edge
 *   [intensity] �? [intensity*0.5] �? [intensity*0.15] �? [0.0]
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
        radius = maxOf(size.width, size.height) * 1.2f,
        center = localLightCenter
    )
}

// ══════════════════════════════════════════════════════════════�?
//  AGSL 着色器实现 (API 33+ �? GPU 原生硬件加�?)
// ══════════════════════════════════════════════════════════════�?

private val SHADER_REVEAL_LIGHT = """
    uniform float2 uLightCenter;
    uniform float uRadius;
    uniform float3 uColor;
    uniform float uIntensity;
    uniform float uActive;

    half4 main(float2 fragCoord) {
        if (uActive < 0.5) {
            return half4(0.0);
        }
        float2 delta = fragCoord - uLightCenter;
        float dist = length(delta);
        float falloff = 1.0 - smoothstep(0.0, uRadius, dist);
        falloff = pow(falloff, 1.5);
        return half4(uColor.r, uColor.g, uColor.b, falloff * uIntensity);
    }
""".trimIndent()

/**
 * AGSL RuntimeShader GPU 加速路�? (API 33+)
 */
private fun Modifier.revealLightAGSL(
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

    try {
        val localCenter = Offset(
            x = animatedPos.x - elementWindowPos.x,
            y = animatedPos.y - elementWindowPos.y
        )

        val buffer = radiusPx * 0.3f
        if (localCenter.x < -buffer || localCenter.x > elementSize.width + buffer ||
            localCenter.y < -buffer || localCenter.y > elementSize.height + buffer) {
            return@drawWithContent
        }

        val shader = RuntimeShader(SHADER_REVEAL_LIGHT).apply {
            setFloatUniform("uLightCenter", localCenter.x, localCenter.y)
            setFloatUniform("uRadius", radiusPx)
            setFloatUniform("uColor", lightColor.red, lightColor.green, lightColor.blue)
            setFloatUniform("uIntensity", intensity)
            setFloatUniform("uActive", 1.0f)
        }
        drawRect(brush = ShaderBrush(shader))
    } catch (_: Exception) {
        // AGSL 不可用时静默降级
    }
}
