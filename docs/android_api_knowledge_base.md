# Android 官方知识基线（技术债修复前置检索）

**日期**：2026-07-20
**范围**：电池容量读取 · `waitFor` 超时 · 协程轮询与取消 · SoC 识别 · i18n
**目的**：在动手修技术债前，先以 Android 开发者官网 / AOSP 源码 / Kotlin 官方文档为准绳建立事实基线，避免凭经验臆测。后续修复应逐条回链本表。

---

## 0. 项目约束（来自 `app/build.gradle`，决定可用 API 面）

| 项 | 值 | 对修复的影响 |
|----|----|----|
| `minSdk` | **21** | `Build.SOC_MODEL`/`SOC_MANUFACTURER`(API31)、`Process.waitFor(timeout)`(API26) 在 21–25/21–30 设备**不可用** → 必须降级路径或脱糖 |
| `compileSdk`/`targetSdk` | **35** | 可用全部最新 API，但运行时仍需按 `minSdk` 分支 |
| Kotlin | **2.1.0** | `kotlinx-coroutines` 支持 `withTimeoutOrNull`/`runInterruptible` |
| 已用参考范式 | `ShellCommandDataSource:54` `waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)` | ⚠️ 该 API 为 API26+，minSdk21 下仅当启用 coreLibraryDesugaring 才安全 |

---

## 1. 电池容量 / 电荷量

**对应债务**：`charge_full` 单位归一化 P0、设计容量误赋值

### 1.1 官方常量与单位（来源：AOSP `frameworks/base/core/java/android/os/BatteryManager.java`）

| 常量 | 值 | 单位 | 含义 |
|------|----|------|------|
| `BATTERY_PROPERTY_CHARGE_COUNTER` | 1 | **microampere-hours (µAh)** | 电池电荷量计数 |
| `BATTERY_PROPERTY_CURRENT_NOW` | 2 | **microamperes (µA)** | 瞬时电流，正=充入/负=放出 |
| `BATTERY_PROPERTY_CURRENT_AVERAGE` | 3 | µA | 平均电流（硬件定义周期） |
| `BATTERY_PROPERTY_CAPACITY` | 4 | **百分比 (%)** | **剩余容量百分比，无小数** |
| `BATTERY_PROPERTY_ENERGY_COUNTER` | 5 | **nanowatt-hours (nWh)** | 剩余能量 |
| `BATTERY_PROPERTY_STATUS` | 6 | — | 充电状态枚举 |
| `BATTERY_PROPERTY_STATE_OF_HEALTH` | 10 | **百分比 (%)** | 官方 SoH%（剩余满充容量 / 额定容量），`@FlaggedApi` 较新 Android |

> 全部经 `getIntProperty()` / `getLongProperty()` / `getStringProperty()` 读取。

### 1.2 关键纠正（直接命中现有代码）

- ⚠️ **`BATTERY_PROPERTY_CAPACITY` 是百分比，不是 mAh。** 项目 `BatteryDataSource.readBatteryCapacity` step 1：
  ```kotlin
  val capacity = it.getLongProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
  if (capacity != Long.MIN_VALUE && capacity > 0) info.capacityDesignMAh = capacity
  ```
  **这是误赋值**——会把 0–100 的百分比塞进本应存 mAh 的 `capacityDesignMAh`，污染后续 SoH / 设计容量展示。设计容量应改从 `charge_full_design` sysfs 或 `power_profile.xml` 获取；该常量最多只应作为 `levelPercent` 的冗余校验。
- ✅ `BATTERY_PROPERTY_CHARGE_COUNTER` 单位为 µAh，`/ 1000 → mAh` 正确（与项目 `chargeCounterUAh / 1000` 一致）。
- ✅ `BATTERY_PROPERTY_CURRENT_NOW` 为 µA，与项目 `currentNowUA` 口径一致。
- sysfs `/sys/class/power_supply/battery/charge_full[|_design]` 按 Linux **power supply class** 规范为 **µAh**，`/ 1000 → mAh` 是标准归一化；但**部分 OEM 内核以 mAh 或库仑上报**，导致 1000× 偏差——这正是 `val mah = value / 1000`（~line 417）"无单位归一化" P0 的本质：缺合理性边界校验。
- 官方 SoH 信号 `BATTERY_PROPERTY_STATE_OF_HEALTH` 已存在（值 10），但 `@FlaggedApi` 非全量可用；手动 `charge_full / charge_full_design` 比率法仍是当前主力，可保留并补充官方值优先。

### 1.3 修复方向（待执行）

- 移除 step 1 对 `capacityDesignMAh` 的误赋值；`BATTERY_PROPERTY_CAPACITY` 仅用于交叉校验 `levelPercent`。
- 对 sysfs `charge_full`/`charge_full_design` 归一化结果做边界 sanity check：典型手机电芯 ≈ **2000–7000 mAh**；若 `/1000` 后超出该量级 1000×，则反向乘/除 1000 纠正（或检测原始值是否已在 mAh 量级）。
- 优先采用 `BATTERY_PROPERTY_STATE_OF_HEALTH`（API 可达时）覆盖手动 SoH。

---

## 2. `waitFor` 超时

**对应债务**：13 处 `proc.waitFor()` 无超时（易死锁 / ANR）

### 2.1 官方事实

- `Process.waitFor()`（无参）→ **永久阻塞**，直到进程退出。minSdk 21 下唯一可用。
- `Process.waitFor(long timeout, TimeUnit unit)` → **新增于 API 26 (Android 8.0)**。
- `Process.destroyForcibly()` → 同 API 26，强制杀进程；`destroy()` 为请求式优雅终止。

### 2.2 项目现状（grep 实测，与辩证审计"13 处"吻合）

| 文件:行 | 调用 | 有无超时 |
|---------|------|---------|
| `BaseSysFsDataSource.kt:28` | `process.waitFor()` | ❌ |
| `BatteryDataSource.kt` | 699 / 730 / 956 / 1063 / 1298 / 1323 / 1380 / 1407 / 1604 | ❌ ×9 |
| `DeviceDetailDataSource.kt:1652` | `proc.waitFor()` | ❌ |
| `GpuDataSource.kt:803` | `try { proc.waitFor() }` | ❌ |
| `SystemDataSource.kt:108` | `proc.waitFor()` | ❌ |
| `ShellCommandDataSource.kt:54` | `process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)` | ✅ 参考范式（但 API26+） |
| `BatteryDataSource.kt:811-815` | `waitForDeclaredService`（ServiceManager，非进程） | — 不计入 |

### 2.3 修复方向（官方兼容）

- **通用安全范式**：用 `ExecutorService`/`Future.get(timeout)` 包裹阻塞 `waitFor()`，超时后 `destroy()`/`destroyForcibly()`；或命令前缀加 shell `timeout`。
  ```kotlin
  val future = executor.submit { process.waitFor() }
  try { future.get(30, TimeUnit.SECONDS) }
  catch (e: TimeoutException) { process.destroyForcibly() /* API26+；低版本用 destroy() */ }
  ```
- 若启用 `coreLibraryDesugaring`，可统一改用 `waitFor(timeout, unit)`；否则需分 API 分支（API ≥ 26 用原生，< 26 用 Future 范式）。
- 读取 stdout/stderr 需并行消费，避免子进程因管道写满而挂起（经典死锁诱因）。

---

## 3. 协程轮询与取消

**对应债务**：`PollingFlow` 吞 `CancellationException`

### 3.1 官方规则（Kotlin `cancellation-and-timeouts` + Android `coroutines` 指南）

- `withTimeout` / `withTimeoutOrNull` 做有界等待；`withTimeoutOrNull` **超时返回 `null` 不抛异常**。
- **`CancellationException` 绝不可吞**：捕获后必须 `throw e` 重抛，否则破坏结构化并发的取消传播。
- 阻塞 JVM 代码（`Process.waitFor` / `Thread.sleep`）用 `runInterruptible { }`，使协程取消能中断底层线程。
- 长循环用 `ensureActive()` / `isActive` 检查取消；不可取消的清理用 `withContext(NonCancellable)`。
- Android：`viewModelScope` 自动随 `ViewModel` 销毁取消；收集应 `repeatOnLifecycle` / 生命周期感知；阻塞读取放 `Dispatchers.IO`；注入 `Dispatchers` 便于测试。

### 3.2 项目现状（`PollingFlow.kt`）

```kotlin
scope.launch(context) {
    intervalFlow.flatMapLatest { delayMs ->
        flow { while (isActive) {
            val start = System.currentTimeMillis()
            try { fetcher() } catch (_: Throwable) {}   // ← 吞掉一切，含 CancellationException
            ...
            if (remaining > 0) delay(remaining)
        } }
    }.collect { }
}
```
- 结构基本正确（`while(isActive)` + `delay` 轮询、`flatMapLatest` 切换间隔）。
- **缺陷**：`catch (_: Throwable) {}` 把 `CancellationException` 一并吞掉 → 取消信号无法上抛，父作用域取消时 fetcher 仍可能继续执行、或取消被静默忽略（债务点）。

### 3.3 修复方向

```kotlin
try { fetcher() }
catch (e: CancellationException) { throw e }   // 重抛，保持取消传播
catch (_: Throwable) { }                        // 业务异常按原样吞
```
- 若 `fetcher()` 内含阻塞 `waitFor`，进一步用 `runInterruptible(Dispatchers.IO) { ... }` 包裹，使取消能真正中断等待。

---

## 4. SoC 识别

**对应债务**：天玑识别覆没（辩证审计已澄清：4 款 MTK 已识别，仅中低端缺失，非"全覆没"）

### 4.1 官方字段（`android.os.Build`）

| 字段 | 引入 API | 说明 | 底层系统属性 |
|------|---------|------|----|
| `SOC_MODEL` | **31 (Android 12)** | SoC 型号，如 `mt6983` | `ro.soc.model` |
| `SOC_MANUFACTURER` | **31 (Android 12)** | SoC 厂商，如 `MediaTek` | `ro.soc.manufacturer` |
| `BOARD` | 1 | 主板 | `ro.product.board` |
| `HARDWARE` | 1 | 硬件 | `ro.hardware` |
| `DEVICE` / `MANUFACTURER` / `MODEL` / `PRODUCT` | 1 | 设备标识 | `ro.product.*` |
| `SUPPORTED_ABIS` | 21 | 指令集 | `ro.product.cpu.abilist` |

### 4.2 项目现状

- 用 `ro.board.platform` 匹配 `sm8750/sm8650/mt689/mt698...` 判定 SoC；minSdk 21 无法用 `Build.SOC_MODEL`。
- 天玑覆盖不全：仅部分 `mt68xx/mt69xx` 在白名单，中低端 `mt67xx` 系列缺失。

### 4.3 修复方向

- **API 31+** 优先 `Build.SOC_MODEL` / `Build.SOC_MANUFACTURER`（直接、权威）。
- **降级链**（minSdk 21 兼容）：`ro.soc.model` → `ro.board.platform` → `Build.HARDWARE`/`BOARD` → `ro.hardware`。
- 天玑全系正则覆盖：`mt67\d\d|mt68\d\d|mt69\d\d`（含 MT678x/685x/687x/689x/698x/699x 等），避免逐型号硬编码遗漏。
- 辩证审计结论保留：修复目标为"补全中低端天玑"，而非推倒重来的"覆没"叙事。

---

## 5. i18n（国际化）

**对应债务**：OEM ~80+ 硬编码中文字符串（主要在 `OemDataSource.kt`）+ 部分 UI 硬编码 EN 标题

### 5.1 官方最佳实践（`Localize your app`）

- **不硬编码任何字符串**，全部声明于 `strings.xml`；默认资源 `res/values/strings.xml` **必须完整**（缺失会在不支持 locale 下崩溃 Force Close）。
- 区域目录命名：`values-zh-rCN`（语言 `zh` + 区域 `rCN`）、`values-zh-rTW`（`rTW`）；区域 qualifier 用 `r` + 2 字母 ISO 3166-1；子集缺失自动回退默认。
- locale 解析优先级：**区域 qualifier 几乎总优先**（除 MCC/MNC）；`zh-CN`→`values-zh-rCN`，`zh-TW`→`values-zh-rTW`，不支持→默认；例外 MCC/MNC。
- **API 33+** 每应用语言偏好：`LocaleManager` / AppCompat 1.6+ `AppCompatDelegate.setApplicationLocales`。
- 非译片段用 `<xliff:g id="..." example="...">` 保护；用 `tools:ignore` 抑制 lint 缺失翻译告警。
- Compose 中经 `stringResource(R.string.xxx)` 引用。

### 5.2 项目现状

- 三语 `strings.xml` 已存在（`values` / `values-zh-rCN` / `values-zh-rTW`），本次电池改动已三语同步。
- `BatteryScreen` 等仍有硬编码 EN 卡片标题（"Battery health"/"Cycle count" 等）；`OemDataSource` 含大量 OEM 名/参数硬编码中文。

### 5.3 修复方向

- **渐进式**：优先抽取用户可见 UI 文案到 `strings.xml` 并三语补全（卡片标题、状态词）。
- OEM 设备名属"数据"而非"界面文案"，可豁免翻译，但**参数说明类文案**需抽取；避免一次性大改引发回归。

---

## 参考来源

- BatteryManager | Android Developers（developer.android.com / .google.cn）
- AOSP `platform_frameworks_base` → `core/java/android/os/BatteryManager.java`（常量值与注释）
- Build | Android Developers（字段与 API level）
- Process | Android Developers（`waitFor`/`destroyForcibly` API 26）
- Cancellation and timeouts | Kotlin Documentation（kotlinlang.org）
- Kotlin coroutines on Android | Android Developers
- Localize your app | Android Developers

> 本基线为修复前置事实依据，关键结论（尤其是 §1.2 的 `BATTERY_PROPERTY_CAPACITY` 误赋值）已实测命中现有代码，修复时务必回链。
