package com.example.deviceinfoviewer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.deviceinfoviewer.ui.theme.*

/**
 * 顶部标题栏纯色背景 — 无动效、无 Canvas
 */
@Composable
fun NeonHeaderDecoration(
    modifier: Modifier = Modifier,
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
