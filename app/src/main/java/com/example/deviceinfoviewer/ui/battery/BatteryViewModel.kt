package com.example.deviceinfoviewer.ui.battery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.deviceinfoviewer.data.model.BatteryInfo
import com.example.deviceinfoviewer.data.repository.DeviceRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * [Architect Note] Battery ViewModel (方案B: 保留 + 业务逻辑下沉)
 */
class BatteryViewModel(
    private val repo: DeviceRepository
) : ViewModel() {

    val batteryInfo: StateFlow<BatteryInfo> = repo.batteryFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BatteryInfo())

    /**
     * 充电状态 → UI 文案 (业务逻辑下沉)
     */
    fun formatChargingStatus(batteryInfo: BatteryInfo): String {
        return when {
            batteryInfo.isCharging && batteryInfo.isFastCharging -> "快速充电中"
            batteryInfo.isCharging -> "充电中"
            else -> "未充电"
        }
    }

    /**
     * 电池温度格式化
     */
    fun formatBatteryTemp(celsius: Float): String {
        if (celsius.isNaN()) return "--°C"
        return "%.1f°C".format(celsius)
    }

    /**
     * SoH 健康度格式化
     */
    fun formatSoH(percent: Float): String {
        if (percent.isNaN() || percent <= 0f) return "--%"
        return "%.1f%%".format(percent)
    }

    val historyData get() = repo.historyData
}
