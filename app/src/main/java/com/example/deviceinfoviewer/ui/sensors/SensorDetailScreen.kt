package com.example.deviceinfoviewer.ui.sensors

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.deviceinfoviewer.data.model.HistoryDataPoint
import com.example.deviceinfoviewer.data.model.SensorItemInfo
import com.example.deviceinfoviewer.data.model.SensorLiveData
import com.example.deviceinfoviewer.data.model.SensorTypeMeta
import com.example.deviceinfoviewer.ui.theme.*
import org.koin.androidx.compose.koinViewModel

// 轴颜色映射
private val axisColors = listOf(NeonPurple, NeonCyan, NeonMagenta)
private val axisNames = listOf("X", "Y", "Z")

/**
 * 传感器详情页 — 竞品风格
 * 包含: 实时数值卡片 + 波形图 + 静态信息
 */
@Composable
fun SensorDetailScreen(
    sensor: SensorItemInfo,
    onBack: () -> Unit,
    viewModel: SensorDetailViewModel = koinViewModel()
) {
    val meta = SensorTypeMeta.fromTypeId(sensor.type)
    val liveData by viewModel.liveData.observeAsState()
    val histData by viewModel.sensorHistoryData.observeAsState(emptyMap())

    // 生命周期管理: 进入页面启动，离开页面停止
    DisposableEffect(sensor) {
        viewModel.startListening(sensor)
        onDispose { viewModel.stopListening() }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── 标题栏: 返回按钮 + 传感器名称 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(36.dp)
            ) {
                Text("\u2190", fontSize = 20.sp, color = NeonPurpleBright)
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    meta?.displayName ?: sensor.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (meta != null) {
                    Text(
                        sensor.name,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ── 实时数值卡片 ──
        SensorValueCard(sensor = sensor, meta = meta, liveData = liveData)

        // ── 实时波形图 ──
        SensorChartCard(
            meta = meta,
            histData = histData,
            sensorType = sensor.type,
            liveData = liveData
        )

        // ── 传感器静态信息 ──
        SensorInfoCard(sensor = sensor, meta = meta)
    }
}

// ============================================================
//  实时数值卡片
// ============================================================
@Composable
private fun SensorValueCard(
    sensor: SensorItemInfo,
    meta: SensorTypeMeta?,
    liveData: SensorLiveData?
) {
    val valueCount = meta?.valueCount ?: 3
    val labels = meta?.axisLabels ?: listOf("X", "Y", "Z")
    val unit = meta?.unit?.takeIf { it.isNotEmpty() } ?: ""

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── 传感器图标 ──
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(NeonPurple.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    when (meta) {
                        SensorTypeMeta.LIGHT -> "\u2600"
                        SensorTypeMeta.PROXIMITY -> "\u2194"
                        SensorTypeMeta.GYROSCOPE,
                        SensorTypeMeta.GYROSCOPE_UNCALIBRATED -> "\u21BB"
                        SensorTypeMeta.ACCELEROMETER,
                        SensorTypeMeta.LINEAR_ACCELERATION,
                        SensorTypeMeta.ACCELEROMETER_UNCALIBRATED -> "\u2195"
                        SensorTypeMeta.GRAVITY -> "\u2B07"
                        SensorTypeMeta.ORIENTATION -> "\u2316"
                        SensorTypeMeta.ROTATION_VECTOR,
                        SensorTypeMeta.GAME_ROTATION_VECTOR,
                        SensorTypeMeta.GEOMAGNETIC_ROTATION_VECTOR -> "\u27F3"
                        else -> "\u25C9"
                    },
                    fontSize = 22.sp
                )
            }

            Spacer(Modifier.height(12.dp))

            // ── 数值显示 ──
            if (valueCount == 1) {
                // 单值传感器: 光线 / 距离 / 压力
                val raw = liveData?.x
                val value = if (raw != null && !raw.isNaN()) raw else Float.NaN
                val formatted = if (!value.isNaN()) {
                    when (meta) {
                        SensorTypeMeta.LIGHT -> "%.0f".format(value)
                        SensorTypeMeta.PROXIMITY -> "%.1f".format(value)
                        SensorTypeMeta.PRESSURE -> "%.1f".format(value)
                        SensorTypeMeta.HUMIDITY -> "%.1f".format(value)
                        SensorTypeMeta.AMBIENT_TEMPERATURE -> "%.1f".format(value)
                        else -> "%.2f".format(value)
                    }
                } else "---"

                Text(
                    formatted,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonPurpleBright
                )

                if (unit.isNotEmpty()) {
                    Text(
                        unit,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 距离传感器的特殊状态指示
                if (meta == SensorTypeMeta.PROXIMITY && !value.isNaN()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when {
                            value <= 0.5f -> "\u25CF 贴近 (Near)"
                            value <= sensor.maxRange * 0.3f -> "\u25CB 接近 (Close)"
                            else -> "\u25CC 远离 (Far)"
                        },
                        fontSize = 13.sp,
                        color = if (value <= 0.5f) NeonMagenta else SuccessNeon
                    )
                }
            } else {
                // 多轴传感器: X/Y/Z 三列
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (i in 0 until valueCount) {
                        val raw = when (i) {
                            0 -> liveData?.x
                            1 -> liveData?.y
                            else -> liveData?.z
                        }
                        val value = if (raw != null && !raw.isNaN()) raw else Float.NaN
                        val formatted = if (!value.isNaN()) {
                            when (meta) {
                                SensorTypeMeta.ORIENTATION,
                                SensorTypeMeta.GYROSCOPE,
                                SensorTypeMeta.GYROSCOPE_UNCALIBRATED -> "%.4f".format(value)
                                SensorTypeMeta.ROTATION_VECTOR,
                                SensorTypeMeta.GAME_ROTATION_VECTOR,
                                SensorTypeMeta.GEOMAGNETIC_ROTATION_VECTOR -> "%.6f".format(value)
                                else -> "%.2f".format(value)
                            }
                        } else "---"

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // 轴标签 (彩色圆点)
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(axisColors.getOrElse(i) { NeonPurple })
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                labels.getOrElse(i) { "?" },
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                formatted,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = axisColors.getOrElse(i) { NeonPurpleBright }
                            )
                        }
                    }
                }

                if (unit.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "单位: $unit",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── 精度指示 ──
            liveData?.let { ld ->
                if (ld.accuracy > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when (ld.accuracy) {
                            3 -> "\u25CF 高精度"
                            2 -> "\u25D0 中精度"
                            1 -> "\u25D1 低精度"
                            else -> "\u25CB 不可靠"
                        },
                        fontSize = 12.sp,
                        color = when (ld.accuracy) {
                            3 -> SuccessNeon
                            2 -> WarningNeon
                            else -> NeonMagenta
                        }
                    )
                }
            }
        }
    }
}

// ============================================================
//  实时波形图卡片
// ============================================================
@Composable
private fun SensorChartCard(
    meta: SensorTypeMeta?,
    histData: Map<String, List<HistoryDataPoint>>,
    sensorType: Int,
    liveData: SensorLiveData?
) {
    val labels = meta?.axisLabels ?: listOf("X", "Y", "Z")
    val valueCount = meta?.valueCount ?: 3
    val prefix = "sensor_${sensorType}"

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "实时波形",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(12.dp))

            // 图例
            if (valueCount > 1) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until valueCount) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(axisColors.getOrElse(i) { NeonPurple })
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            labels.getOrElse(i) { "?" },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (i < valueCount - 1) Spacer(Modifier.width(10.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            // 图表
            if (valueCount == 1) {
                val series = histData["${prefix}_${labels[0]}"] ?: emptyList()
                SensorLineChart(
                    data = series,
                    lineColor = NeonPurpleBright,
                    modifier = Modifier.fillMaxWidth().height(180.dp)
                )
            } else {
                // 多轴图表
                MultiAxisChart(
                    seriesList = labels.mapNotNull { label ->
                        val s = histData["${prefix}_${label}"] ?: emptyList()
                        if (s.isNotEmpty()) s else null
                    }.ifEmpty {
                        labels.map { emptyList() }
                    },
                    colors = axisColors.take(valueCount),
                    modifier = Modifier.fillMaxWidth().height(180.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            // 采样计数
            val sampleCount = histData["${prefix}_${labels[0]}"]?.size ?: 0
            Text(
                "已采样 $sampleCount 个数据点",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End
            )
        }
    }
}

// ============================================================
//  传感器静态信息卡片
// ============================================================
@Composable
private fun SensorInfoCard(sensor: SensorItemInfo, meta: SensorTypeMeta?) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "传感器信息",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(12.dp))

            infoRow("传感器 ID", sensor.sensorId.toString())
            infoRow("传感器名称", sensor.name)
            infoRow("传感器类型", meta?.displayName ?: "传感器 ${sensor.type}")
            infoRow("供应商", sensor.vendor.ifEmpty { sensor.name.split(" ").firstOrNull() ?: "未知" })
            infoRow("版本", if (sensor.version >= 0) sensor.version.toString() else "-")
            infoRow("解析度", if (!sensor.resolution.isNaN()) "%.6f".format(sensor.resolution) else "-")
            infoRow("耗电量", if (!sensor.powerMa.isNaN()) "%.3f mA".format(sensor.powerMa) else "-")
            infoRow("最大范围", if (!sensor.maxRange.isNaN()) {
                val metaUnit = meta?.unit?.takeIf { it.isNotEmpty() }
                if (metaUnit != null) "%.2f %s".format(sensor.maxRange, metaUnit) else "%.2f".format(sensor.maxRange)
            } else "-")
            infoRow("最小延迟", if (sensor.minDelay > 0) "${sensor.minDelay} μs" else "-")
            infoRow("动态传感器", if (sensor.isDynamic) "是" else "否")
            infoRow("唤醒型传感器", if (sensor.isWakeUp) "是" else "否")
            infoRow("报告方式", reportingModeName(sensor.reportingMode))
        }
    }
}

@Composable
private fun infoRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.weight(0.4f)
        )
        Text(
            value,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.6f),
            textAlign = TextAlign.End,
            maxLines = 2
        )
    }
}

private fun reportingModeName(mode: Int): String = when (mode) {
    0 -> "连续 (CONTINUOUS)"
    1 -> "变化时 (ON_CHANGE)"
    2 -> "单次 (ONE_SHOT)"
    3 -> "特殊触发 (SPECIAL_TRIGGER)"
    else -> "未知 ($mode)"
}

// ============================================================
//  单线传感器图表
// ============================================================
@Composable
private fun SensorLineChart(
    data: List<HistoryDataPoint>,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("等待数据...", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val recent = data.takeLast(60)
    val values = recent.map { it.value }
    val minVal = values.minOrNull() ?: 0f
    val maxVal = values.maxOrNull() ?: 1f
    val range = (maxVal - minVal).coerceAtLeast(0.001f)

    val transition = updateTransition(targetState = values, label = "sensorChart")
    val animated = remember(values) {
        values.mapIndexed { i, v -> i to v }
    }.map { (i, _) ->
        val target = values.getOrElse(i) { 0f }
        val anim by transition.animateFloat(
            label = "sv$i",
            transitionSpec = { tween(300) }
        ) { target }
        anim
    }

    Canvas(modifier) {
        val w = size.width; val h = size.height; val pad = 8.dp.toPx()
        val cw = w - pad * 2; val ch = h - pad * 2
        val count = animated.size.coerceAtLeast(1)

        // 网格线
        for (i in 0..5) {
            val y = pad + (ch / 5) * i
            drawLine(DividerCyber, Offset(pad, y), Offset(w - pad, y), 1f)
        }

        // 数据点
        val points = animated.mapIndexed { i, v ->
            Offset(
                pad + cw / (count - 1).coerceAtLeast(1) * i,
                pad + ch - ((v - minVal) / range).coerceIn(0f, 1f) * ch
            )
        }

        // 面积填充
        if (points.size > 1) {
            val areaPath = Path().apply {
                moveTo(points.first().x, h - pad)
                lineTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    val prev = points[i - 1]; val curr = points[i]
                    val cx = prev.x + (curr.x - prev.x) * 0.5f
                    cubicTo(cx, prev.y, cx, curr.y, curr.x, curr.y)
                }
                lineTo(points.last().x, h - pad); close()
            }
            drawPath(
                areaPath,
                Brush.verticalGradient(
                    listOf(lineColor.copy(alpha = 0.2f), lineColor.copy(alpha = 0.02f)),
                    startY = pad, endY = h - pad
                )
            )
        }

        // 曲线
        if (points.size > 1) {
            val linePath = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    val prev = points[i - 1]; val curr = points[i]
                    val cx = prev.x + (curr.x - prev.x) * 0.5f
                    cubicTo(cx, prev.y, cx, curr.y, curr.x, curr.y)
                }
            }
            drawPath(linePath, lineColor, style = Stroke(2.5f, cap = StrokeCap.Round))
        }

        // 当前点高亮
        points.lastOrNull()?.let { drawCircle(lineColor, 5f, it) }
    }
}

// ============================================================
//  多轴传感器图表
// ============================================================
@Composable
private fun MultiAxisChart(
    seriesList: List<List<HistoryDataPoint>>,
    colors: List<Color>,
    modifier: Modifier = Modifier
) {
    val allEmpty = seriesList.all { it.isEmpty() }
    if (allEmpty) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("等待数据...", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    // 全局归一化
    val allValues = seriesList.flatMap { series ->
        series.takeLast(60).map { it.value }
    }
    val globalMin = allValues.minOrNull() ?: 0f
    val globalMax = allValues.maxOrNull() ?: 1f
    val globalRange = (globalMax - globalMin).coerceAtLeast(0.001f)

    Canvas(modifier) {
        val w = size.width; val h = size.height; val pad = 8.dp.toPx()
        val cw = w - pad * 2; val ch = h - pad * 2

        // 网格线
        for (i in 0..5) {
            val y = pad + (ch / 5) * i
            drawLine(DividerCyber, Offset(pad, y), Offset(w - pad, y), 1f)
        }

        // 绘制每条曲线
        seriesList.forEachIndexed { si, series ->
            val recent = series.takeLast(60)
            if (recent.size < 2) return@forEachIndexed
            val color = colors.getOrElse(si) { NeonPurple }

            val points = recent.mapIndexed { i, pt ->
                val norm = ((pt.value - globalMin) / globalRange).coerceIn(0f, 1f)
                Offset(
                    pad + cw / (recent.size - 1).coerceAtLeast(1) * i,
                    pad + ch - norm * ch
                )
            }

            val linePath = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    val prev = points[i - 1]; val curr = points[i]
                    val cx = prev.x + (curr.x - prev.x) * 0.5f
                    cubicTo(cx, prev.y, cx, curr.y, curr.x, curr.y)
                }
            }
            drawPath(linePath, color, style = Stroke(2f, cap = StrokeCap.Round))

            // 末尾点
            points.lastOrNull()?.let { drawCircle(color, 4f, it) }
        }
    }
}
