package com.example.deviceinfoviewer.data.model

data class GpsStatusInfo(
    var gpsEnabled: Boolean = false,
    var fixAcquired: Boolean = false,
    var latitude: Double = Double.NaN,
    var longitude: Double = Double.NaN,
    var accuracy: Float = Float.NaN,
    var satelliteCount: Int = 0,
    var fixSatelliteCount: Int = 0,
    var satellites: MutableList<GpsSatelliteInfo> = mutableListOf()
)
