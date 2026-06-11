package com.example.deviceinfoviewer.ui.sensors

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.example.deviceinfoviewer.data.model.SensorItemInfo
import com.example.deviceinfoviewer.data.repository.DeviceRepository

class SensorsViewModel(private val repo: DeviceRepository) : ViewModel() {
    val sensors: LiveData<List<SensorItemInfo>> get() = repo.sensorsLiveData
}
