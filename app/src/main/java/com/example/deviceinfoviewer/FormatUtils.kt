package com.example.deviceinfoviewer

import java.util.Locale

/**
 * 格式化工具 — Kotlin 扩展函数风格
 */
object FormatUtils {

    /**
     * 格式化频率 KHz → 自动选单位
     */
    fun formatFreq(khz: Long): String = when {
        khz <= 0 -> "N/A"
        khz >= 1_000_000L -> String.format(Locale.US, "%.2f GHz", khz / 1_000_000.0)
        khz >= 1_000L -> String.format(Locale.US, "%.0f MHz", khz / 1_000.0)
        else -> String.format(Locale.US, "%d KHz", khz)
    }

    /**
     * 格式化字节数 → 自动选单位
     */
    fun formatBytes(bytes: Long): String = when {
        bytes <= 0 -> "0 B"
        bytes >= 1_073_741_824L -> String.format(Locale.US, "%.2f GB", bytes / 1_073_741_824.0)
        bytes >= 1_048_576L -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
        bytes >= 1_024L -> String.format(Locale.US, "%.0f KB", bytes / 1_024.0)
        else -> String.format(Locale.US, "%d B", bytes)
    }

    /**
     * 格式化摄氏温度
     */
    fun formatTempCelsius(temp: Float): String =
        if (temp.isNaN()) "N/A"
        else String.format(Locale.US, "%.1f°C", temp)

    /**
     * 格式化信号强度 (dBm)
     */
    fun formatDbm(dbm: Int): String =
        if (dbm == Int.MAX_VALUE || dbm == Int.MIN_VALUE) "N/A"
        else String.format(Locale.US, "%d dBm", dbm)

    /**
     * 格式化百分比
     */
    fun formatPercent(pct: Int): String =
        if (pct < 0 || pct > 100) "N/A"
        else "$pct%"

    /**
     * 频率扩展 — Long.khz
     */
    val Long.asFreq: String get() = formatFreq(this)

    /**
     * 字节扩展 — Long.bytes
     */
    val Long.asBytes: String get() = formatBytes(this)

    /**
     * 温度扩展
     */
    val Float.asTemp: String get() = formatTempCelsius(this)
}
