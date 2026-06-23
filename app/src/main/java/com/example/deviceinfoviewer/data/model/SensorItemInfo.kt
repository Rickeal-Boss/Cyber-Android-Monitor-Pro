package com.example.deviceinfoviewer.data.model

import android.content.Context
import androidx.annotation.StringRes
import com.example.deviceinfoviewer.R

data class SensorItemInfo(
    val name: String = "",
    val type: Int = -1,
    val vendor: String = "",
    val powerMa: Float = Float.NaN,
    val maxRange: Float = Float.NaN,
    val resolution: Float = Float.NaN,
    val minDelay: Int = -1,
    val sensorId: Int = -1,
    val version: Int = -1,
    val isDynamic: Boolean = false,
    val isWakeUp: Boolean = false,
    val reportingMode: Int = -1
    // ── 注意: 传感器显示名称统一通过 SensorTypeMeta.getDisplayName(type, context) 获取,
    //         不存储到 data class 中, 以保持单一数据源、支持运行时 i18n 切换.
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
 * 传感器类型 → 显示名称(资源ID) + 单位 + 轴标签(资源ID) 映射
 * displayName 和 axisLabels 通过资源 ID 引用，使用 Context.getString() 解析以支持国际化
 */
enum class SensorTypeMeta(
    val typeId: Int,
    @StringRes val displayNameResId: Int,
    val unit: String,
    @StringRes val axisLabelResIds: List<Int> = listOf(R.string.sensor_axis_x, R.string.sensor_axis_y, R.string.sensor_axis_z),
    val valueCount: Int = 3
) {
    ACCELEROMETER(1, R.string.sensor_type_accelerometer, "m/s²"),
    MAGNETIC_FIELD(2, R.string.sensor_type_magnetic_field, "μT"),
    ORIENTATION(3, R.string.sensor_type_orientation, "°"),
    GYROSCOPE(4, R.string.sensor_type_gyroscope, "rad/s"),
    LIGHT(5, R.string.sensor_type_light, "lx", listOf(R.string.sensor_axis_illumination), 1),
    PRESSURE(6, R.string.sensor_type_pressure, "hPa", listOf(R.string.sensor_axis_pressure), 1),
    PROXIMITY(8, R.string.sensor_type_proximity, "cm", listOf(R.string.sensor_axis_distance), 1),
    GRAVITY(9, R.string.sensor_type_gravity, "m/s²"),
    LINEAR_ACCELERATION(10, R.string.sensor_type_linear_accel, "m/s²"),
    ROTATION_VECTOR(11, R.string.sensor_type_rotation_vector, ""),
    HUMIDITY(12, R.string.sensor_type_humidity, "%", listOf(R.string.sensor_axis_humidity), 1),
    AMBIENT_TEMPERATURE(13, R.string.sensor_type_ambient_temp, "°C", listOf(R.string.sensor_axis_temperature), 1),
    MAGNETIC_FIELD_UNCALIBRATED(14, R.string.sensor_type_magnetic_uncalibrated, "μT"),
    GAME_ROTATION_VECTOR(15, R.string.sensor_type_game_rotation_vector, ""),
    GYROSCOPE_UNCALIBRATED(16, R.string.sensor_type_gyroscope_uncalibrated, "rad/s"),
    GEOMAGNETIC_ROTATION_VECTOR(20, R.string.sensor_type_geomagnetic_rotation_vector, ""),
    ACCELEROMETER_UNCALIBRATED(35, R.string.sensor_type_accelerometer_uncalibrated, "m/s²");

    companion object {
        fun fromTypeId(type: Int): SensorTypeMeta? = entries.find { it.typeId == type }

        /**
         * 根据 Context 解析传感器类型的本地化显示名称
         */
        fun getDisplayName(type: Int, context: Context): String {
            val meta = fromTypeId(type)
            return if (meta != null) context.getString(meta.displayNameResId)
            else context.getString(R.string.sensor_info_type_fallback, type)
        }

        /**
         * 根据 Context 解析轴标签的本地化名称
         */
        fun getAxisLabel(type: Int, axisIndex: Int, context: Context): String {
            val meta = fromTypeId(type) ?: return when (axisIndex) {
                0 -> "X"; 1 -> "Y"; 2 -> "Z"; else -> "Axis$axisIndex"
            }
            val ids = meta.axisLabelResIds
            return if (axisIndex in ids.indices) context.getString(ids[axisIndex])
            else "Axis$axisIndex"
        }

        /**
         * 光线传感器全量程照度等级描述
         * 参考: CIE 标准照度范围
         */
        fun describeLightLevel(lux: Float, context: Context): String = when {
            lux <= 0.01f -> context.getString(R.string.light_level_pitch_black)
            lux <= 0.1f -> context.getString(R.string.light_level_starlight)
            lux <= 1f -> context.getString(R.string.light_level_moonlight)
            lux <= 3.4f -> context.getString(R.string.light_level_deep_dusk)
            lux <= 10f -> context.getString(R.string.light_level_dusk)
            lux <= 50f -> context.getString(R.string.light_level_twilight)
            lux <= 100f -> context.getString(R.string.light_level_dark_indoor)
            lux <= 500f -> context.getString(R.string.light_level_normal_indoor)
            lux <= 1000f -> context.getString(R.string.light_level_bright_indoor)
            lux <= 2500f -> context.getString(R.string.light_level_overcast)
            lux <= 10000f -> context.getString(R.string.light_level_cloudy)
            lux <= 25000f -> context.getString(R.string.light_level_shade)
            lux <= 50000f -> context.getString(R.string.light_level_half_daylight)
            lux <= 100000f -> context.getString(R.string.light_level_full_daylight)
            else -> context.getString(R.string.light_level_direct_sun)
        }

        /**
         * 距离传感器多档状态描述 (cm)
         *
         * 性能/稳定性修复 (2026-06-20):
         * - 原 BUG: `getString(resId, "%.1f".format(maxRange * 0.25f))` 传入 String,
         *   但资源占位符是 `%.1f` (期望 Float/Double) → String.format 抛
         *   IllegalFormatConversionException: f != java.lang.String → 距离传感器闪退
         * - 修复: 直接传 Float 给 getString, 让 String.format 内部处理格式化
         * - 边界: maxRange 为 NaN 或 0 时降级为安全默认值 (避免 NaN 传播)
         */
        fun describeProximityState(distance: Float, maxRange: Float, context: Context): String {
            // 安全校验: maxRange 异常时降级 (某些 OEM 传感器返回 NaN/0/负数)
            val safeMaxRange = when {
                maxRange.isNaN() || maxRange.isInfinite() -> 5f  // 通用距离传感器典型量程
                maxRange <= 0.001f -> 5f
                else -> maxRange
            }
            // distance 异常时按"远离"处理
            val safeDistance = when {
                distance.isNaN() || distance.isInfinite() -> safeMaxRange + 1f
                else -> distance
            }
            return when {
                safeDistance <= 0f -> context.getString(R.string.proximity_state_contact)
                safeDistance <= 0.5f -> context.getString(R.string.proximity_state_near)
                safeDistance <= 2f -> context.getString(R.string.proximity_state_close)
                safeDistance <= safeMaxRange * 0.25f ->
                    context.getString(R.string.proximity_state_fair, safeMaxRange * 0.25f)
                safeDistance <= safeMaxRange * 0.5f ->
                    context.getString(R.string.proximity_state_mid, safeMaxRange * 0.5f)
                safeDistance <= safeMaxRange * 0.75f ->
                    context.getString(R.string.proximity_state_far, safeMaxRange * 0.75f)
                else -> context.getString(R.string.proximity_state_out)
            }
        }
    }
}
