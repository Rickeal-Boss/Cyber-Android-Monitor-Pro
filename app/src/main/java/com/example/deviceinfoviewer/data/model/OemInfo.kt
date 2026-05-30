package com.example.deviceinfoviewer.data.model

/**
 * OEM 定制系统信息 — HyperOS / ColorOS / OriginOS
 */
data class OemInfo(
    // 识别
    var oem: String = "",       // Xiaomi / OPPO / Vivo / AOSP
    var osName: String = "",    // HyperOS / ColorOS / OriginOS
    var osVersion: String = "",

    // 系统
    var buildDisplayId: String = "",
    var securityPatch: String = "",
    var socManufacturer: String = "",
    var socModel: String = "",
    var boardPlatform: String = "",

    // 小米 HyperOS 专用
    var miuiVersion: String = "",
    var miuiRegion: String = "",
    var miuiHardware: String = "",
    var miuiFeatures: String = "",

    // OPPO ColorOS 专用
    var oppoVersion: String = "",
    var oppoScreenRatio: String = "",
    var oplusCharging: String = "",

    // Vivo OriginOS 专用
    var vivoOsVersion: String = "",
    var vivoProductSolution: String = "",
    var vivoModel: String = "",

    // 游戏/性能模式
    var gameModeSupported: Boolean = false,
    var highPerformanceMode: Boolean = false,

    // 厂商原始属性 (完整导出)
    var rawProperties: List<Pair<String, String>> = emptyList()
)
