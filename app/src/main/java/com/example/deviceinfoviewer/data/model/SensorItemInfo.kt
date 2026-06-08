package com.example.deviceinfoviewer.data.model

data class SensorItemInfo(
    var name: String = "",
    var type: Int = -1,
    var vendor: String = "",
    var powerMa: Float = Float.NaN,
    var maxRange: Float = Float.NaN,
    var resolution: Float = Float.NaN,
    var minDelay: Int = -1,
    var sensorId: Int = -1,
    var version: Int = -1,
    var isDynamic: Boolean = false,
    var isWakeUp: Boolean = false,
    var reportingMode: Int = -1,
    var typeName: String = ""
)

/**
 * 传感器实时采样数据 — 单次采样
 */
data class SensorLiveData(
    val values: FloatArray = FloatArray(0),
    val timestampMs: Long = 0L,
    val accuracy: Int = 0,
    val sensorType: Int = -1,
    val sensorName: String = ""
) {
    val x: Float get() = if (values.size >= 1) values[0] else Float.NaN
    val y: Float get() = if (values.size >= 2) values[1] else Float.NaN
    val z: Float get() = if (values.size >= 3) values[2] else Float.NaN
    val valueCount: Int get() = values.size

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SensorLiveData) return false
        return values.contentEquals(other.values) &&
                timestampMs == other.timestampMs &&
                accuracy == other.accuracy &&
                sensorType == other.sensorType
    }

    override fun hashCode(): Int {
        var result = values.contentHashCode()
        result = 31 * result + timestampMs.hashCode()
        result = 31 * result + accuracy
        result = 31 * result + sensorType
        return result
    }
}

/**
 * 传感器类型 → 中文名 + 单位 + 轴标签映射
 */
enum class SensorTypeMeta(
    val typeId: Int,
    val displayName: String,
    val unit: String,
    val axisLabels: List<String> = listOf("X", "Y", "Z"),
    val valueCount: Int = 3
) {
    ACCELEROMETER(1, "加速度传感器", "m/s²"),
    MAGNETIC_FIELD(2, "磁力计", "μT"),
    ORIENTATION(3, "方向传感器", "°"),
    GYROSCOPE(4, "陀螺仪", "rad/s"),
    LIGHT(5, "光线传感器", "lx", listOf("强度"), 1),
    PRESSURE(6, "压力传感器", "hPa", listOf("压力"), 1),
    PROXIMITY(8, "距离传感器", "cm", listOf("距离"), 1),
    GRAVITY(9, "重力传感器", "m/s²"),
    LINEAR_ACCELERATION(10, "线性加速度传感器", "m/s²"),
    ROTATION_VECTOR(11, "旋转矢量传感器", ""),
    HUMIDITY(12, "湿度传感器", "%", listOf("湿度"), 1),
    AMBIENT_TEMPERATURE(13, "环境温度传感器", "°C", listOf("温度"), 1),
    MAGNETIC_FIELD_UNCALIBRATED(14, "磁场传感器(未校准)", "μT"),
    GAME_ROTATION_VECTOR(15, "游戏旋转矢量传感器", ""),
    GYROSCOPE_UNCALIBRATED(16, "陀螺仪(未校准)", "rad/s"),
    GEOMAGNETIC_ROTATION_VECTOR(20, "地磁旋转矢量传感器", ""),
    ACCELEROMETER_UNCALIBRATED(35, "加速度传感器(未校准)", "m/s²");

    companion object {
        fun fromTypeId(type: Int): SensorTypeMeta? = entries.find { it.typeId == type }
    }
}
