package com.example.deviceinfoviewer.data.source

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * [Architect Note] 路径注册表 — O(1) 精准路径匹配
 *
 * 设计哲学: 替代 O(N) sysfs 路径穷举。首次启动探测设备指纹后，
 * 从注册表查询匹配路径，写入 SharedPreferences 缓存。
 * 后续启动直接读缓存 → 零探测开销。
 *
 * 查询策略 (3 次尝试降级):
 *   1. 精确匹配: (OEM, ROM, SoC) 三元组
 *   2. 降级匹配: (OEM, SoC) → 忽略 ROM 版本差异
 *   3. 通用兜底: platform 默认路径
 *
 * 来源参考:
 *   - 高通 PMIC: CodeLinaro kernel/msm-5.x/drivers/power/supply/qcom/
 *   - OPPO oplus_chg: OPPO 内核 drivers/power/supply/oplus/
 *   - MTK: MediaTek kernel drivers/power/supply/mtk_battery.c
 *   - 三星: Samsung OSRC kernel/drivers/battery/
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

    // ═══════ 电流路径注册表 ═══════
    // [Architect Note] 每个条目精确标注 OEM + ROM 范围 + 路径来源

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

        // ── OPPO/OnePlus/Realme (ColorOS 12/13/14, oplus_chg 驱动) ──
        // Source: OPPO 内核 drivers/power/supply/oplus/oplus_chg.c
        "oppo" to CurrentPathEntry(
            primaryPath = "/sys/class/oplus_chg/battery/current_now",
            fallbackPaths = listOf(
                "/sys/class/oplus_chg/battery/real_icharging",
                "/sys/class/oplus_chg/battery/charging_current",
                "/sys/class/power_supply/battery/current_now",
            ),
            unit = CurrentUnit.MILLIAMP
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
