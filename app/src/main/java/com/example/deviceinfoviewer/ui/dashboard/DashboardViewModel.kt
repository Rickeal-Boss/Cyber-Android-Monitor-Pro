package com.example.deviceinfoviewer.ui.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.example.deviceinfoviewer.data.model.*
import com.example.deviceinfoviewer.data.repository.DeviceRepository

class DashboardViewModel(
    private val repo: DeviceRepository
) : ViewModel() {

    val cpuInfo: LiveData<CpuInfo> get() = repo.cpuLiveData
    val gpuInfo: LiveData<GpuInfo> get() = repo.gpuLiveData
    val batteryInfo: LiveData<BatteryInfo> get() = repo.batteryLiveData
    val memoryInfo: LiveData<MemoryInfo> get() = repo.memoryLiveData
    val storageInfo: LiveData<StorageInfo> get() = repo.storageLiveData
    val systemInfo: LiveData<SystemInfo> get() = repo.systemLiveData
    val historyData get() = repo.historyData
    val sourceHealth get() = repo.sourceHealth
}
