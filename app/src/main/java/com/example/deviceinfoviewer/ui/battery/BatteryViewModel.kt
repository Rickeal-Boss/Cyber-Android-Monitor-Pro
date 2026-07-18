package com.example.deviceinfoviewer.ui.battery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.deviceinfoviewer.data.model.BatteryInfo
import com.example.deviceinfoviewer.data.repository.DeviceRepository
import com.example.deviceinfoviewer.data.source.BatteryDataSource
import kotlinx.coroutines.launch

class BatteryViewModel(
    private val repo: DeviceRepository
) : ViewModel() {

    val batteryInfo get() = repo.batteryLiveData

    init {
        // ★ 插拔事件立即触发电池采集，不等轮询 tick
        viewModelScope.launch {
            repo.batteryPulseFlow().collect { event ->
                if (event is BatteryDataSource.BatteryPulseEvent.PlugStateChanged) {
                    repo.forceRefreshBattery()
                }
            }
        }
    }

    fun formatChargingStatus(batteryInfo: BatteryInfo): String {
        return if (batteryInfo.isCharging) "充电中" else "未充电"
    }

    fun formatBatteryTemp(celsius: Float): String {
        if (celsius.isNaN()) return "--°C"
        return "%.1f°C".format(celsius)
    }

    fun formatSoH(percent: Float): String {
        if (percent.isNaN() || percent <= 0f) return "--%"
        return "%.1f%%".format(percent)
    }

    val historyData get() = repo.historyData

    /**
     * 用户在电池页手动切换双电芯后调用：
     * 立即重采电池数据，使 BatteryInfo.dualCell / effectiveVoltage 按新偏好重算。
     */
    fun refreshDualCell() {
        repo.forceRefreshBattery()
    }
}
