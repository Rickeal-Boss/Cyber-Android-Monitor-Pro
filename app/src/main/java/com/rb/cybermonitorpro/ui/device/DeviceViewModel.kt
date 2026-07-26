package com.rb.cybermonitorpro.ui.device

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.rb.cybermonitorpro.data.model.DeviceDetailInfo
import com.rb.cybermonitorpro.data.repository.DeviceRepository

class DeviceViewModel(private val repo: DeviceRepository) : ViewModel() {
    val detail: LiveData<DeviceDetailInfo> get() = repo.deviceDetailLiveData
}
