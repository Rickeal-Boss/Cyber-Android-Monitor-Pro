package com.example.deviceinfoviewer.ui.gps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.deviceinfoviewer.data.model.GpsSatelliteInfo
import com.example.deviceinfoviewer.ui.components.InfoCard
import com.example.deviceinfoviewer.ui.components.MetricCard
import com.example.deviceinfoviewer.ui.theme.NeonPurple
import com.example.deviceinfoviewer.ui.theme.NeonPurpleBright
import org.koin.androidx.compose.koinViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow

@Composable
fun GpsScreen(viewModel: GpsViewModel = koinViewModel()) {
    val gps by viewModel.gpsInfo.observeAsState()

    val enabled = gps?.gpsEnabled ?: false
    val satellites = gps?.satellites ?: emptyList()
    val hasFix = gps?.fixAcquired ?: false

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val statusTitle = when {
            !enabled -> "GPS · 未启用"
            hasFix -> "GPS · 已定位"
            satellites.isNotEmpty() -> "GPS · 搜星中 (${satellites.size}颗)"
            else -> "GPS · 已启用 · 等待定位"
        }
        val statusSubtitle = when {
            !enabled -> "请在系统设置中开启GPS"
            hasFix -> "定位成功"
            else -> "正在搜索卫星信号..."
        }

        InfoCard(
            title = statusTitle,
            subtitle = statusSubtitle,
            icon = Icons.Default.PlayArrow,
            iconTint = if (enabled) NeonPurple else NeonPurple.copy(alpha = 0.4f)
        )

        gps?.latitude?.takeIf { it != 0.0 && !it.isNaN() }?.let { lat ->
            gps?.longitude?.takeIf { it != 0.0 && !it.isNaN() }?.let { lon ->
                MetricCard(
                    title = "坐标",
                    value = "%.6f, %.6f".format(lat, lon),
                    valueColor = NeonPurpleBright
                )
            }
        }

        gps?.accuracy?.takeIf { it > 0 && !it.isNaN() }?.let { acc ->
            MetricCard(
                title = "精度",
                value = "%.1f m".format(acc),
                valueColor = NeonPurpleBright
            )
        }

        MetricCard(
            title = "卫星数量",
            value = "${satellites.size} / ${gps?.satelliteCount ?: 0}",
            valueColor = NeonPurpleBright,
            subtitle = if (hasFix) "已定位" else if (satellites.isNotEmpty()) "搜索中" else "等待卫星..."
        )

        if (satellites.isNotEmpty()) {
            Text(
                "卫星列表", fontSize = 16.sp,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface
            )

            satellites.forEach { sat ->
                SatelliteCard(sat)
            }
        }
    }
}

@Composable
private fun SatelliteCard(sat: GpsSatelliteInfo) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("PRN ${sat.prn}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(sat.constellation ?: "", fontSize = 14.sp, color = NeonPurple)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("SNR: %.1f".format(sat.snr), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("仰角: %.1f°".format(sat.elevation), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
