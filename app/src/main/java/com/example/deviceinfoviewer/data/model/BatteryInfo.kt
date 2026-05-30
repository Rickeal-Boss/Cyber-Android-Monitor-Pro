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
    var internalResistanceMOhm: Float = Float.NaN,  // 估算内阻 (mΩ)
    var protocolDetected: String = ""        // 充电协议: USB-PD / QC3.0 / etc.
) {
    @Deprecated("Use chargingPowerMw/dischargingPowerMw", ReplaceWith("if (isCharging) chargingPowerMw else dischargingPowerMw"))
    val powerMilliwatts: Long
        get() = if (isCharging) chargingPowerMw else dischargingPowerMw

    /** 获取有效电压（双电芯×2） */
    val effectiveVoltage: Int
        get() = if (dualCell && voltage > 0) voltage * 2 else voltage
}
