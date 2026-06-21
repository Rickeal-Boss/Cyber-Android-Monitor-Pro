package com.example.deviceinfoviewer

import android.os.Bundle
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import android.util.Log
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
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.deviceinfoviewer.HapticUtils
import com.example.deviceinfoviewer.LocaleManager
import com.example.deviceinfoviewer.R
import com.example.deviceinfoviewer.ui.AppViewModel
import com.example.deviceinfoviewer.ui.battery.BatteryScreen
import com.example.deviceinfoviewer.ui.components.GlowBackButton
import com.example.deviceinfoviewer.ui.components.NeonDivider
import com.example.deviceinfoviewer.ui.components.NeonHeaderDecoration
import com.example.deviceinfoviewer.ui.components.neonBorderGlow
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
import com.example.deviceinfoviewer.ui.effects.GlobalLightProvider
import com.example.deviceinfoviewer.ui.effects.acrylic
import com.example.deviceinfoviewer.ui.effects.revealLight
import com.example.deviceinfoviewer.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

class MainActivity : ComponentActivity() {

    // 应用保存的语言偏好 — 在 attachBaseContext 中应用，确保 Compose stringResource() 加载正确语言
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            enableEdgeToEdge()
        } catch (e: Throwable) {
            Log.e("MainActivity", "enableEdgeToEdge failed", e)
        }
        super.onCreate(savedInstanceState)
        try {
            configureSystemBars()
        } catch (e: Throwable) {
            Log.e("MainActivity", "configureSystemBars failed", e)
        }
        // ★ 预测性返回手势兼容性诊断 (2026-06-19)
        // 国产 ROM 对 OnBackInvokedCallback 支持参差不齐，启动时输出诊断日志
        com.example.deviceinfoviewer.util.BackGestureCompat.isPredictiveBackSupported(this)
        com.example.deviceinfoviewer.util.BackGestureCompat.logPredictiveBackDevOptionState(this)
        try {
            // ★ 二分法通关: 用回完整 SystemMonitorApp（安全版 NeonHeaderDecoration）
            setContent {
                DeviceInfoViewerTheme {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        SystemMonitorApp()
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e("MainActivity", "setContent failed", e)
        }
    }
    private fun configureSystemBars() {
        try {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        } catch (e: Throwable) {
            Log.w("MainActivity", "系统栏配置失败（OEM 兼容性）", e)
        }
    }
}

private data class TopTabItem(val title: String, val icon: ImageVector)

private val topTabIcons = listOf(
    Icons.Filled.Home,
    Icons.Filled.PlayArrow,
    Icons.Filled.Info,
    Icons.Filled.Star,
    Icons.Filled.Favorite,
    Icons.Filled.Share,
    Icons.Filled.PlayArrow,
    Icons.Filled.Info,
    Icons.Filled.Search
)

/** Tab 标题国际化 — 在 Composable 内调用 stringResource 获取当前语言标题 */
@Composable
private fun rememberTopTabs(): List<TopTabItem> {
    val titles = listOf(
        stringResource(R.string.tab_dashboard),
        stringResource(R.string.tab_cpu),
        stringResource(R.string.tab_gpu),
        stringResource(R.string.tab_memory),
        stringResource(R.string.tab_battery),
        stringResource(R.string.tab_network),
        stringResource(R.string.tab_gps),
        stringResource(R.string.tab_sensors),
        stringResource(R.string.tab_device)
    )
    return titles.mapIndexed { i, title -> TopTabItem(title, topTabIcons[i]) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemMonitorAppMinimal() {
    val safeViewModel = runCatching { koinViewModel<AppViewModel>() }.getOrNull()
    if (safeViewModel == null) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Text(stringResource(R.string.common_koin_failed), color = Color.White, modifier = Modifier.align(Alignment.Center))
        }
        return
    }
    Scaffold { padding ->
        Box(Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemMonitorAppNoFx(appViewModel: AppViewModel? = null) {
    val safeViewModel = appViewModel ?: runCatching { koinViewModel<AppViewModel>() }.getOrNull()
    if (safeViewModel == null) {
        Box(Modifier.fillMaxSize().background(CyberBackground)) {
            Text(stringResource(R.string.common_init_failed), color = NeonPurple, modifier = Modifier.align(Alignment.Center))
        }
        return
    }
    val topTabs = rememberTopTabs()
    val pagerState = rememberPagerState(pageCount = { topTabs.size })
    val scope = rememberCoroutineScope()

    BackHandler(enabled = pagerState.currentPage != 0) {
        scope.launch { pagerState.animateScrollToPage(0) }
    }

    Column(Modifier.fillMaxSize()) {
        // 简明头部: 无动效 — 实际使用 NeonHeaderDecoration 纯静态版
        Box(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp).height(52.dp)
            .clip(RoundedCornerShape(26.dp))) {
            NeonHeaderDecoration(Modifier.matchParentSize())
            Row(
                verticalAlignment = Alignment.CenterVertically) {
                ScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = Color.Transparent, contentColor = NeonPurple,
                    edgePadding = 0.dp, modifier = Modifier.weight(1f), divider = {},
                    indicator = { pos -> TabRowDefaults.Indicator(
                        Modifier.tabIndicatorOffset(pos[pagerState.currentPage]), color = NeonPurple, height = 3.dp) }
                ) {
                    topTabs.forEachIndexed { i, tab ->
                        Tab(selected = pagerState.currentPage == i,
                            onClick = { scope.launch { pagerState.animateScrollToPage(i) } },
                            text = { Text(tab.title, fontSize = 12.sp, fontWeight = if (pagerState.currentPage == i) FontWeight.Bold else FontWeight.Normal, maxLines = 1) },
                            icon = { Icon(tab.icon, null, Modifier.size(16.dp)) },
                            selectedContentColor = NeonPurple, unselectedContentColor = NeonSteelBlue.copy(alpha = 0.7f))
                    }
                }
            }
        }
        // ★ 二分: 只加回 NeonDivider，NeonHeaderDecoration 仍是纯 Box
        NeonDivider(Modifier.fillMaxWidth().padding(horizontal = 6.dp))
        // 页面
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            Box(Modifier.fillMaxSize().background(Color.Black))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemMonitorApp(appViewModel: AppViewModel? = null) {
    // 安全获取 ViewModel: 如果 Koin 未初始化或找不到 ViewModel, 不会崩溃
    val safeViewModel = appViewModel ?: runCatching {
        koinViewModel<AppViewModel>()
    }.getOrNull()

    if (safeViewModel == null) {
        // Koin 初始化失败 — 显示纯黑屏 + 错误提示
        Box(Modifier.fillMaxSize().background(CyberBackground)) {
            Text(
                text = stringResource(R.string.common_init_failed),
                color = NeonPurple,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        return
    }

    DisposableEffect(Unit) {
        safeViewModel.startMonitoring()
        onDispose { safeViewModel.stopMonitoring() }
    }

    var showSettings by remember { mutableStateOf(false) }
    var showFloatConfig by remember { mutableStateOf(false) }
    var showSensorDetail by remember { mutableStateOf<Boolean>(false) }
    var selectedSensorForDetail by remember { mutableStateOf<com.example.deviceinfoviewer.data.model.SensorItemInfo?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    // pagerState 提升到 SystemMonitorApp 层级 — 确保覆盖层返回时 Tab 位置不被重置
    val topTabs = rememberTopTabs()
    val pagerState = rememberPagerState(pageCount = { topTabs.size })
    val scope = rememberCoroutineScope()

    // GPS 智能开关状态 — 仅在需要时请求定位权限
    var gpsTabActive by remember { mutableStateOf(false) }

    val overlayVisible = showSettings || showFloatConfig || showSensorDetail

    // ★ 预测性返回手势进度 (2026-06-19): 驱动覆盖层缩放/位移，替代纯 alpha 动画
    //   手指拖拽返回时 progress 0→1，覆盖层缩小+右移模拟"被拽走"；
    //   手势完成关闭覆盖层，手势取消则 spring 回弹。
    //
    //   国产 ROM (MIUI/ColorOS/OriginOS/HarmonyOS) 兼容性策略:
    //   1. AndroidManifest application+activity 均已声明 enableOnBackInvokedCallback="true"
    //   2. Android 13+ 需用户在开发者选项开启"预测性返回动画"，14+ 默认开启，15+ 强制
    //   3. 国产 ROM 即使阉割预测动画，PredictiveBackHandler 的 flow 为空 → 立即完成
    //      等价普通 BackHandler，覆盖层仍能正常关闭（仅无缩放进度动画）
    //   4. 与 MainTabs 的 pager BackHandler 互斥: overlayVisible 时本回调启用，
    //      MainTabs BackHandler enabled = (currentPage!=0 && !overlayVisible) 为 false
    //   5. 不支持预测时 backProgress 保持 0f，覆盖层用 animateFloatAsState 的 alpha
    //      动画提供退出过渡（tween 300ms），视觉上仍有淡出效果
    //   6. BackGestureCompat 工具在启动时输出诊断日志，辅助排查 ROM 兼容性问题
    val backProgress = remember { Animatable(0f) }

    // ── 预测性返回: PredictiveBackHandler 接收手指拖拽进度 ──
    // activity-compose 1.9.0 中 PredictiveBackHandler 已稳定（无需 @OptIn）
    PredictiveBackHandler(enabled = overlayVisible) { progress: Flow<BackEventCompat> ->
        try {
            progress.collect { event ->
                backProgress.snapTo(event.progress)
            }
            // 手势完成 — 关闭当前覆盖层，重置进度
            backProgress.snapTo(0f)
            when {
                showSettings -> showSettings = false
                showFloatConfig -> showFloatConfig = false
                showSensorDetail -> {
                    showSensorDetail = false
                    selectedSensorForDetail = null
                }
            }
        } catch (e: CancellationException) {
            // 手势取消 — Spring 平滑回弹 (仅支持预测的 ROM 会触发)
            backProgress.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
    }

    // GPS 开关观察 — 离开 GPS/网络 Tab 时自动关闭定位
    LaunchedEffect(gpsTabActive) {
        safeViewModel.setGpsEnabled(gpsTabActive)
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            // ★ Windows 10 风格全局光照 — 包裹全部内容以捕获指针事件
            GlobalLightProvider {
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
                    .graphicsLayer {
                        val p = backProgress.value
                        alpha = settingsAlpha * (1f - p * 0.3f)
                        scaleX = 1f - p * 0.06f
                        scaleY = 1f - p * 0.06f
                        translationX = size.width * p * 0.25f
                    }
                    .acrylic(
                        tintColor = CyberCardStart,
                        tintOpacity = 0.85f,
                        noiseOpacity = 0.04f,
                        borderColor = NeonPurple.copy(alpha = 0.25f),
                        // ★ 性能优化 (2026-06-20): 全屏覆盖层禁用噪点
                        //   原因: 覆盖层上方有 SettingsScreen 等不透明内容, 噪点仅在边缘可见,
                        //   视觉收益极低; 但 drawWithCache 首次生成 ~7700 个 Offset 对象 +
                        //   isInsideRoundedRect 7700 次浮点运算在主线程同步完成, 是进入覆盖层
                        //   时可感知卡顿的根因。禁用后 acrylic 退化为半透明渐变, 开销极低。
                        enableNoise = false
                    )
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
                    .graphicsLayer {
                        val p = backProgress.value
                        alpha = floatAlpha * (1f - p * 0.3f)
                        scaleX = 1f - p * 0.06f
                        scaleY = 1f - p * 0.06f
                        translationX = size.width * p * 0.25f
                    }
                    .acrylic(
                        tintColor = CyberCardStart,
                        tintOpacity = 0.85f,
                        noiseOpacity = 0.04f,
                        borderColor = NeonPurple.copy(alpha = 0.25f),
                        enableNoise = false  // ★ 同设置页
                    )
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
                        .graphicsLayer {
                        val p = backProgress.value
                        alpha = sensorAlpha * (1f - p * 0.3f)
                        scaleX = 1f - p * 0.06f
                        scaleY = 1f - p * 0.06f
                        translationX = size.width * p * 0.25f
                    }
                        .acrylic(
                            tintColor = CyberCardStart,
                            tintOpacity = 0.85f,
                            noiseOpacity = 0.04f,
                            borderColor = NeonPurple.copy(alpha = 0.25f),
                            enableNoise = false  // ★ 同设置页
                        )
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
            } // end GlobalLightProvider
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
    val topTabs = rememberTopTabs()
    // 智能 GPS: 仅"网络" (index 5) 和 "GPS" (index 6) Tab 启用定位
    val currentPage = pagerState.currentPage
    val ctx = LocalContext.current
    LaunchedEffect(currentPage) {
        val isGpsRelated = currentPage == 5 || currentPage == 6
        onGpsTabChanged(isGpsRelated)
    }

    // ★ 覆盖层显示时不拦截 → 由 SystemMonitorApp 的 PredictiveBackHandler 统一处理
    //   互斥逻辑: overlayVisible 时本回调 disabled (!overlayVisible=false)，
    //   PredictiveBackHandler (enabled=overlayVisible) 接管返回手势；
    //   非覆盖层时本回调处理 pager 回首页
    BackHandler(enabled = currentPage != 0 && !overlayVisible) {
        scope.launch { pagerState.animateScrollToPage(0) }
    }

    Column(Modifier.fillMaxSize()) {
        // ── 暗玻璃药丸头部: padding + 大圆角容器 + 动效装饰 + Windows 10 光照 ──
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp)
                .height(52.dp)
                .revealLight(radius = 200.dp, intensity = 0.15f)
                .neonBorderGlow()
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
                        onClick = {
                            HapticUtils.lightTap(ctx)
                            scope.launch { pagerState.animateScrollToPage(i) }
                        },
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

            IconButton(onClick = {
                HapticUtils.standardTap(ctx)
                onOpenFloat()
            }, modifier = Modifier.size(36.dp)) {
                Text("◫", fontSize = 14.sp, color = NeonPurple)
            }
            IconButton(onClick = {
                HapticUtils.standardTap(ctx)
                onOpenSettings()
            }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Settings, stringResource(R.string.common_settings), tint = NeonPurple, modifier = Modifier.size(18.dp))
            }
        }
        } // end Box — 霓虹动效头部

        // ── 霓虹动效分割线 (替代原 HorizontalDivider, 对齐药丸头部的水平边距) ──
        NeonDivider(Modifier.fillMaxWidth().padding(horizontal = 6.dp))

        // 页面内容
        // ★ 性能优化 (2026-06-19): 去掉嵌套 AnimatedContent
        //   HorizontalPager 自带页面切换滑动动画，内部 AnimatedContent(fadeIn/fadeOut 300ms)
        //   是双重动画 + 每个 page 额外重组，去掉后滑动更流畅且减少重组开销。
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val navigate: (Int) -> Unit = { scope.launch { pagerState.animateScrollToPage(it) } }
            when (page) {
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
