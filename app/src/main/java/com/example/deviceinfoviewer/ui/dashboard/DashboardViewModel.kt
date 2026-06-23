package com.example.deviceinfoviewer.ui.dashboard

import androidx.lifecycle.ViewModel
import com.example.deviceinfoviewer.data.model.*
import com.example.deviceinfoviewer.data.repository.DeviceRepository

class DashboardViewModel(
    private val repo: DeviceRepository
) : ViewModel() {

    val cpuInfo get() = repo.cpuLiveData
    val gpuInfo get() = repo.gpuLiveData
    val batteryInfo get() = repo.batteryLiveData
    val memoryInfo get() = repo.memoryLiveData
    val storageInfo get() = repo.storageLiveData
    val systemInfo get() = repo.systemLiveData
    val historyData get() = repo.historyData
    val sourceHealth get() = repo.sourceHealth
}
