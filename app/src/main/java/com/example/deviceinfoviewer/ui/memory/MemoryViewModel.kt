package com.example.deviceinfoviewer.ui.memory

import androidx.lifecycle.ViewModel
import com.example.deviceinfoviewer.data.repository.DeviceRepository

class MemoryViewModel(
    private val repo: DeviceRepository
) : ViewModel() {

    val memoryInfo get() = repo.memoryLiveData
    val historyData get() = repo.historyData
}
