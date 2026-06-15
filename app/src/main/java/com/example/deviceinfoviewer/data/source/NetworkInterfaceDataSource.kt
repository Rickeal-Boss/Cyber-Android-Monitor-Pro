package com.example.deviceinfoviewer.data.source

import com.example.deviceinfoviewer.data.model.NetworkInterfaceInfo

import java.net.NetworkInterface

/**
 * 网络接口数据源，通过 Java NetworkInterface API 获取信息
 */
class NetworkInterfaceDataSource {

    fun getNetworkInterfaces(): List<NetworkInterfaceInfo> {
        val result = mutableListOf<NetworkInterfaceInfo>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
                ?: return result
            while (interfaces.hasMoreElements()) {
                val ni = interfaces.nextElement()
                val info = NetworkInterfaceInfo()
                info.name = ni.name
                info.mtu = ni.mtu

                // MAC 地址
                ni.hardwareAddress?.let { mac ->
                    val macStr = StringBuilder()
                    for (i in mac.indices) {
                        macStr.append(String.format("%02X", mac[i]))
                        if (i < mac.size - 1) {
                            macStr.append(":")
                        }
                    }
                    info.macAddress = macStr.toString()
                }

                // IP 地址
                val addresses = ni.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress) {
                        addr.hostAddress?.let { ip ->
                            // 移除 % 后的 scope id
                            val cleanIp: String = ip.indexOf('%').let {
                                if (it > 0) ip.substring(0, it) else ip
                            }
                            if (cleanIp.contains(":")) {
                                info.ipAddress = cleanIp
                            } else if (info.ipAddress.isEmpty()) {
                                info.ipAddress = cleanIp
                            }
                        }
                    }
                }

                result.add(info)
            }
        } catch (_: Throwable) {
            // 忽略异常
        }
        return result
    }
}
