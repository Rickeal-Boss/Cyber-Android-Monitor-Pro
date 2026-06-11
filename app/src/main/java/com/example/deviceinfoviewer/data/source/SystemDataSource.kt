package com.example.deviceinfoviewer.data.source

import android.os.Build

import com.example.deviceinfoviewer.data.model.SystemInfo

/**
 * 系统信息数据源，通过反射读取 Build 字段
 */
class SystemDataSource {

    fun getSystemInfo(): SystemInfo {
        val info = SystemInfo()

        // 反射读取 Build 全部字段
        val buildFields = mutableMapOf<String, String>()
        for (field in Build::class.java.declaredFields) {
            try {
                val name = field.name
                val value = field.get(null)
                buildFields[name] = if (value is String) value else value.toString()
            } catch (_: Throwable) {}
        }
        // 也读取 VERSION 类的字段
        for (field in Build.VERSION::class.java.declaredFields) {
            try {
                val name = "VERSION.${field.name}"
                val value = field.get(null)
                buildFields[name] = value.toString()
            } catch (_: Throwable) {}
        }

        info.buildFields = buildFields
        info.androidVersion = Build.VERSION.RELEASE
        info.bootloader = Build.BOOTLOADER
        info.securityPatch = Build.VERSION.SECURITY_PATCH

        // 内核版本
        info.kernelVersion = SysFsReader.readLine("/proc/version")

        // Java VM 版本
        info.javaVmVersion = System.getProperty("java.vm.version", "")
        info.javaRuntimeName = System.getProperty("java.runtime.name", "")

        // 系统已开机时长 (/proc/uptime)
        info.uptimeSeconds = readUptime()

        return info
    }

    private fun readUptime(): Long {
        try {
            val line = SysFsReader.readLine("/proc/uptime")
            if (line.isNotBlank()) {
                val parts = line.trim().split("\\s+".toRegex())
                return parts.firstOrNull()?.toFloatOrNull()?.toLong() ?: 0L
            }
        } catch (_: Throwable) {}
        return 0L
    }
}
