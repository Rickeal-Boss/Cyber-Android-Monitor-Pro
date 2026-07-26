package com.rb.cybermonitorpro.ui.network

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.rb.cybermonitorpro.data.model.MobileNetworkInfo
import com.rb.cybermonitorpro.data.model.WifiDetailInfo
import com.rb.cybermonitorpro.data.repository.DeviceRepository

class NetworkViewModel(
    private val repo: DeviceRepository
) : ViewModel() {
    val wifiInfo: LiveData<WifiDetailInfo> get() = repo.wifiLiveData
    val mobileNetworkInfo: LiveData<MobileNetworkInfo> get() = repo.mobileNetworkLiveData
    val historyData get() = repo.historyData
}
