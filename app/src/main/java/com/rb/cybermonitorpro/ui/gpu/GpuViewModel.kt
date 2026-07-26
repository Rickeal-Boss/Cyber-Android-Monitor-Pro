package com.rb.cybermonitorpro.ui.gpu

import androidx.lifecycle.ViewModel
import com.rb.cybermonitorpro.data.repository.DeviceRepository

class GpuViewModel(
    private val repo: DeviceRepository
) : ViewModel() {
    val gpuInfo get() = repo.gpuLiveData

    fun formatGpuLoad(percent: Float): String {
        if (percent.isNaN()) return "--%"
        return "%.0f%%".format(percent)
    }

    fun formatGpuFreq(kHz: Long): String {
        if (kHz <= 0) return "-- MHz"
        return "%.0f MHz".format(kHz / 1000.0)
    }

    val historyData get() = repo.historyData
}
