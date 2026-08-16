package com.rb.cybermonitorpro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rb.cybermonitorpro.ui.effects.CyberNightlightSwitch
import com.rb.cybermonitorpro.ui.theme.*

/**
 * 顶部标题栏霓虹装饰 — 纯色底 + 渐变光晕 + 霓虹边框光效
 *
 * 去除了 infiniteTransition / Brush.radialGradient / Canvas 自绘,
 * 改为纯静态背景 + border + shadow 实现，零重组开销。
 */
@Composable
fun NeonHeaderDecoration(
    modifier: Modifier = Modifier,
) {
    val pillShape = RoundedCornerShape(26.dp)

    Box(
        modifier.fillMaxWidth()
    ) {
        // 层 1: 纯色背景
        Box(
            Modifier.matchParentSize()
                .background(CyberCardStart)
        )
        // 层 2: 渐变光晕 (水平扫光)
        Box(
            Modifier.matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            NeonPurple.copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

/**
 * 霓虹边框光效 Modifier — 可复用于任何圆角容器
 *
 * 效果: 渐变描边 + 紫色外发光投影
 * shape 默认与头部药丸 (26.dp) 对齐
 *
 * @param turboXdr 真 HDR（CyberNightlight TurboXDR）。开启时增强外发光与描边亮度，
 *                默认跟随 CyberNightlightSwitch.enabled —— 开关打开即头部药丸边框点亮。
 */
fun Modifier.neonBorderGlow(
    cornerRadius: Dp = 26.dp,
    borderWidth: Dp = 1.5.dp,
    glowElevation: Dp = 6.dp,
    turboXdr: Boolean = CyberNightlightSwitch.enabled,
): Modifier {
    val elevation = if (turboXdr) glowElevation + 6.dp else glowElevation
    return this
        .shadow(
            elevation = elevation,
            shape = RoundedCornerShape(cornerRadius),
            ambientColor = if (turboXdr) NeonCyan.copy(alpha = 0.6f) else NeonPurple.copy(alpha = 0.5f),
            spotColor = if (turboXdr) NeonPurpleBright.copy(alpha = 0.9f) else NeonPurpleBright.copy(alpha = 0.7f),
        )
        .border(
            width = if (turboXdr) borderWidth + 0.5.dp else borderWidth,
            brush = Brush.horizontalGradient(
                colors = if (turboXdr) listOf(
                    Color.Transparent,
                    NeonPurpleDeep.copy(alpha = 0.6f),
                    NeonCyan.copy(alpha = 0.95f),
                    NeonPurpleBright.copy(alpha = 0.9f),
                    NeonPurpleDeep.copy(alpha = 0.6f),
                    Color.Transparent,
                ) else listOf(
                    Color.Transparent,
                    NeonPurpleDeep.copy(alpha = 0.4f),
                    NeonPurpleBright.copy(alpha = 0.7f),
                    NeonPurple.copy(alpha = 0.6f),
                    NeonPurpleDeep.copy(alpha = 0.4f),
                    Color.Transparent,
                )
            ),
            shape = RoundedCornerShape(cornerRadius),
        )
}

/**
 * 霓虹动效分割线 — 极简版
 */
@Composable
fun NeonDivider(
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().height(1.5.dp)
        .background(
            Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    NeonPurpleDeep.copy(alpha = 0.3f),
                    NeonPurple.copy(alpha = 0.5f),
                    NeonPurpleDeep.copy(alpha = 0.3f),
                    Color.Transparent
                )
            )
        )
    )
}
