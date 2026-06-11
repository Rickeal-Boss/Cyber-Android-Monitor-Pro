package com.example.deviceinfoviewer.data.model

/**
 * CPU 核心完整信息
 */
data class CpuCoreInfo(
    var coreIndex: Int = 0,
    /** 当前频率 (KHz) */
    var currentFreqKHz: Long = 0L,
    /** 最大频率 (KHz) */
    var maxFreqKHz: Long = 0L,
    /** 最小频率 (KHz) */
    var minFreqKHz: Long = 0L,
    /** 调度器 */
    var governor: String? = null,
    /** 核心在线状态 (热插拔) */
    var online: Boolean = true,
    /** 核心类型推断 (Cortex-X3 / A715 / A510 等) */
    var coreType: String = "",
    /** 核心集群 (prime / performance / efficiency) */
    var coreCluster: String = "",
    /** 当前使用率 (%) */
    var usagePercent: Float = Float.NaN,
)
