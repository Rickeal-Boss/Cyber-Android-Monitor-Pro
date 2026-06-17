package com.example.deviceinfoviewer.ui.sensors

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import com.example.deviceinfoviewer.data.model.HistoryDataPoint
import com.example.deviceinfoviewer.data.model.SensorItemInfo
import com.example.deviceinfoviewer.data.model.SensorLiveData
import com.example.deviceinfoviewer.data.model.SensorTypeMeta
import com.example.deviceinfoviewer.data.repository.DeviceRepository

/**
 * 传感器详情页 ViewModel
 * 管理单个传感器的实时数据采集和静态信息展示
 */
class SensorDetailViewModel(
    private val repo: DeviceRepository
) : ViewModel() {

    val liveData: LiveData<SensorLiveData> get() = repo.sensorLiveData
    val sensorHistoryData get() = repo.sensorHistoryData

    private var currentSensor: SensorItemInfo? = null
    private var currentMeta: SensorTypeMeta? = null

    fun getSensorInfo(): SensorItemInfo? = currentSensor
    fun getSensorMeta(): SensorTypeMeta? = currentMeta

    /**
     * 进入详情页时调用 — 启动传感器实时监听
     */
    fun startListening(sensor: SensorItemInfo) {
        currentSensor = sensor
        currentMeta = SensorTypeMeta.fromTypeId(sensor.type)
        repo.enableSensor(sensor.type)
    }

    /**
     * 离开详情页时调用 — 立即停止传感器监听
     */
    fun stopListening() {
        repo.disableSensor()
        currentSensor = null
        currentMeta = null
    }

    /**
     * 获取格式化的实时数值字符串
     */
    fun formatValue(data: SensorLiveData?, index: Int): String {
        val meta = currentMeta ?: return "---"
        data ?: return "---"
        if (index >= data.valueCount) return "---"
        val v = data.values[index]
        if (v.isNaN()) return "---"

        return when (meta) {
            SensorTypeMeta.ORIENTATION,
            SensorTypeMeta.GYROSCOPE,
            SensorTypeMeta.GYROSCOPE_UNCALIBRATED -> "%.4f".format(v)
            SensorTypeMeta.ROTATION_VECTOR,
            SensorTypeMeta.GAME_ROTATION_VECTOR,
            SensorTypeMeta.GEOMAGNETIC_ROTATION_VECTOR -> "%.6f".format(v)
            else -> "%.2f".format(v)
        }
    }

    override fun onCleared() {
        super.onCleared()
        repo.disableSensor()
    }
}
