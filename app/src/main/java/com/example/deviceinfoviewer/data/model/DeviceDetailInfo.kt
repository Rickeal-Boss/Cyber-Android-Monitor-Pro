package com.example.deviceinfoviewer.data.model

/**
 * 设备详细信息 — 无 root 可获取的全面设备信息
 */
data class DeviceDetailInfo(
    // Display
    var resolution: String = "",
    var densityDpi: Int = 0,
    var density: Float = 0f,
    var refreshRateHz: Float = 0f,
    var hdrCapabilities: List<String> = emptyList(),
    var physicalSizeInches: Float = 0f,

    // Graphics (OpenGL ES)
    var glEsVersion: String = "",
    var glVendor: String = "",
    var glRenderer: String = "",
    var glExtensions: List<String> = emptyList(),

    // Vulkan
    var vulkanVersion: String = "",            // Vulkan API 版本号
    var vulkanApiLevel: String = "",           // Vulkan 硬件特性级别
    var rayTracingSupported: Boolean = false,  // 硬件光线追踪支持

    // Media Codecs
    var audioCodecs: List<String> = emptyList(),
    var videoCodecs: List<String> = emptyList(),

    // DRM
    var widevineLevel: String = "",
    var drmSchemes: List<String> = emptyList(),

    // Telephony
    var simOperator: String = "",
    var simMccMnc: String = "",
    var networkCountryIso: String = "",
    var phoneType: String = "",
    var isDualSim: Boolean = false,

    // Bluetooth
    var bluetoothName: String = "",
    var bluetoothAddress: String = "",
    var bluetoothSupported: Boolean = false,

    // Input / Touch
    var touchscreenType: String = "",
    var hasKeyboard: Boolean = false,

    // Camera
    var cameraIds: List<String> = emptyList(),

    // NFC
    var hasNfc: Boolean = false,

    // Charging
    var usbConnected: Boolean = false
)
