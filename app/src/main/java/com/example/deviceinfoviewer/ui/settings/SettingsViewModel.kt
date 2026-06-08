package com.example.deviceinfoviewer.ui.settings

import androidx.lifecycle.ViewModel
import com.example.deviceinfoviewer.data.repository.DeviceRepository

class SettingsViewModel(private val repo: DeviceRepository) : ViewModel() {
    fun setIntervalMs(ms: Long) {
        repo.setIntervalMs(ms)
    }

    fun getIntervalMs(): Long = repo.getIntervalMs()
}
