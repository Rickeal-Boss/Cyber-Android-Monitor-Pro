package com.example.deviceinfoviewer.ui.cpu

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.example.deviceinfoviewer.data.model.CpuInfo
import com.example.deviceinfoviewer.data.repository.DeviceRepository

class CpuViewModel(
    private val repo: DeviceRepository
) : ViewModel() {
    val cpuInfo: LiveData<CpuInfo> get() = repo.cpuLiveData
    val historyData get() = repo.historyData
}
