package com.rb.cybermonitorpro.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rb.cybermonitorpro.ui.theme.NeonPurple
import com.rb.cybermonitorpro.ui.theme.NeonPurpleBright
import com.rb.cybermonitorpro.ui.theme.NeonPurpleDeep
import com.rb.cybermonitorpro.ui.theme.NeonSteelBlue

/**
 * FancySlider — 厚胶囊轨道 + 房子图标旋转 thumb。
 *
 * 使用 M3 原生 Slider 负责拖动/无障碍/语义，只替换 thumb 与 track 渲染：
 * - track：38dp 厚胶囊，已选段 NeonPurple / 未选段钢蓝 30%，NeonPurpleDeep 30% 描边；
 * - thumb：ic_cyber_home 图标按进度旋转（默认 1080° = 3 整圈首尾同角回正）。
 *
 * SLIDER-01：material3 1.3.2 的 track lambda 签名是 (SliderState) -> Unit，
 * SliderPositions.fraction 已废弃不可用，进度比由 value/valueRange 自行计算。
 * SLIDER-02：thumb 用 48dp Box 包裹保住触控目标（minimumInteractiveComponentSize 在当前依赖下解析失败，已弃用）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FancySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
    icon: ImageVector = CyberIcons.Home,
    iconSize: Dp = 26.dp,
    trackHeight: Dp = 38.dp,
    rotationDegrees: Float = 1080f,
    thumbColor: Color = NeonPurpleBright,
    activeTrackColor: Color = NeonPurple,
    inactiveTrackColor: Color = NeonSteelBlue.copy(alpha = 0.3f),
    borderColor: Color = NeonPurpleDeep.copy(alpha = 0.3f),
) {
    val fraction = if (valueRange.endInclusive > valueRange.start) {
        ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
    } else 0f

    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        steps = steps,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        colors = SliderDefaults.colors(
            thumbColor = thumbColor,
            activeTrackColor = activeTrackColor,
            inactiveTrackColor = inactiveTrackColor,
        ),
        // SLIDER-01: track 收 SliderState，fraction 自算，绝不回退 positions.fraction
        track = { state ->
            val trackFraction = if (valueRange.endInclusive > valueRange.start) {
                ((state.value - valueRange.start) /
                    (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
            } else 0f
            Box(
                modifier = Modifier.fillMaxWidth().height(trackHeight),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxWidth().height(trackHeight)) {
                    val cap = size.height / 2f
                    val activeWidth = size.width * trackFraction
                    drawRoundRect(  // 未选段
                        color = inactiveTrackColor,
                        cornerRadius = CornerRadius(cap),
                    )
                    drawRoundRect(  // 已选段
                        color = activeTrackColor,
                        size = Size(activeWidth.coerceIn(0f, size.width), size.height),
                        cornerRadius = CornerRadius(cap),
                    )
                    val stroke = 2.dp.toPx()  // 描边
                    drawRoundRect(
                        color = borderColor,
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke),
                        cornerRadius = CornerRadius(cap - stroke / 2),
                        style = Stroke(width = stroke),
                    )
                }
            }
        },
        // SLIDER-02: thumb 用 48dp Box 保证触控目标 (不依赖 minimumInteractiveComponentSize)
        thumb = {
            Box(
                modifier = Modifier.size(48.dp),   // 48dp 触控目标
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = thumbColor,
                    modifier = Modifier
                        .size(iconSize)
                        .graphicsLayer { rotationZ = rotationDegrees * fraction },
                )
            }
        },
    )
}
