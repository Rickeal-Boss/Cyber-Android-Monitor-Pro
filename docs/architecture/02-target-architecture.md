# Device Info Viewer — 目标架构设计

> **架构模式**: MVVM + UseCase + Repository
> **DI 框架**: Koin
> **UI**: View-based (XML + Fragment)，非 Compose

---

## 1. C4 Level 1 — 系统上下文

```
┌──────────────────────────────────────────────────────────────┐
│  Device Info Viewer                                          │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  5 Tab Monitor (CPU / GPU / Memory / Battery / Network) │  │
│  │  + Dashboard + Hardware + System                        │  │
│  │  + Floating Window Monitor                              │  │
│  └───────────────────────────┬────────────────────────────┘  │
│                              │                                │
│         ┌────────────────────┼────────────────────┐          │
│         ▼                    ▼                     ▼          │
│  ┌───────────┐   ┌──────────────────┐   ┌──────────────┐    │
│  │  /proc/sys │   │  Android API     │   │  Shell CMD   │    │
│  │  文件系统   │   │  (BatteryManager,│   │  (dumpsys,   │    │
│  │            │   │   WifiManager...) │   │   logcat...)  │    │
│  └───────────┘   └──────────────────┘   └──────────────┘    │
└──────────────────────────────────────────────────────────────┘
```

## 2. C4 Level 2 — 容器（分层结构）

```
┌─────────────────────────────────────────────────────────────────────┐
│                          UI Layer                                    │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌─────────┐  │
│  │Dashboard │ │CPU Fgmt  │ │ GPU Fgmt │ │ Mem Fgmt │ │ Bat Fgmt│  │
│  │ Fragment │ │          │ │          │ │          │ │         │  │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬────┘  │
│       │            │            │            │            │         │
│  ┌────▼────────────▼────────────▼────────────▼────────────▼─────┐  │
│  │                        ViewModels                             │  │
│  │  DashboardVM  CpuVM  GpuVM  MemoryVM  BatteryVM  NetworkVM   │  │
│  │  HardwareVM   SystemVM                                        │  │
│  └───────────────────────────┬──────────────────────────────────┘  │
├──────────────────────────────┼──────────────────────────────────────┤
│                     Domain Layer (Use Cases)                         │
│  ┌───────────────────────────┼──────────────────────────────────┐  │
│  │  MonitorCpuUseCase        │  MonitorBatteryUseCase           │  │
│  │  MonitorMemoryUseCase     │  MonitorNetworkUseCase           │  │
│  │  GetSystemInfoUseCase     │  GetHardwareInfoUseCase          │  │
│  └───────────────────────────┼──────────────────────────────────┘  │
├──────────────────────────────┼──────────────────────────────────────┤
│                        Data Layer                                   │
│  ┌───────────────────────────┼──────────────────────────────────┐  │
│  │     Repository (按领域拆分)                                    │  │
│  │  CpuRepo  GpuRepo  BatteryRepo  MemoryRepo  StorageRepo      │  │
│  │  WifiRepo  NetworkRepo  GpsRepo  SensorRepo  SystemRepo      │  │
│  │                                                               │  │
│  │     HistoryCache (统一时间序列)                                │  │
│  └───────────────────────────┼──────────────────────────────────┘  │
│  ┌───────────────────────────┼──────────────────────────────────┐  │
│  │  DataSource (12 个，保持不变)                                  │  │
│  │  CpuDS  GpuDS  BatteryDS  MemoryDS  StorageDS                │  │
│  │  WifiDS  MobileDS  NetIfDS  GpsDS  SensorDS  SystemDS  SysFs │  │
│  └──────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
                          │
    ┌─────────────────────┼─────────────────────┐
    ▼                     ▼                      ▼
 /proc/sys           Android API           Shell (dumpsys)
```

## 3. C4 Level 3 — 组件图（包结构）

```
com.example.deviceinfoviewer/
│
├── DeviceApplication.kt          # Application + Koin 初始化
├── MainActivity.kt               # 单 Activity
├── TabPagerAdapter.kt
├── AppSettings.kt
├── FormatUtils.kt
│
├── di/                           # 🆕 依赖注入
│   └── AppModule.kt              # Koin 模块定义
│
├── domain/                       # 🆕 Domain Layer
│   ├── model/                    # 领域模型（UI 展示用的轻量对象）
│   │   ├── CpuUiModel.kt
│   │   ├── BatteryUiModel.kt
│   │   └── ...（8 个）
│   └── usecase/
│       ├── MonitorCpuUseCase.kt
│       ├── MonitorBatteryUseCase.kt
│       ├── MonitorMemoryUseCase.kt
│       ├── GetSystemInfoUseCase.kt
│       ├── GetHardwareInfoUseCase.kt
│       └── ...（10 个）
│
├── data/                         # Data Layer（重构后）
│   ├── model/                    # 数据模型（不变）
│   │   ├── CpuInfo.kt
│   │   ├── BatteryInfo.kt
│   │   └── ...（14 个）
│   ├── source/                   # DataSource（不变）
│   │   ├── CpuDataSource.kt
│   │   ├── GpuDataSource.kt
│   │   └── ...（12 个）
│   └── repository/
│       ├── CpuRepository.kt      # 🆕 拆分后的 Repository
│       ├── GpuRepository.kt
│       ├── BatteryRepository.kt
│       ├── MemoryRepository.kt
│       ├── StorageRepository.kt
│       ├── NetworkRepository.kt  # 合并 WiFi + Mobile + NetIF
│       ├── GpsRepository.kt
│       ├── SensorRepository.kt
│       ├── SystemRepository.kt
│       └── HistoryCache.kt       # 保留，改为可观察
│
├── ui/                           # 🆕 UI Layer（重组）
│   ├── dashboard/
│   │   ├── DashboardFragment.kt
│   │   └── DashboardViewModel.kt
│   ├── cpu/
│   │   ├── CpuFragment.kt
│   │   └── CpuViewModel.kt
│   ├── gpu/
│   │   ├── GpuFragment.kt
│   │   └── GpuViewModel.kt
│   ├── memory/
│   │   ├── MemoryFragment.kt
│   │   └── MemoryViewModel.kt
│   ├── battery/
│   │   ├── BatteryFragment.kt
│   │   └── BatteryViewModel.kt
│   ├── network/
│   │   ├── NetworkFragment.kt
│   │   └── NetworkViewModel.kt
│   ├── hardware/
│   │   ├── HardwareFragment.kt
│   │   └── HardwareViewModel.kt
│   └── system/
│       ├── SystemFragment.kt
│       └── SystemViewModel.kt
│       │
│       └── common/               # 共享组件
│           ├── BaseMonitorFragment.kt   # 重构后的基类
│           └── UiState.kt               # 通用 sealed state
│
├── widget/                       # 自定义 View（不变）
│   ├── MonitorChartView.kt
│   ├── HistoryChartView.kt
│   └── CpuBarChartView.kt
│
├── adapter/                      # RecyclerView Adapter（不变）
│   ├── SensorListAdapter.kt
│   └── NetworkInterfaceAdapter.kt
│
├── service/                      # 后台服务（不变）
│   ├── DeviceMonitorService.kt
│   └── FloatingWindowService.kt
│
└── util/                         # 工具类
    ├── ExportHelper.kt
    ├── PermissionHelper.kt
    └── SafeViewPagerBinder.kt    # 🆕 封装 TabLayout ↔ ViewPager2 绑定
```

## 4. 关键交互时序 — 以 CPU Tab 为例

```
User          Fragment        ViewModel       UseCase          Repository      DataSource
 │               │                │               │                │               │
 │  打开 CPU Tab  │                │               │                │               │
 │──────────────>│                │               │                │               │
 │               │  onCreateView  │               │                │               │
 │               │───────┬───────>│               │                │               │
 │               │       │ observe(uiState)      │                │               │
 │               │<──────┘       │               │                │               │
 │               │               │               │                │               │
 │               │               │ startMonitoring()               │               │
 │               │               │──────────────>│                │               │
 │               │               │               │ collectData()  │               │
 │               │               │               │───────────────>│               │
 │               │               │               │                │ getCpuInfo()  │
 │               │               │               │                │──────────────>│
 │               │               │               │                │  CpuInfo      │
 │               │               │               │                │<──────────────│
 │               │               │               │  CPU 数据        │               │
 │               │               │               │<───────────────│               │
 │               │               │  UiState.Success(data)          │               │
 │               │               │<──────────────│                │               │
 │               │               │               │                │               │
 │               │  uiState changed               │                │               │
 │               │<──────────────│               │                │               │
 │               │  render(CPU data)               │                │               │
 │               │               │               │                │               │
 │  ┌─────────── │               │               │                │               │
 │  │  显示 CPU   │               │               │                │               │
 │  └─────────── │               │               │                │               │
```

**关键改进**：
1. 图表不再由 Handler/Runnable 轮询 → LiveData 驱动自动更新
2. Fragment 不再直接依赖 Repository → 全部通过 ViewModel
3. UseCase 封装 2s 采集循环逻辑 → Repository 变为纯数据提供者
4. 每个采集周期只修改 ViewModel State，View 自动响应

## 5. 数据流对比

### 现状（❌）
```
Fragment ←→ DeviceRepository (God)
    ↓ Handler/Runnable(3000ms) 轮询图表
    ↓ 直接 .observe(repo.xxxLiveData)
    ↓ repo!! 强制非空
```

### 目标（✅）
```
Fragment → ViewModel (by viewModel())
    ↓ observe(viewModel.cpuUiState) — 单向数据流
    ↓ observe(viewModel.chartData)   — LiveData 驱动

ViewModel → UseCase (startMonitoring / getInfo)
    ↓ 注入 Repository

UseCase → Repository.collectData()
    ↓ runCatching { ... }.onFailure { log }

Repository → DataSource.getData()
```

---

## 6. 新架构的崩溃防线

基于 10 条历史教训，新架构从各层建立防御：

| 层面 | 措施 | 对应教训 |
|------|------|---------|
| **编译期** | compileSdk=35, 锁定所有依赖版本 | 教训 1 |
| **主题** | 自包含 theme，不依赖 Material 内部 parent | 教训 2 |
| **View 绑定** | SafeViewPagerBinder 封装防递归 | 教训 3 |
| **UI 组件** | 全局统一 MaterialCardView | 教训 4 |
| **异常处理** | 全采集层 runCatching（捕获 Throwable） | 教训 5 |
| **布局验证** | ViewModel 保证数据可用性，不再依赖空 XML | 教训 6 |
| **API 兼容** | DataSource 层多 API 路径 fallback + 可空类型 | 教训 7, 8 |
| **自动化** | CI 构建 + ktlint + 未来添加单元测试 | 教训 9 |
| **UI 封装** | 悬浮窗使用标准 FrameLayout，不用 CardView | 教训 10 |
