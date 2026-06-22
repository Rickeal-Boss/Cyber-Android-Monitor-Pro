package com.example.deviceinfoviewer.data.model

/**
 * 存储信息
 */
data class StorageInfo(
    var internalTotalBytes: Long = -1L,
    var internalUsedBytes: Long = -1L,
    var internalAvailableBytes: Long = -1L,
    var partitions: MutableList<PartitionInfo> = mutableListOf()
) {
    /**
     * 分区信息内部类
     */
    data class PartitionInfo(
        var mountPoint: String = "",
        var totalBytes: Long = -1L,
        var usedBytes: Long = -1L,
        var availableBytes: Long = -1L
    )
}
