package com.example.deviceinfoviewer.ui.device

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.example.deviceinfoviewer.data.model.DeviceDetailInfo
import com.example.deviceinfoviewer.data.repository.DeviceRepository

class DeviceViewModel(private val repo: DeviceRepository) : ViewModel() {
    val detail: LiveData<DeviceDetailInfo> get() = repo.deviceDetailLiveData
}
