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
        } catch (e: Throwable) { android.util.Log.w("AppVM", "监控启动失败", e) }
    }

    fun stopMonitoring() {
        repo.stopMonitoring()
    }

    // 按 Tab 智能控制 GPS — 仅在 GPS/网络页面启用，离开时关闭以省电
    fun setGpsEnabled(enabled: Boolean) {
        if (enabled) repo.enableGps() else repo.disableGps()
    }

    override fun onCleared() {
        super.onCleared()
        repo.stopMonitoring()
    }
}
