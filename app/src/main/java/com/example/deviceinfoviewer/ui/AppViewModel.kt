package com.example.deviceinfoviewer.ui

import androidx.lifecycle.ViewModel
import com.example.deviceinfoviewer.data.repository.DeviceRepository

/**
 * AppViewModel — 全局监控生命周期管理
 * 在 SystemMonitorApp 级别启动，确保所有 Tab 都能获得实时数据
 */
class AppViewModel(
    private val repo: DeviceRepository
) : ViewModel() {

    fun startMonitoring(intervalMs: Long = DeviceRepository.DEFAULT_INTERVAL_MS) {
        try {
            repo.startMonitoring(intervalMs)
            repo.loadStaticData()
        } catch (_: Throwable) { android.util.Log.w("AppVM", "Start failed") }
    }

    fun stopMonitoring() {
        repo.stopMonitoring()
    }

    override fun onCleared() {
        super.onCleared()
        repo.stopMonitoring()
    }
}
