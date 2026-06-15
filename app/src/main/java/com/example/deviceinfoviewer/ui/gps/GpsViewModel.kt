package com.example.deviceinfoviewer.ui.gps

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.example.deviceinfoviewer.data.model.GpsStatusInfo
import com.example.deviceinfoviewer.data.repository.DeviceRepository

class GpsViewModel(private val repo: DeviceRepository) : ViewModel() {
    val gpsInfo: LiveData<GpsStatusInfo> get() = repo.gpsLiveData
}
