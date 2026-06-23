package com.example.deviceinfoviewer.ui.gpu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.deviceinfoviewer.data.model.GpuInfo
import com.example.deviceinfoviewer.data.repository.DeviceRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class GpuViewModel(
    private val repo: DeviceRepository
) : ViewModel() {
    val gpuInfo: StateFlow<GpuInfo> = repo.gpuFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GpuInfo())

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
