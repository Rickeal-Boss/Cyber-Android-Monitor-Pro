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
                trimmed.startsWith("MemFree:") -> info.freeMemoryKB = parseKB(trimmed)
                trimmed.startsWith("SwapTotal:") -> info.swapTotalKB = parseKB(trimmed)
                trimmed.startsWith("SwapFree:") -> {
                    val swapFree = parseKB(trimmed)
                    if (info.swapTotalKB > 0) {
                        info.swapUsedKB = info.swapTotalKB - swapFree
                    }
                }
                // === 内存分布分类所需字段 ===
                trimmed.startsWith("Buffers:") -> info.buffersKB = parseKB(trimmed)
                trimmed.startsWith("Cached:") -> info.cachedKB = parseKB(trimmed)
                trimmed.startsWith("SReclaimable:") -> info.slabReclaimableKB = parseKB(trimmed)
                trimmed.startsWith("SUnreclaim:") -> info.slabUnreclaimKB = parseKB(trimmed)
                trimmed.startsWith("Shmem:") -> info.shmemKB = parseKB(trimmed)
                trimmed.startsWith("PageTables:") -> info.pageTablesKB = parseKB(trimmed)
                trimmed.startsWith("KernelStack:") -> info.kernelStackKB = parseKB(trimmed)
                trimmed.startsWith("VmallocUsed:") -> info.vmallocUsedKB = parseKB(trimmed)
                trimmed.startsWith("Mapped:") -> info.mappedKB = parseKB(trimmed)
            }
        }

        // 计算已用内存
        if (info.totalKB > 0 && info.availableKB > 0) {
            info.usedKB = info.totalKB - info.availableKB
        }

        // === 内存分布分类计算 (混合: dumpsys OOM + /proc/meminfo) ===
        calculateMemoryDistribution(info)

        // 获取 ZRAM 统计
        getZramStats(info)

        // === P2: dumpsys procstats 进程级统计（缓存，每30秒刷新一次） ===
        loadProcstats(info)

        return info
    }

    @Volatile
    private var lastProcstatsTime = 0L
    @Volatile
    private var cachedProcstats = emptyList<Triple<String, Float, Float>>()

    private fun loadProcstats(info: MemoryInfo) {
        val now = System.currentTimeMillis()
        if (now - lastProcstatsTime < 30_000L) {
            info.topProcesses = cachedProcstats.map { "${it.first}: ${String.format("%.0f", it.second)}MB" }
            return
        }
        try {
            val output = ShellCommandDataSource.getDumpsysProcstats()
            cachedProcstats = ShellCommandDataSource.extractTopProcesses(output, 5)
            lastProcstatsTime = now
        } catch (_: Throwable) {}
        info.topProcesses = cachedProcstats.map { "${it.first}: ${String.format("%.0f", it.second)}MB" }
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

    // ================================================================
    //  内存分布分类 — 混合 dumpsys meminfo OOM + /proc/meminfo
    // ================================================================

    @Volatile
    private var lastDumpsysMeminfoTime = 0L
    @Volatile
    private var cachedDumpsysOom: DumpsysMeminfoParser.OomCategories? = null

    /**
     * 加载 dumpsys meminfo OOM 分类数据 (缓存 30 秒)
     *
     * dumpsys meminfo 开销较大 (需遍历所有进程)，每 30 秒刷新一次，
     * 日常刷新时复用缓存。OOM 分类不频繁变化，30 秒延迟对显示无影响。
     */
    private fun loadDumpsysOom(info: MemoryInfo) {
        val now = System.currentTimeMillis()
        if (now - lastDumpsysMeminfoTime < 30_000L) {
            cachedDumpsysOom?.let {
                info.systemProcessPssKB = it.systemPssKB
                info.appProcessPssKB = it.appPssKB + it.cachedPssKB // 应用进程 = 活跃 + 缓存
                info.cachedProcessPssKB = it.cachedPssKB
                info.dumpsysAvailable = it.isAvailable
            }
            return
        }
        try {
            val output = ShellCommandDataSource.getDumpsysMeminfoDetail()
            val (oom, _) = DumpsysMeminfoParser.parse(output)
            if (oom.isAvailable) {
                cachedDumpsysOom = oom
                lastDumpsysMeminfoTime = now
            }
            info.systemProcessPssKB = oom.systemPssKB
            info.appProcessPssKB = oom.appPssKB + oom.cachedPssKB
            info.cachedProcessPssKB = oom.cachedPssKB
            info.dumpsysAvailable = oom.isAvailable
        } catch (_: Throwable) {
            info.dumpsysAvailable = false
        }
    }

    /**
     * 内存分布分类计算 — 混合 dumpsys meminfo OOM + /proc/meminfo
     *
     * **优先模式 (dumpsys 可用):**
     *  空闲 (Free)   = MemFree                           — 完全未使用的物理内存
     *  缓存 (Cached) = Cached OOM PSS                    — 后台可回收的应用缓存
     *                + Buffers + Cached + SReclaimable    — 内核文件缓存 (page cache)
     *  系统 (System) = System OOM PSS                    — 系统进程 PSS
     *                  (Native + System + Persistent + Persistent Service)
     *                + SUnreclaim + PageTables            — 内核不可回收结构
     *                  + KernelStack + VmallocUsed + Shmem
     *  应用 (App)    = App OOM PSS (non-Cached user)     — 前台/可见/感知/服务进程
     *                  (Foreground + Visible + Perceptible + Services)
     *  其他 (Other)  = Total - (Free + Cached + System + App) — Lost/差额
     *
     * **降级模式 (/proc/meminfo only):**
     *  空闲 = MemFree
     *  缓存 = Buffers + Cached + SReclaimable
     *  系统 = SUnreclaim + PageTables + KernelStack + VmallocUsed + Shmem
     *  应用 = Total - 空闲 - 缓存 - 系统 (AnonPages + Mapped + ...)
     *  其他 = 差额
     */
    private fun calculateMemoryDistribution(info: MemoryInfo) {
        val total = info.totalKB
        if (total <= 0) return

        // 先加载 dumpsys OOM 数据
        loadDumpsysOom(info)

        val free = info.freeMemoryKB.coerceAtLeast(0)
        val buffers = info.buffersKB.coerceAtLeast(0)
        val cached = info.cachedKB.coerceAtLeast(0)
        val sReclaimable = info.slabReclaimableKB.coerceAtLeast(0)
        val sUnreclaim = info.slabUnreclaimKB.coerceAtLeast(0)
        val shmem = info.shmemKB.coerceAtLeast(0)
        val pageTables = info.pageTablesKB.coerceAtLeast(0)
        val kernelStack = info.kernelStackKB.coerceAtLeast(0)
        val vmallocUsed = info.vmallocUsedKB.coerceAtLeast(0)

        // 内核结构 (不可回收的系统和内核开销)
        val kernelStructures = sUnreclaim + pageTables + kernelStack + vmallocUsed + shmem

        // 内核文件缓存 (可回收)
        val kernelFileCache = buffers + cached + sReclaimable

        if (info.dumpsysAvailable && info.systemProcessPssKB >= 0 && info.appProcessPssKB >= 0) {
            // === 优先模式: 使用 dumpsys OOM 分类实现精确 app/system 分离 ===
            // 系统 = 系统进程 PSS + 内核不可回收结构
            val systemDumpsys = info.systemProcessPssKB.coerceAtLeast(0)
            val systemMemory = systemDumpsys + kernelStructures

            // 缓存 = 缓存进程 PSS (后台应用) + 内核文件缓存 (page cache)
            val cachedDumpsys = info.cachedProcessPssKB.coerceAtLeast(0)
            val cachedMemory = cachedDumpsys + kernelFileCache

            // 应用 = 活跃应用进程 PSS (前台/可见/感知/服务)
            val activeApp = (info.appProcessPssKB - cachedDumpsys).coerceAtLeast(0)

            // 其他 = Lost RAM
            val accounted = free + cachedMemory + systemMemory + activeApp
            val otherMemory = (total - accounted).coerceAtLeast(0)

            info.freeMemoryKB = free
            info.cachedMemoryKB = cachedMemory
            info.systemMemoryKB = systemMemory
            info.appMemoryKB = activeApp
            info.otherMemoryKB = otherMemory
        } else {
            // === 降级模式: 纯 /proc/meminfo ===
            // 缓存 = 内核文件缓存
            val cachedMemory = kernelFileCache
            // 系统 = 内核不可回收结构 (无法分离系统 vs 应用进程)
            val systemMemory = kernelStructures
            // 应用 = 剩下的全部 (AnonPages + Mapped + 系统进程 PSS + 应用进程 PSS)
            val appMemory = (total - free - cachedMemory - systemMemory).coerceAtLeast(0)
            // 其他 = 差额
            val otherMemory = (total - free - cachedMemory - systemMemory - appMemory).coerceAtLeast(0)

            info.freeMemoryKB = free
            info.cachedMemoryKB = cachedMemory
            info.systemMemoryKB = systemMemory
            info.appMemoryKB = appMemory
            info.otherMemoryKB = otherMemory
        }
    }
}
