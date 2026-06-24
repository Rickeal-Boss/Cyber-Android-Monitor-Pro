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
    var timestamp: Long = 0L,
    var topProcesses: List<String> = emptyList(),  // 进程级统计: "包名: PSS_MB (CPU_s)"

    // === 内存分布分类 (Memory Distribution) ===
    var appMemoryKB: Long = -1L,       // 应用内存 (匿名页等进程占用)
    var cachedMemoryKB: Long = -1L,    // 缓存内存 (Buffers + Cached + SReclaimable)
    var systemMemoryKB: Long = -1L,    // 系统内存 (SUnreclaim + PageTables + KernelStack + VmallocUsed + Shmem)
    var freeMemoryKB: Long = -1L,      // 空闲内存 (MemFree)
    var otherMemoryKB: Long = -1L,     // 其他/未统计 (Total - 以上四项)

    // === /proc/meminfo 细分字段 (供调试和详细展示) ===
    var buffersKB: Long = -1L,
    var cachedKB: Long = -1L,
    var slabReclaimableKB: Long = -1L,
    var slabUnreclaimKB: Long = -1L,
    var shmemKB: Long = -1L,
    var pageTablesKB: Long = -1L,
    var kernelStackKB: Long = -1L,
    var vmallocUsedKB: Long = -1L,
    var mappedKB: Long = -1L,

    // === dumpsys meminfo OOM 分类 (每 30 秒刷新，用于精确 app/system 分离) ===
    var systemProcessPssKB: Long = -1L,    // 系统进程 PSS: Native + System + Persistent + Persistent Service
    var appProcessPssKB: Long = -1L,       // 应用进程 PSS: Foreground + Visible + Perceptible + Services + Cached
    var cachedProcessPssKB: Long = -1L,    // 缓存进程 PSS: Cached (可回收的应用进程)
    var dumpsysAvailable: Boolean = false  // dumpsys 数据是否可用 (用于降级判断)
)
