package com.example.deviceinfoviewer.data.model

data class GpsStatusInfo(
    var gpsEnabled: Boolean = false,
    var fixAcquired: Boolean = false,
    var latitude: Double = Double.NaN,
    var longitude: Double = Double.NaN,
    var accuracy: Float = Float.NaN,
    /** 卫星定位速度 (m/s), 有速度数据时 ≥ 0 */
    var speedMps: Float = -1f,
    var satelliteCount: Int = 0,
    var fixSatelliteCount: Int = 0,
    var satellites: MutableList<GpsSatelliteInfo> = mutableListOf()
)
