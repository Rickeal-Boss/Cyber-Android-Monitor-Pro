package com.example.deviceinfoviewer.data.source

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.GpsSatellite
import android.location.GpsStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.deviceinfoviewer.data.model.GpsSatelliteInfo
import com.example.deviceinfoviewer.data.model.GpsStatusInfo

class GpsDataSource(private val context: Context) {

    private val appContext = context.applicationContext
    private val locationManager: LocationManager? = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private var listening = false
    private var locationListener: LocationListener? = null
    private var gnssCallback: Any? = null
    private var gpsListener: GpsStatus.Listener? = null
    private var lastKnownEnabled: Boolean? = null

    // 反射缓存 — GnssStatus 方法
    private var reflectGetConstellationType: java.lang.reflect.Method? = null  // API 26+

    // 保存最近一次真实数据用于恢复
    private var lastRealStatus: GpsStatusInfo? = null

    fun interface GpsCallback {
        fun onGpsStatusUpdate(statusInfo: GpsStatusInfo)
    }

    @Suppress("MissingPermission")
    fun startListening(callback: GpsCallback) {
        val lm = locationManager ?: return
        if (listening) return
        listening = true

        // 检查定位权限
        val hasLocationPermission = ContextCompat.checkSelfPermission(appContext,
            Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        Log.d("GpsDS", "startListening, hasLocationPermission=$hasLocationPermission")

        try {
            val providerEnabled = try {
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
            } catch (_: Throwable) { false }

            // 发送初始状态
            val info = GpsStatusInfo()
            info.gpsEnabled = providerEnabled && hasLocationPermission
            info.fixAcquired = false
            callback.onGpsStatusUpdate(info)
            lastKnownEnabled = info.gpsEnabled

            if (!info.gpsEnabled) {
                Log.w("GpsDS", "GPS disabled or no permission")
                return
            }

            // 尝试获取被动定位（快速获取最后一次已知位置）
            try {
                val lastLoc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
                if (lastLoc != null) {
                    val li = GpsStatusInfo()
                    li.gpsEnabled = true
                    li.fixAcquired = true
                    li.latitude = lastLoc.latitude
                    li.longitude = lastLoc.longitude
                    li.accuracy = lastLoc.accuracy
                    callback.onGpsStatusUpdate(li)
                    lastRealStatus = li
                    Log.d("GpsDS", "Got last known location")
                }
            } catch (_: Throwable) {}

            // 位置监听器
            locationListener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    val li = GpsStatusInfo()
                    li.gpsEnabled = true
                    li.fixAcquired = true
                    li.latitude = location.latitude
                    li.longitude = location.longitude
                    li.accuracy = location.accuracy
                    // 保留卫星数据
                    lastRealStatus?.satellites?.let { li.satellites = it }
                    lastRealStatus?.satelliteCount?.let { li.satelliteCount = it }
                    li.fixSatelliteCount = lastRealStatus?.fixSatelliteCount ?: 0
                    lastRealStatus = li
                    lastKnownEnabled = true
                    Log.d("GpsDS", "onLocationChanged: ${location.latitude},${location.longitude}")
                    callback.onGpsStatusUpdate(li)
                }
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {
                    lastKnownEnabled = true
                    val ei = GpsStatusInfo()
                    ei.gpsEnabled = true
                    callback.onGpsStatusUpdate(ei)
                }
                override fun onProviderDisabled(provider: String) {
                    lastKnownEnabled = false
                    val di = GpsStatusInfo()
                    di.gpsEnabled = false
                    callback.onGpsStatusUpdate(di)
                }
            }

            // 策略 1: GNSS Callback (API 24+，反射)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && tryGnssCallback(lm, callback)) {
                Log.d("GpsDS", "Using GNSS callback")
            }
            // 策略 2: GpsStatus.Listener (API 21-23 或 GNSS 反射失败)
            else {
                Log.d("GpsDS", "Using GpsStatus.Listener")
                gpsListener = GpsStatus.Listener { event ->
                    try {
                        @Suppress("DEPRECATION")
                        val gpsStatus = lm.getGpsStatus(null) ?: return@Listener
                        val si = GpsStatusInfo()
                        si.gpsEnabled = true
                        val satellites = mutableListOf<GpsSatelliteInfo>()
                        var usedCount = 0
                        @Suppress("DEPRECATION")
                        for (sat in gpsStatus.satellites) {
                            val (conName, conType) = GpsSatelliteInfo.constellationFromPrn(sat.prn)
                            val s = GpsSatelliteInfo(
                                prn = sat.prn,
                                constellation = conName,
                                constellationType = conType,
                                snr = sat.snr,
                                elevation = sat.elevation,
                                azimuth = sat.azimuth,
                                usedInFix = sat.usedInFix()
                            )
                            if (sat.usedInFix()) usedCount++
                            satellites.add(s)
                        }
                        si.satellites = satellites
                        si.satelliteCount = satellites.size
                        si.fixSatelliteCount = usedCount
                        si.fixAcquired = usedCount > 0
                        // 保留坐标
                        lastRealStatus?.let { r ->
                            if (!r.latitude.isNaN()) { si.latitude = r.latitude; si.longitude = r.longitude; si.accuracy = r.accuracy }
                        }
                        lastRealStatus = si
                        lastKnownEnabled = true
                        Log.d("GpsDS", "GpsStatus.Listener: ${satellites.size} sats, fix=$usedCount")
                        callback.onGpsStatusUpdate(si)
                    } catch (_: Throwable) {}
                }
                lm.addGpsStatusListener(gpsListener!!)
            }

            // 始终请求位置更新（两种策略都需要）
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locationListener!!, Looper.getMainLooper())
            // 额外 PASSIVE 更新源
            try {
                lm.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 2000L, 0f, locationListener!!, Looper.getMainLooper())
            } catch (_: Throwable) {}

        } catch (_: SecurityException) {
            Log.w("GpsDS", "SecurityException - permission denied")
            val si = GpsStatusInfo()
            si.gpsEnabled = false
            callback.onGpsStatusUpdate(si)
        } catch (t: Throwable) {
            Log.e("GpsDS", "startListening failed", t)
        }
    }

    /**
     * 检查 GPS 启用状态（供 Repository 定期轮询）
     * 仅在 GPS 被禁用时返回非 null（避免空白数据覆盖真实卫星信息）
     */
    fun checkGpsStatus(): GpsStatusInfo? {
        val lm = locationManager ?: return null
        return try {
            val enabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
            if (!enabled) {
                lastKnownEnabled = false
                GpsStatusInfo().apply { gpsEnabled = false }
            } else {
                null
            }
        } catch (_: Throwable) { null }
    }

    /**
     * 通过反射设置 GnssStatus.Callback（完整版，含星座类型检测）
     */
    private fun tryGnssCallback(lm: LocationManager, callback: GpsCallback): Boolean {
        return try {
            val gnssStatusClass = Class.forName("android.location.GnssStatus")
            val callbackClass = Class.forName("android.location.GnssStatus\$Callback")

            // 预取反射方法
            val getSatelliteCount = gnssStatusClass.getMethod("getSatelliteCount")
            val getSvid = gnssStatusClass.getMethod("getSvid", Int::class.java)
            val getCn0DbHz = gnssStatusClass.getMethod("getCn0DbHz", Int::class.java)
            val getElevation = gnssStatusClass.getMethod("getElevationDegrees", Int::class.java)
            val getAzimuth = gnssStatusClass.getMethod("getAzimuthDegrees", Int::class.java)
            val usedInFix = gnssStatusClass.getMethod("usedInFix", Int::class.java)

            // getConstellationType 是 API 26+ 才有的
            try {
                reflectGetConstellationType = gnssStatusClass.getMethod("getConstellationType", Int::class.java)
            } catch (_: Throwable) {
                reflectGetConstellationType = null
                Log.d("GpsDS", "getConstellationType not available (API<26)")
            }

            val gnssCallbackInstance = java.lang.reflect.Proxy.newProxyInstance(
                callbackClass.classLoader, arrayOf(callbackClass)
            ) { _, method, args ->
                if (method.name == "onSatelliteStatusChanged" && args != null && args.isNotEmpty()) {
                    val status = args[0]
                    val si = GpsStatusInfo()
                    si.gpsEnabled = true
                    try {
                        val count = getSatelliteCount.invoke(status) as Int
                        si.satelliteCount = count
                        val satellites = mutableListOf<GpsSatelliteInfo>()
                        var usedCount = 0

                        for (i in 0 until count) {
                            val svid = (getSvid.invoke(status, i) as? Int) ?: -1
                            var conType = -1
                            var conName = "?"

                            // 尝试通过 getConstellationType 获取星座类型（API 26+）
                            if (reflectGetConstellationType != null) {
                                try {
                                    conType = reflectGetConstellationType!!.invoke(status, i) as? Int ?: -1
                                    conName = GpsSatelliteInfo.constellationLabel(conType)
                                } catch (_: Throwable) {}
                            }

                            // 从 SVID + 星座类型推算 PRN
                            val prn = if (conType > 0) svidToPrn(svid, conType) else -svid

                            // 回退到 PRN 范围检测（仅当无星座类型时）
                            if (conType < 0) {
                                // GNSS SVID 不是 PRN，无法精确检测星座 → 标记 Unknown
                                conName = "?"
                                conType = -1
                            }

                            val sat = GpsSatelliteInfo(
                                prn = prn,
                                constellation = conName,
                                constellationType = conType,
                                snr = (getCn0DbHz.invoke(status, i) as? Float) ?: 0f,
                                elevation = (getElevation.invoke(status, i) as? Float) ?: 0f,
                                azimuth = (getAzimuth.invoke(status, i) as? Float) ?: 0f,
                                usedInFix = (usedInFix.invoke(status, i) as? Boolean) ?: false
                            )
                            if (sat.usedInFix) usedCount++
                            satellites.add(sat)
                        }
                        si.satellites = satellites
                        si.fixSatelliteCount = usedCount
                        si.fixAcquired = usedCount > 0
                        // 保留坐标
                        lastRealStatus?.let { r ->
                            if (!r.latitude.isNaN()) { si.latitude = r.latitude; si.longitude = r.longitude; si.accuracy = r.accuracy }
                        }
                        lastRealStatus = si
                        lastKnownEnabled = true
                        Log.d("GpsDS", "GNSS callback: ${satellites.size} sats, fix=$usedCount")
                    } catch (_: Throwable) {}
                    callback.onGpsStatusUpdate(si)
                }
                null
            }
            gnssCallback = gnssCallbackInstance
            val registerMethod = LocationManager::class.java.getMethod("registerGnssStatusCallback",
                callbackClass, android.os.Handler::class.java)
            registerMethod.invoke(lm, gnssCallbackInstance, null)
            true
        } catch (t: Throwable) {
            Log.w("GpsDS", "tryGnssCallback failed: ${t.message}")
            false
        }
    }

    /**
     * SVID + 星座类型 → 标准 PRN 编号
     */
    private fun svidToPrn(svid: Int, constellationType: Int): Int = when (constellationType) {
        GpsSatelliteInfo.CONSTELLATION_GPS     -> svid
        GpsSatelliteInfo.CONSTELLATION_SBAS    -> svid + 119
        GpsSatelliteInfo.CONSTELLATION_GLONASS -> svid + 64
        GpsSatelliteInfo.CONSTELLATION_QZSS    -> svid + 192
        GpsSatelliteInfo.CONSTELLATION_BEIDOU  -> svid + 200
        GpsSatelliteInfo.CONSTELLATION_GALILEO -> svid + 300
        GpsSatelliteInfo.CONSTELLATION_IRNSS   -> svid + 400
        else -> svid
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
