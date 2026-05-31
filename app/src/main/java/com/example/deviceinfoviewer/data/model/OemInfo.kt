package com.example.deviceinfoviewer.data.model

/**
 * OEM 定制系统信息 — Android 16 深度支持
 *
 * 覆盖: Xiaomi HyperOS 2.0/3.0 · OPPO ColorOS 15/16 · Vivo OriginOS 5/6
 */
data class OemInfo(
    // ── 识别 ──
    var oem: String = "",           // Xiaomi / OPPO / Vivo / AOSP
    var osName: String = "",        // HyperOS / ColorOS / OriginOS
    var osVersion: String = "",     // ROM 版本号
    var androidVersion: String = "",// Android 基础版本
    var sdkLevel: Int = 0,          // API Level

    // ── 系统基础 ──
    var buildDisplayId: String = "",
    var securityPatch: String = "",
    var socManufacturer: String = "",
    var socModel: String = "",
    var boardPlatform: String = "",

    // ── 小米 HyperOS 专用 ──
    var miuiVersion: String = "",
    var miuiRegion: String = "",
    var miuiHardware: String = "",
    var miuiFeatures: String = "",

    // ── OPPO ColorOS 专用 ──
    var oppoVersion: String = "",
    var oppoScreenRatio: String = "",
    var oplusCharging: String = "",

    // ── Vivo OriginOS 专用 ──
    var vivoOsVersion: String = "",
    var vivoProductSolution: String = "",
    var vivoModel: String = "",

    // ── 游戏/性能模式 ──
    var gameModeSupported: Boolean = false,
    var highPerformanceMode: Boolean = false,

    // ── 厂商子系统特性 (Android 16 新增) ──
    var aiEngineInfo: String = "",       // AI引擎: HyperMind / AndesGPT / BlueLM
    var memoryFusion: String = "",       // 内存融合/扩展配置
    var thermalSolution: String = "",    // 散热方案: VC液冷/石墨烯等
    var storageBoost: String = "",       // 存储加速: UFS Turbo/闪存加速
    var displayFeatures: String = "",    // 显示特性: LTPO/护眼模式/HDR

    // ── 厂商原始属性 (完整导出) ──
    var rawProperties: List<Pair<String, String>> = emptyList()
)
