package com.example.deviceinfoviewer.data.model

/**
 * 设备详细信息 — 无 root 可获取的全面设备信息
 * v3: CPU缓存/内存类型/存储类型/USB/蓝牙/Wi-Fi版本/SoC制程/显示色深/热区
 */
data class DeviceDetailInfo(
    // ── Display (显示) ──
    var resolution: String = "",
    var densityDpi: Int = 0,
    var density: Float = 0f,
    var refreshRateHz: Float = 0f,
    var hdrCapabilities: List<String> = emptyList(),
    var physicalSizeInches: Float = 0f,
    var displayTechnology: String = "",       // OLED / LCD / AMOLED / LTPO
    var maxBrightnessNits: Int = 0,           // 峰值亮度 (dumpsys)
    var colorDepth: String = "",              // 8-bit / 10-bit / 12-bit (新增)
    var colorGamut: String = "",              // sRGB / DCI-P3 / Display P3 / BT.2020 (新增)

    // ── Graphics / GPU (OpenGL ES + EGL) ──
    var glEsVersion: String = "",
    var glVendor: String = "",                // 厂商 (Qualcomm/ARM/Imagination)
    var glRenderer: String = "",              // 真实 GPU 型号 (Adreno 750/Mali-G720)
    var glExtensions: List<String> = emptyList(),
    var glKeyExtensions: List<String> = emptyList(),  // v4: 关键扩展类别
    var gpuDriverVersion: String = "",        // GPU 驱动版本号
    var gpuLocalMemoryKb: Int = 0,           // GPU 专用显存 KB (新增, Adreno/Mali)

    // ── Vulkan ──
    var vulkanVersion: String = "",
    var vulkanApiLevel: String = "",
    var rayTracingSupported: Boolean = false,
    var vulkanDeviceCount: Int = 0,           // Vulkan 物理设备数
    var vulkanExtensions: List<String> = emptyList(),

    // ── CPU Cache Architecture (新增) ──
    var cpuCacheL1iKb: Int = 0,              // L1 指令缓存 KB
    var cpuCacheL1dKb: Int = 0,              // L1 数据缓存 KB
    var cpuCacheL2Kb: Int = 0,               // L2 缓存 KB (per cluster)
    var cpuCacheL3Kb: Int = 0,               // L3 缓存 KB (shared, 0=无)
    var cpuCacheSource: String = "",         // 来源标记 (sysfs/cpuinfo/lookup)

    // ── CPU Topology (新增) ──
    var cpuArchitecture: String = "",         // ARMv8-A / ARMv9-A
    var cpuImplementer: String = "",         // 0x41=ARM / 0x51=QCOM / 0x48=HiSilicon
    var cpuPart: String = "",                 // 0xd05=Cortex-A72 等
    var bigLITTLE: String = "",               // big.LITTLE 拓扑描述

    // ── SoC Details (新增) ──
    var socProcessNode: String = "",          // 制程: 3nm / 4nm / 5nm / 6nm / 7nm
    var socProcessNodeSource: String = "",    // 来源: lookup/property

    // ── Memory (新增) ──
    var memoryType: String = "",              // LPDDR4 / LPDDR4X / LPDDR5 / LPDDR5X
    var memoryTypeSource: String = "",        // 来源标记
    var memorySpeedMhz: Int = 0,              // 内存频率 MHz

    // ── Storage (新增) ──
    var storageType: String = "",             // UFS 2.1 / UFS 2.2 / UFS 3.0 / UFS 3.1 / UFS 4.0 / eMMC 5.1
    var storageTypeSource: String = "",      // 来源标记
    var storageProtocol: String = "",         // SCSI / eMMC 协议标识

    // ── USB (增强) ──
    var usbVersion: String = "",              // USB 2.0 / USB 3.0 / USB 3.1 / USB 3.2
    var usbTypeC: Boolean = false,            // 是否 Type-C
    var usbHostMode: Boolean = false,         // USB Host 模式

    // ── Bluetooth (增强) ──
    var bluetoothName: String = "",
    var bluetoothAddress: String = "",
    var bluetoothSupported: Boolean = false,
    var bluetoothVersion: String = "",        // BT 4.0 / 4.1 / 4.2 / 5.0 / 5.1 / 5.2 / 5.3 (新增)
    var bleSupported: Boolean = false,        // BLE 支持 (新增)
    var bluetoothLeAudio: Boolean = false,    // LE Audio (新增)

    // ── Wi-Fi (增强) ──
    var wifiStandard: String = "",           // Wi-Fi 4 / Wi-Fi 5 / Wi-Fi 6 / Wi-Fi 6E / Wi-Fi 7 (新增)
    var wifiStandardSource: String = "",     // 来源标记
    var wifi6EEnabled: Boolean = false,       // 6GHz 频段支持 (新增)
    var wifiAware: Boolean = false,           // Wi-Fi Aware (NAN) (新增)

    // ── Media Codecs ──
    var audioCodecs: List<String> = emptyList(),
    var videoCodecs: List<String> = emptyList(),
    var hwAcceleratedCodecs: List<String> = emptyList(),  // v4: 硬件加速编解码器

    // ── DRM ──
    var widevineLevel: String = "",
    var drmSchemes: List<String> = emptyList(),

    // ── Telephony / SIM ──
    var simOperator: String = "",
    var simMccMnc: String = "",
    var networkCountryIso: String = "",
    var phoneType: String = "",
    var isDualSim: Boolean = false,

    // ── Audio (音频能力) ──
    var stereoSpeakers: Boolean = false,
    var audioSampleRate: String = "",
    var audioOutputChannels: String = "",
    var supportsHiResAudio: Boolean = false,
    var headphoneJack: Boolean = false,
    var audioFormats: List<String> = emptyList(),

    // ── Input / Touch ──
    var touchscreenType: String = "",
    var hasKeyboard: Boolean = false,

    // ── Camera (相机) ──
    var cameraIds: List<String> = emptyList(),
    var cameraSensors: List<CameraSensorInfo> = emptyList(),

    // ── Thermal (新增) ──
    var thermalZoneCount: Int = 0,            // 热区传感器总数
    var thermalZoneTypes: List<String> = emptyList(),  // 热区类型列表

    // ── Security (安全) ──
    var teeSupported: Boolean = false,
    var secureBootEnabled: Boolean = false,
    var fileEncryption: String = "",
    var selinuxEnforcing: Boolean = false,
    var bootloaderUnlocked: Boolean = false,

    // ── NFC ──
    var hasNfc: Boolean = false,

    // ── Additional Connectivity ──
    var hasInfrared: Boolean = false,
    var hasFmRadio: Boolean = false,
    var hasUwb: Boolean = false,
    var hasWirelessCharging: Boolean = false,

    // ── Charging ──
    var usbConnected: Boolean = false,

    // ── Identifiers (可重置标识符) ──
    var androidId: String = "",              // Android ID (Settings.Secure.ANDROID_ID, 64-bit hex)
    var serialNumber: String = "",           // 序列号 (Build.getSerial(), API 26+ 需权限)
    var hardwareSerial: String = "",          // ro.serialno 硬件序列号
    var deviceFingerprint: String = "",       // Build.FINGERPRINT 设备指纹

    // ── Runtime Environment (运行环境) ──
    var javaRuntimeVersion: String = "",     // JDK 版本号 + 供应商 e.g. "17.0.13 (Android)"
    var javaVmName: String = "",             // VM 名称 e.g. "Dalvik" / "ART"
    var opensslVersion: String = "",         // OpenSSL 版本 e.g. "OpenSSL 3.0.2 15 Mar 2022"
    var buildTimestamp: String = ""          // APK 构建时间 e.g. "2026-06-13 20:30:00 UTC"
)

/**
 * 相机传感器详细信息
 */
data class CameraSensorInfo(
    val id: String = "",
    var facing: String = "",
    val sensorModel: String = "",
    var aperture: String = "",
    var focalLength: String = "",
    var pixelSize: String = "",
    var resolution: String = "",
    var oisSupported: Boolean = false,
    var eisSupported: Boolean = false,
    var flashSupported: Boolean = false
)
