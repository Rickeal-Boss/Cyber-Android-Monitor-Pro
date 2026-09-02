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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
    // 仅"键盘搜索/完成键"提交后用于索引定位, 避免每次按键即时跳动
    var submittedQuery by remember { mutableStateOf("") }
    // 改动 2: 搜索触发计数 searchTrigger — 仅在 onCommit 自增, 作索引定位 LaunchedEffect 的 key;
    // 即使同查询重复提交也能重新触发单步定位 (修复偶发不触发)。
    var searchTrigger by remember { mutableIntStateOf(0) }
    // 改动 3: 单步定位游标 searchStep — 记录当前走到第几个匹配, 到末张后环绕回顶部 (重复按搜索键继续下一步)。
    var searchStep by remember { mutableIntStateOf(0) }
    // 双计数器(改动 3 时序修正): pulseTick 为独立脉冲计数, 仅由索引 LaunchedEffect 在写入 highlightedIdx
    // 的同一批内自增, 使脉冲精确打在正确高亮卡一次; 卡片维持 LaunchedEffect(pulseTick), 不用 (pulseTick, highlighted)。
    var pulseTick by remember { mutableIntStateOf(0) }
    var highlightedIdx by remember { mutableStateOf(-1) }
    // 滚动列表容器顶部在根坐标系的 Y (视口锚点)
    var listRootTopPx by remember { mutableStateOf(0f) }
    // 各卡片顶部在根坐标系的 Y (随滚动实时更新, onGloballyPositioned 合并写入)
    val cardTops = remember { mutableStateMapOf<Int, Float>() }
    val density = LocalDensity.current

    // 改动 4: 进入传感器覆盖层 (卡片点击导航) 前关闭输入法所需句柄
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val view = LocalView.current

    // 改动 2+3: 单步定位 — 仅以 searchTrigger 为 key, 每次 onCommit「搜索键」+1 即重跑 (同查询重复提交也能再触发)。
    // 多匹配时自上而下逐步推进, 到末张后环绕回顶部 (重复按搜索键继续下一步); 不再用 delay 多匹配循环。
    LaunchedEffect(searchTrigger) {
        if (searchTrigger == 0) return@LaunchedEffect   // 初始未搜索, 不触发
        // 纯空格/空 query 不参与匹配 (否则 haystack 含空格恒命中第一张卡 → 跳顶+脉冲)
        val q = submittedQuery
        if (q.isEmpty()) { highlightedIdx = -1; return@LaunchedEffect }
        val matchList = sensors.mapIndexedNotNull { idx, s ->
            if (matchesQuery(s, q, ctx)) idx else null
        }
        if (matchList.isEmpty()) { highlightedIdx = -1; return@LaunchedEffect }
        // 自上而下逐步推进, 到末张后环绕回顶部 (重复按搜索键继续下一步)
        val matchIdx = matchList[searchStep % matchList.size]
        searchStep++
        highlightedIdx = matchIdx
        // 双计数器(改动 3 时序修正): pulseTick 与 highlightedIdx 同批写入, 脉冲仅打在正确高亮卡一次
        pulseTick++
        withFrameNanos { }
        // 坐标就绪等待: 沿用「等帧 + snapshotFlow + 超时」, 并强化兜底
        val cardTop = cardTops[matchIdx]
            ?: withTimeoutOrNull(2000L) {
                snapshotFlow { cardTops[matchIdx] }.filterNotNull().first()
            }
        if (cardTop != null) {
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

    // ── BODY_SENSORS (API 23+): 心率传感器运行时权限 — 2026-09-01 审查补漏 ──
    //   未授权时 TYPE_HEART_RATE 不出现在 getSensorList(多数 ROM) 或不返回样本 → 心率功能静默失效;
    //   与 ACTIVITY_RECOGNITION 同模式: 授权后重采列表回填心率传感器
    var bodyPermGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 23 ||
                ContextCompat.checkSelfPermission(
                    ctx, Manifest.permission.BODY_SENSORS
                ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val bodyPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        bodyPermGranted = granted
        if (granted) onRefreshSensors()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
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
                onQueryChange = { query = it },
                onCommit = {
                    val newQ = query.trim()
                    if (newQ != submittedQuery) searchStep = 0   // 查询变化 → 新查询从顶部开始
                    submittedQuery = newQ
                    searchTrigger++
                },
                onClear = { query = ""; submittedQuery = ""; searchStep = 0; searchTrigger++ },
                focusRequester = focusRequester
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

        // ── 权限提示行: API 23+ 未授权 BODY_SENSORS 时显示, 授权后消失 (心率传感器) ──
        if (Build.VERSION.SDK_INT >= 23 && !bodyPermGranted) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(R.string.sensor_perm_body_hint),
                    fontSize = 12.sp,
                    color = WarningNeon,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { bodyPermLauncher.launch(Manifest.permission.BODY_SENSORS) },
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
                    pulseTick = pulseTick,
                    onClick = { origin ->
                        // 改动 4: 进入覆盖层前关闭输入法, 避免返回键/覆盖层动画被打断
                        focusRequester.freeFocus()
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        // 国产 OEM 健壮性兜底: 部分小米/OPPO/vivo 输入法对 controller.hide() 不响应
                        val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                        imm.hideSoftInputFromWindow(view.windowToken, 0)
                        onSensorClick(sensor, origin)
                    },
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

/** 搜索匹配: 命中任一搜索别名 (P1-b 三语标题/硬件名/厂商, 预计算) 即算匹配, 大小写不敏感 contains */
private fun matchesQuery(sensor: SensorItemInfo, q: String, ctx: Context): Boolean {
    if (q.isEmpty()) return false
    // searchAliases 由 SensorDataSource.getAllSensors 预计算; 防御性回退到旧 haystack
    // (手工构造的 SensorItemInfo 可能未填充 searchAliases, 如单测)
    val aliases = sensor.searchAliases.ifEmpty {
        listOf(
            SensorTypeMeta.getDisplayName(sensor.type, ctx, sensor.stringType),
            sensor.name,
            sensor.vendor
        ).filter { it.isNotBlank() }
    }
    return aliases.any { it.contains(q, ignoreCase = true) }
}

/**
 * 传感器搜索框 — 霓虹主题风格, 输入即匹配传感器名称并滚动定位到对应卡片
 */
@Composable
private fun SensorSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onCommit: () -> Unit,
    onClear: () -> Unit,
    focusRequester: FocusRequester
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        modifier = Modifier.widthIn(max = 210.dp).focusRequester(focusRequester),
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
                    modifier = Modifier.clickable { onClear() }
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
        textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onCommit() })
    )
}

@Composable
private fun SensorItemCard(
    sensor: SensorItemInfo,
    highlighted: Boolean,
    pulseTick: Int,
    onClick: (Offset) -> Unit,
    onCardPositioned: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val meta = SensorTypeMeta.fromTypeId(sensor.type)
    val ctx = LocalContext.current
    // F3: 卡片中心触点（boundsInRoot; RIPPLE-04: 偏移异常时降级 positionInWindow 换算）
    var cardCenter by remember { mutableStateOf(Offset.Zero) }

    // 搜索定位脉冲: scale 微弹 + 辉光淡出
    // 改动 3(双计数器): 脉冲以独立 pulseTick 为 key, 由索引 LaunchedEffect 与 highlightedIdx 同批自增触发;
    // 因此每次 onCommit 仅高亮卡被重播一次, 单匹配重复按仍重播, 且不会误打旧卡 (不用 (pulseTick, highlighted))。
    // 仅当本卡被高亮 (highlighted) 时才播放。
    val pulse = remember { Animatable(1f) }
    val glow = remember { Animatable(0f) }
    LaunchedEffect(pulseTick) {
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
    // 改动 3(P2): 取消高亮即复位 scale/glow, 避免旧 glow 动画被取消后冻结在中间值留下鬼影光环
    LaunchedEffect(highlighted) {
        if (!highlighted) {
            pulse.snapTo(1f)
            glow.snapTo(0f)
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
