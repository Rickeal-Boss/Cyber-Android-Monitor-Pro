package com.example.deviceinfoviewer.data.model

/**
 * 电池信息 - 增强版：区分充放电、双电芯支持、循环次数全网方案
 */
data class BatteryInfo(
    var temperatureCelsius: Float = Float.NaN,
    var chargingPowerMw: Long = -1L,
    var dischargingPowerMw: Long = -1L,
    var isCharging: Boolean = false,
    var currentNowUA: Long = 0L,          // 带符号的电流(µA), 正=充电 负=放电
    var cycleCount: Int = -1,
    var capacityNowMAh: Long = -1L,
    var capacityDesignMAh: Long = -1L,
    var chargeFullMAh: Long = -1L,         // sysfs charge_full (当前满电容量)
    var chargeFullDesignMAh: Long = -1L,   // sysfs charge_full_design
    var levelPercent: Int = -1,
    var chargeStatus: String = "",
    var health: String = "",
    var technology: String = "",
    var voltage: Int = -1,
    var timestamp: Long = 0L,
    var dualCell: Boolean = false,         // 双电芯模式

    // 数据来源追踪
    var cycleCountSource: String = "",     // 循环次数数据来源
    var chargeFullSource: String = "",     // 当前容量数据来源
    var currentNowSource: String = "",     // 电流数据来源

    // dumpsys battery 附加信息
    var maxChargingCurrentUA: Long = -1L,  // 最大充电电流
    var maxChargingVoltageUV: Long = -1L,  // 最大充电电压
    var chargeCounterUAh: Long = -1L,      // 已充电量计数器
    var chargerType: String = "",           // USB / AC / Wireless / Dock
    var chargerTypeFromPlugged: String = "", // 硬件级充电类型 (EXTRA_PLUGGED)
    var isPlugged: Boolean = false,          // 是否物理连接充电器
    var internalResistanceMOhm: Float = Float.NaN,  // 估算内阻 (mΩ)
    var protocolDetected: String = "",        // 充电协议: USB-PD / QC3.0 / etc.

    // ── 增强健康度指标 ──
    var sohPercent: Float = Float.NaN,         // State of Health: charge_full / charge_full_design × 100%
    var chargeRemainingTimeMin: Int = -1,       // 预估充电剩余时间 (min)
    var dischargeRemainingTimeMin: Int = -1,    // 预估放电剩余时间 (min)
    var estimatedOcvMv: Int = -1,               // 估算开路电压 OCV (mV), 零电流时采样
    var capacityFadePercent: Float = Float.NaN, // 容量衰减百分比 (100% - SoH)
    var temperatureNormalized: Boolean = false,  // 容量补偿温度标志
    var capacityRemainingUAh: Long = -1L,          // capacity_now 剩余绝对容量 (µAh)
    var chargeNowUAh: Long = -1L,                   // charge_now 电荷量 (µAh)
    var capacityMaxPct: Int = -1,                    // capacity_max 锁容上限 (Xiaomi)
    var isCapacityLocked: Boolean = false,           // 是否检测到锁容
    var capacityLockLevel: Int = -1,                  // 锁容百分比 (如 80)
    var powerProfileCapacityMAh: Long = -1L,          // power_profile.xml 配置容量

    // ── 电池增强指标 (2026-06-18) ──
    // 电源来源分类标签 — 从 EXTRA_PLUGGED + dumpsys 综合判定
    var powerSourceLabel: String = "",                // 电源来源: AC/USB/无线/底座/电池
    var wattageNow: Double = Double.NaN,              // 预计算实时瓦特数 (V × I)
    var currentNormalizedMa: Int = 0,                 // BBK 归一化电流 (mA, 含方向)

    // ── 电池健康度 (基于 AOSP 标准 API) ──
    // BATTERY_PROPERTY_STATE_OF_HEALTH (propId=10) — AOSP 标准属性 (Android 14+)
    var healthPercent: Int = -1                       // 电池健康百分比 (0-100, -1=不支持)
) {
    @Deprecated("Use chargingPowerMw/dischargingPowerMw", ReplaceWith("if (isCharging) chargingPowerMw else dischargingPowerMw"))
    val powerMilliwatts: Long
        get() = if (isCharging) chargingPowerMw else dischargingPowerMw

    /** 获取有效电压（双电芯×2） */
    val effectiveVoltage: Int
        get() = if (dualCell && voltage > 0) voltage * 2 else voltage
}
