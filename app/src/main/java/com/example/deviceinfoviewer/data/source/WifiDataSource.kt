package com.example.deviceinfoviewer.data.source

import android.content.Context
import android.net.DhcpInfo
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log

import com.example.deviceinfoviewer.data.model.WifiDetailInfo

import java.net.InetAddress
import java.net.UnknownHostException

/**
 * WiFi 数据源，通过 WifiManager 获取 WiFi 详细信息
 */
class WifiDataSource(private val context: Context) {

    private val appContext = context.applicationContext

    @Suppress("MissingPermission")
    fun getWifiDetail(): WifiDetailInfo {
        val info = WifiDetailInfo()

        val wm = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return info

        // AP 扫描前置：WiFi 开启即扫描，不依赖连接状态
        if (isWifiOn(wm)) {
            Log.d(TAG, "WiFi ON — starting AP scan (connected=${wm.connectionInfo?.ssid != null})")
            info.nearbyAps = scanNearbyAps()
        }

        val wifiInfo: WifiInfo = wm.connectionInfo ?: return info

        info.ssid = wifiInfo.ssid.replace("\"", "")
        info.bssid = wifiInfo.bssid
        info.signalDbm = wifiInfo.rssi
        info.linkSpeedMbps = wifiInfo.linkSpeed
        info.macAddress = wifiInfo.macAddress

        // WiFi 频率 & 标准检测 (Android 5.0+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            info.frequencyMHz = wifiInfo.frequency
            info.wifiStandard = detectWifiStandard(wifiInfo.frequency)
            info.channelWidth = detectChannelWidth(wifiInfo)
        }

        // IPv4 地址
        val ipInt = wifiInfo.ipAddress
        if (ipInt != 0) {
            info.ipv4 = formatIp(ipInt)
        }

        // DHCP 信息（网关、DNS、子网掩码）—— 可能为 null (WiFi 未连接)
        val dhcp: DhcpInfo? = wm.dhcpInfo
        if (dhcp != null) {
            info.gateway = formatIp(dhcp.gateway)
            info.dns = formatIp(dhcp.dns1)
            info.subnetMask = formatIp(dhcp.netmask)
        }

        // === P1: dumpsys wifi 芯片温度和省电模式 ===
        resolveDumpsysWifi(info)

        return info
    }

    /**
     * 从 dumpsys wifi 提取额外数据（芯片温度、省电模式）
     * 仅执行一次后缓存（dumpsys 开销较大）
     */
    @Volatile
    private var dumpsysWifiResolved = false
    @Volatile
    private var cachedWifiTemp = Float.NaN
    @Volatile
    private var cachedPowerSave = ""

    private fun resolveDumpsysWifi(info: WifiDetailInfo) {
        if (dumpsysWifiResolved) {
            info.chipTemperatureCelsius = cachedWifiTemp
            info.powerSaveMode = cachedPowerSave
            return
        }
        try {
            val wifiOutput = ShellCommandDataSource.getDumpsysWifi()
            cachedWifiTemp = ShellCommandDataSource.extractWifiTemperature(wifiOutput)
            cachedPowerSave = ShellCommandDataSource.extractWifiPowerSave(wifiOutput)
            dumpsysWifiResolved = true
        } catch (e: Throwable) {}
        info.chipTemperatureCelsius = cachedWifiTemp
        info.powerSaveMode = cachedPowerSave
    }

    private fun formatIp(ip: Int): String {
        if (ip == 0) {
            return ""
        }
        return try {
            val bytes = byteArrayOf(
                (ip and 0xFF).toByte(),
                ((ip shr 8) and 0xFF).toByte(),
                ((ip shr 16) and 0xFF).toByte(),
                ((ip shr 24) and 0xFF).toByte()
            )
            InetAddress.getByAddress(bytes).hostAddress ?: ""
        } catch (e: UnknownHostException) { "" }
    }

    companion object {
        private const val TAG = "WifiDS"
    }

    /**
     * WiFi 是否已开启 — wifiState 为主 (OEM ROM 兼容)，isWifiEnabled 为兜底
     */
    @Suppress("DEPRECATION")
    private fun isWifiOn(wm: WifiManager): Boolean {
        return try {
            val state = wm.wifiState
            if (state == WifiManager.WIFI_STATE_ENABLED) {
                Log.d(TAG, "isWifiOn: wifiState=ENABLED") ; true
            } else if (state == WifiManager.WIFI_STATE_ENABLING) {
                Log.d(TAG, "isWifiOn: wifiState=ENABLING — still starting") ; false
            } else {
                val enabled = wm.isWifiEnabled
                Log.d(TAG, "isWifiOn: wifiState=$state, isWifiEnabled=$enabled")
                enabled
            }
        } catch (e: Throwable) {
            Log.w(TAG, "isWifiOn failed", e) ; false
        }
    }

    // ═══════ 扫描结果缓存 (解决 API 29+ 限速：前台 app 2 分钟最多 4 次) ═══════
    // 扫描与轮询解耦：扫描间隔 ≥30s，轮询 (2s) 只读缓存
    @Volatile private var cachedAps: List<String> = emptyList()
    @Volatile private var lastScanTimestamp: Long = 0L
    private val scanIntervalMs = 15_000L       // 最小扫描间隔 15s (远低于 30s 硬上限)
    private val scanCooldownMs = 30_000L       // startScan 失败后冷却 30s

    private fun scanNearbyAps(): List<String> {
        val now = System.currentTimeMillis()
        val sinceLastScan = now - lastScanTimestamp

        // 缓存有效 → 直接返回
        if (cachedAps.isNotEmpty() && sinceLastScan < scanIntervalMs) {
            Log.d(TAG, "returning cached APs (age=${sinceLastScan}ms, count=${cachedAps.size})")
            return cachedAps
        }

        // 距上次扫描太近且之前失败 → 跳过本次
        if (cachedAps.isEmpty() && sinceLastScan < scanCooldownMs && lastScanTimestamp > 0) {
            Log.d(TAG, "scan cooldown active (${sinceLastScan}ms < ${scanCooldownMs}ms)")
            return emptyList()
        }

        lastScanTimestamp = now

        // 触发扫描并读取结果
        return try {
            val wm = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                ?: return emptyList()

            val scanTriggered = tryStartScan(wm)
            Log.d(TAG, "startScan triggered=$scanTriggered, SDK=${
                Build.VERSION.SDK_INT}")

            // 扫描需要时间完成，立即读取可能拿旧缓存；尝试 API 读取
            val results = wm.scanResults ?: emptyList()
            Log.d(TAG, "getScanResults: ${results.size} APs (triggered=$scanTriggered)")

            if (results.isNotEmpty()) {
                cachedAps = results.take(5).map { r ->
                    (r.SSID.ifEmpty { "<hidden>" }) + ": " + r.level + "dBm"
                }
                Log.d(TAG, "cached ${cachedAps.size} APs")
                return cachedAps
            }

            // startScan 成功但结果暂未就绪 → 保留旧缓存给下轮
            if (scanTriggered && cachedAps.isNotEmpty()) {
                Log.d(TAG, "scan triggered, returning stale cache (${cachedAps.size} APs)")
                return cachedAps
            }

            emptyList()
        } catch (e: Throwable) {
            Log.w(TAG, "scanNearbyAps failed", e)
            // 异常不清缓存，保留上次有效结果
            cachedAps.ifEmpty { emptyList() }
        }
    }

    /**
     * 触发 WiFi 扫描 — 带限速保护
     * API 29+ 需要 CHANGE_WIFI_STATE + ACCESS_FINE_LOCATION + 位置已开启
     */
    @Suppress("DEPRECATION", "MissingPermission")
    private fun tryStartScan(wm: WifiManager): Boolean {
        return try {
            val ok = wm.startScan()
            if (!ok) Log.d(TAG, "startScan() returned false (throttled or HW busy)")
            ok
        } catch (e: SecurityException) {
            Log.w(TAG, "startScan SecurityException — missing CHANGE_WIFI_STATE or location disabled", e)
            false
        } catch (e: Throwable) {
            Log.w(TAG, "startScan failed", e)
            false
        }
    }

    /**
     * 根据频率(MHz)检测 WiFi 标准
     * WiFi 4 (802.11n): 2.4G/5G
     * WiFi 5 (802.11ac): 5G only
     * WiFi 6 (802.11ax): 2.4G/5G/6G
     * WiFi 6E: 6GHz
     * WiFi 7 (802.11be): all bands
     */
    private fun detectWifiStandard(freqMhz: Int): String {
        return when {
            freqMhz in 5925..7125 -> "WiFi 6E (6GHz)"
            freqMhz in 5000..5895 -> {
                if (android.os.Build.VERSION.SDK_INT >= 29) "WiFi 6 (5GHz)" else "WiFi 5 (5GHz)"
            }
            freqMhz in 2400..2495 -> "WiFi 4/6 (2.4GHz)"
            freqMhz > 0 -> "$freqMhz MHz"
            else -> ""
        }
    }

    /**
     * 根据 linkSpeed 估算信道宽度
     * >866 Mbps → 160MHz
     * >433 Mbps → 80MHz
     * >150 Mbps → 40MHz
     * else → 20MHz
     */
    private fun detectChannelWidth(wifiInfo: android.net.wifi.WifiInfo): String {
        val speed = wifiInfo.linkSpeed
        return when {
            speed > 2400 -> "320 MHz"  // WiFi 7
            speed > 1200 -> "160 MHz"  // WiFi 6
            speed > 600 -> "80 MHz"    // WiFi 5/6
            speed > 200 -> "40 MHz"
            speed > 0 -> "20 MHz"
            else -> ""
        }
    }
}
