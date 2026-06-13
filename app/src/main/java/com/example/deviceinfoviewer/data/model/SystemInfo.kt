package com.example.deviceinfoviewer.data.model

data class SystemInfo(
    var buildFields: MutableMap<String, String> = mutableMapOf(),
    var androidVersion: String = "",
    var kernelVersion: String = "",
    var javaVmVersion: String = "",
    var javaRuntimeName: String = "",
    var bootloader: String = "",
    var securityPatch: String = "",
    /** 系统已开机时长 (秒) */
    var uptimeSeconds: Long = 0L,
)
