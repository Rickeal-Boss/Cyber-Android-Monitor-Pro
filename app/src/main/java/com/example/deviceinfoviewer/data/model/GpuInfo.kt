package com.example.deviceinfoviewer.data.model

/**
 * GPU 信息 — 增强版
 * 频率 / 温度 / 负载 / 调速器 / 频率范围 / Vulkan 驱动
 */
data class GpuInfo(
    var model: String = "",
    var vendor: String = "",
    var renderer: String = "",              // OpenGL ES Renderer
    var frequencyKHz: Long = -1L,
    var minFreqKHz: Long = -1L,
    var maxFreqKHz: Long = -1L,
    var governor: String = "",
    var availableGovernors: String = "",
    var loadPercentage: Float = Float.NaN,  // GPU 使用率 (%)
    var loadSource: String = "",             // 负载数据来源
    var temperatureCelsius: Float = Float.NaN,
    var timestamp: Long = 0L,
    var isThrottled: Boolean = false,        // DVFS 节流状态 (当前频率 < 最大频率 的 70%)
    // Vulkan 信息
    var vulkanApiVersion: String = "",       // Vulkan API 版本, e.g. "1.3.0"
    var vulkanDriverVersion: String = "",    // GPU 驱动版本, e.g. "2.0.1.0"
    var vulkanDriverInfo: String = "",       // 驱动完整描述 (VkPhysicalDeviceDriverProperties.driverInfo)
    var vulkanDeviceType: String = "",       // 设备类型: Integrated/Discrete/Virtual/CPU
    var vulkanDeviceName: String = "",       // Vulkan 设备名
    var vulkanSource: String = "",           // 数据来源
) {
    /** 实际性能利用率 = 负载% × (当前频率/最大频率)，反映 DVFS 影响 */
    val effectiveUtilization: Float
        get() {
            if (loadPercentage.isNaN()) return Float.NaN
            if (maxFreqKHz <= 0 || frequencyKHz <= 0) return loadPercentage
            return loadPercentage * (frequencyKHz.toFloat() / maxFreqKHz.toFloat())
        }
}
