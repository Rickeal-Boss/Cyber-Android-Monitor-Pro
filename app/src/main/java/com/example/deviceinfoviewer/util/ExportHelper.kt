package com.example.deviceinfoviewer.util

import android.content.Context
import android.content.Intent
import com.example.deviceinfoviewer.FormatUtils
import com.example.deviceinfoviewer.R
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
    fun exportToText(context: Context, repository: DeviceRepository): String {
        val sb = StringBuilder()
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        sb.append("========================================\n")
        sb.append("       ${context.getString(R.string.export_report_title)}\n")
        sb.append("       ${context.getString(R.string.export_time_label, sdf.format(Date()))}\n")
        sb.append("========================================\n\n")

        // CPU
        val cpu = repository.cpuLiveData.value
        if (cpu != null) {
            sb.append(context.getString(R.string.export_section_cpu) + "\n")
            sb.append(context.getString(R.string.export_arch) + " ${cpu.architecture}\n")
            sb.append(context.getString(R.string.export_core_count) + " ${cpu.coreCount}\n")
            sb.append(context.getString(R.string.export_temperature) + " ${FormatUtils.formatTempCelsius(cpu.temperatureCelsius)}\n")
            for (core in cpu.cores) {
                sb.append(context.getString(R.string.export_core_index, core.coreIndex) + ": ${FormatUtils.formatFreq(core.currentFreqKHz)}")
                    .append(context.getString(R.string.export_max, FormatUtils.formatFreq(core.maxFreqKHz)))
                    .append(context.getString(R.string.export_governor, core.governor) + "\n")
            }
            sb.append("\n")
        }

        // GPU
        val gpu = repository.gpuLiveData.value
        if (gpu != null) {
            sb.append(context.getString(R.string.export_section_gpu) + "\n")
            sb.append(context.getString(R.string.export_model) + " ${gpu.model}\n")
            sb.append(context.getString(R.string.export_vendor) + " ${gpu.vendor}\n")
            sb.append(context.getString(R.string.export_frequency) + " ${FormatUtils.formatFreq(gpu.frequencyKHz)}\n")
            sb.append("\n")
        }

        // 电池
        val battery = repository.batteryLiveData.value
        if (battery != null) {
            sb.append(context.getString(R.string.export_section_battery) + "\n")
            sb.append(context.getString(R.string.export_level) + " ${FormatUtils.formatPercent(battery.levelPercent)}\n")
            sb.append(context.getString(R.string.export_temperature) + " ${FormatUtils.formatTempCelsius(battery.temperatureCelsius)}\n")
            sb.append(context.getString(R.string.export_status) + " ${battery.chargeStatus}\n")
            sb.append(context.getString(R.string.export_health) + " ${battery.health}\n")
            sb.append(context.getString(R.string.export_cycle_count) + " ${battery.cycleCount}\n")
            sb.append("\n")
        }

        // 内存
        val memory = repository.memoryLiveData.value
        if (memory != null) {
            sb.append(context.getString(R.string.export_section_memory) + "\n")
            sb.append(context.getString(R.string.export_total) + " ${FormatUtils.formatBytes(memory.totalKB * 1024)}\n")
            sb.append(context.getString(R.string.export_available) + " ${FormatUtils.formatBytes(memory.availableKB * 1024)}\n")
            sb.append("\n")
        }

        // 存储
        val storage = repository.storageLiveData.value
        if (storage != null) {
            sb.append(context.getString(R.string.export_section_storage) + "\n")
            sb.append(context.getString(R.string.export_internal_total) + " ${FormatUtils.formatBytes(storage.internalTotalBytes)}\n")
            sb.append(context.getString(R.string.export_used) + " ${FormatUtils.formatBytes(storage.internalUsedBytes)}\n")
            sb.append(context.getString(R.string.export_available) + " ${FormatUtils.formatBytes(storage.internalAvailableBytes)}\n")
            sb.append("\n")
        }

        // 系统
        val sys = repository.systemLiveData.value
        if (sys != null) {
            sb.append(context.getString(R.string.export_section_system) + "\n")
            sb.append(context.getString(R.string.export_android) + " ${sys.androidVersion}\n")
            sb.append(context.getString(R.string.export_kernel) + " ${sys.kernelVersion}\n")
            sb.append("\n")
        }

        sb.append("========================================\n")
        sb.append("        ${context.getString(R.string.export_report_end)}\n")
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
        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.export_share_chooser)))
    }
}
