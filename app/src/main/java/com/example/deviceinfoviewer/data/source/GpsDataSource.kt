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
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.deviceinfoviewer.data.model.GpsSatelliteInfo
import com.example.deviceinfoviewer.data.model.GpsStatusInfo

/**
 * GPS 数据源 v3 — 修复 Android 16 兼容 + 多策略卫星检测
 *
 * 修复要点：
 * 1. API 30+ 直接使用 Executor-based registerGnssStatusCallback（跳过反射）
 * 2. Handler 参数使用 Looper.getMainLooper() 而非 null
 * 3. 修复位置更新覆盖卫星数据的时序问题
 * 4. 增加权限诊断日志
 * 5. 增加 ACCESS_BACKGROUND_LOCATION 检测
 */
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

    // 保存最近一次真实数据用于恢复（防止位置更新覆盖卫星数据）
    private var lastRealStatus: GpsStatusInfo? = null
    // 锁保护 lastRealStatus 读写
    private val statusLock = Any()

    // API 30+ 直接使用 GnssStatus.Callback（非反射）
    private var directGnssCallback: android.location.GnssStatus.Callback? = null

    fun interface GpsCallback {
        fun onGpsStatusUpdate(statusInfo: GpsStatusInfo)
    }

    @Suppress("MissingPermission")
    fun startListening(callback: GpsCallback) {
        val lm = locationManager ?: return
        if (listening) return
        listening = true

        // 检查定位权限
        val hasFineLocation = ContextCompat.checkSelfPermission(appContext,
            Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarseLocation = ContextCompat.checkSelfPermission(appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        
        // 检查后台定位权限（Android 10+）
        val hasBackgroundLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(appContext,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else true

        Log.d("GpsDS", "startListening: fine=$hasFineLocation coarse=$hasCoarseLocation bg=$hasBackgroundLocation")

        // 输出诊断信息
        if (!hasFineLocation) {
            Log.w("GpsDS", "ACCESS_FINE_LOCATION not granted — GPS satellite data will NOT be available")
            Log.w("GpsDS", "READ_PHONE_STATE=" + (ContextCompat.checkSelfPermission(appContext,
                Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED))
        }
        if (!hasBackgroundLocation && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Log.w("GpsDS", "ACCESS_BACKGROUND_LOCATION not granted — may affect satellite callbacks")
        }
        Log.d("GpsDS", "SDK_INT=${Build.VERSION.SDK_INT}, BRAND=${Build.BRAND}, MODEL=${Build.MODEL}")

        try {
            val providerEnabled = try {
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
            } catch (_: Throwable) { false }

            // 发送初始状态
            val info = GpsStatusInfo()
            info.gpsEnabled = providerEnabled && hasFineLocation
            info.fixAcquired = false
            callback.onGpsStatusUpdate(info)
            lastKnownEnabled = info.gpsEnabled

            if (!info.gpsEnabled) {
                Log.w("GpsDS", "GPS disabled or no fine location permission")
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
                    li.speedMps = if (lastLoc.hasSpeed()) lastLoc.speed else -1f
                    callback.onGpsStatusUpdate(li)
                    synchronized(statusLock) { lastRealStatus = li }
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
                    li.speedMps = if (location.hasSpeed()) location.speed else -1f
                    // 保留卫星数据（线程安全）
                    synchronized(statusLock) {
                        lastRealStatus?.satellites?.let { li.satellites = it }
                        lastRealStatus?.satelliteCount?.let { li.satelliteCount = it }
                        li.fixSatelliteCount = lastRealStatus?.fixSatelliteCount ?: 0
                        lastRealStatus = li
                    }
                    lastKnownEnabled = true
                    Log.d("GpsDS", "onLocationChanged: ${location.latitude},${location.longitude} " +
                            "sats=${li.satelliteCount} fix=${li.fixSatelliteCount}")
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

            // ── 先激活 GPS 芯片：请求位置更新必须在注册 GNSS 回调之前 ──
            // 重要：某些设备上 GPS 芯片需要先开始定位才广播卫星状态
            try {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f,
                    locationListener!!, Looper.getMainLooper())
            } catch (t: Throwable) {
                Log.w("GpsDS", "GPS_PROVIDER requestLocationUpdates failed", t)
            }
            // 额外 NETWORK 更新源（改善首次定位速度）
            try {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000L, 0f,
                    locationListener!!, Looper.getMainLooper())
            } catch (_: Throwable) {}
            // PASSIVE 更新源
            try {
                lm.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 2000L, 0f,
                    locationListener!!, Looper.getMainLooper())
            } catch (_: Throwable) {}

            // ── 然后注册 GNSS 状态回调（GPS 芯片已激活）──
            // 策略 1: API 30+ 直接 GnssStatus.Callback
            val hasDirect = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && tryDirectGnssCallback(lm, callback)
            if (hasDirect) Log.d("GpsDS", "GNSS callback registered (API 30+ direct)")

            // 策略 2: API 24-29 反射 GnssStatus.Callback（仅当策略1未使用）
            val hasReflected = !hasDirect && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && tryGnssCallback(lm, callback)
            if (hasReflected) Log.d("GpsDS", "GNSS callback registered (reflected)")

            // 策略 3: 始终注册 GpsStatus.Listener 作为冗余备份
            // 某些 OEM ROM (ColorOS/HyperOS) 上 GnssStatus.Callback 可能静默失效
            runCatching {
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
                        synchronized(statusLock) {
                            lastRealStatus?.let { r ->
                                if (!r.latitude.isNaN()) {
                                    si.latitude = r.latitude
                                    si.longitude = r.longitude
                                    si.accuracy = r.accuracy
                                }
                            }
                            lastRealStatus = si
                        }
                        lastKnownEnabled = true
                        Log.d("GpsDS", "GpsStatus.Listener: ${satellites.size} sats, fix=$usedCount")
                        callback.onGpsStatusUpdate(si)
                    } catch (_: Throwable) {}
                }
                lm.addGpsStatusListener(gpsListener!!)
            }

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
     * API 30+ 直接使用 android.location.GnssStatus.Callback（非反射）
     * 这是最高优先级方案，因为反射在某些 Android 16 ROM 上可能失败
     */
    private fun tryDirectGnssCallback(lm: LocationManager, callback: GpsCallback): Boolean {
        return try {
            val gnssCallback = object : android.location.GnssStatus.Callback() {
                override fun onSatelliteStatusChanged(status: android.location.GnssStatus) {
                    try {
                        val si = GpsStatusInfo()
                        si.gpsEnabled = true
                        val count = status.satelliteCount
                        si.satelliteCount = count
                        val satellites = mutableListOf<GpsSatelliteInfo>()
                        var usedCount = 0

                        for (i in 0 until count) {
                            val svid = status.getSvid(i)
                            var conType = -1
                            var conName = "?"

                            // API 26+ 才有 getConstellationType
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                try {
                                    conType = status.getConstellationType(i)
                                    conName = GpsSatelliteInfo.constellationLabel(conType)
                                } catch (_: Throwable) {}
                            }

                            // 从 SVID + 星座类型推算 PRN
                            val prn = if (conType > 0) svidToPrn(svid, conType) else -svid

                            val sat = GpsSatelliteInfo(
                                prn = prn,
                                constellation = conName,
                                constellationType = conType,
                                snr = status.getCn0DbHz(i),
                                elevation = status.getElevationDegrees(i),
                                azimuth = status.getAzimuthDegrees(i),
                                usedInFix = status.usedInFix(i)
                            )
                            if (sat.usedInFix) usedCount++
                            satellites.add(sat)
                        }
                        si.satellites = satellites
                        si.fixSatelliteCount = usedCount
                        si.fixAcquired = usedCount > 0
                        // 保留坐标（线程安全）
                        synchronized(statusLock) {
                            lastRealStatus?.let { r ->
                                if (!r.latitude.isNaN()) {
                                    si.latitude = r.latitude
                                    si.longitude = r.longitude
                                    si.accuracy = r.accuracy
                                }
                            }
                            lastRealStatus = si
                        }
                        lastKnownEnabled = true
                        Log.d("GpsDS", "Direct GNSS: ${satellites.size} sats, fix=$usedCount")
                        callback.onGpsStatusUpdate(si)
                    } catch (t: Throwable) {
                        Log.e("GpsDS", "Direct GNSS callback error", t)
                    }
                }

                override fun onStarted() {
                    Log.d("GpsDS", "Direct GNSS callback: onStarted")
                }

                override fun onStopped() {
                    Log.d("GpsDS", "Direct GNSS callback: onStopped")
                }

                override fun onFirstFix(ttffMillis: Int) {
                    Log.d("GpsDS", "Direct GNSS callback: onFirstFix ttff=$ttffMillis")
                }
            }
            directGnssCallback = gnssCallback

            // API 30+ 使用 Handler 重载（已被标记 @Deprecated，但仍然可用且兼容）
            // 避免 Executor SAM 转换的编译问题
            val handler = Handler(Looper.getMainLooper())
            @Suppress("DEPRECATION")
            lm.registerGnssStatusCallback(gnssCallback, handler)
            true
        } catch (t: Throwable) {
            Log.w("GpsDS", "tryDirectGnssCallback failed: ${t.message}")
            false
        }
    }

    /**
     * 通过反射设置 GnssStatus.Callback（API 24+，含星座类型检测）
     * 作为 API 30+ 直接方案的备用
     */
    @Suppress("SameParameterValue")
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
                        // 保留坐标（线程安全）
                        synchronized(statusLock) {
                            lastRealStatus?.let { r ->
                                if (!r.latitude.isNaN()) {
                                    si.latitude = r.latitude
                                    si.longitude = r.longitude
                                    si.accuracy = r.accuracy
                                }
                            }
                            lastRealStatus = si
                        }
                        lastKnownEnabled = true
                        Log.d("GpsDS", "Reflected GNSS: ${satellites.size} sats, fix=$usedCount")
                    } catch (t: Throwable) {
                        Log.e("GpsDS", "Reflected GNSS callback error", t)
                    }
                    callback.onGpsStatusUpdate(si)
                }
                null
            }
            gnssCallback = gnssCallbackInstance

            // 重要修复：传递 Looper.getMainLooper() 而非 null
            // 部分 Android 版本在 Handler=null 时注册失败
            val handler = Handler(Looper.getMainLooper())
            try {
                val registerMethod = LocationManager::class.java.getMethod(
                    "registerGnssStatusCallback", callbackClass, android.os.Handler::class.java)
                registerMethod.invoke(lm, gnssCallbackInstance, handler)
            } catch (_: NoSuchMethodException) {
                // API 30+ 可能的另一个签名
                try {
                    val registerMethod = LocationManager::class.java.getMethod(
                        "registerGnssStatusCallback", callbackClass)
                    registerMethod.invoke(lm, gnssCallbackInstance)
                } catch (_: NoSuchMethodException) {
                    Log.w("GpsDS", "No registerGnssStatusCallback method found")
                    return false
                }
            }
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

    fun stopListening() {
        listening = false
        locationManager?.let { lm ->
            try {
                // 注销直接 GnssStatus.Callback (API 30+)
                if (directGnssCallback != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    lm.unregisterGnssStatusCallback(directGnssCallback!!)
                    directGnssCallback = null
                    Log.d("GpsDS", "Unregistered direct GNSS callback")
                }
            } catch (_: Throwable) {}
            try {
                // 注销反射 GnssStatus.Callback
                if (gnssCallback != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val callbackClass = Class.forName("android.location.GnssStatus\$Callback")
                    val unregisterMethod = LocationManager::class.java.getMethod(
                        "unregisterGnssStatusCallback", callbackClass)
                    unregisterMethod.invoke(lm, gnssCallback)
                    gnssCallback = null
                    Log.d("GpsDS", "Unregistered reflected GNSS callback")
                }
            } catch (_: Throwable) {}
            try {
                if (gpsListener != null) {
                    lm.removeGpsStatusListener(gpsListener!!)
                    gpsListener = null
                }
            } catch (_: Throwable) {}
            try {
                if (locationListener != null) {
                    lm.removeUpdates(locationListener!!)
                    locationListener = null
                }
            } catch (_: Throwable) {}
        }
        synchronized(statusLock) { lastRealStatus = null }
    }
}
