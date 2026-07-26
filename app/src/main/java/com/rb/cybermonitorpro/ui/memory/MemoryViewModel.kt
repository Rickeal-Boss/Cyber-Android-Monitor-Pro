package com.rb.cybermonitorpro.ui.memory

import androidx.lifecycle.ViewModel
import com.rb.cybermonitorpro.data.repository.DeviceRepository

class MemoryViewModel(
    private val repo: DeviceRepository
) : ViewModel() {

    val memoryInfo get() = repo.memoryLiveData
    val historyData get() = repo.historyData
}
