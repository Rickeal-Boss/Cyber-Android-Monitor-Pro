# 架构迁移路线图 (Migration Plan)

> **核心原则**: 每阶段交付可运行的应用，零中断

---

## 概览

| Phase | 名称 | 目标 | 工期 | 风险 |
|-------|------|------|------|------|
| 0 | 崩溃防线加固 | 消除所有已知崩溃点 | 1 天 | 🟢 低 |
| 1 | DI + ViewModel | 引入 Koin，创建 ViewModel | 1-2 天 | 🟡 中 |
| 2 | 拆分 Repository + UseCase | 消灭 God Object | 2-3 天 | 🟡 中 |
| 3 | UI 状态管理升级 | Sealed State + LiveData 驱动 | 1-2 天 | 🟢 低 |
| 4 | 质量加固 | 测试、混淆、Lint | 2 天 | 🟢 低 |

---

## Phase 0 — 崩溃防线加固 🔴 最高优先级

### 目标
基于 10 条历史教训，立即修复所有已知崩溃点。

### 变更清单

| # | 文件 | 变更 | 对应教训 |
|---|------|------|---------|
| 0.1 | `app/build.gradle` | `compileSdk 36 → 35`, `targetSdk 36 → 35` | 教训 1 |
| 0.2 | `app/build.gradle` | 锁定所有依赖版本为精确版本 | 教训 1 |
| 0.3 | `DeviceRepository.kt` | 所有 `runCatching` 确认覆盖（已是 ✅） | 教训 5 |
| 0.4 | 所有 DataSource | 确认 `runCatching` 或 `catch(Throwable)` | 教训 5, 7 |
| 0.5 | `themes.xml` | TabTextAppearance 完全自包含，不继承 Material 内部 style | 教训 2 |
| 0.6 | 所有 `*_new.xml` | 统一使用 `MaterialCardView` | 教训 4 |
| 0.7 | `MainActivity.kt` | 提取 SafeViewPagerBinder 工具类 | 教训 3 |
| 0.8 | `floating_window.xml` | 移除 MaterialCardView，用 LinearLayout | 教训 10 |

### 预期结果
- ✅ `./gradlew assembleDebug` 一次通过
- ✅ 启动无闪退
- ✅ 5 Tab 切换正常

---

## Phase 1 — 引入 Koin DI + ViewModel 层

### 目标
建立 DI 框架，创建 ViewModel 层，Fragment 通过 ViewModel 访问数据。

### 依赖变更
```kotlin
// app/build.gradle
implementation("io.insert-koin:koin-android:3.5.6")
implementation("io.insert-koin:koin-androidx-viewmodel:3.5.6")
```

### 步骤

#### Step 1.1: 创建 Koin Module
```kotlin
// di/AppModule.kt
val appModule = module {
    // 保留现有 DeviceRepository（Phase 2 再拆分）
    single { DeviceRepository(androidContext()) }
    
    // ViewModel（每个 Fragment 一个）
    viewModel { DashboardViewModel(get()) }
    viewModel { CpuViewModel(get()) }
    viewModel { GpuViewModel(get()) }
    viewModel { MemoryViewModel(get()) }
    viewModel { BatteryViewModel(get()) }
    viewModel { NetworkViewModel(get()) }
    viewModel { HardwareViewModel(get()) }
    viewModel { SystemViewModel(get()) }
}
```

#### Step 1.2: 初始化 Koin
```kotlin
// DeviceApplication.kt
override fun onCreate() {
    super.onCreate()
    startKoin {
        androidLogger()
        androidContext(this@DeviceApplication)
        modules(appModule)
    }
    // ... 但保留崩溃日志
}
```

#### Step 1.3: 创建 ViewModel（以 CpuViewModel 为例）
```kotlin
// ui/cpu/CpuViewModel.kt
class CpuViewModel(
    private val repository: DeviceRepository
) : ViewModel() {
    val cpuInfo = repository.cpuLiveData
    val gpuInfo = repository.gpuLiveData
    val historyData = MutableLiveData<List<HistoryDataPoint>>()
    
    init {
        // 历史数据观察
        repository.historyCache.observe("cpu_temp") { series ->
            historyData.postValue(series)
        }
    }
    
    fun refresh() { repository.loadStaticData() }
}
```

#### Step 1.4: 更新 Fragment
```kotlin
// ui/cpu/CpuFragment.kt
class CpuFragment : Fragment() {
    private val viewModel: CpuViewModel by viewModel()
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // ❌ 删除: repo = DeviceApplication.getDeviceRepository()
        
        // ✅ 改为:
        viewModel.cpuInfo.observe(viewLifecycleOwner) { cpu -> updateCpuInfo(cpu) }
        viewModel.historyData.observe(viewLifecycleOwner) { chart.setData(it) }
        
        // ❌ 删除: handler.postDelayed(chartUpdater, 3000)
    }
}
```

### 回滚策略
如果 Phase 1 出现问题，只需回退 Fragment 中 `by viewModel()` 改为 `DeviceApplication.getDeviceRepository()`。Koin 模块可以保留不删。

---

## Phase 2 — 拆分 God Repository + 引入 UseCase 层

### 目标
将 `DeviceRepository` 拆分为 8 个独立的 Repository + 10 个 UseCase。

### 拆分方案

| 新 Repository | 职责 | 依赖的 DataSource |
|--------------|------|-------------------|
| `CpuRepository` | CPU 数据采集 + 历史 | CpuDataSource |
| `GpuRepository` | GPU 数据采集 | GpuDataSource |
| `BatteryRepository` | 电池数据采集 + 历史 | BatteryDataSource |
| `MemoryRepository` | 内存/ZRAM 数据 + 历史 | MemoryDataSource |
| `StorageRepository` | 存储信息 | StorageDataSource |
| `NetworkRepository` | WiFi + 移动网络 + 网络接口 | WifiDS, MobileDS, NetIfDS |
| `GpsRepository` | GPS 卫星数据 | GpsDataSource |
| `SensorRepository` | 传感器列表 + 系统信息 | SensorDS, SystemDS |

**HistoryCache 改为独立注入**，每个需要历史数据的 Repository 注入 HistoryCache。

### UseCase 设计
```kotlin
// domain/usecase/MonitorCpuUseCase.kt
class MonitorCpuUseCase(
    private val cpuRepo: CpuRepository,
    private val gpuRepo: GpuRepository,
    private val historyCache: HistoryCache
) {
    suspend fun collect(): CpuSnapshot {
        val cpu = cpuRepo.getCpuInfo()
        val gpu = gpuRepo.getGpuInfo()
        // 记录历史
        historyCache.addPoint("cpu_temp", cpu.temperature)
        return CpuSnapshot(cpu, gpu)
    }
}

data class CpuSnapshot(val cpu: CpuInfo, val gpu: GpuInfo)
```

### Koin 模块更新
```kotlin
val appModule = module {
    // DataSource
    single { CpuDataSource() }
    single { GpuDataSource() }
    // ... 12 个
    
    // Repository
    single { CpuRepository(get()) }
    single { GpuRepository(get()) }
    // ... 8 个
    
    // History
    single { HistoryCache() }
    
    // UseCase
    factory { MonitorCpuUseCase(get(), get(), get()) }
    // ...
    
    // ViewModel（稍作调整）
    viewModel { CpuViewModel(get()) }  // 注入 UseCase 而非 Repository
}
```

### 回滚策略
若 Phase 2 出现问题，保留新的 Repository 类但 ViewModel 可以回退到直接依赖 DeviceRepository。Koin 模块的热切换能力保证回滚成本极低。

---

## Phase 3 — UI 状态管理升级

### 目标
每个 ViewModel 引入 sealed UiState，Fragment 通过状态渲染。

### 示例
```kotlin
// ui/common/UiState.kt
sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String, val cause: Throwable? = null) : UiState<Nothing>()
}

// ui/cpu/CpuViewModel.kt
class CpuViewModel(...) : ViewModel() {
    private val _uiState = MutableLiveData<UiState<CpuUiData>>()
    val uiState: LiveData<UiState<CpuUiData>> = _uiState
    
    init {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            useCase.monitorCpu.collect { snapshot ->
                _uiState.value = UiState.Success(snapshot.toUiData())
            }
        }
    }
}

// ui/cpu/CpuFragment.kt
viewModel.uiState.observe(viewLifecycleOwner) { state ->
    when (state) {
        is UiState.Loading -> showLoading()
        is UiState.Success -> render(state.data)
        is UiState.Error -> showError(state.message)
    }
}
```

---

## Phase 4 — 质量加固

### 4.1 混淆
```kotlin
// app/build.gradle
buildTypes {
    release {
        minifyEnabled = true
        shrinkResources = true
        proguardFiles(...)
    }
}
```

### 4.2 Lint
```bash
./gradlew lint
```

### 4.3 单元测试（至少覆盖核心逻辑）
- `MemoryDataSource` 的 ZRAM 压缩比计算
- `BatteryDataSource` 的循环次数计算
- 各 UseCase 的 collect 逻辑

### 4.4 未来迁移计划
- MPAndroidChart → Vico（更现代的图表库）
- XML/Fragment → Jetpack Compose（渐进迁移）
