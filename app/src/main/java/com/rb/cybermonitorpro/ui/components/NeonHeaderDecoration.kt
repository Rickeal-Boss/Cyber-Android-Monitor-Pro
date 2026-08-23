package com.rb.cybermonitorpro.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rb.cybermonitorpro.ui.theme.*

/**
 * 顶部标题栏霓虹装饰 — 纯色底 + 渐变光晕
 *
 * 去除了 infiniteTransition / Brush.radialGradient / Canvas 自绘,
 * 改为纯静态背景实现，零重组开销。
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
