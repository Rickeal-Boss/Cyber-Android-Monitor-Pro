package com.example.deviceinfoviewer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.deviceinfoviewer.ui.theme.*

/**
 * 顶部标题栏暗玻璃动效装饰 — 极简版 v2
 *
 * [修复 v2] 移除所有 Canvas 绘制 + animateFloat
 * 仅使用纯色 + box，确保所有 GPU 驱动兼容
 *
 * 曾排除过大半径 radialGradient → 无效
 * 现在排除无限循环动画 + Canvas → 测试裸背景
 */
@Composable
fun NeonHeaderDecoration(
    modifier: Modifier = Modifier,
    showParticles: Boolean = true,
    showFlowLine: Boolean = true,
) {
    Box(
        modifier.fillMaxWidth()
    ) {
        Box(
            Modifier.matchParentSize()
                .background(CyberCardStart)
        )
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
