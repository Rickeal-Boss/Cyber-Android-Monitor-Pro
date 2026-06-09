package com.example.deviceinfoviewer.util

import android.content.Context
import android.content.Intent
import com.example.deviceinfoviewer.FormatUtils
import com.example.deviceinfoviewer.data.model.*
import com.example.deviceinfoviewer.data.repository.DeviceRepository
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 导出工具类，支持纯文本和 JSON 格式导出
 */
object ExportHelper {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    /**
     * 导出为纯文本报告
     */
    fun exportToText(repository: DeviceRepository): String {
        val sb = StringBuilder()
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        sb.append("========================================\n")
        sb.append("       设备信息报告\n")
        sb.append("       导出时间: ${sdf.format(Date())}\n")
        sb.append("========================================\n\n")

        // CPU
        val cpu = repository.cpuLiveData.value
        if (cpu != null) {
            sb.append("[CPU 信息]\n")
            sb.append("架构: ${cpu.architecture}\n")
            sb.append("核心数: ${cpu.coreCount}\n")
            sb.append("温度: ${FormatUtils.formatTempCelsius(cpu.temperatureCelsius)}\n")
            for (core in cpu.cores) {
                sb.append("  核心${core.coreIndex}: ${FormatUtils.formatFreq(core.currentFreqKHz)}")
                    .append(" (最大 ${FormatUtils.formatFreq(core.maxFreqKHz)}")
                    .append(", 调度器: ${core.governor})\n")
            }
            sb.append("\n")
        }

        // GPU
        val gpu = repository.gpuLiveData.value
        if (gpu != null) {
            sb.append("[GPU 信息]\n")
            sb.append("型号: ${gpu.model}\n")
            sb.append("厂商: ${gpu.vendor}\n")
            sb.append("频率: ${FormatUtils.formatFreq(gpu.frequencyKHz)}\n")
            sb.append("\n")
        }

        // 电池
        val battery = repository.batteryLiveData.value
        if (battery != null) {
            sb.append("[电池信息]\n")
            sb.append("电量: ${FormatUtils.formatPercent(battery.levelPercent)}\n")
            sb.append("温度: ${FormatUtils.formatTempCelsius(battery.temperatureCelsius)}\n")
            sb.append("状态: ${battery.chargeStatus}\n")
            sb.append("健康: ${battery.health}\n")
            sb.append("循环次数: ${battery.cycleCount}\n")
            sb.append("\n")
        }

        // 内存
        val memory = repository.memoryLiveData.value
        if (memory != null) {
            sb.append("[内存信息]\n")
            sb.append("总: ${FormatUtils.formatBytes(memory.totalKB * 1024)}\n")
            sb.append("可用: ${FormatUtils.formatBytes(memory.availableKB * 1024)}\n")
            sb.append("\n")
        }

        // 存储
        val storage = repository.storageLiveData.value
        if (storage != null) {
            sb.append("[存储信息]\n")
            sb.append("内部存储总: ${FormatUtils.formatBytes(storage.internalTotalBytes)}\n")
            sb.append("已用: ${FormatUtils.formatBytes(storage.internalUsedBytes)}\n")
            sb.append("可用: ${FormatUtils.formatBytes(storage.internalAvailableBytes)}\n")
            sb.append("\n")
        }

        // 系统
        val sys = repository.systemLiveData.value
        if (sys != null) {
            sb.append("[系统信息]\n")
            sb.append("Android: ${sys.androidVersion}\n")
            sb.append("内核: ${sys.kernelVersion}\n")
            sb.append("\n")
        }

        sb.append("========================================\n")
        sb.append("        报告结束\n")
        sb.append("========================================\n")

        return sb.toString()
    }

    /**
     * 导出为 JSON 格式
     */
    fun exportToJson(repository: DeviceRepository): String {
        // 简单的 JSON 导出
        return buildString {
            append("{\n")
            append("  \"exportTime\": ${System.currentTimeMillis()},\n")
            append("  \"cpu\": ${gson.toJson(repository.cpuLiveData.value)},\n")
            append("  \"gpu\": ${gson.toJson(repository.gpuLiveData.value)},\n")
            append("  \"battery\": ${gson.toJson(repository.batteryLiveData.value)},\n")
            append("  \"memory\": ${gson.toJson(repository.memoryLiveData.value)},\n")
            append("  \"storage\": ${gson.toJson(repository.storageLiveData.value)},\n")
            append("  \"system\": ${gson.toJson(repository.systemLiveData.value)}\n")
            append("}")
        }
    }

    /**
     * 通过 Intent 分享报告（直接用 EXTRA_TEXT）
     */
    fun shareReport(context: Context, content: String, title: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, content)
        }
        context.startActivity(Intent.createChooser(shareIntent, "分享设备信息"))
    }
}
