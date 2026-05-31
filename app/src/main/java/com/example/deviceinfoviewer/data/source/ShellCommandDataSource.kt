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
    fun getDumpsysBattery(): String = exec("/system/bin/dumpsys", "battery")

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
