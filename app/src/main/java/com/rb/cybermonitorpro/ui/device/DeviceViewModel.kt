package com.rb.cybermonitorpro.ui.device

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.rb.cybermonitorpro.data.model.DeviceDetailInfo
import com.rb.cybermonitorpro.data.repository.DeviceRepository

class DeviceViewModel(private val repo: DeviceRepository) : ViewModel() {
    val detail: LiveData<DeviceDetailInfo> get() = repo.deviceDetailLiveData

    /** ★ 2026-08-07: BLUETOOTH_CONNECT 授权回调后触发重采，蓝牙名称即刻回填 */
    fun refreshDetail() = repo.refreshDeviceDetail()
}
