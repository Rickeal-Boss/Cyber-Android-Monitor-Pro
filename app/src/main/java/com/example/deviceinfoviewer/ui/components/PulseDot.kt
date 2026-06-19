package com.example.deviceinfoviewer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.example.deviceinfoviewer.ui.theme.NeonPurpleBright
import com.example.deviceinfoviewer.ui.theme.SuccessNeon

/**
 * 赛博朋克脉冲指示器 — 顶部实时监控状态灯
 */
@Composable
fun PulseDot(modifier: Modifier = Modifier, isActive: Boolean = true) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulseDot")
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "ra"
    )
    val ringScale by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "rs"
    )

    val color = if (isActive) SuccessNeon else NeonPurpleBright

    Canvas(modifier.size(12.dp)) {
        val cx = size.width / 2; val cy = size.height / 2
        val radius = size.width * 0.18f
        drawCircle(color = color.copy(alpha = ringAlpha), radius = radius * ringScale, center = Offset(cx, cy))
        drawCircle(color = color, radius = radius, center = Offset(cx, cy))
    }
}
