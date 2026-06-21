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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.deviceinfoviewer.R
import com.example.deviceinfoviewer.data.model.HistoryDataPoint
import com.example.deviceinfoviewer.data.model.SensorItemInfo
import com.example.deviceinfoviewer.data.model.SensorLiveData
import com.example.deviceinfoviewer.data.model.SensorTypeMeta
import com.example.deviceinfoviewer.ui.components.GlowBackButton
import com.example.deviceinfoviewer.ui.theme.*
import org.koin.androidx.compose.koinViewModel

// 轴颜色映射
private val axisColors = listOf(NeonPurple, NeonCyan, NeonMagenta)

/**
 * 传感器详情页 — 全屏沉浸式风格 (对齐设置/悬浮窗覆盖层)
 * 包含: 实时数值卡片(含照度等级/距离状态) + 波形图 + 静态信息
 */
@Composable
fun SensorDetailScreen(
    sensor: SensorItemInfo,
    onBack: () -> Unit,
    viewModel: SensorDetailViewModel = koinViewModel()
) {
    val meta = SensorTypeMeta.fromTypeId(sensor.type)
    val liveData by viewModel.liveData.observeAsState()

    // ★ 改用本地 Compose 状态列表 + LaunchedEffect, 直接消费 sensorLiveData
    //   避免依赖 repo.sensorHistoryData 的间接 LiveData 管线
    val chartPoints = remember { mutableStateListOf<HistoryDataPoint>() }
    // 多轴传感器: X/Y/Z 分别累积
    val chartPointsX = remember { mutableStateListOf<HistoryDataPoint>() }
    val chartPointsY = remember { mutableStateListOf<HistoryDataPoint>() }
    val chartPointsZ = remember { mutableStateListOf<HistoryDataPoint>() }
    val maxChartPoints = 80
    val isSingleAxis = meta?.valueCount == 1
    LaunchedEffect(liveData) {
        liveData?.let { ld ->
            if (isSingleAxis) {
                val idx = when (meta) {
                    SensorTypeMeta.LIGHT,
                    SensorTypeMeta.PROXIMITY,
                    SensorTypeMeta.PRESSURE,
                    SensorTypeMeta.HUMIDITY,
                    SensorTypeMeta.AMBIENT_TEMPERATURE -> 0
                    else -> -1
                }
                if (idx >= 0 && idx < ld.valueCount && !ld.values[idx].isNaN()) {
                    val pt = HistoryDataPoint(System.currentTimeMillis(), ld.values[idx], "")
                    if (chartPoints.size >= maxChartPoints) chartPoints.removeAt(0)
                    chartPoints.add(pt)
                }
            } else {
                // 多轴传感器: X/Y/Z 独立累积
                val axes = arrayOf(chartPointsX, chartPointsY, chartPointsZ)
                for (axisIdx in 0 until minOf(ld.valueCount, 3)) {
                    if (!ld.values[axisIdx].isNaN()) {
                        val pt = HistoryDataPoint(System.currentTimeMillis(), ld.values[axisIdx],
                            when (axisIdx) { 0 -> "X"; 1 -> "Y"; else -> "Z" })
                        val list = axes[axisIdx]
                        if (list.size >= maxChartPoints) list.removeAt(0)
                        list.add(pt)
                    }
                }
            }
        }
    }

    // 生命周期管理: 进入页面启动，离开页面停止
    DisposableEffect(sensor) {
        viewModel.startListening(sensor)
        onDispose { viewModel.stopListening() }
    }

    val scrollState = rememberScrollState()

    // ── 全屏沉浸式 Box (对齐设置页/悬浮窗覆盖层风格) ──
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(start = 16.dp, end = 16.dp, top = 50.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── 传感器名称 ──
            Text(
                SensorTypeMeta.getDisplayName(sensor.type, LocalContext.current),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (meta != null) {
                // ── 命名规范 (2026-06-20): 副标题统一为"厂商 · 硬件名",
                //   不再显示 type 数字 (用户无感), 硬件名在信息卡单独展示 ──
                val vendorLabel = sensor.vendor.ifEmpty { sensor.name.split(" ").firstOrNull() ?: "" }
                Text(
                    if (vendorLabel.isNotEmpty()) "$vendorLabel · ${sensor.name}"
                    else sensor.name,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── 实时数值卡片 (含光线等级/距离状态) ──
            SensorValueCard(sensor = sensor, meta = meta, liveData = liveData)

            // ── 实时波形图 (增强动画) ──
            SensorChartCard(
                meta = meta,
                chartPoints = if (isSingleAxis) chartPoints else emptyList(),
                chartPointsX = chartPointsX,
                chartPointsY = chartPointsY,
                chartPointsZ = chartPointsZ,
                liveData = liveData
            )

            // ── 传感器静态信息 ──
            SensorInfoCard(sensor = sensor, meta = meta)
        }

        // ── 沉浸式返回按钮 (玻璃质感 + 辉光动效) ──
        GlowBackButton(
            onClick = onBack,
            btnSize = 48.dp,
            modifier = Modifier
                .padding(top = 8.dp, start = 16.dp)
                .align(Alignment.TopStart)
        )
    }
}

// ============================================================
//  实时数值卡片 (增强: 光线全量程、距离多档)
// ============================================================
@Composable
private fun SensorValueCard(
    sensor: SensorItemInfo,
    meta: SensorTypeMeta?,
    liveData: SensorLiveData?
) {
    val valueCount = meta?.valueCount ?: 3
    val ctx = LocalContext.current
    val labels = meta?.axisLabelResIds?.map { ctx.getString(it) } ?: listOf("X", "Y", "Z")
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

            if (valueCount == 1) {
                // ── 单值传感器: 光线 / 距离 / 压力 ──
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

                // ── 光线传感器: 全量程照度等级 ──
                if (meta == SensorTypeMeta.LIGHT && !value.isNaN()) {
                    Spacer(Modifier.height(6.dp))
                    val lightDesc = SensorTypeMeta.describeLightLevel(value, ctx)
                    Text(
                        lightDesc,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            value <= 10f -> NeonCyan     // 暗光 → 青色
                            value <= 500f -> NeonPurple  // 室内 → 紫色
                            value <= 10000f -> SuccessNeon // 室外 → 绿色
                            else -> NeonMagenta          // 强光 → 品红
                        }
                    )
                }

                // ── 距离传感器: 多档连续追踪 ──
                if (meta == SensorTypeMeta.PROXIMITY && !value.isNaN()) {
                    Spacer(Modifier.height(6.dp))
                    val stateDesc = SensorTypeMeta.describeProximityState(value, sensor.maxRange, ctx)
                    Text(
                        stateDesc,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            value <= 0.5f -> NeonMagenta
                            value <= 2f -> WarningNeon
                            else -> SuccessNeon
                        }
                    )
                    // 连续距离采样指示器
                    Spacer(Modifier.height(6.dp))
                    // ★ 安全除法: maxRange 异常 (NaN/0/负数) 时降级为典型值 5cm, 避免除零/NaN 传播
                    val safeMaxRange = when {
                        sensor.maxRange.isNaN() || sensor.maxRange.isInfinite() -> 5f
                        sensor.maxRange <= 0.001f -> 5f
                        else -> sensor.maxRange
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 距离变化趋势指示器（基于当前值对比最大范围）
                        val pct = (value / safeMaxRange).coerceIn(0f, 1f)
                        Box(
                            Modifier
                                .width(120.dp)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(NeonPurpleDeep.copy(alpha = 0.3f))
                        ) {
                            Box(
                                Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fraction = pct)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        when {
                                            pct <= 0.1f -> NeonMagenta
                                            pct <= 0.3f -> WarningNeon
                                            else -> SuccessNeon
                                        }
                                    )
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${(pct * 100).toInt()}%",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // ★ 修复 (2026-06-20): 同 describeProximityState 的格式化 BUG
                    //   资源 sensor_detail_max_range = "Max range: %.1f cm", 占位符 %.1f 期望 Float
                    //   原代码 stringResource(resId, "%.1f".format(safeMaxRange)) 传入 String →
                    //   IllegalFormatConversionException: f != java.lang.String → 距离传感器
                    //   首次 onSensorChanged 回调后 value 有值, 此 Text 渲染即崩溃 (点击唤醒触发闪退根因)
                    //   修复: 直接传 Float, 让 String.format 内部处理格式化
                    Text(
                        stringResource(R.string.sensor_detail_max_range, safeMaxRange),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            } else {
                // ── 多轴传感器: X/Y/Z 三列 ──
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

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                        stringResource(R.string.sensor_detail_unit_label, unit),
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
                            3 -> stringResource(R.string.sensor_detail_accuracy_high)
                            2 -> stringResource(R.string.sensor_detail_accuracy_medium)
                            1 -> stringResource(R.string.sensor_detail_accuracy_low)
                            else -> stringResource(R.string.sensor_detail_accuracy_unreliable)
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
//  实时波形图卡片 (增强: 动态动画 + 实时数据点跟踪)
// ============================================================
@Composable
private fun SensorChartCard(
    meta: SensorTypeMeta?,
    chartPoints: List<HistoryDataPoint>,
    chartPointsX: List<HistoryDataPoint>,
    chartPointsY: List<HistoryDataPoint>,
    chartPointsZ: List<HistoryDataPoint>,
    liveData: SensorLiveData?
) {
    val labels = meta?.axisLabelResIds?.map { LocalContext.current.getString(it) } ?: listOf("X", "Y", "Z")
    val valueCount = meta?.valueCount ?: 3
    // ★ 数据点计数 — 提升到 Column 作用域, 供标题栏和底部共用 (2026-06-20 修复 Unresolved reference)
    val sampleCount = when {
        valueCount == 1 -> chartPoints.size
        valueCount >= 2 -> chartPointsX.size
        else -> 0
    }

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.sensor_detail_chart_title),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "$sampleCount pts",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            Spacer(Modifier.height(8.dp))

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

            // 图表 (增强: 动画 + 面积填充)
            if (valueCount == 1) {
                SensorLineChart(
                    data = chartPoints,
                    lineColor = NeonPurpleBright,
                    modifier = Modifier.fillMaxWidth().height(200.dp)
                )
            } else {
                // ★ 性能优化 (2026-06-20): 移除 toList() 拷贝
                //   mutableStateListOf 是 List 的子类, 可直接传递;
                //   原方案每次重组都 toList() 创建 3 个新 ArrayList, 加剧 GC 压力
                MultiAxisChart(
                    seriesList = listOf(chartPointsX, chartPointsY, chartPointsZ)
                        .take(valueCount),
                    colors = axisColors.take(valueCount),
                    modifier = Modifier.fillMaxWidth().height(200.dp)
                )
            }

            // 实时数值指示 (底部)
            if (liveData != null) {
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.sensor_detail_realtime_label, liveData.values.joinToString(", ") { "%.2f".format(it) }),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.sensor_detail_point_count, sampleCount),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
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
                stringResource(R.string.sensor_info_title),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(12.dp))

            infoRow(stringResource(R.string.sensor_info_id), sensor.sensorId.toString())
            infoRow(stringResource(R.string.sensor_info_name), SensorTypeMeta.getDisplayName(sensor.type, LocalContext.current))
            infoRow(stringResource(R.string.sensor_info_hardware), sensor.name)
            infoRow(stringResource(R.string.sensor_info_type_id), "${sensor.type}  (${reportingModeName(sensor.reportingMode)})")
            infoRow(stringResource(R.string.sensor_info_vendor), sensor.vendor.ifEmpty { sensor.name.split(" ").firstOrNull() ?: stringResource(R.string.common_unknown) })
            infoRow(stringResource(R.string.sensor_info_version), if (sensor.version >= 0) sensor.version.toString() else "-")
            infoRow(stringResource(R.string.sensor_info_resolution), if (!sensor.resolution.isNaN()) "%.6f".format(sensor.resolution) else "-")
            infoRow(stringResource(R.string.sensor_info_power), if (!sensor.powerMa.isNaN()) "%.3f mA".format(sensor.powerMa) else "-")
            infoRow(stringResource(R.string.sensor_info_max_range), if (!sensor.maxRange.isNaN()) {
                val metaUnit = meta?.unit?.takeIf { it.isNotEmpty() }
                if (metaUnit != null) "%.2f %s".format(sensor.maxRange, metaUnit) else "%.2f".format(sensor.maxRange)
            } else "-")
            infoRow(stringResource(R.string.sensor_info_min_delay), if (sensor.minDelay > 0) "${sensor.minDelay} μs" else "-")
            infoRow(stringResource(R.string.sensor_info_is_dynamic), if (sensor.isDynamic) stringResource(R.string.common_yes) else stringResource(R.string.common_no))
            infoRow(stringResource(R.string.sensor_info_is_wakeup), if (sensor.isWakeUp) stringResource(R.string.common_yes) else stringResource(R.string.common_no))
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

@Composable
private fun reportingModeName(mode: Int): String = when (mode) {
    0 -> stringResource(R.string.sensor_mode_continuous)
    1 -> stringResource(R.string.sensor_mode_on_change)
    2 -> stringResource(R.string.sensor_mode_one_shot)
    3 -> stringResource(R.string.sensor_mode_special_trigger)
    else -> stringResource(R.string.sensor_mode_unknown, mode)
}

// ============================================================
//  单线传感器图表 (增强: 更平滑动画 + 动态范围自适应)
// ============================================================
@Composable
private fun SensorLineChart(
    data: List<HistoryDataPoint>,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.common_waiting_data), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val recent = remember(data) { data.takeLast(80) }
    val values = remember(recent) { recent.map { it.value } }
    val minVal = remember(values) { values.minOrNull() ?: 0f }
    val maxVal = remember(values) { values.maxOrNull() ?: 1f }
    val range = remember(minVal, maxVal) { (maxVal - minVal).coerceAtLeast(0.001f) }
    val gridColor = remember { NeonCyan.copy(alpha = 0.12f) }
    val axisColor = remember { NeonPurple.copy(alpha = 0.45f) }
    val labelColor = remember { NeonPurpleBright.copy(alpha = 0.55f) }

    Canvas(modifier) {
        val w = size.width; val h = size.height; val pad = 14.dp.toPx()
        val leftPad = 36.dp.toPx()
        val cw = w - pad - leftPad; val ch = h - pad * 2
        val count = values.size.coerceAtLeast(1)

        // ── 图表区背景 (半透明暗紫, 提供视觉边界) ──
        drawRect(
            CyberCardStart.copy(alpha = 0.25f),
            Offset(leftPad, pad),
            size = androidx.compose.ui.geometry.Size(cw + pad, ch)
        )

        // ── 网格线 (青色调, 微妙但可见) ──
        for (i in 0..4) {
            val y = pad + (ch / 4) * i
            drawLine(gridColor, Offset(leftPad, y), Offset(w - pad, y), 0.5f)
        }

        // ── Y 轴 (左) & X 轴 (底) ──
        drawLine(axisColor, Offset(leftPad, pad), Offset(leftPad, pad + ch), 1.5f)
        drawLine(axisColor, Offset(leftPad, pad + ch), Offset(w - pad, pad + ch), 1.5f)

        // ── 数据点 ──
        val points = values.mapIndexed { i, v ->
            Offset(
                leftPad + cw / (count - 1).coerceAtLeast(1) * i,
                pad + ch - ((v - minVal) / range).coerceIn(0f, 1f) * ch
            )
        }

        // ── 面积填充 ──
        if (points.size > 1) {
            val areaPath = Path().apply {
                moveTo(points.first().x, pad + ch)
                lineTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    val prev = points[i - 1]; val curr = points[i]
                    val cx = prev.x + (curr.x - prev.x) * 0.5f
                    cubicTo(cx, prev.y, cx, curr.y, curr.x, curr.y)
                }
                lineTo(points.last().x, pad + ch); close()
            }
            drawPath(
                areaPath,
                Brush.verticalGradient(
                    listOf(lineColor.copy(alpha = 0.18f), lineColor.copy(alpha = 0.01f)),
                    startY = pad, endY = pad + ch
                )
            )
        }

        // ── 曲线 ──
        if (points.size > 1) {
            val linePath = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    val prev = points[i - 1]; val curr = points[i]
                    val cx = prev.x + (curr.x - prev.x) * 0.5f
                    cubicTo(cx, prev.y, cx, curr.y, curr.x, curr.y)
                }
            }
            drawPath(linePath, lineColor, style = Stroke(3f, cap = StrokeCap.Round))
        }

        // ── 当前点高亮 ──
        points.lastOrNull()?.let {
            drawCircle(lineColor.copy(alpha = 0.25f), 11f, it)
            drawCircle(lineColor, 5.5f, it)
        }
    }
}

// ============================================================
//  多轴传感器图表 (增强: 更平滑动画)
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
            Text(stringResource(R.string.common_waiting_data), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val recentSeries = seriesList.map { it.takeLast(80) }
    val allValues = recentSeries.flatMap { series -> series.map { it.value } }
    val globalMin = allValues.minOrNull() ?: 0f
    val globalMax = allValues.maxOrNull() ?: 1f
    val globalRange = (globalMax - globalMin).coerceAtLeast(0.001f)
    val gridColor = remember { NeonCyan.copy(alpha = 0.12f) }
    val axisColor = remember { NeonPurple.copy(alpha = 0.45f) }

    Canvas(modifier) {
        val w = size.width; val h = size.height; val pad = 14.dp.toPx()
        val leftPad = 36.dp.toPx()
        val cw = w - pad - leftPad; val ch = h - pad * 2

        // ── 图表区背景 ──
        drawRect(
            CyberCardStart.copy(alpha = 0.25f),
            Offset(leftPad, pad),
            size = androidx.compose.ui.geometry.Size(cw + pad, ch)
        )

        // ── 网格线 ──
        for (i in 0..4) {
            val y = pad + (ch / 4) * i
            drawLine(gridColor, Offset(leftPad, y), Offset(w - pad, y), 0.5f)
        }

        // ── 轴 ──
        drawLine(axisColor, Offset(leftPad, pad), Offset(leftPad, pad + ch), 1.5f)
        drawLine(axisColor, Offset(leftPad, pad + ch), Offset(w - pad, pad + ch), 1.5f)

        // ── 绘制每条曲线 ──
        recentSeries.forEachIndexed { si, series ->
            if (series.size < 2) return@forEachIndexed
            val color = colors.getOrElse(si) { NeonPurple }

            val points = series.mapIndexed { i, pt ->
                val norm = ((pt.value - globalMin) / globalRange).coerceIn(0f, 1f)
                Offset(
                    leftPad + cw / (series.size - 1).coerceAtLeast(1) * i,
                    pad + ch - norm * ch
                )
            }

            // 面积填充
            if (points.size > 1) {
                val areaPath = Path().apply {
                    moveTo(points.first().x, pad + ch)
                    lineTo(points.first().x, points.first().y)
                    for (i in 1 until points.size) {
                        val prev = points[i - 1]; val curr = points[i]
                        val cx = prev.x + (curr.x - prev.x) * 0.5f
                        cubicTo(cx, prev.y, cx, curr.y, curr.x, curr.y)
                    }
                    lineTo(points.last().x, pad + ch); close()
                }
                drawPath(areaPath, color.copy(alpha = 0.07f), style = Stroke(1f))
            }

            // 曲线
            val linePath = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    val prev = points[i - 1]; val curr = points[i]
                    val cx = prev.x + (curr.x - prev.x) * 0.5f
                    cubicTo(cx, prev.y, cx, curr.y, curr.x, curr.y)
                }
            }
            drawPath(linePath, color, style = Stroke(2.5f, cap = StrokeCap.Round))

            // 末尾点高亮
            points.lastOrNull()?.let {
                drawCircle(color.copy(alpha = 0.25f), 9f, it)
                drawCircle(color, 4.5f, it)
            }
        }
    }
}
