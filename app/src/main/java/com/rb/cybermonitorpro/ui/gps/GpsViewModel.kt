package com.rb.cybermonitorpro.ui.gps

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.rb.cybermonitorpro.data.model.GpsStatusInfo
import com.rb.cybermonitorpro.data.repository.DeviceRepository

class GpsViewModel(private val repo: DeviceRepository) : ViewModel() {
    val gpsInfo: LiveData<GpsStatusInfo> get() = repo.gpsLiveData
}
