package com.example.deviceinfoviewer.data.repository

import android.content.Context
import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.example.deviceinfoviewer.data.model.*
import com.example.deviceinfoviewer.data.source.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Collects WiFi/Mobile/Storage/NetIf/GPS auxiliary data */
class AuxiliaryCollector(context: Context) {
    private val appContext = context.applicationContext

    // DataSources
    private val storageDataSource = StorageDataSource()
    private val wifiDataSource = WifiDataSource(appContext)
    private val mobileNetworkDataSource = MobileNetworkDataSource(appContext)
    private val networkInterfaceDataSource = NetworkInterfaceDataSource()
    private val gpsDataSource = GpsDataSource(appContext)

    // LiveData
    val storageLiveData = MutableLiveData<StorageInfo>()
    val wifiLiveData = MutableLiveData<WifiDetailInfo>()
    val mobileNetworkLiveData = MutableLiveData<MobileNetworkInfo>()
    val networkInterfacesLiveData = MutableLiveData<List<NetworkInterfaceInfo>>()
    val gpsLiveData = MutableLiveData<GpsStatusInfo>()

    // GPS lifecycle
    @Volatile
    private var gpsEnabled = false

    fun enableGps() {
        if (gpsEnabled) return
        gpsEnabled = true
        try {
            gpsDataSource.startListening { gpsLiveData.postValue(it) }
        } catch (e: Throwable) { Log.w("AuxCollector", "GPS监听启动失败", e) }
    }

    fun disableGps() {
        if (!gpsEnabled) return
        gpsEnabled = false
        try {
            gpsDataSource.stopListening()
        } catch (e: Throwable) { Log.w("AuxCollector", "GPS监听停止失败", e) }
    }

    /**
     * 一次采集所有辅助模块 (并行 async/await)
     * @param healthTracker 健康状态追踪器，用于上报各模块健康度
     * @param historyCache 历史缓存，用于写入图表数据
     */
    suspend fun collectAndPushHistory(
        healthTracker: HealthTracker,
        historyCache: HistoryCache,
    ): Map<String, List<HistoryDataPoint>> = coroutineScope {
        val asyncResults = listOf(
            async {
                try {
                    storageLiveData.postValue(storageDataSource.getStorageInfo())
                    healthTracker.mark(HealthTracker.SourceHealth.Health.OK, "storage")
                } catch (e: Throwable) {
                    Log.w("AuxCollector", "存储采集失败", e)
                    healthTracker.mark(HealthTracker.SourceHealth.Health.ERROR, "storage")
                }
            },
            async {
                try {
                    val wifi = wifiDataSource.getWifiDetail()
                    wifiLiveData.postValue(wifi)
                    if (wifi.linkSpeedMbps > 0)
                        historyCache.addPoint("wifi_speed", wifi.linkSpeedMbps.toFloat())
                    healthTracker.mark(HealthTracker.SourceHealth.Health.OK, "wifi")
                } catch (e: Throwable) {
                    Log.w("AuxCollector", "WiFi采集失败", e)
                    healthTracker.mark(HealthTracker.SourceHealth.Health.ERROR, "wifi")
                }
            },
            async {
                try {
                    val mobile = mobileNetworkDataSource.getMobileNetworkInfo()
                    mobileNetworkLiveData.postValue(mobile)
                    val signalDbm = mobile.signalStrengthDbm
                    if (signalDbm > Int.MIN_VALUE && signalDbm < 0)
                        historyCache.addPoint("signal_strength", signalDbm.toFloat())
                    healthTracker.mark(HealthTracker.SourceHealth.Health.OK, "mobile")
                } catch (e: Throwable) {
                    Log.w("AuxCollector", "移动网络采集失败", e)
                    healthTracker.mark(HealthTracker.SourceHealth.Health.ERROR, "mobile")
                }
            },
            async {
                try {
                    networkInterfacesLiveData.postValue(networkInterfaceDataSource.getNetworkInterfaces())
                    healthTracker.mark(HealthTracker.SourceHealth.Health.OK, "netif")
                } catch (e: Throwable) {
                    Log.w("AuxCollector", "网卡信息采集失败", e)
                    healthTracker.mark(HealthTracker.SourceHealth.Health.ERROR, "netif")
                }
            },
            async {
                try {
                    gpsDataSource.checkGpsStatus()?.let { gpsLiveData.postValue(it) }
                } catch (e: Throwable) { Log.w("AuxCollector", "GPS状态检查失败", e) }
            }
        )
        asyncResults.awaitAll()

        HashMap<String, List<HistoryDataPoint>>(15).apply {
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
        }
    }

    fun shutdown() {
        disableGps()
    }
}
