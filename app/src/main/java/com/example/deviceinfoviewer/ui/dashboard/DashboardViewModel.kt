package com.example.deviceinfoviewer.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.deviceinfoviewer.data.model.*
import com.example.deviceinfoviewer.data.repository.DeviceRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Dashboard ViewModel consuming SharedFlow from DeviceRepository */
class DashboardViewModel(
    private val repo: DeviceRepository
) : ViewModel() {

    val cpuInfo: StateFlow<CpuInfo> = repo.cpuFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CpuInfo())

    val gpuInfo: StateFlow<GpuInfo> = repo.gpuFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GpuInfo())

    val batteryInfo: StateFlow<BatteryInfo> = repo.batteryFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BatteryInfo())

    val memoryInfo: StateFlow<MemoryInfo> = repo.memoryFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MemoryInfo())

    // ═══════ Auxiliary modules (LiveData) ═══════
    val storageInfo get() = repo.storageLiveData
    val systemInfo get() = repo.systemLiveData

    val historyData get() = repo.historyData
    val sourceHealth get() = repo.sourceHealth
}
