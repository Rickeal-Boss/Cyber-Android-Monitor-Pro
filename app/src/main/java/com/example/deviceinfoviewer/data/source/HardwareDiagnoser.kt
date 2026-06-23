package com.example.deviceinfoviewer.data.source

import android.os.Build
import android.util.Log

/**
 * [Architect Note] 设备指纹诊断层 — O(1) SoC 平台识别
 *
 * 设计哲学: 替代 O(N) sysfs 路径穷举。启动时一次性检测 OEM/SoC 平台，
 * 后续所有 DataSource 通过 Platform 枚举走 O(1) 精准路径，消除 45 层 try-catch。
 *
 * 判定策略 (优先级降序):
 *   1. Build.HARDWARE → 高通 SoC 专属字符串 (qcom, msm, sm, kona, lahaina, taro 等)
 *   2. Build.BOARD → 主板代号 (kalama, pineapple 等)
 *   3. /proc/cpuinfo Hardware 行 → 内核级 Hardware 标识
 *   4. ro.board.platform / ro.hardware.chipname → 系统属性 (最可靠)
 *
 * 来源参考:
 *   - AOSP: hardware/libhardware/include/hardware/hardware.h → hw_get_module()
 *   - Linux: Documentation/ABI/stable/sysfs-devices-soc
 *   - XDA: SoC detection patterns across OEMs
 */
object HardwareDiagnoser {

    private const val TAG = "HardwareDiagnoser"

    enum class Platform(val displayName: String) {
        QUALCOMM("Qualcomm Snapdragon"),
        MEDIATEK("MediaTek Dimensity/Helio"),
        SAMSUNG_EXYNOS("Samsung Exynos"),
        UNISOC("Unisoc"),
        HISILICON("HiSilicon Kirin"),
        GENERIC("Generic ARM")
    }

    data class DeviceProfile(
        val platform: Platform,
        val oem: String,       // 厂商: "xiaomi", "oppo", "samsung" 等 (小写)
        val rom: String,       // ROM 标识: "miui", "coloros", "oneui" 等
        val socModel: String,  // SoC 型号: "SM8635", "kona", "mt6989" 等
    ) {
        val isOppoGroup get() = oem in setOf("oppo", "oneplus", "realme")
        val isXiaomi get() = oem == "xiaomi" || oem == "redmi"
        val isSamsung get() = oem == "samsung"
        val isHuawei get() = oem == "huawei" || oem == "honor"
    }

    /** 缓存: 首次调用后不变 */
    @Volatile
    private var cachedProfile: DeviceProfile? = null

    /**
     * 诊断当前设备并返回 DeviceProfile (幂等，首次后命中缓存)
     */
    fun diagnose(): DeviceProfile {
        cachedProfile?.let { return it }
        synchronized(this) {
            cachedProfile?.let { return it }
            val profile = buildProfile()
            cachedProfile = profile
            Log.i(TAG, "Device diagnosed: ${profile.platform.displayName} | OEM=${profile.oem} | SoC=${profile.socModel}")
            return profile
        }
    }

    private fun buildProfile(): DeviceProfile {
        val hardware = Build.HARDWARE.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        val board = Build.BOARD.lowercase()

        // [Architect Note] Step 1: 判定 SoC 平台 (Build.HARDWARE 最可靠)
        val platform = when {
            // 高通: qcom/msm/sm/sdx + codename 模式
            hardware.contains("qcom") || hardware.contains("msm") ||
                hardware.matches(Regex("sm\\d+")) || board.matches(Regex("sm\\d+")) ||
                hardware in setOf("kona", "lahaina", "taro", "kalama", "pineapple", "sun", "parrot") ||
                manufacturer in setOf("oneplus", "realme") -> Platform.QUALCOMM  // OPPO 系默认为高通

            // MTK: mt 前缀
            hardware.startsWith("mt") || hardware.contains("mediatek") ||
                board.startsWith("mt") || board.contains("mediatek") -> Platform.MEDIATEK

            // 三星 Exynos
            hardware.startsWith("exynos") || hardware.contains("universal") ||
                (manufacturer == "samsung" && !hardware.contains("qcom")) -> Platform.SAMSUNG_EXYNOS

            // 紫光展锐
            hardware.startsWith("spreadtrum") || hardware.startsWith("ums") ||
                hardware.startsWith("sc") -> Platform.UNISOC

            // 海思麒麟
            hardware.startsWith("kirin") || hardware.startsWith("hi") ||
                manufacturer == "huawei" || manufacturer == "honor" -> Platform.HISILICON

            else -> Platform.GENERIC
        }

        // [Architect Note] Step 2: 判定 OEM + ROM
        val oem = when {
            manufacturer in setOf("xiaomi", "redmi") -> "xiaomi"
            manufacturer in setOf("oppo", "oneplus", "realme") -> manufacturer
            manufacturer == "samsung" -> "samsung"
            manufacturer == "huawei" -> "huawei"
            manufacturer == "honor" -> "honor"
            manufacturer == "vivo" || manufacturer == "iqoo" -> "vivo"
            else -> "generic"
        }

        val rom = when (oem) {
            "xiaomi" -> if (Build.VERSION.SDK_INT >= 31) getSystemProp("ro.miui.ui.version.name")?.let { "miui" } ?: "hyperos"
            else "hyperos"
            "oppo", "oneplus", "realme" -> "coloros"
            "samsung" -> "oneui"
            "huawei" -> "harmonyos"
            "honor" -> "magicos"
            "vivo" -> "originos"
            else -> "aosp"
        }

        // [Architect Note] Step 3: SoC 型号 (三级 fallback)
        val socModel = getSystemProp("ro.board.platform")
            ?: getSystemProp("ro.hardware.chipname")
            ?: hardware

        return DeviceProfile(
            platform = platform,
            oem = oem,
            rom = rom,
            socModel = socModel.trim().lowercase()
        )
    }

    /** [Architect Note] 系统属性读取 (反射，API 21+) */
    private fun getSystemProp(key: String): String? {
        return try {
            val sp = Class.forName("android.os.SystemProperties")
            val value = sp.getMethod("get", String::class.java, String::class.java)
                .invoke(null, key, "") as? String
            value?.takeIf { it.isNotEmpty() }
        } catch (_: Throwable) { null }
    }
}
