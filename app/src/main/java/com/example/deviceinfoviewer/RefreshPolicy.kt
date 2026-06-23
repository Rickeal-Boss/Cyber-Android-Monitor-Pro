package com.example.deviceinfoviewer

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 统一刷新策略管理器 — 全局刷新频率的唯一真相源
 *
 * ## 设计目标
 * 1. **消除分散的硬编码间隔**: 每个模块不再自行决定刷新频率
 * 2. **前后台自动切换**: 所有数据源/悬浮窗在后台统一降级
 * 3. **平滑过渡**: StateFlow 驱动，避免突变
 * 4. **模块级可配置**: 各模块可指定自己的默认 Tier，后台时统一覆盖
 *
 * ## 刷新层级 (RefreshTier)
 * ```
 * HIGH    = 500ms    → 悬浮窗实时指标、高频采样
 * NORMAL  = 2000ms   → 主监控默认、数据采集
 * ECONOMY = 5000ms   → 后台全局降级
 * SLOW    = 10000ms  → 非关键指标 (如 GPS 状态)
 * IDLE    = 30000ms  → 极低功耗 (如开机时长更新)
 * ```
 *
 * ## 前后台切换
 * ```
 * App 前台 → RefreshState.FOREGROUND → 各模块使用各自 Tier
 * App 后台 → RefreshState.BACKGROUND → 所有模块强制 Tier.ECONOMY (5s)
 * ```
 *
 * ## 使用方式
 * ```kotlin
 * // 观察状态
 * RefreshPolicy.state.collect { state -> ... }
 *
 * // 获取有效间隔
 * val ms = RefreshPolicy.effectiveMs(RefreshPolicy.Tier.NORMAL)  // 前台 2s, 后台 5s
 *
 * // 设置前后台
 * RefreshPolicy.updateState(RefreshState.BACKGROUND)
 * ```
 */
object RefreshPolicy {

    private const val TAG = "RefreshPolicy"

    // ═══════ Tier 定义 ═══════
    enum class Tier(val defaultMs: Long) {
        /** 高频: 悬浮窗实时、传感器高速采样 */
        HIGH(500L),
        /** 标准: 主监控数据采集默认频率 */
        NORMAL(2000L),
        /** 节电: 后台全局降级统一频率 */
        ECONOMY(5000L),
        /** 低速: 非关键指标、GPS 状态巡检 */
        SLOW(10000L),
        /** 空闲: 极低频更新 */
        IDLE(30000L)
    }

    // ═══════ 全局状态 ═══════
    enum class RefreshState {
        FOREGROUND,
        BACKGROUND
    }

    private val _state = MutableStateFlow(RefreshState.FOREGROUND)
    val state: StateFlow<RefreshState> = _state.asStateFlow()

    /** ★ 系统省电模式 (PowerManager.isPowerSaveMode, API 21+)
     *  开启时 effectiveMs() 强制封顶 5s，无论前台/后台 */
    private val _powerSaveMode = MutableStateFlow(false)
    val powerSaveModeFlow: StateFlow<Boolean> = _powerSaveMode.asStateFlow()
    var isPowerSaveMode: Boolean
        get() = _powerSaveMode.value
        set(v) { _powerSaveMode.value = v }

    // ═══════ 后台全局最小间隔 (所有模块在后台时强制不低于此值) ═══════
    const val BACKGROUND_CAP_MS = 5000L

    // ═══════ 状态更新 ═══════
    fun updateState(newState: RefreshState) {
        if (_state.value == newState) return
        Log.d(TAG, "RefreshState: ${_state.value} → $newState")
        _state.value = newState
    }

    val isForeground: Boolean get() = _state.value == RefreshState.FOREGROUND
    val isBackground: Boolean get() = _state.value == RefreshState.BACKGROUND

    // ═══════ 有效间隔计算 ═══════
    /**
     * 根据当前前台/后台状态计算某模块的实际应刷新间隔
     *
     * @param tier 模块的默认 Tier
     * @return 前台时返回 tier.defaultMs，后台时返回 max(tier.defaultMs, BACKGROUND_CAP_MS)
     */
    fun effectiveMs(tier: Tier): Long {
        // ★ 省电模式: 强制封顶 5s，系统全局降频，应用必须配合
        if (isPowerSaveMode) return maxOf(tier.defaultMs, BACKGROUND_CAP_MS)
        return when (_state.value) {
            RefreshState.FOREGROUND -> tier.defaultMs
            RefreshState.BACKGROUND -> {
                maxOf(tier.defaultMs, BACKGROUND_CAP_MS)
            }
        }
    }

    /**
     * 根据自定间隔 + 当前状态计算有效间隔
     * 用于模块有独立配置间隔时 (如 SettingsScreen 的模块 Slider)
     *
     * @param customMs 用户自定义的间隔值 (ms)，0 表示使用 tier 默认值
     * @param fallbackTier 自定义值为 0 时的回退 Tier
     */
    fun effectiveMs(customMs: Long, fallbackTier: Tier): Long {
        val base = if (customMs > 0) customMs else fallbackTier.defaultMs
        // ★ 省电模式: 强制封顶 5s
        if (isPowerSaveMode) return maxOf(base, BACKGROUND_CAP_MS)
        return when (_state.value) {
            RefreshState.FOREGROUND -> base
            RefreshState.BACKGROUND -> maxOf(base, BACKGROUND_CAP_MS)
        }
    }

    // ═══════ 诊断 ═══════
    fun currentStateName(): String = _state.value.name
    fun describe(): String = "RefreshPolicy[state=${_state.value.name}]"
}
