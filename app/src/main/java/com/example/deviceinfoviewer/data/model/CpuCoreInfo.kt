package com.example.deviceinfoviewer.data.model

/**
 * CPU 核心信息
 */
data class CpuCoreInfo(
    var coreIndex: Int = 0,
    var currentFreqKHz: Long = 0L,
    var maxFreqKHz: Long = 0L,
    var minFreqKHz: Long = 0L,
    var governor: String? = null
)
