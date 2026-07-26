package com.rb.cybermonitorpro.ui.settings

import androidx.lifecycle.ViewModel
import com.rb.cybermonitorpro.data.repository.DeviceRepository

class SettingsViewModel(private val repo: DeviceRepository) : ViewModel() {
    fun setIntervalMs(ms: Long) {
        repo.setIntervalMs(ms)
    }

    fun getIntervalMs(): Long = repo.getIntervalMs()

    // ── 分模块刷新间隔 — 仅 CPU/GPU/Memory/Battery 支持 ──
    fun setCpuRefreshMs(ms: Long) = repo.setCpuRefreshMs(ms)
    fun getCpuRefreshMs(): Long = repo.getCpuRefreshMs()
    fun setGpuRefreshMs(ms: Long) = repo.setGpuRefreshMs(ms)
    fun getGpuRefreshMs(): Long = repo.getGpuRefreshMs()
    fun setMemoryRefreshMs(ms: Long) = repo.setMemoryRefreshMs(ms)
    fun getMemoryRefreshMs(): Long = repo.getMemoryRefreshMs()
    fun setBatteryRefreshMs(ms: Long) = repo.setBatteryRefreshMs(ms)
    fun getBatteryRefreshMs(): Long = repo.getBatteryRefreshMs()
}
