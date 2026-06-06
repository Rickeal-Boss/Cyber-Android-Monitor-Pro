# Device Info Viewer — 架构现状分析（AI代码审查文档,可能代码读取不准确）

> **日期**: 2026-05-27
> **版本**: 2.0 (Kotlin 迁移后)
> **文件数**: 53 Kotlin + 48 XML + 6 Config

---

## 1. 现有架构概览

```
┌─────────────────────────────────────────────────────────┐
│  UI Layer (Fragments)                                    │
│  Dashboard / CPU / GPU / Memory / Battery / Network      │
│  Hardware / System                                       │
├─────────────────────────────────────────────────────────┤
│  ❌ ViewModel 层 — 不存在                                  │
├─────────────────────────────────────────────────────────┤
│  God Repository: DeviceRepository                        │
│  ├── 12 DataSources (CPU/GPU/Battery/Memory/Storage/     │
│  │    WiFi/Mobile/GPS/Sensors/System/Network/SysFs)      │
│  ├── 11 MutableLiveData                                  │
│  ├── HistoryCache                                        │
│  ├── CoroutineScope + SupervisorJob                     │
│  └── 所有业务逻辑散落于此                                    │
├─────────────────────────────────────────────────────────┤
│  DataSource Layer (12 个)                                 │
│  └── 直接访问 /proc /sys, dumpsys, Android API             │
├─────────────────────────────────────────────────────────┤
│  Model Layer (14 data class)                              │
└─────────────────────────────────────────────────────────┘
```

## 2. 问题清单

### 2.1 🔴 P0 — 架构结构性缺陷

| # | 问题 | 严重性 | 影响范围 |
|---|------|--------|---------|
| 1 | **God Repository**: `DeviceRepository` 包含全部 12 个 DataSource + 11 个 LiveData + HistoryCache + 协程调度，违反 SRP | 致命 | 所有数据流 |
| 2 | **无 ViewModel 层**: 全项目 0 个 ViewModel，MVVM 架构名存实亡 | 致命 | 所有 UI |
| 3 | **手动单例 DI**: `DeviceApplication.getDeviceRepository()` 无生命周期管理，全局可变 | 高 | 全局状态 |
| 4 | **异常处理缺陷**: 历史 3 次闪退皆因 `catch(Exception)` 漏掉 `Error` 子类 (`NoSuchMethodError`) | 致命 | 启动 & 采集 |

### 2.2 🟡 P1 — 设计与可维护性

| # | 问题 | 严重性 | 说明 |
|---|------|--------|------|
| 5 | **Handler/Runnable 图表更新**: CpuFragment + DashboardFragment 各有一套独立 Handler 轮询更新图表，而非基于数据驱动 | 高 | 2 处 |
| 6 | **BaseMonitorFragment 形同虚设**: 声称封装 ViewModel 但无 ViewModel | 中 | 1 处 |
| 7 | **Fragment 直接依赖 Repository**: 8 个 Fragment 都直接持有 `DeviceRepository?` 并调用 `?.observe()` + `!!` 强制非空 | 中 | 8 个 Fragment |
| 8 | **字符串拼接在 Fragment 中**: 格式化逻辑散落各 Fragment（如 "核心 ${core.coreIndex}"、"%.0f MHz"） | 低 | 多处 |

### 2.3 🔵 P2 — 技术债

| # | 问题 | 严重性 | 说明 |
|---|------|--------|------|
| 9 | **`compileSdk 36` + Material 1.12.0 + MPAndroidChart v3.1.0** 组合已验证不稳定 | 高 | 历史 4 轮修复 |
| 10 | **`minifyEnabled = false`** → 无混淆、无代码压缩 | 中 | 安全 + APK 大小 |
| 11 | **40+ 处 `catch(Exception ignored)`** 静默吞异常 | 中 | 调试噩梦 |
| 12 | **无测试**: 0 个单元测试，0 个 UI 测试 | 中 | 回归无保障 |
| 13 | **TabLayout ↔ ViewPager2 互递归**: 已用手动 flag 修复，但代码脆弱 | 低 | 历史根因 |

## 3. 历史教训清单

以下是 2026-05-20 到 2026-05-27 期间经历的重要教训，**新架构必须彻底杜绝**：

| 教训 # | 场景 | 根因 | 架构级预防措施 |
|--------|------|------|---------------|
| 1 | 启动闪退（4 轮） | `compileSdk 36` + 老库内部 View 构造链冲突 | **编译期锁定 compileSdk 35** |
| 2 | 启动闪退（第 2 次） | `TabTextAppearance` 父样式在 Material 3 中不存在 | **使用自包含 theme，不依赖内部 parent** |
| 3 | 启动闪退（第 3 次） | TabLayout ↔ ViewPager2 互递归死循环 | **封装为 SafeViewPagerBinder 工具类** |
| 4 | 网络页闪退 | CardView vs MaterialCardView 混用导致 onMeasure 崩溃 | **全局统一使用 MaterialCardView** |
| 5 | 运行时崩溃 | `NoSuchMethodError` 不被 `catch(Exception)` 捕获 | **所有采集层使用 `catch(Throwable)`** |
| 6 | 网络页 CI 失败 | `fragment_network_new.xml` 空壳 → 14 个 R.id 缺失 | **编译器会自动检查；真正的防范是必须有 ViewModel** |
| 7 | 信号永远 N/A | `getDbm()` API 31 移除 + `Integer.MAX_VALUE` 初始值 | **编译期 API 检查 + 数据模型用可空类型** |
| 8 | ZRAM 压缩比反了 | 逻辑错误：compressed/original 方向颠倒 | **单元测试覆盖** |
| 9 | Java→Kotlin CI 失败 | 30+ getter 风格调用错误 | **自动化 Lint 检查** |
| 10 | 悬浮窗不可见 | `app:cardBackgroundColor` 覆盖 `android:background` | **UI 组件统一封装** |

---

## 4. 依赖风险矩阵

| 依赖 | 版本 | 发布日期 | 风险 |
|------|------|---------|------|
| compileSdk | 36 | 2025 | 🔴 过高，与 Material 1.12.0 冲突 |
| Material Components | 1.12.0 | 2024 Q4 | 🟡 未针对 SDK 36 优化 |
| MPAndroidChart | 3.1.0 | 2020 | 🔴 5 年未更新，SDK 29 时代产物 |
| Kotlin | 2.0.21 | 2024 Q4 | 🟢 稳定 |
| Coroutines | 1.8.1 | 2024 Q1 | 🟢 稳定 |
| AndroidX Core KTX | 1.13.1 | 2024 Q2 | 🟡 可升级到 1.15+ |
