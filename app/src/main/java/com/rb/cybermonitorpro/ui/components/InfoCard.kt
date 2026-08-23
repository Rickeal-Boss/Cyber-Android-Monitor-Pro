package com.rb.cybermonitorpro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rb.cybermonitorpro.ui.effects.cardRipple
import com.rb.cybermonitorpro.ui.theme.*

/**
 * Ardot Cyberpunk Mobile HUD 卡片组件
 * 镜瓷白天青主题: 平涂卡面 + 釉影 + 天青描边
 */

// 卡片渐变 (现取可变 token，深浅切换自动跟随)
internal val CardGradient: Brush
    get() = Brush.linearGradient(listOf(CyberCardStart, CyberCardEnd))

// 空图表 sentinel — 用于判断是否显示 fillMaxWidth
private val NoChart: @Composable () -> Unit = {}

@Composable
fun InfoCard(
    title: String, subtitle: String, icon: ImageVector,
    modifier: Modifier = Modifier, iconTint: Color = NeonPurple
) {
    Card(
        modifier = modifier.fillMaxWidth()
            .shadow(elevation = 2.dp, shape = RoundedCornerShape(20.dp), ambientColor = AmbientShadow, spotColor = SpotShadow)
            .cardRipple(inset = 4.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardGradient)) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(14.dp))
                        .background(CyberMuted),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = title, fontSize = 17.sp, color = TextPrimary,
                        fontWeight = FontWeight.Bold, letterSpacing = 0.sp
                    )
                    Text(
                        text = subtitle, fontSize = 13.sp, color = TextSecondary,
                        letterSpacing = 0.sp
                    )
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
    borderColor: Color? = null,
    chart: @Composable () -> Unit = NoChart
) {
    Card(
        modifier = modifier
            .then(if (chart === NoChart) Modifier.fillMaxWidth() else Modifier)
            .shadow(elevation = 2.dp, shape = RoundedCornerShape(20.dp), ambientColor = AmbientShadow, spotColor = SpotShadow)
            .cardRipple(inset = 4.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardGradient)) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    text = title, fontSize = 11.sp, color = TextSecondary,
                    letterSpacing = 0.5.sp, fontWeight = FontWeight.Normal
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = value, fontSize = 22.sp, color = valueColor, letterSpacing = 1.5.sp
                )
                if (showProgress && progress >= 0f) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(2.5.dp)),
                        color = valueColor, trackColor = CyberMuted
                    )
                }
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle, fontSize = 12.sp,
                        color = TextSecondary.copy(alpha = 0.7f), letterSpacing = 0.5.sp
                    )
                }
                chart()
            }
        }
    }
}
