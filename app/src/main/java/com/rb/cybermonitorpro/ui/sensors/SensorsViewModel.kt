package com.rb.cybermonitorpro.ui.sensors

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.rb.cybermonitorpro.data.model.SensorItemInfo
import com.rb.cybermonitorpro.data.repository.DeviceRepository

class SensorsViewModel(private val repo: DeviceRepository) : ViewModel() {
    val sensors: LiveData<List<SensorItemInfo>> get() = repo.sensorsLiveData
}
