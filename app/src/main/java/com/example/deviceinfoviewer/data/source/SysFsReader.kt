package com.example.deviceinfoviewer.data.source

import android.content.Context
import android.os.BatteryManager
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

/**
 * sysfs / proc 文件系统读取工具 + 隐藏 API 反射访问
 *
 * Android 11+ 对 sysfs 的 SELinux 访问越来越严格（尤其是 HyperOS/ColorOS/OriginOS 等国产 ROM），
 * 本工具类提供多层 fallback 策略：
 * 1. 直接 sysfs 读取（最准确，但可能被 SELinux 拦截）
 * 2. SystemProperties 反射读取（厂商通常通过系统属性暴露部分信息）
 * 3. HardwarePropertiesManager 反射（需要 DEVICE_POWER 权限，仅系统应用可用）
 * 4. BatteryManager 隐藏 API 反射（BATTERY_PROPERTY_CHARGE_COUNTER 等）
 */
object SysFsReader {

    fun readLine(path: String): String =
        try {
            BufferedReader(FileReader(path)).use { it.readLine()?.trim() ?: "" }
        } catch (_: Throwable) { "" }

    fun readLong(path: String): Long =
        readLine(path).toLongOrNull() ?: -1L

    fun readFloat(path: String): Float =
        readLine(path).toFloatOrNull() ?: Float.NaN

    fun readInt(path: String): Int =
        readLine(path).toIntOrNull() ?: -1

    /**
     * 读取文件全部内容（与 readLine 类似但读取多行场景更安全）
     */
    fun readFile(path: String): String =
        try {
            File(path).readText().trim()
        } catch (_: Throwable) { "" }

    fun fileExists(path: String): Boolean = File(path).exists()

    fun listDir(path: String): List<String> {
        val dir = File(path)
        return if (dir.exists() && dir.isDirectory) dir.list()?.toList() ?: emptyList()
        else emptyList()
    }

    /**
     * 读取所有 sysfs thermal zone type 并返回 Map<zoneName, tempPath>
     */
    fun scanThermalZones(basePath: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val zones = listDir(basePath)
        for (zone in zones) {
            if (!zone.startsWith("thermal_zone")) continue
            val typePath = basePath + zone + "/type"
            val tempPath = basePath + zone + "/temp"
            val type = readLine(typePath)
            if (type.isNotEmpty()) {
                result[type] = tempPath
            }
        }
        return result
    }

    /**
     * 遍历多个 sysfs 路径读取数值，返回第一个有效值
     */
    fun readFirstLong(paths: List<String>): Long {
        for (path in paths) {
            val value = readLong(path)
            if (value > 0) return value
        }
        return -1L
    }

    /**
     * 遍历多个 sysfs 路径读取数值，返回第一个有效值
     */
    fun readFirstFloat(paths: List<String>): Float {
        for (path in paths) {
            val value = readFloat(path)
            if (!value.isNaN() && value > 0) return value
        }
        return Float.NaN
    }

    fun readProp(key: String): String =
        try {
            val cls = Class.forName("android.os.SystemProperties")
            cls.getMethod("get", String::class.java, String::class.java)
                .invoke(null, key, "")?.toString() ?: ""
        } catch (_: Throwable) { "" }

    fun readPropInt(key: String): Int =
        readProp(key).toIntOrNull() ?: -1

    fun readPropLong(key: String): Long =
        readProp(key).toLongOrNull() ?: -1L

    fun readAll(path: String): String =
        try {
            BufferedReader(FileReader(path)).use { it.readText() }
        } catch (_: Throwable) { "" }

    // ========== 硬件属性管理器反射（系统级温度 API） ==========

    /**
     * 通过反射调用 HardwarePropertiesManager.getDeviceTemperatures()
     * 需要 android.permission.DEVICE_POWER 权限 — 仅系统应用可用
     * @return Map<设备名称, 温度℃> 或空 Map
     */
    fun getDeviceTemperaturesViaReflection(context: Context): Map<String, Float> {
        return try {
            val service = context.getSystemService(Context.HARDWARE_PROPERTIES_SERVICE)
                ?: return emptyMap()
            val method = service.javaClass.getMethod("getDeviceTemperatures")
            val temperatures = method.invoke(service) as? Array<*> ?: return emptyMap()

            val result = mutableMapOf<String, Float>()
            for (temp in temperatures) {
                temp ?: continue
                try {
                    val name = temp.javaClass.getMethod("getName").invoke(temp) as? String ?: "unknown"
                    val value = (temp.javaClass.getMethod("getTemperature").invoke(temp) as? Float) ?: Float.NaN
                    val type = (temp.javaClass.getMethod("getType").invoke(temp) as? Int) ?: -1
                    val label = when (type) {
                        0 -> "CPU_$name"
                        1 -> "GPU_$name"
                        2 -> "BATTERY_$name"
                        3 -> "SKIN_$name"
                        else -> "OTHER_$name"
                    }
                    if (!value.isNaN()) result[label] = value
                } catch (_: Throwable) { /* skip this entry */ }
            }
            result
        } catch (_: Throwable) { emptyMap() }
    }

    /**
     * 通过反射获取 CPU 温度数组（简化版）
     * @return FloatArray of CPU core temperatures, or null on failure
     */
    fun getCpuTemperaturesViaReflection(context: Context): FloatArray? {
        return try {
            val service = context.getSystemService(Context.HARDWARE_PROPERTIES_SERVICE)
                ?: return null
            val method = service.javaClass.getMethod("getCpuTemperatures")
            method.invoke(service) as? FloatArray
        } catch (_: Throwable) { null }
    }

    // ========== BatteryManager 隐藏属性反射 ==========

    /**
     * 通过反射读取 BatteryManager 的隐藏属性
     * 如 BATTERY_PROPERTY_CHARGE_COUNTER (API 21+), BATTERY_PROPERTY_CURRENT_NOW (API 21+)
     * 注意：这些常量从 Android 内部定义，第三方 SDK 中不存在
     */
    fun getBatteryIntProperty(context: Context, propertyName: String): Int {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                ?: return -1
            // BATTERY_PROPERTY_CHARGE_COUNTER = 1, BATTERY_PROPERTY_CURRENT_NOW = 2, etc.
            val propField = BatteryManager::class.java.getDeclaredField(propertyName)
            propField.isAccessible = true
            val propId = propField.getInt(null)
            bm.getIntProperty(propId)
        } catch (_: Throwable) { -1 }
    }

    fun getBatteryLongProperty(context: Context, propertyName: String): Long {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                ?: return Long.MIN_VALUE
            val propField = BatteryManager::class.java.getDeclaredField(propertyName)
            propField.isAccessible = true
            val propId = propField.getInt(null)
            bm.getLongProperty(propId)
        } catch (_: Throwable) { Long.MIN_VALUE }
    }
}
