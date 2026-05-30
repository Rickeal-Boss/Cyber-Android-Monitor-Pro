package com.example.deviceinfoviewer.data.model

data class GpsSatelliteInfo(
    var prn: Int = -1,
    var constellation: String = "",
    var snr: Float = Float.NaN,
    var elevation: Float = Float.NaN,
    var azimuth: Float = Float.NaN,
    var usedInFix: Boolean = false
)
