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
}
