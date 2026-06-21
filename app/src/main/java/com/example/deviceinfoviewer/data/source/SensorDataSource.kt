package com.example.deviceinfoviewer.data.source

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.deviceinfoviewer.data.model.SensorItemInfo
import com.example.deviceinfoviewer.data.model.SensorLiveData
import com.example.deviceinfoviewer.data.model.SensorTypeMeta

/**
 * 传感器数据源 — 静态列表 + 按需实时监听
 */
class SensorDataSource(private val context: Context) {

    companion object {
        private const val TAG = "SensorDS"

        /**
         * 反射调用 Sensor.getId() — API 24+，安全兼容 minSdk 21
         */
        fun safeGetSensorId(sensor: Sensor): Int {
            return try {
                val m = Sensor::class.java.getMethod("getId")
                m.invoke(sensor) as? Int ?: -1
            } catch (_: Throwable) { -1 }
        }

        /**
         * 反射调用 Sensor.isDynamic() — API 24+，安全兼容 minSdk 21
         */
        fun safeIsDynamic(sensor: Sensor): Boolean {
            return try {
                val m = Sensor::class.java.getMethod("isDynamic")
                m.invoke(sensor) as? Boolean ?: false
            } catch (_: Throwable) { false }
        }
    }

    private val appContext = context.applicationContext
    private val sm: SensorManager? by lazy {
        appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    }
    private val handler = Handler(Looper.getMainLooper())

    // 实时监听回调注册表 — 同一时间最多一个活跃的监听
    @Volatile
    private var activeSensorType: Int = -1
    @Volatile
    private var activeCallback: ((SensorLiveData) -> Unit)? = null

    private val sensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            event ?: return
            if (event.sensor.type != activeSensorType) return
            val meta = SensorTypeMeta.fromTypeId(event.sensor.type)
            activeCallback?.invoke(
                SensorLiveData(
                    values = event.values.clone(),
                    timestampMs = event.timestamp / 1_000_000L,
                    accuracy = event.accuracy,
                    sensorType = event.sensor.type,
                    sensorName = event.sensor.name
                )
            )
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    fun getAllSensors(): List<SensorItemInfo> {
        val result = mutableListOf<SensorItemInfo>()
        val manager = sm ?: return result

        val sensors = try {
            manager.getSensorList(Sensor.TYPE_ALL)
        } catch (e: Throwable) {
            Log.w(TAG, "获取传感器列表失败", e)
            return result
        }

        for (sensor in sensors) {
            result.add(
                SensorItemInfo(
                    name = sensor.name,
                    type = sensor.type,
                    vendor = sensor.vendor.ifEmpty { sensor.name.split(" ").firstOrNull() ?: context.getString(com.example.deviceinfoviewer.R.string.common_unknown) },
                    powerMa = if (sensor.power > 0f) sensor.power else Float.NaN,
                    maxRange = sensor.maximumRange,
                    resolution = if (sensor.resolution > 0f) sensor.resolution else Float.NaN,
                    minDelay = if (sensor.minDelay > 0) sensor.minDelay else -1,
                    sensorId = safeGetSensorId(sensor),
                    version = sensor.version,
                    isDynamic = safeIsDynamic(sensor),
                    isWakeUp = sensor.isWakeUpSensor,
                    reportingMode = sensor.reportingMode
                )
            )
        }

        return result
    }

    /**
     * 开始监听指定类型传感器 — 仅用户在第二层详情页时调用
     */
    fun startListening(sensorType: Int, callback: (SensorLiveData) -> Unit) {
        val manager = sm ?: return
        stopListening() // 确保上一监听已停止

        val sensor = try {
            manager.getDefaultSensor(sensorType)
        } catch (e: Throwable) {
            Log.w(TAG, "获取默认传感器失败 type=$sensorType", e)
            null
        }

        if (sensor == null) {
            Log.w(TAG, "设备不支持传感器类型: $sensorType")
            return
        }

        activeSensorType = sensorType
        activeCallback = callback

        try {
            // 采样率说明:
            // - 距离传感器通常是 on-change 模式 (reportingMode=1), 采样率参数被忽略,
            //   但某些 OEM 实现不完全遵循, 过低值可能导致高频回调加剧主线程负载
            // - 距离传感器典型 minDelay ≥ 50ms, 30ms 低于多数设备下限, SensorManager
            //   会自动调整到设备支持的最小延迟, 但仍传入合理值避免误导
            // - 2026-06-20: 从 30ms 调整为 100ms (距离状态变化不频繁, 无需高频)
            val samplingPeriod = when (sensorType) {
                Sensor.TYPE_LIGHT -> 50_000        // 光线传感器: 50ms 精细采样 (全量程照度识别)
                Sensor.TYPE_PROXIMITY -> 100_000   // 距离传感器: 100ms (on-change, 无需高频)
                else -> SensorManager.SENSOR_DELAY_UI // ~60ms
            }
            manager.registerListener(sensorEventListener, sensor, samplingPeriod, handler)
            Log.d(TAG, "传感器监听已启动 type=$sensorType name=${sensor.name} sampling=${samplingPeriod}μs")
        } catch (e: Throwable) {
            Log.w(TAG, "注册传感器监听失败 type=$sensorType", e)
            activeSensorType = -1
            activeCallback = null
        }
    }

    /**
     * 停止当前传感器监听
     */
    fun stopListening() {
        if (activeSensorType < 0) return
        try {
            sm?.unregisterListener(sensorEventListener)
        } catch (e: Throwable) {
            Log.w(TAG, "注销传感器监听异常", e)
        }
        activeSensorType = -1
        activeCallback = null
        Log.d(TAG, "传感器监听已停止")
    }

    /**
     * 检查指定类型传感器是否可用
     */
    fun hasSensor(sensorType: Int): Boolean {
        return try {
            sm?.getDefaultSensor(sensorType) != null
        } catch (e: Throwable) { false }
    }

    }

