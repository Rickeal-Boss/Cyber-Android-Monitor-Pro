package com.example.deviceinfoviewer.data.source

/**
 * dumpsys meminfo 输出解析器 — 精准提取 OOM 分类的内存统计
 *
 * 解析目标：
 * 1. "Total PSS by OOM adjustment" 段落 — 按进程优先级分组的 PSS 总量
 * 2. 总览摘要 — Used RAM / Free RAM / Lost RAM 中的 used pss/kernel
 *
 * OOM 分类映射规则 (参照 dumpsys meminfo AOSP 源码):
 *   系统进程 (System)     = Native + System + Persistent + Persistent Service
 *   应用进程 (App)        = Foreground + Visible + Perceptible + A Services + B Services + Cached
 *   缓存进程 (Cached)     = Cached (可回收的应用进程，计入 Free RAM)
 *
 * 输出单位统一为 KB。
 */
object DumpsysMeminfoParser {

    /**
     * OOM 分类解析结果
     */
    data class OomCategories(
        val systemPssKB: Long,       // 系统进程 PSS: Native + System + Persistent + PersistentService
        val appPssKB: Long,          // 应用进程 PSS: Foreground + Visible + Perceptible + Services
        val cachedPssKB: Long,       // 缓存进程 PSS: Cached
        val isAvailable: Boolean     // 数据是否成功解析
    )

    /**
     * dumpsys meminfo 摘要信息
     */
    data class MeminfoSummary(
        val totalRamKB: Long,
        val usedRamKB: Long,
        val freeRamKB: Long,
        val lostRamKB: Long,
        val usedPssKB: Long,         // used pss 子项
        val kernelUsedKB: Long,      // kernel 子项
        val cachedPssKB: Long,       // cached pss 子项 (来自 Free RAM 注释)
        val cachedKernelKB: Long     // cached kernel 子项
    )

    /**
     * 解析 dumpsys meminfo 输出，提取 OOM 分类和摘要
     *
     * @param output dumpsys meminfo (或 meminfo -a) 的完整输出
     * @return Pair<OomCategories, MeminfoSummary>
     */
    fun parse(output: String?): Pair<OomCategories, MeminfoSummary> {
        val emptyOom = OomCategories(-1L, -1L, -1L, false)
        val emptySummary = MeminfoSummary(-1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L)

        if (output.isNullOrBlank()) return Pair(emptyOom, emptySummary)

        val oom = parseOomCategories(output)
        val summary = parseSummary(output)

        return Pair(oom, summary)
    }

    // ========================================================
    //  Total PSS by OOM adjustment 解析
    // ========================================================

    /**
     * 解析 "Total PSS by OOM adjustment:" 段落
     *
     * 格式示例:
     *   Total PSS by OOM adjustment:
     *       1,722,628K: Native
     *       302,883K: System
     *       2,538,575K: Persistent
     *       24,735K: Persistent Service
     *       480,609K: Foreground
     *       1,178,460K: Visible
     *       776,717K: Perceptible
     *       85,791K: A Services
     *       0K: B Services
     *       177,923K: Cached
     */
    private fun parseOomCategories(output: String): OomCategories {
        var native = 0L
        var system = 0L
        var persistent = 0L
        var persistentService = 0L
        var foreground = 0L
        var visible = 0L
        var perceptible = 0L
        var aServices = 0L
        var bServices = 0L
        var cached = 0L

        var inOomSection = false

        for (line in output.split("\n")) {
            val trimmed = line.trim()

            // 检测段落起始
            if (trimmed == "Total PSS by OOM adjustment:") {
                inOomSection = true
                continue
            }

            // 段落结束检测: 遇到空行或下一段
            if (inOomSection) {
                if (trimmed.isEmpty() || trimmed.startsWith("Total") && !trimmed.startsWith("Total PSS by OOM")) {
                    inOomSection = false
                    continue
                }
            }

            if (!inOomSection) continue

            val result = parseOomLine(trimmed) ?: continue
            val (valueKB, category) = result

            when {
                category == "Native" -> native += valueKB
                category == "System" -> system += valueKB
                category == "Persistent" -> persistent += valueKB
                category == "Persistent Service" -> persistentService += valueKB
                category == "Foreground" -> foreground += valueKB
                category == "Visible" -> visible += valueKB
                category == "Perceptible" -> perceptible += valueKB
                category == "A Services" -> aServices += valueKB
                category == "B Services" -> bServices += valueKB
                category == "Cached" -> cached += valueKB
            }
        }

        val systemPss = native + system + persistent + persistentService
        val appPss = foreground + visible + perceptible + aServices + bServices

        val hasData = native > 0 || systemPss > 0 || appPss > 0

        return OomCategories(
            systemPssKB = if (hasData) systemPss else -1L,
            appPssKB = if (hasData) appPss else -1L,
            cachedPssKB = if (hasData) cached else -1L,
            isAvailable = hasData
        )
    }

    /**
     * 解析单行 OOM 分类:   "1,234K: CategoryName" → Pair(1234, "CategoryName")
     */
    private fun parseOomLine(line: String): Pair<Long, String>? {
        val colonIdx = line.indexOf(':')
        if (colonIdx < 0) return null

        val valuePart = line.substring(0, colonIdx).trim()
        val categoryPart = line.substring(colonIdx + 1).trim()

        // 提取数值 (移除逗号千位分隔符和 "K" 后缀)
        val numericStr = valuePart.replace(",", "")
            .replace("K", "").replace("kB", "")
            .trim()

        val valueKB = numericStr.toLongOrNull() ?: return null
        if (valueKB <= 0 && categoryPart != "B Services") return null  // B Services 可能为 0K

        // 处理 "Persistent Service"  vs "Persistent" 的歧义
        val normalizedCategory = when {
            categoryPart.contains("Persistent Service", ignoreCase = true) -> "Persistent Service"
            categoryPart.equals("Persistent", ignoreCase = true) -> "Persistent"
            categoryPart.equals("A Services", ignoreCase = true) -> "A Services"
            categoryPart.equals("B Services", ignoreCase = true) -> "B Services"
            else -> categoryPart
        }

        return Pair(valueKB, normalizedCategory)
    }

    // ========================================================
    //  总览摘要 (Total/Free/Used/Lost RAM) 解析
    // ========================================================

    /**
     * 解析 dumpsys meminfo 总览部分
     *
     * 格式示例:
     *   Total RAM: 7,500,000K (status normal)
     *    Free RAM: 3,500,000K (  500,000K cached pss + 1,000,000K cached kernel + 2,000,000K free)
     *    Used RAM: 4,000,000K (  2,500,000K used pss + 1,500,000K kernel)
     *   Lost RAM: 500,000K
     */
    private fun parseSummary(output: String): MeminfoSummary {
        var totalRam = -1L
        var freeRam = -1L
        var usedRam = -1L
        var lostRam = -1L
        var usedPss = -1L
        var kernelUsed = -1L
        var cachedPss = -1L
        var cachedKernel = -1L

        for (line in output.split("\n")) {
            val trimmed = line.trim()

            when {
                trimmed.startsWith("Total RAM:") -> {
                    totalRam = extractFirstNumberKB(trimmed)
                }

                trimmed.startsWith("Free RAM:") -> {
                    freeRam = extractFirstNumberKB(trimmed)
                    // 从注释中提取 cached pss 和 cached kernel
                    // 格式: Free RAM: X (Y cached pss + Z cached kernel + W free)
                    val parenIdx = trimmed.indexOf('(')
                    if (parenIdx >= 0) {
                        val parenContent = trimmed.substring(parenIdx + 1)
                        cachedPss = extractNamedValue(parenContent, "cached pss")
                        cachedKernel = extractNamedValue(parenContent, "cached kernel")
                    }
                }

                trimmed.startsWith("Used RAM:") -> {
                    usedRam = extractFirstNumberKB(trimmed)
                    val parenIdx = trimmed.indexOf('(')
                    if (parenIdx >= 0) {
                        val parenContent = trimmed.substring(parenIdx + 1)
                        usedPss = extractNamedValue(parenContent, "used pss")
                        kernelUsed = extractNamedValue(parenContent, "kernel")
                    }
                }

                trimmed.startsWith("Lost RAM:") -> {
                    lostRam = extractFirstNumberKB(trimmed)
                }
            }
        }

        return MeminfoSummary(
            totalRamKB = totalRam,
            usedRamKB = usedRam,
            freeRamKB = freeRam,
            lostRamKB = lostRam,
            usedPssKB = usedPss,
            kernelUsedKB = kernelUsed,
            cachedPssKB = cachedPss,
            cachedKernelKB = cachedKernel
        )
    }

    /**
     * 从 "1,234,567K" 格式的字符串中提取 Long 值 (KB)
     */
    private fun extractFirstNumberKB(text: String): Long {
        val match = Regex("""([\d,]+)\s*K""").find(text) ?: return -1L
        return match.groupValues[1].replace(",", "").toLongOrNull() ?: -1L
    }

    /**
     * 从 "Y cached pss + Z cached kernel + W free" 格式中提取指定项的值
     */
    private fun extractNamedValue(text: String, name: String): Long {
        // 匹配:  "123,456K named_key"
        // 注意: "+" 号前面的空格、后缀
        val escapedName = Regex.escape(name)
        val regex = Regex("""([\d,]+)\s*K\s*$escapedName""")
        val match = regex.find(text) ?: return -1L
        return match.groupValues[1].replace(",", "").toLongOrNull() ?: -1L
    }
}
