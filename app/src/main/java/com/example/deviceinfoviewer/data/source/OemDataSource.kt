package com.example.deviceinfoviewer.data.source

import android.os.Build
import android.util.Log
import com.example.deviceinfoviewer.data.model.OemInfo
import java.io.File
import java.lang.reflect.Method

/**
 * OEM 信息数据源 v2 — Android 16 三大 ROM 深度识别
 *
 * 覆盖: Xiaomi HyperOS 2.0/3.0 · OPPO ColorOS 15/16 · Vivo OriginOS 5/6
 * 新增: AI引擎 · 内存融合 · 散热方案 · 存储加速 · 显示特性 · OTA信息
 */
class OemDataSource {

    companion object {
        private const val TAG = "OemDataSource"
        private const val OEM_XIAOMI = "Xiaomi"
        private const val OEM_OPPO = "OPPO"
        private const val OEM_VIVO = "Vivo"
        private const val OEM_AOSP = "AOSP"

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

        // Android 版本
        info.androidVersion = Build.VERSION.RELEASE
        info.sdkLevel = Build.VERSION.SDK_INT

        // 通用属性
        info.buildDisplayId = prop("ro.build.display.id")
            .ifEmpty { Build.DISPLAY }
        info.securityPatch = prop("ro.build.version.security_patch")
        info.socManufacturer = prop("ro.soc.manufacturer")
        info.socModel = prop("ro.soc.model")
            .ifEmpty { prop("ro.soc.name") }
        info.boardPlatform = prop("ro.board.platform")
            .ifEmpty { prop("ro.hardware.chipname") }
            .ifEmpty { prop("ro.chipname") }

        // 厂商专用
        when (oem) {
            OEM_XIAOMI -> collectXiaomi(info)
            OEM_OPPO -> collectOppo(info)
            OEM_VIVO -> collectVivo(info)
        }

        // 子系统特性 (Android 16)
        detectAiEngine(info)
        detectMemoryFusion(info)
        detectThermalSolution(info)
        detectStorageBoost(info)
        detectDisplayFeatures(info)

        // 游戏/性能模式
        detectPerformanceModes(info, oem)

        // 原始属性
        info.rawProperties = collectRawProperties(oem)

        return info
    }

    // ═══════════════ OEM 检测 ═══════════════

    private fun detectOem(): String {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        val fingerprint = Build.FINGERPRINT.lowercase()
        val product = Build.PRODUCT.lowercase()

        return when {
            "xiaomi" in manufacturer || "xiaomi" in brand
                || "redmi" in fingerprint || "poco" in fingerprint
                || "redmi" in product || "poco" in product -> OEM_XIAOMI
            "oppo" in manufacturer || "oppo" in brand
                || "oneplus" in fingerprint || "realme" in fingerprint
                || "oneplus" in product || "realme" in product -> OEM_OPPO
            "vivo" in manufacturer || "vivo" in brand
                || "iqoo" in fingerprint || "iqoo" in product -> OEM_VIVO
            else -> OEM_AOSP
        }
    }

    // ═══════════════ 小米 HyperOS 2.0/3.0 ═══════════════

    private fun collectXiaomi(info: OemInfo) {
        // → HyperOS 版本
        val hyperVersion = prop("ro.miui.ui.version.name")
        val hyperMajor = prop("ro.miui.ui.version.code")
        val incremental = prop("ro.build.version.incremental")

        if (hyperVersion.isNotEmpty()) {
            info.osName = "HyperOS"
            info.osVersion = "$hyperVersion ($hyperMajor)"
        } else if (incremental.contains("OS2")) {
            info.osName = "HyperOS 2.0"
            info.osVersion = incremental
        } else {
            info.osName = "MIUI"
            info.osVersion = incremental
        }

        info.miuiVersion = info.osVersion
        info.miuiRegion = prop("ro.miui.region")
            .ifEmpty { prop("ro.product.locale.region") }
        info.miuiHardware = prop("ro.product.mod_device")
            .ifEmpty { prop("ro.product.board") }

        // → HyperOS 特性检测
        val features = mutableListOf<String>()

        // 系统特性
        if (prop("ro.miui.has_real_blur", "0") == "1") features.add("RealBlur动态模糊")
        if (prop("ro.miui.has_handy_mode_sf", "0") == "1") features.add("单手模式")
        if (prop("ro.miui.notch", "0") == "1") features.add("刘海屏")
        if (prop("ro.miui.support_security_cta", "0") == "1") features.add("安全中心")

        // HyperOS 2.0/3.0 特性
        val hyperMind = prop("persist.sys.hypermind.enable", "0")
        if (hyperMind == "1") features.add("HyperMind")

        val advancedTextures = prop("ro.vendor.hyperos.advanced_textures", "0")
        if (advancedTextures == "1") features.add("高级材质渲染")

        val aiAssistant = prop("ro.miui.ai_assistant", "0")
        if (aiAssistant == "1") features.add("小爱AI")

        // 互联特性
        val hyperConnect = prop("ro.miui.hyperconnect.available", "0")
        if (hyperConnect == "1") features.add("HyperConnect互联")

        // OTA 信息
        val otaVersion = prop("ro.build.version.ota")
        if (otaVersion.isNotEmpty()) features.add("OTA:$otaVersion")

        info.miuiFeatures = features.joinToString(" · ")

        // → HyperOS 版本推断
        val majorVer = try { hyperMajor.toIntOrNull() ?: 0 } catch (_: Throwable) { 0 }
        if (majorVer >= 816 || incremental.contains("OS3")) {
            info.osName = "HyperOS 3.0"
        } else if (majorVer >= 816 || incremental.contains("OS2")) {
            info.osName = "HyperOS 2.0"
        }
    }

    // ═══════════════ OPPO ColorOS 15/16 ═══════════════

    private fun collectOppo(info: OemInfo) {
        val oppoVer = prop("ro.build.version.opporom")
        val oplusVer = prop("ro.oplus.display.oplusrom")
        val otaVer = prop("ro.build.version.ota")

        info.osName = "ColorOS"
        info.osVersion = oppoVer.ifEmpty { oplusVer }
        info.oppoVersion = info.osVersion
        info.oppoScreenRatio = prop("ro.oplus.display.screen.ratio")

        // → OPPO 充电信息
        val chargingInfo = mutableListOf<String>()
        val fcc = readSysfs("/sys/class/oplus_chg/battery/battery_fcc")
        if (fcc != null) chargingInfo.add("FCC=${fcc.trim()}mA")
        val rm = readSysfs("/sys/class/oplus_chg/battery/battery_rm")
        if (rm != null) chargingInfo.add("RM=${rm.trim()}mAh")

        // 充电协议检测
        val chargeProtocol = prop("ro.oplus.chg.protocol", "")
        if (chargeProtocol.isNotEmpty()) chargingInfo.add("协议=$chargeProtocol")

        // SUPERVOOC 版本
        val superVooc = prop("ro.oplus.chg.vooc_version", "")
        if (superVooc.isNotEmpty()) chargingInfo.add("SUPERVOOC $superVooc")

        info.oplusCharging = chargingInfo.joinToString(" · ")

        // → ColorOS 15/16 版本推断
        if (oppoVer.startsWith("V15") || otaVer.contains("COLOROS15")) {
            info.osName = "ColorOS 15"
        } else if (oppoVer.startsWith("V16") || otaVer.contains("COLOROS16")) {
            info.osName = "ColorOS 16"
        }

        // → OnePlus 识别
        val isOnePlus = prop("ro.product.manufacturer", "").contains("OnePlus")
        if (isOnePlus) {
            info.osName = info.osName.replace("ColorOS", "OxygenOS/ColorOS")
        }
    }

    // ═══════════════ Vivo OriginOS 5/6 ═══════════════

    private fun collectVivo(info: OemInfo) {
        val originVer = prop("ro.vivo.os.version")
        val buildVer = prop("ro.vivo.os.build.display.id")
        val otaVer = prop("ro.build.version.ota")

        info.osName = "OriginOS"
        info.osVersion = originVer.ifEmpty { buildVer }
        info.vivoOsVersion = info.osVersion
        info.vivoProductSolution = prop("ro.vivo.product.solution")
        info.vivoModel = prop("ro.vivo.product.model")
            .ifEmpty { Build.MODEL }

        // → OriginOS 5/6 版本推断
        if (originVer.contains("5.") || otaVer.contains("ORIGINOS5")) {
            info.osName = "OriginOS 5"
        } else if (originVer.contains("6.") || otaVer.contains("ORIGINOS6")) {
            info.osName = "OriginOS 6"
        }
    }

    // ═══════════════ 子系统特性 (Android 16 新增) ═══════════════

    private fun detectAiEngine(info: OemInfo) {
        info.aiEngineInfo = when (info.oem) {
            OEM_XIAOMI -> {
                val hyperMind = prop("persist.sys.hypermind.enable", "0")
                val aiVersion = prop("ro.miui.ai.version", "")
                when {
                    hyperMind == "1" && aiVersion.isNotEmpty() -> "HyperMind $aiVersion"
                    hyperMind == "1" -> "HyperMind (已启用)"
                    aiVersion.isNotEmpty() -> "小爱AI v$aiVersion"
                    else -> ""
                }
            }
            OEM_OPPO -> {
                val andesGpt = prop("ro.oplus.ai.andesgpt", "0")
                val aiBreeno = prop("ro.oplus.ai.breeno", "0")
                when {
                    andesGpt == "1" -> "AndesGPT (安第斯大模型)"
                    aiBreeno == "1" -> "Breeno AI"
                    else -> ""
                }
            }
            OEM_VIVO -> {
                val blueLM = prop("ro.vivo.ai.bluelm", "0")
                val joviVer = prop("ro.vivo.ai.jovi.version", "")
                when {
                    blueLM == "1" && joviVer.isNotEmpty() -> "BlueLM v$joviVer (蓝心大模型)"
                    blueLM == "1" -> "BlueLM (蓝心大模型)"
                    else -> ""
                }
            }
            else -> ""
        }
    }

    private fun detectMemoryFusion(info: OemInfo) {
        info.memoryFusion = when (info.oem) {
            OEM_XIAOMI -> {
                // 小米内存扩展
                val swapSize = prop("persist.sys.memory_extension.size", "0")
                if (swapSize != "0") {
                    "${swapSize}GB 内存扩展"
                } else {
                    val miuiOpt = prop("persist.sys.miui_optimistic", "0")
                    if (miuiOpt == "1") "MIUI 内存优化已启用" else ""
                }
            }
            OEM_OPPO -> {
                // OPPO 内存融合 (RAM+)
                val ramPlus = prop("ro.oplus.memory.ramplus", "0")
                val ramPlusActual = prop("persist.sys.oplus_ramplus", "0")
                when {
                    ramPlusActual != "0" -> "+${ramPlusActual}GB RAM+扩展"
                    ramPlus != "0" -> "+${ramPlus}GB RAM+支持"
                    else -> ""
                }
            }
            OEM_VIVO -> {
                // Vivo 内存融合
                val memFusion = prop("persist.vivo.memory_fusion", "0")
                val memFusionSize = prop("persist.vivo.memory_fusion.size", "0")
                when {
                    memFusionSize != "0" -> "+${memFusionSize}GB 内存融合"
                    memFusion == "1" -> "内存融合已启用"
                    else -> ""
                }
            }
            else -> ""
        }
    }

    private fun detectThermalSolution(info: OemInfo) {
        info.thermalSolution = when (info.oem) {
            OEM_XIAOMI -> {
                val coolingType = prop("ro.product.cooling.solution", "")
                val vapChamber = prop("persist.vendor.vc_cooling", "0")
                when {
                    coolingType.isNotEmpty() -> coolingType
                    vapChamber == "1" -> "VC均热板散热"
                    else -> ""
                }
            }
            OEM_OPPO -> {
                val thermal = prop("ro.oplus.thermal.solution", "")
                thermal.ifEmpty { "" }
            }
            OEM_VIVO -> {
                val thermal = prop("ro.vivo.thermal.solution", "")
                thermal.ifEmpty { "" }
            }
            else -> ""
        }
    }

    private fun detectStorageBoost(info: OemInfo) {
        info.storageBoost = when (info.oem) {
            OEM_XIAOMI -> {
                // UFS Turbo / F2FS
                val isF2fs = prop("ro.product.fs.type", "").contains("f2fs")
                val ufsTurbo = prop("persist.sys.ufs_turbo", "0")
                when {
                    ufsTurbo == "1" -> "UFS Turbo (已启用)"
                    isF2fs -> "F2FS 文件系统"
                    else -> ""
                }
            }
            OEM_OPPO -> {
                val ufs = prop("ro.oplus.storage.ufs", "")
                ufs.ifEmpty { "" }
            }
            OEM_VIVO -> {
                val storage = prop("ro.vivo.storage.boost", "")
                storage.ifEmpty { "" }
            }
            else -> ""
        }
    }

    private fun detectDisplayFeatures(info: OemInfo) {
        val features = mutableListOf<String>()

        // 通用: 刷新率
        val refreshRate = prop("ro.surface_flinger.refresh_rate", "")
        if (refreshRate.isNotEmpty()) features.add("${refreshRate}Hz")

        // 通用: HDR
        val hdr = prop("ro.product.display.hdr", "")
        if (hdr.isNotEmpty()) features.add(hdr)

        when (info.oem) {
            OEM_XIAOMI -> {
                // LTPO
                if (prop("ro.vendor.display.ltpo", "0") == "1") features.add("LTPO")
                // DC调光
                if (prop("ro.vendor.display.dc_dimming", "0") == "1") features.add("DC调光")
                // 护眼模式
                if (prop("ro.vendor.display.eye_care", "0") == "1") features.add("护眼模式")
            }
            OEM_OPPO -> {
                // LTPO
                if (prop("ro.oplus.display.ltpo", "0") == "1") features.add("LTPO")
                // PWM调光
                if (prop("ro.oplus.display.pwm", "0") == "1") features.add("高频PWM")
            }
            OEM_VIVO -> {
                if (prop("ro.vivo.display.ltpo", "0") == "1") features.add("LTPO")
                if (prop("ro.vivo.display.pwm", "0") == "1") features.add("高频PWM")
            }
        }

        info.displayFeatures = features.joinToString(" · ")
    }

    // ═══════════════ 性能模式检测 ═══════════════

    private fun detectPerformanceModes(info: OemInfo, oem: String) {
        when (oem) {
            OEM_XIAOMI -> {
                info.gameModeSupported = prop("persist.sys.game_mode", "0") == "1"
                    || prop("persist.vendor.game_mode", "0") == "1"
                    || prop("persist.sys.miui_game_mode", "0") == "1"
                    || prop("sys.game_mode", "0") == "1"

                val powerMode = prop("persist.sys.power_mode", "0")
                val vendorPm = prop("persist.vendor.power_mode", "0")
                info.highPerformanceMode = powerMode == "1" || vendorPm == "1"
                    || prop("sys.power_mode", "0") == "1"
                    || prop("persist.sys.performance_mode", "0") == "1"
                    || prop("sys.perf_mode", "0") == "1"
            }
            OEM_OPPO -> {
                info.gameModeSupported = prop("persist.sys.oplus_gamemode", "0") == "1"
                    || prop("persist.vendor.oplus_gamemode", "0") == "1"
                    || prop("sys.oplus.gamemode", "0") == "1"
                    || prop("persist.sys.game_mode", "0") == "1"

                info.highPerformanceMode = prop("sys.oplus.performance_mode", "0") == "1"
                    || prop("persist.sys.performance_mode", "0") == "1"
                    || prop("sys.perf_mode", "0") == "1"
                    || prop("persist.vendor.performance_mode", "0") == "1"
            }
            OEM_VIVO -> {
                info.gameModeSupported = prop("persist.vivo.game_mode_supported", "0") == "1"
                    || prop("persist.sys.game_mode", "0") == "1"

                info.highPerformanceMode = prop("persist.sys.power_mode", "0") == "1"
                    || prop("persist.vivo.power_mode", "0") == "1"
                    || prop("sys.power_mode", "0") == "1"
                    || prop("sys.perf_mode", "0") == "1"
            }
            else -> {
                info.gameModeSupported = prop("persist.sys.game_mode", "0") == "1"
                info.highPerformanceMode = prop("sys.perf_mode", "0") == "1"
                    || prop("persist.sys.performance_mode", "0") == "1"
                    || prop("persist.sys.power_mode", "0") == "1"
            }
        }
    }

    // ═══════════════ 厂商原始属性 (50+ keys) ═══════════════

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
                "ro.build.version.incremental",
                "persist.sys.power_mode", "persist.sys.game_mode",
                "persist.sys.miui_game_mode",
                "ro.miui.hyperconnect.available",
                "persist.sys.hypermind.enable",
                "ro.miui.ai.version", "ro.miui.ai_assistant",
                "ro.vendor.hyperos.advanced_textures",
                "persist.sys.memory_extension.size",
            )
            OEM_OPPO -> listOf(
                "ro.build.version.opporom", "ro.oplus.display.oplusrom",
                "ro.oplus.image.my_engineering.version",
                "ro.oplus.image.my_product.version",
                "ro.oplus.display.screen.ratio",
                "ro.oplus.audio.soundeffect.type",
                "ro.oplus.camera.types",
                "persist.oplus.radio.multisim.config",
                "ro.build.version.ota", "ro.oplus.anr.layout",
                "persist.sys.oplus_region", "persist.sys.oplus_gamemode",
                "sys.oplus.performance_mode", "persist.sys.performance_mode",
                "ro.oplus.ai.andesgpt", "ro.oplus.ai.breeno",
                "ro.oplus.memory.ramplus", "ro.oplus.chg.protocol",
                "ro.oplus.chg.vooc_version", "ro.oplus.storage.ufs",
                "persist.sys.oplus_ramplus",
            )
            OEM_VIVO -> listOf(
                "ro.vivo.os.version", "ro.vivo.os.build.display.id",
                "ro.vivo.product.solution", "ro.vivo.product.model",
                "ro.vivo.product.release.name", "ro.vivo.market.name",
                "ro.vivo.oem.sku", "persist.vivo.game_mode_supported",
                "ro.vivo.hardware.version",
                "persist.sys.power_mode", "persist.vivo.power_mode",
                "ro.vivo.ai.bluelm", "ro.vivo.ai.jovi.version",
                "persist.vivo.memory_fusion", "persist.vivo.memory_fusion.size",
                "ro.vivo.storage.boost", "ro.vivo.thermal.solution",
            )
            else -> listOf(
                "ro.build.display.id", "ro.build.version.security_patch",
                "ro.board.platform", "ro.soc.manufacturer", "ro.soc.model",
                "ro.chipname", "ro.hardware.chipname",
                "ro.build.description",
                "persist.sys.power_mode", "sys.perf_mode",
            )
        }
        return keys.mapNotNull { k -> prop(k).takeIf { it.isNotEmpty() }?.let { k to it } }
    }

    // ═══════════════ SystemProperties 反射 ═══════════════

    private fun prop(key: String, default: String = ""): String {
        return try {
            spGet?.invoke(null, key, default) as? String ?: default
        } catch (_: Throwable) {
            try {
                Class.forName("android.os.SystemProperties")
                    .getMethod("get", String::class.java, String::class.java)
                    .invoke(null, key, default) as? String ?: default
            } catch (_: Throwable) { default }
        }
    }

    private fun readSysfs(path: String): String? {
        return try { File(path).readText() } catch (_: Throwable) { null }
    }
}
