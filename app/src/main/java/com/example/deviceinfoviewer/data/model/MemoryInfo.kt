package com.example.deviceinfoviewer.data.model

/**
 * 内存信息
 */
data class MemoryInfo(
    var totalKB: Long = -1L,
    var availableKB: Long = -1L,
    var usedKB: Long = -1L,
    var swapTotalKB: Long = -1L,
    var swapUsedKB: Long = -1L,
    var zramOriginalKB: Long = -1L,
    var zramCompressedKB: Long = -1L,
    var zramMemUsedTotalKB: Long = -1L,     // mm_stat: mem_used_total (实际占用)
    var compressionRatio: Float = -1f,
    var timestamp: Long = 0L
)
