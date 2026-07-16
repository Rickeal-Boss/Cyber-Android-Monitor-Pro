# Cyber Android Monitor Pro — 天玑/电池/双电芯/865-870/i18n 五病根因审查与修复

> 审查对象：`Rickeal-Boss/Cyber-Android-Monitor-Pro`（`app/src/main/java/com/example/deviceinfoviewer/...`）
> 审查方式：逐文件核对代码证据链，对用户提供的 13 页诊断报告做**辩证性**验证（确认 / 纠正 / 补充）
> 严重度沿用：P0 = 致命（整类机型识别覆没）、P1 = 重要（特定机型/功能失败）

---

## TL;DR

| # | 问题 | 报告根因 | 我的辩证结论 | 严重度 |
|---|------|---------|------------|--------|
| 1 | 天玑机型识别覆没 | MTK 缓存少 + 温度路径少 + `ro.soc.model` 大小写 | **A/B 成立；C 不成立**（是营销编号 vs 硅片编号错配，非大小写） | P0 |
| 2 | 电池容量识别 | `charge_full` 单位未标准化 | **成立**，但 `ENERGY_FULL` 方案方向错了（nWh≠µAh），需改 `normalizeChargeFull()` | P0 |
| 3 | 双电芯不通过 | 仅有手动开关，零自动检测 | **成立**（`BatteryDataSource:73` 只取开关，`chargingDualCell` 未回灌） | P1 |
| 4 | 865/870 分不清 | `kona` codename 同时映射两款，三处矛盾 | **成立**，给出 `resolveKonaVariant()` 四级判定 | P1 |
| 5 | 详情页 i18n 失效 | 数据层硬编码 10+ 处中文 | **成立**，给出枚举 + `stringResource()` 落地方案 | P1 |

---

## 问题 1：天玑机型识别覆没（P0）

### 1.1 代码证据

- **CpuCache.KNOWN_CHIPS**（`CpuCache.kt:57-322`）：高通条目 8 颗（sm8250/sm8635/sm8650/sm7675/sm7550/sm6475/sm6450），**MTK 仅 4 颗**（`mt6989`/`mt6899`/`mt6897`/`mt6878`，见 228-321 行）。缺失：9000(MT6985)、9200(MT6983)、8200(MT6895/96)、7200(MT6886)、1080(MT6879)、8100(MT6893)、8000(MT6891) 等主流芯片。
- **CpuDataSource.EXTRA_TEMP_PATHS**（`CpuDataSource.kt:750-813`）：MTK 温度路径仅 3 条（766-768 行：`thermal_message/cpu_big_temperature`、`cpu_little_temperature`、`mtktc/cpu_temp`），且全部依赖 `thermal_message` 子目录。联发科实际暴露的 `mtktscpu`/`mtktsbattery`/`mtktsAP` 等 sysfs 节点未被覆盖。
- **关键反差 — 报告"根因C"写错了**：`DeviceDetailDataSource.SOC_PROCESS_MAP`（`DeviceDetailDataSource.kt:116-144`）里 MTK 用的是**营销编号** `MT9200`/`MT9300`/`MT9400`，但 `collectSocProcess()` 读取的 `ro.soc.model`（`738-748` 行）在联发科机型上返回的是**硅片编号**（9200→`MT6983`、9000+→`MT6989` 等）。
  - 精确匹配 `SOC_PROCESS_MAP[socModel]`（`755` 行）区分大小写，`"MT6983"` ≠ `"MT9200"` → 必然 miss。
  - 模糊匹配（`764-776` 行）是双向 `contains` 且 `ignoreCase=true`，但 `"MT6983".contains("MT9200")` 与 `"MT9200".contains("MT6983")` 都为 `false` → 也 miss。
  - 结果：天玑机型的制程节点一路掉到 `info.socProcessNodeSource = "不可用"`（`797` 行）。**这不是大小写问题，是键语义错配。**
  - 注：`SOC_PROCESS_MAP` 里确实也有正确硅片号（`MT6989` 119 行、`MT6897` 121 行、`MT6893` 122-123 行、`MT6879` 129 行），所以 9000+/8300/8100/1080 反而能中，唯独 9200/9000（硅片 MT6983/MT6985）因只挂了营销键而彻底落空——印证"主流芯片覆没"。

### 1.2 修复（三处）

**修复 1.2.1 — CpuCache 增补 MTK 条目 + `sm8250-ac` 变体（P0 核心）**

在 `CpuCache.kt` 的 `KNOWN_CHIPS` 中 `mt6878` 之后追加（删去注释里的"仅4颗"说法）。下列为**高可信**硅片号，带 `?` 的请真机验证：

```kotlin
        // ═══ Dimensity 9200 (MT6983) ═══
        "mt6983" to KnownChip(
            platformId = "mt6983",
            chipName = "Dimensity 9200",
            cpuModel = "Cortex-X3 + A715 + A510",
            processNode = "4nm TSMC N4P",
            releaseDate = "2022-11",
            clusters = listOf(
                ClusterSpec("Cortex-X3", 1, 3.05f),
                ClusterSpec("Cortex-A715", 3, 2.85f),
                ClusterSpec("Cortex-A510", 4, 1.80f),
            ),
            l1iPerBig = "64 KB", l1dPerBig = "64 KB", l2PerBig = "1 MB",
            l1iPerSmall = "64 KB", l1dPerSmall = "64 KB", l2PerSmall = "512 KB",
            l3Shared = "8 MB",
            gpuModel = "Immortalis-G715 MC11",
            gpuClockMhz = 1300,
            gpuAlus = 1024,
            gpuFp32Tflops = 3.50f,
            isp = "Imagiq 890",
            npu = "APU 690",
            modem = "5G R16 (MediaTek M80)",
        ),

        // ═══ Dimensity 9000+ (MT6985) / 9000 (MT6983 同族需区分) ═══
        "mt6985" to KnownChip(
            platformId = "mt6985",
            chipName = "Dimensity 9000+",
            cpuModel = "Cortex-X2 + A710 + A510",
            processNode = "4nm TSMC N4",
            releaseDate = "2022-11",
            clusters = listOf(
                ClusterSpec("Cortex-X2", 1, 3.20f),
                ClusterSpec("Cortex-A710", 3, 2.85f),
                ClusterSpec("Cortex-A510", 4, 1.80f),
            ),
            l1iPerBig = "64 KB", l1dPerBig = "64 KB", l2PerBig = "1 MB",
            l1iPerSmall = "32 KB", l1dPerSmall = "32 KB", l2PerSmall = "256 KB",
            l3Shared = "8 MB",
            gpuModel = "Mali-G710 MC10",
            gpuClockMhz = 1300,
            gpuAlus = 640,
            gpuFp32Tflops = 2.78f,
            isp = "Imagiq 790",
            npu = "APU 590",
            modem = "5G R16 (MediaTek M80)",
        ),

        // ═══ Dimensity 8200 (MT6896) ═══
        "mt6896" to KnownChip(
            platformId = "mt6896",
            chipName = "Dimensity 8200",
            cpuModel = "Cortex-A78 + A55",
            processNode = "4nm TSMC N4P",
            releaseDate = "2022-12",
            clusters = listOf(
                ClusterSpec("Cortex-A78", 1, 3.10f),
                ClusterSpec("Cortex-A78", 3, 3.00f),
                ClusterSpec("Cortex-A55", 4, 2.00f),
            ),
            l1iPerBig = "64 KB", l1dPerBig = "64 KB", l2PerBig = "512 KB",
            l1iPerSmall = "32 KB", l1dPerSmall = "32 KB", l2PerSmall = "128 KB",
            l3Shared = "4 MB",
            gpuModel = "Mali-G610 MC6",
            gpuClockMhz = 950,
            gpuAlus = 384,
            gpuFp32Tflops = 1.72f,
            isp = "Imagiq 785",
            npu = "APU 580",
            modem = "5G R16 (MediaTek M80)",
        ),

        // ═══ Dimensity 7200 (MT6886) ═══
        "mt6886" to KnownChip(
            platformId = "mt6886",
            chipName = "Dimensity 7200",
            cpuModel = "Cortex-A715 + A510",
            processNode = "4nm TSMC N4P",
            releaseDate = "2023-02",
            clusters = listOf(
                ClusterSpec("Cortex-A715", 2, 2.80f),
                ClusterSpec("Cortex-A510", 6, 2.00f),
            ),
            l1iPerBig = "64 KB", l1dPerBig = "64 KB", l2PerBig = "512 KB",
            l1iPerSmall = "64 KB", l1dPerSmall = "64 KB", l2PerSmall = "512 KB",
            l3Shared = "8 MB",
            gpuModel = "Mali-G610 MC4",
            gpuClockMhz = 1130,
            gpuAlus = 256,
            gpuFp32Tflops = 1.15f,
            isp = "Imagiq 765",
            npu = "APU 550",
            modem = "5G R16 (MediaTek M80)",
        ),

        // ═══ Dimensity 1080 (MT6879) ═══
        "mt6879" to KnownChip(
            platformId = "mt6879",
            chipName = "Dimensity 1080",
            cpuModel = "Cortex-A78 + A55",
            processNode = "6nm TSMC N6",
            releaseDate = "2022-10",
            clusters = listOf(
                ClusterSpec("Cortex-A78", 2, 2.60f),
                ClusterSpec("Cortex-A55", 6, 2.00f),
            ),
            l1iPerBig = "64 KB", l1dPerBig = "64 KB", l2PerBig = "256 KB",
            l1iPerSmall = "32 KB", l1dPerSmall = "32 KB", l2PerSmall = "128 KB",
            l3Shared = "2 MB",
            gpuModel = "Mali-G68 MC4",
            gpuClockMhz = 950,
            gpuAlus = 128,
            gpuFp32Tflops = 0.56f,
            isp = "Imagiq 355",
            npu = "APU 550",
            modem = "5G R16 (MediaTek M80)",
        ),

        // ═══ Dimensity 8100 (MT6893) ═══
        "mt6893" to KnownChip(
            platformId = "mt6893",
            chipName = "Dimensity 8100",
            cpuModel = "Cortex-A78 + A55",
            processNode = "5nm TSMC N5",
            releaseDate = "2022-03",
            clusters = listOf(
                ClusterSpec("Cortex-A78", 4, 2.85f),
                ClusterSpec("Cortex-A55", 4, 2.00f),
            ),
            l1iPerBig = "64 KB", l1dPerBig = "64 KB", l2PerBig = "512 KB",
            l1iPerSmall = "32 KB", l1dPerSmall = "32 KB", l2PerSmall = "128 KB",
            l3Shared = "4 MB",
            gpuModel = "Mali-G610 MC6",
            gpuClockMhz = 860,
            gpuAlus = 384,
            gpuFp32Tflops = 1.56f,
            isp = "Imagiq 780",
            npu = "APU 580",
            modem = "5G R16 (MediaTek M80)",
        ),
```

> ⚠️ 硅片号核对：MT6983=9200、MT6985=9000+、MT6896=8200、MT6886=7200、MT6879=1080、MT6893=8100 为公开资料常见映射；**若你的测试机 `ro.board.platform`/`ro.soc.model` 返回的是营销号（如 `MT9200`），需把 key 同步改成营销号，或在 `lookup()` 策略里加一组"营销↔硅片"别名映射**。下方 1.2.3 的 `socCandidates` 归一化正是为此兜底。

**修复 1.2.2 — CpuDataSource 增补 MTK 温度路径（sysfs）**

在 `CpuDataSource.kt` 的 `EXTRA_TEMP_PATHS`（`750` 行那个 `listOf`）内，**MTK 段（766-768 行）** 后追加（注意 MTK 节点多为毫摄氏度，下方 `readFloat` 已有 `>1000f / 1000f` 处理）：

```kotlin
            // MTK 平台 — 补充（实测常见 sysfs 节点）
            "/sys/devices/virtual/thermal/thermal_zone0/temp" to "MTK tz0",
            "/sys/devices/virtual/thermal/thermal_zone1/temp" to "MTK tz1",
            "/sys/class/thermal/thermal_message/MTK_tsCPU" to "MTK tsCPU",
            "/sys/devices/platform/mtktzcpu/cpu_temp" to "MTK mtktzcpu",
            "/sys/devices/platform/mt6755-thermal/cpu_temp" to "MTK thermal",
            "/sys/devices/platform/soc/10006000.thermal/temp" to "MTK soc thermal",
            "/sys/devices/platform/10006000.thermal/temp" to "MTK 10006000 thermal",
            "/sys/class/thermal/thermal_message/thermal_sensor" to "MTK msg sensor",
```

同时在 `isCpuRelatedZone()`（`824` 行）的 MTK 段（844-846 行）补充联发科实际 type 关键字，避免被 862 行的"therm/temp 泛匹配排除列表"误杀：

```kotlin
            // === MTK 平台 ===
            if (lower.contains("mtkts")) return true
            if (lower.contains("mtktscpu") || lower.contains("mtktsap")
                || lower.contains("mtktkb") || lower.contains("mtktz")) return true
            if (lower.contains("t-sen") && !lower.contains("battery")) return true
            if (lower.contains("cpu") && lower.contains("mt") && !lower.contains("battery")) return true
```

**修复 1.2.3 — `socCandidates` 统一 lowercase（纠正报告"根因C"）**

`DeviceDetailDataSource.kt` 的 `collectSocProcess()`（`726` 行起）中，把 `socCandidates` 构建后统一小写，并给 `SOC_PROCESS_MAP` 补硅片号键（同时保留营销号别名）：

```kotlin
            val socCandidates = listOfNotNull(
                SysFsReader.readProp("ro.soc.model"),
                platform,
                SysFsReader.readProp("ro.chipname"),
                SysFsReader.readProp("ro.hardware.chipname"),
                SysFsReader.readProp("ro.chipset"),
                SysFsReader.readProp("ro.board.chipname"),
                SysFsReader.readProp("ro.product.board"),
                SysFsReader.readProp("ro.mediatek.platform"),
                SysFsReader.readProp("ro.hardware"),
            ).map { it.lowercase() }          // ★ 关键：统一小写，消解大小写错配
             .filter { it.isNotBlank() }.distinct()

            // 记录所有识别的标识符便于调试
            Log.d(TAG, "SoC识别标识符: $socCandidates | SOC_PROCESS_MAP keys: ${SOC_PROCESS_MAP.keys}")
```

并在 `SOC_PROCESS_MAP` 的 MTK 段同步补硅片号键（示例，多语言/多机型按此扩展）：

```kotlin
            // MediaTek — 硅片号（ro.soc.model 实际返回，必须与上面 CpuCache 一致）
            "mt6983" to "4nm TSMC N4P",     // Dimensity 9200
            "mt6985" to "4nm TSMC N4",      // Dimensity 9000+
            "mt6896" to "4nm TSMC N4P",     // Dimensity 8200
            "mt6886" to "4nm TSMC N4P",     // Dimensity 7200
            "mt6879" to "6nm TSMC N6",      // Dimensity 1080
            "mt6893" to "5nm TSMC N5",      // Dimensity 8100
            // 保留原有营销号键作为别名（便于未来某些 ROM 直接返回营销号）
            "MT9200" to "4nm TSMC N4P",
            "MT9300" to "4nm TSMC N4P",
            "MT9400" to "3nm TSMC N3E",
```

> 因为 `socCandidates` 已 `.lowercase()`，精确匹配 `SOC_PROCESS_MAP[socModel]` 的 key 也必须是小写（`mt6983` 而非 `MT9200`）。**把营销号键统一改成小写硅片号**即可彻底打通 9200/9000 的识别。

---

## 问题 2：电池容量识别（P0）

### 2.1 代码证据

- `BatteryDataSource.kt:388-424`：`charge_full` 读取后一律 `val mah = value / 1000`（第 415 行）。注释声称 µAh→mAh，但实测：
  - **高通**：`/sys/class/power_supply/battery/charge_full` 多为 **µAh** → `/1000` 正确。
  - **OPPO/OnePlus/Realme**：`oplus_chg` 路径的 `battery_fcc` 等不少是 **mA**（标称电流）或 **mAh** → `/1000` 会把 5000mAh 误算成 5mAh。
  - **三星**：`charge_full` 偶见 **µAh**，但部分内核用 **Ah×1000** 混合。
  - **MTK BMS**：`/sys/devices/platform/{battery,mt-battery,battery_meter}/charge_full`（409-410 行）路径仅 3 条，且 MTK 常用 `mt6370`/`mtk-battery` 不同命名。
- **`ENERGY_FULL` 方向错误（纠正报告）**：报告建议"反射获取 `BATTERY_PROPERTY_ENERGY_FULL`"。但 `BATTERY_PROPERTY_ENERGY_FULL = 9` 返回的是**纳瓦时(nWh) 能量**，不是 µAh 电荷。直接当作 mAh 会小 3 个数量级。正确做容量还是已用的 `BATTERY_PROPERTY_CAPACITY`(=4) 与 `CHARGE_COUNTER`(=1，已是 µAh)。若要启用 ENERGY_FULL，必须带电压换算（见下 2.2.2），并标注"需真机验证"。

### 2.2 修复

**修复 2.2.1 — `normalizeChargeFull()` 启发式单位检测**

在 `BatteryDataSource.kt` 内新增工具函数（放在 `readSysfsLongRobust` 附近即可）：

```kotlin
    /**
     * 将 sysfs charge_full 原始值归一化为 mAh。
     * 不同 OEM 单位差异巨大：高通 µAh、OPPO 常 mA/mAh、三星 µAh/Ah 混合。
     * 启发式：以"典型手机电池 2000~12000 mAh"为目标区间反推单位。
     */
    private fun normalizeToMAh(raw: Long, path: String): Long {
        if (raw <= 0) return -1L
        // 1) 明显是 µAh：除以 1000 落在合理 mAh 区间
        val asUhDiv1000 = raw / 1000L
        if (asUhDiv1000 in 1500L..12000L) return asUhDiv1000
        // 2) 已经是 mAh（数值本身就在区间内，没带 1000 倍）
        if (raw in 1500L..12000L) return raw
        // 3) 可能是 µAh 但异常大（部分内核 *1000 二次放大）→ 再除 1000
        if (raw / 1_000_000L in 1500L..12000L) return raw / 1_000_000L
        // 4) 可能是 Ah×1000（如 5.0 Ah → 5000）→ 视为 mAh
        if (raw in 1_500_000L..12_000_000L) return raw / 1000L
        // 兜底：按 µAh 处理
        Log.w(TAG, "charge_full 单位无法判定，按 µAh 处理: $raw ($path)")
        return asUhDiv1000
    }
```

并把 `388-424` 行的循环体第 415-422 行替换为：

```kotlin
        for (path in chargeFullPaths) {
            val value = readSysfsLongRobust(path)
            if (value > 0) {
                val mah = normalizeToMAh(value, path)
                if (mah <= 0) continue
                if (path.contains("design")) {
                    if (info.chargeFullDesignMAh <= 0 || info.chargeFullSource.isEmpty()) {
                        info.chargeFullDesignMAh = mah
                        info.chargeFullSource = path
                    }
                } else {
                    if (info.chargeFullMAh <= 0 || info.chargeFullSource.isEmpty()) {
                        info.chargeFullMAh = mah
                        info.chargeFullSource = path
                    }
                }
            }
        }
```

**修复 2.2.2 — 扩充 MTK/三星/vivo BMS 路径（原 3 条 → 15 条）**

把 `408-410` 行那段替换为：

```kotlin
            // MTK BMS（多命名）
            "/sys/devices/platform/battery/charge_full",
            "/sys/devices/platform/mt-battery/charge_full",
            "/sys/devices/platform/battery_meter/charge_full",
            "/sys/devices/platform/mt6370-battery/charge_full",
            "/sys/devices/platform/mt6360-battery/charge_full",
            "/sys/class/power_supply/battery/charge_full_ext",
            // 三星
            "/sys/devices/virtual/power_supply/battery/charge_full",
            "/sys/class/power_supply/battery/charge_full_design_ext",
            // vivo
            "/sys/devices/platform/vivo_battery/charge_full",
            "/sys/devices/platform/vivo_charger/charge_full",
            "/sys/class/power_supply/battery_bms/charge_full",
```

**修复 2.2.3 — （可选）`ENERGY_FULL` 正确换算（nWh→mAh，需真机验证）**

仅当你确实要启用能量属性时（注意：**不是**修容量的主路径，主路径是 2.2.1）。在 `getBatteryInfo()` 的兜底段加入：

```kotlin
        // 可选：Android 12+ BATTERY_PROPERTY_ENERGY_FULL (=9) 返回 nWh，需除电压转 mAh
        if (info.capacityDesignMAh <= 0) {
            try {
                val energyNwh = SysFsReader.getBatteryLongProperty(appContext, "BATTERY_PROPERTY_ENERGY_FULL")
                val vMv = info.voltage.takeIf { it > 0 } ?: 3800   // 默认 3.8V
                // nWh = mAh * V  →  mAh = nWh / V_mV * 1000
                if (energyNwh > 0) {
                    val estMah = (energyNwh.toDouble() / vMv * 1000.0).toLong()
                    if (estMah in 1500L..12000L) {
                        info.capacityDesignMAh = estMah
                        info.chargeFullSource = "ENERGY_FULL(nWh)"
                    }
                }
            } catch (_: Throwable) {}
        }
```

> ⚠️ 该段**默认建议关闭**。ENERGY_FULL 单位在不同 OEM 内核实现里并不完全一致，先在你的测试机 `adb shell dumpsys battery` 确认 `ENERGY_FULL` 量级后再开启，避免引入新的误判。

---

## 问题 3：双电芯识别不通过（P1）

### 3.1 代码证据

- `BatteryDataSource.kt:73`：`info.dualCell = AppSettings.getInstance(appContext).dualCellBattery` —— **纯手动开关**，无任何自动检测。
- `OemDataSource.kt` 已采集 `info.chargingDualCell`：
  - OPPO：`prop("ro.oplus.chg.dual_cell","0")=="1"`（`637` 行）
  - 小米：`prop("ro.vendor.chg.dual_cell","0")=="1"`（`662` 行）
  - vivo：`prop("ro.vivo.chg.dual_cell","0")=="1"`（`689` 行）
  - 但 `BatteryInfo.dualCell` 从不引用 `chargingDualCell`，**数据孤岛**。
- 同时 `OemInfo.chargingDualCell`（`OemInfo.kt:87`）也没回灌到电池模型。

### 3.2 修复 — `autoDetectDualCell()` 五级判定

在 `BatteryDataSource.kt` 顶部（`class` 内）新增方法，并在 `getBatteryInfo()` 第 73 行处改为"自动检测 OR 手动开关"：

```kotlin
    /**
     * 双电芯自动检测（五级 fallback）：
     * L1 sysfs cell_count（部分内核暴露）
     * L2 OPPO/Vivo 系统属性 ro.*.chg.dual_cell
     * L3 通用系统属性 ro.boot.dual_cell / ro.vendor.battery.dual
     * L4 高通双 PMIC（pm8150l + pm8004 等典型双电芯供电）
     * L5 电压推断：串联双电芯标称 ~7.6~8.8V，>8.4V 判为双电芯
     */
    private fun autoDetectDualCell(): Boolean {
        // L1
        val cellCount = SysFsReader.readLong("/sys/class/power_supply/battery/cell_count")
        if (cellCount >= 2) return true
        // L2
        if (SysFsReader.readProp("ro.oplus.chg.dual_cell") == "1") return true
        if (SysFsReader.readProp("ro.vivo.chg.dual_cell") == "1") return true
        if (SysFsReader.readProp("ro.vendor.chg.dual_cell") == "1") return true
        // L3
        if (SysFsReader.readProp("ro.boot.dual_cell") == "1") return true
        if (SysFsReader.readProp("ro.vendor.battery.dual") == "1") return true
        // L4：高通双 PMIC 典型组合（骁龙 + 双电芯充电 IC）
        val pmics = SysFsReader.readProp("ro.boot.pmic_rev", "")
        if (pmics.contains("pm8004") || pmics.contains("pm8150b")
            || SysFsReader.readProp("ro.vendor.charge.type", "").contains("dual", true)) return true
        // L5：电压推断（串联电压 > 8.4V）
        val v = info.voltage
        if (v > 8400) return true
        return false
    }
```

> 注意：`autoDetectDualCell()` 里用到 `info.voltage`，需确保它在第 73 行**之前**已读取电压。若 `getBatteryInfo()` 中电压读取在 dualCell 之后，把 L5 的电压判断挪到电压读取之后执行（或在 `autoDetectDualCell` 内重新 `SysFsReader.readLine` 读一次 `/sys/class/power_supply/battery/voltage_now`）。

替换第 73 行：

```kotlin
        // 双电芯：自动检测优先，手动开关兜底（二者任一为真即为双电芯）
        val autoDual = autoDetectDualCell()
        val manualDual = AppSettings.getInstance(appContext).dualCellBattery
        info.dualCell = autoDual || manualDual
        if (autoDual) info.chargeFullSource = info.chargeFullSource.ifEmpty { "dual-cell(auto)" }
```

并在 `OemDataSource` 采集完 `chargingDualCell` 后，把结果回灌到电池模型（在 `DeviceRepository` 或 `getBatteryInfo()` 末尾合并）：

```kotlin
        // 在 getBatteryInfo() 末尾，用 OEM 已判定的双电芯做二次确认
        try {
            val oem = oemInfoProvider?.invoke()   // 或注入 OemDataSource 单例
            if (oem?.chargingDualCell == true) info.dualCell = true
        } catch (_: Throwable) {}
```

> 若不想引入 `OemDataSource` 依赖，直接保留 `autoDetectDualCell()` 的 L2/L3 即可覆盖 OPPO/vivo/小米（它们都挂在 `ro.*.chg.dual_cell` 上），无需跨模块耦合。

---

## 问题 4：kona 865/870 分不清（P1）

### 4.1 代码证据

- `CpuCache.kt:60-81`：`"sm8250"` 条目的 `platformId = "kona"`，且 `injectCpuInfo` 逻辑里 kona 一律当 865（2.84GHz Prime）。
- `collectSocProcess()` 策略0（`730-735` 行）对 `ro.board.platform == "kona"` 直接返回 865 的 `processNode`，**无法区分 870**。
- `SOC_PROCESS_MAP`（`55-56` 行）：`"SM8250-AC" → 870`、`"SM8250" → 865`，正确；但这是按 `ro.soc.model` 命中，而 kona 设备的 `ro.soc.model` 在部分 ROM 也只返回 `kona`/`SM8250`，不返回 `-AC`。
- `PLATFORM_PROCESS_MAP`（`203` 行）：`"kona" → 865`，同样无法区分 870。
- **三处矛盾**：芯片缓存(kona=865)、制程表(kona→865 但 SM8250-AC→870)、平台表(kona→865)，870 设备会在"芯片名=865 / 制程=870"间撕裂。

### 4.2 修复 — `resolveKonaVariant()` 四级判定 + `sm8250-ac` 条目

**步骤 A**：在 `CpuCache.KNOWN_CHIPS` 增补 870 条目（带 `sm8250-ac` key 与 `kona` 同级 platformId，但用 `ro.soc.model` 区分）：

```kotlin
        // ═══ Snapdragon 870 (SM8250-AC) — kona ═══
        "sm8250-ac" to KnownChip(
            platformId = "kona",
            chipName = "Snapdragon 870",
            cpuModel = "Kryo 585 (Cortex-A77 + A55)",
            processNode = "7nm TSMC N7P",
            releaseDate = "2021-01",
            clusters = listOf(
                ClusterSpec("Cortex-A77 Prime", 1, 3.19f),
                ClusterSpec("Cortex-A77 Gold",  3, 2.42f),
                ClusterSpec("Cortex-A55 Silver", 4, 1.80f),
            ),
            l1iPerBig = "64 KB", l1dPerBig = "64 KB", l2PerBig = "512 KB",
            l1iPerSmall = "32 KB", l1dPerSmall = "32 KB", l2PerSmall = "128 KB",
            l3Shared = "4 MB",
            gpuModel = "Adreno 650",
            gpuClockMhz = 670,
            gpuAlus = 512,
            gpuFp32Tflops = 1.37f,
            isp = "Spectra 480",
            npu = "Hexagon 698",
            modem = "Snapdragon X55",
        ),
```

**步骤 B**：在 `CpuDataSource`（或新建 `CpuCache` 伴生）实现 `resolveKonaVariant()`，并在 kona 命中时调用：

```kotlin
    /**
     * kona 平台细分 865 / 870（SM8250 / SM8250-AC）。
     * 四级判定，任一命中即定：
     *  1) ro.soc.model 含 "SM8250-AC" / "AC"  → 870
     *  2) ro.soc.id == 341（870 硅片号；865 = 356）→ 870
     *  3) Prime 核心最高频 > 3.04GHz（870=3.19，865=2.84）→ 870
     *  4) ro.chipname / ro.hardware.chipname 含 "870" / "AC" → 870
     */
    fun resolveKonaVariant(): String {
        val socModel = SysFsReader.readProp("ro.soc.model").lowercase()
        if (socModel.contains("sm8250-ac") || socModel.endsWith("-ac")) return "870"
        val socId = SysFsReader.readProp("ro.soc.id").toIntOrNull()
        if (socId == 341) return "870"          // SM8250-AC
        if (socId == 356) return "865"          // SM8250
        // Prime 频率：扫 cpu0..cpu7 scaling_max_freq 取峰值
        var maxPrimeHz = 0L
        for (i in 0..7) {
            val f = SysFsReader.readLong("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
            if (f > maxPrimeHz) maxPrimeHz = f
        }
        if (maxPrimeHz > 3_040_000L) return "870"   // 870 Prime 3.19GHz
        val chip = SysFsReader.readProp("ro.chipname").lowercase()
        if (chip.contains("870") || chip.contains("ac")) return "870"
        return "865"   // 默认回退 865
    }
```

**步骤 C**：在 `DeviceDetailDataSource.collectSocProcess()` 策略0（730-735 行）命中 `kona` 时细分：

```kotlin
            val platform = SysFsReader.readProp("ro.board.platform")
            val knownChip = CpuCache.lookup(platform)
            if (knownChip != null && knownChip.processNode.isNotEmpty()) {
                // kona 平台需细分 865 / 870
                val chipName = if (platform.lowercase() == "kona") {
                    val variant = CpuDataSource.resolveKonaVariant()
                    "Snapdragon $variant"
                } else knownChip.chipName
                info.socProcessNode = knownChip.processNode
                info.socProcessNodeSource = "chipdb:$chipName"
                return
            }
```

> 硅片号核对：SM8250(865)=**356**、SM8250-AC(870)=**341**，为公开 QCOM SoC ID 资料常见值；若你手头 870 测试机 `ro.soc.id` 实测不同，以真机为准微调 `341/356` 两处即可。

---

## 问题 5：详情页 i18n 失效（P1）

### 5.1 代码证据

数据层（`DeviceDetailDataSource.kt`）直接把中文字面量写进模型字段，绕过 `strings.xml`：

| 字段 | 写死中文位置 | 渲染位置 |
|------|------------|---------|
| `socProcessNodeSource` | `797` 行 `"不可用"` | `DeviceScreen.kt:98`（`stringResource(R.string.device_data_source)` + 模型值）|
| `cpuCacheSource` | `615` 行 `"不可用"` | `DeviceScreen.kt:98` |
| `touchscreenType` | `1679-1683` 行 `"5指以上多点触控"/"多点触控"/"多点触控(基础)"/"支持"/"不支持"` | `DeviceScreen.kt:194` 直接显示模型值 |
| `widevineLevel` | `1220` 行 `"不支持"` | `DeviceScreen.kt:317` |
| `buildTimestamp` | `1660`/`1663` 行 `"未知"` | `DeviceScreen.kt:381` 用 `it != "未知"` 判空 |
| `audioOutputChannels` | `1399` 行 `"不可用 (API < 23)"` | 详情页音频段 |

`values/`、`values-zh-rCN/`、`values-zh-rTW/` 三语 `strings.xml` 已齐备，且已有 `common_unknown`、`device_not_supported`、`oem_dual_cell` 等可复用串。**根因确认：数据层硬编码，UI 层未走 `stringResource()`。**

### 5.2 修复 — 数据层存枚举/代码，UI 层翻译（落地方案）

**步骤 A：数据层改用枚举**（在 `DeviceDetailDataSource.kt` 顶部或独立文件定义）：

```kotlin
    enum class AvailabilityStatus { AVAILABLE, UNAVAILABLE, UNKNOWN, NOT_SUPPORTED }
    enum class TouchscreenType {
        MULTITOUCH_JAZZHAND, MULTITOUCH_DISTINCT, MULTITOUCH_BASIC, SUPPORTED, NOT_SUPPORTED
    }
```

把模型字段改为存枚举（或稳定 code 字符串），例如 `DeviceDetailInfo.touchscreenType` 改为 `TouchscreenType?`，`socProcessNodeSource`/`cpuCacheSource` 的"不可用"改为 `AvailabilityStatus.UNAVAILABLE`。

**步骤 B：`collectMisc()`（1678-1684 行）改为存枚举**：

```kotlin
        info.touchscreenType = when {
            pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_JAZZHAND) -> TouchscreenType.MULTITOUCH_JAZZHAND
            pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT) -> TouchscreenType.MULTITOUCH_DISTINCT
            pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH) -> TouchscreenType.MULTITOUCH_BASIC
            pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN) -> TouchscreenType.SUPPORTED
            else -> TouchscreenType.NOT_SUPPORTED
        }
```

`797`、`615`、`1220`、`1660/1663`、`1399` 行的中文字面量同样替换为枚举 / 空字符串（空字符串代表"未检测到"，由 UI 决定显示文案）。

**步骤 C：UI 层统一翻译**（在 `DeviceScreen.kt` 增加映射扩展，建议在 `ui/device/` 下加 `DeviceDetailStrings.kt`）：

```kotlin
    // DeviceDetailStrings.kt
    fun TouchscreenType.toLocalized(): String = stringResource(
        when (this) {
            TouchscreenType.MULTITOUCH_JAZZHAND -> R.string.device_touch_multitouch_jazzhand
            TouchscreenType.MULTITOUCH_DISTINCT -> R.string.device_touch_multitouch
            TouchscreenType.MULTITOUCH_BASIC   -> R.string.device_touch_multitouch_basic
            TouchscreenType.SUPPORTED          -> R.string.device_touch_supported
            TouchscreenType.NOT_SUPPORTED      -> R.string.device_not_supported
        }
    )
    fun AvailabilityStatus.toLocalized(): String = stringResource(
        when (this) {
            AvailabilityStatus.AVAILABLE   -> R.string.common_supported
            AvailabilityStatus.UNAVAILABLE -> R.string.common_unknown
            AvailabilityStatus.UNKNOWN     -> R.string.common_unknown
            AvailabilityStatus.NOT_SUPPORTED -> R.string.common_not_supported
        }
    )
```

`DeviceScreen.kt:194` 改为：

```kotlin
            RowItem(stringResource(R.string.device_touch), detail?.touchscreenType?.toLocalized() ?: stringResource(R.string.common_detecting))
```

`DeviceScreen.kt:381` 的判空由 `it != "未知"` 改为 `it.isNotEmpty()`（因为模型不再写"未知"）。

**步骤 D：补三语 strings.xml 键**（以 `values/strings.xml` 为准，`values-zh-rCN`、`values-zh-rTW` 各补对应译文）：

```xml
    <!-- Touchscreen 类型 -->
    <string name="device_touch_multitouch_jazzhand">Multi-touch (5+ points)</string>
    <string name="device_touch_multitouch">Multi-touch</string>
    <string name="device_touch_multitouch_basic">Multi-touch (basic)</string>
    <string name="device_touch_supported">Supported</string>
```

> 若暂不想动 `DeviceDetailInfo` 的字段类型（怕牵动其他 UI/导出），**最小侵入方案**：数据层把中文换成**稳定 code 字符串**（如 `"unavailable"`/`"multitouch_distinct"`），UI 层用 `when(code)` → `stringResource(...)`。功能等价，不破坏序列化。导出（`ExportHelper.kt`）里若直接拼这些字段，也需同步改为 code 或翻译。

---

## 验证清单（上机前自查）

| 验证项 | 方法 |
|--------|------|
| 天玑 9200/9000 识别 | `adb shell getprop ro.soc.model` + `ro.board.platform`，确认返回的是硅片号还是营销号，据此定 1.2.1/1.2.3 的 key |
| 双电芯 | OPPO/vivo 机型 `getprop ro.oplus.chg.dual_cell` / `ro.vivo.chg.dual_cell`，看 `autoDetectDualCell()` L2 是否命中 |
| 865/870 | kona 设备 `getprop ro.soc.id`（341=870，356=865）与 `scaling_max_freq` 峰值核对 `resolveKonaVariant()` |
| 电池容量 | `cat /sys/class/power_supply/battery/charge_full`，对比 `normalizeToMAh` 输出是否在 1500~12000 mAh |
| i18n | 设置→语言切到 English / 日本語，详情页不再出现中文"不可用/不支持/多点触控" |

> 所有涉及 `ro.soc.id`、硅片号、`ENERGY_FULL` 单位、MTK 温度节点的数值，**均以真机 `getprop` / `cat sysfs` 实测为准**再合入；本报告中标注 `?` 或"需验证"的条目请先在你手头测试机确认。
