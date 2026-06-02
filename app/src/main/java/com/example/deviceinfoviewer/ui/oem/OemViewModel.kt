package com.example.deviceinfoviewer.ui.oem

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.example.deviceinfoviewer.data.model.OemInfo
import com.example.deviceinfoviewer.data.repository.DeviceRepository

class OemViewModel(private val repo: DeviceRepository) : ViewModel() {
    val oemInfo: LiveData<OemInfo> get() = repo.oemLiveData
}
