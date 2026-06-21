package com.example.deviceinfoviewer.data.repository

import android.content.Context
import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.example.deviceinfoviewer.AppSettings
import com.example.deviceinfoviewer.RefreshPolicy
import com.example.deviceinfoviewer.data.model.*
import com.example.deviceinfoviewer.data.source.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow

/**
 * 核心数据仓库 — Kotlin 协程驱动
 *
 * 前后台统一刷新 (2026-06-21):
 * - 内置 RefreshPolicy.state 观察者，前后台切换自动调速
 * - 前台: Tier.NORMAL (2000ms) / 后台: Tier.ECONOMY (5000ms)
 * - 无需外部手动调频，状态变更由 RefreshPolicy 统一广播
 */
class DeviceRepository(context: Context) {
    private val appContext: Context = context.applicationContext

    companion object {
        const val TAG = "DeviceRepo"
    }

    // ── 数据源健康状态 ──
    data class SourceHealth(
        val cpu: Health = Health.OK,
        val gpu: Health = Health.OK,
        val battery: Health = Health.OK,
        val memory: Health = Health.OK,
        val storage: Health = Health.OK,
        val wifi: Health = Health.OK,
        val mobileNetwork: Health = Health.OK,
        val networkInterface: Health = Health.OK,
        val gps: Health = Health.OK,
        val sensors: Health = Health.OK,
        val system: Health = Health.OK,
        val deviceDetail: Health = Health.OK,
        val oem: Health = Health.OK
    ) {
        enum class Health { OK, WARN, ERROR }
        val allHealthy get() = listOf(cpu, gpu, battery, memory, storage,
            wifi, mobileNetwork, networkInterface, gps, sensors, system, deviceDetail, oem)
            .all { it == Health.OK }
        val errorCount get() = listOf(cpu, gpu, battery, memory, storage,
            wifi, mobileNetwork, networkInterface, gps, sensors, system, deviceDetail, oem)
            .count { it == Health.ERROR }
    }

    val sourceHealth = MutableLiveData(SourceHealth())

    /** 统一标记数据源健康状态 (合并原 markHealthy/markError 消灭 45 行重复) */
    private fun markHealth(health: SourceHealth.Health, vararg names: String) {
        val current = sourceHealth.value ?: return
        var h = current
        names.forEach { n ->
            h = when (n) {
                "cpu" -> h.copy(cpu = health)
                "gpu" -> h.copy(gpu = health)
                "battery" -> h.copy(battery = health)
                "memory" -> h.copy(memory = health)
                "storage" -> h.copy(storage = health)
                "wifi" -> h.copy(wifi = health)
                "mobile" -> h.copy(mobileNetwork = health)
                "netif" -> h.copy(networkInterface = health)
                "gps" -> h.copy(gps = health)
                "sensors" -> h.copy(sensors = health)
                "system" -> h.copy(system = health)
                "device" -> h.copy(deviceDetail = health)
                "oem" -> h.copy(oem = health)
                else -> h
            }
        }
        sourceHealth.postValue(h)
    }

    // DataSources
    private val cpuDataSource = CpuDataSource(appContext)
    private val gpuDataSource = GpuDataSource()
    private val batteryDataSource = BatteryDataSource(appContext)
    private val memoryDataSource = MemoryDataSource()
    private val storageDataSource = StorageDataSource()
    private val wifiDataSource = WifiDataSource(appContext)
    private val mobileNetworkDataSource = MobileNetworkDataSource(appContext)
    private val networkInterfaceDataSource = NetworkInterfaceDataSource()
    private val gpsDataSource = GpsDataSource(appContext)
    private val sensorDataSource = SensorDataSource(appContext)
    private val systemDataSource = SystemDataSource()
    private val deviceDetailDataSource = DeviceDetailDataSource(appContext)
    private val oemDataSource = OemDataSource(appContext)

    // 处理器预缓存 — 匹配平台时注入精确信息（SystemProperties 反射）
    private val cachedChip: CpuCache.KnownChip? by lazy {
        val platform = try {
            val sp = Class.forName("android.os.SystemProperties")
            sp.getMethod("get", String::class.java, String::class.java)
                .invoke(null, "ro.board.platform", "") as? String ?: ""
        } catch (_: Throwable) { "" }
        CpuCache.lookup(platform)
    }

    // History
    val historyCache = HistoryCache()

    // LiveData — 单向数据流 (只读视图)
    val cpuLiveData = MutableLiveData<CpuInfo>()
    val gpuLiveData = MutableLiveData<GpuInfo>()
    val batteryLiveData = MutableLiveData<BatteryInfo>()
    val memoryLiveData = MutableLiveData<MemoryInfo>()
    val storageLiveData = MutableLiveData<StorageInfo>()
    val wifiLiveData = MutableLiveData<WifiDetailInfo>()
    val mobileNetworkLiveData = MutableLiveData<MobileNetworkInfo>()
    val networkInterfacesLiveData = MutableLiveData<List<NetworkInterfaceInfo>>()
    val gpsLiveData = MutableLiveData<GpsStatusInfo>()
    val sensorsLiveData = MutableLiveData<List<SensorItemInfo>>()
    val systemLiveData = MutableLiveData<SystemInfo>()
    val deviceDetailLiveData = MutableLiveData<DeviceDetailInfo>()
    val oemLiveData = MutableLiveData<OemInfo>()

    // 传感器实时数据 — 第二层详情页专用
    val sensorLiveData = MutableLiveData<SensorLiveData>()

    // 历史图表数据 — Compose 可观察
    val historyData = MutableLiveData<Map<String, List<HistoryDataPoint>>>(emptyMap())

    // 传感器历史数据 — 详情页图表专用
    val sensorHistoryData = MutableLiveData<Map<String, List<HistoryDataPoint>>>(emptyMap())

    // Coroutine
    @Volatile
    private var intervalMs = RefreshPolicy.Tier.NORMAL.defaultMs
    private var monitoringJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Volatile
    private var monitoring = false

    /** ★ RefreshPolicy 状态观察者 — 前后台切换自动调速 */
    private var policyObserverJob: Job? = null

    /**
     * 启动后台数据采集（幂等）
     * 使用 RefreshPolicy 分层刷新策略，前后台自动切换
     */
    fun startMonitoring(intervalMs: Long = RefreshPolicy.Tier.NORMAL.defaultMs) {
        if (monitoring) return
        monitoring = true
        this.intervalMs = intervalMs

        monitoringJob = scope.launch {
            collectData()  // 立即采集第一帧数据，消除启动空白期
            while (isActive && monitoring) {
                val tickStart = System.currentTimeMillis()
                collectData()
                val elapsed = System.currentTimeMillis() - tickStart
                val remaining = intervalMs - elapsed
                if (remaining > 0) delay(remaining)
            }
        }

        // ★ 启动 RefreshPolicy 观察者: 状态变更时自动调整间隔
        policyObserverJob = scope.launch {
            RefreshPolicy.state.collect { refreshState ->
                val newMs = RefreshPolicy.effectiveMs(RefreshPolicy.Tier.NORMAL)
                Log.d(TAG, "RefreshPolicy state=${refreshState.name}, adjusting interval to ${newMs}ms")
                // 仅在间隔实际变化时调用重启循环的 setIntervalMs
                if (newMs != this@DeviceRepository.intervalMs) {
                    setIntervalMs(newMs)
                }
            }
        }

        // GPS 不再在此无条件启动，改为由 UI 层按 Tab 智能控制
        // 仅在 GPS/网络 Tab 时调用 enableGps()，离开时调用 disableGps()
    }

    // GPS 状态 — 由 UI 层按 Tab 控制
    @Volatile
    private var gpsEnabled = false

    fun enableGps() {
        if (gpsEnabled) return
        gpsEnabled = true
        try {
            gpsDataSource.startListening { gpsLiveData.postValue(it) }
        } catch (e: Throwable) { Log.w(TAG, "GPS监听启动失败", e) }
    }

    fun disableGps() {
        if (!gpsEnabled) return
        gpsEnabled = false
        try {
            gpsDataSource.stopListening()
        } catch (e: Throwable) { Log.w(TAG, "GPS监听停止失败", e) }
    }

    // 传感器实时采集 — 仅随第二层详情页生命周期管理
    @Volatile
    private var sensorListening = false

    fun enableSensor(sensorType: Int) {
        if (sensorListening) {
            disableSensor()
        }
        sensorListening = true
        try {
            sensorDataSource.startListening(sensorType) { liveData ->
                // 推送实时数据
                sensorLiveData.postValue(liveData)
                // 同时写入历史缓存用于图表
                val meta = SensorTypeMeta.fromTypeId(sensorType)
                val seriesPrefix = "sensor_${sensorType}"
                when (liveData.valueCount) {
                    in 1..3 -> {
                        val labels = meta?.axisLabelResIds?.map { appContext.getString(it) }
                            ?: listOf("X", "Y", "Z")
                        for (i in 0 until liveData.valueCount) {
                            if (!liveData.values[i].isNaN()) {
                                historyCache.addPoint("${seriesPrefix}_${labels.getOrElse(i) { "$i" }}", liveData.values[i])
                            }
                        }
                    }
                }
                // ★ 关键修复: 写完缓存后立即推送到 sensorHistoryData LiveData
                pushSensorHistory(sensorType)
            }
        } catch (e: Throwable) { Log.w(TAG, "传感器实时监听启动失败", e) }
    }

    fun disableSensor() {
        if (!sensorListening) return
        sensorListening = false
        try {
            sensorDataSource.stopListening()
            // 清空传感器历史数据，避免切换传感器时数据混乱
            historyCache.clearSensorSeries()
            sensorHistoryData.postValue(emptyMap())
            Log.d(TAG, "传感器实时监听已停止，历史缓存已清空")
        } catch (e: Throwable) { Log.w(TAG, "传感器监听停止失败", e) }
    }

    /**
     * 推送传感器图表数据到 Compose
     */
    fun pushSensorHistory(sensorType: Int) {
        val meta = SensorTypeMeta.fromTypeId(sensorType) ?: return
        val prefix = "sensor_${sensorType}"
        val labels = meta.axisLabelResIds.map { appContext.getString(it) }
        val map = mutableMapOf<String, List<HistoryDataPoint>>()
        for (label in labels) {
            map["${prefix}_${label}"] = historyCache.getSeries("${prefix}_${label}")
        }
        sensorHistoryData.postValue(map)
    }

    fun stopMonitoring() {
        monitoring = false
        monitoringJob?.cancel()
        policyObserverJob?.cancel()
        disableGps()
        disableSensor()
    }

    /**
     * 释放所有资源，仅供 Application.onTerminate() 调用
     */
    fun shutdown() {
        stopMonitoring()
        historyCache.shutdown()
    }

    private suspend fun collectData() = coroutineScope {
        // ★ 性能优化 (2026-06-19): 并行采集各 DataSource
        // 原方案 12+ DataSource 串行，sysfs/shell 读取阻塞，单轮耗时被拖长（CPU 温度 fallback
        // 链 + cpuidle 8 核扫描尤其重）。各 DataSource 间无共享状态（CPU 内部 prev 统计仅在
        // CPU 块内调用），可安全并行。awaitAll 后统一推送历史数据。
        val jobs = listOf(
            async {
                runCatching {
                    val cpu = cpuDataSource.getCpuInfo()
                    val perCoreUsage = cpuDataSource.getPerCoreUsage()
                    cpu.cores.forEach { core ->
                        core.usagePercent = perCoreUsage[core.coreIndex] ?: Float.NaN
                    }
                    cachedChip?.let { CpuCache.injectCpuInfo(it, cpu) }
                    cpuLiveData.postValue(cpu)
                    if (!cpu.temperatureCelsius.isNaN())
                        historyCache.addPoint("cpu_temp", cpu.temperatureCelsius)
                    val maxFreq = cpu.cores.maxOfOrNull { it.currentFreqKHz } ?: 0L
                    if (maxFreq > 0) historyCache.addPoint("cpu_freq", maxFreq.toFloat())
                    if (!cpu.cpuUsagePercent.isNaN())
                        historyCache.addPoint("cpu_usage", cpu.cpuUsagePercent)
                    if (!cpu.deepSleepPercent.isNaN())
                        historyCache.addPoint("cpu_deep_sleep", cpu.deepSleepPercent)
                    markHealth(SourceHealth.Health.OK, "cpu")
                }.onFailure { e ->
                    Log.w(TAG, "CPU采集失败", e)
                    markHealth(SourceHealth.Health.ERROR, "cpu")
                }
            },
            async {
                runCatching {
                    val gpu = gpuDataSource.getGpuInfo()
                    cachedChip?.let { CpuCache.injectGpuInfo(it, gpu) }
                    gpu.isThrottled = gpu.maxFreqKHz > 0 && gpu.frequencyKHz > 0
                        && gpu.frequencyKHz < gpu.maxFreqKHz * 0.7f
                    gpuLiveData.postValue(gpu)
                    if (!gpu.loadPercentage.isNaN())
                        historyCache.addPoint("gpu_load", gpu.loadPercentage)
                    if (!gpu.temperatureCelsius.isNaN())
                        historyCache.addPoint("gpu_temp", gpu.temperatureCelsius)
                    if (gpu.frequencyKHz > 0 && gpu.maxFreqKHz > 0)
                        historyCache.addPoint("gpu_freq", gpu.frequencyKHz.toFloat() / gpu.maxFreqKHz * 100f)
                    markHealth(SourceHealth.Health.OK, "gpu")
                }.onFailure { e ->
                    Log.w(TAG, "GPU采集失败", e)
                    markHealth(SourceHealth.Health.ERROR, "gpu")
                }
            },
            async {
                runCatching {
                    val bat = batteryDataSource.getBatteryInfo()
                    batteryLiveData.postValue(bat)
                    if (!bat.temperatureCelsius.isNaN())
                        historyCache.addPoint("battery_temp", bat.temperatureCelsius)
                    val batPowerMw = if (bat.isCharging) bat.chargingPowerMw else bat.dischargingPowerMw
                    if (batPowerMw >= 0)
                        historyCache.addPoint("battery_power", batPowerMw.toFloat())
                    if (bat.levelPercent >= 0)
                        historyCache.addPoint("battery_level", bat.levelPercent.toFloat())
                    // 瓦特数历史追踪 (2026-06-18)
                    if (!bat.wattageNow.isNaN() && bat.wattageNow > 0)
                        historyCache.addPoint("battery_wattage", bat.wattageNow.toFloat())
                    // 容量衰减追踪 (charge_full / charge_full_design)
                    if (bat.chargeFullMAh > 0 && bat.chargeFullDesignMAh > 0) {
                        val soh = bat.chargeFullMAh.toFloat() / bat.chargeFullDesignMAh.toFloat() * 100f
                        historyCache.addPoint("battery_soh", soh)
                        historyCache.addPoint("battery_charge_full", bat.chargeFullMAh.toFloat())
                    }
                    // 内阻追踪
                    if (!bat.internalResistanceMOhm.isNaN() && bat.internalResistanceMOhm > 0) {
                        historyCache.addPoint("battery_resistance", bat.internalResistanceMOhm)
                    }
                    markHealth(SourceHealth.Health.OK, "battery")
                }.onFailure { e ->
                    Log.w(TAG, "电池采集失败", e)
                    markHealth(SourceHealth.Health.ERROR, "battery")
                }
            },
            async {
                runCatching {
                    val mem = memoryDataSource.getMemoryInfo()
                    memoryLiveData.postValue(mem)
                    if (mem.totalKB > 0) {
                        val pct = mem.usedKB.toFloat() / mem.totalKB * 100f
                        historyCache.addPoint("ram_usage", pct)
                    }
                    markHealth(SourceHealth.Health.OK, "memory")
                }.onFailure { e ->
                    Log.w(TAG, "内存采集失败", e)
                    markHealth(SourceHealth.Health.ERROR, "memory")
                }
            },
            async {
                runCatching { storageLiveData.postValue(storageDataSource.getStorageInfo()); markHealth(SourceHealth.Health.OK, "storage") }
                    .onFailure { e -> Log.w(TAG, "存储采集失败", e); markHealth(SourceHealth.Health.ERROR, "storage") }
            },
            async {
                runCatching {
                    val wifi = wifiDataSource.getWifiDetail()
                    wifiLiveData.postValue(wifi)
                    if (wifi.linkSpeedMbps > 0)
                        historyCache.addPoint("wifi_speed", wifi.linkSpeedMbps.toFloat())
                    markHealth(SourceHealth.Health.OK, "wifi")
                }.onFailure { e ->
                    Log.w(TAG, "WiFi采集失败", e)
                    markHealth(SourceHealth.Health.ERROR, "wifi")
                }
            },
            async {
                runCatching {
                    val mobile = mobileNetworkDataSource.getMobileNetworkInfo()
                    mobileNetworkLiveData.postValue(mobile)
                    val signalDbm = mobile.signalStrengthDbm
                    if (signalDbm > Int.MIN_VALUE && signalDbm < 0)
                        historyCache.addPoint("signal_strength", signalDbm.toFloat())
                    markHealth(SourceHealth.Health.OK, "mobile")
                }.onFailure { e ->
                    Log.w(TAG, "移动网络采集失败", e)
                    markHealth(SourceHealth.Health.ERROR, "mobile")
                }
            },
            async {
                runCatching {
                    networkInterfacesLiveData.postValue(networkInterfaceDataSource.getNetworkInterfaces())
                    markHealth(SourceHealth.Health.OK, "netif")
                }.onFailure { e ->
                    Log.w(TAG, "网卡信息采集失败", e)
                    markHealth(SourceHealth.Health.ERROR, "netif")
                }
            },
            async {
                runCatching {
                    gpsDataSource.checkGpsStatus()?.let { status -> gpsLiveData.postValue(status) }
                }.onFailure { e ->
                    Log.w(TAG, "GPS状态检查失败", e)
                }
            }
        )
        jobs.awaitAll()

        // 推送历史数据给 Compose 图表
        // ★ 性能优化 (2026-06-19): 用 getRecentSeries(80) 替代 getSeries()
        //   图表组件实际只显示最近 80 点，推送 80 点快照即可
        //   原方案每轮拷贝 15 series × ~1800 点(1小时窗口) = 27000 点，现降至 1200 点
        // ★ 性能优化 (2026-06-21): 复用 HashMap 替代每轮 mapOf(15 entries) 分配
        //   mapOf 每次创建不可变 Map + 15 Entry 对象，热路径每 2s 执行一次
        historyData.postValue(HashMap<String, List<HistoryDataPoint>>(15).apply {
            put("cpu_temp", historyCache.getRecentSeries("cpu_temp", 80))
            put("cpu_freq", historyCache.getRecentSeries("cpu_freq", 80))
            put("cpu_usage", historyCache.getRecentSeries("cpu_usage", 80))
            put("gpu_load", historyCache.getRecentSeries("gpu_load", 80))
            put("gpu_temp", historyCache.getRecentSeries("gpu_temp", 80))
            put("battery_temp", historyCache.getRecentSeries("battery_temp", 80))
            put("battery_power", historyCache.getRecentSeries("battery_power", 80))
            put("battery_level", historyCache.getRecentSeries("battery_level", 80))
            put("battery_wattage", historyCache.getRecentSeries("battery_wattage", 80))
            put("battery_soh", historyCache.getRecentSeries("battery_soh", 80))
            put("battery_charge_full", historyCache.getRecentSeries("battery_charge_full", 80))
            put("battery_resistance", historyCache.getRecentSeries("battery_resistance", 80))
            put("ram_usage", historyCache.getRecentSeries("ram_usage", 80))
            put("wifi_speed", historyCache.getRecentSeries("wifi_speed", 80))
            put("signal_strength", historyCache.getRecentSeries("signal_strength", 80))
        })
    }

    fun loadStaticData() {
        scope.launch(Dispatchers.Default) {
            runCatching { systemLiveData.postValue(systemDataSource.getSystemInfo()); markHealth(SourceHealth.Health.OK, "system") }
                .onFailure { e -> Log.w(TAG, "系统信息采集失败", e); markHealth(SourceHealth.Health.ERROR, "system") }
            runCatching { storageLiveData.postValue(storageDataSource.getStorageInfo()) }
                .onFailure { e -> Log.w(TAG, "存储信息采集失败", e) }
            runCatching { sensorsLiveData.postValue(sensorDataSource.getAllSensors()); markHealth(SourceHealth.Health.OK, "sensors") }
                .onFailure { e -> Log.w(TAG, "传感器列表采集失败", e); markHealth(SourceHealth.Health.ERROR, "sensors") }
            runCatching { deviceDetailLiveData.postValue(deviceDetailDataSource.collect()); markHealth(SourceHealth.Health.OK, "device") }
                .onFailure { e -> Log.w(TAG, "设备详情采集失败", e); markHealth(SourceHealth.Health.ERROR, "device") }
            runCatching { oemLiveData.postValue(oemDataSource.collect()); markHealth(SourceHealth.Health.OK, "oem") }
                .onFailure { e -> Log.w(TAG, "OEM信息采集失败", e); markHealth(SourceHealth.Health.ERROR, "oem") }
        }
    }

    fun setIntervalMs(ms: Long) {
        if (ms == this.intervalMs) return
        this.intervalMs = ms
        // 重启监控协程，使新 interval 立刻生效 (解决 delay() 可能缓冲旧值的问题)
        monitoringJob?.cancel()
        if (monitoring) {
            monitoringJob = scope.launch {
                collectData()
                while (isActive && monitoring) {
                    val tickStart = System.currentTimeMillis()
                    collectData()
                    val elapsed = System.currentTimeMillis() - tickStart
                    val remaining = intervalMs - elapsed
                    if (remaining > 0) delay(remaining)
                }
            }
        }
    }

    fun getIntervalMs(): Long = intervalMs

    // ── 电池脉冲事件流 (2026-06-18) ──
    /** 暴露电池实时脉冲事件 Flow，用于 Compose 细粒度重组 */
    fun batteryPulseFlow(): Flow<BatteryDataSource.BatteryPulseEvent> =
        batteryDataSource.monitorBatteryPulses()

    // ── 分模块刷新间隔 (读写 AppSettings) ──
    fun setCpuRefreshMs(ms: Long) = AppSettings.getInstance(appContext).apply { cpuRefreshMs = ms.toInt() }
    fun getCpuRefreshMs(): Long = AppSettings.getInstance(appContext).cpuRefreshMs.toLong()
    fun setGpuRefreshMs(ms: Long) = AppSettings.getInstance(appContext).apply { gpuRefreshMs = ms.toInt() }
    fun getGpuRefreshMs(): Long = AppSettings.getInstance(appContext).gpuRefreshMs.toLong()
    fun setMemoryRefreshMs(ms: Long) = AppSettings.getInstance(appContext).apply { memoryRefreshMs = ms.toInt() }
    fun getMemoryRefreshMs(): Long = AppSettings.getInstance(appContext).memoryRefreshMs.toLong()
    fun setBatteryRefreshMs(ms: Long) = AppSettings.getInstance(appContext).apply { batteryRefreshMs = ms.toInt() }
    fun getBatteryRefreshMs(): Long = AppSettings.getInstance(appContext).batteryRefreshMs.toLong()
    fun setNetworkRefreshMs(ms: Long) = AppSettings.getInstance(appContext).apply { networkRefreshMs = ms.toInt() }
    fun getNetworkRefreshMs(): Long = AppSettings.getInstance(appContext).networkRefreshMs.toLong()
    fun setGpsRefreshMs(ms: Long) = AppSettings.getInstance(appContext).apply { gpsRefreshMs = ms.toInt() }
    fun getGpsRefreshMs(): Long = AppSettings.getInstance(appContext).gpsRefreshMs.toLong()
    fun setSensorsRefreshMs(ms: Long) = AppSettings.getInstance(appContext).apply { sensorsRefreshMs = ms.toInt() }
    fun getSensorsRefreshMs(): Long = AppSettings.getInstance(appContext).sensorsRefreshMs.toLong()
}
