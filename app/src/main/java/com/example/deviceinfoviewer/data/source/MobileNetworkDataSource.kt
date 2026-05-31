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

        // 信号强度（通过反射 getDbm）
        val ss = tm.signalStrength
        if (ss != null) {
            try {
                val method = SignalStrength::class.java.getMethod("getDbm")
                val dbm = method.invoke(ss) as Int
                info.signalStrengthDbm = dbm
            } catch (_: Throwable) {
                info.signalStrengthDbm = Int.MIN_VALUE
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
            val identity = nr.cellIdentity as? android.telephony.CellIdentityNr ?: return false
            val signal = nr.cellSignalStrength as? android.telephony.CellSignalStrengthNr ?: return false

            // 小区身份
            info.cellId = identity.nci
            info.pci = identity.pci

            // 频段
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                try {
                    val bands = identity.bands
                    if (bands != null && bands.isNotEmpty()) {
                        info.band = bands.joinToString("/") { nrBandToString(it) }
                    }
                } catch (_: Throwable) {}
            }
            // ARFCN
            info.arfcn = identity.nrarfcn

            // 带宽 — 用 ARFCN 推算更可靠
            if (info.dlBandwidth.isEmpty()) {
                estimateNrBandwidth(info.arfcn, info)
            }

            // 信号参数
            info.rsrp = signal.csiRsrp
            info.rsrq = signal.csiRsrq
            info.sinr = signal.csiSinr

            // UL 状态
            info.ulConfigured = if (signal.csiRsrp != CellInfo.UNAVAILABLE) "Configured" else "Unknown"

            return signal.csiRsrp != CellInfo.UNAVAILABLE
        } catch (_: Throwable) {
            return false
        }
    }

    // ───────── LTE ─────────

    private fun parseLte(lte: CellInfoLte, info: MobileNetworkInfo): Boolean {
        try {
            val identity = lte.cellIdentity as? android.telephony.CellIdentityLte ?: return false
            val signal = lte.cellSignalStrength as? android.telephony.CellSignalStrengthLte ?: return false

            info.cellId = identity.ci.toLong()
            info.pci = identity.pci
            info.band = lteBandToString(identity.band)
            info.arfcn = identity.earfcn

            // 带宽 (API 28+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val bw = identity.bandwidth
                if (bw != CellInfo.UNAVAILABLE) {
                    info.dlBandwidth = "${bw} MHz"
                }
            }
            if (info.dlBandwidth.isEmpty()) {
                estimateLteBandwidth(info.arfcn, identity.band, info)
            }

            info.rsrp = signal.rsrp
            info.rsrq = signal.rsrq
            info.rssi = signal.rssi
            info.sinr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) signal.rssnr else Int.MIN_VALUE
            info.ulConfigured = if (signal.rsrp != CellInfo.UNAVAILABLE) "Configured" else "Unknown"

            return signal.rsrp != CellInfo.UNAVAILABLE
        } catch (_: Throwable) {
            return false
        }
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
