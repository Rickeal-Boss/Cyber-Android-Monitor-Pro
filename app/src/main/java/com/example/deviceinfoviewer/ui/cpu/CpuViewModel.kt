package com.example.deviceinfoviewer.ui.cpu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.deviceinfoviewer.data.model.CpuInfo
import com.example.deviceinfoviewer.data.repository.DeviceRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * [Architect Note] CPU ViewModel (方案B: 保留 + 业务逻辑下沉)
 *
 * 从 SharedFlow 管道消费数据，通过 stateIn 转为 StateFlow 供 Compose 消费。
 * SharingStarted.WhileSubscribed(5000) 确保 Tab 切换离开时 5 秒后停止上游，
 * 切换回来时自动恢复 (保留 replay=1 的最新值)。
 */
class CpuViewModel(
    private val repo: DeviceRepository
) : ViewModel() {

    /**
     * CPU 信息 StateFlow — Compose 通过 collectAsState() 直接消费。
     * replay=1 保证新订阅者立即获取最新值，无需等待下一个 tick。
     */
    val cpuInfo: StateFlow<CpuInfo> = repo.cpuFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CpuInfo())

    /**
     * CPU 温度格式化文案 (业务逻辑下沉)
     * 将 Float.NaN → "--" 的 UI 逻辑从 Composable 提取到 ViewModel
     */
    fun formatCpuTemp(celsius: Float): String {
        if (celsius.isNaN()) return "--°C"
        return "%.1f°C".format(celsius)
    }

    /**
     * CPU 使用率格式化 (异常值处理)
     */
    fun formatCpuUsage(percent: Float): String {
        if (percent.isNaN() || percent < 0f) return "--%"
        return "%.1f%%".format(percent.coerceAtMost(100f))
    }

    val historyData get() = repo.historyData
}
