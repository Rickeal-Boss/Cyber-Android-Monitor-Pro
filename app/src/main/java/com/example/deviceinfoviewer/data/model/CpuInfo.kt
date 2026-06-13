package com.example.deviceinfoviewer.data.model

/**
 * CPU 整体信息
 */
data class CpuInfo(
    var architecture: String = "",
    var coreCount: Int = 0,
    var cores: MutableList<CpuCoreInfo> = mutableListOf(),
    var temperatureCelsius: Float = Float.NaN,
    var temperatureSource: String = "",
    var timestamp: Long = 0L,
    var cacheL1: String = "",
    var cacheL2: String = "",
    var cacheL3: String = "",
    var cpuUsagePercent: Float = Float.NaN   // /proc/stat 计算的实际 CPU 使用率
)
