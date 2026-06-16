package com.example.deviceinfoviewer.ui.settings

import androidx.lifecycle.ViewModel
import com.example.deviceinfoviewer.data.repository.DeviceRepository

class SettingsViewModel(private val repo: DeviceRepository) : ViewModel() {
    fun setIntervalMs(ms: Long) {
        repo.setIntervalMs(ms)
    }

    fun getIntervalMs(): Long = repo.getIntervalMs()

    // ── 分模块刷新间隔 ──
    fun setCpuRefreshMs(ms: Long) = repo.setCpuRefreshMs(ms)
    fun getCpuRefreshMs(): Long = repo.getCpuRefreshMs()
    fun setGpuRefreshMs(ms: Long) = repo.setGpuRefreshMs(ms)
    fun getGpuRefreshMs(): Long = repo.getGpuRefreshMs()
    fun setMemoryRefreshMs(ms: Long) = repo.setMemoryRefreshMs(ms)
    fun getMemoryRefreshMs(): Long = repo.getMemoryRefreshMs()
    fun setBatteryRefreshMs(ms: Long) = repo.setBatteryRefreshMs(ms)
    fun getBatteryRefreshMs(): Long = repo.getBatteryRefreshMs()
    fun setNetworkRefreshMs(ms: Long) = repo.setNetworkRefreshMs(ms)
    fun getNetworkRefreshMs(): Long = repo.getNetworkRefreshMs()
    fun setGpsRefreshMs(ms: Long) = repo.setGpsRefreshMs(ms)
    fun getGpsRefreshMs(): Long = repo.getGpsRefreshMs()
    fun setSensorsRefreshMs(ms: Long) = repo.setSensorsRefreshMs(ms)
    fun getSensorsRefreshMs(): Long = repo.getSensorsRefreshMs()

    /** 获取某模块的有效间隔 — 若为 0 则回退到全局默认 */
    fun effectiveRefreshMs(moduleMs: Long): Long =
        if (moduleMs > 0) moduleMs else getIntervalMs()
}
