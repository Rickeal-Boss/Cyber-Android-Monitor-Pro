package com.example.deviceinfoviewer.data.model

data class MobileNetworkInfo(
    var networkType: String = "",
    var operatorName: String = "",
    var mccMnc: String = "",
    var signalStrengthDbm: Int = Int.MIN_VALUE,
    var isRoaming: Boolean = false,

    // ── 5G / LTE 小区详情 ──
    var cellId: Long = -1L,
    var pci: Int = -1,
    var band: String = "",
    var arfcn: Int = -1,
    var dlBandwidth: String = "",
    var ulConfigured: String = "",
    var rsrp: Int = Int.MIN_VALUE,
    var rsrq: Int = Int.MIN_VALUE,
    var sinr: Int = Int.MIN_VALUE,
    var rssi: Int = Int.MIN_VALUE,

    // ── 分制式信号强度 dBm ──
    var nrSignalDbm: Int = Int.MIN_VALUE,    // NR 5G 独立信号强度
    var lteSignalDbm: Int = Int.MIN_VALUE,   // LTE 4G 独立信号强度
    var nrRsrp: Int = Int.MIN_VALUE,         // NR RSRP (dBm)
    var lteRsrp: Int = Int.MIN_VALUE         // LTE RSRP (dBm)
)
