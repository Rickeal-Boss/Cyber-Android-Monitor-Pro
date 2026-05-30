package com.example.deviceinfoviewer.data.model

data class SensorItemInfo(
    var name: String = "",
    var type: Int = -1,
    var vendor: String = "",
    var powerMa: Float = Float.NaN,
    var maxRange: Float = Float.NaN,
    var resolution: Float = Float.NaN,
    var minDelay: Int = -1
)
