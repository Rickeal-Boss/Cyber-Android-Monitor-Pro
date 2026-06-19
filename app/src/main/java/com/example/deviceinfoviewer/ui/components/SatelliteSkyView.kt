package com.example.deviceinfoviewer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.deviceinfoviewer.R
import com.example.deviceinfoviewer.data.model.GpsSatelliteInfo
import com.example.deviceinfoviewer.ui.theme.*
import kotlin.math.*

/**
 * 卫星分布天空图 — 极坐标视图
 *
 * - 外圆 = 地平线（仰角 0°）
 * - 圆心 = 天顶（仰角 90°）
 * - 同心圆 = 仰角 30°/60° 等距线
 * - 上方 = 北（方位角 0°），顺时针
 * - 每个点 = 一颗卫星，颜色代表星座系统
 */
@Composable
fun SatelliteSkyView(
    satellites: List<GpsSatelliteInfo>,
    modifier: Modifier = Modifier,
    title: String? = null
) {
    val localeIndependentDefault = stringResource(R.string.gps_sky_view_default)
    val displayTitle = when {
        title == null -> localeIndependentDefault
        title.startsWith(localeIndependentDefault) -> title
        else -> title
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = displayTitle,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(8.dp))

            // 天空图
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(8.dp)
            ) {
                drawSkyPlot(satellites, size.minDimension)
            }

            // 图例
            if (satellites.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                ConstellationLegend(satellites)
            }
        }
    }
}

private fun DrawScope.drawSkyPlot(satellites: List<GpsSatelliteInfo>, canvasSize: Float) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val radius = (canvasSize / 2f) * 0.92f

    val bgColor = Color(0xFF0D0D15)
    val ringColor = Color(0xFF2A2A40)
    val ringMajorColor = Color(0xFF3A3A55)
    val textColor = Color(0xFF7A7A9A)

    // ── 背景圆 ──
    drawCircle(color = bgColor, radius = radius + 4.dp.toPx(), center = Offset(cx, cy))

    // ── 仰角同心圆 (0°/30°/60°) ──
    for (angle in listOf(0f, 30f, 60f)) {
        val r = radius * (1f - angle / 90f)
        val color = if (angle == 0f) ringMajorColor else ringColor
        drawCircle(color = color, radius = r, center = Offset(cx, cy), style = Stroke(width = 1.dp.toPx()))
    }

    // ── 仰角标注 ──
    for (angle in listOf(0f, 30f, 60f)) {
        val r = radius * (1f - angle / 90f)
        val offsetAngle = (-PI / 2 + PI / 4).toFloat()
        val labelX = cx + r * cos(offsetAngle) * 0.7f  // 右下45°位置
        val labelY = cy + r * sin(offsetAngle) * 0.7f
        drawSkyText("${angle.toInt()}°", labelX, labelY, textColor, 10.sp)
    }

    // ── 方位线（十字线 N/S/E/W）──
    data class Direction(val azimuth: Float, val label: String)
    val directions = listOf(
        Direction(0f, "N"),   // 上方 (azimuth 0° = North)
        Direction(90f, "E"),  // 右侧
        Direction(180f, "S"), // 下方
        Direction(270f, "W")  // 左侧
    )

    for (dir in directions) {
        val az = dir.azimuth
        val label = dir.label
        val angleRad = Math.toRadians((90.0 - az).toDouble()).toFloat()
        val endX = cx + radius * cos(angleRad)
        val endY = cy - radius * sin(angleRad)
        drawLine(
            color = ringColor,
            start = Offset(cx, cy),
            end = Offset(endX, endY),
            strokeWidth = 0.5.dp.toPx()
        )

        // 方向标签（稍微外移）
        val labelDist = radius * 1.06f
        val lx = cx + labelDist * cos(angleRad)
        val ly = cy - labelDist * sin(angleRad)
        drawSkyText(label, lx, ly, textColor, 11.sp)
    }

    // ── 绘制卫星 ──
    for (sat in satellites) {
        if (sat.elevation.isNaN() || sat.azimuth.isNaN()) continue

        val elev = sat.elevation.coerceIn(0f, 90f)
        val az = sat.azimuth

        // 计算位置
        val angleRad = Math.toRadians((90.0 - az).toDouble()).toFloat()
        val dist = radius * (1f - elev / 90f)

        val sx = cx + dist * cos(angleRad)
        val sy = cy - dist * sin(angleRad)

        val satColor = constellationColor(sat.constellationType)
        val dotRadius = if (sat.usedInFix) 5.dp.toPx() else 3.5.dp.toPx()
        val dotAlpha = if (sat.usedInFix) 1f else 0.6f

        // 外圈光环（usedInFix 卫星）
        if (sat.usedInFix) {
            drawCircle(
                color = satColor.copy(alpha = 0.3f),
                radius = dotRadius + 2.dp.toPx(),
                center = Offset(sx, sy)
            )
        }

        // 卫星圆点
        drawCircle(
            color = satColor.copy(alpha = dotAlpha),
            radius = dotRadius,
            center = Offset(sx, sy)
        )

        // SNR > 30 的卫星画小十字标记
        if (sat.snr > 30f) {
            val crossLen = dotRadius + 1.5.dp.toPx()
            drawLine(satColor.copy(alpha = dotAlpha * 0.6f),
                Offset(sx - crossLen, sy), Offset(sx + crossLen, sy), strokeWidth = 1.dp.toPx())
            drawLine(satColor.copy(alpha = dotAlpha * 0.6f),
                Offset(sx, sy - crossLen), Offset(sx, sy + crossLen), strokeWidth = 1.dp.toPx())
        }
    }

    // ── 中心点（天顶标记）──
    drawCircle(color = Color(0xFF4A4A6A), radius = 2.5.dp.toPx(), center = Offset(cx, cy))
}

/**
 * 在 Canvas 上绘制文字（使用原生 Paint 确保中文字体正常）
 */
private fun DrawScope.drawSkyText(text: String, x: Float, y: Float, color: Color, size: androidx.compose.ui.unit.TextUnit) {
    val paint = android.graphics.Paint().apply {
        this.color = android.graphics.Color.argb(
            (color.alpha * 255 + 0.5f).toInt(),
            (color.red * 255 + 0.5f).toInt(),
            (color.green * 255 + 0.5f).toInt(),
            (color.blue * 255 + 0.5f).toInt()
        )
        this.textSize = size.toPx()
        this.textAlign = android.graphics.Paint.Align.CENTER
        this.isAntiAlias = true
    }
    drawContext.canvas.nativeCanvas.drawText(text, x, y + size.toPx() / 3f, paint)
}

/**
 * 星座 → 颜色映射
 */
fun constellationColor(type: Int): Color = when (type) {
    GpsSatelliteInfo.CONSTELLATION_GPS     -> Color(0xFF42A5F5)  // 蓝
    GpsSatelliteInfo.CONSTELLATION_BEIDOU  -> Color(0xFFEF5350)  // 红
    GpsSatelliteInfo.CONSTELLATION_GLONASS -> Color(0xFF66BB6A)  // 绿
    GpsSatelliteInfo.CONSTELLATION_GALILEO -> Color(0xFFFFCA28)  // 黄
    GpsSatelliteInfo.CONSTELLATION_QZSS    -> Color(0xFFAB47BC)  // 紫
    GpsSatelliteInfo.CONSTELLATION_SBAS    -> Color(0xFFFF8A65)  // 橙
    GpsSatelliteInfo.CONSTELLATION_IRNSS   -> Color(0xFF26C6DA)  // 青
    else -> Color(0xFF78909C)                                     // 灰
}

/**
 * 星座图例
 */
@Composable
fun ConstellationLegend(satellites: List<GpsSatelliteInfo>) {
    // 统计各星座卫星数
    val constellationSets = satellites
        .groupBy { it.constellationType }
        .mapValues { (_, list) -> list.count() }

    val usedSets = satellites
        .filter { it.usedInFix }
        .groupBy { it.constellationType }
        .mapValues { (_, list) -> list.count() }

    // 按常见顺序排列
    val typeOrder = listOf(
        GpsSatelliteInfo.CONSTELLATION_GPS,
        GpsSatelliteInfo.CONSTELLATION_BEIDOU,
        GpsSatelliteInfo.CONSTELLATION_GLONASS,
        GpsSatelliteInfo.CONSTELLATION_GALILEO,
        GpsSatelliteInfo.CONSTELLATION_QZSS,
        GpsSatelliteInfo.CONSTELLATION_SBAS,
        GpsSatelliteInfo.CONSTELLATION_IRNSS
    )

    val seenTypes = constellationSets.keys.toList()

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.gps_sky_legend), fontSize = 13.sp, fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            for (type in typeOrder) {
                if (type !in seenTypes) continue
                val count = constellationSets[type] ?: 0
                val used = usedSets[type] ?: 0
                val label = "${GpsSatelliteInfo.constellationLabel(type)}"
                val clr = constellationColor(type)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(10.dp)) {
                        drawCircle(color = clr, radius = 4.dp.toPx())
                    }
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = "$label $used/$count",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
