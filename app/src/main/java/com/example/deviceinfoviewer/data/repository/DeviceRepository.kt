package com.example.deviceinfoviewer.data.repository

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.example.deviceinfoviewer.AppSettings
import com.example.deviceinfoviewer.RefreshPolicy
import com.example.deviceinfoviewer.data.model.*
import com.example.deviceinfoviewer.data.source.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*

/** Core data repository using Kotlin coroutines and SharedFlow */
class DeviceRepository(context: Context) {
    private val appContext: Context = context.applicationContext

    companion object {
        const val TAG = "DeviceRepo"
    }

    // ═══════ 提取的独立类 ═══════
    val healthTracker = HealthTracker()
    val sourceHealth get() = healthTracker.liveData  // 向后兼容
    private val auxCollector = AuxiliaryCollector(appContext)

    // ═══════ DataSources (核心 5 模块 + 静态) ═══════
    private val cpuDataSource = CpuDataSource(appContext)
    private val gpuDataSource = GpuDataSource()
    private val batteryDataSource = BatteryDataSource(appContext)
    private val memoryDataSource = MemoryDataSource()
    private val sensorDataSource = SensorDataSource(appContext)
    private val systemDataSource = SystemDataSource()
    private val deviceDetailDataSource = DeviceDetailDataSource(appContext)
    private val oemDataSource = OemDataSource(appContext)

    // ═══════ CpuCache ═══════
    private val cachedChip: CpuCache.KnownChip? by lazy {
        // ★ Android 12+ (API31) 官方 SoC 字段优先：Build.SOC_MODEL 直接给出型号(如 mt6989)与厂商(MediaTek)
        //   仅在 API31+ 可达时使用，作为比 ro.board.platform 更高优先级的候选；
        //   minSdk=21，故旧路径 (ro.board.platform → ro.hardware.chipname) 必须保留为降级链。
        val socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL.trim().lowercase()
        } else ""
        val platform = if (socModel.isNotEmpty()) socModel
        else SysFsReader.readProp("ro.board.platform")
        val resolved = if (platform.isNotEmpty()) platform
        else SysFsReader.readProp("ro.board.platform")
        val finalPlatform = if (resolved.isNotEmpty()) resolved
        else SysFsReader.readProp("ro.hardware.chipname")
        CpuCache.lookup(finalPlatform)
    }

    // ═══════ History + 静态数据 ═══════
    val historyCache = HistoryCache()

    // ═══════ SharedFlow 输出 (悬浮窗消费) ═══════
    // replay=1 ensures new subscribers get latest value; DROP_OLDEST prevents backpressure
    // 注: SharedFlow 当前仅被 FloatingWindowService 消费 (悬浮窗实时刷新);
    //     UI 主屏经下方 LiveData 消费 (P2#8 见说明)。
    private val _cpuFlow = MutableSharedFlow<CpuInfo>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val cpuFlow: SharedFlow<CpuInfo> = _cpuFlow.asSharedFlow()

    private val _gpuFlow = MutableSharedFlow<GpuInfo>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val gpuFlow: SharedFlow<GpuInfo> = _gpuFlow.asSharedFlow()

    private val _memoryFlow = MutableSharedFlow<MemoryInfo>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val memoryFlow: SharedFlow<MemoryInfo> = _memoryFlow.asSharedFlow()

    private val _batteryFlow = MutableSharedFlow<BatteryInfo>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val batteryFlow: SharedFlow<BatteryInfo> = _batteryFlow.asSharedFlow()

    // ═══════ LiveData (UI 主用 — 暂保留) ═══════
    // 全部 ViewModel (Dashboard/Cpu/Gpu/Memory/Battery) + ExportHelper 经此消费,
    // 故为当前 UI 主数据管道; SharedFlow 并行 emit 以服务悬浮窗。
    // P2#8: 完整迁移 (LiveData → SharedFlow + collectAsStateWithLifecycle) 属大型 UI 改动,
    //       需严格审查 + 真机验证, 当前保留双管道 (collect 块内双写在后续去重)。
    val cpuLiveData = MutableLiveData<CpuInfo>()
    val gpuLiveData = MutableLiveData<GpuInfo>()
    val memoryLiveData = MutableLiveData<MemoryInfo>()
    val batteryLiveData = MutableLiveData<BatteryInfo>()

    // 辅助模块 LiveData (委托 AuxiliaryCollector)
    val storageLiveData get() = auxCollector.storageLiveData
    val wifiLiveData get() = auxCollector.wifiLiveData
    val mobileNetworkLiveData get() = auxCollector.mobileNetworkLiveData
    val networkInterfacesLiveData get() = auxCollector.networkInterfacesLiveData
    val gpsLiveData get() = auxCollector.gpsLiveData

    // 静态模块 LiveData
    val sensorsLiveData = MutableLiveData<List<SensorItemInfo>>()
    val systemLiveData = MutableLiveData<SystemInfo>()
    val deviceDetailLiveData = MutableLiveData<DeviceDetailInfo>()
    val oemLiveData = MutableLiveData<OemInfo>()

    // 传感器
    val sensorLiveData = MutableLiveData<SensorLiveData>()
    val sensorHistoryData = MutableLiveData<Map<String, List<HistoryDataPoint>>>(emptyMap())

    // 聚合历史数据
    val historyData = MutableLiveData<Map<String, List<HistoryDataPoint>>>(emptyMap())

    // ═══════ 协程调度 ═══════
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile private var monitoring = false
    @Volatile private var intervalMs: Long = RefreshPolicy.Tier.NORMAL.defaultMs

    private var cpuJob: Job? = null
    private var gpuJob: Job? = null
    private var memoryJob: Job? = null
    private var batteryJob: Job? = null
    private var auxJob: Job? = null
    private var policyObserverJob: Job? = null

    private val cpuIntervalFlow = MutableStateFlow(RefreshPolicy.Tier.NORMAL.defaultMs)
    private val gpuIntervalFlow = MutableStateFlow(RefreshPolicy.Tier.NORMAL.defaultMs)
    private val memIntervalFlow = MutableStateFlow(RefreshPolicy.Tier.NORMAL.defaultMs)
    private val batIntervalFlow = MutableStateFlow(RefreshPolicy.Tier.NORMAL.defaultMs)

    // ═══════ 生命周期 ═══════

    fun startMonitoring(intervalMs: Long = RefreshPolicy.Tier.NORMAL.defaultMs) {
        if (monitoring) return
        monitoring = true
        this.intervalMs = intervalMs
        pushAllIntervalFlows(intervalMs)

        cpuJob = PollingFlow.launchModulePolling("CPU", cpuIntervalFlow, scope) { collectCpuBlock() }
        gpuJob = PollingFlow.launchModulePolling("GPU", gpuIntervalFlow, scope) { collectGpuBlock() }
        memoryJob = PollingFlow.launchModulePolling("Memory", memIntervalFlow, scope) { collectMemoryBlock() }
        batteryJob = PollingFlow.launchModulePolling("Battery", batIntervalFlow, scope) { collectBatteryBlock() }

        // 辅助模块 + 历史数据推送 — 共享全局间隔
        auxJob = PollingFlow.launchModulePolling("Aux", cpuIntervalFlow, scope, immediate = false) {
            val historyMap = auxCollector.collectAndPushHistory(healthTracker, historyCache)
            historyData.postValue(historyMap)
        }

        // 观察刷新策略 (省电模式降频，前后台不降频 — 仅动画暂停)
        policyObserverJob = scope.launch {
            combine(
                RefreshPolicy.state,
                RefreshPolicy.powerSaveModeFlow
            ) { state, powerSave ->
                Pair(state, powerSave)
            }.collect { (state, powerSave) ->
                Log.d(TAG, "RefreshPolicy state=${state.name}, powerSave=$powerSave, pushing per-module intervals")
                pushPolicyAdjustedIntervals()
            }
        }
    }

    fun stopMonitoring() {
        monitoring = false
        cpuJob?.cancel(); gpuJob?.cancel(); memoryJob?.cancel(); batteryJob?.cancel(); auxJob?.cancel()
        policyObserverJob?.cancel()
        auxCollector.disableGps()
        disableSensor()
    }

    fun shutdown() {
        stopMonitoring()
        historyCache.shutdown()
    }

    fun loadStaticData() {
        scope.launch(Dispatchers.Default) {
            runCatching { systemLiveData.postValue(systemDataSource.getSystemInfo()); healthTracker.mark(HealthTracker.SourceHealth.Health.OK, "system") }
                .onFailure { e -> Log.w(TAG, "系统信息采集失败", e); healthTracker.mark(HealthTracker.SourceHealth.Health.ERROR, "system") }
            runCatching { auxCollector.storageLiveData.postValue(StorageDataSource().getStorageInfo()) }
                .onFailure { e -> Log.w(TAG, "存储信息采集失败", e) }
            runCatching { sensorsLiveData.postValue(sensorDataSource.getAllSensors()); healthTracker.mark(HealthTracker.SourceHealth.Health.OK, "sensors") }
                .onFailure { e -> Log.w(TAG, "传感器列表采集失败", e); healthTracker.mark(HealthTracker.SourceHealth.Health.ERROR, "sensors") }
            runCatching { deviceDetailLiveData.postValue(deviceDetailDataSource.collect()); healthTracker.mark(HealthTracker.SourceHealth.Health.OK, "device") }
                .onFailure { e -> Log.w(TAG, "设备详情采集失败", e); healthTracker.mark(HealthTracker.SourceHealth.Health.ERROR, "device") }
            runCatching { oemLiveData.postValue(oemDataSource.collect()); healthTracker.mark(HealthTracker.SourceHealth.Health.OK, "oem") }
                .onFailure { e -> Log.w(TAG, "OEM信息采集失败", e); healthTracker.mark(HealthTracker.SourceHealth.Health.ERROR, "oem") }
        }
    }

    // ═══════ 核心采集块 (写入 SharedFlow + LiveData + History) ═══════

    private suspend fun collectCpuBlock() {
        try {
            val cpu = cpuDataSource.getCpuInfo()
            val perCoreUsage = cpuDataSource.getPerCoreUsage()
            cpu.cores.forEach { core -> core.usagePercent = perCoreUsage[core.coreIndex] ?: Float.NaN }
            cachedChip?.let { CpuCache.injectCpuInfo(it, cpu) }
            _cpuFlow.emit(cpu); cpuLiveData.postValue(cpu)
            if (!cpu.temperatureCelsius.isNaN()) historyCache.addPoint("cpu_temp", cpu.temperatureCelsius)
            val maxFreq = cpu.cores.maxOfOrNull { it.currentFreqKHz } ?: 0L
            if (maxFreq > 0) historyCache.addPoint("cpu_freq", maxFreq.toFloat())
            if (!cpu.cpuUsagePercent.isNaN()) historyCache.addPoint("cpu_usage", cpu.cpuUsagePercent)
            if (!cpu.deepSleepPercent.isNaN()) historyCache.addPoint("cpu_deep_sleep", cpu.deepSleepPercent)
            healthTracker.mark(HealthTracker.SourceHealth.Health.OK, "cpu")
        } catch (e: Throwable) {
            Log.w(TAG, "CPU采集失败", e)
            healthTracker.mark(HealthTracker.SourceHealth.Health.ERROR, "cpu")
        }
    }

    private suspend fun collectGpuBlock() {
        try {
            val gpu = gpuDataSource.getGpuInfo()
            cachedChip?.let { CpuCache.injectGpuInfo(it, gpu) }
            gpu.isThrottled = gpu.maxFreqKHz > 0 && gpu.frequencyKHz > 0 && gpu.frequencyKHz < gpu.maxFreqKHz * 0.7f
            _gpuFlow.emit(gpu); gpuLiveData.postValue(gpu)
            if (!gpu.loadPercentage.isNaN()) historyCache.addPoint("gpu_load", gpu.loadPercentage)
            if (!gpu.temperatureCelsius.isNaN()) historyCache.addPoint("gpu_temp", gpu.temperatureCelsius)
            if (gpu.frequencyKHz > 0 && gpu.maxFreqKHz > 0)
                historyCache.addPoint("gpu_freq", gpu.frequencyKHz.toFloat() / gpu.maxFreqKHz * 100f)
            healthTracker.mark(HealthTracker.SourceHealth.Health.OK, "gpu")
        } catch (e: Throwable) {
            Log.w(TAG, "GPU采集失败", e)
            healthTracker.mark(HealthTracker.SourceHealth.Health.ERROR, "gpu")
        }
    }

    private suspend fun collectMemoryBlock() {
        try {
            val mem = memoryDataSource.getMemoryInfo()
            _memoryFlow.emit(mem); memoryLiveData.postValue(mem)
            if (mem.totalKB > 0) historyCache.addPoint("ram_usage", mem.usedKB.toFloat() / mem.totalKB * 100f)
            healthTracker.mark(HealthTracker.SourceHealth.Health.OK, "memory")
        } catch (e: Throwable) {
            Log.w(TAG, "内存采集失败", e)
            healthTracker.mark(HealthTracker.SourceHealth.Health.ERROR, "memory")
        }
    }

    private suspend fun collectBatteryBlock() {
        try {
            val bat = batteryDataSource.getBatteryInfo()
            _batteryFlow.emit(bat); batteryLiveData.postValue(bat)
            RefreshPolicy.isPowerSaveMode = bat.isPowerSaveMode
            if (!bat.temperatureCelsius.isNaN()) historyCache.addPoint("battery_temp", bat.temperatureCelsius)
            val batPowerMw = if (bat.isCharging) bat.chargingPowerMw else bat.dischargingPowerMw
            if (batPowerMw >= 0) historyCache.addPoint("battery_power", batPowerMw.toFloat())
            if (bat.levelPercent >= 0) historyCache.addPoint("battery_level", bat.levelPercent.toFloat())
            if (!bat.wattageNow.isNaN() && bat.wattageNow > 0) historyCache.addPoint("battery_wattage", bat.wattageNow.toFloat())
            if (bat.chargeFullMAh > 0 && bat.chargeFullDesignMAh > 0) {
                historyCache.addPoint("battery_soh", bat.chargeFullMAh.toFloat() / bat.chargeFullDesignMAh.toFloat() * 100f)
                historyCache.addPoint("battery_charge_full", bat.chargeFullMAh.toFloat())
            }
            if (!bat.internalResistanceMOhm.isNaN() && bat.internalResistanceMOhm > 0)
                historyCache.addPoint("battery_resistance", bat.internalResistanceMOhm)
            healthTracker.mark(HealthTracker.SourceHealth.Health.OK, "battery")
        } catch (e: Throwable) {
            Log.w(TAG, "电池采集失败", e)
            healthTracker.mark(HealthTracker.SourceHealth.Health.ERROR, "battery")
        }
    }

    /** 强制刷新电池数据 — 供 batteryPulseFlow 插拔事件消费，不等轮询 tick */
    fun forceRefreshBattery() {
        scope.launch { collectBatteryBlock() }
    }

    // ═══════ GPS / 传感器 ═══════

    fun enableGps() = auxCollector.enableGps()
    fun disableGps() = auxCollector.disableGps()

    @Volatile private var sensorListening = false

    fun enableSensor(sensorType: Int) {
        if (sensorListening) disableSensor()
        sensorListening = true
        try {
            sensorDataSource.startListening(sensorType) { liveData ->
                sensorLiveData.postValue(liveData)
                val meta = SensorTypeMeta.fromTypeId(sensorType)
                val seriesPrefix = "sensor_${sensorType}"
                when (liveData.valueCount) {
                    in 1..3 -> {
                        val labels = meta?.axisLabelResIds?.map { appContext.getString(it) }
                            ?: listOf("X", "Y", "Z")
                        for (i in 0 until liveData.valueCount) {
                            if (!liveData.values[i].isNaN())
                                historyCache.addPoint("${seriesPrefix}_${labels.getOrElse(i) { "$i" }}", liveData.values[i])
                        }
                    }
                }
                pushSensorHistory(sensorType)
            }
        } catch (e: Throwable) { Log.w(TAG, "传感器实时监听启动失败", e) }
    }

    fun disableSensor() {
        if (!sensorListening) return
        sensorListening = false
        try {
            sensorDataSource.stopListening()
            historyCache.clearSensorSeries()
            sensorHistoryData.postValue(emptyMap())
        } catch (e: Throwable) { Log.w(TAG, "传感器监听停止失败", e) }
    }

    fun pushSensorHistory(sensorType: Int) {
        val meta = SensorTypeMeta.fromTypeId(sensorType) ?: return
        val prefix = "sensor_${sensorType}"
        val labels = meta.axisLabelResIds.map { appContext.getString(it) }
        val map = mutableMapOf<String, List<HistoryDataPoint>>()
        for (label in labels) map["${prefix}_${label}"] = historyCache.getSeries("${prefix}_${label}")
        sensorHistoryData.postValue(map)
    }

    // ═══════ 分模块间隔控制 ═══════

    private fun pushAllIntervalFlows(baseMs: Long) {
        val settings = AppSettings.getInstance(appContext)
        cpuIntervalFlow.value = settings.effectiveRefreshMs(settings.cpuRefreshMs).toLong()
        gpuIntervalFlow.value = settings.effectiveRefreshMs(settings.gpuRefreshMs).toLong()
        memIntervalFlow.value = settings.effectiveRefreshMs(settings.memoryRefreshMs).toLong()
        batIntervalFlow.value = settings.effectiveRefreshMs(settings.batteryRefreshMs).toLong()
    }

    // Applies RefreshPolicy (省电模式封装，前后台不降频)
    private fun pushPolicyAdjustedIntervals() {
        val settings = AppSettings.getInstance(appContext)
        val tier = RefreshPolicy.Tier.NORMAL
        cpuIntervalFlow.value = RefreshPolicy.effectiveMs(
            settings.effectiveRefreshMs(settings.cpuRefreshMs).toLong(), tier)
        gpuIntervalFlow.value = RefreshPolicy.effectiveMs(
            settings.effectiveRefreshMs(settings.gpuRefreshMs).toLong(), tier)
        memIntervalFlow.value = RefreshPolicy.effectiveMs(
            settings.effectiveRefreshMs(settings.memoryRefreshMs).toLong(), tier)
        batIntervalFlow.value = RefreshPolicy.effectiveMs(
            settings.effectiveRefreshMs(settings.batteryRefreshMs).toLong(), tier)
    }

    fun setIntervalMs(ms: Long) {
        if (ms == this.intervalMs) return
        this.intervalMs = ms
        cpuIntervalFlow.value = ms
        gpuIntervalFlow.value = ms
        memIntervalFlow.value = ms
        batIntervalFlow.value = ms
    }

    fun getIntervalMs(): Long = intervalMs

    // ═══════ 电池脉冲事件 ═══════
    fun batteryPulseFlow(): Flow<BatteryDataSource.BatteryPulseEvent> =
        batteryDataSource.monitorBatteryPulses()

    // ═══════ 分模块刷新间隔 getter/setter ═══════
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
