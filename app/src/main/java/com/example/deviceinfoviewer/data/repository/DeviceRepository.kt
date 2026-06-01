package com.example.deviceinfoviewer.data.repository

import android.content.Context
import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.example.deviceinfoviewer.data.model.*
import com.example.deviceinfoviewer.data.source.*
import kotlinx.coroutines.*

/**
 * 核心数据仓库 — Kotlin 协程驱动
 */
class DeviceRepository(context: Context) {

    companion object {
        const val TAG = "DeviceRepo"
        const val DEFAULT_INTERVAL_MS = 2000L
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

    private fun markHealthy(vararg names: String) {
        val current = sourceHealth.value ?: return
        var h = current
        names.forEach { n ->
            h = when (n) {
                "cpu" -> h.copy(cpu = SourceHealth.Health.OK)
                "gpu" -> h.copy(gpu = SourceHealth.Health.OK)
                "battery" -> h.copy(battery = SourceHealth.Health.OK)
                "memory" -> h.copy(memory = SourceHealth.Health.OK)
                "storage" -> h.copy(storage = SourceHealth.Health.OK)
                "wifi" -> h.copy(wifi = SourceHealth.Health.OK)
                "mobile" -> h.copy(mobileNetwork = SourceHealth.Health.OK)
                "netif" -> h.copy(networkInterface = SourceHealth.Health.OK)
                "gps" -> h.copy(gps = SourceHealth.Health.OK)
                "sensors" -> h.copy(sensors = SourceHealth.Health.OK)
                "system" -> h.copy(system = SourceHealth.Health.OK)
                "device" -> h.copy(deviceDetail = SourceHealth.Health.OK)
                "oem" -> h.copy(oem = SourceHealth.Health.OK)
                else -> h
            }
        }
        sourceHealth.postValue(h)
    }

    private fun markError(vararg names: String) {
        val current = sourceHealth.value ?: return
        var h = current
        names.forEach { n ->
            h = when (n) {
                "cpu" -> h.copy(cpu = SourceHealth.Health.ERROR)
                "gpu" -> h.copy(gpu = SourceHealth.Health.ERROR)
                "battery" -> h.copy(battery = SourceHealth.Health.ERROR)
                "memory" -> h.copy(memory = SourceHealth.Health.ERROR)
                "storage" -> h.copy(storage = SourceHealth.Health.ERROR)
                "wifi" -> h.copy(wifi = SourceHealth.Health.ERROR)
                "mobile" -> h.copy(mobileNetwork = SourceHealth.Health.ERROR)
                "netif" -> h.copy(networkInterface = SourceHealth.Health.ERROR)
                "gps" -> h.copy(gps = SourceHealth.Health.ERROR)
                "sensors" -> h.copy(sensors = SourceHealth.Health.ERROR)
                "system" -> h.copy(system = SourceHealth.Health.ERROR)
                "device" -> h.copy(deviceDetail = SourceHealth.Health.ERROR)
                "oem" -> h.copy(oem = SourceHealth.Health.ERROR)
                else -> h
            }
        }
        sourceHealth.postValue(h)
    }

    // DataSources
    private val cpuDataSource = CpuDataSource(context.applicationContext)
    private val gpuDataSource = GpuDataSource()
    private val batteryDataSource = BatteryDataSource(context.applicationContext)
    private val memoryDataSource = MemoryDataSource()
    private val storageDataSource = StorageDataSource()
    private val wifiDataSource = WifiDataSource(context.applicationContext)
    private val mobileNetworkDataSource = MobileNetworkDataSource(context.applicationContext)
    private val networkInterfaceDataSource = NetworkInterfaceDataSource()
    private val gpsDataSource = GpsDataSource(context.applicationContext)
    private val sensorDataSource = SensorDataSource(context.applicationContext)
    private val systemDataSource = SystemDataSource()
    private val deviceDetailDataSource = DeviceDetailDataSource(context.applicationContext)
    private val oemDataSource = OemDataSource(context.applicationContext)

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

    // 历史图表数据 — Compose 可观察
    val historyData = MutableLiveData<Map<String, List<HistoryDataPoint>>>(emptyMap())

    // Coroutine
    private var intervalMs = DEFAULT_INTERVAL_MS
    private var monitoringJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Volatile
    private var monitoring = false

    /**
     * 启动后台数据采集（幂等）
     */
    fun startMonitoring(intervalMs: Long) {
        if (monitoring) return
        monitoring = true
        this.intervalMs = intervalMs

        monitoringJob = scope.launch {
            while (isActive && monitoring) {
                collectData()
                delay(intervalMs)
            }
        }

        try {
            gpsDataSource.startListening { gpsLiveData.postValue(it) }
        } catch (e: Throwable) { Log.w(TAG, "GPS监听启动失败", e) }
    }

    fun stopMonitoring() {
        monitoring = false
        monitoringJob?.cancel()
        gpsDataSource.stopListening()
    }

    /**
     * 释放所有资源，仅供 Application.onTerminate() 调用
     */
    fun shutdown() {
        stopMonitoring()
        historyCache.shutdown()
    }

    private suspend fun collectData() = withContext(Dispatchers.Default) {
        runCatching {
            val cpu = cpuDataSource.getCpuInfo()
            cachedChip?.let { CpuCache.injectCpuInfo(it, cpu) }
            cpuLiveData.postValue(cpu)
            if (!cpu.temperatureCelsius.isNaN())
                historyCache.addPoint("cpu_temp", cpu.temperatureCelsius)
            val maxFreq = cpu.cores.maxOfOrNull { it.currentFreqKHz } ?: 0L
            if (maxFreq > 0) historyCache.addPoint("cpu_freq", maxFreq.toFloat())
            if (!cpu.cpuUsagePercent.isNaN())
                historyCache.addPoint("cpu_usage", cpu.cpuUsagePercent)
            markHealthy("cpu")
        }.onFailure { e ->
            Log.w(TAG, "CPU采集失败", e)
            markError("cpu")
        }

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
            markHealthy("gpu")
        }.onFailure { e ->
            Log.w(TAG, "GPU采集失败", e)
            markError("gpu")
        }

        runCatching {
            val bat = batteryDataSource.getBatteryInfo()
            batteryLiveData.postValue(bat)
            if (!bat.temperatureCelsius.isNaN())
                historyCache.addPoint("battery_temp", bat.temperatureCelsius)
            if (bat.powerMilliwatts >= 0)
                historyCache.addPoint("battery_power", bat.powerMilliwatts.toFloat())
            if (bat.levelPercent >= 0)
                historyCache.addPoint("battery_level", bat.levelPercent.toFloat())
            markHealthy("battery")
        }.onFailure { e ->
            Log.w(TAG, "电池采集失败", e)
            markError("battery")
        }

        runCatching {
            val mem = memoryDataSource.getMemoryInfo()
            memoryLiveData.postValue(mem)
            if (mem.totalKB > 0) {
                val pct = mem.usedKB.toFloat() / mem.totalKB * 100f
                historyCache.addPoint("ram_usage", pct)
            }
            markHealthy("memory")
        }.onFailure { e ->
            Log.w(TAG, "内存采集失败", e)
            markError("memory")
        }

        runCatching { storageLiveData.postValue(storageDataSource.getStorageInfo()); markHealthy("storage") }
            .onFailure { e -> Log.w(TAG, "存储采集失败", e); markError("storage") }

        runCatching {
            val wifi = wifiDataSource.getWifiDetail()
            wifiLiveData.postValue(wifi)
            if (wifi.linkSpeedMbps > 0)
                historyCache.addPoint("wifi_speed", wifi.linkSpeedMbps.toFloat())
            markHealthy("wifi")
        }.onFailure { e ->
            Log.w(TAG, "WiFi采集失败", e)
            markError("wifi")
        }

        runCatching {
            val mobile = mobileNetworkDataSource.getMobileNetworkInfo()
            mobileNetworkLiveData.postValue(mobile)
            val signalDbm = mobile.signalStrengthDbm
            if (signalDbm > Int.MIN_VALUE && signalDbm < 0)
                historyCache.addPoint("signal_strength", signalDbm.toFloat())
            markHealthy("mobile")
        }.onFailure { e ->
            Log.w(TAG, "移动网络采集失败", e)
            markError("mobile")
        }

        runCatching {
            networkInterfacesLiveData.postValue(networkInterfaceDataSource.getNetworkInterfaces())
            markHealthy("netif")
        }.onFailure { e ->
            Log.w(TAG, "网卡信息采集失败", e)
            markError("netif")
        }

        runCatching {
            gpsDataSource.checkGpsStatus()?.let { status -> gpsLiveData.postValue(status) }
        }.onFailure { e ->
            Log.w(TAG, "GPS状态检查失败", e)
        }

        // 推送历史数据给 Compose 图表
        historyData.postValue(
            mapOf(
                "cpu_temp" to historyCache.getSeries("cpu_temp"),
                "cpu_freq" to historyCache.getSeries("cpu_freq"),
                "cpu_usage" to historyCache.getSeries("cpu_usage"),
                "gpu_load" to historyCache.getSeries("gpu_load"),
                "gpu_temp" to historyCache.getSeries("gpu_temp"),
                "battery_temp" to historyCache.getSeries("battery_temp"),
                "battery_power" to historyCache.getSeries("battery_power"),
                "battery_level" to historyCache.getSeries("battery_level"),
                "ram_usage" to historyCache.getSeries("ram_usage"),
                "wifi_speed" to historyCache.getSeries("wifi_speed"),
                "signal_strength" to historyCache.getSeries("signal_strength")
            )
        )
    }

    fun loadStaticData() {
        scope.launch(Dispatchers.Default) {
            runCatching { systemLiveData.postValue(systemDataSource.getSystemInfo()); markHealthy("system") }
                .onFailure { e -> Log.w(TAG, "系统信息采集失败", e); markError("system") }
            runCatching { storageLiveData.postValue(storageDataSource.getStorageInfo()) }
                .onFailure { e -> Log.w(TAG, "存储信息采集失败", e) }
            runCatching { sensorsLiveData.postValue(sensorDataSource.getAllSensors()); markHealthy("sensors") }
                .onFailure { e -> Log.w(TAG, "传感器列表采集失败", e); markError("sensors") }
            runCatching { deviceDetailLiveData.postValue(deviceDetailDataSource.collect()); markHealthy("device") }
                .onFailure { e -> Log.w(TAG, "设备详情采集失败", e); markError("device") }
            runCatching { oemLiveData.postValue(oemDataSource.collect()); markHealthy("oem") }
                .onFailure { e -> Log.w(TAG, "OEM信息采集失败", e); markError("oem") }
        }
    }

    fun setIntervalMs(ms: Long) {
        if (ms != intervalMs) {
            stopMonitoring()
            startMonitoring(ms)
        }
    }

    fun getIntervalMs(): Long = intervalMs
}
