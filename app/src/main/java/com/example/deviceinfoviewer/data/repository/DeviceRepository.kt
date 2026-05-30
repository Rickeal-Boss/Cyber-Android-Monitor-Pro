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
        const val DEFAULT_INTERVAL_MS = 2000L
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
    private val oemDataSource = OemDataSource()

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
        } catch (_: Throwable) { Log.w("DeviceRepo", "GPS listen failed") }
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
            cpuLiveData.postValue(cpu)

            if (!cpu.temperatureCelsius.isNaN())
                historyCache.addPoint("cpu_temp", cpu.temperatureCelsius)

            val maxFreq = cpu.cores.maxOfOrNull { it.currentFreqKHz } ?: 0L
            if (maxFreq > 0) historyCache.addPoint("cpu_freq", maxFreq.toFloat())
        }

        runCatching {
            val gpu = gpuDataSource.getGpuInfo()
            gpu.isThrottled = gpu.maxFreqKHz > 0 && gpu.frequencyKHz > 0
                && gpu.frequencyKHz < gpu.maxFreqKHz * 0.7f
            gpuLiveData.postValue(gpu)
            if (!gpu.loadPercentage.isNaN())
                historyCache.addPoint("gpu_load", gpu.loadPercentage)
            if (!gpu.temperatureCelsius.isNaN())
                historyCache.addPoint("gpu_temp", gpu.temperatureCelsius)
            if (gpu.frequencyKHz > 0 && gpu.maxFreqKHz > 0)
                historyCache.addPoint("gpu_freq", gpu.frequencyKHz.toFloat() / gpu.maxFreqKHz * 100f)
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
        }

        runCatching {
            val mem = memoryDataSource.getMemoryInfo()
            memoryLiveData.postValue(mem)
            if (mem.totalKB > 0) {
                val pct = mem.usedKB.toFloat() / mem.totalKB * 100f
                historyCache.addPoint("ram_usage", pct)
            }
        }

        runCatching { storageLiveData.postValue(storageDataSource.getStorageInfo()) }
        runCatching {
            val wifi = wifiDataSource.getWifiDetail()
            wifiLiveData.postValue(wifi)
            if (wifi.linkSpeedMbps > 0)
                historyCache.addPoint("wifi_speed", wifi.linkSpeedMbps.toFloat())
        }
        runCatching {
            val mobile = mobileNetworkDataSource.getMobileNetworkInfo()
            mobileNetworkLiveData.postValue(mobile)
            val signalDbm = mobile.signalStrengthDbm
            if (signalDbm > Int.MIN_VALUE && signalDbm < 0)
                historyCache.addPoint("signal_strength", signalDbm.toFloat())
        }
        runCatching { networkInterfacesLiveData.postValue(networkInterfaceDataSource.getNetworkInterfaces()) }

        // 定期检查 GPS 启用状态（只在 GPS 被禁用时通知 UI）
        runCatching {
            gpsDataSource.checkGpsStatus()?.let { status ->
                gpsLiveData.postValue(status)
            }
        }

        // 推送历史数据给 Compose 图表
        historyData.postValue(
            mapOf(
                "cpu_temp" to historyCache.getSeries("cpu_temp"),
                "cpu_freq" to historyCache.getSeries("cpu_freq"),
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
            runCatching { systemLiveData.postValue(systemDataSource.getSystemInfo()) }
            runCatching { storageLiveData.postValue(storageDataSource.getStorageInfo()) }
            runCatching { sensorsLiveData.postValue(sensorDataSource.getAllSensors()) }
            runCatching { deviceDetailLiveData.postValue(deviceDetailDataSource.collect()) }
            runCatching { oemLiveData.postValue(oemDataSource.collect()) }
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
