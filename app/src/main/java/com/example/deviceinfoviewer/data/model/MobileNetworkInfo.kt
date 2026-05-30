package com.example.deviceinfoviewer.data.model

data class MobileNetworkInfo(
    var networkType: String = "",
    var operatorName: String = "",
    var mccMnc: String = "",
    var signalStrengthDbm: Int = Int.MIN_VALUE,
    var isRoaming: Boolean = false
)
