package com.example.deviceinfoviewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.deviceinfoviewer.ui.AppViewModel
import com.example.deviceinfoviewer.ui.battery.BatteryScreen
import com.example.deviceinfoviewer.ui.cpu.CpuScreen
import com.example.deviceinfoviewer.ui.dashboard.DashboardScreen
import com.example.deviceinfoviewer.ui.device.DeviceScreen
import com.example.deviceinfoviewer.ui.floatwindow.FloatingWindowScreen
import com.example.deviceinfoviewer.ui.gps.GpsScreen
import com.example.deviceinfoviewer.ui.gpu.GpuScreen
import com.example.deviceinfoviewer.ui.memory.MemoryScreen
import com.example.deviceinfoviewer.ui.network.NetworkScreen
import com.example.deviceinfoviewer.ui.sensors.SensorsScreen
import com.example.deviceinfoviewer.ui.settings.SettingsScreen
import com.example.deviceinfoviewer.ui.theme.*
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
    val snackbarHostState = remember { SnackbarHostState() }

    // GPS 智能开关状态 — 仅在需要时请求定位权限
    var gpsTabActive by remember { mutableStateOf(false) }

    BackHandler(enabled = showSettings || showFloatConfig) {
        when {
            showSettings -> showSettings = false
            showFloatConfig -> showFloatConfig = false
        }
    }

    // GPS 开关观察 — 离开 GPS/网络 Tab 时自动关闭定位
    LaunchedEffect(gpsTabActive) {
        appViewModel.setGpsEnabled(gpsTabActive)
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        AnimatedContent(
            targetState = when {
                showSettings -> "settings"
                showFloatConfig -> "float"
                else -> "main"
            },
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
            modifier = Modifier.padding(padding)
        ) { screen ->
            when (screen) {
                "settings" -> Box(Modifier.fillMaxSize()) {
                    SettingsScreen()
                    IconButton(onClick = { showSettings = false },
                        Modifier.padding(top = 8.dp, start = 4.dp).align(Alignment.TopStart)
                    ) { Text("\u2190", fontSize = 22.sp, color = NeonPurple) }
                }
                "float" -> Box(Modifier.fillMaxSize()) {
                    FloatingWindowScreen(onBack = { showFloatConfig = false })
                    IconButton(onClick = { showFloatConfig = false },
                        Modifier.padding(top = 8.dp, start = 4.dp).align(Alignment.TopStart)
                    ) { Text("\u2190", fontSize = 22.sp, color = NeonPurple) }
                }
                else -> MainTabs(
                    onOpenSettings = { showSettings = true },
                    onOpenFloat = { showFloatConfig = true },
                    onGpsTabChanged = { active -> gpsTabActive = active }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTabs(
    onOpenSettings: () -> Unit,
    onOpenFloat: () -> Unit,
    onGpsTabChanged: (Boolean) -> Unit = {}
) {
    val pagerState = rememberPagerState(pageCount = { topTabs.size })
    val scope = rememberCoroutineScope()

    // 智能 GPS: 仅"网络" (index 5) 和 "GPS" (index 6) Tab 启用定位
    val currentPage = pagerState.currentPage
    LaunchedEffect(currentPage) {
        val isGpsRelated = currentPage == 5 || currentPage == 6
        onGpsTabChanged(isGpsRelated)
    }

    // 两步返回键退出: 不在概览页 → 回到概览页; 已在概览页 → 退出应用
    BackHandler(enabled = currentPage != 0) {
        scope.launch { pagerState.animateScrollToPage(0) }
    }

    Column(Modifier.fillMaxSize()) {
        // 紧凑型顶部栏：TabRow + 操作按钮在同一行
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.background,
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

        HorizontalDivider(thickness = 0.5.dp, color = NeonPurpleDeep.copy(alpha = 0.3f))

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
                    7 -> SensorsScreen()
                    8 -> DeviceScreen()
                }
            }
        }
    }
}
