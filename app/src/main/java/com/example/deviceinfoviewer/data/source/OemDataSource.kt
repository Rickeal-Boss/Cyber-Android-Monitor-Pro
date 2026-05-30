package com.example.deviceinfoviewer.data.source

import android.os.Build
import android.util.Log
import com.example.deviceinfoviewer.data.model.OemInfo
import java.io.File
import java.lang.reflect.Method

/**
 * OEM 信息数据源 — 全面读取国产 ROM 定制信息
 *
 * 覆盖: Xiaomi HyperOS/MIUI / OPPO ColorOS / Vivo OriginOS
 * 无需 root: 全部通过 SystemProperties.get() 和 sysfs 读取
 */
class OemDataSource {

    companion object {
        private const val TAG = "OemDataSource"

        private const val OEM_XIAOMI = "Xiaomi"
        private const val OEM_OPPO = "OPPO"
        private const val OEM_VIVO = "Vivo"
        private const val OEM_AOSP = "AOSP"

        // SystemProperties class for reflection
        private var spClass: Class<*>? = null
        private var spGet: Method? = null

        init {
            try {
                spClass = Class.forName("android.os.SystemProperties")
                spGet = spClass?.getMethod("get", String::class.java, String::class.java)
            } catch (_: Throwable) {}
        }
    }

    fun collect(): OemInfo {
        val oem = detectOem()
        val info = OemInfo(oem = oem)

        // 通用
        info.buildDisplayId = prop("ro.build.display.id")
        info.securityPatch = prop("ro.build.version.security_patch")
        info.socManufacturer = prop("ro.soc.manufacturer")
        info.socModel = prop("ro.soc.model")
        info.boardPlatform = prop("ro.board.platform")
            .ifEmpty { prop("ro.hardware.chipname") }
            .ifEmpty { prop("ro.chipname") }

        // 厂商专用
        when (oem) {
            OEM_XIAOMI -> collectXiaomi(info)
            OEM_OPPO -> collectOppo(info)
            OEM_VIVO -> collectVivo(info)
        }

        // 游戏/性能模式（全网方案）
        detectPerformanceModes(info, oem)

        // 原始属性
        info.rawProperties = collectRawProperties(oem)

        return info
    }

    // ── OEM 检测 ──
    private fun detectOem(): String {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        val fingerprint = Build.FINGERPRINT.lowercase()

        return when {
            "xiaomi" in manufacturer || "xiaomi" in brand || "redmi" in fingerprint || "poco" in fingerprint
                -> OEM_XIAOMI
            "oppo" in manufacturer || "oppo" in brand || "oneplus" in fingerprint || "realme" in fingerprint
                -> OEM_OPPO
            "vivo" in manufacturer || "vivo" in brand || "iqoo" in fingerprint
                -> OEM_VIVO
            else -> OEM_AOSP
        }
    }

    // ── 小米 HyperOS / MIUI ──
    private fun collectXiaomi(info: OemInfo) {
        info.osName = "HyperOS"
        val version = prop("ro.miui.ui.version.name")
        if (version.isNotEmpty()) {
            info.osVersion = version
        } else {
            info.osVersion = prop("ro.build.version.incremental")
            info.osName = "MIUI"
        }
        info.miuiVersion = info.osVersion
        info.miuiRegion = prop("ro.miui.region")
        info.miuiHardware = prop("ro.product.mod_device")
        info.miuiFeatures = buildString {
            if (prop("ro.miui.has_real_blur", "false") == "true") append("RealBlur ")
            if (prop("ro.miui.has_handy_mode_sf", "false") == "true") append("单手模式 ")
            if (prop("ro.miui.support_security_cta", "false") == "true") append("安全中心 ")
            if (prop("ro.miui.notch", "0") == "1") append("刘海屏 ")
        }
    }

    // ── OPPO ColorOS ──
    private fun collectOppo(info: OemInfo) {
        info.osName = "ColorOS"
        info.osVersion = prop("ro.build.version.opporom")
            .ifEmpty { prop("ro.oplus.display.oplusrom") }
        info.oppoVersion = info.osVersion
        info.oppoScreenRatio = prop("ro.oplus.display.screen.ratio")
        info.oplusCharging = buildString {
            val fcc = readSysfs("/sys/class/oplus_chg/battery/battery_fcc")
            if (fcc != null) append("FCC=${fcc.trim()} ")
            val rm = readSysfs("/sys/class/oplus_chg/battery/battery_rm")
            if (rm != null) append("RM=${rm.trim()}")
        }
    }

    // ── Vivo OriginOS ──
    private fun collectVivo(info: OemInfo) {
        info.osName = "OriginOS"
        info.osVersion = prop("ro.vivo.os.version")
            .ifEmpty { prop("ro.vivo.os.build.display.id") }
        info.vivoOsVersion = info.osVersion
        info.vivoProductSolution = prop("ro.vivo.product.solution")
        info.vivoModel = prop("ro.vivo.product.model")
    }

    // ── 性能模式检测（全网方案） ──
    private fun detectPerformanceModes(info: OemInfo, oem: String) {
        // 游戏模式检测
        info.gameModeSupported = when (oem) {
            OEM_XIAOMI -> {
                // 小米游戏模式：GameTurbo / 游戏加速
                prop("persist.sys.game_mode", "0") == "1"
                    || prop("persist.vendor.game_mode", "0") == "1"
                    || prop("ro.vendor.perf.scroll_opt", "0") == "1"
                    || prop("persist.sys.miui_game_mode", "0") == "1"
                    || prop("sys.game_mode", "0") == "1"
            }
            OEM_OPPO -> {
                // OPPO 游戏空间 / 游戏助手
                prop("persist.sys.oplus_gamemode", "0") == "1"
                    || prop("persist.vendor.oplus_gamemode", "0") == "1"
                    || prop("sys.oplus.gamemode", "0") == "1"
                    || prop("persist.sys.game_mode", "0") == "1"
            }
            OEM_VIVO -> {
                // Vivo 游戏魔盒
                prop("persist.vivo.game_mode_supported", "0") == "1"
                    || prop("persist.sys.game_mode", "0") == "1"
                    || prop("sys.game_mode", "0") == "1"
            }
            else -> {
                prop("persist.sys.game_mode", "0") == "1"
                    || prop("ro.vendor.perf.scroll_opt", "0") == "1"
            }
        }

        // 高性能模式检测
        info.highPerformanceMode = when (oem) {
            OEM_XIAOMI -> {
                // 小米性能模式：persist.sys.power_mode (0=均衡, 1=性能, 2=省电)
                val powerMode = prop("persist.sys.power_mode", "0")
                val vendorPowerMode = prop("persist.vendor.power_mode", "0")
                powerMode == "1" || vendorPowerMode == "1"
                    || prop("sys.power_mode", "0") == "1"
                    || prop("persist.sys.performance_mode", "0") == "1"
                    || prop("sys.perf_mode", "0") == "1"
                    || prop("vendor.perf_mode", "0") == "1"
            }
            OEM_OPPO -> {
                // OPPO 高性能模式
                prop("sys.oplus.performance_mode", "0") == "1"
                    || prop("persist.sys.performance_mode", "0") == "1"
                    || prop("sys.perf_mode", "0") == "1"
                    || prop("persist.vendor.performance_mode", "0") == "1"
                    || prop("persist.sys.power_mode", "0") == "1"
            }
            OEM_VIVO -> {
                // Vivo 性能模式 (Monster模式)
                prop("persist.sys.power_mode", "0") == "1"
                    || prop("persist.vivo.power_mode", "0") == "1"
                    || prop("sys.power_mode", "0") == "1"
                    || prop("persist.sys.performance_mode", "0") == "1"
                    || prop("sys.perf_mode", "0") == "1"
            }
            else -> {
                // AOSP/通用
                prop("sys.perf_mode", "0") == "1"
                    || prop("persist.sys.performance_mode", "0") == "1"
                    || prop("persist.sys.power_mode", "0") == "1"
                    || prop("vendor.perf_mode", "0") == "1"
            }
        }
    }

    // ── 厂商原始属性（完整导出 30+ 条） ──
    private fun collectRawProperties(oem: String): List<Pair<String, String>> {
        val keys = when (oem) {
            OEM_XIAOMI -> listOf(
                "ro.miui.ui.version.name", "ro.miui.ui.version.code",
                "ro.miui.region", "ro.miui.cust_variant",
                "ro.product.mod_device", "ro.product.manufacturer",
                "ro.build.hidden_ver", "persist.sys.miui_optimistic",
                "ro.miui.has_real_blur", "ro.miui.has_handy_mode_sf",
                "ro.miui.notch", "ro.miui.support_security_cta",
                "persist.sys.timezone", "ro.product.locale.region",
                "ro.build.version.incremental", "persist.sys.power_mode",
                "persist.sys.game_mode"
            )
            OEM_OPPO -> listOf(
                "ro.build.version.opporom", "ro.oplus.display.oplusrom",
                "ro.oplus.image.my_engineering.version", "ro.oplus.image.my_product.version",
                "ro.oplus.display.screen.ratio", "ro.oplus.audio.soundeffect.type",
                "ro.oplus.camera.types", "persist.oplus.radio.multisim.config",
                "ro.build.version.ota", "ro.oplus.anr.layout",
                "persist.sys.oplus_region", "persist.sys.oplus_gamemode",
                "sys.oplus.performance_mode", "persist.sys.performance_mode"
            )
            OEM_VIVO -> listOf(
                "ro.vivo.os.version", "ro.vivo.os.build.display.id",
                "ro.vivo.product.solution", "ro.vivo.product.model",
                "ro.vivo.product.release.name", "ro.vivo.market.name",
                "ro.vivo.oem.sku", "persist.vivo.game_mode_supported",
                "ro.vivo.hardware.version", "persist.sys.power_mode",
                "persist.vivo.power_mode"
            )
            else -> listOf(
                "ro.build.display.id", "ro.build.version.security_patch",
                "ro.board.platform", "ro.soc.manufacturer", "ro.soc.model",
                "ro.chipname", "ro.hardware.chipname", "ro.build.description",
                "persist.sys.power_mode", "sys.perf_mode"
            )
        }
        return keys.mapNotNull { k -> prop(k).takeIf { it.isNotEmpty() }?.let { k to it } }
    }

    // ── SystemProperties 反射 ──
    private fun prop(key: String, default: String = ""): String {
        return try {
            spGet?.invoke(null, key, default) as? String ?: default
        } catch (_: Throwable) {
            try {
                // Android 11+ 可能限制 SystemProperties 访问
                val m = Class.forName("android.os.SystemProperties")
                    .getMethod("get", String::class.java, String::class.java)
                m.invoke(null, key, default) as? String ?: default
            } catch (_: Throwable) { default }
        }
    }

    private fun readSysfs(path: String): String? {
        return try { File(path).readText() } catch (_: Throwable) { null }
    }
}
