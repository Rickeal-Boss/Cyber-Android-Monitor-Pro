package com.example.deviceinfoviewer.data.source

import android.content.Context
import android.util.Log
import com.example.deviceinfoviewer.data.model.CpuCoreInfo
import com.example.deviceinfoviewer.data.model.CpuInfo

    /**
     * CPU 数据源 — 全网方案版 v2（骁龙专项优化）
     *
     * 温度获取策略（5 级 Fallback）：
     * Level 1: 反射 HardwarePropertiesManager (需要系统权限)
     * Level 2: sysfs thermal_zone 扫描 — 骁龙 TSENS 集群聚合（/sys/class/thermal/ + /sys/devices/virtual/thermal/）
     * Level 3: sysfs hwmon / 平台专用路径（高通/MTK/三星/华为/OPPO等）
     * Level 4: SensorManager 温度传感器
     * Level 5: 电池温度降级（BatteryManager EXTRA_TEMPERATURE）
     *
     * 骁龙 Snapdragon 专项优化：
     * - TSENS `cpu-*-*-usr` 传感器集群聚合（prime/performance/efficiency）
     * - `cpuss-*-usr` CPU 子系统温度
     * - Xiaomi HyperOS `thermal_message` 目录
     * - OPPO ColorOS 专用 thermal zone
     *
     * 小米 HyperOS / Android 14+ SELinux 收紧问题：
     * - /sys/class/thermal/ 可能被 SELinux 完全限制读取
     * - 此时 Level 1 和 Level 4 成为主要路径
     * - Level 5 作为最后的兜底
     */
class CpuDataSource(private val context: Context) {

    private val appContext = context.applicationContext

    fun getCpuInfo(): CpuInfo {
        val info = CpuInfo()
        info.architecture = System.getProperty("os.arch", "unknown") ?: "unknown"
        info.timestamp = System.currentTimeMillis()

        val cores = mutableListOf<CpuCoreInfo>()
        // Phase 1: 扫描所有核心，收集最大频率用于集群推断
        var coreIndex = 0
        val allMaxFreqs = mutableListOf<Long>()
        while (true) {
            val cpuDir = CPU_BASE + "cpu$coreIndex/cpufreq/"
            if (!SysFsReader.fileExists(cpuDir)) break
            val maxF = SysFsReader.readLong(cpuDir + "scaling_max_freq")
            allMaxFreqs.add(if (maxF > 0) maxF else SysFsReader.readLong(
                CPU_BASE + "cpu$coreIndex/cpufreq/cpuinfo_max_freq"))
            coreIndex++
        }

        // 频率集群命名 (dynamIQ / tri-cluster / dual-cluster)
        val freqGroups = allMaxFreqs.distinct().sortedDescending()
        val clusterNames = when (freqGroups.size) {
            1 -> listOf("all")
            2 -> listOf("big", "LITTLE")
            3 -> listOf("prime", "big", "LITTLE")
            4 -> listOf("prime", "big", "mid", "LITTLE")
            else -> freqGroups.mapIndexed { i, _ -> "cluster_$i" }
        }
        val freqToCluster = freqGroups.mapIndexed { i, f -> f to clusterNames[i] }.toMap()

        // Phase 2: 收集每个核心的详细信息
        coreIndex = 0
        while (true) {
            val cpuDir = CPU_BASE + "cpu$coreIndex/cpufreq/"
            if (!SysFsReader.fileExists(cpuDir)) break

            val core = CpuCoreInfo()
            core.coreIndex = coreIndex
            core.currentFreqKHz = SysFsReader.readLong(cpuDir + "scaling_cur_freq")
            core.maxFreqKHz = SysFsReader.readLong(cpuDir + "scaling_max_freq")
            core.minFreqKHz = SysFsReader.readLong(cpuDir + "scaling_min_freq")
            core.governor = SysFsReader.readLine(cpuDir + "scaling_governor")

            // 在线状态 (/sys/devices/system/cpu/cpuX/online)
            val onlinePath = CPU_BASE + "cpu$coreIndex/online"
            core.online = if (SysFsReader.fileExists(onlinePath))
                SysFsReader.readInt(onlinePath) == 1 else true

            // 核心集群推断
            if (core.maxFreqKHz > 0) {
                core.coreCluster = freqToCluster[core.maxFreqKHz] ?: "unknown"
            }

            // 核心类型推断 (基于集群 + 频率范围)
            core.coreType = inferCoreType(core.coreCluster, core.maxFreqKHz)

            cores.add(core)
            coreIndex++
        }
        info.coreCount = cores.size
        info.cores = cores

        // 获取 CPU 温度（多级 fallback）
        val tempResult = getCpuTemperatureFull()
        info.temperatureCelsius = tempResult.first
        info.temperatureSource = tempResult.second

        // 获取 CPU 缓存信息
        readCacheInfo(info)

        // 获取 CPU 使用率 (/proc/stat)
        info.cpuUsagePercent = getCpuUsage()

        // 获取 CPU 深度睡眠统计 (C-States)
        val idleResult = collectCpuIdleStats()
        info.deepSleepPercent = idleResult.first
        info.cStates = idleResult.second
        info.totalIdlePercent = idleResult.third
        info.cpuidleSource = idleResult.fourth

        // 收集支持的 ABI 列表
        info.supportedAbis = collectSupportedAbis()

        return info
    }

    /**
     * 多级 Fallback 获取 CPU 温度
     * @return Pair<温度℃, 数据来源描述>
     */
    fun getCpuTemperatureFull(): Pair<Float, String> {
        // === Level 1: HardwarePropertiesManager 反射 ===
        try {
            val cpuTemps = SysFsReader.getCpuTemperaturesViaReflection(appContext)
            if (cpuTemps != null && cpuTemps.isNotEmpty()) {
                val avgTemp = cpuTemps.filter { !it.isNaN() }.average().toFloat()
                if (avgTemp > 0 && avgTemp < 200) {
                    Log.i(TAG, "CPU temp via HardwarePropertiesManager: $avgTemp°C")
                    return Pair(avgTemp, "HardwarePropertiesManager (system API)")
                }
            }
        } catch (_: Throwable) { /* fall through */ }

        // === Level 1b: getDeviceTemperatures 过滤 CPU ===
        try {
            val deviceTemps = SysFsReader.getDeviceTemperaturesViaReflection(appContext)
            val cpuEntries = deviceTemps.filterKeys { it.startsWith("CPU_") }
            if (cpuEntries.isNotEmpty()) {
                val avgTemp = cpuEntries.values.filter { !it.isNaN() }.average().toFloat()
                if (avgTemp > 0 && avgTemp < 200) {
                    Log.i(TAG, "CPU temp via DeviceTemperatures: $avgTemp°C")
                    return Pair(avgTemp, "HardwarePropertiesManager (device)")
                }
            }
        } catch (_: Throwable) { /* fall through */ }

        // === Level 2: 标准 sysfs thermal_zone 扫描（骁龙：聚合所有 CPU 传感器） ===
        val primaryTemp = scanThermalZonesForCpu(THERMAL_BASE)
        if (!primaryTemp.isNaN()) {
            Log.i(TAG, "CPU temp (avg) via sysfs /sys/class/thermal/: $primaryTemp°C")
            return Pair(primaryTemp, "sysfs /sys/class/thermal/ (cluster avg)")
        }

        val virtualTemp = scanThermalZonesForCpu(VIRTUAL_THERMAL_BASE)
        if (!virtualTemp.isNaN()) {
            Log.i(TAG, "CPU temp (avg) via sysfs /sys/devices/virtual/thermal/: $virtualTemp°C")
            return Pair(virtualTemp, "sysfs /sys/devices/virtual/thermal/ (cluster avg)")
        }

        // === Level 2b: 骁龙 TSENS 回退 — 扫描所有 tsens 传感器（不限 CPU cluster） ===
        val tsensTemp = scanTsensSensors(THERMAL_BASE)
        if (!tsensTemp.isNaN()) {
            return Pair(tsensTemp, "Qualcomm TSENS (sysfs)")
        }
        val tsensTemp2 = scanTsensSensors(VIRTUAL_THERMAL_BASE)
        if (!tsensTemp2.isNaN()) {
            return Pair(tsensTemp2, "Qualcomm TSENS (virtual)")
        }

        // === Level 2c: Xiaomi/HyperOS thermal_message 目录 ===
        val hyperosTemp = scanHyperOsThermalMessage()
        if (!hyperosTemp.isNaN()) {
            return Pair(hyperosTemp, "HyperOS thermal_message")
        }

        // === Level 2d: OPPO ColorOS 专用 thermal 目录 ===
        val colorOsTemp = scanColorOsThermal()
        if (!colorOsTemp.isNaN()) {
            return Pair(colorOsTemp, "OPPO ColorOS thermal")
        }

        // === Level 2e: Vivo OriginOS 专用 thermal 目录 ===
        val originOsTemp = scanOriginOsThermal()
        if (!originOsTemp.isNaN()) {
            return Pair(originOsTemp, "Vivo OriginOS thermal")
        }

        // === Level 3: 扩展 sysfs 路径（hwmon / 平台专用） ===
        for ((path, desc) in EXTRA_TEMP_PATHS) {
            var temp = SysFsReader.readFloat(path)
            if (!temp.isNaN() && temp > 0) {
                if (temp > 1000f) temp /= 1000f  // 毫摄氏度 → 摄氏度
                if (temp in 10f..150f) {
                    Log.i(TAG, "CPU temp via $desc: $temp°C")
                    return Pair(temp, desc)
                }
            }
        }

        // === Level 4: SensorManager 温度传感器 ===
        val sensorTemp = getTempFromSensor()
        if (!sensorTemp.isNaN()) {
            Log.i(TAG, "CPU temp via Sensor: $sensorTemp°C")
            return Pair(sensorTemp, "温度传感器 (SensorManager)")
        }

        // === Level 4.5: dumpsys thermalservice 温控数据 (P1) ===
        try {
            val thermalOutput = ShellCommandDataSource.getDumpsysThermal()
            val temps = ShellCommandDataSource.extractThermalTemperatures(thermalOutput)
            if (temps.isNotEmpty()) {
                // 按 CPU 相关规则过滤后取最大值（SoC 最热点）
                val cpuTemps = temps.filter { it in 10f..150f }
                if (cpuTemps.isNotEmpty()) {
                    val maxTemp = cpuTemps.max()
                    Log.i(TAG, "CPU temp via dumpsys thermalservice: $maxTemp°C")
                    return Pair(maxTemp, "dumpsys thermalservice")
                }
            }
        } catch (_: Throwable) {}

        // === Level 5: 电池温度降级 ===
        val batteryTemp = getBatteryTemperature()
        if (!batteryTemp.isNaN()) {
            Log.w(TAG, "CPU temp fallback to battery temp: $batteryTemp°C")
            return Pair(batteryTemp, "电池温度 (降级方案)")
        }

        Log.w(TAG, "All CPU temperature methods failed")
        return Pair(Float.NaN, "无法获取")
    }

    /** 兼容旧 API */
    fun getCpuTemperature(): Float = getCpuTemperatureFull().first

    // ========== 内部方法 ==========

    /**
     * 扫描 thermal_zone 目录，聚合**所有** CPU 相关传感器温度求均值
     *
     * 骁龙 Snapdragon 专项：TSENS 为每个 CPU 集群暴露独立传感器：
     *   cpu-0-0-usr  → Prime 核心（Cortex-X4/X2/A77）
     *   cpu-1-0-usr  → 首个 Performance 核心
     *   cpu-2-0-usr  → 第二个 Performance 核心
     *   ...以此类推，通常还有 cpuss 子系统传感器
     *
     * 8s Gen 3: cpu-0~4-usr (perf) + cpu-5~7-usr (eff) + cpuss-0~2-usr
     * 865:     cpu-0~3-usr (eff) + cpu-4~6-usr (perf) + cpu-7-usr (prime)
     *
     * 返回所有 CPU 传感器的平均值，更准确反映整体温度
     */
    private fun scanThermalZonesForCpu(basePath: String): Float {
        val temps = mutableListOf<Float>()
        val zones = SysFsReader.listDir(basePath)

        for (zone in zones) {
            if (!zone.startsWith("thermal_zone")) continue
            val typePath = basePath + zone + "/type"
            val tempPath = basePath + zone + "/temp"
            val type = SysFsReader.readLine(typePath)
            if (isCpuRelatedZone(type)) {
                val temp = SysFsReader.readFloat(tempPath)
                if (!temp.isNaN() && temp > 0) {
                    val celsius = if (temp > 1000f) temp / 1000f else temp
                    if (celsius in 10f..150f) temps.add(celsius)
                }
            }
        }

        return if (temps.isNotEmpty()) {
            temps.average().toFloat()
        } else Float.NaN
    }

    /**
     * 扫描所有 TSENS 传感器（不限 CPU，取最高温度作为 SoC 温度降级）
     * 适用于 CPU thermal zone 被 SELinux 拦截但 TSENS 仍可读的场景
     */
    private fun scanTsensSensors(basePath: String): Float {
        val tsensPatterns = listOf("tsens", "tsens_tz", "tz-")
        val temps = mutableListOf<Float>()
        val zones = SysFsReader.listDir(basePath)

        for (zone in zones) {
            if (!zone.startsWith("thermal_zone")) continue
            val typePath = basePath + zone + "/type"
            val type = SysFsReader.readLine(typePath).lowercase()

            // TSENS 传感器
            if (tsensPatterns.any { type.contains(it) } && !type.contains("mmw")) {
                val temp = SysFsReader.readFloat(basePath + zone + "/temp")
                if (!temp.isNaN() && temp > 0) {
                    val celsius = if (temp > 1000f) temp / 1000f else temp
                    if (celsius in 10f..150f) temps.add(celsius)
                }
            }
        }

        // 取最高温度（SoC 最热点 ≈ CPU 温度上限）
        return if (temps.isNotEmpty()) temps.max().toFloat() else Float.NaN
    }

    /**
     * 扫描 Xiaomi/HyperOS thermal_message 目录
     * 部分 HyperOS 版本在 /sys/class/thermal/thermal_message/ 下暴露直接可读的温度文件
     * 这些文件通常不受 SELinux 限制（因为它们是内核直接导出的属性文件）
     */
    private fun scanHyperOsThermalMessage(): Float {
        val messageDir = "/sys/class/thermal/thermal_message/"
        if (!SysFsReader.fileExists(messageDir)) return Float.NaN

        val candidates = listOf(
            "soc_temperature",
            "cpu_big_temperature",
            "cpu_little_temperature",
            "cpu_temperature",
            "board_sensor_temp",
            "soc_temp",
        )
        for (file in candidates) {
            val temp = SysFsReader.readFloat(messageDir + file)
            if (!temp.isNaN() && temp > 0) {
                val celsius = if (temp > 1000f) temp / 1000f else temp
                if (celsius in 10f..150f) return celsius
            }
        }
        return Float.NaN
    }

    /**
     * OPPO ColorOS 专用 thermal 扫描
     * ColorOS 有独立的 thermal-engine 和 thermal_message 目录
     */
    private fun scanColorOsThermal(): Float {
        // ColorOS 15/16 可能暴露的路径
        val colorOsPaths = listOf(
            "/sys/class/thermal/thermal_message/soc_max_temp",
            "/sys/class/thermal/thermal_message/cpu_temp",
            "/sys/devices/virtual/thermal/thermal_message/cpu_temp",
            "/sys/class/thermal/thermal_zone0/temp",
            "/sys/devices/platform/soc/cpu_temp",
            "/sys/devices/platform/thermal_sensor/cpu_temp",
        )
        for (path in colorOsPaths) {
            val temp = SysFsReader.readFloat(path)
            if (!temp.isNaN() && temp > 0) {
                val celsius = if (temp > 1000f) temp / 1000f else temp
                if (celsius in 10f..150f) return celsius
            }
        }
        return Float.NaN
    }

    /**
     * Vivo OriginOS 专用 thermal 扫描
     */
    private fun scanOriginOsThermal(): Float {
        val originOsPaths = listOf(
            "/sys/class/thermal/thermal_message/soc_temp",
            "/sys/class/thermal/thermal_message/cpu_temp",
            "/sys/devices/virtual/thermal/thermal_message/soc_temp",
            "/sys/devices/platform/vivo-thermal/cpu_temp",
            "/sys/devices/platform/thermal-sensor/cpu_temp",
        )
        for (path in originOsPaths) {
            val temp = SysFsReader.readFloat(path)
            if (!temp.isNaN() && temp > 0) {
                val celsius = if (temp > 1000f) temp / 1000f else temp
                if (celsius in 10f..150f) return celsius
            }
        }
        return Float.NaN
    }

    /**
     * 通过 SensorManager 获取温度传感器值
     * 注: 大部分手机没有 CPU 专用温度传感器，此方法成功率低
     */
    private fun getTempFromSensor(): Float {
        try {
            val sm = appContext.getSystemService(Context.SENSOR_SERVICE) as? android.hardware.SensorManager
                ?: return Float.NaN
            val sensors = sm.getSensorList(android.hardware.Sensor.TYPE_ALL)
            for (s in sensors) {
                val type = s.stringType.uppercase()
                if (type.contains("TEMP") || type.contains("CPU") || type.contains("AMBIENT")) {
                    // 无法在非回调模式下获取传感器的当前值
                    // 这里只能返回 NaN，实际生产代码需要注册 SensorEventListener
                    // 但这会增加架构复杂度，保留框架代码供未来扩展
                    Log.d(TAG, "Temperature sensor found: ${s.name} (type=$type)")
                    // 如果未来实现：注册 SensorEventListener 并等待首次回调
                }
            }
        } catch (_: Throwable) { /* fall through */ }
        return Float.NaN
    }

    /**
     * 通过 BatteryManager 获取电池温度（作为 CPU 温度的最后降级）
     */
    private fun getBatteryTemperature(): Float {
        return try {
            val filter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
            val status = appContext.registerReceiver(null, filter) ?: return Float.NaN
            val tempRaw = status.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, -1)
            if (tempRaw > 0) tempRaw / 10.0f else Float.NaN
        } catch (_: Throwable) { Float.NaN }
    }

    // ========== CPU 使用率 (/proc/stat) ==========
    // 基于两次采样差值: (Δtotal - Δidle) / Δtotal × 100

    private var prevTotal = 0L
    private var prevIdle = 0L

    fun getCpuUsage(): Float {
        return try {
            val stat = SysFsReader.readLine("/proc/stat")
            if (!stat.startsWith("cpu ")) return Float.NaN
            val fields = stat.trim().split("\\s+".toRegex())
            if (fields.size < 5) return Float.NaN
            val user = fields[1].toLongOrNull() ?: return Float.NaN
            val nice = fields[2].toLongOrNull() ?: return Float.NaN
            val system = fields[3].toLongOrNull() ?: return Float.NaN
            val idle = fields[4].toLongOrNull() ?: return Float.NaN
            val iowait = fields.getOrNull(5)?.toLongOrNull() ?: 0L
            val irq = fields.getOrNull(6)?.toLongOrNull() ?: 0L
            val softirq = fields.getOrNull(7)?.toLongOrNull() ?: 0L
            val total = user + nice + system + idle + iowait + irq + softirq
            val totalIdle = idle + iowait
            if (prevTotal == 0L || prevIdle == 0L) { prevTotal = total; prevIdle = totalIdle; return Float.NaN }
            val deltaTotal = total - prevTotal
            val deltaIdle = totalIdle - prevIdle
            prevTotal = total; prevIdle = totalIdle
            if (deltaTotal <= 0) return Float.NaN
            ((deltaTotal - deltaIdle).toFloat() / deltaTotal * 100f).coerceIn(0f, 100f)
        } catch (_: Throwable) { Float.NaN }
    }

    /**
     * 计算每个核心的使用率 (/proc/stat cpuN 行)
     */
    private val prevCoreStats = mutableMapOf<Int, LongArray>()

    fun getPerCoreUsage(): Map<Int, Float> {
        val result = mutableMapOf<Int, Float>()
        try {
            val stat = SysFsReader.readAll("/proc/stat")
            for (line in stat.split("\n")) {
                val trimmed = line.trim()
                if (!trimmed.startsWith("cpu")) continue
                val cpuName = trimmed.substringBefore(" ")
                if (cpuName == "cpu") continue  // 跳过总行
                val coreIdx = cpuName.removePrefix("cpu").toIntOrNull() ?: continue

                val fields = trimmed.split("\\s+".toRegex())
                if (fields.size < 5) continue
                val user = fields[1].toLongOrNull() ?: continue
                val nice = fields[2].toLongOrNull() ?: continue
                val system = fields[3].toLongOrNull() ?: continue
                val idle = fields[4].toLongOrNull() ?: continue
                val iowait = fields.getOrNull(5)?.toLongOrNull() ?: 0L
                val irq = fields.getOrNull(6)?.toLongOrNull() ?: 0L
                val softirq = fields.getOrNull(7)?.toLongOrNull() ?: 0L
                val total = user + nice + system + idle + iowait + irq + softirq
                val totalIdle = idle + iowait

                val prev = prevCoreStats[coreIdx]
                if (prev == null) { prevCoreStats[coreIdx] = longArrayOf(total, totalIdle); continue }
                val deltaTotal = total - prev[0]
                val deltaIdle = totalIdle - prev[1]
                prevCoreStats[coreIdx] = longArrayOf(total, totalIdle)
                if (deltaTotal > 0) {
                    result[coreIdx] = ((deltaTotal - deltaIdle).toFloat() / deltaTotal * 100f).coerceIn(0f, 100f)
                }
            }
        } catch (_: Throwable) {}
        return result
    }

    // ========== CPU 深度睡眠统计 (C-States) ==========
    /**
     * 读取所有 CPU 核心的 cpuidle 状态
     * 路径: /sys/devices/system/cpu/cpu0/cpuidle/state{N}/name, time, usage, latency
     *
     * @return (deepSleepPercent, cStates列表, totalIdlePercent, source)
     */
    fun collectCpuIdleStats(): Quadruple<Float, List<com.example.deviceinfoviewer.data.model.CpuCState>, Float, String> {
        try {
            // 聚合所有核心的各 C-State
            val allStates = mutableMapOf<String, MutableList<com.example.deviceinfoviewer.data.model.CpuCState>>()
            var coresFound = 0

            for (coreIdx in 0..7) {
                val cpuidleBase = "/sys/devices/system/cpu/cpu$coreIdx/cpuidle/"
                if (!SysFsReader.fileExists(cpuidleBase)) continue

                val stateDirs = SysFsReader.listDir(cpuidleBase)
                coresFound++

                for (dir in stateDirs) {
                    if (!dir.startsWith("state")) continue
                    val statePath = cpuidleBase + dir
                    val name = SysFsReader.readLine(statePath + "/name").trim()
                    if (name.isEmpty()) continue
                    val time = SysFsReader.readLong(statePath + "/time")
                    val usage = SysFsReader.readInt(statePath + "/usage")
                    val latency = SysFsReader.readInt(statePath + "/latency")

                    if (time > 0) {
                        val level = dir.removePrefix("state").toIntOrNull() ?: 0
                        val state = com.example.deviceinfoviewer.data.model.CpuCState(
                            name = name, timeUs = time, usage = usage,
                            latencyUs = latency, level = level
                        )
                        allStates.getOrPut(name) { mutableListOf() }.add(state)
                    }
                }
            }

            if (coresFound == 0 || allStates.isEmpty()) {
                return Quadruple(Float.NaN, emptyList(), Float.NaN, "cpuidle 不可用")
            }

            // 聚合: 每个 C-State 取所有核心的 time 总和
            val aggregated = allStates.map { (name, states) ->
                com.example.deviceinfoviewer.data.model.CpuCState(
                    name = name,
                    timeUs = states.sumOf { it.timeUs },
                    usage = states.sumOf { it.usage },
                    latencyUs = states.firstOrNull()?.latencyUs ?: 0,
                    level = states.firstOrNull()?.level ?: 0
                )
            }.sortedBy { it.level }

            // C0 (WFI/active idle) 是 idle 但不算深度睡眠
            // C1 (浅眠) + C2/C3 (深度睡眠)
            val totalSleepTime = aggregated.sumOf { it.timeUs }
            if (totalSleepTime <= 0) {
                return Quadruple(Float.NaN, emptyList(), Float.NaN, "cpuidle 数据为零")
            }

            // 深度睡眠: C2 及更深的睡眠状态
            val deepSleepStates = aggregated.filter { it.level >= 2 || it.name.contains("C2", ignoreCase = true) || it.name.contains("SLEEP", ignoreCase = true) || it.name.contains("retention", ignoreCase = true) }
            val deepTime = deepSleepStates.sumOf { it.timeUs }
            val deepPct = if (totalSleepTime > 0) (deepTime.toFloat() / totalSleepTime * 100f).coerceIn(0f, 100f) else Float.NaN

            // 总空闲: 全部 C-State 时间 (不包括 active)
            val totalIdlePct = 100f  // cpuidle 本身就是 idle 时间

            return Quadruple(deepPct, aggregated, totalIdlePct, "cpuidle ($coresFound cores)")
        } catch (_: Throwable) {
            return Quadruple(Float.NaN, emptyList(), Float.NaN, "cpuidle 读取失败")
        }
    }

    /** 简单的四元组容器，避免引入额外依赖 */
    data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    /**
     * 收集 CPU 支持的 ABI 列表
     * 数据源: Build.SUPPORTED_ABIS (Android 5+), Build.CPU_ABI (legacy)
     */
    private fun collectSupportedAbis(): List<String> {
        return try {
            val abis = mutableListOf<String>()
            // 主 ABI 列表 (Android 5+, API 21+)
            try {
                android.os.Build.SUPPORTED_ABIS.forEach { abis.add(it) }
            } catch (_: Throwable) {
                // 降级: 32-bit 和 64-bit ABI 分别获取
                try { android.os.Build.SUPPORTED_64_BIT_ABIS.forEach { abis.add(it) } }
                    catch (_: Throwable) {}
                try { android.os.Build.SUPPORTED_32_BIT_ABIS.forEach { abis.add(it) } }
                    catch (_: Throwable) {}
            }
            // 兜底: 单个 CPU_ABI
            if (abis.isEmpty()) {
                try {
                    val cpuAbi = android.os.Build.CPU_ABI
                    if (cpuAbi.isNotEmpty()) abis.add(cpuAbi)
                } catch (_: Throwable) {}
                try {
                    val cpuAbi2 = android.os.Build.CPU_ABI2
                    if (cpuAbi2.isNotEmpty()) abis.add(cpuAbi2)
                } catch (_: Throwable) {}
            }
            abis
        } catch (_: Throwable) { emptyList() }
    }

    /**
     * 读取 CPU 缓存信息 (L1/L2/L3)
     * sysfs 路径: /sys/devices/system/cpu/cpu0/cache/index{0,1,2,3}/size
     */
    private fun readCacheInfo(info: CpuInfo) {
        val cacheBase = "/sys/devices/system/cpu/cpu0/cache/"
        for (i in 0..3) {
            val type = SysFsReader.readLine(cacheBase + "index$i/type").trim()
            val size = SysFsReader.readLine(cacheBase + "index$i/size").trim()
            if (size.isNotEmpty()) {
                val formatted = formatCacheSize(size)
                when {
                    type.contains("L1") && type.contains("Data") || i == 0 ->
                        info.cacheL1 = "${info.cacheL1}Data:$formatted "
                    type.contains("L1") && type.contains("Instruction") || i == 1 ->
                        info.cacheL1 = "${info.cacheL1}Inst:$formatted "
                    type.contains("L2") || i == 2 ->
                        info.cacheL2 = formatted
                    type.contains("L3") || i == 3 ->
                        info.cacheL3 = formatted
                }
            }
        }
    }

    private fun formatCacheSize(size: String): String {
        return try {
            val kb = size.replace(Regex("[^0-9]"), "").toLongOrNull() ?: return size
            when {
                kb >= 1024 -> "${kb / 1024} MB"
                kb > 0 -> "${kb} KB"
                else -> size
            }
        } catch (_: Throwable) { size }
    }

    /**
     * 基于集群名称 + 最大频率推断 ARM 核心类型
     */
    private fun inferCoreType(cluster: String, maxFreqKHz: Long): String {
        val freqGHz = maxFreqKHz / 1_000_000f
        return when {
            cluster == "prime" -> when {
                freqGHz >= 3.3f -> "Cortex-X4"
                freqGHz >= 3.2f -> "Cortex-X3"
                freqGHz >= 3.0f -> "Cortex-X2"
                else -> "Cortex-X1"
            }
            cluster == "big" || cluster == "performance" -> when {
                freqGHz >= 2.8f -> "Cortex-A715"
                freqGHz >= 2.5f -> "Cortex-A710"
                else -> "Cortex-A78"
            }
            cluster == "mid" -> when {
                freqGHz >= 2.4f -> "Cortex-A78"
                freqGHz >= 2.0f -> "Cortex-A77"
                else -> "Cortex-A76"
            }
            cluster == "LITTLE" || cluster == "efficiency" -> when {
                freqGHz >= 2.0f -> "Cortex-A520"
                freqGHz >= 1.8f -> "Cortex-A510"
                else -> "Cortex-A55"
            }
            cluster == "all" -> "Universal"
            else -> "${cluster}_${"%.1f".format(freqGHz)}GHz"
        }
    }

    companion object {
        private const val TAG = "CpuDataSource"
        private const val CPU_BASE = "/sys/devices/system/cpu/"
        private const val THERMAL_BASE = "/sys/class/thermal/"
        private const val VIRTUAL_THERMAL_BASE = "/sys/devices/virtual/thermal/"

        /**
         * 扩展 sysfs 温度路径 — 覆盖各芯片厂商和特殊路径
         * Pair<路径, 来源描述>
         */
        private val EXTRA_TEMP_PATHS = listOf(
            // 通用 hwmon
            "/sys/class/hwmon/hwmon0/device/temp1_input" to "hwmon temp1_input",
            "/sys/class/hwmon/hwmon1/device/temp1_input" to "hwmon1 temp1_input",
            "/sys/class/hwmon/hwmon2/device/temp1_input" to "hwmon2 temp1_input",
            "/sys/class/hwmon/hwmon3/device/temp1_input" to "hwmon3 temp1_input",

            // 高通 Snapdragon 专用 — TSENS CPU
            "/sys/devices/system/cpu/cpu0/cpufreq/cpu_temp" to "Snapdragon cpu_temp",
            "/sys/devices/system/cpu/cpu0/cpufreq/FakeShmoo_cpu_temp" to "Snapdragon FakeShmoo",
            "/sys/devices/virtual/thermal/thermal_zone0/temp" to "Snapdragon virtual tz0",

            // 骁龙 BCL (Battery Current Limiter) — 温控调速代理温度
            "/sys/class/thermal/thermal_zone0/temp" to "Snapdragon BCL tz0",

            // MTK 平台
            "/sys/class/thermal/thermal_message/cpu_big_temperature" to "MTK cpu_big",
            "/sys/class/thermal/thermal_message/cpu_little_temperature" to "MTK cpu_little",
            "/sys/devices/platform/mtktc/cpu_temp" to "MTK mtktc",

            // 三星 Exynos
            "/sys/devices/platform/s5p-tmu/temperature" to "Exynos s5p-tmu",
            "/sys/devices/platform/s5p-tmu/curr_temp" to "Exynos curr_temp",
            "/sys/devices/virtual/sensor/tmu/cpu" to "Exynos tmu/cpu",

            // NVIDIA Tegra
            "/sys/devices/platform/tegra_tmon/temp1_input" to "Tegra tmon",
            "/sys/kernel/debug/tegra_thermal/temp_tj" to "Tegra temp_tj",

            // TI OMAP
            "/sys/devices/platform/omap/omap_temp_sensor.0/temperature" to "OMAP temp_sensor",

            // 华为 Kirin
            "/sys/class/thermal/thermal_zone0/temp" to "Kirin thermal_zone0",
            "/sys/devices/virtual/thermal/thermal_zone0/temp" to "Kirin virtual thermal",

            // i2c 传感器
            "/sys/class/i2c-adapter/i2c-4/4-004c/temperature" to "i2c temperature",
            "/sys/devices/platform/tegra-i2c.3/i2c-4/4-004c/temperature" to "Tegra i2c",

            // Xiaomi/HyperOS Snapdragon 专用
            "/sys/class/thermal/thermal_message/soc_temperature" to "HyperOS soc_temp",
            "/sys/class/thermal/thermal_message/board_sensor_temp" to "HyperOS board_sensor",
            "/sys/class/thermal/thermal_zone0/temp" to "HyperOS tz0",

            // OPPO ColorOS 骁龙专用
            "/sys/devices/virtual/thermal/thermal_zone0/temp" to "OPPO ColorOS tz0",
            "/sys/class/thermal/thermal_zone1/temp" to "OPPO ColorOS tz1",
            "/sys/class/thermal/thermal_zone2/temp" to "OPPO ColorOS tz2",
            "/sys/class/thermal/thermal_zone3/temp" to "OPPO ColorOS tz3",

            // OPPO ColorOS 15/16
            "/sys/devices/platform/soc/cpu_temp" to "OPPO ColorOS SoC CPU",
            "/sys/devices/platform/thermal_sensor/cpu_temp" to "OPPO thermal_sensor",
            "/sys/class/thermal/thermal_message/soc_max_temp" to "OPPO soc_max",

            // Vivo OriginOS
            "/sys/devices/platform/vivo-thermal/cpu_temp" to "Vivo thermal",
            "/sys/devices/platform/thermal-sensor/cpu_temp" to "Vivo thermal_sensor",
            "/sys/class/thermal/thermal_message/soc_temp" to "Vivo soc_temp",

            // Xiaomi/HyperOS 增强
            "/sys/devices/platform/soc/soc:qcom,cpuss-thermal/cpu_temp" to "HyperOS cpuss",
        )

        /**
         * 判断 thermal zone type 是否与 CPU 相关
         *
         * 骁龙 TSENS 命名规范：
         *   cpu-{cluster}-{sensor}-usr  → CPU 用户空间传感器
         *   cpuss-{n}-usr               → CPU 子系统传感器
         *   tsens_tz_sensor{n}          → TSENS 温敏传感器
         *   cpu{n}-tsens                → CPU TSENS 映射
         */
        private fun isCpuRelatedZone(type: String?): Boolean {
            if (type.isNullOrEmpty()) return false
            val lower = type.lowercase()

            // === 骁龙 TSENS CPU 集群（高优先级，直接匹配） ===
            if (lower.startsWith("cpu-") && lower.endsWith("-usr")) return true
            if (lower.startsWith("cpuss-") && lower.endsWith("-usr")) return true

            // === 直接包含 CPU ===
            if (lower.contains("cpu")) return true

            // === 高通 TSENS ===
            if (lower.contains("tsens") && (
                lower.contains("tz_sensor") || lower.contains("cpu") ||
                lower.contains("soc") || lower.contains("apc")
            )) return true

            // === Qualcomm BCL (Battery Current Limiter — 温控调速代理) ===
            if (lower == "bcl" || lower.startsWith("bcl-")) return true

            // === MTK 平台 ===
            if (lower.contains("mtkts")) return true
            if (lower.contains("t-sen") && !lower.contains("battery")) return true

            // === SOC 级别传感器 ===
            if (lower == "soc" || lower.startsWith("soc-")) return true
            if (lower.contains("x86_pkg_temp")) return true  // Intel
            if (lower.contains("acpitz")) return true         // ACPI Thermal Zone
            if (lower.contains("soc_max") || lower.contains("soc-thermal")) return true

            // === 集群/核心温度 ===
            if (lower.contains("cluster") && lower.contains("temp")) return true
            if (lower.contains("core") && lower.contains("temp")) return true

            // === 平台温控 ===
            if (lower.contains("ddr")) return true
            if (lower.contains("gpu")) return true

            // === therm/temp 泛匹配（排除非 CPU 的传感器） ===
            if (lower.contains("therm") || lower.contains("temp")) {
                val excludePatterns = listOf(
                    "battery", "batt", "charger", "charge",
                    "wifi", "bt", "bluetooth",
                    "camera", "cam", "pa", "rf",
                    "modem", "mdm", "lcd", "display", "backlight",
                    "mmw", "nfc", "flash", "usb"
                )
                return !excludePatterns.any { lower.contains(it) }
            }

            // === 华为麒麟 ===
            if (lower.contains("kirin")) return true

            // === 大小核模式 ===
            if ((lower.contains("big") || lower.contains("little") ||
                 lower.contains("prime") || lower.contains("middle")) &&
                (lower.contains("temp") || lower.contains("thermal"))) return true

            return false
        }
    }
}
