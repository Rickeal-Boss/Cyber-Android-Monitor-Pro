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
        } catch (_: UnknownHostException) { "" }
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

    private fun scanNearbyAps(): List<String> {
        return try {
            // 先触发一次扫描 — 非连接状态下系统不主动扫描，缓存为空
            triggerWifiScan()

            val wm = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return emptyList()

            // API 读取 (GPS 开启 + 权限已授时可用)
            val apiResults = wm.scanResults ?: emptyList()
            Log.d(TAG, "scanResults from API: ${apiResults.size} APs")

            if (apiResults.isNotEmpty()) {
                return apiResults.take(5).map { r ->
                    (r.SSID.ifEmpty { "<hidden>" }) + ": " + r.level + "dBm"
                }
            }

            // API 返回空 → fallback 链
            Log.d(TAG, "API scanResults empty — fallback chain")

            // 策略 1: cmd wifi list-scan-results (API 31+, 直接读 WiFi HAL 缓存)
            val cmdResults = scanNearbyApsViaCmdWifi()
            if (cmdResults.isNotEmpty()) return cmdResults

            // 策略 2: dumpsys wifi 解析 (全版本兼容)
            scanNearbyApsViaDumpsys()
        } catch (e: Throwable) {
            Log.w(TAG, "scanNearbyAps failed", e)
            emptyList()
        }
    }

    /**
     * 触发一次 WiFi 扫描 — 命令行优先 (绕过 API 限制)，WifiManager 兜底
     * 非连接状态下系统不主动扫描，无此步骤则缓存永远为空
     */
    private fun triggerWifiScan() {
        // 优先: cmd wifi start-scan (shell 权限，不受 app 限制)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                ShellCommandDataSource.exec("/system/bin/cmd", "wifi", "start-scan")
                Log.d(TAG, "cmd wifi start-scan triggered")
                return  // shell 方式成功，跳过 API 方式
            } catch (e: Throwable) {
                Log.d(TAG, "cmd wifi start-scan failed, trying API: ${e.message}")
            }
        }
        // 兜底: WifiManager.startScan() (API 28- 工作，API 29+ 受限)
        tryWifiManagerStartScan()
    }

    @Suppress("DEPRECATION")
    private fun tryWifiManagerStartScan() {
        try {
            val wm = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                wm.startScan()
                Log.d(TAG, "WifiManager.startScan() triggered")
            } else {
                Log.d(TAG, "API 29+ — WifiManager.startScan() not called (restricted by system)")
            }
        } catch (e: Throwable) {
            Log.w(TAG, "startScan failed", e)
        }
    }

    /**
     * Fallback 1: cmd wifi list-scan-results (API 31+)
     * 直接读取 WiFi 服务内部缓存的扫描结果，绕过 location 权限强制要求
     */
    private fun scanNearbyApsViaCmdWifi(): List<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return emptyList()
        return try {
            val raw = ShellCommandDataSource.exec("/system/bin/cmd", "wifi", "list-scan-results")
            if (raw.isBlank()) return emptyList()
            Log.d(TAG, "cmd wifi list-scan-results: ${raw.length} chars")

            // 格式: "  xx:xx:xx:xx:xx:xx  2462  -45  2.3  SSID Name"
            val pattern = Regex(
                """([0-9a-fA-F:]{17})\s+\d+\s+(-\d+)\s+\d+\.?\d*\s+(.+)""",
                RegexOption.MULTILINE
            )
            val aps = pattern.findAll(raw).map { m ->
                val ssid = m.groupValues[3].trim().removeSurrounding("\"")
                val rssi = m.groupValues[2]
                "${ssid.ifEmpty { "<hidden>" }}: ${rssi}dBm"
            }.toList()

            Log.d(TAG, "cmd wifi parsed: ${aps.size} APs")
            aps.take(5)
        } catch (e: Throwable) {
            Log.w(TAG, "cmd wifi fallback failed", e)
            emptyList()
        }
    }

    /**
     * Fallback 2: 从 dumpsys wifi 输出解析附近 AP 列表
     * 支持 AOSP 12+ 表格格式 + OEM 老旧 SSID/level 格式
     */
    private fun scanNearbyApsViaDumpsys(): List<String> {
        return try {
            val raw = ShellCommandDataSource.getDumpsysWifi()
            if (raw.isBlank()) return emptyList()

            // 格式1 (AOSP 12+): "Latest scan results:" 表格
            //   xx:xx:xx:xx:xx:xx  2462  -45  2.3  MyNetwork
            val tablePattern = Regex(
                """([0-9a-fA-F:]{17})\s+\d+\s+(-\d+)\s+\d+\.?\d*\s+(.+)""",
                RegexOption.MULTILINE
            )
            val startMarker = raw.indexOf("Latest scan results")
            val tableSection = if (startMarker >= 0) raw.substring(startMarker) else raw

            val aps = mutableListOf<String>()
            for (match in tablePattern.findAll(tableSection)) {
                val ssid = match.groupValues[3].trim().removeSurrounding("\"")
                val rssi = match.groupValues[2]
                aps.add("${ssid.ifEmpty { "<hidden>" }}: ${rssi}dBm")
            }

            if (aps.isNotEmpty()) {
                Log.d(TAG, "dumpsys parsed: ${aps.size} APs")
                return aps.take(5)
            }

            // 格式2 (老旧 OEM): "SSID: xxx, BSSID: xxx, level: -xx"
            val legacyPattern = Regex("""SSID:\s*"?([^",\n]+)"?\s*[,\n].*?(?:level|RSSI|rssi):\s*(-?\d+)""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
            for (match in legacyPattern.findAll(raw)) {
                val ssid = match.groupValues[1].trim()
                val rssi = match.groupValues[2]
                aps.add("${ssid.ifEmpty { "<hidden>" }}: ${rssi}dBm")
            }

            Log.d(TAG, "dumpsys legacy parsed: ${aps.size} APs")
            aps.take(5)
        } catch (e: Throwable) {
            Log.w(TAG, "dumpsys AP parse failed", e)
            emptyList()
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
