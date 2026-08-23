package com.rb.cybermonitorpro.ui.sensors

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import com.rb.cybermonitorpro.ui.nightlight.rememberHdrScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.rb.cybermonitorpro.R
import com.rb.cybermonitorpro.data.model.SensorItemInfo
import com.rb.cybermonitorpro.data.model.SensorTypeMeta
import com.rb.cybermonitorpro.ui.effects.cardGradientBorder
import com.rb.cybermonitorpro.ui.effects.cardRipple
import com.rb.cybermonitorpro.ui.theme.NeonPurple
import com.rb.cybermonitorpro.ui.theme.NeonPurpleBright
import com.rb.cybermonitorpro.ui.theme.SuccessNeon
import com.rb.cybermonitorpro.ui.theme.WarningNeon
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import com.rb.cybermonitorpro.ui.effects.staggeredSwipe

/**
 * 传感器列表页 — 现在通过回调将传感器选择上抛给 MainActivity 以全屏覆盖层展示
 * F3: 回调携带卡片中心触点坐标（boundsInRoot），覆盖层从卡片中心圆形展开
 */
@Composable
fun SensorsScreen(
    viewModel: SensorsViewModel = koinViewModel(),
    onNavigateToSensor: (SensorItemInfo, Offset) -> Unit = { _, _ -> }
) {
    val sensors by viewModel.sensors.observeAsState(emptyList())

    SensorListContent(
        sensors = sensors,
        onSensorClick = { sensor, origin -> onNavigateToSensor(sensor, origin) },
        onRefreshSensors = { viewModel.refreshSensors() }
    )
}

@Composable
private fun SensorListContent(
    sensors: List<SensorItemInfo>,
    onSensorClick: (SensorItemInfo, Offset) -> Unit,
    onRefreshSensors: () -> Unit
) {
    val ctx = LocalContext.current
    // pre20 红线: 列表滚动必须用 rememberHdrScrollState (上报垂直滚动状态给 HDR 贴片渲染)
    val scrollState = rememberHdrScrollState()
    var query by remember { mutableStateOf("") }
    var highlightedIdx by remember { mutableStateOf(-1) }
    // 滚动列表容器顶部在根坐标系的 Y (视口锚点)
    var listRootTopPx by remember { mutableStateOf(0f) }
    // 各卡片顶部在根坐标系的 Y (随滚动实时更新, onGloballyPositioned 合并写入)
    val cardTops = remember { mutableStateMapOf<Int, Float>() }
    val density = LocalDensity.current

    // 方案Y: 输入即定位首条匹配 — 滚动跳转 + 卡片脉冲高亮 (列表保持完整, 不做过滤)
    LaunchedEffect(query) {
        // 纯空格 query 不参与匹配 (否则 haystack 含空格恒命中第一张卡 → 跳顶+脉冲)
        val q = query.trim()
        if (q.isEmpty()) {
            highlightedIdx = -1
            return@LaunchedEffect
        }
        val matchIdx = sensors.indexOfFirst { matchesQuery(it, q, ctx) }
        if (matchIdx >= 0) {
            highlightedIdx = matchIdx
            val cardTop = cardTops[matchIdx] ?: return@LaunchedEffect
            // cardTops 是当前滚动状态下的视口坐标 (boundsInRoot 已含滚动平移),
            // 补偿当前滚动量得到未滚动布局位置, 再留 12dp 顶部呼吸间距
            val target = cardTop + scrollState.value - listRootTopPx -
                    with(density) { 12.dp.toPx() }
            scrollState.animateScrollTo(target.toInt().coerceIn(0, scrollState.maxValue))
        }
    }

    // ── ACTIVITY_RECOGNITION (API 29+): 步数传感器运行时权限 ──
    // 未授权时 STEP_COUNTER/STEP_DETECTOR 直接不出现在 getSensorList, 授权后需重采列表
    var permGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 29 ||
                ContextCompat.checkSelfPermission(
                    ctx, Manifest.permission.ACTIVITY_RECOGNITION
                ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permGranted = granted
        if (granted) onRefreshSensors()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // ── 固定头部 (不随列表滚动): 标题 + 搜索框 ──
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.sensor_list_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            SensorSearchField(
                query = query,
                onQueryChange = { query = it }
            )
        }
        Text(
            stringResource(R.string.sensor_list_count, sensors.size),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // ── 权限提示行: API 29+ 未授权 ACTIVITY_RECOGNITION 时显示, 授权后消失 ──
        if (Build.VERSION.SDK_INT >= 29 && !permGranted) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(R.string.sensor_perm_activity_hint),
                    fontSize = 12.sp,
                    color = WarningNeon,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { permLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
                ) {
                    Text(
                        stringResource(R.string.sensor_perm_activity_grant),
                        fontSize = 12.sp
                    )
                }
            }
        }

        // ── 滚动列表 (完整列表) ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState)
                // 内容 padding: 跟随滚动, 恢复滚动到底时末张卡距视口底边 16dp
                // (verticalScroll 之后 = 内容 padding; 不影响 top 坐标采集)
                .padding(bottom = 16.dp)
                .onGloballyPositioned { listRootTopPx = it.boundsInRoot().top },
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            sensors.forEachIndexed { idx, sensor ->
                SensorItemCard(
                    sensor = sensor,
                    highlighted = idx == highlightedIdx,
                    onClick = { origin -> onSensorClick(sensor, origin) },
                    onCardPositioned = { top -> cardTops[idx] = top },
                    modifier = Modifier.staggeredSwipe(idx)
                )
            }

            // OIS/EIS 预期管理: 用户在传感器页找不到 OIS(它属相机子系统, 不在 Sensor API)
            Text(
                stringResource(R.string.sensor_list_footnote),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

/** 搜索匹配: 归纳名(含 stringType 回退) / 硬件名 / 厂商, 大小写不敏感 contains */
private fun matchesQuery(sensor: SensorItemInfo, q: String, ctx: Context): Boolean {
    if (q.isEmpty()) return false
    val haystack = listOf(
        SensorTypeMeta.getDisplayName(sensor.type, ctx, sensor.stringType),
        sensor.name,
        sensor.vendor
    ).joinToString(" ").lowercase()
    return haystack.contains(q.lowercase())
}

/**
 * 传感器搜索框 — 霓虹主题风格, 输入即匹配传感器名称并滚动定位到对应卡片
 */
@Composable
private fun SensorSearchField(
    query: String,
    onQueryChange: (String) -> Unit
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        modifier = Modifier.widthIn(max = 210.dp),
        placeholder = {
            Text(
                stringResource(R.string.sensor_search_hint),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        },
        leadingIcon = { Text("🔍", fontSize = 14.sp) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                Text(
                    "✕",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { onQueryChange("") }
                )
            }
        },
        shape = RoundedCornerShape(14.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = NeonPurple.copy(alpha = 0.10f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
            focusedIndicatorColor = NeonPurpleBright,
            unfocusedIndicatorColor = NeonPurple.copy(alpha = 0.4f),
            cursorColor = NeonPurpleBright
        ),
        textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp)
    )
}

@Composable
private fun SensorItemCard(
    sensor: SensorItemInfo,
    highlighted: Boolean,
    onClick: (Offset) -> Unit,
    onCardPositioned: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val meta = SensorTypeMeta.fromTypeId(sensor.type)
    val ctx = LocalContext.current
    // F3: 卡片中心触点（boundsInRoot; RIPPLE-04: 偏移异常时降级 positionInWindow 换算）
    var cardCenter by remember { mutableStateOf(Offset.Zero) }

    // 搜索定位脉冲: scale 微弹 + 辉光淡出, highlighted 变 true 时播放一次
    val pulse = remember { Animatable(1f) }
    val glow = remember { Animatable(0f) }
    LaunchedEffect(highlighted) {
        if (highlighted) {
            launch {
                pulse.animateTo(1.04f, tween(180))
                pulse.animateTo(1f, tween(420))
            }
            launch {
                glow.animateTo(1f, tween(180))
                glow.animateTo(0f, tween(600))
            }
        }
    }

    val cardModifier = Modifier
        .fillMaxWidth()
        .onGloballyPositioned {
            cardCenter = it.boundsInRoot().center
            onCardPositioned(it.boundsInRoot().top)
        }
        .cardGradientBorder(20.dp, hdrHighlight = true)
        .cardRipple(onClick = { onClick(cardCenter) })

    val cardContent: @Composable ColumnScope.() -> Unit = {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    SensorTypeMeta.getDisplayName(sensor.type, ctx, sensor.stringType),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                // 可监控标识
                if (meta != null) {
                    Text(
                        "\u25B6",
                        fontSize = 14.sp,
                        color = NeonPurpleBright
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    sensor.vendor.ifEmpty { sensor.name.split(" ").firstOrNull() ?: stringResource(R.string.sensor_unknown_vendor) },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (sensor.isWakeUp) {
                        Text(
                            stringResource(R.string.sensor_tag_wakeup),
                            fontSize = 11.sp,
                            color = WarningNeon
                        )
                    }
                    if (sensor.isDynamic) {
                        Text(
                            stringResource(R.string.sensor_tag_dynamic),
                            fontSize = 11.sp,
                            color = SuccessNeon
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.sensor_range_label, sensor.maxRange, meta?.unit?.takeIf { it.isNotEmpty() } ?: ""),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }

    // 外包 Box 承载脉冲 scale + 辉光; 卡片描边/水波纹修饰符顺序不变
    // (KB §3 红线: cardGradientBorder 在 OUTER、cardRipple 在 INNER)
    Box(
        modifier
            .graphicsLayer {
                scaleX = pulse.value
                scaleY = pulse.value
            }
            .drawBehind {
                // 脉冲辉光 halo: 向外扩 8dp 画在卡片后面 — 与卡片同尺寸会被不透明 surface 完全遮住,
                // 外扩后露出边缘光环; Compose 默认不裁剪超出布局边界的绘制, 滚动视口内不会被裁
                if (glow.value > 0f) {
                    val inflate = 8.dp.toPx()
                    drawRoundRect(
                        color = NeonPurpleBright,
                        alpha = glow.value * 0.35f,
                        topLeft = Offset(-inflate, -inflate),
                        size = Size(size.width + inflate * 2, size.height + inflate * 2),
                        cornerRadius = CornerRadius(20.dp.toPx() + inflate)
                    )
                }
            }
    ) {
        Card(
            cardModifier,
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            content = cardContent
        )
    }
}
