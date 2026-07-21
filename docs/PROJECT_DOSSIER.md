# Cyber Android Monitor — 项目档案（Project Dossier）

> **用途**：长期查阅的单一事实源（single source of truth），覆盖结构 / 架构 / 逻辑 / 偏好 / 风格 / 技术债。后续会话先读此档再动手，避免上下文片断。
> **最后审查**：2026-07-19（全面代码审查，两位成员并行实地读码）
> **同步（版本对齐）**：2026-07-20（§13 工具链升级已 CI 绿灯，详情见 §11 近期变更；§0/§4 版本速查已对齐）
> **当前分支**：`Hy-agent` ｜ **包名**：`com.example.deviceinfoviewer`
> **版本线**：DeviceInfoViewer → v2.0.202（MVVM+Koin 3.5.6, compileSdk 35, Compose+Material3）→ 进行中 v3 重构（**2026-07-20 升级 AGP9/Kotlin2.2.10/BOM2025.06.00/compileSdk36**）
> ⚠️ 凡与记忆/旧文档冲突，**以本档 + 源码 `文件:行号` 为准**。本档已纠正若干过时假设（如 `values/colors.xml` 实际不存在）。

---

## 0. 技术栈速查（精确版本）

| 维度 | 事实（证据） |
|------|------|
| 语言/构建 | Kotlin **2.2.10**（AGP 9.0.1 内置 built-in Kotlin，无需显式 `kotlin-android` 插件）、`com.android.application` **9.0.1**（`build.gradle:1-5`）｜ Gradle **9.1.0**（wrapper） |
| UI | Compose BOM **2025.06.00**（≈Compose 1.7.x；受内置 Kotlin 2.2.10 编译器约束，2026.06.00 需 Kotlin 2.4.x=AGP 9.1+）、Material3、`material-icons-extended`、`ui-text-google-fonts`（`app/build.gradle:98-107`） |
| 架构 | MVVM + **Koin 3.5.6**（`app/build.gradle:96-97`）、lifecycle **2.8.4** |
| 三方库 | `sh.calvin.reorderable:reorderable:3.1.0`、`gson 2.10.1`、`kotlinx-coroutines-android:1.11.0`（`app/build.gradle`） |
| SDK | `compileSdk 36` / `minSdk 21` / `targetSdk 36`（已升，Android 16，满足 2026-08-31 Play 目标 API 截止）（`app/build.gradle:9,37-38`）；Java17 toolchain（`:62-69`） |
| 版本号 | `versionCode 404` / `versionName 4.0.404.0`（`app/build.gradle:39-40`） |
| 构建/混淆 | Release：`minifyEnabled true` + `shrinkResources true`；AGP9 要求 `getDefaultProguardFile('proguard-android-optimize.txt')`（已改）；`proguard-rules.pro` 仍 `-dontobfuscate` → **"缩而不混"**，App 自身代码整体 keep、仅第三方被缩减（`proguard-rules.pro:44-66`） |
| 签名 | **仅 CI 签名**：`KEYSTORE_BASE64` 环境变量解码到临时文件（`app/build.gradle:10-33`） |
| 模块 | **单模块 `app`**（`settings.gradle:16-17` 仅 `include ':app'`） |
| 测试 | CI **不跑测试**；`app/src/test` 仅 `BatteryDataSourceCurrentUnitTest`（电流单位），无 instrumentation 测试 |

---

## 1. 项目结构

```
app/src/main/java/com/example/deviceinfoviewer/
├── DeviceApplication.kt        # Application：Koin 引导 + 崩溃日志 + 异步启动诊断
├── MainActivity.kt             # 入口：9 Tab 的 HorizontalPager + 预测性返回 + 前后台生命周期
├── AppViewModel.kt             # 全局监控生命周期（start/stop/changeInterval/setGpsEnabled）
├── AppSettings.kt              # SharedPreferences 单例（所有持久化字段）
├── RefreshPolicy.kt            # 刷新策略唯一真相源（Tier + 省电模式 + 前后台）
├── FormatUtils / HapticUtils / LocaleManager.kt
├── di/AppModule.kt             # Koin module（1 single + 14 viewModel）
├── data/model/                 # 15+ 数据类（CpuInfo/GpuInfo/BatteryInfo/...）
├── data/source/                # 18 个 DataSource + SysFsReader + ShellCommandDataSource + CpuCache
├── data/repository/            # DeviceRepository / AuxiliaryCollector / HealthTracker / HistoryCache / PollingFlow
├── ui/                         # dashboard/cpu/gpu/memory/battery/network/gps/sensors/device/oem/settings/floatwindow
│   ├── components/  effects/  theme/   # 霓虹/玻璃拟态 UI 与光照特效
├── service/                    # FloatingWindowService / FloatingWindowConfig（前台悬浮窗）
└── util/                       # BackGestureCompat / ExportHelper / PermissionHelper
docs/  architecture/  *.md       # 既有架构/ADR/迁移/审查文档（含未落地的 5 病修复方案）
```

**模块职责表**

| 包/类 | 职责 | 关键依赖 |
|------|------|------|
| `ui/**` (Compose) | 各监控页 + 概览重排 + 特效渲染 | ViewModel（Koin）、`stringResource` |
| `AppViewModel` | 统一启停监控、前后台状态、GPS 智能开关 | `DeviceRepository` |
| `DeviceRepository` | **中枢**：持有全部 DataSource、调度轮询、产出 SharedFlow + LiveData、写历史/健康度 | 所有 DataSource、`PollingFlow`、`HealthTracker`、`HistoryCache`、`AuxiliaryCollector` |
| `AuxiliaryCollector` | 存储/WiFi/移动网络/网卡/GPS 采集（`async/awaitAll`） | 5 个独立 DataSource |
| `data/source/*` | 单领域数据采集（sysfs/shell/反射/HAL） | `SysFsReader`、`ShellCommandDataSource` |
| `CpuCache` | 芯片硬编码知识库（`object`，平台→规格查表） | 无（纯静态表） |
| `di/AppModule` | Koin 装配 | `DeviceRepository`、`ViewModels` |
| `AppSettings` | 设置持久化（单例） | `SharedPreferences` |
| `service/*` | 前台悬浮窗监控 | `DeviceRepository`（读实时值） |

---

## 2. 架构与数据流

### 2.1 MVVM 落地
- **ViewModel**：多为字段转发器（如 `DashboardViewModel` 仅 `val cpuInfo get() = repo.cpuLiveData`，`ui/dashboard/DashboardViewModel.kt:7-19`），本身无状态。
- **状态暴露**：UI 用 `observeAsState()` 订阅 `LiveData`（传统 `runtime-livedata` 桥接）；也有 `collectAsState` 用于 `StateFlow`。
- **Repository 边界**：`DeviceRepository` 是唯一对外出口，内部 `collectXxxBlock()` 才调具体 DataSource；屏幕**永远不直接碰 DataSource**。

### 2.2 双数据管道并存（技术债，见 §10）
Repository 同时维护 `SharedFlow`（`cpuFlow` 等，`replay=1, DROP_OLDEST`，`DeviceRepository.kt:56-66`）与 `@Deprecated` 的 `MutableLiveData`（`cpuLiveData`，`:70-76`）。**当前 UI 实际只用 LiveData 管道**，SharedFlow 暂未被消费。

### 2.3 DataSource 分层职责

| DataSource | 职责 | 读取方式 | 证据 |
|------|------|------|------|
| `BaseSysFsDataSource`（抽象） | `readSysFs()`（直接 JavaIO + `Runtime.exec cat` 兜底）、`readSysFsLong/Int/Float`、`probeFirstReadable` | sysfs + shell | `BaseSysFsDataSource.kt:17-86` |
| `SysFsReader`（object） | 通用 sysfs/proc 读取 + **反射**：`SystemProperties`、BatteryManager 隐藏属性、`HardwarePropertiesManager` | 直接 IO / 反射 | `SysFsReader.kt:19-194` |
| `ShellCommandDataSource`（object） | `dumpsys`/`logcat`/`cat`/`strings`/进程解析；`exec` 带 **8s 超时** | shell(ProcessBuilder) | `ShellCommandDataSource.kt:19,41-64` |
| `CpuCache`（object） | 芯片知识库查表 + 规格注入 | 内存 Map | `CpuCache.kt:14,57-322` |
| `CpuDataSource` | CPU 拓扑/频率/温度(5级fallback)/使用率/C-State | sysfs + 反射 + shell | `CpuDataSource.kt:30,61,188-298` |
| `GpuDataSource` | GPU 型号/频率(快速通道+10级fallback)/负载/温度/Vulkan | sysfs + devfreq + shell + dumpsys | `GpuDataSource.kt:24,85-119` |
| `BatteryDataSource` | 容量/电流/循环/健康/功率/双电芯/OCV | Intent + sysfs + 反射 + 多 HAL | `BatteryDataSource.kt:68-1650` |
| `DeviceDetailDataSource` | 机型/屏幕/SoC 制程/触控/Widevine/音频 | sysfs + 反射 + 属性 | `DeviceDetailDataSource.kt:293` |
| `SystemDataSource` | 系统/内核/版本/运行时 | sysfs 等 | `SystemDataSource.kt:12` |
| `OemDataSource` | 厂商 ROM 特性标记（充电/双电芯/快充/芯片） | 系统属性反射 | `OemDataSource.kt:130` |
| `MemoryDataSource` | RAM/Swap/ZRAM 用量 | sysfs/proc/meminfo | `MemoryDataSource.kt:10` |
| `SensorDataSource` | 传感器列表 + 实时监听 | SensorManager | `SensorDataSource.kt:75,111,154` |
| `GpsDataSource` | GPS 卫星状态 | GnssStatus 监听 | `GpsDataSource.kt:56,480` |
| `Storage/Wifi/MobileNetwork/NetworkInterface` | 各自领域 | 系统 API / sysfs | — |

### 2.4 文字版数据流
```
[Compose UI: observeAsState(repo.xxxLiveData) / collectAsState(repo.xxxFlow)]
        │ (订阅/重组)
        ▼
[ViewModel: 转发 repo.*LiveData 的 getter]
        ▼
[DeviceRepository.startMonitoring()]
   └─ PollingFlow.launchModulePolling(tag, intervalFlow, scope) { collectXxxBlock() }
        │  intervalFlow = MutableStateFlow，受 RefreshPolicy 观察者驱动（省电/前后台）
        ▼
[collectXxxBlock(): DataSource.getInfo()]
        │  try/catch → HealthTracker.mark(OK/ERROR, module)
        ▼
[DataSource] ──┬─ SysFsReader.readLine/readLong（直接 IO）
               ├─ BaseSysFsDataSource.readSysFs（IO 失败 → Runtime.exec cat 兜底）
               ├─ ShellCommandDataSource.exec(dumpsys/cat)（8s 超时）
               ├─ 反射：SystemProperties / BatteryManager 隐藏属性 / ServiceManager HAL
               └─ CpuCache.lookup + inject（规格补全）
        ▼
[系统接口：sysfs /proc、BatteryManager、Health HAL、SensorManager、GPS、系统属性]
        ▼ (回写)
[_cpuFlow.emit + cpuLiveData.postValue]   →  UI 刷新
[historyCache.addPoint(...)]               →  historyData 图表序列
[HealthTracker.mark(...)]                  →  sourceHealth 健康指示条
```

### 2.5 Koin 注入图
- 装配：`di/AppModule.kt`（唯一 module）；`DeviceApplication.onCreate()` → `startKoin { androidLogger(); androidContext(); modules(appModule) }`（`DeviceApplication.kt:57-61`）。
- 另 `DeviceApplication.deviceRepository` 经 `GlobalContext.get().get()` 提供全局访问器（`:41-43`），与 Koin `single` 并存 —— **两套取 DeviceRepository 的路径**。
- `single { DeviceRepository(androidContext()) }`（全局单例）；`viewModel { XxxViewModel(get()) }` ×14。
- `AppSettings.getInstance(ctx)` 是**手动单例（非 Koin）**，任意处直接调用。
- **无 scope/限定符**，所有 ViewModel 共享同一 `DeviceRepository` 单例；DataSource 全部由 `DeviceRepository` 内部 `new`，**不入 Koin**。

---

## 3. 关键逻辑速查（带行号）

### 3.1 CpuCache 芯片匹配（`CpuCache.kt`）
- **策略1** 精确 key：`KNOWN_CHIPS[raw]`（`:333`）
- **策略2** 精确 platformId：`KNOWN_CHIPS.values.firstOrNull { it.platformId == raw }`（`:336`）
- **策略3** 规范化后匹配：去前缀 `qcom,`/`qti `/`qualcomm `/`mediatek/`/`mt` → 再试 key/platformId/MTK 数值尾（`:339-354`）
- **策略4** codename 别名：`mapOf("sun" to "sm8635")`（`:359-364`），目前仅 1 条
- `injectCpuInfo`（`:369-420`）：仅当 `architecture` 为内核架构串（`aarch64`…）时重写为芯片名（修复原 `isEmpty()` 永远真问题）；L1/L2/L3/cores 仅空时注入；核心→簇按**频率差最小 + 15% 容差**（`:405-418`）。
- `injectGpuInfo`（`:422-428`）：model 空或含 `kgsl` 时填；`maxFreqKHz<=0` 用 `gpuClockMhz*1000`；`minFreqKHz`=20% 最大。
- **已知缺口**：仅 4 颗 MTK（`mt6989/6899/6897/6878`），无 `sm8250-ac`，无 `resolveKonaVariant()`。

### 3.2 BatteryDataSource（`:68-1650`）
- **容量计算**：`readBatteryCapacity` `:369-480`：`CAPACITY` → `CHARGE_COUNTER` → `charge_full` 多路径 → ChargeCounter 估算 → `charge_full_design` 兜底 → `power_profile.xml` 正则 → **SoC 典型值硬猜**（`sm8750/sm8650→5400`、`mt689/mt698/sm→5000`，`:467-477`）。
- **双电芯**（`:73`）：`info.dualCell = AppSettings...dualCellBattery` —— **纯手动开关**。电压/容量翻倍在 `BatteryInfo.effectiveVoltage/effectiveChargeFullMAh`（`BatteryInfo.kt:69-85`，`*2`），**假设串联双电芯**，并联不成立。
- **charge_full 单位**：循环内 `val mah = value / 1000`（`:417`）——**直接除 1000，无归一**（P0）。
- **状态/健康映射**：`chargeStatusToString:1612`、`healthToString:1620` 直接返回中文字面量写入 model（硬编码中文，P1）。
- **电流单位归一**：`convertCurrentToMicroamps` `:527-552` 基于 `CURRENT_PATH_REGISTRY` 的 `ASSUME_UA/ASSUME_MA/AUTO` + 阈值（15A/50mA），逻辑严谨，有单测。

### 3.3 PollingFlow（`:16-40`）
- 结构：`scope.launch { intervalFlow.flatMapLatest { flow { while(isActive){ 首帧 delay; fetcher(); remaining=delay 余量; delay(remaining) } } }.collect{} }`
- 调速：`intervalFlow` 由 `pushPolicyAdjustedIntervals()` 按 `RefreshPolicy` 改写（省电封顶 5s）。
- **取消/异常**：`:32` `try { fetcher() } catch (_: Throwable) {}` —— 吞掉所有异常含 `CancellationException`，取消依赖外层 `while(isActive)`（P1，反模式）。

### 3.4 AppSettings 持久化字段全集（`AppSettings.kt:29-93`）
`refreshIntervalMs`(2000)、`cpu/gpu/memory/batteryRefreshMs`(0=全局)、`darkMode`(true)、`hapticEnabled`(true)、`hapticIntensity`(2)、`dualCellBattery`(false)、`metricCardOrder`(默认 `cpu_temp,mem_usage,battery_level,gpu_load`)、`quickCardOrder`(默认 `cpu,gpu,mem,net,gps,device,battery,sensor`)、`dashboardReorderEnabled`(true)、`appLanguage`("system")。单例用 `@Volatile` + 双重检查锁（`:20-26`），`effectiveRefreshMs(moduleMs)` 提供 0→全局回退（`:50-51`）。

### 3.5 DashboardScreen 卡片重排（`:54-`）
- 库：`rememberReorderableLazyGridState(gridState){ from,to -> ... }`（`:277-281`），`ReorderableItem` 内 `draggableHandle(onDragStarted/Stopped=震动)`（`:295-298`）。
- `getItems` 以 **lambda** 传入（`:159,187`），每帧读最新顺序避免旧快照；`enabled=false` 回落普通 `LazyVerticalGrid`（`:303-314`）。
- 顺序解析 `resolveCardOrder`（`:255-260`）：保留已知 ID 原序 + 追加缺失 + 剔除未知，空则回落默认。
- 持久化：拖拽即写 `metricCardOrder/quickCardOrder`。
- ⚠️ `METRIC_CARD_IDS/QUICK_CARD_IDS` 默认值在 `AppSettings` 与 `DashboardScreen` **两处重复定义**（`:241-242`）。

---

## 4. 构建与 CI
- 单 `app` 模块；`compileSdk/target/min = 36/35/21`（compileSdk 升 36 受 AGP9 上限 36.1 约束；targetSdk 仍 35，计划 2026-08-31 前升 36）。
- Release：`minifyEnabled`+`shrinkResources`+`proguard-rules.pro`（`-dontobfuscate`）→ App 代码零缩减不混淆。
- 签名仅 CI：`KEYSTORE_BASE64` 解码临时 keystore，v1+v2。
- CI：`.github/workflows/android-build.yml` → JDK17+SDK36+接受许可 → 校验密钥 → `./gradlew assembleRelease` → 上传**签名 Release APK**（retention 30d）。**CI 不跑测试**（§10 P2#13）。
- Baseline Profile 仅 `baseline-prof.txt` 手写参考，未集成 macrobenchmark。

---

## 5. 设计系统与视觉风格

### 5.1 Token 定义位置（重要纠正）
- **全部颜色/辉光 Token 在 `ui/theme/Color.kt`（单文件）**，无 `res/values/colors.xml`（旧档假设"存在 colors.xml"已更正）。
- `ui/theme/Theme.kt`：`darkColorScheme` 映射 + `DeviceInfoViewerTheme()`，`dynamicColor` 强制关。
- `ui/theme/Type.kt`：Orbitron 字体族 + M3 Typography 全覆盖；**CJK 靠系统字体自动 fallback**（无显式 fallback chain）。
- **无自定义 Shape Token** —— `Theme.kt` 未设 `shapes`，全部沿用 M3 默认圆角（卡片统一 `RoundedCornerShape(12.dp)`，见 `InfoCard.kt:48`）。

### 5.2 Token 清单（`Color.kt`）
| Token | 取值 | 用途 |
|------|------|------|
| `CyberBackground` | `#0A0A0F` | 深黑底（`background`） |
| `CyberCardStart`/`CyberCardEnd` | `#171417`/`#451B45` | 卡片线性渐变起止 |
| `CyberMuted` | `#27273B` | 图标底/次级表面 |
| `CyberPill` | `#1E1C35` | 药丸/浮层（`surface`） |
| `CyberElevated` | `#18182A` | 弹窗表面 |
| `NeonPurple` | `#7C3AED` | 主霓虹紫（`primary`） |
| `NeonPurpleBright` | `#A78BFA` | 高亮紫（数值） |
| `NeonPurpleDeep` | `#4C1D95` | 深紫边框/分割线 |
| `NeonSteelBlue` | `#3D70B8` | 钢蓝（未选中） |
| `NeonCyan`/`NeonMagenta` | `#00D4FF`/`#F43F5E` | 图表/强调 |
| `PurpleGlow`/`PurpleGlowLight`/`PurpleGlowStrong` | `7C3AED` + α | 辉光阴影 |
| `SuccessNeon`/`WarningNeon`/`ErrorNeon` | `#34C759`/`#FFAB00`/`#FF1744` | 功能色/状态点 |
| `TextPrimary`/`TextSecondary`/`TextValue` | `#E2E8F0`/`#94A3B8`/`NeonPurpleBright` | 文字层级 |

### 5.3 视觉构建手法
- **渐变**：卡片 `Brush.linearGradient(CyberCardStart, CyberCardEnd)`（`InfoCard.kt:34`）；头部/分割线 `horizontalGradient`；按钮 `radialGradient`（`GlowBackButton.kt:161`）；图表面积 `verticalGradient`（`LineChart.kt:92`）。
- **光晕（Glow）**：
  - `revealLight()`（`RevealLightModifier.kt:45`）：指针跟随径向光斑，API33+ 走 AGSL `RuntimeShader`、否则 Canvas 降级；由 `GlobalLightProvider`（`GlobalLightProvider.kt:53`）+ `GlobalLightState`（`GlobalLightState.kt:32`，Spring `DampingRatioMediumBouncy`）+ `LocalLightState` 驱动。
  - `neonBorderGlow()`（`NeonHeaderDecoration.kt:62`）：渐变描边 + 紫色外发光 `shadow`。
  - `hdrHighlight()`（`InfoCard.kt:118`）：白色 0.18α 细高光描边。
- **玻璃拟态（Glassmorphism）**：`acrylic()`（`AcrylicModifier.kt:65`），多层（tint 半透明渐变 + `acrylicNoise` 噪点 + 渐变边框）；API33+ 有 AGSL `acrylicAGSL` 但 `@Suppress("unused")` 默认未启用（`:291`）。用于设置/悬浮窗/传感器详情覆盖层。
- **字体**：Orbitron（仅拉丁/数字），数值用 `FontFamily.Monospace`。

---

## 6. 代码约定与开发者偏好

### 6.1 工程约定（实测）
| 维度 | 结论 |
|------|------|
| Kotlin 风格 | 标准惯用法；`object` 工具类（`FormatUtils`/`HapticUtils`）；属性委托式 `AppSettings`。 |
| 命名 | 类名 PascalCase；组件 `InfoCard`/`MetricCard`/`LineChart`；`private` 助手 `resolveCardOrder`；常量 `UA_SANITY_LOW`（`BatteryDataSource.kt:521`）；语义 key 用 snake（`charger_ac`/`ps_usb`）。 |
| 注释 | **普遍中文注释**，极详尽，常带「★ 修复/性能优化/为什么」+ 日期（如 `MainActivity.kt:95`）。无统一 KDoc 模板，多为 `/* */` 区块注释。 |
| 错误处理 | **零空 `catch {}`**（Grep `catch (_: Throwable) {}` 无匹配）。统一 `try/catch (e: Throwable) { Log.w(TAG, "…采集失败", e) }`；或 `runCatching{}.onFailure{ Log.w }`。AGSL 降级用 `catch (_: Exception) { /* 静默降级 */ }`。 |
| 日志 | `Log.w/d/e`，Tag 为类名缩写（`DeviceRepo`/`MainActivity`/`HapticUtils`/`AuxCollector`/`AppVM`）；中文日志文案。 |
| 可空性 | 全仓仅 **1 处 `!!`**（`DashboardScreen.kt:113` `memoryInfo!!.usedKB`），其余 `?.`/`runCatching`/安全回退 —— 空安全纪律极好。 |
| 工具类 | `FormatUtils`（`String.format(Locale.US,…)` 防 locale 串味）、`ChartUtils`（`normalizeChartData` 等全局共享，消 6 份重复）、`HapticUtils`（`lightTap/standardTap/heavyTap/dragStart/dragEnd/stepTick`）。 |
| 性能洁癖 | 大量「零分配/去重组」注释：FloatArray 替代 List（`LineChart.kt:100`）、`drawWithCache` 缓存噪点（`AcrylicModifier.kt:159`）、去嵌套 `AnimatedContent`（`MainActivity.kt:551`）。 |
| 防御性探测 | 多路径 sysfs 回退（Battery 50+ 路径）；反射调隐藏 API 防 dex 验证崩溃（v3 已用反射修 API33+/28+ OEM ROM ART dex 验证闪退）。 |

### 6.2 反推的开发者偏好（工程取向）
1. **视觉主权**：暗色霓虹 + Orbitron + 紫渐变 + 指针光晕 + 玻璃拟态是「产品指纹」，愿为效果写自定义 Modifier（甚至 AGSL）。
2. **中文优先**：中文注释 + 中文 UI 是默认习惯；i18n 框架搭好，但**数据层偷懒直接塞中文**，zh 实际是一等公民、en 反成 fallback —— 半吊子国际化。
3. **硬编码知识库 > 运行时探测**：SoC 制程表、`CpuCache`、OEM 特性映射全是静态查表，注释极详（来源/代工厂交叉验证）。
4. **轻量持久化**：SharedPreferences 单例 + 属性委托，拒绝重型方案。
5. **防御性 + 性能偏执**：多路径回退、反射防崩、`runCatching`/`Log.w` 吞异常（无空 catch）、零分配绘制、显式去重组。
6. **Koin DI**，ViewModel 按屏一一对应；repository 保留 `@Deprecated LiveData` 兼容层（迁移进行中）。
7. **痕迹管理不佳**：`MainActivity` 留 bisection 调试函数（`SystemMonitorAppMinimal`/`SystemMonitorAppNoFx`，`:156/:171`）与大量行内「★ 修复 2026-06-xx」史 —— 可维护性负债。

---

## 7. 导航与页面映射（`MainActivity.kt`）
`MainActivity` → `SystemMonitorApp`(`:222`) → `MainTabs`(`:458`) → `HorizontalPager`(`:554`)，`when(page)` 索引：

| page | 模块 | 入口 |
|------|------|------|
| 0 | Dashboard | `DashboardScreen(onNavigate=)` |
| 1 | CPU | `CpuScreen()` |
| 2 | GPU | `GpuScreen()` |
| 3 | Memory | `MemoryScreen()` |
| 4 | Battery | `BatteryScreen()` |
| 5 | Network | `NetworkScreen()` |
| 6 | GPS | `GpsScreen()` |
| 7 | Sensors | `SensorsScreen(onNavigateToSensor=)` |
| 8 | Device | `DeviceScreen()` |

- 跳转：概览 `QuickLinkCard` 经 `QUICK_NAV` map（`DashboardScreen.kt:243-246`）跳目标页；传感器 → `onOpenSensorDetail` 打开 `SensorDetailScreen` 覆盖层。
- 覆盖层：设置/悬浮窗/传感器详情经 `showXxx` + `acrylic()` + `PredictiveBackHandler`(`:301`)；Tab 与覆盖层 `BackHandler` 互斥（`:480`）。
- ⚠️ 调试残留：`MainActivity.kt` 含 bisection 调试函数，建议清理。

---

## 8. i18n 现状（重点，已纠正旧审计的低估）

### 8.1 基础设施（健康）
- 三套资源：`values/strings.xml`(en, 1219 键)、`values-zh-rCN/strings.xml`(1211 键)、`values-zh-rTW/strings.xml`；`locales_config.xml` 声明 `zh-CN/en/zh-TW`。
- `LocaleManager`（per-app language，API33+ 委托系统，<33 `wrapContext`+`AppCompatDelegate`）+ `AppSettings.appLanguage`。
- **正确模式已存在**：charger/power-source 用语义 key → `stringResource`（`BatteryScreen.kt:94-97,239-243`），`battery_status_charging`/`oem_rear_label` 等键在 zh-rCN 已定义。
- **键缺口**：en(1219) > zh-rCN(1211)，存在 **8 键未对齐**。

### 8.2 核心矛盾
`en/strings.xml` 无中文；`zh-rCN` 翻译键齐备，但多 DataSource 把**同样中文硬编码进 Kotlin 字面量**并直写 model 字段、UI 原样显示，绕过资源系统。

### 8.3 硬编码中文清单（按文件 + 行号 + 是否用户可见）
> 可见性：model 字段/标签/比较串 = 用户可见；`Log.*` 中文 = 仅日志。

| 文件:行 | 中文 | 可见？ | 备注 |
|------|------|------|------|
| `BatteryDataSource.kt:1613-1617` | 充电中/放电中/已充满/未充电/未知 | ✅ | `getStatusLabel`→`chargeStatus` |
| `BatteryDataSource.kt:1621-1627` | 良好/过热/损坏/过压/故障/过冷/未知 | ✅ | `getHealthLabel`→`health` |
| `BatteryDataSource.kt:1492` | 底座 | ✅ | chargerType |
| `BatteryDataSource.kt:733/1436` | 无法获取 | ✅ | 兜底，且 `BatteryScreen:83/86` 用 `!= "无法获取"` 比较 |
| `BatteryDataSource.kt:434/476` | Charge Counter估算 / SoC 典型值推断 | ✅ | chargeFullSource |
| `BatteryDataSource.kt:1169` | charge_counter推算 | ✅ | cycle 来源 |
| `BatteryDataSource.kt:1241-1244` | 通用 charge_counter/… | ✅(调试) | 属性描述 |
| `BatteryViewModel.kt:28` | 充电中/未充电 | ✅ | `formatChargingStatus` 重复硬编码 |
| `DeviceDetailDataSource.kt:1273-1275` | 后置/前置/闪光灯 | ✅ | cameraIds |
| `DeviceDetailDataSource.kt:1297-1299` | 后置/前置/外置 | ✅ | cameraSensors.facing |
| `DeviceDetailDataSource.kt:1056` | 标准 $standard | ✅ | Wi-Fi 标准 |
| `DeviceDetailDataSource.kt:1572` | 需 READ_PHONE_STATE 权限 | ✅ | 序列号兜底 |
| **`OemDataSource.kt:241-1027`（约 80+ 处）** | 澎湃 C2 ISP / 小爱AI / HyperConnect / Redmi 狂暴引擎 / `${wireless}W 无线` / 内存融合已启用 / 已验证(green) / 均衡模式 … | ✅ | **最大来源**，旧审计完全漏掉 |
| `CpuDataSource.kt:271/293/297/654/672/685` | 温度传感器/电池温度(降级)/无法获取/cpuidle 不可用 | ✅ | 中量 |
| `CpuCache.kt:232/256/379/382` | (全大核)/(大核)·(小核) | ✅ | 芯片库展示 |
| `GpuDataSource.kt:725/782/818/834/898` | 缓存 / OpenGL ES 推断 / 检测失败 / 集成·独立 | ✅ | 小量 |
| `ShellCommandDataSource.kt:305/306` | 省电模式 / 正常模式 | ✅ | 小量 |
| `OemScreen.kt:103-106` | `== "性能模式"/"高性能模式"/"省电模式"/"超级省电模式"` | ✅(代码) | **反模式**：硬编码中文做比较（`MemoryDistributionCard.kt:178` 已修同类，此处未修） |
| `BatteryScreen.kt:168` | `"Battery health"` | ✅ | 英文字面量未 `stringResource`（英文 UI 标签也未本地化） |

> 旧审计称 "Battery 残留 8 处中文" 严重低估 —— 真实是 **横跨 5+ 文件、OEM 单文件约 80+ 处**。修复应按"语义 key → stringResource"统一抽取，参照已有的 `charger_ac`/`oem_rear_label` 模式。

---

## 9. 通用 UI 组件职责表
| 组件 | 位置 | 职责 |
|------|------|------|
| `InfoCard` | `InfoCard.kt:40` | 图标+标题+副标题卡 + `revealLight` + `PurpleGlow` + `hdrHighlight` |
| `MetricCard` | `InfoCard.kt:71` | 指标卡（标题/数值/进度条/图表槽），数值 `Monospace` |
| `QuickLinkCard` | `DashboardScreen.kt:207` | **`private`**，概览快捷入口（非共享） |
| `LineChart`/`DualLineChart` | `LineChart.kt:65/175` | 贝塞尔平滑折线+面积渐变+入场动画 |
| `NeonHeaderDecoration` | `NeonHeaderDecoration.kt:27` | 顶部药丸头部渐变光晕（零重组） |
| `NeonDivider` | `:91` | 霓虹水平分割线 |
| `GlowBackButton` | `GlowBackButton.kt:52` | 暗玻璃返回键（弹簧/涟漪） |
| `PulseDot` | `PulseDot.kt:18` | 实时监控状态灯（infinite 脉冲） |
| `SatelliteSkyView` | `SatelliteSkyView.kt` | GPS 卫星天图 |
| `MemoryDistributionCard` | `MemoryDistributionCard.kt` | 内存分布环形/条形卡 |
| `acrylic()` / `revealLight()` / `GlobalLightProvider` | `AcrylicModifier.kt`/`RevealLightModifier.kt` | 玻璃拟态 / 指针光晕 / 全局光照根 |

---

## 10. 技术债清单（P0/P1/P2， consolidated）

### P0（致命 / 整类机型或核心指标错误）
1. **charge_full 单位未归一**（`BatteryDataSource.kt:417` `val mah = value/1000`，无 `normalizeChargeFull()`）→ 部分机型容量差 1000 倍。
   - ✅ **已修复**（`73435ed`）：落地 `normalizeChargeFull()`/`normalizeToMAh` 启发式单位检测（1500~12000mAh 目标区间反推 µAh/mAh/Ah×1000），并扩充 MTK/三星/vivo BMS 路径。详见 `docs/diagnosis_5_issues_fix.md` 方案 2。
2. **MTK 覆盖不全 + 制程号错配**：`CpuCache` 仅 4 颗 MTK；`DeviceDetailDataSource.SOC_PROCESS_MAP` 用**营销号**键（`MT9200`…）而 `ro.soc.model` 返回**硅片号**（`MT6983`）→ 9200/9000 制程掉到"不可用"。
   - ✅ **已修复**（本次提交）：`CpuCache.KNOWN_CHIPS` 增补 6 颗天玑规格条目（`mt6983/6985/6896/6886/6879/6893`，CPU 架构/缓存/GPU 全规格）→ `collectSocProcess` 策略0（`CpuCache.lookup(ro.board.platform)`）直接命中返回制程；`SOC_PROCESS_MAP` 补硅片号键 `MT6983/MT6896/MT6886`（与既有 `MT6985/MT6893/MT6879/MT6897` 并存）→ 策略1（`ro.soc.model`）精确命中。9200/9000/8200/7200 制程节点不再掉"不可用"。MTK 温度路径增强（方案 1.2.2）留作后续优化。

### P1（重要 / 特定机型或健壮性）
3. **kona 865/870 不可区分**（`CpuCache.kt:60` 仅 `sm8250→865`，无 `sm8250-ac`/无 `resolveKonaVariant`）。
   - ✅ **已修复**（本次提交）：`CpuCache` 增补 `sm8250-ac`（870）规格条目 + `resolveKonaVariant()` 四级判定（ro.soc.model 含 -ac / ro.soc.id 341(870)·356(865) / Prime 频率 >3.04GHz / chipname 含 870·ac）；`collectSocProcess` 策略0 在 `ro.board.platform=="kona"` 时按 variant 选 `sm8250`/`sm8250-ac`，870 不再误判为 865。
4. **`autoDetectDualCell()` 未实现**：`BatteryDataSource.kt:73` 仅读手动开关；`OemDataSource.kt:637/662/689` 已采 `chargingDualCell` 但**从未回灌** `BatteryInfo.dualCell`（数据孤岛）。
   - ⬜ 未修复。落地方案 `diagnosis_5_issues_fix.md` 方案 3（五级 fallback 自动检测 + OEM 回灌）。
5. **13 处 `waitFor()` 无超时**（实测，非旧审计的 7 处）：`GpuDataSource:803`、`SystemDataSource:108`、`DeviceDetailDataSource:1652`、`BatteryDataSource:688/719/945/1052/1287/1312/1369/1396/1593`、`BaseSysFsDataSource:28`。可能线程饥饿。`ShellCommandDataSource.exec` 有 8s 超时但上述未用。
   - ✅ **已修复**（`73435ed`）：13 处 `waitFor()` 全部加超时（复用 `ShellCommandDataSource` 8s 超时机制），消除线程饥饿风险。
6. **`PollingFlow.kt:32` 吞 `CancellationException`**（`catch(_: Throwable)`）→ 取消信号脆弱（被 `while(isActive)` 部分兜底）。
   - ✅ **已修复**（`73435ed`）：改 `catch(e: Throwable) { if (e is CancellationException) throw e; ... }`，取消信号正确上抛。
7. **电池状态/健康硬编码中文**（`BatteryDataSource.kt:1612-1628`）+ 全仓 i18n 债（见 §8，OEM ~80+ 处最大）。
   - 🔶 **部分修复**：`BatteryScreen` 状态/健康已改 `stringResource`（`battery_status_*`/`battery_health_*`）；但 `BatteryDataSource.kt` 内部 `chargeStatusToString`/`healthToString` 及 **OEM 约 80+ 处硬编码中文仍未抽取**（§8.3 最大来源）。落地方案 `diagnosis_5_issues_fix.md` 方案 5（枚举 + `stringResource`）。

### P2（一般 / 可维护性）
8. 双数据管道（SharedFlow 闲置 + Deprecated LiveData 仍主用）。
9. 反射未缓存（`SysFsReader.readProp` 每 tick `Class.forName`）。
10. 全仓 `catch(_: Throwable)` 过度吞异常（叠加 PollingFlow 问题）。
11. 死代码（未用回退 Composable、`METRIC_CARD_IDS` 默认值两处重复定义、bisection 调试函数）。
12. God-class（`BatteryDataSource` ~1650 行、`DeviceDetailDataSource`、`OemDataSource`）。
13. CI 不跑测试；ProGuard 整体 keep 致 App 零缩减。
14. 双电芯 ×2 仅适配串联拓扑。
15. Magic number 散布（0.15 容差、50mA/10mA 阈值、20A 上限、1000 换算）。
16. `OemScreen.kt:103-106` 用硬编码中文做比较串（反模式，团队已在 `MemoryDistributionCard.kt:178` 修过同类）。

> `docs/diagnosis_5_issues_fix.md` 是**待落地方案**。状态对账（截至 2026-07-20 工具链升级后）：
> - ✅ 已修复：`73435ed` 落地 P0#1（charge_full 归一）、P1#5（13× waitFor 超时）、P1#6（PollingFlow 取消信号）。
> - 🔶 部分修复：P1#7（BatteryScreen i18n 完成，OEM 80+ 处未动）。
> - ⬜ 待落地：`diagnosis_5_issues_fix.md` 方案 3/4/5 覆盖的 P1#3（kona 865/870）、P1#4（autoDetectDualCell）、P1#7（OEM i18n）仍**未合入源码**；P0#2（天玑规格 + 制程）已本次提交修复。需逐文件读码后单独 CI 验证提交。

---

## 11. 工作备忘（防片断 · 后续会话先读这段）

**入口与关键文件**
- 启动：`DeviceApplication.kt` → `MainActivity.kt`（9 Tab `HorizontalPager`）
- 数据中枢：`data/repository/DeviceRepository.kt`（轮询/SharedFlow/LiveData/健康度）
- 持久化：`AppSettings.kt`（所有设置；新增设置字段改这里 + 属性委托）
- 芯片库：`data/source/CpuCache.kt`（加芯片改这里 + `lookup` 策略）
- 概览重排：`ui/dashboard/DashboardScreen.kt`（`ReorderableCardGrid` + `resolveCardOrder`）
- 主题/Token：`ui/theme/Color.kt`（**唯一颜色真理源**，改色只动这里）、`Theme.kt`、`Type.kt`
- 光照特效：`ui/effects/`（revealLight / GlobalLightProvider / acrylic）

**不变式（Invariants，改代码前必读）**
- UI 永远不直接调 DataSource，必须经 `DeviceRepository`。
- 拖拽手柄 `draggableHandle` 是 `ReorderableCollectionItemScope` 成员，**只能在 `ReorderableItem { }` 作用域内计算后传出**，不可顶层调用（曾因误用导致 CI 失败，已修）。
- 颜色统一走 `Color.kt` Token，禁止散落色值；圆角统一 `12.dp`（无自定义 Shape Token）。
- 错误处理用 `try/catch(Throwable){Log.w}` 或 `runCatching`，**不要写空 `catch {}`**。
- 空安全纪律极好：新增代码避免 `!!`，用 `?.`/安全回退。
- 中文 UI 文案理论上应走 `stringResource`，但**现状大量硬编码于 DataSource** —— 改显示文案前先确认它是字面量还是资源键，避免重复修复。

**近期变更（截至 2026-07-21）**
- **工具链升级（§13 第一阶段，CI `29722575790` ✅）**：AGP `8.6.0→9.0.1`、Gradle `8.7→9.1.0`、Kotlin `2.1.0→2.2.10`（AGP9 内置 built-in Kotlin，移除显式 `kotlin-android` 插件）、Compose BOM `2024.12.01→2025.06.00`、`kotlinx-coroutines-android 1.11.0`、compileSdk `35→36`；`org.jetbrains.kotlin.plugin.compose` 锁 2.2.10，CI SDK 升 `android-36`。提交 `2b36f35 / cb53e15 / 8f65e50 / b8a6407`。
  - ⚠️ **约束修正**：AGP 9.0.1 内置 Kotlin 锁定 2.2.10，硬禁显式 `kotlin-android` 插件；BOM 2026.06.00（Compose 1.12.x）需 Kotlin 2.4.x = **AGP 9.1+**，故本阶段止步 2025.06.00。Kotlin 2.4.10 / BOM 2026.06.00 留待 AGP 9.1+ 阶段。
- **已修复（`73435ed`，属 §10 收口）**：P0#1（charge_full 归一）、P1#5（13× waitFor 超时）、P1#6（PollingFlow 取消信号）。
- **P0#2 修复（天玑规格 + 制程，CI `29734507239` ✅）**：`CpuCache.KNOWN_CHIPS` 增补 6 颗天玑规格（`mt6983/6985/6896/6886/6879/6893`）；`SOC_PROCESS_MAP` 补硅片号键 `MT6983/MT6896/MT6886`。9200/9000/8200/7200 制程节点不再掉"不可用"。
- **P1#3 修复（kona 865/870，CI `29734939802` ✅）**：`CpuCache` 增补 `sm8250-ac`（870）规格 + `resolveKonaVariant()` 四级判定；`collectSocProcess` 策略0 在 `kona` 平台按 variant 选 `sm8250`/`sm8250-ac`。提交 `7d29407`。
- **store-blocking：targetSdk `35→36`（Android 16，本次提交）**：仅改 `app/build.gradle` `targetSdk 35→36`（compileSdk 已 36）。代码核查确认全仓**未使用** `BODY_SENSORS` / `android.permission.health.*`，故 android-developers 文档所述的「健康权限迁移」硬阻断**不适用**，无需改动 manifest。Android 16 其余行为变更（边缘到边缘/预测返回/`FOREGROUND_SERVICE_SPECIAL_USE`/本地网络 opt-in）对本应用均无硬阻断。满足 2026-08-31 Play 目标 API 截止。
- 概览页 2×2 指标卡 + 快速访问 均支持拖拽重排（reorderable 3.1.0 + AppSettings 持久化）；提交 `a5d797e`（CI `29672704776` ✅）。
- 快速访问新增电池(4)/传感器(7) 按钮（`1f912fb`）。
- 内存卡新增 SWAP/ZRAM 子区域（`ca4c33a`）。
- 全面代码审查完成，产出本档案 + `deliverables/gstack/code-health-audit-dialectical-2026-07-19.md`（辩证审计，纠正 waitFor 7→13、Battery 中文 8→22+ 等偏差）。

**优先修复顺序建议（剩余项）**：
1. ✅ **store-blocking（targetSdk 36）已完成**（本次提交）：targetSdk `35→36`（Android 16，满足 2026-08-31 Play 目标 API 截止）。健康权限迁移经代码核查为**误判** —— 全仓未声明/使用 `BODY_SENSORS` 或 `android.permission.health.*`（仅 `HealthTracker` 模块健康度追踪与 `BatteryInfo.health` 电池健康，均无关），故**无需迁移**。Android 16 行为变更（边缘到边缘/预测返回/前台服务 specialUse/本地网络 opt-in）对本应用均无硬阻断。
2. **P1#4**：`autoDetectDualCell()` + OEM 回灌（方案 3）。
3. **P1#7**：OEM ~80+ 处硬编码中文 → `stringResource`（`diagnosis` 方案 5）。
4. **P2**：双管道 / 反射缓存 / 死代码 / God-class / CI 测试 / ProGuard 缩减 / 双电芯拓扑 / magic number / OEM 比较串。

---

> 本档案由软件工坊 AI 协作生成（主理人汇编两位成员实地审查结论）。关键决策仍由你（工程负责人）复核。
