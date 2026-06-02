package com.example.deviceinfoviewer.data.source

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager

import com.example.deviceinfoviewer.data.model.SensorItemInfo

/**
 * 传感器数据源，通过 SensorManager 获取所有传感器信息
 */
class SensorDataSource(private val context: Context) {

    private val appContext = context.applicationContext

    fun getAllSensors(): List<SensorItemInfo> {
        val result = mutableListOf<SensorItemInfo>()
        val sm = appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            ?: return result

        val sensors = sm.getSensorList(Sensor.TYPE_ALL)
        for (sensor in sensors) {
            val item = SensorItemInfo()
            item.name = sensor.name
            item.type = sensor.type
            item.vendor = sensor.vendor
            item.powerMa = sensor.power
            item.maxRange = sensor.maximumRange
            item.resolution = sensor.resolution
            item.minDelay = sensor.minDelay
            result.add(item)
        }

        return result
    }
}
