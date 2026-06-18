package com.example.deviceinfoviewer.data.model

data class NetworkInterfaceInfo(
    var name: String = "",
    var ipAddress: String = "",
    var macAddress: String = "",
    var mtu: Int = -1,
    var rxBytes: Long = -1L,
    var txBytes: Long = -1L
)
