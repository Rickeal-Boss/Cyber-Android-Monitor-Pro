package com.example.deviceinfoviewer.data.source

import com.example.deviceinfoviewer.data.model.MemoryInfo

/**
 * 内存数据源，解析 /proc/meminfo 和 ZRAM 统计
 */
class MemoryDataSource {

    fun getMemoryInfo(): MemoryInfo {
        val info = MemoryInfo()
        info.timestamp = System.currentTimeMillis()

        val meminfo = SysFsReader.readAll("/proc/meminfo")
        if (meminfo.isEmpty()) {
            return info
        }

        for (line in meminfo.split("\n")) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("MemTotal:") -> info.totalKB = parseKB(trimmed)
                trimmed.startsWith("MemAvailable:") -> info.availableKB = parseKB(trimmed)
                trimmed.startsWith("SwapTotal:") -> info.swapTotalKB = parseKB(trimmed)
                trimmed.startsWith("SwapFree:") -> {
                    val swapFree = parseKB(trimmed)
                    if (info.swapTotalKB > 0) {
                        info.swapUsedKB = info.swapTotalKB - swapFree
                    }
                }
            }
        }

        // 计算已用内存
        if (info.totalKB > 0 && info.availableKB > 0) {
            info.usedKB = info.totalKB - info.availableKB
        }

        // 获取 ZRAM 统计
        getZramStats(info)

        return info
    }

    private fun getZramStats(info: MemoryInfo) {
        val blocks = SysFsReader.listDir("/sys/block/")
        for (block in blocks) {
            if (block.startsWith("zram")) {
                val base = "/sys/block/$block/"
                // orig_data_size / compr_data_size (单位: bytes)
                val origSize = SysFsReader.readLong(base + "orig_data_size")
                val comprSize = SysFsReader.readLong(base + "compr_data_size")
                if (origSize > 0) {
                    info.zramOriginalKB = if (info.zramOriginalKB > 0)
                        info.zramOriginalKB + origSize / 1024
                    else
                        origSize / 1024
                }
                if (comprSize > 0) {
                    info.zramCompressedKB = if (info.zramCompressedKB > 0)
                        info.zramCompressedKB + comprSize / 1024
                    else
                        comprSize / 1024
                }
                // mm_stat: 更准确的 ZRAM 统计
                val mmStat = SysFsReader.readAll(base + "mm_stat")
                if (mmStat.isNotEmpty()) {
                    val parts = mmStat.trim().split("\\s+".toRegex())
                    if (parts.size >= 3) {
                        parts[2].toLongOrNull()?.let { memUsedTotal ->
                            info.zramMemUsedTotalKB = if (info.zramMemUsedTotalKB > 0)
                                info.zramMemUsedTotalKB + memUsedTotal / 1024
                            else
                                memUsedTotal / 1024
                        }
                    }
                }
            }
        }
        // 压缩比 (原始数据 / 压缩后 = X:1，值越大压缩效果越好)
        if (info.zramOriginalKB > 0 && info.zramCompressedKB > 0) {
            info.compressionRatio = info.zramOriginalKB.toFloat() / info.zramCompressedKB.toFloat()
        }
    }

    private fun parseKB(line: String): Long {
        val parts = line.split("\\s+".toRegex())
        if (parts.size >= 2) {
            parts[1].toLongOrNull()?.let { return it }
        }
        return -1L
    }
}
