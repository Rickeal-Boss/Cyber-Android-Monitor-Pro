package com.example.deviceinfoviewer.data.source

import android.os.Build

import com.example.deviceinfoviewer.data.model.SystemInfo

/**
 * 系统信息数据源，通过反射读取 Build 字段 + 深度待机统计
 */
class SystemDataSource {

    fun getSystemInfo(): SystemInfo {
        val info = SystemInfo()

        // 反射读取 Build 全部字段
        val buildFields = mutableMapOf<String, String>()
        for (field in Build::class.java.declaredFields) {
            try {
                val name = field.name
                val value = field.get(null)
                buildFields[name] = if (value is String) value else value.toString()
            } catch (_: Throwable) {}
        }
        for (field in Build.VERSION::class.java.declaredFields) {
            try {
                val name = "VERSION.${field.name}"
                val value = field.get(null)
                buildFields[name] = value.toString()
            } catch (_: Throwable) {}
        }

        info.buildFields = buildFields
        info.androidVersion = Build.VERSION.RELEASE
        info.bootloader = Build.BOOTLOADER
        info.securityPatch = Build.VERSION.SECURITY_PATCH

        info.kernelVersion = SysFsReader.readLine("/proc/version")
        info.javaVmVersion = System.getProperty("java.vm.version", "")
        info.javaRuntimeName = System.getProperty("java.runtime.name", "")

        // 开机时长
        info.uptimeSeconds = readUptime()

        // 深度待机统计
        collectSleepStats(info)

        return info
    }

    private fun readUptime(): Long {
        try {
            val line = SysFsReader.readLine("/proc/uptime")
            if (line.isNotBlank()) {
                val parts = line.trim().split("\\s+".toRegex())
                return parts.firstOrNull()?.toFloatOrNull()?.toLong() ?: 0L
            }
        } catch (_: Throwable) {}
        // shell 兜底 (Android 13+ SELinux 可能拦截直接文件 I/O)
        try {
            val line = Runtime.getRuntime().exec(arrayOf("cat", "/proc/uptime"))
                .inputStream.bufferedReader().readText().trim()
            if (line.isNotBlank()) {
                val parts = line.split("\\s+".toRegex())
                return parts.firstOrNull()?.toFloatOrNull()?.toLong() ?: 0L
            }
        } catch (_: Throwable) {}
        return try {
            android.os.SystemClock.elapsedRealtime() / 1000
        } catch (_: Throwable) { 0L }
    }

    /**
     * 深度待机统计 — 多源融合
     * 优先 dumpsys batterystats --checkin，降级为 /proc/uptime idle 时间
     */
    private fun collectSleepStats(info: SystemInfo) {
        try {
            val uptimeSec = info.uptimeSeconds
            if (uptimeSec <= 0) return

            // ─ 方式1: /proc/uptime idle (所有核心累计空闲秒数) ─
            try {
                var line = SysFsReader.readLine("/proc/uptime")
                // shell 兜底
                if (line.isBlank()) {
                    try {
                        line = Runtime.getRuntime().exec(arrayOf("cat", "/proc/uptime"))
                            .inputStream.bufferedReader().readText().trim()
                    } catch (_: Throwable) {}
                }
                if (line.isNotBlank()) {
                    val parts = line.trim().split("\\s+".toRegex())
                    val idleSec = parts.getOrNull(1)?.toFloatOrNull()?.toLong() ?: 0L
                    if (idleSec > 0) {
                        info.deepSleepSeconds = idleSec
                        info.sleepEfficiency = (idleSec.toFloat() / uptimeSec * 100f).coerceIn(0f, 100f)
                        info.sleepSource = "/proc/uptime idle"
                    }
                }
            } catch (_: Throwable) {}

            // ─ 方式2: dumpsys batterystats --checkin (更精确) ─
            try {
                val proc = Runtime.getRuntime().exec(
                    arrayOf("/system/bin/sh", "-c", "dumpsys batterystats --checkin 2>/dev/null")
                )
                val output = proc.inputStream.bufferedReader().readText()
                proc.waitFor()

                // "ds" (device sleep): ds,<duration_ms>,<count>
                val dsMatch = Regex("""^ds,(\d+),(\d+)""", RegexOption.MULTILINE).find(output)
                if (dsMatch != null) {
                    val durMs = dsMatch.groupValues[1].toLongOrNull() ?: 0L
                    if (durMs > 0) {
                        info.deepSleepSeconds = durMs / 1000
                        info.wakeupCount = dsMatch.groupValues[2].toLongOrNull() ?: 0L
                        info.sleepEfficiency = (info.deepSleepSeconds.toFloat() / uptimeSec * 100f).coerceIn(0f, 100f)
                        info.sleepSource = "dumpsys batterystats"
                    }
                }

                // 唤醒次数聚合
                val wakeMatches = Regex("""^w,(\d+)""", RegexOption.MULTILINE).findAll(output).toList()
                if (wakeMatches.isNotEmpty()) {
                    info.wakeupCount = wakeMatches.sumOf { it.groupValues[1].toLongOrNull() ?: 0L }
                }
            } catch (_: Throwable) { /* dumpsys 不可用 */ }

            // Doze 时间 (粗略: 深睡的 ~70%)
            if (info.dozeTimeSeconds <= 0 && info.deepSleepSeconds > 0) {
                info.dozeTimeSeconds = (info.deepSleepSeconds * 0.7).toLong()
            }
        } catch (_: Throwable) {}
    }
}
