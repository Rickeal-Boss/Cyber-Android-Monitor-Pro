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
 * 包含光线传感器全量程照度等级和距离传感器多档状态
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
    LIGHT(5, "光线传感器", "lx", listOf("照度"), 1),
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

        /**
         * 光线传感器全量程照度等级描述
         * 参考: CIE 标准照度范围
         */
        fun describeLightLevel(lux: Float): String = when {
            lux <= 0.01f -> "全黑 (0.001-0.01 lx)"
            lux <= 0.1f -> "极暗夜光 (0.01-0.1 lx)"
            lux <= 1f -> "昏暗月光 (0.1-1 lx)"
            lux <= 3.4f -> "深黄昏 (1-3.4 lx)"
            lux <= 10f -> "黄昏 (3.4-10 lx)"
            lux <= 50f -> "暮色 (10-50 lx)"
            lux <= 100f -> "室内暗光 (50-100 lx)"
            lux <= 500f -> "普通室内 (100-500 lx)"
            lux <= 1000f -> "明亮室内 (500-1000 lx)"
            lux <= 2500f -> "阴天室外 (1000-2500 lx)"
            lux <= 10000f -> "多云/有云 (2500-10000 lx)"
            lux <= 25000f -> "晴天阴处 (10000-25000 lx)"
            lux <= 50000f -> "半日光 (25000-50000 lx)"
            lux <= 100000f -> "全日光 (50000-100000 lx)"
            else -> "强烈日照 (>100000 lx)"
        }

        /**
         * 距离传感器多档状态描述 (cm)
         */
        fun describeProximityState(distance: Float, maxRange: Float): String = when {
            distance <= 0f -> "接触 (0 cm)"
            distance <= 0.5f -> "贴近 (Near, ≤0.5 cm)"
            distance <= 2f -> "接近 (Close, ≤2 cm)"
            distance <= maxRange * 0.25f -> "较近 (Fair, ≤${"%.1f".format(maxRange * 0.25f)} cm)"
            distance <= maxRange * 0.5f -> "中等 (Mid, ≤${"%.1f".format(maxRange * 0.5f)} cm)"
            distance <= maxRange * 0.75f -> "较远 (Far, ≤${"%.1f".format(maxRange * 0.75f)} cm)"
            else -> "远离 (Out of range)"
        }
    }
}
