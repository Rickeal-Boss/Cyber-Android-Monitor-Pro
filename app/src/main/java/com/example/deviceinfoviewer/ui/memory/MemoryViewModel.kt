package com.example.deviceinfoviewer.ui.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.deviceinfoviewer.data.model.MemoryInfo
import com.example.deviceinfoviewer.data.repository.DeviceRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class MemoryViewModel(
    private val repo: DeviceRepository
) : ViewModel() {
    val memoryInfo: StateFlow<MemoryInfo> = repo.memoryFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MemoryInfo())

    fun formatMemoryGB(kb: Long): String {
        if (kb <= 0) return "-- GB"
        return "%.1f GB".format(kb / 1_048_576.0)
    }

    fun formatMemoryMB(kb: Long): String {
        if (kb <= 0) return "-- MB"
        return "%.0f MB".format(kb / 1024.0)
    }

    val historyData get() = repo.historyData
}
