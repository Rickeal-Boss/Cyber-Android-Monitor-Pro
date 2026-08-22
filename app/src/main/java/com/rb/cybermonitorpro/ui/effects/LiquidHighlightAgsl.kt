package com.rb.cybermonitorpro.ui.effects

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush

/**
 * AGSL 液态高光 — 触点径向渐变发光（API 33+），仅供 GlowBackButton 使用。
 *
 * 【文件隔离规范】本文件独占 GlowBackButton 所需的 android.graphics.RuntimeShader 引用。
 * 低版本设备永远不会加载本类（调用方以 Any? 传句柄，ART 按方法惰性解析），从根上规避
 * NoClassDefFoundError 启动闪退（三星 Android 8.0 / API 26 实测）。对齐项目 RevealLightAgsl.kt。
 *
 * 【颜色 uniform 规范】对齐 RevealLightAgsl.kt：使用 float3 + setFloatUniform(r,g,b)，
 * 而非 layout(color) + setColorUniform —— 后者在部分 Mali/Adreno 旧驱动上有颜色空间转换 bug。
 *
 * 【catch 一律用 Throwable】NoClassDefFoundError 是 Error 子类，catch (Exception) 抓不住它。
 */

private val LIQUID_HIGHLIGHT_AGSL = """
    uniform float2 uSize;
    uniform float3 uColor;
    uniform float uRadius;
    uniform float2 uPosition;
    uniform float uProgress;

    half4 main(float2 coord) {
        float dist = distance(coord, uPosition);
        float core = smoothstep(uRadius, uRadius * 0.35, dist);
        float glow = smoothstep(uRadius * 1.8, uRadius * 0.6, dist) * 0.4;
        float intensity = (core + glow) * uProgress;
        return half4(uColor.r, uColor.g, uColor.b, intensity);
    }
""".trimIndent()

/**
 * 创建 RuntimeShader 实例；构造失败（驱动/API 异常）返回 null。
 * catch Throwable —— NoClassDefFoundError 是 Error 而非 Exception，catch (Exception) 抓不到。
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun createLiquidHighlightShader(): Any? = try {
    RuntimeShader(LIQUID_HIGHLIGHT_AGSL)
} catch (_: Throwable) {
    null
}

/**
 * AGSL 绘制实现。shaderHandle 为 Any?（调用方持有），强转内聚在本文件内，
 * 跨文件边界不泄漏高版本类型。每帧仅更新 uniform，零分配（复用传入的 shader 实例）。
 *
 * touchPos / progress 以 `() -> T` 提供者 lambda 传入（对齐项目 CircularRevealModifier 的
 * 零重组模式）：在 draw lambda 内读取 Animatable.value，避免把值解构成常量导致高光冻结。
 *
 * 绘制顺序（HIGH-1 修正）：先 drawContent()（底座 + 描边 + 涟漪 + 图标），再画高光，
 * BlendMode.Plus 加法混合。底座径向渐变 ~95% 不透明，若高光画在 drawContent 之前会被底座
 * 盖住而不可见；画在内容之上则保证发光一定可见（Plus 只增亮、永不压暗）。
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun Modifier.liquidHighlightAgslImpl(
    shaderHandle: Any?,
    touchPosProvider: () -> Offset,
    radiusPx: Float,
    progressProvider: () -> Float,
    lightColor: Color,
): Modifier = this.drawWithContent {
    // 先画底层内容（底座/描边/涟漪/图标），保证其在高光之下
    drawContent()

    // draw 阶段读 Animatable.value → 零重组（跟随按压/拖拽实时更新）
    val progress = progressProvider()
    val touchPos = touchPosProvider()
    if (progress < 0.01f || shaderHandle == null) return@drawWithContent

    try {
        val shader = shaderHandle as? RuntimeShader ?: return@drawWithContent
        shader.apply {
            setFloatUniform("uSize", size.width, size.height)
            setFloatUniform("uColor", lightColor.red, lightColor.green, lightColor.blue)
            setFloatUniform("uRadius", radiusPx)
            setFloatUniform("uPosition", touchPos.x, touchPos.y)
            setFloatUniform("uProgress", progress)
        }
        // 高光画在内容之上，Plus 加法发光（不会压暗底层）
        drawRect(brush = ShaderBrush(shader), blendMode = BlendMode.Plus)
    } catch (_: Throwable) {
        // AGSL 不可用时静默降级（不绘制高光，底座 + 涟漪 + 图标仍可见）
    }
}

/**
 * ★ 方案A：overlay-only 变体 —— 仅绘制高光、不包裹 content。
 * 供 GlowBackButton 的独立高光层使用：高光层是最后一个子节点（z-order 最高），
 * clip(CircleShape) 在其修饰符链上，避免旧 drawWithContent 结构下
 * "外层 clip 切掉果冻尖端 / 不 clip 则高光溢出方角"的两难。
 * 文件隔离规范不变：RuntimeShader 引用不出本文件。
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun Modifier.liquidHighlightAgslOverlay(
    shaderHandle: Any?,
    touchPosProvider: () -> Offset,
    radiusPx: Float,
    progressProvider: () -> Float,
    lightColor: Color,
): Modifier = this.drawBehind {
    val progress = progressProvider()
    val touchPos = touchPosProvider()
    if (progress < 0.01f || shaderHandle == null) return@drawBehind
    try {
        val shader = shaderHandle as? RuntimeShader ?: return@drawBehind
        shader.apply {
            setFloatUniform("uSize", size.width, size.height)
            setFloatUniform("uColor", lightColor.red, lightColor.green, lightColor.blue)
            setFloatUniform("uRadius", radiusPx)
            setFloatUniform("uPosition", touchPos.x, touchPos.y)
            setFloatUniform("uProgress", progress)
        }
        drawRect(brush = ShaderBrush(shader), blendMode = BlendMode.Plus)
    } catch (_: Throwable) {
        // AGSL 不可用时静默降级（对齐原实现）
    }
}
