package com.rb.cybermonitorpro.data.model

data class GpsStatusInfo(
    var gpsEnabled: Boolean = false,
    var fixAcquired: Boolean = false,
    var latitude: Double = Double.NaN,
    var longitude: Double = Double.NaN,
    var accuracy: Float = Float.NaN,
    /** 卫星定位速度 (m/s), 有速度数据时 ≥ 0 */
    var speedMps: Float = -1f,
    /** 海拔(米), 无定位数据时为 NaN(与 accuracy 的 NaN 风格一致; speedMps 的 -1f 哨兵保留不动, 见 BARO-02) */
    var altitude: Double = Double.NaN,
    var satelliteCount: Int = 0,
    var fixSatelliteCount: Int = 0,
    var satellites: MutableList<GpsSatelliteInfo> = mutableListOf()
)
