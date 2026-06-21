package com.example.deviceinfoviewer.service

import android.content.Context
import android.content.SharedPreferences

/**
 * 悬浮窗配置 — v3: 8种实时指标 + FPS + 每指标独立刷新间隔
 */
object FloatingWindowConfig {
    private const val PREFS = "floating_window"
    private var prefs: SharedPreferences? = null

    fun init(ctx: Context) {
        if (prefs == null) prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    var enabled: Boolean
        get() = prefs?.getBoolean("enabled", false) ?: false
        set(v) { prefs?.edit()?.putBoolean("enabled", v)?.apply() }

    // ── 9 种实时指标 ──
    var showGpuUsage: Boolean
        get() = prefs?.getBoolean("show_gpu_usage", true) ?: true
        set(v) { prefs?.edit()?.putBoolean("show_gpu_usage", v)?.apply() }

    var showCpuTemp: Boolean
        get() = prefs?.getBoolean("show_cpu_temp", true) ?: true
        set(v) { prefs?.edit()?.putBoolean("show_cpu_temp", v)?.apply() }

    var showGpuTemp: Boolean
        get() = prefs?.getBoolean("show_gpu_temp", false) ?: false
        set(v) { prefs?.edit()?.putBoolean("show_gpu_temp", v)?.apply() }

    var showCpuFreq: Boolean
        get() = prefs?.getBoolean("show_cpu_freq", false) ?: false
        set(v) { prefs?.edit()?.putBoolean("show_cpu_freq", v)?.apply() }

    var showRam: Boolean
        get() = prefs?.getBoolean("show_ram", true) ?: true
        set(v) { prefs?.edit()?.putBoolean("show_ram", v)?.apply() }

    var showBatteryTemp: Boolean
        get() = prefs?.getBoolean("show_battery_temp", false) ?: false
        set(v) { prefs?.edit()?.putBoolean("show_battery_temp", v)?.apply() }

    var showBatteryCurrent: Boolean
        get() = prefs?.getBoolean("show_battery_current", false) ?: false
        set(v) { prefs?.edit()?.putBoolean("show_battery_current", v)?.apply() }

    var showBatteryPower: Boolean
        get() = prefs?.getBoolean("show_battery_power", false) ?: false
        set(v) { prefs?.edit()?.putBoolean("show_battery_power", v)?.apply() }

    var showFps: Boolean
        get() = prefs?.getBoolean("show_fps", false) ?: false
        set(v) { prefs?.edit()?.putBoolean("show_fps", v)?.apply() }

    // ── 每指标刷新间隔 (ms, 默认 1000ms=1s) ──
    private const val DEFAULT_REFRESH_MS = 1000

    var gpuUsageRefreshMs: Int
        get() = prefs?.getInt("refresh_gpu_usage", DEFAULT_REFRESH_MS) ?: DEFAULT_REFRESH_MS
        set(v) { prefs?.edit()?.putInt("refresh_gpu_usage", v)?.apply() }

    var cpuTempRefreshMs: Int
        get() = prefs?.getInt("refresh_cpu_temp", DEFAULT_REFRESH_MS) ?: DEFAULT_REFRESH_MS
        set(v) { prefs?.edit()?.putInt("refresh_cpu_temp", v)?.apply() }

    var gpuTempRefreshMs: Int
        get() = prefs?.getInt("refresh_gpu_temp", DEFAULT_REFRESH_MS) ?: DEFAULT_REFRESH_MS
        set(v) { prefs?.edit()?.putInt("refresh_gpu_temp", v)?.apply() }

    var cpuFreqRefreshMs: Int
        get() = prefs?.getInt("refresh_cpu_freq", DEFAULT_REFRESH_MS) ?: DEFAULT_REFRESH_MS
        set(v) { prefs?.edit()?.putInt("refresh_cpu_freq", v)?.apply() }

    var ramRefreshMs: Int
        get() = prefs?.getInt("refresh_ram", DEFAULT_REFRESH_MS) ?: DEFAULT_REFRESH_MS
        set(v) { prefs?.edit()?.putInt("refresh_ram", v)?.apply() }

    var batteryTempRefreshMs: Int
        get() = prefs?.getInt("refresh_battery_temp", DEFAULT_REFRESH_MS) ?: DEFAULT_REFRESH_MS
        set(v) { prefs?.edit()?.putInt("refresh_battery_temp", v)?.apply() }

    var batteryCurRefreshMs: Int
        get() = prefs?.getInt("refresh_battery_cur", DEFAULT_REFRESH_MS) ?: DEFAULT_REFRESH_MS
        set(v) { prefs?.edit()?.putInt("refresh_battery_cur", v)?.apply() }

    var batteryPowRefreshMs: Int
        get() = prefs?.getInt("refresh_battery_pow", DEFAULT_REFRESH_MS) ?: DEFAULT_REFRESH_MS
        set(v) { prefs?.edit()?.putInt("refresh_battery_pow", v)?.apply() }

    var fpsRefreshMs: Int
        get() = prefs?.getInt("refresh_fps", DEFAULT_REFRESH_MS) ?: DEFAULT_REFRESH_MS
        set(v) { prefs?.edit()?.putInt("refresh_fps", v)?.apply() }

    // ── 位置记忆 ──
    fun getWindowX(key: String, default: Int): Int = prefs?.getInt("pos_${key}_x", default) ?: default
    fun setWindowX(key: String, v: Int) { prefs?.edit()?.putInt("pos_${key}_x", v)?.apply() }

    fun getWindowY(key: String, default: Int): Int = prefs?.getInt("pos_${key}_y", default) ?: default
    fun setWindowY(key: String, v: Int) { prefs?.edit()?.putInt("pos_${key}_y", v)?.apply() }
}
