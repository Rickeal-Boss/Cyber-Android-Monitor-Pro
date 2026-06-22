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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    /** 统一标记数据源健康状态。★ 优化: 状态未变时跳过 copy+postValue */
    private fun markHealth(health: SourceHealth.Health, vararg names: String) {
        val current = sourceHealth.value ?: return
        // 快速路径: 检查是否有任何字段实际需要更新
        val needsUpdate = names.any { n ->
            when (n) {
                "cpu" -> current.cpu != health
                "gpu" -> current.gpu != health
                "battery" -> current.battery != health
                "memory" -> current.memory != health
                "storage" -> current.storage != health
                "wifi" -> current.wifi != health
                "mobile" -> current.mobileNetwork != health
                "netif" -> current.networkInterface != health
                "gps" -> current.gps != health
                "sensors" -> current.sensors != health
                "system" -> current.system != health
                "device" -> current.deviceDetail != health
                "oem" -> current.oem != health
                else -> false
            }
        }
        if (!needsUpdate) return  // ★ 95%+ 的 tick 在此返回，零分配

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
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Volatile
    private var monitoring = false

    // ★ 每模块独立 Job — 间隔变更时 flatMapLatest 自动销毁并重建
    private var cpuJob: Job? = null
    private var gpuJob: Job? = null
    private var memoryJob: Job? = null
    private var batteryJob: Job? = null
    private var auxJob: Job? = null  // WiFi/Mobile/Storage/NetIf/GPS 辅助模块

    // ★ 模块刷新间隔 StateFlow — 变更时 flatMapLatest 自动热切换
    private val cpuIntervalFlow = MutableStateFlow(intervalMs)
    private val gpuIntervalFlow = MutableStateFlow(intervalMs)
    private val memIntervalFlow = MutableStateFlow(intervalMs)
    private val batIntervalFlow = MutableStateFlow(intervalMs)

    /** ★ RefreshPolicy 状态观察者 — 前后台切换自动调速 */
    private var policyObserverJob: Job? = null

    /**
     * 启动后台数据采集（幂等）
     *
     * ★ 架构重构 (2026-06-21): 参考竞品 pollingFlow + flatMapLatest 模式，
     *   每模块独立轮询流，间隔变更时自动热切换，无需手动重启。
     *   CPU/GPU/Memory/Battery 各有独立 intervalFlow，
     *   WiFi/Mobile/Storage/NetIf/GPS 共享辅助流随全局间隔。
     */
    fun startMonitoring(intervalMs: Long = RefreshPolicy.Tier.NORMAL.defaultMs) {
        if (monitoring) return
        monitoring = true
        this.intervalMs = intervalMs
        pushAllIntervalFlows(intervalMs)

        // ★ CPU 独立轮询流
        cpuJob = PollingFlow.launchModulePolling("CPU", cpuIntervalFlow, scope) {
            collectCpuBlock()
        }
        // ★ GPU 独立轮询流
        gpuJob = PollingFlow.launchModulePolling("GPU", gpuIntervalFlow, scope) {
            collectGpuBlock()
        }
        // ★ Memory 独立轮询流
        memoryJob = PollingFlow.launchModulePolling("Memory", memIntervalFlow, scope) {
            collectMemoryBlock()
        }
        // ★ Battery 独立轮询流
        batteryJob = PollingFlow.launchModulePolling("Battery", batIntervalFlow, scope) {
            collectBatteryBlock()
        }
        // ★ 辅助模块 (WiFi/Mobile/Storage/NetIf/GPS) + 历史数据推送 — 共享全局间隔
        auxJob = PollingFlow.launchModulePolling("Aux", cpuIntervalFlow, scope, immediate = false) {
            collectData()
        }

        // ★ RefreshPolicy 观察者: 前后台切换 → 统一更新所有模块间隔
        policyObserverJob = scope.launch {
            RefreshPolicy.state.collect { refreshState ->
                val newMs = RefreshPolicy.effectiveMs(RefreshPolicy.Tier.NORMAL)
                Log.d(TAG, "RefreshPolicy state=${refreshState.name}, adjusting all modules to ${newMs}ms")
                setIntervalMs(newMs)
            }
        }
    }

    private fun pushAllIntervalFlows(baseMs: Long) {
        val settings = AppSettings.getInstance(appContext)
        cpuIntervalFlow.value = settings.effectiveRefreshMs(settings.cpuRefreshMs).toLong()
        gpuIntervalFlow.value = settings.effectiveRefreshMs(settings.gpuRefreshMs).toLong()
        memIntervalFlow.value = settings.effectiveRefreshMs(settings.memoryRefreshMs).toLong()
        batIntervalFlow.value = settings.effectiveRefreshMs(settings.batteryRefreshMs).toLong()
    }

    // ═══════ 每模块独立采集块 (原在 collectData 的 async 块中) ═══════

    private suspend fun collectCpuBlock() {
        try {
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
        } catch (e: Throwable) {
            Log.w(TAG, "CPU采集失败", e)
            markHealth(SourceHealth.Health.ERROR, "cpu")
        }
    }

    private suspend fun collectGpuBlock() {
        try {
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
        } catch (e: Throwable) {
            Log.w(TAG, "GPU采集失败", e)
            markHealth(SourceHealth.Health.ERROR, "gpu")
        }
    }

    private suspend fun collectMemoryBlock() {
        try {
            val mem = memoryDataSource.getMemoryInfo()
            memoryLiveData.postValue(mem)
            if (mem.totalKB > 0) {
                historyCache.addPoint("ram_usage", mem.usedKB.toFloat() / mem.totalKB * 100f)
            }
            markHealth(SourceHealth.Health.OK, "memory")
        } catch (e: Throwable) {
            Log.w(TAG, "内存采集失败", e)
            markHealth(SourceHealth.Health.ERROR, "memory")
        }
    }

    private suspend fun collectBatteryBlock() {
        try {
            val bat = batteryDataSource.getBatteryInfo()
            batteryLiveData.postValue(bat)
            if (!bat.temperatureCelsius.isNaN())
                historyCache.addPoint("battery_temp", bat.temperatureCelsius)
            val batPowerMw = if (bat.isCharging) bat.chargingPowerMw else bat.dischargingPowerMw
            if (batPowerMw >= 0) historyCache.addPoint("battery_power", batPowerMw.toFloat())
            if (bat.levelPercent >= 0) historyCache.addPoint("battery_level", bat.levelPercent.toFloat())
            if (!bat.wattageNow.isNaN() && bat.wattageNow > 0)
                historyCache.addPoint("battery_wattage", bat.wattageNow.toFloat())
            if (bat.chargeFullMAh > 0 && bat.chargeFullDesignMAh > 0) {
                historyCache.addPoint("battery_soh", bat.chargeFullMAh.toFloat() / bat.chargeFullDesignMAh.toFloat() * 100f)
                historyCache.addPoint("battery_charge_full", bat.chargeFullMAh.toFloat())
            }
            if (!bat.internalResistanceMOhm.isNaN() && bat.internalResistanceMOhm > 0)
                historyCache.addPoint("battery_resistance", bat.internalResistanceMOhm)
            markHealth(SourceHealth.Health.OK, "battery")
        } catch (e: Throwable) {
            Log.w(TAG, "电池采集失败", e)
            markHealth(SourceHealth.Health.ERROR, "battery")
        }
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
        cpuJob?.cancel(); gpuJob?.cancel(); memoryJob?.cancel(); batteryJob?.cancel(); auxJob?.cancel()
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

    // ★ 辅助模块采集 (WiFi/Mobile/Storage/NetIf/GPS) + 历史数据推送
    //   CPU/GPU/Memory/Battery 已提升为独立 polling flow
    private suspend fun collectData() = coroutineScope {
        val jobs = listOf(
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
                }.onFailure { e -> Log.w(TAG, "WiFi采集失败", e); markHealth(SourceHealth.Health.ERROR, "wifi") }
            },
            async {
                runCatching {
                    val mobile = mobileNetworkDataSource.getMobileNetworkInfo()
                    mobileNetworkLiveData.postValue(mobile)
                    val signalDbm = mobile.signalStrengthDbm
                    if (signalDbm > Int.MIN_VALUE && signalDbm < 0)
                        historyCache.addPoint("signal_strength", signalDbm.toFloat())
                    markHealth(SourceHealth.Health.OK, "mobile")
                }.onFailure { e -> Log.w(TAG, "移动网络采集失败", e); markHealth(SourceHealth.Health.ERROR, "mobile") }
            },
            async {
                runCatching {
                    networkInterfacesLiveData.postValue(networkInterfaceDataSource.getNetworkInterfaces())
                    markHealth(SourceHealth.Health.OK, "netif")
                }.onFailure { e -> Log.w(TAG, "网卡信息采集失败", e); markHealth(SourceHealth.Health.ERROR, "netif") }
            },
            async {
                runCatching {
                    gpsDataSource.checkGpsStatus()?.let { status -> gpsLiveData.postValue(status) }
                }.onFailure { e -> Log.w(TAG, "GPS状态检查失败", e) }
            }
        )
        jobs.awaitAll()

        // 推送历史数据给 Compose 图表
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
        // ★ flatMapLatest 自动热切换: 推入 StateFlow 即可使所有模块间隔即时生效
        //   无需手动 cancel/restart，每个 polling flow 的 flatMapLatest 会自动销毁旧流创建新流
        pushAllIntervalFlows(ms)
    }

    fun getIntervalMs(): Long = intervalMs

    // ── 电池脉冲事件流 (2026-06-18) ──
    /** 暴露电池实时脉冲事件 Flow，用于 Compose 细粒度重组 */
    fun batteryPulseFlow(): Flow<BatteryDataSource.BatteryPulseEvent> =
        batteryDataSource.monitorBatteryPulses()

    // ── 分模块刷新间隔 — 仅 CPU/GPU/Memory/Battery 支持 ──
    //   set 写入 AppSettings + 即时推入 StateFlow → flatMapLatest 自动热切换

    fun setCpuRefreshMs(ms: Long) {
        AppSettings.getInstance(appContext).cpuRefreshMs = ms.toInt()
        cpuIntervalFlow.value = AppSettings.getInstance(appContext).effectiveRefreshMs(ms.toInt()).toLong()
    }
    fun getCpuRefreshMs(): Long = AppSettings.getInstance(appContext).cpuRefreshMs.toLong()
    fun setGpuRefreshMs(ms: Long) {
        AppSettings.getInstance(appContext).gpuRefreshMs = ms.toInt()
        gpuIntervalFlow.value = AppSettings.getInstance(appContext).effectiveRefreshMs(ms.toInt()).toLong()
    }
    fun getGpuRefreshMs(): Long = AppSettings.getInstance(appContext).gpuRefreshMs.toLong()
    fun setMemoryRefreshMs(ms: Long) {
        AppSettings.getInstance(appContext).memoryRefreshMs = ms.toInt()
        memIntervalFlow.value = AppSettings.getInstance(appContext).effectiveRefreshMs(ms.toInt()).toLong()
    }
    fun getMemoryRefreshMs(): Long = AppSettings.getInstance(appContext).memoryRefreshMs.toLong()
    fun setBatteryRefreshMs(ms: Long) {
        AppSettings.getInstance(appContext).batteryRefreshMs = ms.toInt()
        batIntervalFlow.value = AppSettings.getInstance(appContext).effectiveRefreshMs(ms.toInt()).toLong()
    }
    fun getBatteryRefreshMs(): Long = AppSettings.getInstance(appContext).batteryRefreshMs.toLong()
}
