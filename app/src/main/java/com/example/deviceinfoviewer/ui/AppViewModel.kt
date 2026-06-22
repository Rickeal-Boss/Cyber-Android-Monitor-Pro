package com.example.deviceinfoviewer.ui

import androidx.lifecycle.ViewModel
import com.example.deviceinfoviewer.RefreshPolicy
import com.example.deviceinfoviewer.data.repository.DeviceRepository

/**
 * AppViewModel — 全局监控生命周期管理
 * 在 SystemMonitorApp 级别启动，确保所有 Tab 都能获得实时数据
 *
 * 前后台统一刷新 (2026-06-21):
 * - RefreshPolicy 接管前后台调速，不再需要手动 changeInterval()
 * - DeviceRepository 内部观察 RefreshPolicy.state 自动调整
 * - changeInterval() 保留用于 Settings UI 模块间隔覆盖
 */
class AppViewModel(
    private val repo: DeviceRepository
) : ViewModel() {

    fun startMonitoring(intervalMs: Long = RefreshPolicy.Tier.NORMAL.defaultMs) {
        try {
            repo.startMonitoring(intervalMs)
            repo.loadStaticData()
        } catch (e: Throwable) { android.util.Log.w("AppVM", "监控启动失败", e) }
    }

    fun stopMonitoring() {
        repo.stopMonitoring()
    }

    /**
     * 动态调整采样间隔 — 用于 Settings UI 模块级别覆盖
     * 注意: 前后台切换已由 DeviceRepository 内部 RefreshPolicy 观察者自动处理
     */
    fun changeInterval(ms: Long) {
        try {
            repo.setIntervalMs(ms)
        } catch (e: Throwable) { android.util.Log.w("AppVM", "间隔调整失败", e) }
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
