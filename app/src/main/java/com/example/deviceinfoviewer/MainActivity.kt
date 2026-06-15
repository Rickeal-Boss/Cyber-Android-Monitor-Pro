package com.example.deviceinfoviewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.deviceinfoviewer.ui.AppViewModel
import com.example.deviceinfoviewer.ui.battery.BatteryScreen
import com.example.deviceinfoviewer.ui.components.GlowBackButton
import com.example.deviceinfoviewer.ui.components.NeonDivider
import com.example.deviceinfoviewer.ui.components.NeonHeaderDecoration
import com.example.deviceinfoviewer.ui.cpu.CpuScreen
import com.example.deviceinfoviewer.ui.dashboard.DashboardScreen
import com.example.deviceinfoviewer.ui.device.DeviceScreen
import com.example.deviceinfoviewer.ui.floatwindow.FloatingWindowScreen
import com.example.deviceinfoviewer.ui.gps.GpsScreen
import com.example.deviceinfoviewer.ui.gpu.GpuScreen
import com.example.deviceinfoviewer.ui.memory.MemoryScreen
import com.example.deviceinfoviewer.ui.network.NetworkScreen
import com.example.deviceinfoviewer.ui.sensors.SensorDetailScreen
import com.example.deviceinfoviewer.ui.sensors.SensorsScreen
import com.example.deviceinfoviewer.ui.settings.SettingsScreen
import com.example.deviceinfoviewer.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        configureSystemBars()
        setContent {
            DeviceInfoViewerTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SystemMonitorApp()
                }
            }
        }
    }
    private fun configureSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
    }
}

private data class TopTabItem(val title: String, val icon: ImageVector)

private val topTabs = listOf(
    TopTabItem("概览", Icons.Default.Home),
    TopTabItem("CPU", Icons.Default.PlayArrow),
    TopTabItem("GPU", Icons.Default.Info),
    TopTabItem("内存", Icons.Default.Star),
    TopTabItem("电池", Icons.Default.Favorite),
    TopTabItem("网络", Icons.Default.Share),
    TopTabItem("GPS", Icons.Default.PlayArrow),
    TopTabItem("传感器", Icons.Default.Info),
    TopTabItem("详情", Icons.Default.Search)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemMonitorApp(appViewModel: AppViewModel = koinViewModel()) {
    DisposableEffect(Unit) {
        appViewModel.startMonitoring()
        onDispose { appViewModel.stopMonitoring() }
    }

    var showSettings by remember { mutableStateOf(false) }
    var showFloatConfig by remember { mutableStateOf(false) }
    var showSensorDetail by remember { mutableStateOf<Boolean>(false) }
    var selectedSensorForDetail by remember { mutableStateOf<com.example.deviceinfoviewer.data.model.SensorItemInfo?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    // pagerState 提升到 SystemMonitorApp 层级 — 确保覆盖层返回时 Tab 位置不被重置
    val pagerState = rememberPagerState(pageCount = { topTabs.size })
    val scope = rememberCoroutineScope()

    // GPS 智能开关状态 — 仅在需要时请求定位权限
    var gpsTabActive by remember { mutableStateOf(false) }

    val overlayVisible = showSettings || showFloatConfig || showSensorDetail

    // ── 预测性返回: BackHandler 处理系统返回键/手势完成事件 ──
    BackHandler(enabled = overlayVisible) {
        when {
            showSettings -> showSettings = false
            showFloatConfig -> showFloatConfig = false
            showSensorDetail -> {
                showSensorDetail = false
                selectedSensorForDetail = null
            }
        }
    }

    // GPS 开关观察 — 离开 GPS/网络 Tab 时自动关闭定位
    LaunchedEffect(gpsTabActive) {
        appViewModel.setGpsEnabled(gpsTabActive)
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            // ★ 主 Tab 页始终保持在 composition 中，保留所有滚动状态
            MainTabs(
                pagerState = pagerState,
                scope = scope,
                overlayVisible = overlayVisible,
                onOpenSettings = { showSettings = true },
                onOpenFloat = { showFloatConfig = true },
                onGpsTabChanged = { active -> gpsTabActive = active },
                onOpenSensorDetail = { sensor ->
                    selectedSensorForDetail = sensor
                    showSensorDetail = true
                }
            )

            // ── 覆盖层 (graphicsLayer 透明动画, 保持 composition 存活) ──
            // 使用 graphicsLayer.alpha 替代 AnimatedVisibility:
            //   覆盖层在退出动画期间仍留在 composition 树中,
            //   系统预测性返回 (Android 15+) 可以跨页面渐变动画。

            // ── 设置 ──
            val settingsAlpha by animateFloatAsState(
                targetValue = if (showSettings) 1f else 0f,
                animationSpec = tween(300), label = "settingsAlpha"
            )
            if (settingsAlpha > 0.01f || showSettings) {
                Box(Modifier.fillMaxSize()
                    .graphicsLayer { alpha = settingsAlpha }
                    .background(MaterialTheme.colorScheme.background)
                ) {
                    SettingsScreen()
                    GlowBackButton(
                        onClick = { showSettings = false },
                        btnSize = 48.dp,
                        modifier = Modifier.padding(top = 8.dp, start = 16.dp).align(Alignment.TopStart)
                    )
                }
            }

            // ── 悬浮窗 ──
            val floatAlpha by animateFloatAsState(
                targetValue = if (showFloatConfig) 1f else 0f,
                animationSpec = tween(300), label = "floatAlpha"
            )
            if (floatAlpha > 0.01f || showFloatConfig) {
                Box(Modifier.fillMaxSize()
                    .graphicsLayer { alpha = floatAlpha }
                    .background(MaterialTheme.colorScheme.background)
                ) {
                    FloatingWindowScreen(onBack = { showFloatConfig = false })
                    GlowBackButton(
                        onClick = { showFloatConfig = false },
                        btnSize = 48.dp,
                        modifier = Modifier.padding(top = 8.dp, start = 16.dp).align(Alignment.TopStart)
                    )
                }
            }

            // ── 传感器详情 ──
            val sensorAlpha by animateFloatAsState(
                targetValue = if (showSensorDetail) 1f else 0f,
                animationSpec = tween(300), label = "sensorAlpha"
            )
            if (sensorAlpha > 0.01f || showSensorDetail) {
                val sensor = selectedSensorForDetail
                if (sensor != null) {
                    Box(Modifier.fillMaxSize()
                        .graphicsLayer { alpha = sensorAlpha }
                        .background(MaterialTheme.colorScheme.background)
                    ) {
                        SensorDetailScreen(
                            sensor = sensor,
                            onBack = {
                                showSensorDetail = false
                                selectedSensorForDetail = null
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTabs(
    pagerState: PagerState,
    scope: CoroutineScope,
    overlayVisible: Boolean = false,
    onOpenSettings: () -> Unit,
    onOpenFloat: () -> Unit,
    onGpsTabChanged: (Boolean) -> Unit = {},
    onOpenSensorDetail: (com.example.deviceinfoviewer.data.model.SensorItemInfo) -> Unit = {}
) {
    // 智能 GPS: 仅"网络" (index 5) 和 "GPS" (index 6) Tab 启用定位
    val currentPage = pagerState.currentPage
    LaunchedEffect(currentPage) {
        val isGpsRelated = currentPage == 5 || currentPage == 6
        onGpsTabChanged(isGpsRelated)
    }

    // ★ 两步返回键退出: 覆盖层显示时不拦截 → 由 SystemMonitorApp 的 BackHandler 统一处理
    //   避免与系统预测性返回动画产生双重回调冲突 (Android 16+ mandatory predictive back)
    BackHandler(enabled = currentPage != 0 && !overlayVisible) {
        scope.launch { pagerState.animateScrollToPage(0) }
    }

    Column(Modifier.fillMaxSize()) {
        // ── 暗玻璃药丸头部: padding + 大圆角容器 + 动效装饰 ──
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp)
                .height(52.dp)
                .clip(RoundedCornerShape(26.dp))
        ) {
            // 底层: 动效装饰 (渐变光晕 + 内发光边框 + 粒子)
            NeonHeaderDecoration(Modifier.matchParentSize())

            // 顶层: 紧凑型顶部栏 TabRow + 操作按钮在同一行
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = Color.Transparent,
                contentColor = NeonPurple,
                edgePadding = 0.dp,
                modifier = Modifier.weight(1f),
                divider = {},
                indicator = { pos ->
                    TabRowDefaults.Indicator(
                        Modifier.tabIndicatorOffset(pos[pagerState.currentPage]),
                        color = NeonPurple, height = 3.dp
                    )
                }
            ) {
                topTabs.forEachIndexed { i, tab ->
                    Tab(
                        selected = pagerState.currentPage == i,
                        onClick = { scope.launch { pagerState.animateScrollToPage(i) } },
                        text = {
                            Text(tab.title, fontSize = 12.sp,
                                fontWeight = if (pagerState.currentPage == i) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1)
                        },
                        icon = { Icon(tab.icon, null, Modifier.size(16.dp)) },
                        selectedContentColor = NeonPurple,
                        unselectedContentColor = NeonSteelBlue.copy(alpha = 0.7f)
                    )
                }
            }

            IconButton(onClick = onOpenFloat, modifier = Modifier.size(36.dp)) {
                Text("◫", fontSize = 14.sp, color = NeonPurple)
            }
            IconButton(onClick = onOpenSettings, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Settings, "设置", tint = NeonPurple, modifier = Modifier.size(18.dp))
            }
        }
        } // end Box — 霓虹动效头部

        // ── 霓虹动效分割线 (替代原 HorizontalDivider, 对齐药丸头部的水平边距) ──
        NeonDivider(Modifier.fillMaxWidth().padding(horizontal = 6.dp))

        // 页面内容
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            AnimatedContent(targetState = page,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) }
            ) { p ->
                val navigate: (Int) -> Unit = { scope.launch { pagerState.animateScrollToPage(it) } }
                when (p) {
                    0 -> DashboardScreen(onNavigate = navigate)
                    1 -> CpuScreen()
                    2 -> GpuScreen()
                    3 -> MemoryScreen()
                    4 -> BatteryScreen()
                    5 -> NetworkScreen()
                    6 -> GpsScreen()
                    7 -> SensorsScreen(
                        onNavigateToSensor = onOpenSensorDetail
                    )
                    8 -> DeviceScreen()
                }
            }
        }
    }
}
