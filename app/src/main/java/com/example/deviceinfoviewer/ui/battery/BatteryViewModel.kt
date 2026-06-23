package com.example.deviceinfoviewer.ui.battery

import androidx.lifecycle.ViewModel
import com.example.deviceinfoviewer.data.model.BatteryInfo
import com.example.deviceinfoviewer.data.repository.DeviceRepository

class BatteryViewModel(
    private val repo: DeviceRepository
) : ViewModel() {

    val batteryInfo get() = repo.batteryLiveData

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
}
