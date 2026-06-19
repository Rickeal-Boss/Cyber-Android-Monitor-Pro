package com.example.deviceinfoviewer

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * SharedPreferences 封装 — Kotlin 属性委托风格
 */
class AppSettings private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "device_info_viewer_settings"
        private const val DEFAULT_INTERVAL_MS = 2000
        private const val DEFAULT_OPACITY = 0.85f
        private const val DEFAULT_DARK_MODE = true

        @Volatile
        private var instance: AppSettings? = null

        fun getInstance(context: Context): AppSettings =
            instance ?: synchronized(this) {
                instance ?: AppSettings(context).also { instance = it }
            }
    }

    var refreshIntervalMs: Int
        get() = prefs.getInt("refresh_interval_ms", DEFAULT_INTERVAL_MS)
        set(value) = prefs.edit { putInt("refresh_interval_ms", value) }

    // ── 分模块刷新间隔 (0=使用全局默认) ──
    var cpuRefreshMs: Int
        get() = prefs.getInt("cpu_refresh_ms", 0)
        set(value) = prefs.edit { putInt("cpu_refresh_ms", value) }

    var gpuRefreshMs: Int
        get() = prefs.getInt("gpu_refresh_ms", 0)
        set(value) = prefs.edit { putInt("gpu_refresh_ms", value) }

    var memoryRefreshMs: Int
        get() = prefs.getInt("memory_refresh_ms", 0)
        set(value) = prefs.edit { putInt("memory_refresh_ms", value) }

    var batteryRefreshMs: Int
        get() = prefs.getInt("battery_refresh_ms", 0)
        set(value) = prefs.edit { putInt("battery_refresh_ms", value) }

    var networkRefreshMs: Int
        get() = prefs.getInt("network_refresh_ms", 0)
        set(value) = prefs.edit { putInt("network_refresh_ms", value) }

    var gpsRefreshMs: Int
        get() = prefs.getInt("gps_refresh_ms", 0)
        set(value) = prefs.edit { putInt("gps_refresh_ms", value) }

    var sensorsRefreshMs: Int
        get() = prefs.getInt("sensors_refresh_ms", 0)
        set(value) = prefs.edit { putInt("sensors_refresh_ms", value) }

    fun effectiveRefreshMs(moduleMs: Int): Int =
        if (moduleMs > 0) moduleMs else refreshIntervalMs

    var floatingWindowEnabled: Boolean
        get() = prefs.getBoolean("floating_window_enabled", false)
        set(value) = prefs.edit { putBoolean("floating_window_enabled", value) }

    var floatingWindowOpacity: Float
        get() = prefs.getFloat("floating_window_opacity", DEFAULT_OPACITY)
        set(value) = prefs.edit { putFloat("floating_window_opacity", value) }

    var darkMode: Boolean
        get() = prefs.getBoolean("dark_mode", DEFAULT_DARK_MODE)
        set(value) = prefs.edit { putBoolean("dark_mode", value) }

    var floatingWindowX: Int
        get() = prefs.getInt("floating_window_x", -1)
        set(value) = prefs.edit { putInt("floating_window_x", value) }

    var floatingWindowY: Int
        get() = prefs.getInt("floating_window_y", -1)
        set(value) = prefs.edit { putInt("floating_window_y", value) }

    // ── 震动反馈 ──
    var hapticEnabled: Boolean
        get() = prefs.getBoolean("haptic_enabled", true)
        set(value) = prefs.edit { putBoolean("haptic_enabled", value) }

    var hapticIntensity: Int
        get() = prefs.getInt("haptic_intensity", 2) // 1=弱 2=中 3=强
        set(value) = prefs.edit { putInt("haptic_intensity", value) }

    var dualCellBattery: Boolean
        get() = prefs.getBoolean("dual_cell_battery", false)
        set(value) = prefs.edit { putBoolean("dual_cell_battery", value) }

    // 悬浮窗单项显示开关（默认全部显示）
    var showCpuTemp: Boolean
        get() = prefs.getBoolean("show_cpu_temp", true)
        set(value) = prefs.edit { putBoolean("show_cpu_temp", value) }

    var showCpuFreq: Boolean
        get() = prefs.getBoolean("show_cpu_freq", true)
        set(value) = prefs.edit { putBoolean("show_cpu_freq", value) }

    var showBattery: Boolean
        get() = prefs.getBoolean("show_battery", true)
        set(value) = prefs.edit { putBoolean("show_battery", value) }

    var showRam: Boolean
        get() = prefs.getBoolean("show_ram", true)
        set(value) = prefs.edit { putBoolean("show_ram", value) }

    // ── 应用语言偏好（i18n）──
    // 值为 LocaleManager.LANG_SYSTEM（"system"）或 BCP 47 语言 code（如 "zh-CN"、"en"、"ja"）
    // 默认 "system" = 跟随系统语言；用户手动选择后持久化，下次启动优先读取用户偏好
    var appLanguage: String
        get() = prefs.getString("app_language", "system") ?: "system"
        set(value) = prefs.edit { putString("app_language", value) }
}
