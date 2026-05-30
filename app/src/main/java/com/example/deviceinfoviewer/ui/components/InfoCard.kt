package com.example.deviceinfoviewer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.deviceinfoviewer.ui.theme.*

@Composable
fun InfoCard(
    title: String, subtitle: String, icon: ImageVector,
    modifier: Modifier = Modifier, iconTint: Color = NeonPurple
) {
    Card(
        modifier = modifier.fillMaxWidth()
            .border(1.dp, NeonPurple.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CyberSurface)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                .background(NeonPurple.copy(alpha = 0.12f)), contentAlignment = Alignment.Center
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

@Composable
fun MetricCard(
    title: String, value: String, modifier: Modifier = Modifier,
    valueColor: Color = NeonPurpleBright, subtitle: String = "",
    progress: Float = -1f, showProgress: Boolean = false,
    chart: @Composable () -> Unit = {}
) {
    Card(
        modifier = modifier.fillMaxWidth()
            .border(1.dp, NeonPurple.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CyberSurface)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontSize = 12.sp, color = TextSecondary, letterSpacing = 1.sp)
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = valueColor,
                fontFamily = FontFamily.Monospace, letterSpacing = 1.5.sp)
            if (showProgress && progress >= 0f) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = valueColor, trackColor = ProgressTrack
                )
            }
            if (subtitle.isNotBlank()) {
                Text(subtitle, fontSize = 12.sp, color = TextSecondary.copy(alpha = 0.7f), letterSpacing = 0.5.sp)
            }
            chart()
        }
    }
}
