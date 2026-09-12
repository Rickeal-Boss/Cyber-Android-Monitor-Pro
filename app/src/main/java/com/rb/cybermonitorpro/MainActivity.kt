package com.rb.cybermonitorpro

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.draw.drawBehind
import android.graphics.RenderEffect
import android.graphics.Shader
// ★ Compose 有自己的 RenderEffect 包装类型(androidx.compose.ui.graphics.RenderEffect),
//   GraphicsLayerScope.renderEffect 要的是后者, 故 android.graphics 版需经此扩展转换。
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
// F3-flow: 手写 container transform 用 —— layout 在布局期把内层摆到插值矩形;
//   positionInWindow 取覆盖层宿主原点(与卡片 boundsInWindow 同坐标系, 减出局部起点矩形)。
//   ★ 注意: androidx.compose.ui.geometry.lerp(Rect 重载) 与 androidx.compose.ui.unit.lerp(Dp 重载) 同名,
//   本文件两者都用, 故一律写全限定名调用、不 import 任何一个, 避免同名 import 歧义。
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
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
import com.rb.cybermonitorpro.data.AppFirstLaunch
import com.rb.cybermonitorpro.service.FloatingWindowConfig
import com.rb.cybermonitorpro.ui.AppViewModel
import com.rb.cybermonitorpro.RefreshPolicy
import com.rb.cybermonitorpro.ui.battery.BatteryScreen
import com.rb.cybermonitorpro.ui.components.CyberIcons
import com.rb.cybermonitorpro.ui.components.LightCircleBackButton
import com.rb.cybermonitorpro.ui.components.GlassCircleButton
import com.rb.cybermonitorpro.ui.components.NeonDivider
import com.rb.cybermonitorpro.ui.components.NeonHeaderDecoration
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
import com.rb.cybermonitorpro.ui.effects.LocalSharedTransitionScope
import com.rb.cybermonitorpro.ui.effects.StaggeredPageProvider
import com.rb.cybermonitorpro.ui.effects.acrylic
import com.rb.cybermonitorpro.ui.effects.circularReveal
import com.rb.cybermonitorpro.ui.effects.rememberCircularRevealState
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
        // ★ 首次启动权限引导: 一次性展示, 同意/稍后均 markShown 后切入主界面
        val needsFirstLaunch = AppFirstLaunch.shouldShow(this)
        try {
            // ★ 二分法通关: 用回完整 SystemMonitorApp（安全版 NeonHeaderDecoration）
            setContent {
                DeviceInfoViewerTheme {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        var showFirstLaunch by remember { mutableStateOf(needsFirstLaunch) }
                        if (showFirstLaunch) {
                            FirstLaunchPermissionScreen(
                                onDismiss = {
                                    AppFirstLaunch.markShown(this@MainActivity)
                                    showFirstLaunch = false
                                }
                            )
                        } else {
                            SystemMonitorApp()
                        }
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

/**
 * 首次启动权限引导页 — 复用 MainActivity + 状态切换（方案 B）。
 * 按 API 过滤运行时权限清单；SYSTEM_ALERT_WINDOW 不并入 RequestMultiplePermissions
 * （它必须走 ACTION_MANAGE_OVERLAY_PERMISSION，悬浮窗页已按需引导）。
 * 首启页不拦截 Back：默认系统返回可退出，不阻塞用户。
 */
@Composable
private fun FirstLaunchPermissionScreen(onDismiss: () -> Unit) {
    val runtimePerms = remember {
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
            add(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= 29) add(Manifest.permission.ACTIVITY_RECOGNITION)
            // ★ 身体传感器(API 23+ 运行时权限): TYPE_HEART_RATE 直读需要; API 21/22 由清单声明装机即授
            if (Build.VERSION.SDK_INT >= 23) add(Manifest.permission.BODY_SENSORS)
            if (Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { onDismiss() }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            stringResource(R.string.first_launch_title),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = NeonPurpleBright
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.first_launch_desc),
            fontSize = 14.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = { launcher.launch(runtimePerms) },
            colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
        ) {
            Text(stringResource(R.string.first_launch_grant), fontSize = 15.sp)
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = { onDismiss() }) {
            Text(stringResource(R.string.first_launch_later), fontSize = 14.sp, color = TextSecondary)
        }
    }
}

private data class TopTabItem(val title: String, val iconRes: Int)

// F5 → CAMP 修复: 传感器/HDR 卡片式进出 — 固定时长缓动(对齐目标视频 v4: 进 550ms / 出 450ms, 减速无回弹)
private val CARD_ENTRY_SPEC = tween<Float>(durationMillis = 550, easing = FastOutSlowInEasing)
private val CARD_EXIT_SPEC  = tween<Float>(durationMillis = 450, easing = FastOutSlowInEasing)

/**
 * 转场期间"其它地方"的模糊【最大】半径 —— 实际半径由过渡主时钟插值 0 → 本值(即随动画渐变, 非固定强度)。
 * 依赖: RenderEffect(Android 12 / API 31+); API < 31 不挂层, 自动降级为仅 scrim 压暗。
 * 实现: 必须走 graphicsLayer{ renderEffect } 而非 Modifier.blur —— 后者是 modifier 参数,
 *       改强度要重组(会打断转场); 前者在 draw 阶段按帧改半径, 零重组。
 */
private val BG_BLUR_MAX_DP = 10.dp

/**
 * 卡片 ↔ 详情"交还"窗口(占主时钟的比例): 最后这一段里容器表面变【半透明】,
 * 让底下真卡片/列表透出, 与仍完整可见的详情内容【画面叠加】(对齐参考效果的一镜到底收尾)。
 * ⚠️ 不能让内容也一起淡掉 —— 那会把"叠加"变成"消失", 末段只剩空壳。
 * 表面不必降到 0: 容器 p=0 时与真卡片同位同尺寸、且 CyberCardStart 与卡片色相同,
 * 因此交还结束时容器直接移除也不会有色跳/闪变。
 */
private const val HANDOFF_FRACTION = 0.18f

/** 交还末段容器表面的【最低】不透明度(p=0 时), 其余线性回到 0.92。 */
private const val HANDOFF_MIN_SURFACE = 0.35f

/** 详情内容在收起末段的淡出窗口(占主时钟比例) —— 比表面窗口更窄、更靠后。 */
private const val CONTENT_FADE_FRACTION = 0.10f

/**
 * 传感器详情覆盖层【实例】—— 支持多个并存, 这是"并行动画"的载体。
 *
 * 场景: 覆盖层 A 正在播"收回卡片 A"的收起动画时, 用户点了卡片 B →
 *   A 实例继续推进自己的收起时钟, 同时新建 B 实例从卡片 B 长大到全屏,
 *   两个动画【各自独立、同步推进】, 既不串行等待也不互相打断。
 *
 * 每个实例持有自己的: 传感器、起点矩形、几何时钟(progress)、压暗时钟(scrim)。
 * ⚠️ 两条时钟必须是 Animatable 并且 per-instance —— 早期单实例实现里
 *    progress/scrim 是组合级的单个 Animatable, 后一次点击必然打断前一次动画。
 */
private class SensorOverlay(
    val sensor: com.rb.cybermonitorpro.data.model.SensorItemInfo,
    val startRect: Rect?
) {
    /** 几何主时钟: 0f=贴合起点卡片矩形, 1f=全屏 */
    val progress = Animatable(0f)
    /** 压暗主时钟: 与 progress 并行推进(打开同步压暗/收起同步解除) */
    val scrim = Animatable(0f)
    /** 转场是否已稳定(容器已铺满) —— 仅此时才由根 Box 吃点击隔绝主界面 */
    var settled by mutableStateOf(false)
}

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

    // ★ 采集真值表 (2026-09-01): 悬浮窗开启时 Activity 销毁(如滑走任务)不停采集 — 悬浮窗仍需数据;
    //   悬浮窗关闭时照常停。MainActivity 销毁而进程因前台服务存活 → 后续由 AppViewModel.onCleared 同守卫兜住
    DisposableEffect(Unit) {
        safeViewModel.startMonitoring()
        onDispose { if (!FloatingWindowConfig.enabled) safeViewModel.stopMonitoring() }
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
                    // ★ 采集真值表: 仅"悬浮窗关 + App 退后台"停采集 (第 4 行)
                    //   用 ON_STOP 而非 ON_PAUSE: 通知栏下拉/权限对话框只触发 ON_PAUSE, 用它会误杀前台采集
                    if (!FloatingWindowConfig.enabled) safeViewModel.stopMonitoring()
                }
                Lifecycle.Event.ON_START -> {
                    Log.d("SystemMonitor", "App → foreground, RefreshPolicy → FOREGROUND")
                    RefreshPolicy.updateState(RefreshPolicy.RefreshState.FOREGROUND)
                    // ★ 回前台无条件恢复采集 (repo.startMonitoring 幂等, 已在跑则零开销)
                    safeViewModel.startMonitoring()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var showSettings by remember { mutableStateOf(false) }
    var showFloatConfig by remember { mutableStateOf(false) }
    // ★ 2026-08-16: HDR 实验室（详情页二层 — 局部 EDR 真机验证）
    var showHdrLab by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // pagerState 提升到 SystemMonitorApp 层级 — 确保覆盖层返回时 Tab 位置不被重置
    val topTabs = rememberTopTabs()
    val pagerState = rememberPagerState(pageCount = { topTabs.size })
    val scope = rememberCoroutineScope()

    // GPS 智能开关状态 — 仅在需要时定位权限
    var gpsTabActive by remember { mutableStateOf(false) }

    // ── 传感器详情覆盖层: 【多实例并存】形态 (并行动画) ──
    //   旧实现是组合级的单个 Animatable 三元组(progress/scrim/alive), 因此"收起途中点新卡片"
    //   只能互相打断 —— 新的 animateTo 会取消旧的, 视觉上变成"一个容器半途改道"。
    //   现在每次打开都新建一个 SensorOverlay 实例(自带两条时钟), 渲染时逐个绘制:
    //   A 继续播"收回卡片 A", B 同时播"从卡片 B 长大到全屏", 两动画各自推进、互不干扰。
    //   渲染遍历读实例列表(组合期), 主时钟 progress/scrim 仍只在 layout/draw lambda 内读 → 零重组。
    val sensorOverlays = remember { mutableStateListOf<SensorOverlay>() }
    // 渲染条件/返回键接管都改看列表是否为空
    val sensorAlive = sensorOverlays.isNotEmpty()

    val overlayVisible = showSettings || showFloatConfig || sensorAlive || showHdrLab

    // ── F3-flow: 传感器/HDR 覆盖层"一镜到底"转场起点 ──
    //   sensorRevealRect/hdrRevealRect = 触发卡片(或入口行)的窗口矩形 boundsInWindow;
    //   overlayRootInWindow            = 覆盖层宿主(Scaffold 内容 Box)的窗口原点 positionInWindow();
    //   两者相减得到覆盖层局部坐标系里的起点矩形, 交给容器外层 Modifier.layout 做逐帧矩形插值。
    //   null = 无卡片来源(冷开/来源未上报) → 容器退化为居中 0.3 倍起点(等价旧中心缩放观感)。
    //   ★ 这三个都是普通 State(非 Animatable), 可在组合期读; 主时钟 sensorProgress/hdrProgress 仍只在 layout/draw 内读。
    //   ★ 原 pre7 起"只写不读"的死状态(Offset 版 reveal origin)已删除, 由 sensorRevealRect 取代。
    //   ★ 声明位置硬约束: 必须早于下方四个局部函数(openSensorDetail/closeSensorDetail/openHdrLab/closeHdrLab)。
    //     Kotlin 局部函数只能引用其"之前"声明的局部变量, 而 closeSensorDetail/closeHdrLab 体内
    //     会写 sensorRevealRect/hdrRevealRect 做收起清场 → 声明滞后即触发前向引用编译错误(CI pre1)。
    var sensorRevealRect by remember { mutableStateOf<Rect?>(null) }
    var hdrRevealRect by remember { mutableStateOf<Rect?>(null) }
    var overlayRootInWindow by remember { mutableStateOf(Offset.Zero) }

    // ── 背景模糊开关(仅 HDR 侧需要显式维护; 传感器侧由实例列表的 settled 直接推导) ──
    //   只有"背景确实可见"时才开, 避免给被不透明容器完全遮挡的场景白付 GPU 开销:
    //   容器长大铺满后背景不可见 → 关掉; 收起动画开始 → 再打开。
    //   设置/悬浮窗覆盖层是 0.85 半透明, 背景全程可见 → 由其 showXxx/isRevealing 直接判(见背景模糊 gate)。
    var hdrBlurActive by remember { mutableStateOf(false) }

    // ── 可打断过渡: 转场是否【已稳定】(容器已铺满、动画播完) ──
    //   true 时才由覆盖层根 Box 吃掉点击、隔绝主界面(既有 P1 行为);
    //   false(转场进行中) 不拦截 → 底层卡片可响应点击并打断当前动画,
    //   与设置/悬浮窗覆盖层(半透明、无点击吸收层, 转场中可穿透点击)行为保持一致。
    //   ⚠️ 传感器侧已迁到 SensorOverlay 实例内(每个实例各自记录 settled), 这里只剩 HDR。
    var hdrSettled by remember { mutableStateOf(false) }

    // ── 并行动画: 打开 = 新建实例(不动已在播的其它实例), 关闭 = 只收起【最新】那个实例 ──
    fun openSensorDetail(sensor: com.rb.cybermonitorpro.data.model.SensorItemInfo) {
        // ★ 关键: 不复用/打断既有实例。每次打开都新建独立实例(自带 progress/scrim),
        //   因此"覆盖层 A 正在收回卡片 A"时点卡片 B, A 继续播自己的收起动画、
        //   B 从卡片 B 长大到全屏, 两动画同步推进(而非串行或互相打断)。
        val ov = SensorOverlay(sensor = sensor, startRect = sensorRevealRect)
        sensorOverlays.add(ov)
        scope.launch {
            // 两时钟【并行】: 背景压暗与卡片长大同步进行 (打开期间背景可见 → 模糊由 !settled 推导)
            val geo = launch { ov.progress.animateTo(1f, CARD_ENTRY_SPEC) }   // ① 容器从卡片矩形长大
            val dim = launch { ov.scrim.animateTo(1f, CARD_ENTRY_SPEC) }      // ② 背景同步压暗
            geo.join(); dim.join()
            ov.settled = true    // 该实例转场稳定 → 恢复点击隔绝 (且不再需要背景模糊)
        }
    }

    fun closeSensorDetail() {
        // ★ 只收起【最新】那个实例: 若此刻还有更早的实例正在收回, 它不受影响, 继续播完自己的动画。
        val ov = sensorOverlays.lastOrNull() ?: return
        ov.settled = false       // 收起过程中不拦截 → 可被打断; 同时重新需要背景模糊
        scope.launch {
            // ★ 返回键即时响应: 两时钟【并行】。旧实现串行(scrim 先 450ms → 容器再 450ms),
            //   而容器铺满时 scrim 在它背后、根本不可见 → 按下返回键后有约 450ms 视觉上毫无反应(像卡顿)。
            //   并行后容器在按下当帧就开始收起; scrim 同步淡出, 背景随容器收缩而逐渐露出(对齐参考效果)。
            val dim = launch { ov.scrim.animateTo(0f, CARD_EXIT_SPEC) }   // 背景同步解除压暗
            val geo = launch { ov.progress.animateTo(0f, CARD_EXIT_SPEC) } // 容器收回到起点矩形
            dim.join(); geo.join()
            // 收起完成 → 只移除【本实例】; 其它实例(可能正在长大或正在收回)完全不受影响
            sensorOverlays.remove(ov)
            // 起点矩形不再需要全局清理: 它已在创建实例时被快照进 ov.startRect
        }
    }

    // ── F5 → CAMP 修复: HDR 实验室覆盖层 — scrim + 入口行矩形一镜到底(可打断过渡, 主时钟 hdrProgress) ──
    //   进入: animateTo(1f, CARD_ENTRY_SPEC) 550ms 从入口行矩形插值展开到全屏;
    //   SurfaceView 延迟到进入动画完成后挂载(防 punch-through 突跳);
    //   预测返回: snapTo 跟手 / 取消回弹 1f / 完成 animateTo(0f) 后移出组合。
    //   scrim(hdrScrim) 与容器进度仍是两个 Animatable(预测返回各自 snapTo), 但打开时【并行】推进
    //   (容器变换二轮, 同 openSensorDetail); 关闭时两时钟【并行】解除, 返回键即时响应。
    val hdrProgress = remember { Animatable(0f) }
    // scrim 主时钟: 打开时与 hdrProgress 并行 animateTo (见 sensorScrim 注释)
    val hdrScrim = remember { Animatable(0f) }
    var hdrAlive by remember { mutableStateOf(false) }
    var hdrSurfacesVisible by remember { mutableStateOf(false) }

    fun openHdrLab() {
        showHdrLab = true
        hdrAlive = true
        hdrSurfacesVisible = false
        hdrSettled = false                                                      // ★ 同传感器: 转场中不拦截点击
        hdrBlurActive = true                                                    // ★ 转场期间背景可见 → 开模糊
        scope.launch {
            // ★ 容器变换二轮: 与 openSensorDetail 同款并行编排 (几何与压暗同步)。
            val geo = launch { hdrProgress.animateTo(1f, CARD_ENTRY_SPEC) }      // ① 容器从入口行矩形长大
            val dim = launch { hdrScrim.animateTo(1f, CARD_ENTRY_SPEC) }         // ② 背景同步压暗
            geo.join(); dim.join()
            hdrBlurActive = false   // 容器已铺满, 关模糊
            hdrSettled = true       // 转场稳定 → 恢复点击隔绝
            hdrSurfacesVisible = true   // 两个动画都完成后才挂载 SurfaceView, 防 punch-through 突跳
        }
    }

    fun closeHdrLab() {
        hdrSurfacesVisible = false     // 先卸载, 再播退出遮罩
        hdrSettled = false             // ★ 收起过程中不拦截 → 可被打断
        hdrBlurActive = true           // 收起过程中背景重新露出 → 开模糊
        scope.launch {
            // ★ 返回键即时响应: 与 closeSensorDetail 同款并行编排(几何与压暗同步)。
            val dim = launch { hdrScrim.animateTo(0f, CARD_EXIT_SPEC) }      // 背景同步解除压暗
            val geo = launch { hdrProgress.animateTo(0f, CARD_EXIT_SPEC) }   // 容器收回到入口行矩形
            dim.join(); geo.join()
            showHdrLab = false
            hdrAlive = false
            hdrRevealRect = null                            // ③ 收起完成, 清起点矩形
            hdrBlurActive = false                           // ④ 收起结束, 关模糊
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

    // 兜底原点: 触发点未上报时从右上角按钮区展开（与悬浮窗/设置按钮同区, CAMP 二轮修复:
    //   原屏幕中心兜底导致冷启动首开圆形从中心展开, 与按钮位置脱节）
    val configuration = LocalConfiguration.current
    val revealDensity = LocalDensity.current
    val fallbackOrigin = remember(configuration, revealDensity) {
        with(revealDensity) {
            Offset(
                configuration.screenWidthDp.dp.toPx() - 56.dp.toPx(),  // 药丸头部右侧按钮区
                40.dp.toPx()
            )
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

    // ── 预测性返回: PredictiveBackHandler 接收手指拖拽进度 ──
    // activity-compose 1.9.0 中 PredictiveBackHandler 已稳定（无需 @OptIn）
    // CAMP 二轮修复: 手势进度直接驱动各自覆盖层的过渡主时钟 —
    //   设置/悬浮窗 → 水波纹圆形收缩 (settingsReveal/floatReveal.progress);
    //   传感器/HDR → 卡片矩形收缩 (sensorProgress/hdrProgress, 布局期矩形插值)。
    //   放开手: 完成 → 收缩到底并关闭; 中途取消 → 回弹至 1f。
    //   ★ 无进度事件时(ROM 不支持/被阉割预测动画、或实体/导航栏返回键)不再 snapTo(0) 吞掉动画,
    //     而是改走与"按返回键"完全相同的完整退场动画(closeXxx / circularReveal.collapse)。
    //
    //   国产 ROM (MIUI/ColorOS/OriginOS/HarmonyOS) 兼容性策略:
    //   1. AndroidManifest application+activity 均已声明 enableOnBackInvokedCallback="true"
    //   2. Android 13+ 需用户在开发者选项开启"预测性返回动画"，14+ 默认开启，15+ 强制
    //   3. 国产 ROM 即使阉割预测动画，PredictiveBackHandler 的 flow 为空 → 立即完成
    //      等价普通 BackHandler，覆盖层仍能正常关闭（仅无跟手进度动画）
    //   4. 与 MainTabs 的 pager BackHandler 互斥: overlayVisible 时本回调启用，
    //      MainTabs BackHandler enabled = (currentPage!=0 && !overlayVisible) 为 false
    //   5. BackGestureCompat 工具在启动时输出诊断日志，辅助排查 ROM 兼容性问题
    val backProgress = remember { Animatable(0f) }

    PredictiveBackHandler(enabled = overlayVisible) { progress: Flow<BackEventCompat> ->
        val settingsStart = if (showSettings) settingsReveal.progress.value else 0f
        val floatStart = if (showFloatConfig) floatReveal.progress.value else 0f
        // 传感器: 只对【最新】实例做跟手与收尾 —— 更早的实例(若仍在播)不受返回手势影响
        val sensorTop = sensorOverlays.lastOrNull()
        val sensorStart = sensorTop?.progress?.value ?: 0f
        val hdrStart = if (showHdrLab) hdrProgress.value else 0f
        try {
            var receivedProgress = false      // ★ 是否收到过跟手进度事件
            // ★ 跟手拖拽期间背景可见 → 同样开模糊(半径随拖拽进度渐变, 与按钮返回/打开收起体验一致,
            //   避免同一转场在"按钮返回"与"手势返回"两种路径下模糊表现割裂)。
            //   传感器侧模糊由"实例 !settled"推导(collect 里会把实例置为未稳定), 故这里只需 HDR 的显式开关。
            hdrBlurActive = true
            progress.collect { event ->
                receivedProgress = true
                backProgress.snapTo(event.progress)
                val t = 1f - event.progress
                if (showSettings) settingsReveal.progress.snapTo((settingsStart * t).coerceIn(0f, 1f))
                if (showFloatConfig) floatReveal.progress.snapTo((floatStart * t).coerceIn(0f, 1f))
                if (sensorTop != null) {
                    sensorTop.settled = false   // 拖拽期不拦截点击 + 重新需要背景模糊
                    sensorTop.progress.snapTo((sensorStart * t).coerceIn(0f, 1f))
                    sensorTop.scrim.snapTo((sensorStart * t * t).coerceIn(0f, 1f))   // 关闭拖拽: scrim 先行解除隔绝
                }
                if (showHdrLab) {
                    hdrProgress.snapTo((hdrStart * t).coerceIn(0f, 1f))
                    hdrScrim.snapTo((hdrStart * t * t).coerceIn(0f, 1f))
                }
            }
            // ★ 无预测性返回进度: ROM 不支持预测动画 / 被阉割 / 实体或导航栏返回键 →
            //   绝不能把主时钟 snapTo(0) 直接吞掉动画, 必须播与"按返回键"完全一致的完整退场动画。
            if (!receivedProgress) {
                backProgress.snapTo(0f)
                when {
                    sensorTop != null -> closeSensorDetail()  // 收起最新实例(两时钟并行), 容器收回其起点矩形
                    showHdrLab -> closeHdrLab()
                    showSettings -> showSettings = false      // LaunchedEffect 触发 settingsReveal.collapse()
                    showFloatConfig -> showFloatConfig = false
                }
                return@PredictiveBackHandler
            }
            // 手势完成 — 各覆盖层已被手指拖到收缩态, 直接收尾(动画已由跟手过程播完)
            backProgress.snapTo(0f)
            when {
                sensorTop != null -> {
                    // 跟手已把该实例拖到收缩态 → 直接归零并移除(只动这一个实例)
                    sensorTop.progress.snapTo(0f)
                    sensorTop.scrim.snapTo(0f)
                    sensorTop.settled = false
                    sensorOverlays.remove(sensorTop)
                }
                showHdrLab -> {
                    hdrSurfacesVisible = false
                    hdrProgress.snapTo(0f)
                    hdrScrim.snapTo(0f)
                    showHdrLab = false
                    hdrAlive = false
                    hdrSettled = false
                    hdrBlurActive = false     // 覆盖层已移除, 关模糊
                    hdrRevealRect = null      // 同上
                }
                showSettings -> {
                    settingsReveal.progress.snapTo(0f)
                    showSettings = false
                }
                showFloatConfig -> {
                    floatReveal.progress.snapTo(0f)
                    showFloatConfig = false
                }
            }
        } catch (e: CancellationException) {
            // 手势取消 — 平滑回弹至展开态 (仅支持预测的 ROM 会触发)
            backProgress.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            if (showSettings) settingsReveal.progress.animateTo(1f, tween(400))
            if (showFloatConfig) floatReveal.progress.animateTo(1f, tween(400))
            if (sensorTop != null) {
                sensorTop.progress.animateTo(1f, CARD_ENTRY_SPEC)
                sensorTop.scrim.animateTo(1f, CARD_ENTRY_SPEC)
                sensorTop.settled = true    // 回弹完成 → 容器重新铺满, 恢复点击隔绝(且不再需要模糊)
            }
            if (showHdrLab) {
                hdrProgress.animateTo(1f, CARD_ENTRY_SPEC)
                hdrScrim.animateTo(1f, CARD_ENTRY_SPEC)
            }
            // 回弹完成 → 容器重新铺满, 背景不可见 → 关模糊 (hdrBlurActive 会在下次拖拽/收起时再开)
            hdrBlurActive = false
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
        // F3-flow: 记录覆盖层宿主原点 —— 该 onGloballyPositioned 挂在链尾(最内), 上报的坐标即
        //   Box 放置其子节点(各覆盖层)所用的坐标系原点, 与卡片 boundsInWindow 同在窗口坐标系,
        //   相减即得覆盖层局部起点矩形(勿移到 .padding() 之前, 否则会多带一层 padding 偏移)。
        Box(Modifier.padding(padding).fillMaxSize()
            .onGloballyPositioned { overlayRootInWindow = it.positionInWindow() }
        ) {
            // ★ F5-2: SharedTransitionLayout 包裹 — 传感器卡片 ↔ 详情标题 sharedElement 形变
            //   (CARD-01: 实验 API 必须 @OptIn; scope 经 CompositionLocal 下发, 调用方判空降级)
            SharedTransitionLayout {
                CompositionLocalProvider(LocalSharedTransitionScope provides this@SharedTransitionLayout) {
            // ★ 主 Tab 页始终保持在 composition 中，保留所有滚动状态
            //   背景模糊(随动画渐变): 把主界面包一层, 用 graphicsLayer + RenderEffect 按帧插值模糊半径,
            //   使焦点随卡片长大/覆盖层展开而逐步转移到前景。半径 0 → BG_BLUR_MAX_DP, 非固定强度;
            //   API < 31 无 RenderEffect → 整层不挂, 自动降级为仅 scrim 压暗。
            Box(
                Modifier.fillMaxSize().then(
                    if (Build.VERSION.SDK_INT >= 31) Modifier.graphicsLayer {
                        // 各覆盖层取各自的过渡主时钟; isRevealing/Alive 用于覆盖"已置 showXxx=false 但仍在退场"的阶段
                        val p = when {
                            showSettings || settingsReveal.isRevealing -> settingsReveal.progress.value
                            showFloatConfig || floatReveal.isRevealing -> floatReveal.progress.value
                            // 传感器: 取最新实例的主时钟(多实例并存时模糊随"前景那个"的转场渐变)
                            sensorAlive -> sensorOverlays.lastOrNull()?.progress?.value ?: 0f
                            showHdrLab || hdrAlive -> hdrProgress.value
                            else -> 0f
                        }
                        // 传感器/HDR 容器铺满后不再需要模糊(背景不可见), 不给白付 GPU:
                        //   传感器 → 任一实例 unsettled 即需要; HDR → 显式开关 hdrBlurActive
                        val gate = sensorOverlays.any { !it.settled } || hdrBlurActive ||
                            showSettings || settingsReveal.isRevealing ||
                            showFloatConfig || floatReveal.isRevealing
                        val radiusPx = if (gate) BG_BLUR_MAX_DP.toPx() * p.coerceIn(0f, 1f) else 0f
                        // 量化到 0.5px: 减少每帧新建 RenderEffect 对象的分配
                        val q = (radiusPx * 2f).toInt() / 2f
                        renderEffect = if (q > 0.5f)
                            RenderEffect.createBlurEffect(q, q, Shader.TileMode.CLAMP).asComposeRenderEffect()
                        else null
                    } else Modifier
                )
            ) {
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
                onOpenSensorDetail = { sensor, rect ->
                    sensorRevealRect = rect
                    openSensorDetail(sensor)
                },
                onOpenHdrLab = { rect ->
                    hdrRevealRect = rect
                    openHdrLab()
                }
            )
            } // end 背景静态模糊 Box

            // ── 覆盖层 (graphicsLayer 透明动画, 保持 composition 存活) ──
            // 使用 graphicsLayer.alpha 替代 AnimatedVisibility:
            //   覆盖层在退出动画期间仍留在 composition 树中,
            //   系统预测性返回 (Android 15+) 可以跨页面渐变动画。

            // ── 设置 (F3: 水波纹圆形展开; 预测返回手势驱动圆形收缩, 见 PredictiveBackHandler) ──
            //   CAMP 二轮修复: 覆盖层回归半透明 (0.85/0.85) — 底下传感器卡片与 SDR 描边可透出,
            //   圆形展开本身即揭示过程, 无需实心化。
            if (settingsReveal.isRevealing || showSettings) {
                Box(Modifier.fillMaxSize()
                    .circularReveal(
                        progress = { settingsReveal.progress.value },
                        origin = { if (settingsOrigin != Offset.Zero) settingsOrigin else fallbackOrigin },
                    )
                    .acrylic(
                        tintColor = CyberCardStart,
                        tintOpacity = 0.85f,
                        endOpacityMultiplier = 0.85f,
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

            // ── 悬浮窗 (F3: 水波纹圆形展开; 预测返回手势驱动圆形收缩, 见 PredictiveBackHandler) ──
            if (floatReveal.isRevealing || showFloatConfig) {
                Box(Modifier.fillMaxSize()
                    .circularReveal(
                        progress = { floatReveal.progress.value },
                        origin = { if (floatOrigin != Offset.Zero) floatOrigin else fallbackOrigin },
                    )
                    .acrylic(
                        tintColor = CyberCardStart,
                        tintOpacity = 0.85f,
                        endOpacityMultiplier = 0.85f,
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

            // ── HDR 实验室（F3-flow 三: 入口行矩形 → 全屏 一镜到底, 替代原"屏幕中心 0.3 倍缩放"）──
            //   容器 = 「外层定位 + 内层尺寸」双节点: 外层 Modifier.layout 在布局期把内层摆到插值矩形 r,
            //   内层自身尺寸即 r 的宽高 —— 小矩形长成全屏是真实布局尺寸变化(非位图级缩放), 文字不再被拉伸变形;
            //   起点矩形 = 入口行窗口矩形 hdrRevealRect(boundsInWindow) - 覆盖层宿主原点 overlayRootInWindow,
            //   无来源(null)时退化为居中 0.3 倍矩形(等价旧观感); 内层圆角 20dp→0 随 *Progress 收口 + clip 裁剪内容。
            //   容器背景(CyberCardStart ×0.92)自 p=0 首段 15% 渐入后恒定不透明(容器变换二轮) —
            //   起始态即被点入口行所在表面, 随几何一起长大; 黑色 scrim 仍由 hdrScrim 驱动并与几何并行压暗,
            //   打开=容器长大 + 背景同步压暗; 关闭=容器收起 + 背景同步解除(两时钟并行, 返回键即时响应);
            //   关闭=容器收起与背景解除同步进行(两时钟并行); 0.22 scrim 与 bg 同源 (均跟 *Scrim)
            //   (hdrSurfacesVisible 门控, 防 punch-through 突跳);
            //   渲染条件读 hdrAlive State; 预测返回手势 snapTo 跟手、取消回弹 1f。
            if (hdrAlive || showHdrLab) {
                // P1: 覆盖层根 Box 消费点击, 隔绝主界面触摸 (内部交互仍由子节点优先消费)
                // ★ 可打断过渡: 仅在 hdrSettled(转场已稳定/容器铺满) 时才吃点击 ——
                //   转场进行中不拦截, 底层入口行/卡片可响应点击并打断当前动画(对齐设置/悬浮窗覆盖层行为)。
                Box(Modifier.fillMaxSize()
                    .then(if (hdrSettled) Modifier.pointerInput(Unit) { detectTapGestures { } } else Modifier)
                ) {
                    // ① scrim: 全屏压暗层 (alpha 由 hdrScrim 驱动, 二次曲线半透明; 打开时与 hdrProgress【并行】压暗, 关闭时两时钟【并行】解除, 返回键即时响应)
                    //   pre12 修复: 改 drawBehind 直接以目标 alpha 画黑矩形, 取代 "background(Color.Black)+graphicsLayer{alpha}" —
                    //   后者把实心黑填进离屏 alpha 层, 首帧离屏缓冲被清成不透明黑 → scrim 下出现全黑闪层。
                    Box(Modifier.fillMaxSize()
                        .drawBehind {
                            drawRect(
                                color = Color.Black,
                                alpha = (hdrScrim.value * hdrScrim.value) * 0.22f
                            )
                        }
                    )
                    // ② 卡片容器: 外层只负责"摆位置"(布局期矩形插值), 内层自身尺寸 = 插值矩形尺寸;
                    //   背景(CyberCardStart ×0.92) 自 p=0 首段 15% 渐入后恒定不透明(容器变换二轮, 详见内层注释):
                    //   打开=容器长大与背景压暗同步进行; 关闭=容器收起与背景解除同步进行
                    val hdrStartLocal: Rect = hdrRevealRect?.let {
                        Rect(
                            it.left - overlayRootInWindow.x, it.top - overlayRootInWindow.y,
                            it.right - overlayRootInWindow.x, it.bottom - overlayRootInWindow.y
                        )
                    } ?: Rect.Zero
                    Box(Modifier.fillMaxSize()
                        .layout { measurable, constraints ->
                            // 布局期读主时钟: 只致 layout 失效, 零重组 (与旧 graphicsLayer 内读法同源)
                            val p = hdrProgress.value
                            val maxW = constraints.maxWidth
                            val maxH = constraints.maxHeight
                            val target = Rect(0f, 0f, maxW.toFloat(), maxH.toFloat())
                            val from = if (hdrStartLocal == Rect.Zero)
                                Rect(maxW * 0.35f, maxH * 0.35f, maxW * 0.65f, maxH * 0.65f)
                            else hdrStartLocal
                            val r = androidx.compose.ui.geometry.lerp(from, target, p)
                            // 用 toInt() 截断(亚像素差, 观感等同取整): 本项目约定不引 kotlin.math.roundToInt
                            //   (同 BatteryScreen.snapTierIndex 的 (raw+0.5f).toInt() 规避, 见其注释)
                            val w = r.width.toInt().coerceAtLeast(1)
                            val h = r.height.toInt().coerceAtLeast(1)
                            val placeable = measurable.measure(Constraints.fixed(w, h))
                            layout(maxW, maxH) {
                                placeable.place(r.left.toInt(), r.top.toInt())
                            }
                        }
                    ) {
                        // 内层: 自身尺寸 = 插值矩形尺寸 → 圆角 20dp 收口到 0 + clip 裁剪。
                        // ★ 容器变换二轮(F3-flow): 与传感器覆盖层同款 —— bg 自 p=0 起恒定不透明(详见传感器侧注释)。
                        //   旧实现 bg 跟 hdrScrim(s>0.6 淡入) → 长大过程容器全透明, 同样退化为"从底部升起"。
                        // pre12 修复保留: 容器背景 alpha 移出任何层属性, 改 drawBehind 直接画带 alpha 的 CyberCardStart
                        // (圆角/裁剪留在 graphicsLayer, 不参与 alpha 合成)。
                        Box(Modifier.fillMaxSize()
                            .graphicsLayer {
                                val p = hdrProgress.value
                                shape = RoundedCornerShape(androidx.compose.ui.unit.lerp(20.dp, 0.dp, p))
                                clip = true
                            }
                            .drawBehind {
                                // 与传感器覆盖层同款: 末段 HANDOFF 内表面 0.92 → 0.35 半透明, 让目标画面透出叠加。
                                drawRect(
                                    color = CyberCardStart,
                                    alpha = 0.92f - (0.92f - HANDOFF_MIN_SURFACE) *
                                        ((1f - hdrProgress.value / HANDOFF_FRACTION).coerceIn(0f, 1f))
                                )
                            }
                        ) {
                            // ③ 内容渐变 + 上移 (SurfaceView 由 surfaceVisible 门控延迟挂载) — 原样保留
                            Box(Modifier.fillMaxSize()
                                .graphicsLayer {
                                    val p = hdrProgress.value
                                    // ★ 同传感器: 内容全程可见, 仅最后 10% 快速淡出 → 收尾是"叠加"而非"消失"。
                                    val ca = ((p - 0.06f) / 0.10f).coerceIn(0f, 1f) *
                                        ((p / CONTENT_FADE_FRACTION).coerceIn(0f, 1f))
                                    alpha = ca
                                    translationY = (1f - ca) * 24.dp.toPx()
                                }
                            ) {
                                HdrLabScreen(onBack = { closeHdrLab() }, surfaceVisible = hdrSurfacesVisible)
                                LightCircleBackButton(
                                    onClick = { closeHdrLab() },
                                    btnSize = 48.dp,
                                    modifier = Modifier.padding(top = 8.dp, start = 16.dp).align(Alignment.TopStart)
                                )
                            }
                        }
                    }
                }
            }

            // ── 传感器详情 (F3-flow: 卡片矩形 → 全屏 一镜到底, 替代原"屏幕中心 0.3 倍缩放") ──
            //   容器 = 「外层定位 + 内层尺寸」双节点: 外层 Modifier.layout 布局期把内层摆到插值矩形 r,
            //   内层自身尺寸即 r 的宽高 —— 卡片小矩形长成全屏是真实布局尺寸变化(非位图级缩放), 文字不再被拉伸变形;
            //   起点矩形 = 卡片窗口矩形 sensorRevealRect(boundsInWindow) - 覆盖层宿主原点 overlayRootInWindow,
            //   无来源(null)时退化为居中 0.3 倍矩形(等价旧观感); 内层圆角 20dp→0 随 *Progress 收口 + clip 裁剪内容。
            //   容器背景(CyberCardStart ×0.92)自 p=0 首段 15% 渐入后恒定不透明(容器变换二轮) —
            //   起始态即被点卡片本身, 随几何一起长大; 黑色 scrim 由【本实例的 scrim】驱动并与几何并行压暗,
            //   打开=容器长大 + 背景同步压暗; 关闭=容器收起 + 背景同步解除(两时钟并行, 返回键即时响应);
            //   关闭=容器收起与背景解除同步进行(两时钟并行); 0.22 scrim 与 bg 同源 (均跟 *Scrim)
            //   内容: alpha 渐变 (p>0.25 后) + 24dp 上移, 由容器尺寸先行、内容跟进;
            //   渲染条件 = 实例列表非空(sensorAlive); 各实例主时钟只在 layout/draw 内读, 绝不在组合期读。
            //   ★ 并行动画: 逐【实例】绘制 —— 每个 SensorOverlay 自带 progress/scrim/起点矩形,
            //     因此"旧的正在收回卡片 A"与"新的正从卡片 B 长大"可同时存在于列表中、各自独立推进。
            //     渲染遍历读实例列表(组合期); 各实例主时钟仍只在 layout/draw lambda 内读 → 零重组。
            sensorOverlays.forEach { ov ->
                run {
                    val sensor = ov.sensor
                    val density = LocalDensity.current
                    // P1: 覆盖层根 Box 消费点击, 隔绝主界面触摸 (内部交互仍由子节点优先消费)
                    // ★ 可打断过渡: 仅在【该实例】已稳定(转场播完)时才吃点击 —— 转场中不拦截,
                    //   底层卡片可响应点击(正是"并行动画"的触发条件)。
                    Box(Modifier.fillMaxSize()
                        .then(if (ov.settled) Modifier.pointerInput(Unit) { detectTapGestures { } } else Modifier)
                    ) {
                        // ① scrim: 全屏压暗层 (alpha 由本实例 scrim 驱动, 二次曲线半透明; 与 progress 并行推进)
                        //   pre12 修复: 同 HDR scrim, 改 drawBehind 直接以目标 alpha 画黑矩形, 根除黑闪。
                        Box(Modifier.fillMaxSize()
                            .drawBehind {
                                drawRect(
                                    color = Color.Black,
                                    alpha = (ov.scrim.value * ov.scrim.value) * 0.22f
                                )
                            }
                        )
                        // ② 卡片容器: 外层只负责"摆位置"(布局期矩形插值), 内层自身尺寸 = 插值矩形尺寸;
                        //   背景(CyberCardStart ×0.92) 自 p=0 首段 15% 渐入后恒定不透明(容器变换二轮, 详见内层注释):
                        //   打开=容器长大与背景压暗同步进行; 关闭=容器收起与背景解除同步进行
                        // 起点矩形取【本实例】在创建时快照的 startRect —— 多实例并存时各画各的终点,
                        //   这正是"旧的收回卡片 A / 新的从卡片 B 长大"能同时成立的前提。
                        val sensorStartLocal: Rect = ov.startRect?.let {
                            Rect(
                                it.left - overlayRootInWindow.x, it.top - overlayRootInWindow.y,
                                it.right - overlayRootInWindow.x, it.bottom - overlayRootInWindow.y
                            )
                        } ?: Rect.Zero
                        Box(Modifier.fillMaxSize()
                            .layout { measurable, constraints ->
                                // 布局期读主时钟: 只致 layout 失效, 零重组 (与旧 graphicsLayer 内读法同源)
                                val p = ov.progress.value
                                val maxW = constraints.maxWidth
                                val maxH = constraints.maxHeight
                                val target = Rect(0f, 0f, maxW.toFloat(), maxH.toFloat())
                                val from = if (sensorStartLocal == Rect.Zero)
                                    Rect(maxW * 0.35f, maxH * 0.35f, maxW * 0.65f, maxH * 0.65f)
                                else sensorStartLocal
                                val r = androidx.compose.ui.geometry.lerp(from, target, p)
                                // 用 toInt() 截断(亚像素差, 观感等同取整): 本项目约定不引 kotlin.math.roundToInt
                                //   (同 BatteryScreen.snapTierIndex 的 (raw+0.5f).toInt() 规避, 见其注释)
                                val w = r.width.toInt().coerceAtLeast(1)
                                val h = r.height.toInt().coerceAtLeast(1)
                                val placeable = measurable.measure(Constraints.fixed(w, h))
                                layout(maxW, maxH) {
                                    placeable.place(r.left.toInt(), r.top.toInt())
                                }
                            }
                        ) {
                            // 内层: 自身尺寸 = 插值矩形尺寸 → 圆角 20dp 收口到 0 + clip 裁剪。
                            // ★ 容器变换二轮(F3-flow): bg 自 p=0 起【恒定不透明】—— 起始态就是被点卡片本身,
                            //   随几何一起从卡片长到全屏。旧实现 bg alpha 跟 sensorScrim(s>0.6 才淡入),
                            //   而 scrim 原本排在容器动画之后 → 长大全程容器透明, 只剩内容悬浮在未压暗列表上,
                            //   观感退化成"内容从底部升起"(真机 frames 已复现)。
                            //   色彩安全性: CyberCardStart 与卡片 containerColor(colorScheme.surface=CyberPill)
                            //   深色主题同为 0xFF1E2226、浅色仅差 FFFFFF/F7F8F5 → 无色跳。
                            //   仍用 drawBehind 直接画(非层属性 alpha), pre12 黑闪结论不变。
                            Box(Modifier.fillMaxSize()
                                .graphicsLayer {
                                    val p = ov.progress.value
                                    shape = RoundedCornerShape(androidx.compose.ui.unit.lerp(20.dp, 0.dp, p))
                                    clip = true
                                }
                                .drawBehind {
                                    // bg alpha = 首段 HANDOFF 内升满(0.35→0.92, 起点半透明让真卡片透出, 同色无色跳);
                                    // 末段 HANDOFF 内 0.92 → 0.35(半透明) —— 让底下目标画面透出与内容【叠加】,
                                    // 表面不降到 0: p=0 时容器与真卡片同位同尺寸同色, 直接移除也不闪变。
                                    drawRect(
                                        color = CyberCardStart,
                                        alpha = 0.92f - (0.92f - HANDOFF_MIN_SURFACE) *
                                            ((1f - ov.progress.value / HANDOFF_FRACTION).coerceIn(0f, 1f))
                                    )
                                }
                            ) {
                                // ③ 内容渐变 + 上移 (draw 阶段驱动, 零重组) — 原样保留
                                Box(Modifier.fillMaxSize()
                                    .graphicsLayer {
                                        val p = ov.progress.value
                                        // ★ 交还=画面叠加(对齐参考): 内容【全程保持可见】, 只在最后 CONTENT_FADE
                                        //   快速淡出 —— 此时表面已半透明、真卡片已透出, 不会出现空壳。
                                        //   旧写法 (p-0.10)/0.90 * (p/0.18) 会让内容在中段就很淡, 末段直接消失,
                                        //   叠加感全无(用户实测否决)。
                                        // 进场: p=0.06 起淡入, p=0.16 即全亮(表面此时恰已不透明, 无空壳窗口);
                                        // 出场: 仅最后 10% 快速淡出(表面已半透明、真卡片已透出)。
                                        val ca = ((p - 0.06f) / 0.10f).coerceIn(0f, 1f) *
                                            ((p / CONTENT_FADE_FRACTION).coerceIn(0f, 1f))
                                        alpha = ca
                                        translationY = (1f - ca) * 24.dp.toPx()
                                    }
                                ) {
                                    SensorDetailContent(
                                        sensor = sensor,
                                        progress = ov.progress,
                                        density = density,
                                        onBack = { closeSensorDetail() }
                                    )
                                }
                            }
                        }
                    }
                }
            }
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
    onOpenSensorDetail: (com.rb.cybermonitorpro.data.model.SensorItemInfo, Rect) -> Unit = { _, _ -> },
    onOpenHdrLab: (Rect) -> Unit = {}
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
        // ── 天青玻璃药丸头部: padding + 大圆角容器 + 动效装饰 ──
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp)
                .height(64.dp)
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
                        Modifier.tabIndicatorOffset(pos[pagerState.currentPage]),
                        color = NeonPurple,
                        height = 3.dp
                    )
                }
            ) {
            topTabs.forEachIndexed { i, tab ->
                val onTabClick: () -> Unit = remember(i, ctx, scope, pagerState) {
                    { HapticUtils.lightTap(ctx); scope.launch { pagerState.animateScrollToPage(i) }; Unit }
                }
                val selected = pagerState.currentPage == i
                Tab(
                    selected = selected,
                    onClick = onTabClick,
                    // ★ 足线选中态: 选中 Tab 背景 10% 天青药丸(圆角 12dp) + 底部 2dp 天青短线
                    modifier = Modifier.drawBehind {
                        if (selected) {
                            drawRoundRect(
                                color = NeonPurple.copy(alpha = 0.10f),
                                cornerRadius = CornerRadius(12.dp.toPx())
                            )
                            val lineH = 2.dp.toPx()
                            val lineW = size.width * 0.5f
                            drawRoundRect(
                                color = NeonPurple,
                                topLeft = Offset(size.width / 2f - lineW / 2f, size.height - lineH - 5.dp.toPx()),
                                size = Size(lineW, lineH),
                                cornerRadius = CornerRadius(lineH / 2f)
                            )
                        }
                    },
                    text = {
                        Text(
                            text = tab.title,
                            fontSize = 13.sp,
                            color = if (selected) NeonPurple else NeonSteelBlue.copy(alpha = 0.7f),
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    icon = {
                        Icon(
                            painterResource(tab.iconRes), null,
                            Modifier.size(16.dp)
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
