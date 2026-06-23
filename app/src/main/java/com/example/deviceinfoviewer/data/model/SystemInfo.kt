package com.example.deviceinfoviewer.data.model

data class SystemInfo(
    var buildFields: MutableMap<String, String> = mutableMapOf(),
    var androidVersion: String = "",
    var kernelVersion: String = "",
    var javaVmVersion: String = "",
    var javaRuntimeName: String = "",
    var bootloader: String = "",
    var securityPatch: String = "",
    /** 系统已开机时长 (秒) */
    var uptimeSeconds: Long = 0L,
    // 深度待机统计
    /** 累计深度待机时间 (秒) — 基于 sysfs cpuidle 聚合 */
    var deepSleepSeconds: Long = 0L,
    /** 待机效率 (深睡/总运行时间 × 100%) */
    var sleepEfficiency: Float = Float.NaN,
    /** 数据来源描述 */
    var sleepSource: String = "",
    /** Doze 模式累计时长 (秒) */
    var dozeTimeSeconds: Long = 0L,
    /** 唤醒次数 (基于 dumpsys batterystats) */
    var wakeupCount: Long = 0L,
)
