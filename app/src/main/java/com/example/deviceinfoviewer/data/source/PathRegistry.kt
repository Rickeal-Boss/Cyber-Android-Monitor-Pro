package com.example.deviceinfoviewer.data.source

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Path registry for O(1) platform-specific sysfs path lookup.
 * Caches detected paths in SharedPreferences to avoid per-startup probing.
 */
object PathRegistry {

    private const val TAG = "PathRegistry"
    private const val PREFS_NAME = "path_registry_cache"

    /**
     * 电池电流路径注册表条目
     */
    data class CurrentPathEntry(
        val primaryPath: String,     // 首选路径 (大概率命中)
        val fallbackPaths: List<String> = emptyList(),  // 降级路径
        val unit: CurrentUnit = CurrentUnit.MICROAMP,   // 已知单位
    )

    enum class CurrentUnit { MICROAMP, MILLIAMP }

    /**
     * 温控路径注册表条目
     */
    data class ThermalPathEntry(
        val zoneBase: String,          // /sys/class/thermal/thermal_zoneX
        val tempFile: String = "temp", // 温度文件
        val typeFile: String = "type", // 类型标识文件
    )

    /**
     * GPU 频率路径注册表条目
     */
    data class GpuFreqPathEntry(
        val primaryPath: String,
        val fallbackPaths: List<String> = emptyList(),
    )

    // ═══════ Current path registry ═══════

    private val currentRegistry: Map<String, CurrentPathEntry> = mapOf(
        // ── 高通标准 BMS (CodeLinaro qpnp-vm-bms.c) ──
        "qualcomm" to CurrentPathEntry(
            primaryPath = "/sys/class/power_supply/battery/current_now",
            fallbackPaths = listOf(
                "/sys/class/power_supply/bms/current_now",
                "/sys/class/power_supply/battery/battery_current",
            ),
            unit = CurrentUnit.MICROAMP
        ),

        // ── 小米 (HyperOS / MIUI, 高通 BMS + 扩展) ──
        // Source: Xiaomi HyperOS 内核, XDA 多设备实测
        "xiaomi" to CurrentPathEntry(
            primaryPath = "/sys/class/power_supply/bms/current_now",
            fallbackPaths = listOf(
                "/sys/class/power_supply/battery/current_now",
                "/sys/class/power_supply/bms/battery_current",
            ),
            unit = CurrentUnit.MICROAMP
        ),

        // ── 三星 (OneUI, Samsung battery driver) ──
        // Source: Samsung OSRC kernel/drivers/battery/sec_battery.c
        "samsung" to CurrentPathEntry(
            primaryPath = "/sys/class/power_supply/battery/current_now",
            fallbackPaths = listOf(
                "/sys/class/power_supply/battery/batt_current_now",
                "/sys/class/power_supply/battery/batt_current_adc",
            ),
            unit = CurrentUnit.MICROAMP
        ),

        // ── 华为/荣耀 (HiSilicon/Kirin) ──
        // Source: 华为内核 drivers/power/supply/
        "huawei" to CurrentPathEntry(
            primaryPath = "/sys/class/power_supply/battery/current_now",
            fallbackPaths = listOf(
                "/sys/class/power_supply/battery/charging_current",
            ),
            unit = CurrentUnit.MICROAMP
        ),
        "honor" to CurrentPathEntry(
            primaryPath = "/sys/class/power_supply/battery/current_now",
            unit = CurrentUnit.MICROAMP
        ),

        // ── vivo/iQOO ──
        "vivo" to CurrentPathEntry(
            primaryPath = "/sys/class/power_supply/battery/current_now",
            fallbackPaths = listOf(
                "/sys/class/power_supply/battery/vivo_current",
            ),
            unit = CurrentUnit.MICROAMP
        ),

        // ── MTK (MediaTek battery driver) ──
        // Source: MediaTek kernel drivers/power/supply/mtk_battery.c
        "mediatek" to CurrentPathEntry(
            primaryPath = "/sys/class/power_supply/battery/current_now",
            fallbackPaths = listOf(
                "/sys/devices/platform/mt-battery/current_now",
                "/sys/devices/platform/battery_meter/current_now",
            ),
            unit = CurrentUnit.MICROAMP
        ),

        // ── 通用兜底 ──
        "generic" to CurrentPathEntry(
            primaryPath = "/sys/class/power_supply/battery/current_now",
            unit = CurrentUnit.MICROAMP
        ),
    )

    // ═══════ GPU 频率路径注册表 ═══════

    private val gpuFreqRegistry: Map<String, GpuFreqPathEntry> = mapOf(
        "qualcomm" to GpuFreqPathEntry(
            primaryPath = "/sys/class/kgsl/kgsl-3d0/gpuclk",
            fallbackPaths = listOf(
                "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq",
                "/sys/class/devfreq/*/cur_freq",  // 通配符，需运行时展开
            )
        ),
        "mediatek" to GpuFreqPathEntry(
            primaryPath = "/sys/class/devfreq/*/cur_freq",  // MTK 使用 devfreq
        ),
        "samsung_exynos" to GpuFreqPathEntry(
            primaryPath = "/sys/class/devfreq/*/cur_freq",
        ),
        "generic" to GpuFreqPathEntry(
            primaryPath = "/sys/class/kgsl/kgsl-3d0/gpuclk",
            fallbackPaths = listOf(
                "/sys/class/devfreq/*/cur_freq",
                "/sys/kernel/gpu/gpu_clock",
            )
        ),
    )

    // ═══════ 查询 API ═══════

    /**
     * 查询电流路径 (3 次尝试降级)
     */
    fun queryCurrentPath(profile: HardwareDiagnoser.DeviceProfile): CurrentPathEntry {
        // 策略 1: 精确 OEM 匹配
        currentRegistry[profile.oem]?.let { return it }

        // 策略 2: 平台匹配 (OPPO 系 → oppo key)
        if (profile.isOppoGroup) {
            currentRegistry["oppo"]?.let { return it }
        }

        // 策略 3: SoC 平台兜底
        currentRegistry[profile.platform.name.lowercase()]?.let { return it }

        // 策略 4: 通用兜底
        return currentRegistry["generic"]!!
    }

    /**
     * 查询 GPU 频率路径
     */
    fun queryGpuFreqPath(profile: HardwareDiagnoser.DeviceProfile): GpuFreqPathEntry {
        gpuFreqRegistry[profile.platform.name.lowercase()]?.let { return it }
        return gpuFreqRegistry["generic"]!!
    }

    // ═══════ SP 缓存 (运行时学习) ═══════

    /**
     * 将探测成功的路径写入缓存 (运行时学习)
     * 下次启动直接从缓存读取，零探测开销
     */
    fun cacheSuccessfulPath(context: Context, key: String, path: String) {
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(key, path).apply()
        } catch (_: Throwable) {}
    }

    /**
     * 读取缓存的成功路径
     */
    fun getCachedPath(context: Context, key: String): String? {
        return try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(key, null)
        } catch (_: Throwable) { null }
    }
}
