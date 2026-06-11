package com.example.deviceinfoviewer.data.source

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Log
import com.example.deviceinfoviewer.data.model.OemInfo
import java.io.File
import java.lang.reflect.Method

// OEM v3 - 三大ROM深度识别 + 硬件增强 (相机/充电/显示/调度器/安全)
// Xiaomi HyperOS / OPPO ColorOS / Vivo OriginOS
class OemDataSource(private val context: Context? = null) {

    companion object {
        private const val TAG = "OemDataSource"
        private const val OEM_XIAOMI = "Xiaomi"
        private const val OEM_OPPO = "OPPO"
        private const val OEM_VIVO = "Vivo"
        private const val OEM_SAMSUNG = "Samsung"
        private const val OEM_AOSP = "AOSP"

        private var spClass: Class<*>? = null
        private var spGet: Method? = null

        init {
            try {
                spClass = Class.forName("android.os.SystemProperties")
                spGet = spClass?.getMethod("get", String::class.java, String::class.java)
            } catch (_: Throwable) {}
        }

        // Camera2 API 28+ 常量反射缓存 — 避免低版本设备 dex 验证崩溃
        private var cachedOisKey: Any? = null
        private var cachedOisModeOn: Int = -1
        private var oisKeyResolved = false

        private fun resolveCamera2Constants() {
            if (oisKeyResolved) return
            oisKeyResolved = true
            try {
                val field = CameraCharacteristics::class.java.getField("LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION")
                cachedOisKey = field.get(null)
            } catch (_: Throwable) {}
            try {
                val field = CameraCharacteristics::class.java.getField("LENS_OPTICAL_STABILIZATION_MODE_ON")
                cachedOisModeOn = field.getInt(null)
            } catch (_: Throwable) {}
        }
    }

    // ═══ 电池调度模式属性列表 (供 detectPerformanceModes 使用) ═══

        /** 性能/高性能模式 (合并) → 属性列表 */
        private val PERFORMANCE_PROPS = arrayOf(
            "persist.sys.power_mode" to setOf("1", "performance", "perf", "high_performance", "high", "turbo", "gaming", "game"),
            "persist.vendor.power_mode" to setOf("1", "performance", "perf"),
            "sys.power_mode" to setOf("1", "performance", "perf"),
            "persist.sys.performance_mode" to setOf("1", "true", "on"),
            "persist.vendor.performance_mode" to setOf("1", "true", "on"),
            "sys.oplus.performance_mode" to setOf("1", "true", "high", "performance", "on"),
            "persist.sys.oplus.performance_mode" to setOf("1", "true"),
            "persist.vivo.performance_mode" to setOf("1", "true"),
            "sys.perf_mode" to setOf("1", "true", "high_performance", "performance", "fast"),
            "persist.sys.game_mode" to setOf("1", "true", "on"),
            "persist.vendor.game_mode" to setOf("1", "true"),
            "persist.sys.redmi_fury" to setOf("1", "true"),
            "persist.vendor.godzilla_mode" to setOf("1", "true"),
            "persist.sys.fury_engine" to setOf("1", "true"),
            "persist.sys.miui_game_mode" to setOf("1"),
            "persist.sys.miui_perf_mode" to setOf("1", "true"),
            "debug.sf.gpu_clock" to setOf("1"),
            "sys.performance" to setOf("1", "true"),
        )

        /** 省电模式 → 属性列表 */
        private val POWER_SAVE_PROPS = arrayOf(
            "persist.sys.power_mode" to setOf("2", "powersave", "power_save", "power_saving", "battery_saver", "eco", "low", "low_power"),
            "persist.sys.battery_saver" to setOf("1", "true", "on"),
            "persist.vendor.battery_saver" to setOf("1", "true"),
            "sys.battery_saver" to setOf("1", "true"),
            "persist.sys.xiaomi_battery_saver" to setOf("1"),
            "sys.battery_saver_mode" to setOf("1", "true", "medium_power_saving"),
            "persist.vivo.power_save" to setOf("1", "true"),
            "persist.sys.oplus.energysave" to setOf("1", "true"),
            "persist.sys.eco_mode" to setOf("1", "true"),
        )

        /** 超级省电模式 → 属性列表 */
        private val ULTRA_SAVE_PROPS = arrayOf(
            "persist.sys.ultra_powersave" to setOf("1", "true"),
            "persist.sys.extreme_battery_saver" to setOf("1", "true"),
            "persist.vendor.extreme_battery_saver" to setOf("1", "true"),
            "persist.sys.super_battery_saver" to setOf("1", "true"),
            "persist.vendor.super_battery_saver" to setOf("1", "true"),
            "persist.sys.ultra_battery_saver" to setOf("1", "true"),
            "persist.sys.max_power_save" to setOf("1", "true"),
            "persist.sys.oplus.super_energysave" to setOf("1", "true"),
            "persist.vivo.ultra_power_save" to setOf("1", "true"),
        )

        /** Vivo Boost 模式 → 属性列表 */
        private val VIVO_BOOST_PROPS = arrayOf(
            "persist.vivo.boost_mode" to setOf("1", "true", "boost", "on"),
            "persist.sys.vivo_boost" to setOf("1", "true"),
            "sys.vivo.boost" to setOf("1", "true"),
            "persist.vivo.game_boost" to setOf("1", "true"),
            "persist.sys.vivo_boost_mode" to setOf("1", "true"),
        )

        /** 判断是否匹配可接受值集合 */
        fun valueMatches(raw: String, accepted: Set<String>): Boolean {
            if (raw.isEmpty()) return false
            return raw.lowercase().trim() in accepted
        }

        /** 从一组属性中检测模式是否开启 */
        fun checkMode(props: Array<Pair<String, Set<String>>>): Boolean {
            for ((key, values) in props) {
                try {
                    val raw = prop(key)
                    if (raw.isNotEmpty() && valueMatches(raw, values)) return true
                } catch (_: Throwable) { continue }
            }
            return false
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
            OEM_SAMSUNG -> collectSamsung(info)
        }

        // 子系统特性 (Android 16)
        detectAiEngine(info)
        detectMemoryFusion(info)
        detectThermalSolution(info)
        detectStorageBoost(info)
        detectDisplayFeatures(info)

        // v3 新增: 硬件增强
        collectCameraSensorInfo(info)
        collectChargingEnhanced(info, oem)
        collectDisplayPanel(info)
        collectPerformanceGovernors(info)
        collectSecurityInfo(info)

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
            "samsung" in manufacturer || "samsung" in brand
                || "samsung" in fingerprint -> OEM_SAMSUNG
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
        } else if (incremental.contains("OS3")) {
            info.osName = "HyperOS 3.0"
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

        // → Surge 澎湃芯片检测 (P1 / P2 快充 + G1 电池管理)
        val surgeChips = mutableListOf<String>()
        val surgePmic = prop("ro.boot.pmic_charger", "")
        val surgeType = prop("ro.boot.pmic_charger_type", "")
        val surgeP2 = prop("ro.vendor.surge.p2", "0")
        val surgeG1 = prop("ro.vendor.surge.g1", "0")

        if (surgePmic.contains("surge", ignoreCase = true) || surgeType.contains("surge", ignoreCase = true)) {
            when {
                surgeP2 == "1" || surgePmic.contains("p2", ignoreCase = true) -> {
                    surgeChips.add("Surge P2 快充芯片")
                    if (surgePmic.contains("p1", ignoreCase = true)) surgeChips.add("Surge P1")
                }
                else -> surgeChips.add("Surge P1 快充芯片")
            }
        }
        if (surgeG1 == "1" || prop("ro.vendor.surge.g1.available", "0") == "1") {
            surgeChips.add("Surge G1 电池管理芯片")
        }
        info.xiaomiSurgeChip = surgeChips.joinToString(" + ")
        if (prop("ro.vendor.surge.isp", "0") == "1") {
            info.xiaomiPengpaiISP = "澎湃 C1 ISP"
        }
        if (prop("ro.vendor.pengpai.c2", "0") == "1") {
            info.xiaomiPengpaiISP = if (info.xiaomiPengpaiISP.isNotEmpty()) "澎湃 C2 ISP" else "澎湃 C2 ISP"
        }

        // 安全芯片
        val secChip = prop("ro.boot.security_chip", "")
        if (secChip.isNotEmpty()) info.xiaomiSecurityChip = secChip
        else if (prop("persist.sys.security_chip", "0") == "1") info.xiaomiSecurityChip = "内置安全芯片"

        // HyperOS 3.0 特性 (级联 AI 检测: MiClaw > HyperMind > 小爱AI)
        info.hyperOsAIModel = when {
            prop("persist.sys.miclaw.enable", "0") == "1"
                || prop("ro.miui.miclaw", "0") == "1" -> {
                val v = prop("ro.miui.miclaw.version", "")
                if (v.isNotEmpty()) "MiClaw v$v" else "MiClaw (已启用)"
            }
            prop("persist.sys.hypermind.enable", "0") == "1" -> {
                val v = prop("ro.miui.ai.version", "")
                if (v.isNotEmpty()) "HyperMind v$v" else "HyperMind (已启用)"
            }
            prop("ro.miui.ai_assistant", "0") == "1" -> "小爱AI (已启用)"
            else -> ""
        }

        info.hyperOsCrossDevice = when {
            prop("ro.miui.hyperconnect.available", "0") == "1" -> "HyperConnect (已启用)"
            prop("ro.miui.casta.support", "0") == "1" -> "妙享互联"
            else -> ""
        }

        // 性能评级 (含 Redmi 狂暴引擎 / 小米性能模式) — 在 detectPerformanceModes 中统一处理

        // → 特性列表
        val features = mutableListOf<String>()
        if (prop("ro.miui.has_real_blur", "0") == "1") features.add("RealBlur动态模糊")
        if (prop("ro.miui.has_handy_mode_sf", "0") == "1") features.add("单手模式")
        if (prop("ro.miui.notch", "0") == "1") features.add("刘海屏")
        if (prop("ro.miui.support_security_cta", "0") == "1") features.add("安全中心")
        val hyperMind = prop("persist.sys.hypermind.enable", "0")
        if (hyperMind == "1") features.add("HyperMind")
        val advancedTextures = prop("ro.vendor.hyperos.advanced_textures", "0")
        if (advancedTextures == "1") features.add("高级材质渲染")
        val aiAssistant = prop("ro.miui.ai_assistant", "0")
        if (aiAssistant == "1") features.add("小爱AI")
        val hyperConnect = prop("ro.miui.hyperconnect.available", "0")
        if (hyperConnect == "1") features.add("HyperConnect互联")
        val otaVersion = prop("ro.build.version.ota")
        if (otaVersion.isNotEmpty()) features.add("OTA:$otaVersion")
        if (info.xiaomiSurgeChip.isNotEmpty()) features.add(info.xiaomiSurgeChip)
        if (info.xiaomiPengpaiISP.isNotEmpty()) features.add(info.xiaomiPengpaiISP)
        info.miuiFeatures = features.joinToString(" · ")

        // → 版本精确推断
        val majorVer = try { hyperMajor.toIntOrNull() ?: 0 } catch (_: Throwable) { 0 }
        if (majorVer >= 816 || incremental.contains("OS3")) {
            info.osName = "HyperOS 3.0"
        } else if (majorVer >= 816 || incremental.contains("OS2")) {
            info.osName = "HyperOS 2.0"
        }
    }

    // ═══════════════ OPPO ColorOS 13~16 ═══════════════

    private fun collectOppo(info: OemInfo) {
        val oppoVer = prop("ro.build.version.opporom")
        val oplusVer = prop("ro.oplus.display.oplusrom")
        val otaVer = prop("ro.build.version.ota")
        val colorVersion = prop("ro.oplus.version", "")

        info.osName = "ColorOS"
        info.osVersion = oppoVer.ifEmpty { oplusVer }
        info.oppoVersion = info.osVersion
        info.oppoColorOSVersion = colorVersion.ifEmpty { oppoVer }
        info.oppoScreenRatio = prop("ro.oplus.display.screen.ratio")

        // → MariSilicon NPU 检测 (新增)
        val mari = prop("ro.oplus.ai.marisilicon", "0")
        val mariVer = prop("ro.oplus.mari.version", "")
        if (mari == "1" && mariVer.isNotEmpty()) {
            info.oppoMariSilicon = "MariSilicon $mariVer"
        } else if (mari == "1") {
            info.oppoMariSilicon = "MariSilicon X"
        }

        // → Dynamic Computing Engine
        val dce = prop("ro.oplus.dce.version", "")
        if (dce.isNotEmpty()) info.oppoDCE = "DCE v$dce"
        else if (prop("ro.oplus.dce.enable", "0") == "1") info.oppoDCE = "Dynamic Computing Engine"

        // → Truco 游戏引擎
        val truco = prop("ro.oplus.gaming.truco", "0")
        if (truco == "1") info.oppoTrucoEngine = "Truco 游戏稳帧引擎"
        else if (prop("ro.oplus.gaming.engine", "0") == "1") info.oppoTrucoEngine = "HyperBoost 游戏引擎"

        // → RAM+ 内存融合详情 (增强)
        val ramPlus = prop("ro.oplus.memory.ramplus", "0")
        val ramPlusActual = prop("persist.sys.oplus_ramplus", "0")
        val ramPlusMax = prop("ro.oplus.memory.ramplus.max", "")
        info.oppoRAMPlus = when {
            ramPlusActual != "0" -> "+${ramPlusActual}GB RAM+ (已启用${if (ramPlusMax.isNotEmpty()) "/最大$ramPlusMax" else ""})"
            ramPlus != "0" -> "RAM+ 支持${if (ramPlusMax.isNotEmpty()) " (最大$ramPlusMax)" else ""}"
            else -> ""
        }

        // → OPPO 充电信息 (增强)
        val chargingInfo = mutableListOf<String>()
        val fcc = readSysfs("/sys/class/oplus_chg/battery/battery_fcc")
        if (fcc != null) chargingInfo.add("FCC=${fcc.trim()}mA")
        val rm = readSysfs("/sys/class/oplus_chg/battery/battery_rm")
        if (rm != null) chargingInfo.add("RM=${rm.trim()}mAh")
        val chargeProtocol = prop("ro.oplus.chg.protocol", "")
        if (chargeProtocol.isNotEmpty()) chargingInfo.add("协议=$chargeProtocol")
        val superVooc = prop("ro.oplus.chg.vooc_version", "")
        if (superVooc.isNotEmpty()) chargingInfo.add("SUPERVOOC $superVooc")
        // 双电芯检测
        val dualCell = prop("ro.oplus.chg.dual_cell", "0")
        if (dualCell == "1") chargingInfo.add("双电芯")
        // 充电功率
        val chgPower = prop("ro.oplus.chg.max_power", "")
        if (chgPower.isNotEmpty()) chargingInfo.add("最大功率$chgPower")
        info.oplusCharging = chargingInfo.joinToString(" · ")

        // → ColorOS 版本精确推断
        when {
            oppoVer.startsWith("V16") || otaVer.contains("COLOROS16") || colorVersion.contains("16") -> info.osName = "ColorOS 16"
            oppoVer.startsWith("V15") || otaVer.contains("COLOROS15") || colorVersion.contains("15") -> info.osName = "ColorOS 15"
            oppoVer.startsWith("V14") || colorVersion.contains("14") -> info.osName = "ColorOS 14"
            oppoVer.startsWith("V13") || colorVersion.contains("13") -> info.osName = "ColorOS 13"
        }

        // → OnePlus 识别
        val isOnePlus = prop("ro.product.manufacturer", "").contains("OnePlus")
            || Build.BRAND.contains("OnePlus", ignoreCase = true)
        if (isOnePlus) {
            val oosVer = prop("ro.oxygen.version", "")
            info.osName = if (oosVer.isNotEmpty()) "OxygenOS $oosVer" else "OxygenOS/ColorOS"
        }

        // → Realme 识别
        val isRealme = Build.BRAND.contains("realme", ignoreCase = true)
        if (isRealme) {
            val realmeUI = prop("ro.realme.ui.version", "")
            if (realmeUI.isNotEmpty()) info.osName = "realme UI $realmeUI"
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
        info.vivoOriginOSVersion = originVer
        info.vivoProductSolution = prop("ro.vivo.product.solution")
        info.vivoModel = prop("ro.vivo.product.model")
            .ifEmpty { Build.MODEL }

        // → V3 自研影像芯片检测 (新增)
        val v3Chip = prop("ro.vivo.ai.chip", "0")
        val v3Ver = prop("ro.vivo.v3.version", "")
        if (v3Chip == "1" && v3Ver.isNotEmpty()) {
            info.vivoV3Chip = "V3+ 影像芯片 ($v3Ver)"
        } else if (v3Chip == "1") {
            info.vivoV3Chip = "V3 自研影像芯片"
        } else if (prop("ro.vivo.camera.v3", "0") == "1") {
            info.vivoV3Chip = "V3 自研影像芯片"
        }

        // → 内存融合详情 (增强)
        val memFusion = prop("persist.vivo.memory_fusion", "0")
        val memFusionSize = prop("persist.vivo.memory_fusion.size", "0")
        val memFusionMax = prop("ro.vivo.memory.fusion.max", "")
        info.vivoRAMFusion = when {
            memFusionSize != "0" -> "+${memFusionSize}GB 内存融合${if (memFusionMax.isNotEmpty()) " (最大$memFusionMax)" else ""}"
            memFusion == "1" -> "内存融合已启用"
            else -> ""
        }

        // → 显示引擎 (新增)
        val displayEngine = mutableListOf<String>()
        if (prop("ro.vivo.display.memc", "0") == "1") displayEngine.add("MEMC运动补偿")
        if (prop("ro.vivo.display.eye_care", "0") == "1") displayEngine.add("护眼模式")
        val displayRefresh = prop("ro.vivo.display.refresh", "")
        if (displayRefresh.isNotEmpty()) displayEngine.add("${displayRefresh}Hz")
        info.vivoDisplayEngine = displayEngine.joinToString(" · ")

        // → OriginOS 版本精确推断
        when {
            originVer.contains("6.") || otaVer.contains("ORIGINOS6") -> info.osName = "OriginOS 6"
            originVer.contains("5.") || otaVer.contains("ORIGINOS5") -> info.osName = "OriginOS 5"
            originVer.contains("4.") || otaVer.contains("ORIGINOS4") -> info.osName = "OriginOS 4"
        }

        // → iQOO 识别
        val isIQOO = Build.BRAND.contains("iqoo", ignoreCase = true)
        if (isIQOO) {
            val monsterVer = prop("ro.iqoo.monster.version", "")
            if (monsterVer.isNotEmpty()) info.osName = "Monster UI v$monsterVer"
        }
    }

    // ═══════════════ Samsung One UI ═══════════════
    private fun collectSamsung(info: OemInfo) {
        val oneUiVer = prop("ro.build.version.oneui")
        val seVer = prop("ro.build.version.se")
        val samVer = prop("ro.build.version.samsung")

        info.osName = when {
            oneUiVer.isNotEmpty() -> "One UI $oneUiVer"
            seVer.isNotEmpty() -> "One UI $seVer (Experience)"
            else -> "Samsung Experience"
        }
        info.osVersion = samVer.ifEmpty { prop("ro.build.version.incremental") }

        info.socManufacturer = prop("ro.soc.manufacturer").ifEmpty { "Samsung" }
        if (info.socModel.isEmpty()) {
            info.socModel = prop("ro.soc.model")
                .ifEmpty { prop("ro.hardware.chipname") }
                .ifEmpty { prop("ro.board.platform") }
        }

        val features = mutableListOf<String>()
        if (prop("ro.build.selinux") == "1") features.add("Knox")
        if (prop("ro.config.knox") == "v30" || prop("ro.config.knox").isNotEmpty()) features.add("Knox " + prop("ro.config.knox"))
        if (isPropEnabled("ro.securestorage.knox")) features.add("Secure Storage")
        if (prop("ro.vendor.audio.dolby") == "1" || prop("ro.audio.dolby") == "1") features.add("Dolby Atmos")
        if (isPropEnabled("ro.bt.bdaddr_path")) features.add("Samsung DeX")
        if (prop("ro.vendor.display.lcd_density").isNotEmpty()) features.add("Infinity Display")

        info.rawProperties = buildList {
            add("ro.build.version.oneui" to oneUiVer)
            add("ro.build.version.se" to seVer)
            add("ro.build.version.samsung" to samVer)
            add("ro.build.version.incremental" to prop("ro.build.version.incremental"))
            add("ro.config.knox" to prop("ro.config.knox"))
            add("ro.soc.manufacturer" to prop("ro.soc.manufacturer"))
            add("ro.soc.model" to prop("ro.soc.model"))
            add("ro.hardware.chipname" to prop("ro.hardware.chipname"))
            add("ro.board.platform" to prop("ro.board.platform"))
            add("ro.product.board" to Build.BOARD)
            add("ro.build.characteristics" to prop("ro.build.characteristics"))
            add("ro.build.selinux" to prop("ro.build.selinux"))
            add("ro.vendor.audio.dolby" to prop("ro.vendor.audio.dolby"))
            add("ro.audio.dolby" to prop("ro.audio.dolby"))
        }.filter { it.second.isNotEmpty() }
    }

    // ═══════════════ v3 新增: 相机传感器检测 ═══════════════
    // 参考: Camera2 API (API 21+), NDSS 2024 论证了硬件API无权限获取可行性

    private fun collectCameraSensorInfo(info: OemInfo) {
        if (context == null) return
        try {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
            val rearSensors = mutableListOf<String>()
            val frontSensors = mutableListOf<String>()

            for (id in manager.cameraIdList) {
                try {
                    val chars = manager.getCameraCharacteristics(id)
                    val facing = chars.get(CameraCharacteristics.LENS_FACING)
                    val isRear = facing == CameraCharacteristics.LENS_FACING_BACK
                    val isFront = facing == CameraCharacteristics.LENS_FACING_FRONT

                    if (!isRear && !isFront) continue

                    // 传感器物理尺寸
                    val physicalSize = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                    if (physicalSize != null && isRear) {
                        info.cameraSensorPhysicalSize = "${"%.1f".format(physicalSize.width)}×${"%.1f".format(physicalSize.height)} mm"
                    }

                    // 光圈
                    val apertures = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
                    if (apertures != null && apertures.isNotEmpty()) {
                        val f = apertures[0]
                        val fStr = "f/$f"
                        if (isRear && info.cameraRearAperture.isEmpty()) info.cameraRearAperture = fStr
                        if (isFront && info.cameraFrontAperture.isEmpty()) info.cameraFrontAperture = fStr
                    }

                    // OIS 光学防抖 — 使用反射访问 API 28+ 常量
                    resolveCamera2Constants()
                    if (cachedOisKey != null) {
                        @Suppress("UNCHECKED_CAST")
                        val oisKey = cachedOisKey as CameraCharacteristics.Key<IntArray>
                        val oisModes = chars.get(oisKey)
                        if (oisModes != null && cachedOisModeOn >= 0) {
                            if (oisModes.contains(cachedOisModeOn)) {
                                info.cameraOpticalStabilization = true
                            }
                        }
                    }

                    // 闪光灯
                    if (isRear && info.cameraFlashType.isEmpty()) {
                        val flashAvailable = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)
                        if (flashAvailable == true) {
                            info.cameraFlashType = "LED"
                        }
                    }

                    // 焦距 (用于推算变焦倍数)
                    val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    if (focalLengths != null && focalLengths.isNotEmpty()) {
                        val fl = focalLengths[0]
                        val desc = if (isRear) "后置 ${fl}mm" else "前置 ${fl}mm"
                        if (isRear) rearSensors.add(desc) else frontSensors.add(desc)
                    } else {
                        if (isRear) rearSensors.add("后置 #$id") else frontSensors.add("前置 #$id")
                    }
                } catch (_: Throwable) {}
            }

            info.cameraRearSensors = rearSensors.joinToString(" · ")
            info.cameraFrontSensor = frontSensors.joinToString(" · ")

            // 最大变焦倍数 (通过不同后置摄像头焦距推算)
            if (rearSensors.size > 1) {
                info.cameraMaxZoom = "${rearSensors.size}摄 (${rearSensors.size - 1}x 变焦)"
            }

        } catch (e: Throwable) {
            Log.w(TAG, "相机传感器采集失败", e)
        }

        // 补充: 通过厂商属性获取更详细的相机信息
        if (info.oem == OEM_XIAOMI) {
            val cameraId = prop("ro.product.camera.sensor", "")
            if (cameraId.isNotEmpty() && info.cameraRearSensors.isEmpty()) {
                info.cameraRearSensors = cameraId
            }
        } else if (info.oem == OEM_OPPO) {
            val cameraType = prop("ro.oplus.camera.types", "")
            if (cameraType.isNotEmpty()) info.cameraRearSensors = cameraType
        } else if (info.oem == OEM_VIVO) {
            val cameraSolution = prop("ro.vivo.camera.solution", "")
            if (cameraSolution.isNotEmpty() && info.cameraRearSensors.isEmpty()) {
                info.cameraRearSensors = cameraSolution
            }
        }
    }

    // ═══════════════ v3 新增: 充电协议增强检测 ═══════════════
    // 参考: Super VOOC (OPPO 10V/6.5A 65W), QC5.0, USB-PD 3.0

    private fun collectChargingEnhanced(info: OemInfo, oem: String) {
        // 电池容量
        val batteryCapacity = readSysfs("/sys/class/power_supply/battery/capacity")
        val batteryChargeFull = readSysfs("/sys/class/power_supply/battery/charge_full_design")
        val batteryFull = readSysfs("/sys/class/power_supply/battery/batt_full_cap")
        val capacityMa = batteryChargeFull ?: batteryFull ?: readSysfs("/sys/class/power_supply/battery/fcc_design")
        if (capacityMa != null) {
            val uah = capacityMa.trim().toIntOrNull() ?: 0
            info.chargingBatteryCapacity = if (uah > 0) "${uah / 1000} mAh" else "${capacityMa.trim()} μAh"
        }

        // 充电协议检测 (按 OEM 优先)
        when (oem) {
            OEM_OPPO -> {
                val vooc = prop("ro.oplus.chg.vooc_version", "")
                when {
                    vooc.contains("150") || vooc.contains("super") -> {
                        info.chargingProtocol = "SUPERVOOC 150W"
                        info.chargingMaxWatt = "150W"
                    }
                    vooc.contains("80") || vooc.contains("flash") -> {
                        info.chargingProtocol = "SUPERVOOC 80W"
                        info.chargingMaxWatt = "80W"
                    }
                    vooc.contains("65") || vooc.contains("vooc") -> {
                        info.chargingProtocol = "SUPERVOOC"
                        info.chargingMaxWatt = prop("ro.oplus.chg.max_power", "65W")
                    }
                    else -> {
                        val chgProto = prop("ro.oplus.chg.protocol", "")
                        if (chgProto.isNotEmpty()) info.chargingProtocol = chgProto
                    }
                }
                info.chargingDualCell = prop("ro.oplus.chg.dual_cell", "0") == "1"
                // 无线充电
                val wireless = prop("ro.oplus.chg.wireless_power", "")
                if (wireless.isNotEmpty()) info.chargingWirelessPower = "${wireless.trim()}W 无线"
            }
            OEM_XIAOMI -> {
                val qcVer = prop("ro.vendor.qc.version", "")
                val pdVer = prop("ro.vendor.pd.version", "")
                val surge = prop("ro.boot.pmic_charger", "")
                when {
                    surge.contains("surge", ignoreCase = true) -> {
                        info.chargingProtocol = "小米澎湃快充"
                        val watt = prop("ro.vendor.chg.max_power", "")
                        info.chargingMaxWatt = watt.ifEmpty { "120W" }
                    }
                    qcVer.contains("5") || qcVer.contains("5.0") -> {
                        info.chargingProtocol = "Qualcomm Quick Charge 5.0"
                    }
                    pdVer.isNotEmpty() -> {
                        info.chargingProtocol = "USB-PD $pdVer"
                    }
                    else -> {
                        if (qcVer.isNotEmpty()) info.chargingProtocol = "QC $qcVer"
                    }
                }
                info.chargingDualCell = prop("ro.vendor.chg.dual_cell", "0") == "1"
                val wireless = prop("ro.vendor.wireless_power", "")
                if (wireless.isNotEmpty()) info.chargingWirelessPower = "${wireless.trim()}W 无线"
            }
            OEM_VIVO -> {
                val flashCharge = prop("ro.vivo.chg.flash_version", "")
                when {
                    flashCharge.contains("200") || flashCharge.contains("super") -> {
                        info.chargingProtocol = "超快闪充 200W"
                        info.chargingMaxWatt = "200W"
                    }
                    flashCharge.contains("120") || flashCharge.contains("120w") -> {
                        info.chargingProtocol = "超快闪充 120W"
                        info.chargingMaxWatt = "120W"
                    }
                    flashCharge.contains("80") || flashCharge.contains("80w") -> {
                        info.chargingProtocol = "闪充 80W"
                        info.chargingMaxWatt = "80W"
                    }
                    flashCharge.isNotEmpty() -> {
                        info.chargingProtocol = "闪充 $flashCharge"
                    }
                    else -> {
                        val chgProto = prop("ro.vivo.chg.protocol", "")
                        if (chgProto.isNotEmpty()) info.chargingProtocol = chgProto
                    }
                }
                info.chargingDualCell = prop("ro.vivo.chg.dual_cell", "0") == "1"
                val wireless = prop("ro.vivo.chg.wireless_power", "")
                if (wireless.isNotEmpty()) info.chargingWirelessPower = "${wireless.trim()}W 无线"
            }
            OEM_SAMSUNG -> {
                info.chargingProtocol = "Samsung Adaptive Fast Charging"
                val wireless = prop("ro.vendor.wireless_power", "")
                if (wireless.isNotEmpty()) info.chargingWirelessPower = "${wireless.trim()}W 无线"
            }
        }
    }

    // ═══════════════ v3 新增: 显示面板检测 ═══════════════
    // 参考: BOE/Visionox LTPO OLED 论文验证面板信息可获取性

    private fun collectDisplayPanel(info: OemInfo) {
        // 面板类型
        val panelType = prop("ro.panel.type", "")
        val panelVendor = prop("ro.panel.vendor", "")
        val panelManufacturer = prop("ro.boot.primary_panel", "")

        if (panelType.isNotEmpty()) {
            info.displayPanelType = panelType
        } else {
            // 通过属性推断
            val isAmoled = prop("ro.vendor.display.amoled", "0") == "1"
                || prop("ro.oplus.display.amoled", "0") == "1"
            val isOled = prop("ro.vendor.display.oled", "0") == "1"
            info.displayPanelType = when {
                isAmoled -> "AMOLED"
                isOled -> "OLED"
                else -> "TFT-LCD"
            }
        }

        // 面板厂商
        info.displayPanelManufacturer = when {
            panelVendor.isNotEmpty() -> panelVendor
            panelManufacturer.contains("samsung", ignoreCase = true) -> "Samsung"
            panelManufacturer.contains("boe", ignoreCase = true) -> "BOE"
            panelManufacturer.contains("visionox", ignoreCase = true) -> "Visionox"
            panelManufacturer.contains("csot", ignoreCase = true) -> "CSOT (华星光电)"
            panelManufacturer.contains("tianma", ignoreCase = true) -> "Tianma (天马)"
            else -> ""
        }

        // LTPO
        info.displayLTPO = prop("ro.vendor.display.ltpo", "0") == "1"
            || prop("ro.oplus.display.ltpo", "0") == "1"
            || prop("ro.vivo.display.ltpo", "0") == "1"

        // 刷新率
        val refreshRate = prop("ro.surface_flinger.refresh_rate", "")
        if (refreshRate.isNotEmpty()) {
            info.displayMaxRefreshRate = "${refreshRate}Hz"
        }
        val minRate = prop("ro.vendor.display.min_refresh", "")
        if (minRate.isNotEmpty()) info.displayMinRefreshRate = "${minRate}Hz"

        // 峰值亮度
        val peakBrightness = prop("ro.vendor.display.peak_brightness", "")
        if (peakBrightness.isNotEmpty()) info.displayPeakBrightness = "${peakBrightness} nits"

        // HDR
        val hdr = prop("ro.product.display.hdr", "")
        if (hdr.isNotEmpty()) {
            info.displayHDR = hdr
        } else {
            val hdr10 = prop("ro.vendor.display.hdr10", "0") == "1"
            val dolbyVision = prop("ro.vendor.display.dolby_vision", "0") == "1"
            val hdrList = mutableListOf<String>()
            if (hdr10) hdrList.add("HDR10")
            if (dolbyVision) hdrList.add("Dolby Vision")
            info.displayHDR = hdrList.joinToString(" / ")
        }
    }

    // ═══════════════ v3 新增: 性能调度器检测 ═══════════════
    // 参考: BLAST (ICST 2015) — /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor

    private fun collectPerformanceGovernors(info: OemInfo) {
        // CPU 调度器
        info.cpuGovernor = readSysfs("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor")?.trim() ?: ""

        // GPU 调度器 (高通)
        info.gpuGovernor = readSysfs("/sys/class/kgsl/kgsl-3d0/devfreq/governor")?.trim()
            ?: readSysfs("/sys/class/devfreq/*.0/governor")?.trim()
            ?: ""

        // 热管理
        val thermalFiles = listOf(
            "/sys/class/thermal/thermal_zone*/type",
            "/sys/class/thermal/thermal_message/sconfig"
        )
        val thermalType = thermalFiles.firstNotNullOfOrNull { path ->
            readSysfs(path)?.trim()
        }
        info.thermalGovernor = thermalType ?: ""
    }

    // ═══════════════ v3 新增: 安全信息检测 ═══════════════

    private fun collectSecurityInfo(info: OemInfo) {
        // 安全芯片
        when (info.oem) {
            OEM_XIAOMI -> {
                val sec = prop("ro.boot.security_chip", "")
                if (sec.isNotEmpty()) info.securityChip = sec
                else if (prop("persist.sys.security_chip", "0") == "1") info.securityChip = "内置安全芯片"
            }
            OEM_OPPO -> {
                val sec = prop("ro.oplus.security.chip", "")
                if (sec.isNotEmpty()) info.securityChip = sec
            }
            OEM_VIVO -> {
                val sec = prop("ro.vivo.security.chip", "")
                if (sec.isNotEmpty()) info.securityChip = sec
            }
            OEM_SAMSUNG -> {
                if (prop("ro.config.knox").isNotEmpty()) info.securityChip = "Samsung Knox"
            }
            else -> {
                val sec = prop("ro.hardware.keystore", "")
                if (sec.contains("strongbox", ignoreCase = true)) info.securityChip = "StrongBox"
                else if (sec.contains("tee", ignoreCase = true)) info.securityChip = "TEE"
            }
        }

        // 安全启动
        info.secureBoot = prop("ro.secureboot", "") == "1"
            || prop("ro.boot.verifiedbootstate", "").isNotEmpty()

        // 验证启动状态
        info.verifiedBootState = when (val vb = prop("ro.boot.verifiedbootstate", "")) {
            "green" -> "已验证 (green)"
            "orange" -> "未验证 (orange)"
            "yellow" -> "已验证 (yellow)"
            "red" -> "不可信 (red)"
            else -> if (prop("ro.boot.vb2.device_state", "").isNotEmpty()) "verified" else ""
        }
    }

    // ═══════════════ 子系统特性 (保留) ═══════════════

    private fun detectAiEngine(info: OemInfo) {
        info.aiEngineInfo = when (info.oem) {
            OEM_XIAOMI -> {
                val miclawEnabled = prop("persist.sys.miclaw.enable", "0") == "1"
                    || prop("ro.miui.miclaw", "0") == "1"
                val miclawVer = prop("ro.miui.miclaw.version", "")
                val hyperMind = prop("persist.sys.hypermind.enable", "0")
                val aiVersion = prop("ro.miui.ai.version", "")
                when {
                    miclawEnabled && miclawVer.isNotEmpty() -> "MiClaw v$miclawVer"
                    miclawEnabled -> "MiClaw (已启用)"
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
            OEM_SAMSUNG -> {
                val bixby = prop("ro.samsung.bixby", "0")
                val gauss = prop("ro.samsung.ai.gauss", "0")
                when {
                    gauss == "1" -> "Samsung Gauss (高斯AI)"
                    bixby == "1" -> "Bixby"
                    else -> ""
                }
            }
            else -> ""
        }
    }

    private fun detectMemoryFusion(info: OemInfo) {
        info.memoryFusion = when (info.oem) {
            OEM_XIAOMI -> {
                val swapSize = prop("persist.sys.memory_extension.size", "0")
                if (swapSize != "0") {
                    "${swapSize}GB 内存扩展"
                } else {
                    val miuiOpt = prop("persist.sys.miui_optimistic", "0")
                    if (miuiOpt == "1") "MIUI 内存优化已启用" else ""
                }
            }
            OEM_OPPO -> {
                // 已由 oppoRAMPlus 字段覆盖
                info.oppoRAMPlus
            }
            OEM_VIVO -> {
                // 已由 vivoRAMFusion 字段覆盖
                info.vivoRAMFusion
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
            OEM_OPPO -> prop("ro.oplus.thermal.solution", "")
            OEM_VIVO -> prop("ro.vivo.thermal.solution", "")
            else -> ""
        }
    }

    private fun detectStorageBoost(info: OemInfo) {
        info.storageBoost = when (info.oem) {
            OEM_XIAOMI -> {
                val isF2fs = prop("ro.product.fs.type", "").contains("f2fs")
                val ufsTurbo = prop("persist.sys.ufs_turbo", "0")
                when {
                    ufsTurbo == "1" -> "UFS Turbo (已启用)"
                    isF2fs -> "F2FS 文件系统"
                    else -> ""
                }
            }
            OEM_OPPO -> prop("ro.oplus.storage.ufs", "")
            OEM_VIVO -> prop("ro.vivo.storage.boost", "")
            else -> ""
        }
    }

    private fun detectDisplayFeatures(info: OemInfo) {
        val features = mutableListOf<String>()
        val refreshRate = prop("ro.surface_flinger.refresh_rate", "")
        if (refreshRate.isNotEmpty()) features.add("${refreshRate}Hz")
        val hdr = prop("ro.product.display.hdr", "")
        if (hdr.isNotEmpty()) features.add(hdr)

        when (info.oem) {
            OEM_XIAOMI -> {
                if (prop("ro.vendor.display.ltpo", "0") == "1") features.add("LTPO")
                if (prop("ro.vendor.display.dc_dimming", "0") == "1") features.add("DC调光")
                if (prop("ro.vendor.display.eye_care", "0") == "1") features.add("护眼模式")
            }
            OEM_OPPO -> {
                if (prop("ro.oplus.display.ltpo", "0") == "1") features.add("LTPO")
                if (prop("ro.oplus.display.pwm", "0") == "1") features.add("高频PWM")
            }
            OEM_VIVO -> {
                if (prop("ro.vivo.display.ltpo", "0") == "1") features.add("LTPO")
                if (prop("ro.vivo.display.pwm", "0") == "1") features.add("高频PWM")
            }
            OEM_SAMSUNG -> {
                if (prop("ro.vendor.display.ltpo", "0") == "1") features.add("LTPO")
                if (prop("ro.vendor.display.dynamic_amoled", "0") == "1") features.add("Dynamic AMOLED")
                if (prop("ro.vendor.display.vision_booster", "0") == "1") features.add("Vision Booster")
                if (prop("ro.vendor.display.eye_shield", "0") == "1") features.add("Eye Comfort Shield")
            }
        }
        info.displayFeatures = features.joinToString(" · ")
    }

    private fun detectPerformanceModes(info: OemInfo, oem: String) {
        // ── Phase 1: 独立检测各模式 ──
        val perfOn = checkMode(PERFORMANCE_PROPS)
        val saveOn = checkMode(POWER_SAVE_PROPS)
        val ultraOn = checkMode(ULTRA_SAVE_PROPS)
        val boostOn = checkMode(VIVO_BOOST_PROPS)

        // ── Phase 2: OEM 专属属性补充检测 ──
        // Xiaomi: power_mode=0 → 均衡 (不开启任何模式)
        val xiaomiMode = if (oem == OEM_XIAOMI) {
            val pm = prop("persist.sys.power_mode")
            when {
                pm == "0" -> "均衡模式"
                pm == "1" -> "性能模式"
                pm == "2" -> "省电模式"
                else -> null
            }
        } else null

        // ── Phase 3: 互斥规则 ──
        // 超级省电 与 省电 互斥: 超级省电优先级更高
        val finalSave = if (ultraOn) false else saveOn
        val finalUltra = ultraOn

        // ── Phase 4: 应用检测结果 ──
        info.highPerformanceMode = perfOn
        info.powerSaveMode = finalSave
        info.ultraPowerSaveMode = finalUltra
        info.vivoBoostMode = boostOn

        // ── 构建显示名称 ──
        info.powerModeCurrent = when {
            // 优先级: 超级省电 → 省电 → 性能/高性能 → Vivo Boost → 均衡
            finalUltra -> "超级省电模式"
            finalSave -> "省电模式"
            perfOn -> "性能/高性能模式"
            boostOn -> "Boost 模式"
            xiaomiMode != null -> xiaomiMode  // Xiaomi 均衡模式
            else -> "均衡模式"
        }

        // Xiaomi 专用: 保持 hyperOsPerformanceGrade 兼容
        if (oem == OEM_XIAOMI) {
            info.hyperOsPerformanceGrade = when {
                finalUltra -> "超级省电模式"
                finalSave -> "省电模式"
                perfOn -> {
                    val isFury = checkMode(arrayOf(
                        "persist.sys.redmi_fury" to setOf("1", "true"),
                        "persist.vendor.godzilla_mode" to setOf("1", "true"),
                        "persist.sys.fury_engine" to setOf("1", "true"),
                    ))
                    if (isFury && (Build.BRAND.contains("redmi", ignoreCase = true)
                            || Build.MODEL.contains("Redmi", ignoreCase = true)
                            || prop("ro.product.brand", "").contains("redmi", ignoreCase = true)))
                        "Redmi 狂暴引擎"
                    else "性能模式"
                }
                else -> "均衡模式"
            }
        }

        // ── 游戏模式独立检测 (保持原有行为) ──
        info.gameModeSupported = when (oem) {
            OEM_XIAOMI -> checkMode(arrayOf(
                "persist.sys.game_mode" to setOf("1"),
                "persist.vendor.game_mode" to setOf("1"),
                "persist.sys.miui_game_mode" to setOf("1"),
                "sys.game_mode" to setOf("1")
            ))
            OEM_OPPO -> checkMode(arrayOf(
                "persist.sys.oplus_gamemode" to setOf("1"),
                "persist.vendor.oplus_gamemode" to setOf("1"),
                "sys.oplus.gamemode" to setOf("1")
            ))
            OEM_VIVO -> checkMode(arrayOf(
                "persist.vivo.game_mode_supported" to setOf("1"),
                "persist.sys.game_mode" to setOf("1")
            ))
            OEM_SAMSUNG -> checkMode(arrayOf(
                "persist.sys.gamemode" to setOf("1"),
                "sys.gamedvfs" to setOf("1")
            ))
            else -> checkMode(arrayOf("persist.sys.game_mode" to setOf("1")))
        }
    }

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
                "ro.boot.pmic_charger", "ro.vendor.surge.isp",
                "ro.vendor.surge.p2", "ro.vendor.surge.g1",
                "ro.vendor.pengpai.c2", "ro.boot.security_chip",
                "ro.miui.casta.support",
                "persist.sys.miclaw.enable", "ro.miui.miclaw",
                "persist.sys.redmi_fury", "persist.vendor.godzilla_mode",
                "persist.sys.fury_engine",
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
                "ro.oplus.chg.vooc_version", "ro.oplus.chg.dual_cell",
                "ro.oplus.chg.max_power", "ro.oplus.storage.ufs",
                "ro.oplus.ai.marisilicon", "ro.oplus.dce.version",
                "ro.oplus.gaming.truco", "ro.oplus.version",
                "ro.oplus.display.ltpo", "ro.oplus.display.pwm",
                "ro.oplus.thermal.solution",
            )
            OEM_VIVO -> listOf(
                "ro.vivo.os.version", "ro.vivo.os.build.display.id",
                "ro.vivo.product.solution", "ro.vivo.product.model",
                "ro.vivo.product.release.name", "ro.vivo.market.name",
                "ro.vivo.oem.sku", "persist.vivo.game_mode_supported",
                "ro.vivo.hardware.version",
                "persist.sys.power_mode", "persist.vivo.power_mode",
                "ro.vivo.ai.bluelm", "ro.vivo.ai.jovi.version",
                "ro.vivo.ai.chip", "ro.vivo.v3.version",
                "persist.vivo.memory_fusion", "persist.vivo.memory_fusion.size",
                "ro.vivo.memory.fusion.max",
                "ro.vivo.storage.boost", "ro.vivo.thermal.solution",
                "ro.vivo.chg.flash_version", "ro.vivo.chg.protocol",
                "ro.vivo.chg.dual_cell",
                "ro.vivo.display.ltpo", "ro.vivo.display.memc",
                "ro.vivo.display.eye_care", "ro.vivo.display.refresh",
            )
            OEM_SAMSUNG -> listOf(
                "ro.build.version.oneui", "ro.build.version.se",
                "ro.build.version.samsung", "ro.build.version.incremental",
                "ro.config.knox", "ro.soc.manufacturer", "ro.soc.model",
                "ro.hardware.chipname", "ro.board.platform",
                "ro.product.board", "ro.build.characteristics",
                "ro.build.selinux", "ro.vendor.audio.dolby",
                "ro.audio.dolby", "persist.sys.gamemode",
                "sys.gamedvfs", "sys.perf_mode",
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

    fun isPropEnabled(propName: String): Boolean {
        val v = prop(propName)
        return v == "1" || v == "true" || v == "enabled"
    }
}
