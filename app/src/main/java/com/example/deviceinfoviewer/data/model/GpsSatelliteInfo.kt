package com.example.deviceinfoviewer.data.model

data class GpsSatelliteInfo(
    var prn: Int = -1,
    var constellation: String = "",
    var constellationType: Int = -1,
    var snr: Float = Float.NaN,
    var elevation: Float = Float.NaN,
    var azimuth: Float = Float.NaN,
    var usedInFix: Boolean = false
) {
    companion object {
        // GnssStatus constellation constants (from android.location.GnssStatus)
        const val CONSTELLATION_GPS = 1
        const val CONSTELLATION_SBAS = 2
        const val CONSTELLATION_GLONASS = 3
        const val CONSTELLATION_QZSS = 4
        const val CONSTELLATION_BEIDOU = 5
        const val CONSTELLATION_GALILEO = 6
        const val CONSTELLATION_IRNSS = 7

        /** 根据 PRN 范围推断星座（旧 API 回退方案） */
        fun constellationFromPrn(prn: Int): Pair<String, Int> = when (prn) {
            in 1..32       -> "GPS"       to CONSTELLATION_GPS
            in 65..96      -> "GLONASS"   to CONSTELLATION_GLONASS
            in 120..158     -> "SBAS"      to CONSTELLATION_SBAS
            in 193..199     -> "QZSS"      to CONSTELLATION_QZSS
            in 201..237     -> "BeiDou"    to CONSTELLATION_BEIDOU
            in 301..336     -> "Galileo"   to CONSTELLATION_GALILEO
            in 401..414     -> "NavIC"     to CONSTELLATION_IRNSS
            else           -> "Unknown"   to -1
        }

        /** 星座类型缩写（用于天空图图例） */
        fun constellationLabel(type: Int): String = when (type) {
            CONSTELLATION_GPS     -> "GPS"
            CONSTELLATION_SBAS    -> "SBAS"
            CONSTELLATION_GLONASS -> "GLO"
            CONSTELLATION_QZSS    -> "QZSS"
            CONSTELLATION_BEIDOU  -> "BDS"
            CONSTELLATION_GALILEO -> "GAL"
            CONSTELLATION_IRNSS   -> "IRN"
            else                  -> "?"
        }
    }
}
