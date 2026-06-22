package com.example.deviceinfoviewer.data.model

/**
 * OEM 定制系统信息 — v3 深度增强
 *
 * 覆盖: Xiaomi HyperOS 2.0~3.0 · OPPO ColorOS 13~16 · Vivo OriginOS 5~6
 * 新增: 相机传感器 · 充电协议增强 · 显示面板 · 性能调度器 · 安全芯片 · 自研芯片
 */
data class OemInfo(
    // ── 识别 ──
    var oem: String = "",           // Xiaomi / OPPO / Vivo / Samsung / AOSP
    var osName: String = "",        // HyperOS / ColorOS / OriginOS / One UI
    var osVersion: String = "",     // ROM 版本号
    var androidVersion: String = "",// Android 基础版本
    var sdkLevel: Int = 0,          // API Level

    // ── 系统基础 ──
    var buildDisplayId: String = "",
    var securityPatch: String = "",
    var socManufacturer: String = "",
    var socModel: String = "",
    var boardPlatform: String = "",

    // ══════════════════════════════════════════
    //  小米 HyperOS 专用
    // ══════════════════════════════════════════
    var miuiVersion: String = "",
    var miuiRegion: String = "",
    var miuiHardware: String = "",
    var miuiFeatures: String = "",

    // 小米自研芯片 (新增)
    var xiaomiSurgeChip: String = "",      // Surge 充电/电源芯片
    var xiaomiPengpaiISP: String = "",      // 澎湃 ISP/C1/C2
    var xiaomiSecurityChip: String = "",    // 安全芯片型号

    // HyperOS 3.0 新特性
    var hyperOsAIModel: String = "",        // 大模型: HyperMind/小爱AI
    var hyperOsCrossDevice: String = "",    // 跨端互联: HyperConnect
    var hyperOsPerformanceGrade: String = "",// 性能评级: 性能模式/省电/均衡

    // ══════════════════════════════════════════
    //  OPPO ColorOS 专用
    // ══════════════════════════════════════════
    var oppoVersion: String = "",
    var oppoScreenRatio: String = "",
    var oplusCharging: String = "",

    // OPPO 自研/增强 (新增)
    var oppoMariSilicon: String = "",       // MariSilicon X/Y 影像NPU
    var oppoDCE: String = "",               // Dynamic Computing Engine 动态计算引擎
    var oppoRAMPlus: String = "",           // RAM+ 内存融合详情
    var oppoTrucoEngine: String = "",       // Truco 游戏稳帧引擎
    var oppoColorOSVersion: String = "",    // ColorOS 精确版本号

    // ══════════════════════════════════════════
    //  Vivo OriginOS 专用
    // ══════════════════════════════════════════
    var vivoOsVersion: String = "",
    var vivoProductSolution: String = "",
    var vivoModel: String = "",

    // Vivo 自研芯片/增强 (新增)
    var vivoV3Chip: String = "",            // V3/V3+ 自研影像芯片
    var vivoRAMFusion: String = "",         // 内存融合详情
    var vivoOriginOSVersion: String = "",   // OriginOS 精确版本号
    var vivoDisplayEngine: String = "",     // 显示引擎: MEMC/护眼

    // ══════════════════════════════════════════
    //  相机传感器 (Camera2 API, 新增)
    // ══════════════════════════════════════════
    var cameraRearSensors: String = "",     // 后置传感器型号
    var cameraFrontSensor: String = "",     // 前置传感器型号
    var cameraRearAperture: String = "",    // 后置光圈 f/x.x
    var cameraFrontAperture: String = "",   // 前置光圈
    var cameraSensorPhysicalSize: String = "",// 传感器物理尺寸 mm
    var cameraOpticalStabilization: Boolean = false,// OIS 光学防抖
    var cameraFlashType: String = "",       // 闪光灯: LED/双色温/无
    var cameraMaxZoom: String = "",         // 最大变焦: 2x/5x/10x/100x

    // ══════════════════════════════════════════
    //  充电协议增强 (新增)
    // ══════════════════════════════════════════
    var chargingProtocol: String = "",     // 协议: QC5.0/PD3.0/SUPERVOOC/DashCharge
    var chargingMaxWatt: String = "",      // 最大充电功率
    var chargingBatteryCapacity: String = "",// 电池额定容量 mAh
    var chargingDualCell: Boolean = false,  // 双电芯
    var chargingWirelessPower: String = "", // 无线充电功率 (如有)

    // ══════════════════════════════════════════
    //  显示面板增强 (新增)
    // ══════════════════════════════════════════
    var displayPanelType: String = "",      // AMOLED/PMOLED/TFT-LCD
    var displayPanelManufacturer: String = "",// 面板厂: Samsung/BOE/Visionox/CSOT
    var displayLTPO: Boolean = false,      // LTPO 变频
    var displayMinRefreshRate: String = "",// 最低刷新率 Hz
    var displayMaxRefreshRate: String = "",// 最高刷新率 Hz
    var displayPeakBrightness: String = "",// 峰值亮度 nits
    var displayHDR: String = "",           // HDR 支持: HDR10+/Dolby Vision/无

    // ══════════════════════════════════════════
    //  性能调度器 (新增)
    // ══════════════════════════════════════════
    var cpuGovernor: String = "",          // CPU 调度器: ondemand/performance/interactive
    var gpuGovernor: String = "",          // GPU 调度器: simple/msm-adreno
    var thermalGovernor: String = "",      // 热管理: thermal-engine/freq_limits

    // ══════════════════════════════════════════
    //  安全芯片 (新增)
    // ══════════════════════════════════════════
    var securityChip: String = "",          // 安全芯片: Titan M/Trustzone/TEE
    var secureBoot: Boolean = false,        // 安全启动
    var verifiedBootState: String = "",    // 验证启动状态: green/orange/yellow

    // ══════════════════════════════════════════
    //  游戏/性能模式 (保留)
    // ══════════════════════════════════════════
    var gameModeSupported: Boolean = false,
    /** 性能/高性能模式 (合并为一个: 开启即进入高性能状态) */
    var highPerformanceMode: Boolean = false,
    /** 省电模式 (独立, 与超级省电互斥) */
    var powerSaveMode: Boolean = false,
    /** 超级省电模式 (独立, 与省电互斥) */
    var ultraPowerSaveMode: Boolean = false,
    /** Vivo Boost 模式 (厂商特有) */
    var vivoBoostMode: Boolean = false,
    /** 当前活跃的调度模式名称 (用于显示) */
    var powerModeCurrent: String = "",

    // ══════════════════════════════════════════
    //  厂商子系统特性 (保留)
    // ══════════════════════════════════════════
    var aiEngineInfo: String = "",       // AI引擎: HyperMind / AndesGPT / BlueLM
    var memoryFusion: String = "",       // 内存融合/扩展配置
    var thermalSolution: String = "",    // 散热方案: VC液冷/石墨烯等
    var storageBoost: String = "",       // 存储加速: UFS Turbo/闪存加速
    var displayFeatures: String = "",    // 显示特性: LTPO/护眼模式/HDR

    // ── 厂商原始属性 (完整导出) ──
    var rawProperties: List<Pair<String, String>> = emptyList()
)
