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
    var rssi: Int = Int.MIN_VALUE
)
