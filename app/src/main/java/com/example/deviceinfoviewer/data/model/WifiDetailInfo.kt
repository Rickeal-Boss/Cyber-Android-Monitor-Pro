package com.example.deviceinfoviewer.data.model

data class WifiDetailInfo(
    var ssid: String = "",
    var bssid: String = "",
    var signalDbm: Int = Int.MIN_VALUE,
    var linkSpeedMbps: Int = -1,
    var frequencyMHz: Int = -1,              // WiFi 频率 (MHz) — 2.4G/5G/6G
    var channelWidth: String = "",           // 信道宽度: 20/40/80/160 MHz
    var wifiStandard: String = "",           // WiFi 标准: WiFi 4/5/6/6E/7
    var ipv4: String = "",
    var ipv6: String = "",
    var macAddress: String = "",
    var gateway: String = "",
    var dns: String = "",
    var subnetMask: String = "",
    var nearbyAps: List<String> = emptyList(),  // 附近 AP 列表
    var chipTemperatureCelsius: Float = Float.NaN,  // WiFi 芯片温度 (from dumpsys wifi)
    var powerSaveMode: String = ""             // 省电模式状态 (from dumpsys wifi)
)
