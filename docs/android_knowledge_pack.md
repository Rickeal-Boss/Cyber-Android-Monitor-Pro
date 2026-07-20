# Android 官方开发知识包（Cyber Android Monitor · 随时查阅版）

**适用范围**：Cyber Android Monitor（原 DeviceInfoViewer）v2.0.202+ / v3 重构
**技术栈（项目当前，2026-07-20 工具链升级后）**：Kotlin 2.2.10（AGP 9 内置）· Jetpack Compose BOM 2025.06.00 · Material3 · MVVM + Koin 3.5.6 · kotlinx-coroutines-android 1.11.0 · AGP 9.0.1 / Gradle 9.1 / compileSdk 36 / targetSdk 35
**构建约束**：`minSdk = 21`、`compileSdk/targetSdk = 35`、**未启用 coreLibraryDesugaring**
**整理日期**：2026-07-20（**前瞻性刷新版**：含 Android 16/17、Kotlin 2.4.x、Compose BOM 2026.06.00、Play targetSdk 时间线、健康权限迁移等最新事实）
**性质**：单一事实源（single source of truth）。所有结论均回链 Android 开发者官网 / AOSP 源码 / Kotlin 官方文档。后续开发、修 bug、加功能前先查本包对应章节。

> 🔮 **前瞻性摘要（截至 2026-07）**：Android 16 (`BAKLAWA`, API 36) 已于 2025-06-10 正式发布；Android 17 (API 37) 处 Beta，代号/常量名未定。**Kotlin 最新稳定 2.4.10**（K2 编译器默认稳定）；**Compose BOM 最新 2026.06.00**；**AGP 最新 9.0.1**（需 Gradle 9.1 + JDK 17）。**Google Play 要求 2026-08-31 起新应用/更新须 `targetSdk 36`**（可延至 2026-11-01）——本项目当前 `targetSdk 35` 须在此前完成**健康权限迁移 / 大屏自适应 / edge-to-edge** 改造。版本路线图与升级压力见 §13。

---

## 📑 目录

- [0. 项目约束与速查](#0-项目约束与速查)
- [1. 电池与电源](#1-电池与电源)
- [2. 进程 / Shell 执行](#2-进程--shell-执行)
- [3. Kotlin 协程 / Flow / 生命周期感知](#3-kotlin-协程--flow--生命周期感知)
- [4. 设备标识 (Build) / SoC / CPU](#4-设备标识-build--soc--cpu)
- [5. 内存 (RAM / SWAP / ZRAM)](#5-内存-ram--swap--zram)
- [6. 传感器](#6-传感器)
- [7. 权限与隐私 (API 23–35)](#7-权限与隐私-api-2335)
- [8. Compose / Material3 / 主题 / 设计 Token](#8-compose--material3--主题--设计-token)
- [9. Koin 依赖注入 / DataStore 持久化](#9-koin-依赖注入--datastore-持久化)
- [10. 构建 / 发布 / 签名 / 缩减 / 脱糖](#10-构建--发布--签名--缩减--脱糖)
- [11. 国际化 (i18n) 与本地化](#11-国际化-i18n-与本地化)
- [12. 各 API 级别行为变更（API 11–17，含 Android 15/16/17）](#12-各-api-级别行为变更对监控类-app-的影响)
- [13. 版本前瞻与升级路线图（行动项）](#13-版本前瞻与升级路线图行动项)
- [附：官方参考来源索引](#附官方参考来源索引)

---

## 0. 项目约束与速查

| 项 | 值 | 对可用 API 面的影响 |
|----|----|----|
| `minSdk` | **21** | `Build.SOC_MODEL`/`SOC_MANUFACTURER`(API31)、`Process.waitFor(timeout)`/`destroyForcibly`/`isAlive`(API26)、`collectAsStateWithLifecycle`(Lifecycle 2.7.0)、`dynamicColorScheme`(API31) 等在 21–30 设备**不可用** → 必须降级路径 / 运行时分支 |
| `compileSdk`/`targetSdk` | **36 / 35** | 可编译全部最新 API（含 `BATTERY_PROPERTY_STATE_OF_HEALTH` 等 `@FlaggedApi`），但运行时仍按 `minSdk` 分支；⚠️ **Play 要求 2026-08-31 起须 `targetSdk 36`**（见 §13） |
| Kotlin | **2.2.10**（AGP 9.0.1 内置；最新稳定 **2.4.10** 需 AGP 9.1+） | AGP 9 内置 Kotlin，禁止显式 apply kotlin-android 插件；编译器见 §8.6 |
| Compose BOM | **2025.06.00**（最新稳定 **2026.06.00**） | 统一 Compose 库版本；BOM 2026.06.00 需 Kotlin 2.4.x（AGP 9.1+），AGP 9.0.1 内置 Kotlin 2.2.10 下 BOM 上限 2025.06.00 |
| kotlinx-coroutines | **1.11.0**（最新稳定） | 协程库 |
| AGP / Gradle / JDK | **AGP 9.0.1 / Gradle 9.1 / JDK 17** | 构建工具链；AGP 9 默认内置 Kotlin 2.2.10 |
| coreLibraryDesugaring | **未启用** | ⚠️ `java.time` 子集 / `java.util.stream` 可脱糖，但 **`Process.waitFor(timeout)`/`destroyForcibly`/`isAlive` 不在脱糖表**——低版本必须自建超时范式 |

> **兼容性铁律**：凡文档标注「API N+」的能力，在 minSdk 21 下都需 `if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.X)` 分支 + 降级实现，不可直接调用。

> 🔮 **版本前瞻（截至 2026-07，2026-07-20 已执行工具链升级）**：项目已升 AGP 9.0.1 / Gradle 9.1 / 内置 Kotlin 2.2.10 / BOM 2025.06.00 / coroutines 1.11.0 / compileSdk 36。`targetSdk 35` 仍须于 **2026-08-31 前升到 36**（健康权限迁移 + 大屏自适应 + edge-to-edge），见 §13。Kotlin 2.4.10 + BOM 2026.06.00 需 **AGP 9.1+**（内置 Kotlin 抬升），列为后续。

---

## 1. 电池与电源

> 来源：AOSP `frameworks/base/core/java/android/os/BatteryManager.java` 源码注释 + [BatteryManager 参考](https://developer.android.com/reference/android/os/BatteryManager) + [电池监控指南](https://developer.android.com/training/monitoring-device-state/battery-monitoring) + [Doze/待机](https://developer.android.com/training/monitoring-device-state/doze-standby) + [PowerManager](https://developer.android.com/reference/android/os/PowerManager)

### 1.1 `BatteryManager` 属性常量（经 `getIntProperty`/`getLongProperty`/`getStringProperty` 读取）

| 常量 | 值 | 单位 | 含义 |
|------|----|------|------|
| `BATTERY_PROPERTY_CHARGE_COUNTER` | 1 | **µAh**（微安时） | 当前电荷量计数 |
| `BATTERY_PROPERTY_CURRENT_NOW` | 2 | **µA**（微安） | 瞬时电流，正=充入 / 负=放出 |
| `BATTERY_PROPERTY_CURRENT_AVERAGE` | 3 | µA | 平均电流（周期取决于电量计硬件） |
| `BATTERY_PROPERTY_CAPACITY` | 4 | **百分比 (%)** | **剩余容量百分比，整数无小数** |
| `BATTERY_PROPERTY_ENERGY_COUNTER` | 5 | **nWh**（纳瓦时） | 剩余能量 |
| `BATTERY_PROPERTY_STATUS` | 6 | — | 充电状态枚举值 |
| `BATTERY_PROPERTY_STATE_OF_HEALTH` | 10 | **百分比 (%)** | 健康度（实测满容 / 标称容），`@FlaggedApi`（compileSdk 35 可调用） |

- `getIntProperty(int)`：不支持时返回 `0`（API<28）或 `Integer.MIN_VALUE`（API≥28）。
- `getLongProperty(int)`：不支持时返回 `Long.MIN_VALUE`。
- `getStringProperty(int)`：不支持时返回 `null`（受 Flag 限制，主要用于 serial/part status）。
- ⚠️ **务必判空 / 判 `Long.MIN_VALUE` 异常值**：这些值大多不被所有设备实现。

### 1.2 关键纠正（直接命中本项目）

- ⚠️ **`BATTERY_PROPERTY_CAPACITY` 是百分比，不是 mAh**。原代码曾误赋给 `capacityDesignMAh`——会把 0–100 的百分比污染 mAh 字段。设计容量应改从 sysfs `charge_full_design` 或 `power_profile.xml` 获取；该常量最多只作 `levelPercent` 冗余校验。
- ✅ `CHARGE_COUNTER` 单位 µAh，`/1000 → mAh` 正确。
- ✅ `CURRENT_NOW` 单位 µA，与项目 `currentNowUA` 口径一致。
- sysfs `/sys/class/power_supply/battery/charge_full[_design]` 按 Linux power_supply class 规范为 **µAh**，`/1000 → mAh` 是标准归一化；但**部分 OEM 内核以 mAh 或库仑上报**，导致 1000× 偏差（正是 P0 单位归一债的本质）。

### 1.3 `ACTION_BATTERY_CHANGED` 粘性广播

- **无需权限**；`registerReceiver(null, IntentFilter(ACTION_BATTERY_CHANGED))` 立即返回当前状态 Intent。
- ⚠️ API 26+ 起不能静态（Manifest）监听其变化，只能取当前值或运行时动态注册。
- 返回 Intent 的 `EXTRA_*`：

| Extra | 含义 | 单位/取值 |
|-------|------|----------|
| `EXTRA_LEVEL` | 当前电量 | int |
| `EXTRA_SCALE` | 满量程 | int（通常 100） |
| `EXTRA_TEMPERATURE` | 电池温度 | **0.1 °C**（如 350 = 35.0°C） |
| `EXTRA_VOLTAGE` | 电压 | **mV** |
| `EXTRA_HEALTH` | 健康 | `BATTERY_HEALTH_*` |
| `EXTRA_STATUS` | 状态 | `BATTERY_STATUS_*` |
| `EXTRA_PLUGGED` | 充电源 | 0 / `BATTERY_PLUGGED_AC`(1) / `USB`(2) / `WIRELESS`(4) |
| `EXTRA_PRESENT` | 是否在位 | bool |
| `EXTRA_TECHNOLOGY` | 化学类型 | String（如 Li-ion） |
| `EXTRA_CHARGE_COUNTER`/`CURRENT_NOW`/`CURRENT_AVERAGE`/`CAPACITY`/`ENERGY_COUNTER`/`CYCLE_COUNT` | 历史隐藏字段（maxTargetSdk=R） | 优先用 `BatteryManager.getProperty` 而非 Intent Extra |

### 1.4 健康 / 状态枚举（精确值）

`BATTERY_HEALTH_*`：UNKNOWN=1、GOOD=2、OVERHEAT=3、DEAD=4、OVER_VOLTAGE=5、UNSPECIFIED_FAILURE=6、COLD=7。
`BATTERY_STATUS_*`：UNKNOWN=1、CHARGING=2、DISCHARGING=3、NOT_CHARGING=4、FULL=5。

### 1.5 充电 / 电量 API

- `ACTION_CHARGING` / `ACTION_DISCHARGING`：充电状态变化广播（可 Manifest 注册）。
- `boolean isCharging()`：是否充电（插电且电量上升或已满）。
- `long computeChargeTimeRemaining()`：**API 28+**，充满剩余毫秒；数据不足或放电时返回 `-1`。
- 源码中**无** `registerBatteryStats` 公共方法；`BATTERY_STATS` 权限相关属性为 `@SystemApi`，三方 App 不可直接获取。

### 1.6 电源管理 / Doze（对轮询的影响）

- `PowerManager.isDeviceIdleMode()`：**API 23+**，设备是否处于 Doze。
- `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` + `isIgnoringBatteryOptimizations(pkg)`：电池优化白名单查询。
- `AlarmManager.setAndAllowWhileIdle()` / `setExactAndAllowWhileIdle()`：**API 23+**，Doze 下仍可触发，但**每应用每 9 分钟最多一次**。
- Doze 限制：挂起网络、忽略 WakeLock、推迟 JobScheduler/WorkManager、标准 Alarm 延迟到维护窗口；**后台轮询被严重压制**。Google Play 政策禁止随意申请电池优化豁免——监控需求应评估是否真正符合豁免用例，或结合前台服务。

### 1.7 热量 / 温度

- 电池温度来自 `EXTRA_TEMPERATURE`（0.1°C），BatteryManager **未**暴露 thermal status 常量。
- 设备级温控：`PowerManager.getCurrentThermalStatus()`（**API 29+**）返回 `THERMAL_STATUS_NONE/LIGHT/MODERATE/SEVERE/CRITICAL/EMERGENCY/SHUTDOWN`；系统温控会主动限制充电电流（隐藏字段，三方不可靠）。

---

## 2. 进程 / Shell 执行

> 来源：[Process](https://developer.android.com/reference/java/lang/Process) · [ProcessBuilder](https://developer.android.com/reference/java/lang/ProcessBuilder) · [Runtime](https://developer.android.com/reference/java/lang/Runtime)

### 2.1 `Process` 方法

| 方法 | 引入 | 说明 |
|------|------|------|
| `int waitFor()` | API 1 | **永久阻塞**到进程结束（minSdk 21 唯一可用） |
| `boolean waitFor(long, TimeUnit)` | **API 26** | 超时返回 `false` |
| `int exitValue()` | API 1 | 未结束抛 `IllegalThreadStateException` |
| `void destroy()` | API 1 | 温和终止（请求式） |
| `Process destroyForcibly()` | **API 26** | 强制杀 |
| `boolean isAlive()` | **API 26** | 是否存活 |
| `getInputStream()`/`getErrorStream()`/`getOutputStream()` | API 1 | 子进程 stdout/stderr/stdin |

**minSdk 21 安全超时范式**（无 `waitFor(timeout)`/`destroyForcibly`）：起线程 `p.waitFor()`，`join(timeoutMs)` 后若仍存活则 `p.destroy()`（替代 `destroyForcibly`）。本项目已落地 `util/ProcessExtensions.kt#waitForWithTimeout()`。

### 2.2 `ProcessBuilder`

构造 `ProcessBuilder(List<String>)` / `ProcessBuilder(String...)`；`redirectErrorStream(boolean)` 合并 stderr→stdout；`redirectOutput/Error(Redirect)`；`start()`；`environment()`（Map）；`directory(File)`。

### 2.3 `Runtime.exec` 坑

6 个重载（`exec(String)` / `exec(String, String[])` / `exec(String, String[], File)` / `exec(String[])` / `exec(String[], String[])` / `exec(String[], String[], File)`）。
- **坑1**：`String` 重载按空白 ` \t\n\r\f` 切分，**不支持引号/含空格参数** → 必须用 `String[]` 数组。
- **坑2**：不同步消费 stdout/stderr，缓冲区满会**死锁** → 用线程并行消费，或 `redirectErrorStream(true)` 合并读取；读 sysfs 建议数组 + 边读边消费。

---

## 3. Kotlin 协程 / Flow / 生命周期感知

> 来源：[Kotlin 协程](https://developer.android.com/kotlin/coroutines) · [取消与超时](https://kotlinlang.org/docs/cancellation-and-timeouts.html) · [Flow](https://developer.android.com/kotlin/flow) · [架构协程](https://developer.android.com/topic/libraries/architecture/coroutines) · [Lifecycle 包](https://developer.android.com/reference/kotlin/androidx/lifecycle/package-summary)

### 3.1 取消铁律

- ⚠️ **`CancellationException` 必须重抛，不可吞**（破坏结构化并发取消传播）。本项目 `PollingFlow.kt` 已修复：`catch (e: CancellationException) { throw e }`。
- `runInterruptible(context) { ... }`：包装阻塞 JVM 代码（`Process.waitFor` / `Thread.sleep` / 读流），使协程取消能中断底层线程。
- `withTimeout(duration, block)` / `withTimeoutOrNull(...)`：超时抛异常 / 返回 `null`。
- `SupervisorJob()`：子失败不取消兄弟；`CoroutineScope.cancel()`；`ensureActive()`（取消即抛）/ `isActive`；`yield()`；`NonCancellable`（清理用 `withContext(NonCancellable)`）。

### 3.2 `Dispatchers`

`Dispatchers.IO`（磁盘/网络/进程阻塞）、`Dispatchers.Default`（CPU 计算）、`Dispatchers.Main`（UI）。**注入便于测试**：Repository 接收 `CoroutineDispatcher` 参数，测试用 `Dispatchers.setMain(StandardTestDispatcher())`（需 `kotlinx-coroutines-test`）。

### 3.3 `Flow` / `StateFlow` / `SharedFlow`

- `flatMapLatest { }`：只取最新发射（轮询切换间隔场景）。
- `distinctUntilChanged()`（去重）、`debounce(ms)`（限频）、`conflate()`（丢中间值，轮询态优）。
- `stateIn(scope, started, initial)` / `shareIn(scope, started, replay)`；`SharingStarted.WhileSubscribed(5000)/Eagerly/Lazily`。

### 3.4 Android 生命周期感知（正确收集范式）

- `lifecycleScope`（lifecycle-runtime-ktx 2.2.0+）/`viewModelScope`（lifecycle-viewmodel-ktx 2.1.0+）随生命周期自动取消。
- `repeatOnLifecycle(state, block)`：**lifecycle-runtime-ktx 2.4.0+**；进入 `STARTED` 收集、`STOPPED` 取消，避免后台泄漏。
- `collectAsStateWithLifecycle(minActiveState = STARTED, context)`：`androidx.lifecycle.compose`（lifecycle-runtime-compose，**Lifecycle 2.7.0** 引入），Compose 中收集 Flow 的**官方推荐唯一正确范式**，默认 STARTED 收集、STOPPED 停止。
- 正确：
  ```kotlin
  // Activity/Fragment
  lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { vm.uiState.collect { ... } } }
  // Compose
  val state by vm.uiState.collectAsStateWithLifecycle()
  ```

---

## 4. 设备标识 (Build) / SoC / CPU

> 来源：[Build](https://developer.android.com/reference/android/os/Build) · [Build.VERSION_CODES](https://developer.android.com/reference/android/os/Build.VERSION_CODES)（官网 JS 渲染，字段底层属性取自 AOSP `Build.java`）

### 4.1 `android.os.Build` 字段（全部 `public static final` 只读）

| 字段 | 底层系统属性 | 引入 API | 说明 |
|------|------|------|------|
| `BOARD` | `ro.product.board` | 1 | 主板/PCB 名（如 `sdm845`） |
| `HARDWARE` | `ro.hardware` | 1 | 硬件名，常被 SoC 平台占用 |
| `DEVICE` | `ro.product.device` | 1 | 设计方案名 |
| `MODEL` | `ro.product.model` | 1 | 终端型号（如 `SM-G991B`） |
| `MANUFACTURER` | `ro.product.manufacturer` | 1 | 制造商 |
| `BRAND` / `PRODUCT` | `ro.product.brand/name` | 1 | 商业品牌 / 产品名 |
| `DISPLAY` / `FINGERPRINT` / `ID` / `TYPE` / `TAGS` | `ro.build.*` | 1 | build 标识 |
| `SUPPORTED_ABIS` | `ro.product.cpu.abilist` | **21** | 首选 ABI 列表（替代废弃 `CPU_ABI`） |
| `SOC_MANUFACTURER` | `ro.soc.manufacturer` | **31** | SoC 厂商（骁龙/天玑/Exynos） |
| `SOC_MODEL` | `ro.soc.model` | **31** | SoC 型号（如 `mt6983`） |

### 4.2 非公开系统属性与降级链（需反射 `SystemProperties` 或读 `/proc`/`build.prop`）

- `ro.board.platform`：SoC 平台代号**主判据**（如 `sm8150`=骁龙855、`mt6885`=天玑1000C、`exynos990`）。**非公开 API**，Build 不暴露，须反射 `android.os.SystemProperties.get()`（含 `@hide` 风险，须 try/catch）。
- `ro.soc.model`/`ro.soc.manufacturer`：API 31+ 公开字段；低版本不存在。
- `ro.hardware`/`ro.product.board`：厂商常写 SoC 名，作补充线索。
- **推荐降级链**：`SOC_MODEL`/`SOC_MANUFACTURER`（API≥31）→ `ro.board.platform`（反射）→ `HARDWARE`/`BOARD`（兜底）。本项目 `DeviceRepository` 已落地 `Build.SOC_MODEL` 最高优先级落点 + 天玑 `mt(67|68|69)\d{2}` 正则回退。

### 4.3 `Build.VERSION`

- `SDK_INT`（`int`）、`RELEASE`（`String`）。
- 判定范式：`if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { ... }`。

### 4.4 `Build.VERSION_CODES` 关键对照

| 常量 | 值 | 版本 |
|------|----|------|
| `SCOPED_STORAGE` | 29 | Android 10 |
| `S` | 31 | Android 12 |
| `TIRAMISU` | 33 | Android 13 |
| `UPSIDE_DOWN_CAKE` | 34 | Android 14 |
| `VANILLA_ICE_CREAM` | 35 | Android 15（**已发布/稳定**，本项目 targetSdk） |
| `BAKLAWA` | 36 | Android 16（**已正式发布 2025-06-10**，官方常量名） |
| *Android 17* | *37* | *API 37（**Beta 预览**，代号/常量名官方未定，勿臆测写入代码）* |

> ⚠️ **前瞻性**：Android 16 (`BAKLAWA`, API 36) 已正式发布，不再是"官网已列"。Android 17 (API 37) 截至 2026-07 处 Beta，官方**未公布**最终代号与常量名（民间指向 "Cinnamon Bun" 但非官方）。详见 §12、§13。

### 4.5 CPU 信息

官方**无公开 API**。读 `/proc/cpuinfo` 与 `/sys/devices/system/cpu/`（每核目录、`cpu0/cache/` 下 L1/L2/L3 `size`/`type`/`shared_cpu_map`）。限制：big.LITTLE 下 `processor` 计数可能不准，缓存明细以 sysfs 为准。本项目已实现骁龙 865(kona)/8s Gen3(sm8635) 缓存架构。

---

## 5. 内存 (RAM / SWAP / ZRAM)

> 来源：[ActivityManager.MemoryInfo](https://developer.android.com/reference/android/app/ActivityManager.MemoryInfo) · [ActivityManager](https://developer.android.com/reference/android/app/ActivityManager) · [Debug](https://developer.android.com/reference/android/os/Debug)

### 5.1 `ActivityManager.MemoryInfo`

- 公开：`availMem`(long, B)、`totalMem`(long, **API 16+**)、`threshold`(long, B)、`lowMemory`(boolean)。
- 隐藏 `@hide`（非公开）：`hiddenAppThreshold`、`secondaryServerThreshold`、`visibleAppThreshold`、`foregroundAppThreshold`。
- 获取：`ActivityManager.getMemoryInfo(MemoryInfo)`、`getMemoryClass()`(每 app 约限 MB)、`getLargeMemoryClass()`(**API 11+**)、`isLowRamDevice()`(**API 19+**)。

### 5.2 `android.os.Debug`

- `getMemoryInfo(Debug.MemoryInfo)`、`Debug.MemoryInfo.getMemoryStat(String)`（**API 23+**）。
- `getNativeHeapSize()`/`getNativeHeapAllocatedSize()`/`getNativeHeapFreeSize()`（long, B）。

### 5.3 `Runtime`

`getRuntime().maxMemory()`/`totalMemory()`/`freeMemory()`：Dalvik/ART 堆维度，**非整机 RAM**。

### 5.4 SWAP / ZRAM（非 SDK 公开）

- `/proc/meminfo`：整机内存真相源（含 `MemTotal` 等）。
- **SWAP/ZRAM 无公开 API**——需读 `/proc/swaps`、`/proc/meminfo` 的 Zram 行或 `/sys/block/zram0/`，属内核层、非 SDK。本项目概览内存卡片已展示 SWAP/ZRAM 子区域。

---

## 6. 传感器

> 来源：[传感器概览](https://developer.android.com/guide/topics/sensors/sensors_overview) · [SensorManager](https://developer.android.com/reference/android/hardware/SensorManager) · [Sensor](https://developer.android.com/reference/android/hardware/Sensor) · [SensorEvent](https://developer.android.com/reference/android/hardware/SensorEvent)

### 6.1 `SensorManager`（经 `getSystemService(SENSOR_SERVICE)` 获取）

- `Sensor getDefaultSensor(int type)`：默认传感器，**无则返回 null**。
- `List<Sensor> getSensorList(int type)`：按类型列出（`TYPE_ALL`）。
- `getDefaultSensor(int type, boolean wakeUp)`：**API 21+**。
- `registerListener(SensorEventListener, Sensor, int samplingPeriodUs)`；重载 `+Handler`(API 3)、`+maxReportLatencyUs`(API 19)、`+Executor`(API 30)；**务必在 `onPause()` 注销省电**。
- 采样率常量（速率码）：`SENSOR_DELAY_FASTEST=0`、`SENSOR_DELAY_GAME=1`、`SENSOR_DELAY_UI=2`、`SENSOR_DELAY_NORMAL=3`（概约 0 / 20k / 66.7k / 200k µs）。Android 12+ 运动传感器限流 200 Hz，超限需 `HIGH_SAMPLING_RATE_SENSORS`。

### 6.2 `Sensor` 类

`getName()`/`getVendor()`/`getVersion()`/`getType()`/`getMaximumRange()`/`getResolution()`/`getPower()`：**API 1**；`getMinDelay()`(API 9)；`getStringType()`(API 20+)；`getReportingMode()`/`isWakeUpSensor()`(**API 21+**)；`getId()`(**API 24**，非 35)；`getHighestDirectReportRateLevel()`(**API 26+**)。

### 6.3 `SensorEvent`

`float[] values`、`int accuracy`、`long timestamp`（**纳秒**，自开机 `elapsedRealtimeNanos` 时基）、`Sensor sensor`。

### 6.4 常用类型常量与 `values` 含义

| 常量 | 值 | 引入 | values 含义 |
|------|----|------|------------|
| `TYPE_ACCELEROMETER` | 1 | 1 | x/y/z m/s²（含重力） |
| `TYPE_MAGNETIC_FIELD` | 2 | 1 | x/y/z µT |
| `TYPE_GYROSCOPE` | 4 | 3 | x/y/z rad/s |
| `TYPE_LIGHT` | 5 | 1 | [0] lx |
| `TYPE_PRESSURE` | 6 | 3 | [0] hPa |
| `TYPE_PROXIMITY` | 8 | 1 | [0] cm |
| `TYPE_RELATIVE_HUMIDITY` | 12 | 14 | [0] % |
| `TYPE_AMBIENT_TEMPERATURE` | 13 | 14 | [0] °C |
| `TYPE_STEP_COUNTER` | 19 | 19 | [0] 自重启累计步数 |
| `TYPE_HEART_RATE` | 21 | 20 | [0] bpm |
| `TYPE_DYNAMIC_SENSOR_MOTION` | **32**（非 31） | 24 | 动态传感器事件 |
| `TYPE_LOW_LATENCY_OFFBODY_DETECT` | 34 | 26 | 佩戴/离体检测 |
| `TYPE_HINGE_ANGLE` | 36 | 30 | 折叠屏铰链角度° |
| `TYPE_HEAD_TRACKER` | 37 | **33** | 头部追踪姿态 |

### 6.5 易踩坑

- `getDefaultSensor()` 可能返回 **null**，须判空。
- **`TYPE_AMBIENT_TEMPERATURE` / `TYPE_RELATIVE_HUMIDITY` 多数手机无硬件**，缺失时 `values` 为 0/NaN。
- 心率需 `BODY_SENSORS`；后台还需 `BODY_SENSORS_BACKGROUND`（API 33+）。
- 权限被拒应**降级**：关闭对应卡片 / 显示「不可用」，勿崩溃。

---

## 7. 权限与隐私 (API 23–35)

> 来源：[请求权限](https://developer.android.com/training/permissions/requesting) · [权限使用说明](https://developer.android.com/training/permissions/usage-notes) · [Android 13 特性](https://developer.android.com/about/versions/13/features)

### 7.1 运行时权限基础（API 23+）

范式：`ContextCompat.checkSelfPermission` → `shouldShowRequestPermissionRationale`（教育 UI）→ `requestPermissions`/`ActivityResultLauncher` → `onRequestPermissionsResult`。

### 7.2 本项目相关权限

| 权限 | 引入 | 用途 |
|------|------|------|
| `BODY_SENSORS` | API 20 | 心率/体温传感器 |
| `BODY_SENSORS_BACKGROUND` | **API 33** | 后台读取传感器 |
| `ACTIVITY_RECOGNITION` | **API 29** | 计步相关 |
| `READ_PHONE_STATE` | API 1 | 设备/网络状态 |
| `POST_NOTIFICATIONS` | **API 33** | 通知权限 |
| `READ_MEDIA_IMAGES/VIDEO/AUDIO` | **API 33** | 细粒度媒体 |
| `BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT` | **API 31** | 附近蓝牙设备 |
| `NEARBY_WIFI_DEVICES` | **API 33** | 附近 Wi-Fi（不再强制定位） |

### 7.3 包可见性 `<queries>`（API 30+）

跨包访问（`queryIntentActivities`/`resolveActivity`/`getInstalledPackages`）结果被过滤，须 `<manifest><queries>` 显式声明。

---

## 8. Compose / Material3 / 主题 / 设计 Token

> 来源：[Material3](https://developer.android.com/develop/ui/compose/designsystems/material3) · [Compose 主题](https://developer.android.com/develop/ui/compose/theme) · [动态取色](https://developer.android.com/develop/ui/compose/designsystems/material3#dynamic-color) · [collectAsStateWithLifecycle](https://developer.android.com/reference/kotlin/androidx/lifecycle/compose/package-summary) · [Compose 性能](https://developer.android.com/develop/ui/compose/performance) · [WindowInsets](https://developer.android.com/develop/ui/compose/layouts/insets)

### 8.1 Material3 主题体系

- `MaterialTheme(colorScheme, typography, shapes)` 三件套。
- `ColorScheme` 字段全集：`primary/onPrimary/primaryContainer/onPrimaryContainer`、`secondary/onSecondary/...`、`tertiary/onTertiary/...`、`background/onBackground`、`surface/onSurface/surfaceVariant/onSurfaceVariant`、`surfaceTint`、`outline/outlineVariant`、`error/onError/errorContainer/onErrorContainer`、`inverseSurface/onInverseSurface/inversePrimary`、`scrim`。
- 构造器：`lightColorScheme(primary=…, …)` / `darkColorScheme(primary=…, …)`（参数均为 `Color` 角色，未传用默认）。
- **Design Token 单一真理源**：用 [Material Theme Builder](https://material.io/material-theme-builder) 导出 `Color.kt`（仅存 `Color` token，如 `md_theme_light_primary = Color(0xFF…)`）+ `Theme.kt`（`lightColorScheme/darkColorScheme`），组件统一经 `MaterialTheme.colorScheme.primary` 取色。本项目 `ui/theme/Color.kt` 即唯一颜色真理源，暗色霓虹/玻璃拟态主题在此定义。

### 8.2 动态取色 Dynamic Color（Material You / Monet）

- `dynamicLightColorScheme(context)` / `dynamicDarkColorScheme(context)`：**API 31+**。
- `isSystemInDarkTheme()` 判断深色；降级策略（低版本回退自定义 light/dark）：
  ```kotlin
  val dyn = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
  val colors = when {
      dyn && isSystemInDarkTheme() -> dynamicDarkColorScheme(ctx)
      dyn && !isSystemInDarkTheme() -> dynamicLightColorScheme(ctx)
      isSystemInDarkTheme() -> DarkScheme
      else -> LightScheme
  }
  ```

### 8.3 Shape & Typography

- `Shapes`：`extraSmall(4.dp)/small(8.dp)/medium(12.dp)/large(16.dp)/extraLarge(24.dp)`（均 `RoundedCornerShape`），经 `MaterialTheme.shapes.medium` 取用。
- `Typography`：`display*`/`headline*`/`title*`/`body*`/`label*`（Large/Medium/Small）。⚠️ **M3 `Typography` 无 `defaultFontFamily` 参数**，须在各自 `TextStyle.fontFamily` 指定。

### 8.4 状态收集

- `collectAsStateWithLifecycle(value, lifecycleOwner=LocalLifecycleOwner.current, minActiveState=STARTED, context)`：`androidx.lifecycle.compose`（Lifecycle 2.7.0 引入），内部用 `repeatOnLifecycle`，Compose 收集 Flow 的官方推荐范式。
- 与 `collectAsState()` 区别：后者无生命周期感知、后台持续收集；前者 STOPPED 暂停。
- ViewModel：`viewModel()`（androidx.lifecycle.viewmodel.compose）；Koin 用 `koinViewModel()`。

### 8.5 性能

- `remember` 缓存昂贵计算；`derivedStateOf` 限制高频状态重组；稳定性 `stable`/`@Stable` 与 **StrongSkipping**（Compose 1.5+）。
- `LazyColumn` 用稳定 `key` 复用项；`Modifier` 顺序影响（先 `padding`/`clickable` 再 `background`）；**BaselineProfile** 预编译关键路径。
- `WindowInsets`：`Modifier.imePadding()`、`navigationBarsPadding()`、`systemBarsPadding()`（官方内置，accompanist-insets 已弃用迁移）。

### 8.6 Compose 编译器 ↔ Kotlin 版本耦合（前瞻性）

- **Kotlin ≥ 2.0 起，Compose 编译器已并入 Kotlin 仓库**，版本号 = Kotlin 版本号，通过 `org.jetbrains.kotlin.plugin.compose` Gradle 插件启用，**不再有独立 Compose 编译器版本、也无需核对兼容表**（旧 `composeOptions { kotlinCompilerExtensionVersion }` 已废弃）。
- **本项目绑定关系（AGP 9 实战结论）**：AGP 9.0 启用**内置 Kotlin**，版本由 AGP 9.0.1 固定为 **2.2.10**（含 Compose 编译器 2.2.10），**禁止显式 apply `org.jetbrains.kotlin.android` 插件**（硬报错）。`org.jetbrains.kotlin.plugin.compose` 仍需显式 apply 且版本须 = 内置 Kotlin（**2.2.10**）。**编译器与 Kotlin 版本必须一致**。Kotlin 2.4.10 + Compose 编译器 2.4.10 需 **AGP 9.1+**（内置 Kotlin 抬升到 2.4.x）。
- **Material3 版本**：稳定版 `1.4.0`（2026-07-15）；预览 `1.5.0-alpha24`。**Material 3 Expressive** 截至 2026-07 **仍仅以 `ExperimentalMaterial3ExpressiveApi` 形式随 `1.5.0-alphaXX` 演进，未独立稳定、未纳入稳定 BOM**——生产代码勿依赖。
- ⚠️ **前瞻告警**：Compose `1.12.x`（BOM `2026.06.00`）要求 **Kotlin/Compose 编译器 2.4.x**，而 AGP 9.0.1 内置 Kotlin 仅 2.2.10 → **BOM 2026.06.00 在 AGP 9.0.1 下不可达**。AGP 9 最大支持 API **36.1**（无 compileSdk 37）。升级到 BOM 2026.06.00 须先升 **AGP 9.1+**（内置 Kotlin 2.4.x），见 §13 #7。

---

## 9. Koin 依赖注入 / DataStore 持久化

> 来源：[Koin Android 快速入门](https://insert-koin.io/docs/quickstart/android) · [Koin Compose](https://insert-koin.io/docs/quickstart/android-compose/) · [DataStore](https://developer.android.com/topic/libraries/architecture/datastore) · [ViewModel in Compose](https://developer.android.com/topic/libraries/architecture/viewmodel#compose)

### 9.1 Koin（本项目 3.5.6）

- 依赖：`io.insert-koin:koin-android:3.5.6` + `io.insert-koin:koin-androidx-compose:3.5.6`（Koin 4.x 改 `koin-compose`，3.5.6 仍用 `koin-androidx-compose` 的 `koinViewModel()`）。
- 启动：
  ```kotlin
  class App : Application() {
      override fun onCreate() {
          super.onCreate()
          startKoin { androidLogger(); androidContext(this@App); modules(appModule) }
      }
  }
  ```
- 模块：`module { single<Repo> { RepoImpl() }; viewModel { UserVM(get()) } }`；注入 `private val repo: Repo by inject()` / `get()`。
- Compose VM：`val vm: UserVM = koinViewModel()`（来自 `koin-androidx-compose`）；导航共享用 `getViewModel()`。
- 🔮 **kotlinx-coroutines 前瞻**：项目用 `1.8.1`；最新稳定 **`1.11.0`**（2026-05-07），`1.10.2`（2025-04）为与 Kotlin 2.1.0 匹配的稳妥点。`1.10.x` 重组了 `kotlinx-coroutines-debug` 包路径（升级注意）。建议先升到 `1.10.2` 验证兼容，再追 `1.11.0`。

### 9.2 DataStore

- **Preferences**：
  ```kotlin
  val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
  val key = intPreferencesKey("k")
  val flow: Flow<Int> = dataStore.data.map { it[key] ?: 0 }   // 读
  suspend fun save(v: Int) = dataStore.edit { it[key] = v }    // 写
  ```
- **Proto**：`by dataStore(fileName, serializer)` + `Serializer<T>`，类型安全。
- vs SharedPreferences：后者同步阻塞主线程、无一致性保证；DataStore 基于**协程 + Flow**、事务（`updateData`）、可迁移。
- **本项目 AppSettings 范式**：用 `preferencesDataStore` 暴露 `Flow<AppSettings>`，`collectAsStateWithLifecycle()` 驱动深色/霓虹主题切换，写用 `edit{}` 持久化用户偏好。

---

## 10. 构建 / 发布 / 签名 / 缩减 / 脱糖

> 来源：[配置应用模块](https://developer.android.com/build/configure-app-module) · [缩减代码](https://developer.android.com/build/shrink-code) · [Java 8+ 支持/脱糖](https://developer.android.com/studio/write/java8-support) · [BaselineProfile](https://developer.android.com/topic/performance/baselineprofiles) · [应用签名](https://developer.android.com/studio/publish/app-signing)

### 10.1 模块构建配置

- `compileSdk`(35)：仅决定编译时可用 API，不改变运行行为。`targetSdk`(35)：目标运行平台行为。`minSdk`(21)：可安装最低版本。
- `buildToolsVersion`：AGP 通常自动推断。`ndkVersion`：仅含原生代码时需要。
- `buildTypes`：`debug`/`release`。`isMinifyEnabled` → R8 缩减+混淆；`isShrinkResources` → 移除未引用资源（须先开 minify）。`proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")`。
- `applicationId`：商店唯一标识（发布后不可改）。`namespace`：生成的 `R`/`BuildConfig` 包名。`versionCode`（整数递增）/`versionName`（展示串）。
- 🔮 **工具链前瞻**：本项目 AGP **8.6.0**（Kotlin 插件 2.1.0、Compose 插件 2.1.0）。最新 **AGP 9.0.1**（2026-01）需 **Gradle 9.1 + JDK 17**；且 Compose `1.12.0+`/BOM `2026.06.00` 硬性要求 AGP 9。升级工具链属大跃迁，见 §13 #7。

### 10.2 签名

- `signingConfigs { create("release") { storeFile=file(...); storePassword=...; keyAlias=...; keyPassword=... } }` + `buildTypes.release.signingConfig = signingConfigs["release"]`。
- 方案：`v1SigningEnabled`(JAR) / `v2SigningEnabled`(**Android 11 起要求 v2+**) / `v3SigningEnabled`(支持密钥轮转 lineage)。
- **Play App Signing**：上传密钥（upload key，可重置）≠ 应用签名密钥（Google 托管不可取回）。
- **GitHub Actions 范式**：用 `secrets` 注入；`base64 -d` 还原 `.jks` 到工作区，配合 `keystore.properties`（不入源码库）传给 Gradle，避免明文。本项目 CI `Android Release Build` 已采用此范式产出签名 APK。

### 10.3 R8 / 缩减

- `minifyEnabled`→代码树摇+混淆；`shrinkResources`→移除无用资源。规则：`-keep`（保留类+成员）、`-keepclassmembers`（仅成员）、`-dontwarn`（抑制未知引用告警）。
- **整体 `-keep` 致零缩减**：放弃体积/启动/ANR 收益。精细化用 R8 Configuration Analyzer、按包细化 keep。
- `mapping.txt`（还原混淆栈）惯例在 `build/outputs/mapping/<variant>/`。

### 10.4 coreLibraryDesugaring（重要澄清）

- 启用：`compileOptions { isCoreLibraryDesugaringEnabled = true; sourceCompatibility = JavaVersion.VERSION_1_8 }` + `coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.3")`。
- 覆盖：`java.util.stream`、`java.time` 子集、`java.util.function`、`Optional`、`ConcurrentHashMap` 修复、`java.nio.file` 子集（AGP 7.4+）。
- ⚠️ **澄清：`Process.waitFor(timeout)`、`destroyForcibly()`、`isAlive()` 不在脱糖表中——不被覆盖！** 这些方法需 API 26+，低版本必须自建超时范式（见 §2.1）。本项目未启用脱糖，故 `ShellCommandDataSource` 原 `waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)` 在低版本会 `NoSuchMethodError`——已用 `waitForWithTimeout()` 替换。

### 10.5 Compose BOM / BaselineProfile

- **BOM**：`platform("androidx.compose:compose-bom:2024.12.01")` 统一 Compose 库版本（**编译器自 Kotlin 2.0 起已并入 Kotlin，不在 BOM 内**，见 §8.6）。
- **BaselineProfile**：`BaselineProfileRule`（Macrobenchmark）`collect()` 采集；`./gradlew app:generateBaselineProfile`；`profileinstaller` 于安装/首启写入。建议 release `isMinifyEnabled=true`（AGP 8.2+ R8 重写规则）。收益：首次启动代码执行 ~30% 提速，Startup Profiles 再 +~15%。
- 🔮 **BOM 前瞻**：最新稳定 BOM **`2026.06.00`**（对应 Compose `1.12.x`）需 **Kotlin 2.4.x**（AGP 9.1+ 内置）；AGP 9.0.1 内置 Kotlin 2.2.10 下项目用 **`2025.06.00`**（Compose 1.8.x）。Material3 稳定版 `1.4.0`，Material 3 Expressive 仍未稳定（§8.6）。

---

## 11. 国际化 (i18n) 与本地化

> 来源：[每应用语言](https://developer.android.com/guide/topics/resources/app-languages) · [本地化](https://developer.android.com/guide/topics/resources/localization) · [提供资源](https://developer.android.com/guide/topics/resources/providing-resources) · [字符串资源](https://developer.android.com/guide/topics/resources/string-resource)

### 11.1 资源限定符与优先级

- 优先级（高→低）：MCC/MNC > 语言/脚本/区域 > 性别 > 宽色域 > HDR > UI 模式 > 夜间(`-night`) > 密度 …
- 区域写法：`values-zh-rCN`（简体中国）、`values-zh-rTW`（繁体台湾），**`r` 前缀必须**；`b+zh+Hant`（BCP 47 脚本限定符，API 24+）。
- ⚠️ **默认 `values` 必须完整**：缺失任一字符串在不支持语言设备上崩溃（Force Close）。本项目已有三语 `strings.xml`（values / values-zh-rCN / values-zh-rTW）。

### 11.2 字符串最佳实践

- 不硬编码任何用户可见字符串，全部声明于 `strings.xml`；Compose 经 `stringResource(R.string.xxx)` 引用。
- **plurals**：`zero/one/two/few/many/other`，中文恒用 `other`；`getQuantityString` / Compose `pluralStringResource`。
- 非译片段用 `<xliff:g id="..." example="...">` 保护（需 `xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2"`）。
- 用 `tools:ignore` 抑制 lint 缺失翻译告警。

### 11.3 每应用语言（API 33+）

- `LocaleManager`（framework，API 33）或 `AppCompatDelegate.setApplicationLocales(LocaleListCompat)`（AndroidX AppCompat 1.6+）。
- AGP 8.1+ `androidResources { generateLocaleConfig = true }` 自动生成 `LocaleConfig`；`resources.properties` 设 `unqualifiedResLocale`。

---

## 12. 各 API 级别行为变更（对监控类 App 的影响）

> 来源：[Android 11](https://developer.android.com/about/versions/11/behavior-changes-11) · [12](https://developer.android.com/about/versions/12/behavior-changes-12) · [13](https://developer.android.com/about/versions/13/behavior-changes-13) · [14](https://developer.android.com/about/versions/14/behavior-changes-14) · [15](https://developer.android.com/about/versions/15) · [16](https://developer.android.com/about/versions/16) · [17](https://developer.android.com/about/versions/17) · [非 SDK 限制](https://developer.android.com/guide/app-compatibility/restrictions-non-sdk-interfaces) · [non-sdk-16](https://developer.android.com/about/versions/16/changes/non-sdk-16)

### 12.1 已发布版本（本项目需重点关注）

- **API 31 (Android 12)**：`SCHEDULE_EXACT_ALARM` 精确闹钟权限（设置→特殊应用访问→闹钟和提醒）；后台启动 FGS 受限；通知 trampoline 被禁；**应用休眠**（数月不交互权限自动重置）。
- **API 33 (Android 13)**：`POST_NOTIFICATIONS` 运行时权限（拒绝后前台服务通知不显示在抽屉，仍显示在任务管理器）；`NEARBY_WIFI_DEVICES` 取代 Wi-Fi 定位权限；细粒度媒体权限。
- **API 34 (Android 14)**：前台服务**必须**声明 `android:foregroundServiceType`（`FGS_TYPE_DATA_SYNC` 等，新增 `health`/`remoteMessaging`/`shortService`/`specialUse`/`systemExempted`），否则崩溃；JobScheduler 须 `ACCESS_NETWORK_STATE`。
- **API 35 (Android 15，本项目 targetSdk)**：
  - **Edge-to-edge 强制**：`statusBarColor`/`navigationBarColor`(手势)/`setDecorFitsSystemWindows` **废弃并失效**，默认全面屏；仪表盘/悬浮读数须用 `WindowInsets` 避让状态栏/挖孔。
  - **前台服务类型收紧**：新增 `mediaProcessing`（6h/24h 上限、`BOOT_COMPLETED` 不可启动）；`dataSync` 同 6h 上限且开机不可拉起 → 后台同步健康数据须换 `WorkManager`。
  - **OpenJDK 核心库对齐**：`SequencedCollection`/`List.removeFirst()` 与 Kotlin 冲突易 `NoSuchMethodError`（**minSdk≤34 须用 `removeAt(0)`**）；`String.format` 更严、`Arrays.asList().toArray()` 返回 `Object[]`。
  - **停止态（Force Stop）**：进入 stopped 即取消**所有 PendingIntent**、禁用小组件；`ApplicationStartInfo.wasForceStopped()` 可诊断。
  - **预测性返回**：开发者选项移除，已 opt-in 应用显示系统动画。
- **API 36 (Android 16，已正式发布 2025-06-10)**：
  - **ART 运行时更新**（Play System 推送到 12+）：依赖内部 ART 结构/非 SDK 的库可能崩溃。
  - **隐私/安全加固**：默认防御 Intent 重定向攻击；Companion 应用不再收到 `RESULT_DISCOVERY_TIMEOUT`（位置隐私）。
  - **🔴 健康权限迁移（target 36）**：`BODY_SENSORS`/`BODY_SENSORS_BACKGROUND` 被 `android.permission.health.*`（如 `READ_HEART_RATE`、`READ_HEALTH_DATA_IN_BACKGROUND`）取代；`FOREGROUND_SERVICE_TYPE_HEALTH` 需对应健康权限；**移动端须声明隐私政策 Activity，否则权限被撤**。本项目心率/体温卡片须迁移。
  - **大屏自适应强制（target 36）**：`sw≥600dp` 上 `screenOrientation`/`resizableActivity`/`setRequestedOrientation()` **被忽略**，应用填满窗口；临时退路 `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY`（**API 37 移除**）。
  - **Edge-to-edge 退路移除**：`windowOptOutEdgeToEdgeEnforcement` 废弃并失效（target 36 不可退出）。预测性返回默认开启（可 `enableOnBackInvokedCallback=false` 退出）。
  - **废弃/移除**：`setImportantWhileForeground` 忽略（`isImportantWhileForeground()` 返 false）；`announceForAccessibility`/`TYPE_ANNOUNCEMENT` 废弃；`MediaStore.getVersion()` 改为应用唯一串；`scheduleAtFixedRate` 最多补 1 次。

### 12.2 前瞻版本（Beta / 路线图）

- **API 37 (Android 17，Beta 预览)**：
  - **IME 可见性**：旋转/配置变更未处理时不再恢复键盘，须显式请求。
  - **隐私/安全**：SMS OTP（WebOTP）对非目标应用延迟 3 小时；每应用 Keystore 上限 **5 万键**（target 37+，超限 `ERROR_TOO_MANY_KEYS`）；跨 Profile 回环流量默认拒绝；`usesCleartextTraffic` 计划废弃；隐式 URI 授权将于 **Android 18** 移除（17 起 `StrictMode.detectImplicitUriPermissionGrant()` 可探测）。
  - **后台音频硬化（target 37）**：前台服务需 `while-in-use` 能力（精确闹钟+`USAGE_ALARM` 除外），否则静默失败。
  - 健康/传感器、非 SDK 限制：官方页未明确 → **未定/待发布**。
- **Google Play targetSdk 要求时间线**：
  - **2026-08-31 起**：新应用/更新须 **target API 36（Android 16）+**；既有应用须 **target API 35+** 才能触达更高系统新用户（Wear/Auto 35+，TV/XR 34+）。
  - 可申延至 **2026-11-01**。
  - **本项目压力**：`targetSdk 35` 目前仍能服务新用户，但 **2026-08-31 后提交更新必须升到 36**——需在此前完成健康权限迁移、大屏自适应、edge-to-edge、预测性返回改造（见 §13）。
- **非 SDK 接口限制演进**：分黑名单/深灰/浅灰名单，每版持续收紧。`android.os.SystemProperties`（@hide 隐藏 API，本项目 SoC 识别反射调用）属非 SDK，受 `greylist-max-*` 约束，随版本升级逐步不可访问；建议改用公开替代（`Build` 字段、`DeviceConfig` 或申请公开 API），并接入 StrictMode/veridex 检测。Android 16 已更新受限列表（[non-sdk-16](https://developer.android.com/about/versions/16/changes/non-sdk-16)）。
- **包可见性 `<queries>`**（API 30+）：跨包查询被过滤，须显式声明。
- **Scoped Storage**（API 29 / 强制 30）：外部存储仅限应用专属目录+媒体；读系统文件（sysfs/proc）通常**不需**存储权限，但受 SELinux/文件读权限约束（官网未专门说明）。
- **后台限制**（API 29+）：后台启动 Activity 受限；受限电池状态不投递 `BOOT_COMPLETED`——监控类需求应结合前台服务或 `setExactAndAllowWhileIdle`（受 9 分钟限频）。

---

## 13. 版本前瞻与升级路线图（本项目行动项）

> 基于 §0–§12 的前瞻性结论，汇总本项目需在 2026 年内落地的升级动作。优先级：🔴 阻塞商店更新 / 🟠 强相关 / 🟡 可选。

| # | 优先级 | 事项 | 触发条件 | 影响范围 |
|---|--------|------|----------|----------|
| 1 | 🔴 | **targetSdk 35 → 36** | 2026-08-31 商店更新硬性要求（可延 2026-11-01） | 全量；触发 §12.1 全部 36 变更 |
| 2 | 🔴 | **健康权限迁移** `BODY_SENSORS` → `android.permission.health.READ_HEART_RATE` 等 + 隐私政策 Activity | target 36 | 心率/体温传感器卡片 |
| 3 | 🟠 | **Edge-to-edge 改造**：移除 `setDecorFitsSystemWindows`、用 `WindowInsets` 避让状态栏/挖孔 | target 35 已强制、36 退路移除 | 概览/详情页全屏布局 |
| 4 | 🟠 | **大屏自适应**：`sw≥600dp` 下 `screenOrientation` 失效，需响应式布局 | target 36 | 平板/折叠屏 |
| 5 | 🟠 | **预测性返回**：接入 `OnBackInvokedCallback` | target 35+ 默认开启 | 返回手势 |
| 6 | ✅ | **Kotlin 2.1.0 → 2.2.10（AGP 9 内置）**：已迁移到内置 Kotlin，移除 kotlin-android 插件；Compose 编译器 2.2.10 | 2026-07-20 已完成 | 全工程 |
| 7 | 🟡 | **Compose BOM 2024.12.01 → 2025.06.00（已完成）；2026.06.00 受限于 AGP 9.0.1 内置 Kotlin 2.2.10，需 AGP 9.1+ 方达** | AGP 9.1 后追 2026.06.00 | Compose 全部 |
| 8 | ✅ | **kotlinx-coroutines 1.8.1 → 1.11.0** | 2026-07-20 已完成 | 协程层 |
| 9 | 🟡 | **非 SDK 依赖替换**：`SystemProperties` 反射 → 公开 `Build`/`DeviceConfig` 或申请 API | 长期抗版本收紧 | SoC 识别 |
| 10 | 🟡 | **非 SDK 检测**：接入 StrictMode / veridex 定期扫描 | 持续 | 全工程健壮性 |

> **升级顺序建议**：先在不升 targetSdk 的前提下做 Kotlin/Compose/BOM 库版本升级（低风险）→ 再做 edge-to-edge / 预测性返回 / 大屏自适应（target 35 已部分强制）→ 最后冲 targetSdk 36 + 健康权限迁移（绑定 2026-08-31 deadline）。每一步单独验证、单独提交。
>
> **✅ 第一阶段已完成（2026-07-20，CI 绿灯）**：AGP 8.6.0→9.0.1 / Gradle 8.7→9.1 / 内置 Kotlin 2.1.0→2.2.10 / BOM 2024.12.01→2025.06.00 / coroutines 1.8.1→1.11.0 / compileSdk 35→36（对应 §13 #6/#8 已完成、#7 部分完成）。**关键约束（实战验证）**：AGP 9 强制内置 Kotlin 且禁止显式 apply kotlin-android 插件；内置 Kotlin 版本由 AGP 9.0.1 锁为 2.2.10；BOM 2026.06.00（需 Kotlin 2.4.x）在 AGP 9.0.1 下不可达，须等 AGP 9.1+。下一阶段 = targetSdk 36 + 健康权限迁移（#1/#2），绑定 2026-08-31。

---

## 附：官方参考来源索引

| 领域 | 主要官方来源 |
|------|------------|
| 电池/电源 | [BatteryManager](https://developer.android.com/reference/android/os/BatteryManager) · [电池监控](https://developer.android.com/training/monitoring-device-state/battery-monitoring) · [Doze](https://developer.android.com/training/monitoring-device-state/doze-standby) · [PowerManager](https://developer.android.com/reference/android/os/PowerManager) · AOSP `frameworks/base/core/java/android/os/BatteryManager.java` |
| 进程/Shell | [Process](https://developer.android.com/reference/java/lang/Process) · [ProcessBuilder](https://developer.android.com/reference/java/lang/ProcessBuilder) · [Runtime](https://developer.android.com/reference/java/lang/Runtime) |
| 协程/Flow | [Android 协程](https://developer.android.com/kotlin/coroutines) · [取消与超时](https://kotlinlang.org/docs/cancellation-and-timeouts.html) · [Flow](https://developer.android.com/kotlin/flow) · [架构协程](https://developer.android.com/topic/libraries/architecture/coroutines) |
| Build/SoC | [Build](https://developer.android.com/reference/android/os/Build) · [VERSION_CODES](https://developer.android.com/reference/android/os/Build.VERSION_CODES) |
| 内存 | [MemoryInfo](https://developer.android.com/reference/android/app/ActivityManager.MemoryInfo) · [ActivityManager](https://developer.android.com/reference/android/app/ActivityManager) · [Debug](https://developer.android.com/reference/android/os/Debug) |
| 传感器 | [传感器概览](https://developer.android.com/guide/topics/sensors/sensors_overview) · [SensorManager](https://developer.android.com/reference/android/hardware/SensorManager) · [Sensor](https://developer.android.com/reference/android/hardware/Sensor) |
| 权限 | [请求权限](https://developer.android.com/training/permissions/requesting) · [使用说明](https://developer.android.com/training/permissions/usage-notes) |
| Compose/M3 | [Material3](https://developer.android.com/develop/ui/compose/designsystems/material3) · [主题](https://developer.android.com/develop/ui/compose/theme) · [动态取色](https://developer.android.com/develop/ui/compose/designsystems/material3#dynamic-color) · [性能](https://developer.android.com/develop/ui/compose/performance) · [Compose 编译器](https://developer.android.com/develop/ui/compose/compiler) · [BOM](https://developer.android.com/develop/ui/compose/bom) · [WindowInsets](https://developer.android.com/develop/ui/compose/layouts/insets) |
| Kotlin 版本 | [Releases](https://kotlinlang.org/docs/releases.html) · [What's new 2.2](https://kotlinlang.org/docs/whatsnew22.html) · [Context Parameters](https://kotlinlang.org/docs/context-parameters.html) |
| Koin/DataStore | [Koin Android](https://insert-koin.io/docs/quickstart/android) · [Koin Compose](https://insert-koin.io/docs/quickstart/android-compose/) · [DataStore](https://developer.android.com/topic/libraries/architecture/datastore) · [ViewModel Compose](https://developer.android.com/topic/libraries/architecture/viewmodel#compose) |
| 构建/签名 | [配置模块](https://developer.android.com/build/configure-app-module) · [缩减](https://developer.android.com/build/shrink-code) · [脱糖](https://developer.android.com/studio/write/java8-support) · [BaselineProfile](https://developer.android.com/topic/performance/baselineprofiles) · [签名](https://developer.android.com/studio/publish/app-signing) · [AGP 9.0](https://developer.android.com/build/releases/agp-9-0-0-release-notes) |
| i18n | [每应用语言](https://developer.android.com/guide/topics/resources/app-languages) · [本地化](https://developer.android.com/guide/topics/resources/localization) |
| 版本/行为变更 | [11](https://developer.android.com/about/versions/11/behavior-changes-11) · [12](https://developer.android.com/about/versions/12/behavior-changes-12) · [13](https://developer.android.com/about/versions/13/behavior-changes-13) · [14](https://developer.android.com/about/versions/14/behavior-changes-14) · [15](https://developer.android.com/about/versions/15) · [16](https://developer.android.com/about/versions/16) · [17](https://developer.android.com/about/versions/17) · [非 SDK 限制](https://developer.android.com/guide/app-compatibility/restrictions-non-sdk-interfaces) · [non-sdk-16](https://developer.android.com/about/versions/16/changes/non-sdk-16) · [Play targetSdk](https://developer.android.com/distribute/best-practices/develop/target-sdk) |

> 本知识包为单一事实源，关键结论（尤其 §1.2 `BATTERY_PROPERTY_CAPACITY` 系百分比、§10.4 脱糖不覆盖 `Process.waitFor(timeout)`、§6.4 常量精确值）已与官方/AOSP 核对；前瞻性章节（§0 前瞻摘要、§4.4、§8.6、§12.2、§13）基于 2026-07 最新官方信息整理，Beta/路线图项已标注「未定/预览」。开发前先查对应章节，避免凭经验臆测。
