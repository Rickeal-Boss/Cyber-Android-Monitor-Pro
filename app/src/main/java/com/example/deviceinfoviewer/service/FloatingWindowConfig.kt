package com.example.deviceinfoviewer.service

import android.content.Context
import android.content.SharedPreferences

/**
 * 悬浮窗配置 — v2: 8种实时指标 + FPS
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

    // ── 位置记忆 ──
    fun getWindowX(key: String, default: Int): Int = prefs?.getInt("pos_${key}_x", default) ?: default
    fun setWindowX(key: String, v: Int) { prefs?.edit()?.putInt("pos_${key}_x", v)?.apply() }

    fun getWindowY(key: String, default: Int): Int = prefs?.getInt("pos_${key}_y", default) ?: default
    fun setWindowY(key: String, v: Int) { prefs?.edit()?.putInt("pos_${key}_y", v)?.apply() }
}
