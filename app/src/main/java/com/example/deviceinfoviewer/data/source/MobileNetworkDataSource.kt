package com.example.deviceinfoviewer.data.source

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SignalStrength
import android.telephony.TelephonyManager

import androidx.core.content.ContextCompat

import com.example.deviceinfoviewer.data.model.MobileNetworkInfo

/**
 * 移动网络数据源，通过 TelephonyManager 获取网络信息
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

        // 运营商名称（需要权限 READ_PHONE_STATE）
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_PHONE_STATE)
            == PackageManager.PERMISSION_GRANTED) {
            info.operatorName = tm.networkOperatorName
            info.mccMnc = tm.networkOperator
            info.isRoaming = tm.isNetworkRoaming
        }

        // 信号强度（通过反射）
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

        return info
    }

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
        else -> "未知"
    }
}
