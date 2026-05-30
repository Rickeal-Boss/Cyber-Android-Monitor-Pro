package com.example.deviceinfoviewer.data.model

/**
 * CPU 整体信息
 */
data class CpuInfo(
    var architecture: String = "",
    var coreCount: Int = 0,
    var cores: MutableList<CpuCoreInfo> = mutableListOf(),
    var temperatureCelsius: Float = Float.NaN,
    var temperatureSource: String = "",   // 温度数据来源标识
    var timestamp: Long = 0L,
    var cacheL1: String = "",             // L1 缓存
    var cacheL2: String = "",             // L2 缓存
    var cacheL3: String = ""              // L3 缓存
)
