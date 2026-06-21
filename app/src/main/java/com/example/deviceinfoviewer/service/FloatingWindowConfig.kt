package com.example.deviceinfoviewer.service

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 悬浮窗配置 — v4: 统一刷新 + 事件驱动开关
 *
 * 架构变更 (2026-06-21):
 * - ★ 移除每指标独立刷新间隔 (cpuTempRefreshMs 等 9 个字段)
 *   → 全部统一由 RefreshPolicy 管控 (前台 HIGH=500ms, 后台 ECONOMY=5000ms)
 * - ★ 开关状态改为 StateFlow 事件驱动
 *   → enabled 和 visibleMetrics 变更时立即通知观察者，不再依赖轮询周期
 * - 保留位置记忆 (SharedPreferences)
 */
object FloatingWindowConfig {
    private const val PREFS = "floating_window"
    private var prefs: SharedPreferences? = null

    fun init(ctx: Context) {
        if (prefs == null) {
            prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            // 从 SP 恢复初始状态到 Flow
            _enabled.value = prefs!!.getBoolean("enabled", false)
            _visibleMetrics.value = loadVisibleMetrics()
        }
    }

    // ═══════ 启用/禁用 — StateFlow 事件驱动 ═══════
    private val _enabled = MutableStateFlow(false)
    val enabledFlow: StateFlow<Boolean> = _enabled.asStateFlow()

    var enabled: Boolean
        get() = _enabled.value
        set(v) {
            _enabled.value = v
            prefs?.edit()?.putBoolean("enabled", v)?.apply()
        }

    // ═══════ 可见指标 — StateFlow 事件驱动 ═══════
    private val _visibleMetrics = MutableStateFlow<Set<String>>(emptySet())
    val visibleMetricsFlow: StateFlow<Set<String>> = _visibleMetrics.asStateFlow()

    /** 所有支持的指标键 */
    val ALL_METRICS = setOf(
        "gpu_usage", "cpu_temp", "gpu_temp", "cpu_freq",
        "ram", "battery_temp", "battery_cur", "battery_pow", "fps"
    )

    /** 默认可见的指标 */
    private val DEFAULT_VISIBLE = setOf("gpu_usage", "cpu_temp", "ram")

    fun isVisible(key: String): Boolean = _visibleMetrics.value.contains(key)

    fun setVisible(key: String, visible: Boolean) {
        val current = _visibleMetrics.value.toMutableSet()
        if (visible) current.add(key) else current.remove(key)
        _visibleMetrics.value = current
        // 持久化
        prefs?.edit()?.putStringSet("visible_metrics", current)?.apply()
    }

    private fun loadVisibleMetrics(): Set<String> {
        val saved = prefs?.getStringSet("visible_metrics", null)
        return if (saved != null && saved.isNotEmpty()) saved else DEFAULT_VISIBLE
    }

    // ═══════ 位置记忆 ═══════
    fun getWindowX(key: String, default: Int): Int = prefs?.getInt("pos_${key}_x", default) ?: default
    fun setWindowX(key: String, v: Int) { prefs?.edit()?.putInt("pos_${key}_x", v)?.apply() }

    fun getWindowY(key: String, default: Int): Int = prefs?.getInt("pos_${key}_y", default) ?: default
    fun setWindowY(key: String, v: Int) { prefs?.edit()?.putInt("pos_${key}_y", v)?.apply() }
}
