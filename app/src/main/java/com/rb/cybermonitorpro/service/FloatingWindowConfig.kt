package com.rb.cybermonitorpro.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 悬浮窗配置 — v5: 可配置刷新间隔 + 事件驱动开关
 *
 * 架构变更 (2026-06-21):
 * - ★ 新增 refreshIntervalFlow: 用户可自定义悬浮窗刷新频率 (200ms ~ 30s)
 * - ★ 开关状态改为 StateFlow 事件驱动
 * - 保留位置记忆 (SharedPreferences)
 *
 * ⚠️ init() 必须在 UI 访问前同步调用 (DeviceApplication.onCreate)，否则 setter 中
 *   prefs?.edit() 会在 prefs==null 时静默跳过 SP 持久化，导致进程重启后配置丢失。
 */
object FloatingWindowConfig {
    private const val TAG = "FloatWinCfg"
    private const val PREFS = "floating_window"
    private const val DEFAULT_REFRESH_MS = 500L
    private var prefs: SharedPreferences? = null

    fun init(ctx: Context) {
        if (prefs == null) {
            prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            _enabled.value = prefs!!.getBoolean("enabled", false)
            _visibleMetrics.value = loadVisibleMetrics()
            _refreshInterval.value = prefs!!.getLong("refresh_interval_ms", DEFAULT_REFRESH_MS)
            // ★ F1 (P1-2): 样式读回时钳制范围, 防止越界值(手改 SP/旧版本残留)进入 StateFlow
            _textSizeSp.value = prefs!!.getFloat("text_size_sp", DEFAULT_TEXT_SIZE_SP)
                .coerceIn(TEXT_SIZE_RANGE.start, TEXT_SIZE_RANGE.endInclusive)
            _textColor.value = prefs!!.getInt("text_color", DEFAULT_TEXT_COLOR)
            _windowAlpha.value = prefs!!.getFloat("window_alpha", DEFAULT_WINDOW_ALPHA)
                .coerceIn(ALPHA_RANGE.start, ALPHA_RANGE.endInclusive)
            _bgColor.value = prefs!!.getInt("bg_color", DEFAULT_BG_COLOR)
        }
    }

    // ═══════ F1 样式自定义 — 文字大小/颜色/透明度/背景色 (StateFlow + SP 持久化) ═══════
    // 默认值 = 原 FloatingWindowService 硬编码 (textSize=11f / 0xFFA05CFF / alpha=0.85f / 0xDC0A0A0F)
    const val DEFAULT_TEXT_SIZE_SP = 11f
    const val DEFAULT_TEXT_COLOR = 0xFFA05CFF.toInt()
    const val DEFAULT_WINDOW_ALPHA = 0.85f
    const val DEFAULT_BG_COLOR = 0xDC0A0A0F.toInt()
    val TEXT_SIZE_RANGE = 9f..22f
    val ALPHA_RANGE = 0.2f..1f

    /** 首次打开悬浮窗设置页时写入的默认文字色 = textColorPresets[1] 浅蓝 */
    private const val DEFAULT_TEXT_COLOR_CYAN = 0xFF00D4FF.toInt()

    /** 首次打开时的默认坐标（推断基线: x=16 左侧连续排列, 需对照 final.jpeg 微调） */
    val DEFAULT_POSITIONS = listOf(
        "gpu_usage" to (16 to 160), "cpu_temp" to (16 to 216), "gpu_temp" to (16 to 272),
        "cpu_freq" to (16 to 328),   // 4 行较高, 其后间隙留大
        "ram" to (16 to 440), "battery_temp" to (16 to 496), "battery_cur" to (16 to 552),
        "battery_pow" to (16 to 608), "fps" to (16 to 664)
    )

    private val _textSizeSp = MutableStateFlow(DEFAULT_TEXT_SIZE_SP)
    val textSizeFlow: StateFlow<Float> = _textSizeSp.asStateFlow()

    private val _textColor = MutableStateFlow(DEFAULT_TEXT_COLOR)
    val textColorFlow: StateFlow<Int> = _textColor.asStateFlow()

    private val _windowAlpha = MutableStateFlow(DEFAULT_WINDOW_ALPHA)
    val windowAlphaFlow: StateFlow<Float> = _windowAlpha.asStateFlow()

    private val _bgColor = MutableStateFlow(DEFAULT_BG_COLOR)
    val bgColorFlow: StateFlow<Int> = _bgColor.asStateFlow()

    var textSizeSp: Float
        get() = _textSizeSp.value
        set(v) {
            val coerced = v.coerceIn(TEXT_SIZE_RANGE.start, TEXT_SIZE_RANGE.endInclusive)
            _textSizeSp.value = coerced
            prefs?.edit()?.putFloat("text_size_sp", coerced)?.apply()
        }

    var textColor: Int
        get() = _textColor.value
        set(v) {
            _textColor.value = v   // 任意 ARGB Int 合法, 不钳制
            prefs?.edit()?.putInt("text_color", v)?.apply()
        }

    var windowAlpha: Float
        get() = _windowAlpha.value
        set(v) {
            val coerced = v.coerceIn(ALPHA_RANGE.start, ALPHA_RANGE.endInclusive)
            _windowAlpha.value = coerced
            prefs?.edit()?.putFloat("window_alpha", coerced)?.apply()
        }

    var bgColor: Int
        get() = _bgColor.value
        set(v) {
            _bgColor.value = v
            prefs?.edit()?.putInt("bg_color", v)?.apply()
        }

    // ═══════ 刷新间隔 — StateFlow 事件驱动 ═══════
    private val _refreshInterval = MutableStateFlow(DEFAULT_REFRESH_MS)
    val refreshIntervalFlow: StateFlow<Long> = _refreshInterval.asStateFlow()

    var refreshIntervalMs: Long
        get() = _refreshInterval.value
        set(v) {
            // ★ 持久化修复 (P1-19): 原 setter 仅对 StateFlow 做 coerceIn,
            //   但 SP 写入原始 v, 进程重启后读回未约束的值 (可能 < 500 或 > 5000)
            //   修复: SP 也写入 coerceIn 后的值, 与 StateFlow 保持一致
            val coerced = v.coerceIn(500L, 5000L)
            _refreshInterval.value = coerced
            if (prefs == null) {
                Log.w(TAG, "refreshIntervalMs set before init() — SP write skipped, value in memory only")
            } else {
                prefs!!.edit().putLong("refresh_interval_ms", coerced).apply()
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

    /**
     * 首次打开悬浮窗设置页时的一次性默认写入（非首次安装）。
     * 靠 floatsettings_first_opened 标志只写一次, 老用户已有 visible_metrics 时不会被覆盖。
     * 必须在组合期「先写后读」同步调用（FloatingWindowScreen 的 toggles 用 remember 读初始值）。
     */
    fun ensureFirstOpenedDefaults() {
        val p = prefs ?: return
        if (p.getBoolean("floatsettings_first_opened", false)) return
        // ★ 老用户守卫：任一旧 key 已存在 → 说明是老版本升级用户，
        //   只补 flagsettings_first_opened 标志、不覆盖其已自定义的配置。
        if (p.contains("visible_metrics") || p.contains("text_color") || p.contains("pos_gpu_usage_x")) {
            p.edit().putBoolean("floatsettings_first_opened", true).apply()
            return
        }
        _visibleMetrics.value = ALL_METRICS
        _textColor.value = DEFAULT_TEXT_COLOR_CYAN
        // 单次 edit 链合并写入 SP, 减少 I/O
        val e = p.edit()
            .putStringSet("visible_metrics", ALL_METRICS)
            .putInt("text_color", DEFAULT_TEXT_COLOR_CYAN)
        DEFAULT_POSITIONS.forEach { (k, xy) ->
            e.putInt("pos_${k}_x", xy.first).putInt("pos_${k}_y", xy.second)
        }
        e.putBoolean("floatsettings_first_opened", true).apply()
    }
}
