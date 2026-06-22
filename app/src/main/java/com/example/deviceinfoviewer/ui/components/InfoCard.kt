package com.example.deviceinfoviewer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.deviceinfoviewer.ui.effects.revealLight
import com.example.deviceinfoviewer.ui.theme.*

/**
 * Ardot Cyberpunk Mobile HUD 卡片组件
 * 渐变填充 + 紫色辉光 + 钢蓝辅色
 */

// 卡片渐变 (匹配 Ardot 设计稿)
internal val CardGradient = Brush.linearGradient(listOf(CyberCardStart, CyberCardEnd))

// 空图表 sentinel — 用于判断是否显示 fillMaxWidth
private val NoChart: @Composable () -> Unit = {}

@Composable
fun InfoCard(
    title: String, subtitle: String, icon: ImageVector,
    modifier: Modifier = Modifier, iconTint: Color = NeonPurple
) {
    Card(
        modifier = modifier.fillMaxWidth()
            .revealLight(radius = 160.dp, intensity = 0.22f)
            .shadow(elevation = 12.dp, shape = RoundedCornerShape(12.dp), ambientColor = PurpleGlow),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardGradient).hdrHighlight(12.dp)) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                        .background(CyberMuted),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, fontSize = 13.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun MetricCard(
    title: String, value: String, modifier: Modifier = Modifier,
    valueColor: Color = NeonPurpleBright, subtitle: String = "",
    progress: Float = -1f, showProgress: Boolean = false,
    chart: @Composable () -> Unit = NoChart
) {
    Card(
        modifier = modifier
            .then(if (chart === NoChart) Modifier.fillMaxWidth() else Modifier)
            .revealLight(radius = 160.dp, intensity = 0.22f)
            .shadow(elevation = 10.dp, shape = RoundedCornerShape(12.dp), ambientColor = PurpleGlowLight),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardGradient).hdrHighlight(12.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text(title, fontSize = 11.sp, color = TextSecondary, letterSpacing = 0.5.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    value, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    color = valueColor, fontFamily = FontFamily.Monospace, letterSpacing = 1.5.sp,
                    maxLines = 4, overflow = TextOverflow.Ellipsis, softWrap = true
                )
                if (showProgress && progress >= 0f) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = valueColor, trackColor = CyberMuted
                    )
                }
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle, fontSize = 12.sp,
                        color = TextSecondary.copy(alpha = 0.7f), letterSpacing = 0.5.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
                chart()
            }
        }
    }
}

// ── HDR 细高亮反光边框 (共享: 所有卡片统一使用) ──
internal fun Modifier.hdrHighlight(cornerDp: androidx.compose.ui.unit.Dp): Modifier =
    this.drawWithContent {
        drawContent()
        val cornerPx = cornerDp.toPx()
        drawRoundRect(
            color = Color.White.copy(alpha = 0.18f),
            topLeft = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
            size = size.copy(width = size.width - 1f, height = size.height - 1f),
            cornerRadius = CornerRadius(cornerPx),
            style = Stroke(width = 0.8f.dp.toPx())
        )
    }
