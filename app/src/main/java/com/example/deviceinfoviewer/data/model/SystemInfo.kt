package com.example.deviceinfoviewer.data.model

data class SystemInfo(
    var buildFields: MutableMap<String, String> = mutableMapOf(),
    var androidVersion: String = "",
    var kernelVersion: String = "",
    var javaVmVersion: String = "",
    var javaRuntimeName: String = "",
    var bootloader: String = "",
    var securityPatch: String = ""
)
