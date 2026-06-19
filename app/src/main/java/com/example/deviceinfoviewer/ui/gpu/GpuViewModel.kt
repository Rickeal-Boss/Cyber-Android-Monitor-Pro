package com.example.deviceinfoviewer.ui.gpu

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.example.deviceinfoviewer.data.model.GpuInfo
import com.example.deviceinfoviewer.data.repository.DeviceRepository

class GpuViewModel(
    private val repo: DeviceRepository
) : ViewModel() {
    val gpuInfo: LiveData<GpuInfo> get() = repo.gpuLiveData
    val historyData get() = repo.historyData
}
