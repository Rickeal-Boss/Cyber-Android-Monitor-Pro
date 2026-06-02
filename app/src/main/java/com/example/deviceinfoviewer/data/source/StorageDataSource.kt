package com.example.deviceinfoviewer.data.source

import android.os.StatFs

import com.example.deviceinfoviewer.data.model.StorageInfo

/**
 * 存储数据源，使用 StatFs 获取存储信息
 */
class StorageDataSource {

    fun getStorageInfo(): StorageInfo {
        val info = StorageInfo()

        // 内部存储
        val statFs = StatFs("/data")
        val totalBytes = statFs.blockCountLong * statFs.blockSizeLong
        val availableBytes = statFs.availableBlocksLong * statFs.blockSizeLong
        val usedBytes = totalBytes - availableBytes

        info.internalTotalBytes = totalBytes
        info.internalUsedBytes = usedBytes
        info.internalAvailableBytes = availableBytes

        info.partitions.clear()
        info.partitions.addAll(getPartitions())

        return info
    }

    fun getPartitions(): List<StorageInfo.PartitionInfo> {
        val partitions = mutableListOf<StorageInfo.PartitionInfo>()

        val paths = arrayOf("/data", "/system", "/cache", "/vendor")
        for (path in paths) {
            try {
                val sf = StatFs(path)
                val total = sf.blockCountLong * sf.blockSizeLong
                val avail = sf.availableBlocksLong * sf.blockSizeLong
                val used = total - avail
                partitions.add(StorageInfo.PartitionInfo(path, total, used, avail))
            } catch (_: Throwable) {}
        }
        return partitions
    }
}
