package com.example.deviceinfoviewer.ui.cpu

import androidx.lifecycle.ViewModel
import com.example.deviceinfoviewer.data.repository.DeviceRepository

class CpuViewModel(
    private val repo: DeviceRepository
) : ViewModel() {

    val cpuInfo get() = repo.cpuLiveData

    fun formatCpuTemp(celsius: Float): String {
        if (celsius.isNaN()) return "--°C"
        return "%.1f°C".format(celsius)
    }

    fun formatCpuUsage(percent: Float): String {
        if (percent.isNaN() || percent < 0f) return "--%"
        return "%.1f%%".format(percent.coerceAtMost(100f))
    }

    val historyData get() = repo.historyData
}
