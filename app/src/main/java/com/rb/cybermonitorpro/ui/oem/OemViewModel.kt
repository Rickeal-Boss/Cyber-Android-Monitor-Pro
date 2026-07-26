package com.rb.cybermonitorpro.ui.oem

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.rb.cybermonitorpro.data.model.OemInfo
import com.rb.cybermonitorpro.data.repository.DeviceRepository

class OemViewModel(private val repo: DeviceRepository) : ViewModel() {
    val oemInfo: LiveData<OemInfo> get() = repo.oemLiveData
}
