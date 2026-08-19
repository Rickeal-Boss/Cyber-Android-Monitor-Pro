package com.rb.cybermonitorpro

import android.os.Bundle
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import android.util.Log
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.content.Context
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.rb.cybermonitorpro.HapticUtils
import com.rb.cybermonitorpro.LocaleManager
import com.rb.cybermonitorpro.R
import com.rb.cybermonitorpro.ui.AppViewModel
import com.rb.cybermonitorpro.RefreshPolicy
import com.rb.cybermonitorpro.ui.battery.BatteryScreen
import com.rb.cybermonitorpro.ui.components.CyberIcons
import com.rb.cybermonitorpro.ui.components.LightCircleBackButton
import com.rb.cybermonitorpro.ui.components.GlassCircleButton
import com.rb.cybermonitorpro.ui.components.NeonDivider
import com.rb.cybermonitorpro.ui.components.NeonHeaderDecoration
import com.rb.cybermonitorpro.ui.components.neonBorderGlow
import com.rb.cybermonitorpro.ui.cpu.CpuScreen
import com.rb.cybermonitorpro.ui.dashboard.DashboardScreen
import com.rb.cybermonitorpro.ui.device.DeviceScreen
import com.rb.cybermonitorpro.ui.device.HdrLabScreen
import com.rb.cybermonitorpro.ui.floatwindow.FloatingWindowScreen
import com.rb.cybermonitorpro.ui.gps.GpsScreen
import com.rb.cybermonitorpro.ui.gpu.GpuScreen
import com.rb.cybermonitorpro.ui.memory.MemoryScreen
import com.rb.cybermonitorpro.ui.network.NetworkScreen
import com.rb.cybermonitorpro.ui.sensors.SensorDetailContent
import com.rb.cybermonitorpro.ui.sensors.SensorsScreen
import com.rb.cybermonitorpro.ui.settings.SettingsScreen
import com.rb.cybermonitorpro.ui.effects.GlobalLightProvider
import com.rb.cybermonitorpro.ui.effects.LocalSharedTransitionScope
import com.rb.cybermonitorpro.ui.effects.StaggeredPageProvider
import com.rb.cybermonitorpro.ui.effects.acrylic
import com.rb.cybermonitorpro.ui.effects.circularReveal
import com.rb.cybermonitorpro.ui.effects.rememberCircularRevealState
import com.rb.cybermonitorpro.ui.effects.revealLight
import com.rb.cybermonitorpro.ui.effects.AppGlowBackground
import com.rb.cybermonitorpro.ui.nightlight.CyberNightlightHost
import com.rb.cybermonitorpro.ui.nightlight.HdrOverlayState
import com.rb.cybermonitorpro.ui.nightlight.HdrPatchHost
import com.rb.cybermonitorpro.ui.nightlight.hdrTabIndicatorPatch
import com.rb.cybermonitorpro.ui.nightlight.hdrTabBarBorderPatch
import com.rb.cybermonitorpro.ui.nightlight.hdrTabIconPatch
import com.rb.cybermonitorpro.ui.nightlight.HdrMetricText
import com.rb.cybermonitorpro.ui.nightlight.HdrPatchRegistry
import com.rb.cybermonitorpro.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.koin.androidx.compose.koinViewModel

class MainActivity : ComponentActivity() {

    // 应用保存的语言偏好 — 在 attachBaseContext 中应用，确保 Compose stringResource() 加载正确语言
    // ★ HCP-1 修复: wrapContext 加 try/catch 守卫。attachBaseContext 在 super.onCreate 之前执行，
    //   若此处抛异常（例如 OEM ROM 资源/配置异常）会直接静默崩溃且无 crash.log。守卫后降级回退 base，保证可启动。
    override fun attachBaseContext(newBase: Context) {
        val wrapped = try {
            LocaleManager.wrapContext(newBase)
        } catch (e: Throwable) {
            Log.e("MainActivity", "attachBaseContext wrapContext failed, fallback to base", e)
            newBase
        }
        super.attachBaseContext(wrapped)
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
        com.rb.cybermonitorpro.util.BackGestureCompat.isPredictiveBackSupported(this)
        com.rb.cybermonitorpro.util.BackGestureCompat.logPredictiveBackDevOptionState(this)
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

private data class TopTabItem(val title: String, val iconRes: Int)

// F5: 传感器详情过渡弹簧 — 进入平顺跟手、退出带回弹
private val SENSOR_ENTRY_SPRING = spring<Float>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
private val SENSOR_EXIT_SPRING = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)

/** 赛博风格线条矢量图标 — 与 Tab 含义一一对应 */
private val topTabIcons = listOf(
    R.drawable.ic_cyber_dashboard,
    R.drawable.ic_cyber_cpu,
    R.drawable.ic_cyber_gpu,
    R.drawable.ic_cyber_memory,
    R.drawable.ic_cyber_battery,
    R.drawable.ic_cyber_network,
    R.drawable.ic_cyber_gps,
    R.drawable.ic_cyber_sensors,
    R.drawable.ic_cyber_device,
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


@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
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

    // ★ 前后台统一刷新策略 (2026-06-21):
    //   不再手动调频，仅通知 RefreshPolicy 状态变更，
    //   DeviceRepository 和 FloatingWindowService 各自观察 RefreshPolicy.state 自动调整
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    Log.d("SystemMonitor", "App → background, RefreshPolicy → BACKGROUND")
                    RefreshPolicy.updateState(RefreshPolicy.RefreshState.BACKGROUND)
                }
                Lifecycle.Event.ON_START -> {
                    Log.d("SystemMonitor", "App → foreground, RefreshPolicy → FOREGROUND")
                    RefreshPolicy.updateState(RefreshPolicy.RefreshState.FOREGROUND)
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var showSettings by remember { mutableStateOf(false) }
    var showFloatConfig by remember { mutableStateOf(false) }
    var showSensorDetail by remember { mutableStateOf<Boolean>(false) }
    var selectedSensorForDetail by remember { mutableStateOf<com.rb.cybermonitorpro.data.model.SensorItemInfo?>(null) }
    // ★ 2026-08-16: HDR 实验室（详情页二层 — 局部 EDR 真机验证）
    var showHdrLab by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // pagerState 提升到 SystemMonitorApp 层级 — 确保覆盖层返回时 Tab 位置不被重置
    val topTabs = rememberTopTabs()
    val pagerState = rememberPagerState(pageCount = { topTabs.size })
    val scope = rememberCoroutineScope()

    // GPS 智能开关状态 — 仅在需要时请求定位权限
    var gpsTabActive by remember { mutableStateOf(false) }

    val overlayVisible = showSettings || showFloatConfig || showSensorDetail || showHdrLab

    // ── F5: 传感器详情统一主时钟 — Animatable<Float>, 0f=收起 1f=展开 ──
    //   进入: animateTo(1f, ENTRY_SPRING) / 预测返回: snapTo 跟手 /
    //   手势取消: animateTo(1f) 回弹 / 完成或返回键: animateTo(0f) 后移出组合。
    //   渲染条件用 sensorAlive(keepAlive 守卫)，绝不读 sensorProgress.value 组合判断 → 零重组。
    val sensorProgress = remember { Animatable(0f) }
    var sensorAlive by remember { mutableStateOf(false) }

    fun openSensorDetail(sensor: com.rb.cybermonitorpro.data.model.SensorItemInfo) {
        selectedSensorForDetail = sensor
        showSensorDetail = true
        sensorAlive = true
        scope.launch { sensorProgress.animateTo(1f, SENSOR_ENTRY_SPRING) }
    }

    fun closeSensorDetail() {
        scope.launch {
            sensorProgress.animateTo(0f, SENSOR_EXIT_SPRING)
            showSensorDetail = false
            selectedSensorForDetail = null
            sensorAlive = false
        }
    }

    // ── F5 扩展: HDR 实验室覆盖层 — 同传感器模式的可打断过渡 (keepAlive 守卫 + Animatable 主时钟) ──
    //   进入: animateTo(1f, ENTRY) 从屏幕中心触发点 scale/slide 位移展开;
    //   预测返回: snapTo 跟手 / 取消回弹 1f / 完成 animateTo(0f) 后 showHdrLab=false;
    //   渲染条件读 hdrAlive State, 绝不读 hdrProgress.value 组合判断 → 零重组。
    val hdrProgress = remember { Animatable(0f) }
    var hdrAlive by remember { mutableStateOf(false) }

    fun openHdrLab() {
        showHdrLab = true
        hdrAlive = true
        scope.launch { hdrProgress.animateTo(1f, SENSOR_ENTRY_SPRING) }
    }

    fun closeHdrLab() {
        scope.launch {
            hdrProgress.animateTo(0f, SENSOR_EXIT_SPRING)
            showHdrLab = false
            hdrAlive = false
        }
    }

    // ── F3: 水波纹圆形展开 — 仅【设置】【悬浮窗】2 覆盖层独立 reveal state + origin ──
    //   进入 animateTo(1f) 弹簧扩散、退出 animateTo(0f) 后 isRevealing=false 移出组合;
    //   HDR 实验室已改归 F5 可打断过渡管理（hdrProgress, 见上）;
    //   传感器覆盖层不建自己的 reveal state，只一行挂接 F5 的 sensorProgress（唯一主时钟）。
    //   预测返回"被拽走"(scale/translate)效果已移除，统一只留压暗 alpha —— 圆形展开/收缩是唯一过渡语言。
    val settingsReveal = rememberCircularRevealState()
    val floatReveal = rememberCircularRevealState()
    var settingsOrigin by remember { mutableStateOf(Offset.Zero) }
    var floatOrigin by remember { mutableStateOf(Offset.Zero) }
    var sensorRevealOrigin by remember { mutableStateOf(Offset.Zero) }

    // 兜底原点: 触发点未上报时从屏幕中心展开（方式 B: LocalConfiguration + density，无需 BoxWithConstraints）
    val configuration = LocalConfiguration.current
    val revealDensity = LocalDensity.current
    val fallbackOrigin = remember(configuration, revealDensity) {
        with(revealDensity) {
            Offset(configuration.screenWidthDp.dp.toPx() / 2f, configuration.screenHeightDp.dp.toPx() / 2f)
        }
    }

    LaunchedEffect(showSettings) {
        if (showSettings) settingsReveal.expand(if (settingsOrigin != Offset.Zero) settingsOrigin else fallbackOrigin)
        else settingsReveal.collapse()
    }
    LaunchedEffect(showFloatConfig) {
        if (showFloatConfig) floatReveal.expand(if (floatOrigin != Offset.Zero) floatOrigin else fallbackOrigin)
        else floatReveal.collapse()
    }

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
    //   5. 不支持预测时 backProgress 保持 0f，覆盖层以 F3 圆形展开/收缩弹簧
    //      提供进入/退出过渡（进入 1f 扩散、退出 0f 收缩）
    //   6. BackGestureCompat 工具在启动时输出诊断日志，辅助排查 ROM 兼容性问题
    val backProgress = remember { Animatable(0f) }

    // ── 预测性返回: PredictiveBackHandler 接收手指拖拽进度 ──
    // activity-compose 1.9.0 中 PredictiveBackHandler 已稳定（无需 @OptIn）
    // F5: 传感器/HDR实验室 分支 — 手势期各自 progress 从起点跟手退回，取消回弹到 1f
    PredictiveBackHandler(enabled = overlayVisible) { progress: Flow<BackEventCompat> ->
        val sensorStart = if (showSensorDetail) sensorProgress.value else 0f
        val hdrStart = if (showHdrLab) hdrProgress.value else 0f
        try {
            progress.collect { event ->
                backProgress.snapTo(event.progress)
                if (showSensorDetail) {
                    sensorProgress.snapTo((sensorStart * (1f - event.progress)).coerceIn(0f, 1f))
                }
                if (showHdrLab) {
                    hdrProgress.snapTo((hdrStart * (1f - event.progress)).coerceIn(0f, 1f))
                }
            }
            // 手势完成 — 关闭当前覆盖层，重置进度
            backProgress.snapTo(0f)
            when {
                showSensorDetail -> {
                    sensorProgress.snapTo(0f)
                    showSensorDetail = false
                    selectedSensorForDetail = null
                    sensorAlive = false
                }
                showHdrLab -> {
                    hdrProgress.snapTo(0f)
                    showHdrLab = false
                    hdrAlive = false
                }
                showSettings -> showSettings = false
                showFloatConfig -> showFloatConfig = false
            }
        } catch (e: CancellationException) {
            // 手势取消 — Spring 平滑回弹 (仅支持预测的 ROM 会触发)
            backProgress.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            if (showSensorDetail) sensorProgress.animateTo(1f, SENSOR_EXIT_SPRING)
            if (showHdrLab) hdrProgress.animateTo(1f, SENSOR_EXIT_SPRING)
        }
    }

    // GPS 开关观察 — 离开 GPS/网络 Tab 时自动关闭定位
    LaunchedEffect(gpsTabActive) {
        safeViewModel.setGpsEnabled(gpsTabActive)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // ★ 修复(III): 将 displayCutout 纳入安全区, 使顶部药丸/覆盖层返回按钮不被刘海/挖孔遮挡
        //   (窗口级 layoutInDisplayCutoutMode 已由 enableEdgeToEdge() 设置, 此处仅补足 Compose inset)
        contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            // ★ 固定软件背景光晕 — 根层一次性渲染, 不随卡片/页面/滚动重绘 (性能优化)
            AppGlowBackground()
            // ★ CyberNightlight TurboXDR：局部 HDR 增亮浮层（setZOrderOnTop 盖在 SDR UI 之上，
            //   触摸穿透；覆盖层打开时 hidden=true 隐藏；当前 Tab 变化时触发一次性边缘闪光）
            CyberNightlightHost(hidden = overlayVisible, currentPage = pagerState.currentPage, pagerState = pagerState)
            // ★ 行业首创：局部 UI 元素级真 HDR 增亮浮层（卡片描边 / Tab 指示条 / 大数字 / 折线+网格）
            HdrPatchHost(hidden = overlayVisible, pagerState = pagerState, modifier = Modifier.matchParentSize())
            // ★ Windows 10 风格全局光照 — 包裹全部内容以捕获指针事件
            // ★ F5-2: SharedTransitionLayout 包裹 — 传感器卡片 ↔ 详情标题 sharedElement 形变
            //   (CARD-01: 实验 API 必须 @OptIn; scope 经 CompositionLocal 下发, 调用方判空降级)
            SharedTransitionLayout {
                CompositionLocalProvider(LocalSharedTransitionScope provides this@SharedTransitionLayout) {
                    GlobalLightProvider {
            // ★ 主 Tab 页始终保持在 composition 中，保留所有滚动状态
            MainTabs(
                pagerState = pagerState,
                scope = scope,
                overlayVisible = overlayVisible,
                // F3: 触发点坐标随回调上抛，覆盖层从按钮/卡片中心圆形展开
                onOpenSettings = { origin ->
                    settingsOrigin = origin
                    showSettings = true
                },
                onOpenFloat = { origin ->
                    floatOrigin = origin
                    showFloatConfig = true
                },
                onGpsTabChanged = { active -> gpsTabActive = active },
                onOpenSensorDetail = { sensor, origin ->
                    sensorRevealOrigin = origin
                    openSensorDetail(sensor)
                },
                onOpenHdrLab = { openHdrLab() }
            )

            // ── 覆盖层 (graphicsLayer 透明动画, 保持 composition 存活) ──
            // 使用 graphicsLayer.alpha 替代 AnimatedVisibility:
            //   覆盖层在退出动画期间仍留在 composition 树中,
            //   系统预测性返回 (Android 15+) 可以跨页面渐变动画。

            // ── 设置 (F3: 水波纹圆形展开; 预测返回仅压暗) ──
            if (settingsReveal.isRevealing || showSettings) {
                Box(Modifier.fillMaxSize()
                    .circularReveal(
                        progress = { settingsReveal.progress.value },
                        origin = { settingsReveal.origin },
                    )
                    .graphicsLayer {
                        alpha = (1f - backProgress.value * 0.35f).coerceIn(0f, 1f)
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
                    LightCircleBackButton(
                        onClick = { showSettings = false },
                        btnSize = 48.dp,
                        modifier = Modifier.padding(top = 8.dp, start = 16.dp).align(Alignment.TopStart)
                    )
                }
            }

            // ── 悬浮窗 (F3: 水波纹圆形展开; 预测返回仅压暗) ──
            if (floatReveal.isRevealing || showFloatConfig) {
                Box(Modifier.fillMaxSize()
                    .circularReveal(
                        progress = { floatReveal.progress.value },
                        origin = { floatReveal.origin },
                    )
                    .graphicsLayer {
                        alpha = (1f - backProgress.value * 0.35f).coerceIn(0f, 1f)
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
                    LightCircleBackButton(
                        onClick = { showFloatConfig = false },
                        btnSize = 48.dp,
                        modifier = Modifier.padding(top = 8.dp, start = 16.dp).align(Alignment.TopStart)
                    )
                }
            }

            // ── HDR 实验室（详情页二层 — 局部 EDR 真机验证; F5 可打断过渡, 主时钟 hdrProgress）──
            //   渲染条件读 hdrAlive State; 从屏幕中心触发点 scale/slide 位移展开,
            //   预测返回手势 snapTo 跟手、取消回弹 1f、完成 animateTo(0f) 后移出组合。
            if (hdrAlive || showHdrLab) {
                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier.fillMaxSize()
                            .graphicsLayer {
                                val p = hdrProgress.value
                                alpha = p
                                // F5 入口位移形变: 以屏幕中心为 pivot 微缩放 + 横向滑入 (可打断, 无 sharedElement)
                                // size.width/height 首帧可能为 0, 先 takeIf 守卫再相除, 避免 NaN 落到 TransformOrigin
                                val pivotX = (size.width.takeIf { it > 0f }?.let { fallbackOrigin.x / it } ?: 0.5f).coerceIn(0f, 1f)
                                val pivotY = (size.height.takeIf { it > 0f }?.let { fallbackOrigin.y / it } ?: 0.5f).coerceIn(0f, 1f)
                                transformOrigin = TransformOrigin(pivotX, pivotY)
                                val s = 0.9f + 0.1f * p
                                scaleX = s
                                scaleY = s
                                translationX = (1f - p) * size.width * 0.06f
                            }
                            .acrylic(
                                tintColor = CyberCardStart,
                                tintOpacity = 0.85f,
                                noiseOpacity = 0.04f,
                                borderColor = NeonPurple.copy(alpha = 0.25f),
                                enableNoise = false  // ★ 同设置页：全屏覆盖层禁用噪点（性能）
                            )
                    ) {
                        HdrLabScreen(onBack = { closeHdrLab() })
                        LightCircleBackButton(
                            onClick = { closeHdrLab() },
                            btnSize = 48.dp,
                            modifier = Modifier.padding(top = 8.dp, start = 16.dp).align(Alignment.TopStart)
                        )
                    }
                }
            }

            // ── 传感器详情 (F5: sensorProgress 主时钟 + keepAlive 守卫, 零重组) ──
            // 渲染条件读 sensorAlive State，绝不在组合期读 sensorProgress.value；
            // acrylic 背景与非标题内容的 alpha/位移全部由 Animatable 在 draw/layout 阶段驱动。
            if (sensorAlive || showSensorDetail) {
                val sensor = selectedSensorForDetail
                if (sensor != null) {
                    val density = LocalDensity.current
                    // F3: 传感器覆盖层只做一行挂接 — sensorProgress(F5 主时钟)驱动圆形展开, 不引入自己的 reveal state
                    Box(Modifier.fillMaxSize()
                        .circularReveal(
                            progress = { sensorProgress.value },
                            origin = { if (sensorRevealOrigin != Offset.Zero) sensorRevealOrigin else fallbackOrigin },
                        )
                    ) {
                        Box(
                            Modifier.fillMaxSize()
                                .graphicsLayer { alpha = sensorProgress.value }
                                .acrylic(
                                    tintColor = CyberCardStart,
                                    tintOpacity = 0.85f,
                                    noiseOpacity = 0.04f,
                                    borderColor = NeonPurple.copy(alpha = 0.25f),
                                    enableNoise = false  // ★ 同设置页
                                )
                        )
                        SensorDetailContent(
                            sensor = sensor,
                            progress = sensorProgress,
                            density = density,
                            onBack = { closeSensorDetail() }
                        )
                    }
                }
            }
            } // end GlobalLightProvider
                } // end CompositionLocalProvider(LocalSharedTransitionScope)
            } // end SharedTransitionLayout
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTabs(
    pagerState: PagerState,
    scope: CoroutineScope,
    overlayVisible: Boolean = false,
    onOpenSettings: (Offset) -> Unit,
    onOpenFloat: (Offset) -> Unit,
    onGpsTabChanged: (Boolean) -> Unit = {},
    onOpenSensorDetail: (com.rb.cybermonitorpro.data.model.SensorItemInfo, Offset) -> Unit = { _, _ -> },
    onOpenHdrLab: () -> Unit = {}
) {
    val topTabs = rememberTopTabs()
    // 智能 GPS: 仅"网络" (index 5) 和 "GPS" (index 6) Tab 启用定位
    val currentPage = pagerState.currentPage
    val ctx = LocalContext.current
    // F3: 按钮触发点坐标 — onGloballyPositioned 上报中心(boundsInRoot; RIPPLE-04 备 positionInWindow 降级)
    var floatBtnOrigin by remember { mutableStateOf(Offset.Zero) }
    var settingsBtnOrigin by remember { mutableStateOf(Offset.Zero) }
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
                .height(64.dp)
                .revealLight(radius = 200.dp, intensity = 0.15f)
                .neonBorderGlow()
                // ★ 新增(Tab 栏描边 HDR): 与 neonBorderGlow 同款圆角/描边，仅 TurboXDR 开启时由 PQ 浮层点亮（topZone 绕过顶撞裁剪）
                .hdrTabBarBorderPatch("topbar.border")
                // ★ 修复(顶撞): 记录药丸头部+Tab 行底部（根坐标），供渲染器减去 surfaceRoot 后裁剪内容贴片
                .onGloballyPositioned { coords ->
                    val rootY = coords.localToRoot(Offset.Zero).y
                    HdrPatchRegistry.contentClipTop = rootY + coords.size.height
                }
        ) {
            // 底层: 动效装饰 (渐变光晕 + 内发光边框 + 粒子) — 独立剪裁，不影响 TabRow indicator
            NeonHeaderDecoration(Modifier.matchParentSize().clip(RoundedCornerShape(26.dp)))

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
                        Modifier.tabIndicatorOffset(pos[pagerState.currentPage])
                            .hdrTabIndicatorPatch("topbar.indicator"),
                        // PQ 点亮时隐藏 SDR 指示条，由透明 PQ 浮层承担高亮（避免重影/过曝）
                        color = if (HdrOverlayState.pqActive.value) Color.Transparent else NeonPurple,
                        height = 3.dp
                    )
                }
            ) {
            topTabs.forEachIndexed { i, tab ->
                val onTabClick: () -> Unit = remember(i, ctx, scope, pagerState) {
                    { HapticUtils.lightTap(ctx); scope.launch { pagerState.animateScrollToPage(i) }; Unit }
                }
                Tab(
                    selected = pagerState.currentPage == i,
                    onClick = onTabClick,
                    text = {
                        // ★ 修复(Tab 标签无 HDR): 选中项标签以 TEXT_GLYPH 点亮（topZone 绕过顶撞裁剪）
                        HdrMetricText(
                            text = tab.title,
                            fontSize = 12.sp,
                            color = if (pagerState.currentPage == i) NeonPurple else NeonSteelBlue.copy(alpha = 0.7f),
                            letterSpacing = 0.sp,
                            topZone = true,
                            gate = pagerState.currentPage == i,
                            monospace = false,
                            fontWeight = if (pagerState.currentPage == i) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    icon = {
                        // ★ 修复(Tab 图标无 HDR): 选中项矢量图标外圈霓虹光环（topZone 绕过顶撞裁剪）
                        Icon(
                            painterResource(tab.iconRes), null,
                            Modifier.size(16.dp)
                                .hdrTabIconPatch("tab.icon.$i", selected = pagerState.currentPage == i, iconRes = tab.iconRes)
                        )
                    },
                    selectedContentColor = NeonPurple,
                    unselectedContentColor = NeonSteelBlue.copy(alpha = 0.7f)
                )
                }
            }

        val onFloatClick = remember(ctx) { { HapticUtils.standardTap(ctx); onOpenFloat(floatBtnOrigin) } }
        val onSettingsClick = remember(ctx) { { HapticUtils.standardTap(ctx); onOpenSettings(settingsBtnOrigin) } }

        // ── 玻璃圆底操作按钮 (与 LightCircleBackButton V3 视觉一致) ──
        // F3: onGloballyPositioned 上报按钮中心 → 覆盖层从按钮中心圆形展开
        GlassCircleButton(
            onClick = onFloatClick,
            btnSize = 36.dp,
            modifier = Modifier.onGloballyPositioned { floatBtnOrigin = it.boundsInRoot().center },
            contentDescription = stringResource(R.string.float_title)
        ) {
            Icon(
                CyberIcons.Window,
                contentDescription = null,
                tint = Color(0xFF1A1A2E).copy(alpha = 0.85f),
                modifier = Modifier.size(17.dp)
            )
        }
        GlassCircleButton(
            onClick = onSettingsClick,
            btnSize = 36.dp,
            modifier = Modifier.onGloballyPositioned { settingsBtnOrigin = it.boundsInRoot().center },
            contentDescription = stringResource(R.string.common_settings)
        ) {
            Icon(
                CyberIcons.Settings,
                contentDescription = null,
                tint = Color(0xFF1A1A2E).copy(alpha = 0.85f),
                modifier = Modifier.size(17.dp)
            )
        }
        }
        } // end Box — 霓虹动效头部

        // ── 霓虹动效分割线 (替代原 HorizontalDivider, 对齐药丸头部的水平边距) ──
        NeonDivider(Modifier.fillMaxWidth().padding(horizontal = 6.dp))

        // 页面内容
        // ★ 性能优化 (2026-06-19): 去掉嵌套 AnimatedContent
        //   HorizontalPager 自带页面切换滑动动画，内部 AnimatedContent(fadeIn/fadeOut 300ms)
        //   是双重动画 + 每个 page 额外重组，去掉后滑动更流畅且减少重组开销。
        // ★ StaggeredPageProvider v4: 每页 1 个共享弹簧(Animatable), 卡片读 State 做级联相位映射
        // pageSpacing=0 → 消除滑动时两页之间的黑边间隙
        // ★ 修复(IV): Pager 改占 Column 剩余空间(weight(1f)), 避免与 64dp 头部叠加导致底部 ~64dp 内容被裁
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().weight(1f), pageSpacing = 0.dp) { page ->
            // v4: 父层单弹簧 + CompositionLocal<State<Float>> 下发, 绘制层失效不重组
            StaggeredPageProvider(pagerState = pagerState, page = page) {
            val navigate: (Int) -> Unit = remember(scope, pagerState) {
                { target: Int -> scope.launch { pagerState.animateScrollToPage(target) }; Unit }
            }
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
                8 -> DeviceScreen(onOpenHdrLab = onOpenHdrLab)
            }
            } // end StaggeredPageProvider (per-page)
        }
    }
}
