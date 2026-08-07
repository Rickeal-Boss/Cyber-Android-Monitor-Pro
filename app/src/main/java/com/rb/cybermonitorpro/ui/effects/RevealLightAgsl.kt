package com.rb.cybermonitorpro.ui.effects

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush

/**
 * AGSL RuntimeShader GPU 加速路径 (API 33+)。
 *
 * 此文件独占全部 android.graphics.RuntimeShader 引用。低版本设备永远不会加载本类
 * (调用方 RevealLightModifier 以 Any? 传句柄, ART 按方法惰性解析, 故低版本永不解析
 * RuntimeShader 类型), 从根上规避 NoClassDefFoundError 启动闪退。
 *
 * 绘制逻辑与 uniform 与改动前 (dc634b2 之前) 一字未改, API 33+ 视觉应像素级一致。
 */

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
 * 创建 RuntimeShader 实例; 构造失败 (驱动/API 异常) 返回 null。
 * catch Throwable — NoClassDefFoundError 是 Error 而非 Exception, catch (Exception) 抓不到。
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun createRevealLightShader(): Any? = try {
    RuntimeShader(SHADER_REVEAL_LIGHT)
} catch (_: Throwable) {
    null
}

/**
 * AGSL 绘制实现。shaderHandle 为 Any (调用方持有), 强转内聚在本文件内,
 * 跨文件边界不泄漏高版本类型。
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun Modifier.revealLightAgslImpl(
    shaderHandle: Any,
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
        val shader = shaderHandle as? RuntimeShader ?: return@drawWithContent

        val localCenter = Offset(
            x = animatedPos.x - elementWindowPos.x,
            y = animatedPos.y - elementWindowPos.y
        )

        val buffer = radiusPx * 0.3f
        if (localCenter.x < -buffer || localCenter.x > elementSize.width + buffer ||
            localCenter.y < -buffer || localCenter.y > elementSize.height + buffer) {
            return@drawWithContent
        }

        // 复用传入实例, 每帧只更新 uniform, 零分配
        shader.apply {
            setFloatUniform("uLightCenter", localCenter.x, localCenter.y)
            setFloatUniform("uRadius", radiusPx)
            setFloatUniform("uColor", lightColor.red, lightColor.green, lightColor.blue)
            setFloatUniform("uIntensity", intensity)
            setFloatUniform("uActive", 1.0f)
        }
        drawRect(brush = ShaderBrush(shader))
    } catch (_: Throwable) {
        // AGSL 不可用时静默降级
    }
}
