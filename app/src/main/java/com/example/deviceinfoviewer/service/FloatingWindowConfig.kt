package com.example.deviceinfoviewer.service

import android.content.Context
import android.content.SharedPreferences

/**
 * 悬浮窗配置 — 用户可选择显示哪些信息
 */
object FloatingWindowConfig {
    private const val PREFS = "floating_window"
    private var prefs: SharedPreferences? = null

    fun init(ctx: Context) {
        if (prefs == null) prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    var showCpu: Boolean
        get() = prefs?.getBoolean("show_cpu", true) ?: true
        set(v) { prefs?.edit()?.putBoolean("show_cpu", v)?.apply() }

    var showGpu: Boolean
        get() = prefs?.getBoolean("show_gpu", true) ?: true
        set(v) { prefs?.edit()?.putBoolean("show_gpu", v)?.apply() }

    var showBattery: Boolean
        get() = prefs?.getBoolean("show_battery", true) ?: true
        set(v) { prefs?.edit()?.putBoolean("show_battery", v)?.apply() }

    var showMemory: Boolean
        get() = prefs?.getBoolean("show_memory", true) ?: true
        set(v) { prefs?.edit()?.putBoolean("show_memory", v)?.apply() }

    var showNetwork: Boolean
        get() = prefs?.getBoolean("show_network", false) ?: false
        set(v) { prefs?.edit()?.putBoolean("show_network", v)?.apply() }

    var showTemp: Boolean
        get() = prefs?.getBoolean("show_temp", true) ?: true
        set(v) { prefs?.edit()?.putBoolean("show_temp", v)?.apply() }

    var showRefreshRate: Boolean
        get() = prefs?.getBoolean("show_refresh", false) ?: false
        set(v) { prefs?.edit()?.putBoolean("show_refresh", v)?.apply() }

    var showFps: Boolean
        get() = prefs?.getBoolean("show_fps", false) ?: false
        set(v) { prefs?.edit()?.putBoolean("show_fps", v)?.apply() }

    var enabled: Boolean
        get() = prefs?.getBoolean("enabled", false) ?: false
        set(v) { prefs?.edit()?.putBoolean("enabled", v)?.apply() }
}
