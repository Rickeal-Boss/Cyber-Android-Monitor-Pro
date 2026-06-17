package com.example.deviceinfoviewer.ui.battery

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.example.deviceinfoviewer.data.model.BatteryInfo
import com.example.deviceinfoviewer.data.repository.DeviceRepository

class BatteryViewModel(
    private val repo: DeviceRepository
) : ViewModel() {
    val batteryInfo: LiveData<BatteryInfo> get() = repo.batteryLiveData
    val historyData get() = repo.historyData
}
