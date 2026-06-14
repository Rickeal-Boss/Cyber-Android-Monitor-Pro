package com.example.deviceinfoviewer.ui.network

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.example.deviceinfoviewer.data.model.MobileNetworkInfo
import com.example.deviceinfoviewer.data.model.WifiDetailInfo
import com.example.deviceinfoviewer.data.repository.DeviceRepository

class NetworkViewModel(
    private val repo: DeviceRepository
) : ViewModel() {
    val wifiInfo: LiveData<WifiDetailInfo> get() = repo.wifiLiveData
    val mobileNetworkInfo: LiveData<MobileNetworkInfo> get() = repo.mobileNetworkLiveData
    val historyData get() = repo.historyData
}
