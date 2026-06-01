package com.example.deviceinfoviewer.ui.gps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.deviceinfoviewer.data.model.GpsSatelliteInfo
import com.example.deviceinfoviewer.ui.components.InfoCard
import com.example.deviceinfoviewer.ui.components.MetricCard
import com.example.deviceinfoviewer.ui.components.SatelliteSkyView
import com.example.deviceinfoviewer.ui.components.constellationColor
import com.example.deviceinfoviewer.ui.theme.*
import org.koin.androidx.compose.koinViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow

@Composable
fun GpsScreen(viewModel: GpsViewModel = koinViewModel()) {
    val gps by viewModel.gpsInfo.observeAsState()

    val enabled = gps?.gpsEnabled ?: false
    val satellites = gps?.satellites ?: emptyList()
    val hasFix = gps?.fixAcquired ?: false
    val totalCount = satellites.size
    val fixCount = gps?.fixSatelliteCount ?: 0

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── 状态卡片 ──
        val statusTitle = when {
            !enabled -> "GPS · 未启用"
            hasFix -> "GPS · 已定位"
            satellites.isNotEmpty() -> "GPS · 搜星中 (${totalCount}颗)"
            else -> "GPS · 已启用 · 等待定位"
        }
        val statusSubtitle = when {
            !enabled -> "请在系统设置中开启GPS"
            hasFix -> "定位成功 · 已锁定 $fixCount 颗卫星"
            else -> "正在搜索卫星信号..."
        }

        InfoCard(
            title = statusTitle,
            subtitle = statusSubtitle,
            icon = Icons.Default.PlayArrow,
            iconTint = if (enabled) NeonPurple else NeonPurple.copy(alpha = 0.4f)
        )

        // ── 坐标 ──
        gps?.latitude?.takeIf { it != 0.0 && !it.isNaN() }?.let { lat ->
            gps?.longitude?.takeIf { it != 0.0 && !it.isNaN() }?.let { lon ->
                MetricCard(
                    title = "坐标",
                    value = "%.6f, %.6f".format(lat, lon),
                    valueColor = NeonPurpleBright
                )
            }
        }

        // ── 精度 ──
        gps?.accuracy?.takeIf { it > 0 && !it.isNaN() }?.let { acc ->
            MetricCard(
                title = "精度",
                value = "%.1f m".format(acc),
                valueColor = NeonPurpleBright
            )
        }

        // ── 卫星计数（内/外圈环形统计）──
        MetricCard(
            title = "卫星数量",
            value = "$fixCount / $totalCount",
            valueColor = if (fixCount > 0) SuccessNeon else NeonPurpleBright,
            subtitle = when {
                !enabled -> "GPS 未启用"
                hasFix -> "已锁定 $fixCount 颗 · 可见 $totalCount 颗"
                totalCount > 0 -> "搜索中 · 可见 $totalCount 颗"
                else -> "等待卫星..."
            }
        )

        // ── 卫星分布天空图（始终显示，用于确认新代码已部署）──
        SatelliteSkyView(
            satellites = satellites,
            title = if (satellites.isEmpty()) "卫星分布图 · 无卫星信号" else "卫星分布图"
        )

        // ── 卫星列表 ──
        if (satellites.isNotEmpty()) {
            Text(
                "卫星列表",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp)
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
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "PRN ${sat.prn}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (sat.usedInFix) {
                        Text(
                            " ● 锁定",
                            fontSize = 12.sp,
                            color = SuccessNeon,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
                Text(
                    sat.constellation.ifEmpty { "?" },
                    fontSize = 14.sp,
                    color = constellationColor(sat.constellationType)
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "SNR: %.1f".format(sat.snr),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "仰角: %.1f°".format(sat.elevation),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "方位: %.1f°".format(sat.azimuth),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
