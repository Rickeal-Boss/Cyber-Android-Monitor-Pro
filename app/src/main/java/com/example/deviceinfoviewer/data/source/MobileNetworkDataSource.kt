package com.example.deviceinfoviewer.data.source

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.*
import androidx.core.content.ContextCompat
import com.example.deviceinfoviewer.data.model.MobileNetworkInfo

/**
 * 移动网络数据源 — 支持 5G/LTE 小区级详细信息
 */
class MobileNetworkDataSource(private val context: Context) {

    private val appContext = context.applicationContext

    @Suppress("MissingPermission")
    fun getMobileNetworkInfo(): MobileNetworkInfo {
        val info = MobileNetworkInfo()

        val tm = appContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return info

        // 网络类型
        info.networkType = networkTypeToString(tm.networkType)

        // 运营商名称
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_PHONE_STATE)
            == PackageManager.PERMISSION_GRANTED) {
            info.operatorName = tm.networkOperatorName
            info.mccMnc = tm.networkOperator
            info.isRoaming = tm.isNetworkRoaming
        }

        // 信号强度 — 使用 CellSignalStrength.getDbm() 并验证范围
        val ss = tm.signalStrength
        if (ss != null) {
            try {
                val method = SignalStrength::class.java.getMethod("getDbm")
                val dbm = method.invoke(ss) as Int
                // 有效 dBm 范围: -130 ~ -30 (0=UNAVAILABLE, 99=UNKNOWN)
                info.signalStrengthDbm = if (dbm in -130..-30) dbm else Int.MIN_VALUE
            } catch (_: Throwable) {
                // 回退: 尝试 getCellSignalStrengths() (API 29+)
                val cellSigs = try {
                    ss.javaClass.getMethod("getCellSignalStrengths").invoke(ss) as? List<*>
                } catch (_: Throwable) { null }
                if (cellSigs != null && cellSigs.isNotEmpty()) {
                    val first = cellSigs.firstOrNull()
                    if (first != null) {
                        try {
                            val dbmMethod = first.javaClass.getMethod("getDbm")
                            val dbmVal = dbmMethod.invoke(first) as Int
                            info.signalStrengthDbm = if (dbmVal in -130..-30) dbmVal else Int.MIN_VALUE
                        } catch (_: Throwable) {
                            info.signalStrengthDbm = Int.MIN_VALUE
                        }
                    } else {
                        info.signalStrengthDbm = Int.MIN_VALUE
                    }
                } else {
                    info.signalStrengthDbm = Int.MIN_VALUE
                }
            }
        }

        // ── 5G/LTE 小区详细信息 (API 24+) ──
        collectCellInfo(tm, info)

        return info
    }

    @Suppress("MissingPermission", "DEPRECATION")
    private fun collectCellInfo(tm: TelephonyManager, info: MobileNetworkInfo) {
        try {
            val cellInfoList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                tm.allCellInfo
            } else {
                null
            } ?: return

            // 优先获取服务小区（registered cell）
            var foundServing = false
            for (cellInfo in cellInfoList) {
                if (cellInfo.isRegistered) {
                    parseCellInfo(cellInfo, info)
                    foundServing = true
                    break
                }
            }

            // 如果没有 registered cell，取第一个有信号强度的
            if (!foundServing) {
                for (cellInfo in cellInfoList) {
                    if (parseCellInfo(cellInfo, info)) break
                }
            }
        } catch (_: Throwable) {}
    }

    /**
     * 解析单个 CellInfo，填充 MobileNetworkInfo
     * @return true 如果成功提取到信号信息
     */
    private fun parseCellInfo(cellInfo: CellInfo, info: MobileNetworkInfo): Boolean {
        return when (cellInfo) {
            is CellInfoNr -> parseNr(cellInfo, info)
            is CellInfoLte -> parseLte(cellInfo, info)
            is CellInfoWcdma -> parseWcdma(cellInfo, info)
            is CellInfoGsm -> parseGsm(cellInfo, info)
            else -> false
        }
    }

    // ───────── 5G NR ─────────

    private fun parseNr(nr: CellInfoNr, info: MobileNetworkInfo): Boolean {
        try {
            val identity = nr.cellIdentity
            val signal = nr.cellSignalStrength

            info.cellId = try { (identity.javaClass.getMethod("getNci").invoke(identity) as? Number)?.toLong() ?: -1L } catch (_: Throwable) { -1L }
            info.pci = try { identity.javaClass.getMethod("getPci").invoke(identity) as? Int ?: -1 } catch (_: Throwable) { -1 }
            info.arfcn = try { identity.javaClass.getMethod("getNrarfcn").invoke(identity) as? Int ?: -1 } catch (_: Throwable) { -1 }

            // 频段 (API 33+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                try {
                    val bands = identity.javaClass.getMethod("getBands").invoke(identity) as? IntArray
                    if (bands != null && bands.isNotEmpty()) {
                        info.band = bands.joinToString("/") { nrBandToString(it) }
                    }
                } catch (_: Throwable) {}
            }

            // 带宽用 ARFCN 推算
            if (info.dlBandwidth.isEmpty()) estimateNrBandwidth(info.arfcn, info)

            // ── 5G NR 信号参数 ──
            // 3GPP TS 38.215: SS-RSRP 是 NR 的主信号强度指标 (范围 -156~-31 dBm)
            // CSI-RSRP 是辅助指标，不一定可用 → 作为 SS-RSRP 的备选
            val ssRsrp = try { signal.javaClass.getMethod("getSsRsrp").invoke(signal) as? Int ?: Int.MIN_VALUE } catch (_: Throwable) { Int.MIN_VALUE }
            val csiRsrp = try { signal.javaClass.getMethod("getCsiRsrp").invoke(signal) as? Int ?: Int.MIN_VALUE } catch (_: Throwable) { Int.MIN_VALUE }
            val ssRsrq = try { signal.javaClass.getMethod("getSsRsrq").invoke(signal) as? Int ?: Int.MIN_VALUE } catch (_: Throwable) { Int.MIN_VALUE }
            val csiRsrq = try { signal.javaClass.getMethod("getCsiRsrq").invoke(signal) as? Int ?: Int.MIN_VALUE } catch (_: Throwable) { Int.MIN_VALUE }
            val ssSinr = try { signal.javaClass.getMethod("getSsSinr").invoke(signal) as? Int ?: Int.MIN_VALUE } catch (_: Throwable) { Int.MIN_VALUE }
            val csiSinr = try { signal.javaClass.getMethod("getCsiSinr").invoke(signal) as? Int ?: Int.MIN_VALUE } catch (_: Throwable) { Int.MIN_VALUE }

            // NR 信号强度: SS-RSRP 优先 (3GPP 标准主指标)
            val primaryRsrp = if (ssRsrp != Int.MIN_VALUE) ssRsrp else csiRsrp
            info.rsrp = primaryRsrp
            info.nrRsrp = primaryRsrp
            info.nrSignalDbm = if (primaryRsrp != Int.MIN_VALUE) primaryRsrp else Int.MIN_VALUE

            // NR 信号质量: SS-RSRQ 优先
            info.rsrq = if (ssRsrq != Int.MIN_VALUE) ssRsrq else csiRsrq
            info.sinr = if (ssSinr != Int.MIN_VALUE) ssSinr else csiSinr
            info.ulConfigured = if (primaryRsrp != Int.MIN_VALUE) "Configured" else "Unknown"
            return info.rsrp != Int.MIN_VALUE
        } catch (_: Throwable) { return false }
    }

    // ───────── LTE ─────────

    private fun parseLte(lte: CellInfoLte, info: MobileNetworkInfo): Boolean {
        try {
            val identity = lte.cellIdentity
            val signal = lte.cellSignalStrength

            info.cellId = try { (identity.javaClass.getMethod("getCi").invoke(identity) as? Number)?.toLong() ?: -1L } catch (_: Throwable) { -1L }
            info.pci = try { identity.javaClass.getMethod("getPci").invoke(identity) as? Int ?: -1 } catch (_: Throwable) { -1 }
            info.band = lteBandToString(reflectBand(identity))
            info.arfcn = try { identity.javaClass.getMethod("getEarfcn").invoke(identity) as? Int ?: -1 } catch (_: Throwable) { -1 }

            val bw = try { identity.javaClass.getMethod("getBandwidth").invoke(identity) as? Int ?: -1 } catch (_: Throwable) { -1 }
            if (bw != CellInfo.UNAVAILABLE && bw > 0) info.dlBandwidth = "${bw} MHz"
            if (info.dlBandwidth.isEmpty()) estimateLteBandwidth(info.arfcn, reflectBand(identity), info)

            info.rsrp = try { signal.javaClass.getMethod("getRsrp").invoke(signal) as? Int ?: Int.MIN_VALUE } catch (_: Throwable) { Int.MIN_VALUE }
            info.rsrq = try { signal.javaClass.getMethod("getRsrq").invoke(signal) as? Int ?: Int.MIN_VALUE } catch (_: Throwable) { Int.MIN_VALUE }
            info.rssi = try { signal.javaClass.getMethod("getRssi").invoke(signal) as? Int ?: Int.MIN_VALUE } catch (_: Throwable) { Int.MIN_VALUE }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                info.sinr = try { signal.javaClass.getMethod("getRssnr").invoke(signal) as? Int ?: Int.MIN_VALUE } catch (_: Throwable) { Int.MIN_VALUE }
            }
            info.ulConfigured = if (info.rsrp != Int.MIN_VALUE) "Configured" else "Unknown"

            // 分制式信号强度 (LTE)
            info.lteRsrp = info.rsrp
            if (info.rsrp != Int.MIN_VALUE) {
                info.lteSignalDbm = info.rsrp  // RSRP 本身就是 dBm
            }
            return info.rsrp != Int.MIN_VALUE
        } catch (_: Throwable) { return false }
    }

    private fun reflectBand(identity: Any): Int {
        return try { identity.javaClass.getMethod("getBand").invoke(identity) as? Int ?: -1 } catch (_: Throwable) { -1 }
    }

    // ───────── WCDMA ─────────

    private fun parseWcdma(wcdma: CellInfoWcdma, info: MobileNetworkInfo): Boolean {
        try {
            val signal = wcdma.cellSignalStrength
            info.band = "WCDMA"
            info.rsrp = Int.MIN_VALUE
            info.rsrq = Int.MIN_VALUE
            info.rssi = signal.dbm
            return info.rssi != Int.MIN_VALUE
        } catch (_: Throwable) { return false }
    }

    // ───────── GSM ─────────

    private fun parseGsm(gsm: CellInfoGsm, info: MobileNetworkInfo): Boolean {
        try {
            val identity = gsm.cellIdentity
            val signal = gsm.cellSignalStrength
            info.cellId = identity.cid.toLong()
            info.band = "GSM"
            info.rssi = signal.dbm
            return info.rssi != Int.MIN_VALUE
        } catch (_: Throwable) { return false }
    }

    // ───────── 辅助方法 ─────────

    private fun networkTypeToString(networkType: Int): String = when (networkType) {
        TelephonyManager.NETWORK_TYPE_LTE -> "LTE (4G)"
        TelephonyManager.NETWORK_TYPE_NR -> "NR (5G)"
        TelephonyManager.NETWORK_TYPE_HSPAP -> "HSPA+"
        TelephonyManager.NETWORK_TYPE_HSDPA -> "HSDPA (3G)"
        TelephonyManager.NETWORK_TYPE_HSUPA -> "HSUPA (3G)"
        TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS (3G)"
        TelephonyManager.NETWORK_TYPE_EVDO_0 -> "EVDO Rev 0"
        TelephonyManager.NETWORK_TYPE_EVDO_A -> "EVDO Rev A"
        TelephonyManager.NETWORK_TYPE_EVDO_B -> "EVDO Rev B"
        TelephonyManager.NETWORK_TYPE_CDMA -> "CDMA"
        TelephonyManager.NETWORK_TYPE_1xRTT -> "1xRTT"
        TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE (2G)"
        TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS (2G)"
        TelephonyManager.NETWORK_TYPE_GSM -> "GSM (2G)"
        TelephonyManager.NETWORK_TYPE_IDEN -> "iDEN"
        TelephonyManager.NETWORK_TYPE_IWLAN -> "IWLAN"
        else -> "Unknown"
    }

    private fun nrBandToString(band: Int): String {
        // 常见 5G NR 频段
        return when (band) {
            1 -> "n1"
            2 -> "n2"
            3 -> "n3"
            5 -> "n5"
            7 -> "n7"
            8 -> "n8"
            12 -> "n12"
            20 -> "n20"
            25 -> "n25"
            28 -> "n28"
            38 -> "n38"
            40 -> "n40"
            41 -> "n41"
            48 -> "n48"
            50 -> "n50"
            51 -> "n51"
            66 -> "n66"
            70 -> "n70"
            71 -> "n71"
            75 -> "n75"
            76 -> "n76"
            77 -> "n77"
            78 -> "n78"
            79 -> "n79"
            80 -> "n80"
            81 -> "n81"
            82 -> "n82"
            83 -> "n83"
            84 -> "n84"
            86 -> "n86"
            257 -> "n257"
            258 -> "n258"
            260 -> "n260"
            261 -> "n261"
            else -> "n$band"
        }
    }

    private fun lteBandToString(band: Int): String = when {
        band == CellInfo.UNAVAILABLE -> ""
        band > 0 -> "B$band"
        else -> ""
    }

    private fun estimateNrBandwidth(nrarfcn: Int, info: MobileNetworkInfo) {
        if (nrarfcn <= 0) return
        // 根据 NR ARFCN 范围估算常见带宽
        // n78 (3300-3800 MHz): 常见 100 MHz
        // n41 (2496-2690 MHz): 常见 100 MHz
        info.dlBandwidth = when {
            nrarfcn in 620000..653333 -> "100 MHz"    // n78
            nrarfcn in 499200..537999 -> "100 MHz"    // n41
            nrarfcn in 422000..434000 -> "40 MHz"     // n77
            nrarfcn in 151600..160600 -> "20 MHz"     // n28
            nrarfcn in  40000..  50000 -> "20 MHz"    // low band
            else -> ""
        }
    }

    private fun estimateLteBandwidth(earfcn: Int, band: Int, info: MobileNetworkInfo) {
        if (earfcn <= 0) return
        // 根据 band 和 EARFCN 估算
        info.dlBandwidth = when (band) {
            1, 3, 7 -> "20 MHz"   // FDD 主流
            38, 40, 41 -> "20 MHz" // TDD
            else -> ""
        }
    }
}
