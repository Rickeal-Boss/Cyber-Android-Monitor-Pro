package com.example.deviceinfoviewer.data.source

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Shell 命令数据源 — 通过 ProcessBuilder 执行 dumpsys / logcat 等系统命令，
 * 获取普通 API 和 sysfs 无法提供的深度系统信息。
 *
 * 支持的命令：
 * - dumpsys battery  → 电池详细信息（充电协议、充电电流上限等）
 * - dumpsys cpuinfo  → 各进程 CPU 负载排名
 * - dumpsys thermalservice → 全机温控策略与温度
 * - dumpsys meminfo  → 内存分配详情
 * - logcat -d -b events -t 50 → 系统事件日志（最近50条）
 * - cat /proc/stat    → CPU 时间统计
 */
object ShellCommandDataSource {

    /** 命令执行超时 (秒) */
    private const val TIMEOUT_SECONDS: Long = 8

    /**
     * 执行 shell 命令并返回完整输出
     */
    @JvmStatic
    fun exec(vararg command: String): String {
        val output = StringBuilder()
        var process: Process? = null
        try {
            process = ProcessBuilder(*command)
                .redirectErrorStream(true)
                .start()
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    output.append(line).append("\n")
                }
            }
            val finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
            }
        } catch (_: Throwable) {
            return ""
        } finally {
            process?.destroy()
        }
        return output.toString()
    }

    // ========== dumpsys 系列 ==========

    /**
     * 获取 dumpsys battery 输出
     * 包含：充电协议 (Wireless/USB/AC)、最大充电电流/电压、Charge counter 等
     */
    @JvmStatic
    fun getDumpsysBattery(): String = exec("/system/bin/sh", "-c", "dumpsys battery")

    /**
     * 获取 dumpsys batteryproperties 输出 (Android 10+)
     * 包含：设计容量、充电状态、循环计数等
     */
    @JvmStatic
    fun getDumpsysBatteryProperties(): String =
        exec("/system/bin/sh", "-c", "dumpsys batteryproperties 2>/dev/null")

    /**
     * 获取 dumpsys thermalservice 输出
     * 包含：全机温控节流状态、各 sensor 温度、冷却设备状态
     */
    @JvmStatic
    fun getDumpsysThermal(): String = exec("/system/bin/dumpsys", "thermalservice")

    /**
     * 获取 dumpsys cpuinfo 输出
     * 包含：各进程 CPU 使用时间排名
     */
    @JvmStatic
    fun getDumpsysCpuinfo(): String = exec("/system/bin/dumpsys", "cpuinfo")

    /**
     * 获取 dumpsys meminfo 输出
     * 包含：系统整体 + 各进程的 PSS/RSS/Heap 等详细内存分配
     */
    @JvmStatic
    fun getDumpsysMeminfo(): String = exec("/system/bin/dumpsys", "meminfo")

    /**
     * 获取 dumpsys display 输出
     * 包含：屏幕分辨率、刷新率、HDR 能力等
     */
    @JvmStatic
    fun getDumpsysDisplay(): String = exec("/system/bin/dumpsys", "display")

    /**
     * 获取 dumpsys gfxinfo 输出
     * 包含：GPU 帧率统计、Jank 帧率、百分位帧时间等
     */
    @JvmStatic
    fun getDumpsysGfxinfo(): String = exec("/system/bin/dumpsys", "gfxinfo")

    /**
     * 获取 dumpsys wifi 输出
     * 包含：WiFi 芯片信息、连接统计、信号质量、省电模式等
     */
    @JvmStatic
    fun getDumpsysWifi(): String = exec("/system/bin/dumpsys", "wifi")

    /**
     * 获取 dumpsys procstats 输出（进程内存/CPU 统计）
     * 包含：各进程 PSS/RSS/USS、CPU 时间、服务状态变化等
     * 注意：仅返回最近 3 小时的聚合数据（用 --hours 3 限制输出大小）
     */
    @JvmStatic
    fun getDumpsysProcstats(): String =
        exec("/system/bin/dumpsys", "procstats", "--hours", "3")

    /**
     * 获取 dumpsys meminfo 输出（含进程级详情）
     * 注意：需要 DUMP 权限；部分 ROM 限制访问
     */
    @JvmStatic
    fun getDumpsysMeminfoDetail(): String =
        exec("/system/bin/dumpsys", "meminfo", "-a")

    // ========== logcat 系列 ==========

    /**
     * 获取最近 N 条系统事件日志 (events buffer)
     */
    @JvmStatic
    fun getLogcatEvents(count: Int): String =
        exec("logcat", "-d", "-b", "events", "-t", count.toString())

    /**
     * 获取最近 N 条主日志 (main buffer)
     */
    @JvmStatic
    fun getLogcatMain(count: Int): String =
        exec("logcat", "-d", "-b", "main", "-t", count.toString())

    // ========== 解析辅助方法 ==========

    /**
     * 从 dumpsys battery 输出中提取指定键的值（值在 ": " 之后）
     * 示例：输入 "  Max charging current: 75000" → "75000"
     */
    @JvmStatic
    fun extractDumpsysValue(dumpsysOutput: String?, key: String): String? {
        if (dumpsysOutput.isNullOrEmpty()) return null
        for (line in dumpsysOutput.split("\n")) {
            val trimmed = line.trim()
            if (trimmed.startsWith("$key:") || trimmed.startsWith("$key ")) {
                val colonIdx = trimmed.indexOf(':')
                if (colonIdx >= 0) {
                    return trimmed.substring(colonIdx + 1).trim()
                }
            }
        }
        return null
    }

    /**
     * 从 dumpsys battery 提取 Integer
     */
    @JvmStatic
    fun extractInt(dumpsysOutput: String, key: String): Int =
        extractDumpsysValue(dumpsysOutput, key)?.toIntOrNull() ?: -1

    /**
     * 从 dumpsys battery 提取 Long
     */
    @JvmStatic
    fun extractLong(dumpsysOutput: String, key: String): Long =
        extractDumpsysValue(dumpsysOutput, key)?.toLongOrNull() ?: -1L

    /**
     * 从 thermal service 输出中提取温度列表
     */
    @JvmStatic
    fun extractThermalTemperatures(thermalOutput: String?): List<Float> {
        val temps = mutableListOf<Float>()
        if (thermalOutput.isNullOrEmpty()) return temps
        for (line in thermalOutput.split("\n")) {
            // 匹配 "temperature: xx.x" 格式
            if (line.contains("temperature:") || line.contains("temp:")) {
                try {
                    var idx = line.indexOf("temperature:")
                    if (idx < 0) idx = line.indexOf("temp:")
                    val numPart = line.substring(idx).replace(Regex("[^0-9.]"), " ").trim()
                    val parts = numPart.split("\\s+".toRegex())
                    for (part in parts) {
                        if (part.isNotEmpty()) {
                            part.toFloatOrNull()?.let { temps.add(it) }
                        }
                    }
                } catch (_: Throwable) {}
            }
        }
        return temps
    }

    /**
     * 从 dumpsys gfxinfo 输出中提取帧率相关统计
     * @return Pair<总帧数, jank帧百分比>
     */
    @JvmStatic
    fun extractGfxFrameStats(gfxOutput: String?): Pair<Long, Float> {
        if (gfxOutput.isNullOrEmpty()) return Pair(-1L, Float.NaN)
        var totalFrames = -1L
        var jankyPct = Float.NaN
        for (line in gfxOutput.split("\n")) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("Total frames rendered:") -> {
                    totalFrames = trimmed.substringAfter(":").trim().toLongOrNull() ?: -1L
                }
                trimmed.startsWith("Janky frames:") -> {
                    val jankPart = trimmed.substringAfter(":").trim()
                    jankyPct = jankPart.replace("%", "").replace("(", "").replace(")", "")
                        .trim().toFloatOrNull() ?: Float.NaN
                }
            }
        }
        return Pair(totalFrames, jankyPct)
    }

    /**
     * 从 dumpsys gfxinfo 提取 GPU 负载代理指标
     * 如果 Jank 帧率 > 20%，说明 GPU 负载较高
     * @return GPU 负载估算值 (0-100%) 或 NaN
     */
    @JvmStatic
    fun estimateGpuLoadFromGfx(gfxOutput: String?): Float {
        val (_, jankyPct) = extractGfxFrameStats(gfxOutput)
        if (jankyPct.isNaN()) return Float.NaN
        // Jank 帧率映射: <5% jank → 轻载, >30% jank → 重载
        return (jankyPct * 2.5f).coerceIn(0f, 100f)
    }

    /**
     * 从 dumpsys wifi 输出提取 WiFi 芯片温度
     * @return 温度℃ 或 NaN
     */
    @JvmStatic
    fun extractWifiTemperature(wifiOutput: String?): Float {
        if (wifiOutput.isNullOrEmpty()) return Float.NaN
        val patterns = listOf(
            Regex("""(?i)(?:wifi|chip).*?temp(?:erature)?[=: ]+(\d+)"""),
            Regex("""(?i)temp(?:erature)?[=: ]+(\d+)\s*(?:C|c)"""),
        )
        for (regex in patterns) {
            val match = regex.find(wifiOutput) ?: continue
            val temp = match.groupValues[1].toFloatOrNull() ?: continue
            if (temp in 10f..150f) return temp
            if (temp > 1000f) return temp / 1000f
        }
        return Float.NaN
    }
    // 从 dumpsys wifi 提取省电模式状态
    @JvmStatic
    fun extractWifiPowerSave(wifiOutput: String?): String {
        if (wifiOutput.isNullOrEmpty()) return ""
        return if (Regex("""(?i)power.?save[=: ]+(on|enabled|true)""").containsMatchIn(wifiOutput)) "省电模式"
        else if (Regex("""(?i)power.?save[=: ]+(off|disabled|false)""").containsMatchIn(wifiOutput)) "正常模式"
        else ""
    }

    /**
     * 从 dumpsys procstats 提取进程级内存统计
     * @return List of Triple<进程名, PSS_MB, CPU时间秒>
     */
    @JvmStatic
    fun extractTopProcesses(procstatsOutput: String?, topN: Int = 5): List<Triple<String, Float, Float>> {
        val result = mutableListOf<Triple<String, Float, Float>>()
        if (procstatsOutput.isNullOrEmpty()) return result

        // procstats 输出格式复杂，使用多层正则匹配
        // 匹配 "  * com.example.app / u0a123 / v12345:"
        val processRegex = Regex("""^\s*\*\s*([\w.]+)\s*/\s*u\d+a?\d+\s*/\s*v?(\d+)""")
        val pssRegex = Regex("""(?i)\b(?:pss|total)[=: ]+(\d+\.?\d*)\s*([KMGkmg]?)[Bb]?""")
        val cpuRegex = Regex("""(?i)(?:cpu|cpu time)[=: ]+(\d+\.?\d*)\s*s?""")

        val processes = mutableListOf<Triple<String, Float, Float>>()
        val lines = procstatsOutput.split("\n")
        var currentPkg = ""

        for (line in lines) {
            val trimmed = line.trim()
            val procMatch = processRegex.find(line)
            if (procMatch != null) {
                currentPkg = procMatch.groupValues[1]
                continue
            }
            if (currentPkg.isEmpty()) continue

            // 匹配 PSS
            val pssMatch = pssRegex.find(trimmed)
            if (pssMatch != null) {
                val value = pssMatch.groupValues[1].toFloatOrNull() ?: continue
                val unit = pssMatch.groupValues[2].uppercase()
                val pssMb = when (unit) {
                    "G" -> value * 1024f
                    "M", "" -> value
                    "K" -> value / 1024f
                    else -> value
                }
                var cpuSec = 0f
                val cpuMatch = cpuRegex.find(trimmed)
                cpuMatch?.let { cpuSec = it.groupValues[1].toFloatOrNull() ?: 0f }

                if (pssMb > 0) processes.add(Triple(currentPkg, pssMb, cpuSec))
                currentPkg = ""
            }
        }

        return processes.sortedByDescending { it.second }.take(topN)
    }

    /**
     * 从 dumpsys meminfo -a 提取总内存统计
     * @return Pair<总PSS_MB, 总RSS_MB>
     */
    @JvmStatic
    fun extractTotalMemoryFromMeminfo(meminfoOutput: String?): Pair<Float, Float> {
        if (meminfoOutput.isNullOrEmpty()) return Pair(Float.NaN, Float.NaN)
        var totalPss = Float.NaN
        var totalRss = Float.NaN
        for (line in meminfoOutput.split("\n")) {
            val trimmed = line.trim()
            if (trimmed.startsWith("Total PSS by category:") || trimmed.contains("Total PSS:")) {
                val match = Regex("""([\d,.]+)\s*[Kk]""").find(trimmed)
                match?.let { totalPss = it.groupValues[1].replace(",", "").toFloatOrNull()?.div(1024f) ?: Float.NaN }
            }
            if (trimmed.contains("Total RAM:") || trimmed.contains("TOTAL:")) {
                val match = Regex("""([\d,.]+)\s*[Kk]""").find(trimmed)
                match?.let { totalRss = it.groupValues[1].replace(",", "").toFloatOrNull()?.div(1024f) ?: Float.NaN }
            }
        }
        return Pair(totalPss, totalRss)
    }

    // ========== /proc 额外读取 ==========

    /**
     * 读取 /proc/stat 获取 CPU 时间统计
     * 可用于计算各核心负载百分比
     */
    @JvmStatic
    fun getProcStat(): String = SysFsReader.readAll("/proc/stat")

    /**
     * 读取 /proc/version 内核完整版本字符串
     */
    @JvmStatic
    fun getKernelVersionFull(): String = SysFsReader.readLine("/proc/version")

    /**
     * 读取 /proc/uptime 系统启动时间
     */
    @JvmStatic
    fun getUptimeSeconds(): Float {
        val line = SysFsReader.readLine("/proc/uptime")
        if (line.isEmpty()) return -1f
        val parts = line.split("\\s+".toRegex())
        return parts.getOrNull(0)?.toFloatOrNull() ?: -1f
    }
}
