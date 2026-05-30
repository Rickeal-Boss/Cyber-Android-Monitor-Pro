package com.example.deviceinfoviewer.data.source

import android.content.Context
import android.location.GpsSatellite
import android.location.GpsStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import com.example.deviceinfoviewer.data.model.GpsSatelliteInfo
import com.example.deviceinfoviewer.data.model.GpsStatusInfo

class GpsDataSource(private val context: Context) {

    private val appContext = context.applicationContext
    private val locationManager: LocationManager? = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private var listening = false
    private var locationListener: LocationListener? = null
    private var gnssCallback: Any? = null  // GnssStatus.Callback via reflection
    private var gpsListener: GpsStatus.Listener? = null
    private var lastKnownEnabled: Boolean? = null

    fun interface GpsCallback {
        fun onGpsStatusUpdate(statusInfo: GpsStatusInfo)
    }

    @Suppress("MissingPermission")
    fun startListening(callback: GpsCallback) {
        val lm = locationManager ?: return
        if (listening) return
        listening = true

        try {
            // 立即检查 GPS 是否在系统级别启用
            val providerEnabled = try {
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
            } catch (_: Throwable) { false }

            if (!providerEnabled) {
                val info = GpsStatusInfo()
                info.gpsEnabled = false
                callback.onGpsStatusUpdate(info)
                lastKnownEnabled = false
            }

            locationListener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    val info = GpsStatusInfo()
                    info.gpsEnabled = true
                    info.fixAcquired = true
                    info.latitude = location.latitude
                    info.longitude = location.longitude
                    info.accuracy = location.accuracy
                    lastKnownEnabled = true
                    callback.onGpsStatusUpdate(info)
                }
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {
                    // GPS 被系统启用时通知 UI
                    lastKnownEnabled = true
                    val info = GpsStatusInfo()
                    info.gpsEnabled = true
                    callback.onGpsStatusUpdate(info)
                }
                override fun onProviderDisabled(provider: String) {
                    // GPS 被系统禁用时通知 UI
                    lastKnownEnabled = false
                    val info = GpsStatusInfo()
                    info.gpsEnabled = false
                    callback.onGpsStatusUpdate(info)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && tryGnssCallback(lm, callback)) {
                // GNSS callback set via reflection
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locationListener!!, Looper.getMainLooper())
            } else {
                // API 21-23 fallback
                gpsListener = GpsStatus.Listener { event ->
                    try {
                        @Suppress("DEPRECATION")
                        val gpsStatus = lm.getGpsStatus(null) ?: return@Listener
                        val info = GpsStatusInfo()
                        info.gpsEnabled = true
                        info.satelliteCount = gpsStatus.maxSatellites
                        val satellites = mutableListOf<GpsSatelliteInfo>()
                        var usedCount = 0
                        @Suppress("DEPRECATION")
                        for (sat in gpsStatus.satellites) {
                            val si = GpsSatelliteInfo()
                            si.prn = sat.prn
                            si.snr = sat.snr
                            si.elevation = sat.elevation
                            si.azimuth = sat.azimuth
                            si.usedInFix = sat.usedInFix()
                            if (sat.usedInFix()) usedCount++
                            satellites.add(si)
                        }
                        info.satellites = satellites
                        info.fixAcquired = usedCount > 0
                        lastKnownEnabled = true
                        callback.onGpsStatusUpdate(info)
                    } catch (_: Exception) {}
                }
                lm.addGpsStatusListener(gpsListener!!)
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locationListener!!, Looper.getMainLooper())
            }
        } catch (_: SecurityException) {
            val info = GpsStatusInfo()
            info.gpsEnabled = false
            callback.onGpsStatusUpdate(info)
        } catch (_: Throwable) {
            // 某些设备上 LocationManager 可能直接抛异常
        }
    }

    /**
     * 检查 GPS 启用状态（供 Repository 定期轮询）
     */
    fun checkGpsStatus(): GpsStatusInfo? {
        val lm = locationManager ?: return null
        val info = GpsStatusInfo()
        return try {
            info.gpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
            // 保留上次已知的卫星数据
            if (!info.gpsEnabled) {
                lastKnownEnabled = false
            }
            info
        } catch (_: Throwable) { null }
    }

    /**
     * 通过反射设置 GnssStatus.Callback，避免直接引用 GnssStatus 导致 API<24 类加载崩溃
     */
    private fun tryGnssCallback(lm: LocationManager, callback: GpsCallback): Boolean {
        return try {
            val gnssStatusClass = Class.forName("android.location.GnssStatus")
            val callbackClass = Class.forName("android.location.GnssStatus\$Callback")
            val gnssCallbackInstance = java.lang.reflect.Proxy.newProxyInstance(
                callbackClass.classLoader, arrayOf(callbackClass)
            ) { _, method, args ->
                if (method.name == "onSatelliteStatusChanged" && args != null && args.isNotEmpty()) {
                    val status = args[0]
                    val info = GpsStatusInfo()
                    info.gpsEnabled = true
                    try {
                        val getSatelliteCount = gnssStatusClass.getMethod("getSatelliteCount")
                        info.satelliteCount = getSatelliteCount.invoke(status) as Int
                        val getSvid = gnssStatusClass.getMethod("getSvid", Int::class.java)
                        val getConstellationType = gnssStatusClass.getMethod("getConstellationType", Int::class.java)
                        val getCn0DbHz = gnssStatusClass.getMethod("getCn0DbHz", Int::class.java)
                        val getElevation = gnssStatusClass.getMethod("getElevationDegrees", Int::class.java)
                        val getAzimuth = gnssStatusClass.getMethod("getAzimuthDegrees", Int::class.java)
                        val usedInFix = gnssStatusClass.getMethod("usedInFix", Int::class.java)
                        val satellites = mutableListOf<GpsSatelliteInfo>()
                        var usedCount = 0
                        val count = getSatelliteCount.invoke(status) as Int
                        for (i in 0 until count) {
                            val sat = GpsSatelliteInfo()
                            sat.snr = (getCn0DbHz.invoke(status, i) as? Float) ?: 0f
                            sat.elevation = (getElevation.invoke(status, i) as? Float) ?: 0f
                            sat.azimuth = (getAzimuth.invoke(status, i) as? Float) ?: 0f
                            sat.usedInFix = (usedInFix.invoke(status, i) as? Boolean) ?: false
                            if (sat.usedInFix) usedCount++
                            satellites.add(sat)
                        }
                        info.satellites = satellites
                        info.fixAcquired = usedCount > 0
                    } catch (_: Throwable) {}
                    lastKnownEnabled = true
                    callback.onGpsStatusUpdate(info)
                }
                null
            }
            gnssCallback = gnssCallbackInstance
            val registerMethod = LocationManager::class.java.getMethod("registerGnssStatusCallback",
                callbackClass, android.os.Handler::class.java)
            registerMethod.invoke(lm, gnssCallbackInstance, null)
            true
        } catch (_: Throwable) { false }
    }

    fun stopListening() {
        listening = false
        locationManager?.let { lm ->
            try {
                if (gnssCallback != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val callbackClass = Class.forName("android.location.GnssStatus\$Callback")
                    val unregisterMethod = LocationManager::class.java.getMethod("unregisterGnssStatusCallback", callbackClass)
                    unregisterMethod.invoke(lm, gnssCallback)
                    gnssCallback = null
                }
                if (gpsListener != null) {
                    lm.removeGpsStatusListener(gpsListener!!)
                    gpsListener = null
                }
                if (locationListener != null) {
                    lm.removeUpdates(locationListener!!)
                    locationListener = null
                }
            } catch (_: Throwable) {}
        }
    }
}
