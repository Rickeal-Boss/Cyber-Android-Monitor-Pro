# 架构决策记录 (ADR)

## ADR-001: 采用 MVVM + UseCase 分层架构

### 状态
Accepted

### 上下文
当前架构虽标榜 "MVVM"，但 ViewModel 层完全不存在。Fragments 直接与 God Repository 通信，导致：
- 业务逻辑散落 Fragments
- 无 UI 状态管理
- 配置变更时数据不保留
- 单元测试无法覆盖

### 决策
采用 Android 官方推荐的 **MVVM + UseCase** 三层架构：

```
UI Layer        → Fragment + ViewModel（1:1 绑定）
Domain Layer    → UseCase（封装单一业务操作）
Data Layer      → Repository + DataSource（按领域拆分）
```

**关键原则**:
- 每个 Fragment 绑定唯一 ViewModel，ViewModel 持有 UI 状态
- ViewModel 通过 UseCase 访问 Repository，不直接依赖 Repository
- Repository 按领域拆分（CpuRepository, BatteryRepository 等），拒绝 God Object
- UI 状态使用 sealed class 建模（Loading / Success / Error）

### 替代方案
| 方案 | 优点 | 缺点 | 决策 |
|------|------|------|------|
| MVI (StateFlow + Reducer) | 时间旅行调试 | 学习成本高，本项目简单数据展示不需要 | ❌ 过度设计 |
| 保持现状 + 仅加 ViewModel | 改动最小 | 不解决 God Repository 问题 | ❌ 治标不治本 |
| MVP (Presenter) | 可测性强 | Activity/Fragment 为 View，笨重 | ❌ 已过时 |

### 影响
- **更容易**: 单元测试覆盖 ViewModel + UseCase、Fragments 不再关心数据来源
- **更困难**: 新增 UseCase 需额外编写，但代码量很小（通常 10-20 行/个）

---

## ADR-002: 使用 Koin 作为依赖注入框架

### 状态
Accepted

### 上下文
当前使用 `DeviceApplication.getDeviceRepository()` 手动单例模式：
- Fragment 中 `repo!!` 强制非空调用
- 全局可变状态，无法隔离测试
- 无生命周期感知

### 决策
选择 **Koin** 作为 DI 框架：

```kotlin
// AppModule.kt
val appModule = module {
    // Repository（按领域拆分）
    single { CpuRepository(get()) }
    single { BatteryRepository(androidContext(), get()) }
    // ... 每个 Repository 按需

    // ViewModel
    viewModel { DashboardViewModel(get(), get()) }
    viewModel { CpuViewModel(get(), get()) }
}

// Fragment
class CpuFragment : Fragment() {
    private val viewModel: CpuViewModel by viewModel()
}
```

### 选型对比
| 标准 | Koin | Hilt/Dagger | 手动单例 |
|------|------|------------|---------|
| 编译期安全 | ❌ 运行时 | ✅ 注解处理 | ❌ 无 |
| 初始化时间 | 🟢 ~50ms | 🟡 ~200ms | 🟢 0ms |
| 学习成本 | 🟢 低（纯 Kotlin DSL） | 🔴 高（注解+代码生成） | 🟢 极低 |
| 代码量 | 🟢 ~30 行配置 | 🟡 ~60 行 | 🔴 散落各处 |
| 测试隔离 | ✅ | ✅ | ❌ |

**选择 Koin 的理由**:
1. 项目规模小（<100 文件），不需要 Hilt 的编译期检查
2. Kotlin DSL 简洁，无需注解处理
3. 对编译时间几乎无影响
4. 与 ViewModel 集成无缝 (`by viewModel()`)

### 影响
- **更容易**: Fragment 获取依赖、测试时替换 Mock、生命周期管理
- **更困难**: 无编译期安全检查（但可通过编写 Koin 测试模块弥补）

---

## ADR-003: UI 状态使用 Sealed Class + LiveData

### 状态
Accepted

### 上下文
当前 Fragment 无状态建模，直接操作 View：
- 无 Loading 状态
- 无 Error 状态
- 数据为 null 时静默显示 "N/A"
- `catch(Exception)` 吞掉所有异常，用户无感知

### 决策
每个 ViewModel 定义对应的 UI 状态：

```kotlin
sealed class CpuUiState {
    object Loading : CpuUiState()
    data class Success(
        val model: String,
        val cores: List<CoreUiModel>,
        val temperature: String,
        val chartData: List<Float>
    ) : CpuUiState()
    data class Error(val message: String) : CpuUiState()
}
```

使用 `LiveData` 而非 `StateFlow`：
- `LiveData` 生命周期感知（自动暂停/恢复）
- 与现有 AndroidX 生态一致
- 对于数据展示型应用，Flow 的优势（背压、操作符链）用不上

### 影响
- **更容易**: 用户看到明确的状态（加载中 → 数据 → 出错），异常不再静默
- **更困难**: 每个 ViewModel 需要额外定义 State 类（但代码量可控）

---

## ADR-004: 锁定 compileSdk = 35

### 状态
Accepted

### 上下文
2026-05-26 的 4 轮闪退修复最终根因：`compileSdk 36` 与 `Material Components 1.12.0` (针对 SDK 34/35) 以及 `MPAndroidChart 3.1.0` (2020年，SDK 29) 存在内部 API 不兼容。

### 决策
锁定 `compileSdk = 35` + `targetSdk = 35`，直到以下条件全部满足再升级：
1. Material Components 发布正式支持 SDK 36 的版本（≥ 1.13.0）
2. 替代 MPAndroidChart 或确认其兼容
3. 在真机 SDK 36 上完整回归测试通过

### 影响
- **更容易**: 彻底消除老库的 View 构造链兼容性崩溃
- **更困难**: 无法使用 SDK 36 的新 API（但本项目不需要）

---

## ADR-005: 异常处理策略：Repository 层必须捕获 Throwable

### 状态
Accepted

### 上下文
2026-05-26 第二次闪退：`CellSignalStrength.getDbm()` 在部分 OEM ROM 抛 `NoSuchMethodError`（Error 子类，非 Exception），两层 `catch(Exception)` 全部漏过。

### 决策
所有 DataSource 和 Repository 层的采集代码：

```kotlin
// ✅ 正确
runCatching { getCpuInfo() }.onFailure { log(it) }

// ❌ 错误（历史教训）
try { getCpuInfo() } catch (e: Exception) { /* 漏掉 Error */ }
```

`runCatching` 内部使用 `Result` 类型，会同时捕获 Exception 和 Error。

### 影响
- **更容易**: OEM ROM 古怪 Error 不再导致崩溃
- **更困难**: 无（`runCatching` 替换 `catch(Exception)` 是直接改进）

---

## ADR-006: 图表组件由数据驱动而非轮询更新

### 状态
Accepted

### 上下文
CpuFragment + DashboardFragment 各有一套 `Handler.postDelayed(3000ms)` 轮询 `historyCache.getSeries()` 更新图表。这导致：
- 图表更新频率不一致（采集 2s vs 图表 3s）
- 内存泄漏风险（Handler 需手动清理）
- 代码重复

### 决策
在 ViewModel 中引入 `historyChartData: LiveData<List<HistoryDataPoint>>`，图表组件直接观察。每次 Repository 写入新数据点时自动触发图表更新。

```kotlin
// ViewModel
val cpuTempHistory: LiveData<List<HistoryDataPoint>> =
    Transformations.map(cpuLiveData) { /* 维护历史列表 */ }

// Fragment
viewModel.cpuTempHistory.observe(viewLifecycleOwner) { chart.setData(it) }
```

### 影响
- **更容易**: 图表与数据同步，无轮询开销，无 Handler 泄漏
- **更困难**: 需要调整 HistoryCache 使其可观察（或由 ViewModel 管理历史）

---

## ADR-007: MPAndroidChart 维持现状，暂不替换

### 状态
Proposed

### 上下文
MPAndroidChart v3.1.0 发布于 2020 年，5 年未更新。但因本项目仅用于简单的折线图展示，功能需求简单。

### 决策
暂时保留 MPAndroidChart v3.1.0，但通过自定义 View 封装（MonitorChartView + HistoryChartView 已存在），降低直接依赖面。待项目稳定后评估是否需要迁移到 `Vico` 或 Compose Canvas。

### 影响
- **更容易**: 无（维持现状）
- **更困难**: 后续 API 兼容风险持续存在
