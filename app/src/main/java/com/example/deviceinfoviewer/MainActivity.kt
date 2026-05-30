package com.example.deviceinfoviewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
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

// ── 底部导航 Tab 数据 ──
private data class PillTab(val label: String, val icon: ImageVector)

private val pillTabs = listOf(
    PillTab("概览", Icons.Default.Home),
    PillTab("CPU", Icons.Default.PlayArrow),
    PillTab("电池", Icons.Default.Favorite),
    PillTab("网络", Icons.Default.Share),
    PillTab("更多", Icons.Default.Search)
)

// 顶部 TabRow 用全部 9 页
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

// Pill tab → pager index mapping
private val pillToPage = mapOf(0 to 0, 1 to 1, 2 to 4, 3 to 5, 4 to 8)

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

    BackHandler(enabled = showSettings || showFloatConfig) {
        when {
            showSettings -> showSettings = false
            showFloatConfig -> showFloatConfig = false
        }
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
                    onOpenFloat = { showFloatConfig = true }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTabs(onOpenSettings: () -> Unit, onOpenFloat: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { topTabs.size })
    val scope = rememberCoroutineScope()

    Scaffold(
        bottomBar = {
            FloatingPillBottomBar(
                currentPage = pagerState.currentPage,
                onTabSelected = { pillIdx ->
                    pillToPage[pillIdx]?.let { page ->
                        scope.launch { pagerState.animateScrollToPage(page) }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            // ── 顶部 TabRow ──
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

            // ── 页面内容 ──
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
}

// ── 浮动药丸底部导航 (Ardot Cyberpunk Mobile HUD) ──
@Composable
private fun FloatingPillBottomBar(currentPage: Int, onTabSelected: (Int) -> Unit) {
    // 映射当前 page → pill tab index
    val currentPillIdx = when (currentPage) {
        0 -> 0   // 概览
        1 -> 1   // CPU
        4 -> 2   // 电池
        5 -> 3   // 网络
        else -> 4 // 更多 (详情/GPU/内存/GPS/传感器)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(CyberBackground.copy(alpha = 0.85f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        // 浮动药丸
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .shadow(20.dp, PurpleGlowStrong, RoundedCornerShape(36.dp))
                .clip(RoundedCornerShape(36.dp))
                .background(CyberPill)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            pillTabs.forEachIndexed { idx, tab ->
                val selected = idx == currentPillIdx
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .then(
                            if (selected) Modifier.background(NeonPurple)
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            tab.icon, null,
                            modifier = Modifier.size(18.dp),
                            tint = if (selected) Color.White else NeonSteelBlue.copy(alpha = 0.8f)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            tab.label, fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (selected) Color.White else NeonSteelBlue.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}
