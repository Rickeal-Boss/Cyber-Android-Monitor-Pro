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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.deviceinfoviewer.ui.theme.*

/**
 * Ardot Cyberpunk Mobile HUD 卡片组件
 * 渐变填充 + 紫色辉光 + 钢蓝辅色
 */

// 卡片渐变 (匹配 Ardot 设计稿)
private val CardGradient = Brush.linearGradient(listOf(CyberCardStart, CyberCardEnd))

@Composable
fun InfoCard(
    title: String, subtitle: String, icon: ImageVector,
    modifier: Modifier = Modifier, iconTint: Color = NeonPurple
) {
    Card(
        modifier = modifier.fillMaxWidth()
            .shadow(12.dp, PurpleGlow, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(Modifier.fillMaxWidth().background(CardGradient, RoundedCornerShape(12.dp))) {
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
                    Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(subtitle, fontSize = 13.sp, color = TextSecondary)
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
    chart: @Composable () -> Unit = {}
) {
    Card(
        modifier = modifier
            .then(if (chart == {}) Modifier.fillMaxWidth() else Modifier)
            .shadow(10.dp, PurpleGlowLight, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(Modifier.background(CardGradient, RoundedCornerShape(12.dp)).fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text(title, fontSize = 11.sp, color = TextSecondary, letterSpacing = 0.5.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    value, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    color = valueColor, fontFamily = FontFamily.Monospace, letterSpacing = 1.5.sp
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
                        color = TextSecondary.copy(alpha = 0.7f), letterSpacing = 0.5.sp
                    )
                }
                chart()
            }
        }
    }
}
