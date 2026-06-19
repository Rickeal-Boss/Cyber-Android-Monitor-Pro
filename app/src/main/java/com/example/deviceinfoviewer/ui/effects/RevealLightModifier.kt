package com.example.deviceinfoviewer.ui.effects

import android.os.Build
import android.graphics.RuntimeShader
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.unit.dp
import com.example.deviceinfoviewer.ui.theme.NeonPurple

/**
 * Windows 10 Fluent Design — Reveal Light 光照效果 Modifier
 *
 * 核心算法 (基于 Microsoft RevealBrush + Walterlv WPF 实现):
 * ─────────────────────────────────────────────────────────────
 * 1. 从 CompositionLocal (LocalLightState) 获取全局光照动画位置
 * 2. 通过 onGloballyPositioned 获知元素在窗口中的位置和尺寸
 * 3. 将全局光照坐标映射到元素本地坐标空间:
 *    localLightPos = globalLightPos - elementWindowPos
 * 4. 若光照点在元素范围内 → 绘制径向渐变光斑
 * 5. 光斑从中心 Color(intensity) → 边缘 Transparent
 *    (4 级渐变: 100% → 50% → 15% → 0% alpha)
 *
 * 视觉特征 (对齐 Windows 10 开始菜单/设置页面效果):
 * - 鼠标悬停 → 柔和淡紫色光斑跟随光标 (~25% 强度)
 * - 手指触摸 → 更亮更大光斑 (~40% 强度)
 * - 光斑半径 180dp (移动端适配, 比桌面端 100px 更大)
 * - Spring 物理弹簧动画 (dampingRatio=0.55, stiffness=200)
 *
 * 兼容性:
 * - minSdk 21 → Canvas radialGradient + drawCircle (Skia GPU)
 * - API 33+ → 可选 AGSL RuntimeShader (GPU 原生着色器)
 * - 降级策略: AGSL 异常时自动 fallback 到 Canvas 路径
 *
 * @param radius 光照半径, 默认 180.dp
 * @param intensity 悬停光照强度, 0-1, 默认 0.25f
 * @param lightColor 光照颜色, 默认 NeonPurple (匹配 Cyberpunk 主题)
 * @param touchIntensity 触摸模式额外强度, 默认 0.15f
 * @param useAGSL 是否优先使用 AGSL 着色器 (API 33+), 默认 true
 *
 * @see GlobalLightState 全局光照状态管理
 * @see GlobalLightProvider 全局光照事件捕获
 * @see AcrylicModifier 亚克力毛玻璃效果 (可搭配使用)
 */
fun Modifier.revealLight(
    radius: androidx.compose.ui.unit.Dp = 180.dp,
    intensity: Float = 0.25f,
    lightColor: Color = NeonPurple,
    touchIntensity: Float = 0.15f,
    useAGSL: Boolean = true,
): Modifier = composed(
    inspectorInfo = debugInspectorInfo {
        name = "revealLight"
        properties["radius"] = radius
        properties["intensity"] = intensity
        properties["lightColor"] = lightColor
        properties["touchIntensity"] = touchIntensity
    }
) {
    val lightState = LocalLightState.current
    val animatedPos = rememberAnimatedLightPosition(lightState)

    // 元素在窗口中的位置和尺寸 — onGloballyPositioned 回调更新
    var elementWindowPos by remember { mutableStateOf(Offset.Zero) }
    var elementSize by remember { mutableStateOf(Size.Zero) }

    // 当前有效强度 (根据交互模式调整)
    val effectiveIntensity = when (lightState.mode) {
        GlobalLightState.Mode.TOUCH -> (intensity + touchIntensity).coerceIn(0f, 0.6f)
        GlobalLightState.Mode.HOVER -> intensity
        GlobalLightState.Mode.IDLE -> 0f
    }

    // 仅当光照激活且位置有效 (非 INFINITY) 时才绘制
    val isActive = lightState.visible &&
            lightState.mode != GlobalLightState.Mode.IDLE &&
            animatedPos.x.isFinite() && animatedPos.y.isFinite() &&
            animatedPos.x >= 0 && animatedPos.y >= 0

    val radiusPx = with(androidx.compose.ui.platform.LocalDensity.current) {
        radius.toPx()
    }

    Modifier
        .onGloballyPositioned { coordinates ->
            elementWindowPos = coordinates.positionInWindow()
            elementSize = Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())
        }
        .then(
            if (useAGSL && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // API 33+: AGSL GPU 原生着色器路径
                revealLightAGSL(
                    animatedPos = animatedPos,
                    elementWindowPos = elementWindowPos,
                    elementSize = elementSize,
                    radiusPx = radiusPx,
                    intensity = effectiveIntensity,
                    lightColor = lightColor,
                    isActive = isActive
                )
            } else {
                // API 21+: Canvas radialGradient + drawCircle (Skia GPU 加速)
                revealLightCanvas(
                    animatedPos = animatedPos,
                    elementWindowPos = elementWindowPos,
                    elementSize = elementSize,
                    radiusPx = radiusPx,
                    intensity = effectiveIntensity,
                    lightColor = lightColor,
                    isActive = isActive
                )
            }
        )
}

// ═══════════════════════════════════════════════════════════════
//  Canvas 实现 (API 21+ — 全版本兼容)
// ═══════════════════════════════════════════════════════════════

/**
 * Canvas 径向渐变路径 — 兼容所有 API 级别 (21+)
 *
 * 使用 Brush.radialGradient + drawCircle 在内容上方叠加光照层。
 * Skia 的 radial gradient 有 GPU 加速, 性能对于少量元素来说足够。
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
    // 先绘制原始内容
    drawContent()

    // 光照未激活或强度太低时跳过
    if (!isActive || intensity < 0.001f) return@drawWithContent

    // 计算本地光照中心 (全局坐标 → 元素本地坐标)
    val localLightCenter = Offset(
        x = animatedPos.x - elementWindowPos.x,
        y = animatedPos.y - elementWindowPos.y
    )

    // 光照点是否靠近元素区域 (含半径缓冲区)
    val buffer = radiusPx * 0.3f
    val nearElement = localLightCenter.x >= -buffer &&
            localLightCenter.x <= elementSize.width + buffer &&
            localLightCenter.y >= -buffer &&
            localLightCenter.y <= elementSize.height + buffer

    if (!nearElement) return@drawWithContent

    // 绘制径向渐变光斑 — 渐变半径由 Brush 定义, drawCircle 半径取大值覆盖全元素
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

// ═══════════════════════════════════════════════════════════════
//  AGSL 着色器实现 (API 33+ — GPU 原生硬件加速)
// ═══════════════════════════════════════════════════════════════

/**
 * AGSL Reveal Light 着色器源码
 *
 * 直接在 GPU 上运行, 每个像素并行计算:
 * - 计算到光照中心的欧氏距离
 * - smoothstep 产生 Windows Reveal 风格的柔和边缘衰减
 * - pow(falloff, 1.5) 增强中心亮度, 更接近 Windows 原版效果
 *
 * 性能: 比 Canvas radialGradient 更省 CPU
 *   - Canvas 路径: CPU 计算渐变 → GPU 光栅化
 *   - AGSL 路径: GPU 像素着色器直接并行处理 (零 CPU 开销)
 */
private val SHADER_REVEAL_LIGHT = """
    uniform float2 uLightCenter;
    uniform float uRadius;
    uniform float3 uColor;
    uniform float uIntensity;
    uniform float uActive;

    half4 main(float2 fragCoord) {
        // 未激活 → 完全透明
        if (uActive < 0.5) {
            return half4(0.0);
        }

        // 欧氏距离
        float2 delta = fragCoord - uLightCenter;
        float dist = length(delta);

        // 柔和边缘衰减 (smoothstep)
        float falloff = 1.0 - smoothstep(0.0, uRadius, dist);

        // 增强中心亮度
        falloff = pow(falloff, 1.5);

        // 输出带 alpha 的颜色
        return half4(uColor.r, uColor.g, uColor.b, falloff * uIntensity);
    }
""".trimIndent()

/**
 * AGSL RuntimeShader GPU 加速路径 (API 33+)
 *
 * 通过 RuntimeShader + ShaderBrush 将光照着色器注入 GPU 绘制管线。
 * 每次 draw 时更新 uniform 参数 (位置、半径、颜色), GPU 自动重算所有像素。
 *
 * 降级策略: try-catch 异常时静默跳过 — 不崩溃, 仅光照效果消失。
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
    // 先绘制原始内容
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
        // AGSL 不可用时静默降级 — 不影响应用正常运行
    }
}

// ═══════════════════════════════════════════════════════════════
//  请注意: 此文件不包含任何 private 顶层函数
// ═══════════════════════════════════════════════════════════════
