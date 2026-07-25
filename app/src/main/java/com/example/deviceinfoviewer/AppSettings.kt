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

    fun effectiveRefreshMs(moduleMs: Int): Int =
        if (moduleMs > 0) moduleMs else refreshIntervalMs

    var darkMode: Boolean
        get() = prefs.getBoolean("dark_mode", DEFAULT_DARK_MODE)
        set(value) = prefs.edit { putBoolean("dark_mode", value) }

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

    // ── 电池电流校准倍率 (PlusPlusBattery 思路: 用户校准则准) ──
    // 默认 1.0 = 不修正。ColorOS 等 OEM ROM 的 oplus_chg sysfs / BATTERY_PROPERTY_CURRENT_NOW
    // 读数常因单位或增益偏差而偏大/偏小，由用户在设置中校准 (如读数偏大 2× 则填 0.5)。
    // 钳制 [0.1, 10.0] 防止误配导致瓦特/内阻等派生指标失真。
    var batteryCurrentMultiplier: Double
        get() = prefs.getFloat("battery_current_multiplier", 1.0f).toDouble().coerceIn(0.1, 10.0)
        set(value) = prefs.edit {
            putFloat("battery_current_multiplier", value.toFloat().coerceIn(0.1f, 10.0f))
        }

    // ── 概览页卡片排序 (逗号分隔的卡片 ID) ──
    // 指标卡: cpu_temp, mem_usage, battery_level, gpu_load
    // 快速访问: cpu, gpu, mem, net, gps, device, battery, sensor
    var metricCardOrder: String
        get() = prefs.getString("metric_card_order", "cpu_temp,mem_usage,battery_level,gpu_load")
            ?: "cpu_temp,mem_usage,battery_level,gpu_load"
        set(value) = prefs.edit { putString("metric_card_order", value) }

    var quickCardOrder: String
        get() = prefs.getString("quick_card_order", "cpu,gpu,mem,net,gps,device,battery,sensor")
            ?: "cpu,gpu,mem,net,gps,device,battery,sensor"
        set(value) = prefs.edit { putString("quick_card_order", value) }

    // 概览页卡片拖拽重排总开关 (默认开启；关闭则回落静态网格，零代码回退)
    // 电池页复用同一开关，保证「与概览页一致」的交互行为
    var dashboardReorderEnabled: Boolean
        get() = prefs.getBoolean("dashboard_reorder_enabled", true)
        set(value) = prefs.edit { putBoolean("dashboard_reorder_enabled", value) }

    // ── 电池页卡片排序 (逗号分隔的卡片 ID) ──
    // 卡片集见 BatteryScreen.BATTERY_CARD_IDS；状态概览卡为固定头部不参与重排。
    // 空字符串 → resolveCardOrder 回落默认序 (与原布局一致)。
    var batteryCardOrder: String
        get() = prefs.getString("battery_card_order", "") ?: ""
        set(value) = prefs.edit { putString("battery_card_order", value) }

    // ── 应用语言偏好（i18n）──
    // 值为 LocaleManager.LANG_SYSTEM（"system"）或 BCP 47 语言 code（如 "zh-CN"、"en"、"ja"）
    // 默认 "system" = 跟随系统语言；用户手动选择后持久化，下次启动优先读取用户偏好
    var appLanguage: String
        get() = prefs.getString("app_language", "system") ?: "system"
        set(value) = prefs.edit { putString("app_language", value) }
}
