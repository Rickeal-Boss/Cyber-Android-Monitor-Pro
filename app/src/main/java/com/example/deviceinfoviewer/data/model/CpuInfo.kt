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
    var cpuUsagePercent: Float = Float.NaN,   // /proc/stat 计算的实际 CPU 使用率
    // C-States 睡眠统计
    var deepSleepPercent: Float = Float.NaN,   // 深度睡眠百分比 (C2+C3+...)
    var cStates: List<CpuCState> = emptyList(), // 各 C-State 详情
    var totalIdlePercent: Float = Float.NaN,   // 总空闲百分比 (含 C0 idle)
    var cpuidleSource: String = "",            // 数据来源描述
    var supportedAbis: List<String> = emptyList()  // CPU 支持的 ABI (arm64-v8a/armeabi-v7a/...)
)

/** CPU C-State (cpu idle state) 信息 */
data class CpuCState(
    val name: String,        // e.g., "C0", "C1", "C2", "C3"
    val timeUs: Long,        // 累计时间 (微秒)
    val usage: Int,          // 进入次数
    val latencyUs: Int,      // 退出延迟 (微秒)
    val level: Int           // C-State 层级 (0,1,2,3,...)
)
