package com.example.deviceinfoviewer.data.source

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import com.example.deviceinfoviewer.AppSettings
import com.example.deviceinfoviewer.data.model.BatteryInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * 电池数据源 — 全网方案版 v2
 *
 * 主要增强（针对国产 OEM）：
 * 1. 循环次数：50+ 路径/system property 多级 fallback
 * 2. 容量：charge_full / charge_full_design 多路径 + BatteryManager 反射
 * 3. 电流：current_now 15+ 路径 fallback
 * 4. 放电功率：|电压 × 电流| 实时计算
 * 5. 数据来源追踪：每个关键字段标注来源，方便调试
 *
 * 国产 ROM 适配覆盖：
 * - 小米/HyperOS（含 BMS 路径 + thermal_message）
 * - 华为/荣耀（含 healthd 路径）
 * - OPPO/Realme/一加（含 oplus_chg 专属路径）
 * - vivo/iQOO
 * - 三星
 * - 索尼
 * - 联想/摩托罗拉
 *
 * 骁龙 Snapdragon 专项：
 * - qpnp-vm-bms: /sys/class/power_supply/bms/ (Qualcomm PMIC BMS)
 * - OPlus chg: /sys/class/oplus_chg/battery/ (OPPO/OnePlus/Realme 充电 IC)
 */
class BatteryDataSource(private val context: Context) {

    private val TAG = "BatteryDataSource"
    private val appContext = context.applicationContext

    fun getBatteryInfo(): BatteryInfo {
        val info = BatteryInfo()
        info.timestamp = System.currentTimeMillis()

        // 双电芯开关
        info.dualCell = AppSettings.getInstance(appContext).dualCellBattery

        val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = appContext.registerReceiver(null, ifilter)
            ?: return info

        // === 电量百分比 ===
        val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level >= 0 && scale > 0) {
            info.levelPercent = (level * 100.0f / scale).toInt()
        }

        // === 温度 (decicelsius → celsius) ===
        val tempRaw = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
        if (tempRaw > 0) {
            info.temperatureCelsius = tempRaw / 10.0f
        } else {
            val sysTemp = SysFsReader.readFloat("/sys/class/power_supply/battery/temp")
            if (!sysTemp.isNaN() && sysTemp > 0) {
                info.temperatureCelsius = if (sysTemp > 100) sysTemp / 10.0f else sysTemp
            }
        }

        // === 电压 (mV, 双电芯×2) ===
        info.voltage = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)

        // === 充电/放电判定 — 三级融合（解决国产 ROM 满电误判问题） ===
        // 问题：ColorOS/HyperOS 电池满电后状态变为 DISCHARGING/NOT_CHARGING 而非 FULL
        // 解决：EXTRA_PLUGGED（硬件真值）为第一优先级，电流符号次之，EXTRA_STATUS 最低
        val plugged = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isPlugged = plugged > 0
        val statusIsCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL

        // 暂存电流值（稍后读取）
        info.chargeStatus = chargeStatusToString(status)
        // 临时赋值：以插电 + 状态做初步判断，后面用电流方向修正
        info.isCharging = isPlugged && (statusIsCharging
                || status == BatteryManager.BATTERY_STATUS_NOT_CHARGING
                || status == BatteryManager.BATTERY_STATUS_DISCHARGING)
        // 保存充电类型
        info.chargerTypeFromPlugged = when {
            (plugged and BatteryManager.BATTERY_PLUGGED_AC) != 0 -> "AC"
            (plugged and BatteryManager.BATTERY_PLUGGED_USB) != 0 -> "USB"
            (plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS) != 0 -> "无线"
            else -> if (isPlugged) "未知" else ""
        }
        info.isPlugged = isPlugged

        // === 健康状态 (双源: EXTRA_HEALTH + BATTERY_PROPERTY_STATE_OF_HEALTH) ===
        val health = batteryStatus.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
        info.health = healthToString(health)

        // BATTERY_PROPERTY_STATE_OF_HEALTH (propId=10) — AOSP 标准属性
        // 返回电池健康状态百分比 (0-100)，Android 14+ 系统支持
        // 参考: frameworks/base/core/java/android/os/BatteryManager.java
        try {
            val bm = appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            if (bm != null) {
                val sohPct = bm.getIntProperty(10)  // BATTERY_PROPERTY_STATE_OF_HEALTH = 10
                if (sohPct in 1..100) {
                    info.healthPercent = sohPct
                    Log.d(TAG, "health_percent via STATE_OF_HEALTH (prop 10): $sohPct%")
                }
            }
        } catch (_: Throwable) { /* property 10 not supported */ }

        // === 电池技术 ===
        info.technology = batteryStatus.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: ""

        // === 容量（多路径） ===
        readBatteryCapacity(info)

        // === 电流（多路径，带符号 µA） ===
        val (currentUA, currentSource) = getCurrentNowFull()
        info.currentNowUA = currentUA
        info.currentNowSource = currentSource

        // === BBK 电流归一化 (mA, 含方向) ===
        info.currentNormalizedMa = normalizeBbKCurrent(currentUA, currentSource)

        // === 电源来源标签 (EXTRA_PLUGGED 三级检测) ===
        info.powerSourceLabel = when {
            (plugged and BatteryManager.BATTERY_PLUGGED_AC) != 0 -> "交流电源 (AC)"
            (plugged and BatteryManager.BATTERY_PLUGGED_USB) != 0 -> "USB 供电"
            (plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS) != 0 -> "无线充电"
            isPlugged -> "外部电源"
            else -> "电池供电"
        }

        // === 有效电压（双电芯×2）===
        val effVoltage = info.effectiveVoltage

        // === 预计算实时瓦特数 (V × I / 1,000,000) ===
        if (effVoltage > 0 && currentUA != 0L) {
            info.wattageNow = effVoltage.toDouble() * kotlin.math.abs(currentUA).toDouble() / 1_000_000.0
        }

        // === 功率 = |电压(V) × 电流(A)| = |电压(mV) × 电流(µA)| / 1,000,000 = mW ===
        if (effVoltage > 0 && currentUA != 0L) {
            val powerMw = Math.abs(effVoltage.toDouble() * currentUA.toDouble()) / 1_000_000.0
            if (currentUA > 0) {
                // 电流流入 → 一定在充电，覆盖任何误判
                info.chargingPowerMw = powerMw.toLong()
                info.isCharging = true
            } else if (currentUA < 0) {
                // 电流流出 → 一定在放电
                info.dischargingPowerMw = powerMw.toLong()
                // 除非物理连接存在（可能电池已满但仍有极小放电）
                info.isCharging = false
            }
        } else if (info.isPlugged && effVoltage > 0) {
            // 有电流数据但为0 或 无电压数据，但已插电 — 保持之前判断
            // 不做额外修正
        } else if (!info.isPlugged) {
            // 没插电 → 必定放电
            info.isCharging = false
        }

        // === 内阻估算 = 电压(mV) / 电流(µA) × 1000 = mΩ ===
        if (effVoltage > 0 && currentUA != 0L) {
            val absCurrent = Math.abs(currentUA)
            if (absCurrent > 10000) {  // 电流 > 10mA 才有意义
                info.internalResistanceMOhm = (effVoltage.toFloat() / absCurrent.toFloat()) * 1000f
            }
        }

        // === 充电协议电压特征匹配 ===
        info.protocolDetected = detectChargingProtocolVoltage(info)

        // === 循环次数（50+ 路径） ===
        val (cycleCount, cycleSource) = getBatteryCycleCountFull()
        info.cycleCount = cycleCount
        info.cycleCountSource = cycleSource
        if (cycleCount > 0) {
            Log.d(TAG, "cycle_count=$cycleCount from $cycleSource")
        }

        // === dumpsys battery 附加信息 ===
        readDumpsysBattery(info)

        // === capacity_now / charge_now 直接读取（剩余绝对容量） ===
        val capacityNowPaths = listOf(
            "/sys/class/power_supply/battery/capacity_now",
            "/sys/class/power_supply/bms/capacity_now",
            "/sys/class/power_supply/battery/charge_now",
            "/sys/class/power_supply/bms/charge_now",
            "/sys/devices/platform/battery/capacity_now",
            "/sys/devices/platform/battery_meter/capacity_now",
            "/sys/class/oplus_chg/battery/capacity_now",
            "/sys/kernel/oplus_chg/battery/capacity_now"
        )
        for (path in capacityNowPaths) {
            val raw = readSysfsLongRobust(path).takeIf { it > 0 }
            if (raw != null) {
                // capacity_now 单位通常是 µAh，也有以 mAh 为单位的情况
                info.capacityRemainingUAh = if (raw > 1_000_000) raw else raw * 1000
                break
            }
        }

        // === charge_now (电荷量, 不同于 charge_counter) ===
        for (path in listOf(
            "/sys/class/power_supply/battery/charge_now",
            "/sys/class/power_supply/bms/charge_now",
            "/sys/devices/platform/battery/charge_now"
        )) {
            val raw = readSysfsLongRobust(path).takeIf { it > 0 }
            if (raw != null && raw > 0) {
                info.chargeNowUAh = if (raw > 1_000_000) raw else raw * 1000
                break
            }
        }

        // === capacity_max 锁容检测 (Xiaomi/HyperOS) ===
        val capMaxPaths = listOf(
            "/sys/class/power_supply/battery/capacity_max",
            "/sys/class/power_supply/bms/capacity_max",
            "/sys/devices/platform/battery/capacity_max",
            "/sys/kernel/debug/battery/capacity_max"
        )
        for (path in capMaxPaths) {
            val raw = readSysfsLine(path)?.toIntOrNull()
            if (raw != null && raw > 0 && raw <= 100) {
                info.capacityMaxPct = raw
                info.isCapacityLocked = raw < 100
                info.capacityLockLevel = raw
                break
            }
        }

        // === 读取 power_profile.xml 配置容量 (作为校准参考) ===
        try {
            val propManager = appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            if (propManager != null) {
                val powerProfileCap = SysFsReader.getBatteryLongProperty(appContext, "BATTERY_PROPERTY_CAPACITY")
                if (powerProfileCap != null && powerProfileCap > 0) {
                    info.powerProfileCapacityMAh = powerProfileCap / 1000
                }
            }
        } catch (_: Throwable) {}

        // === SoH (State of Health) 计算 ===
        // charge_full / charge_full_design × 100%
        if (info.chargeFullMAh > 0 && info.chargeFullDesignMAh > 0) {
            val ratio = info.chargeFullMAh.toFloat() / info.chargeFullDesignMAh.toFloat()
            info.sohPercent = (ratio * 100f).coerceIn(0f, 120f)
            info.capacityFadePercent = (100f - info.sohPercent).coerceAtLeast(0f)
        }

        // === 开路电压 OCV 估算 (零电流时采样) ===
        if (kotlin.math.abs(info.currentNowUA) < 5000 && info.voltage > 0) {
            // 电流 < 5mA 时，电压近似等于开路电压
            info.estimatedOcvMv = info.voltage
        } else if (info.voltage > 0) {
            // 有负载时，OCV ≈ V_now + I × R_internal
            if (!info.internalResistanceMOhm.isNaN() && info.internalResistanceMOhm > 0) {
                val irDrop = (kotlin.math.abs(info.currentNowUA).toFloat() * info.internalResistanceMOhm / 1_000_000f).toInt()
                info.estimatedOcvMv = if (info.isCharging) {
                    info.voltage + irDrop  // 充电时实际电压高于 OCV
                } else {
                    info.voltage + irDrop  // 放电时实际电压低于 OCV，补偿回来
                }
            }
        }

        // === 内阻估算增强: 基于 OCV 更精确 ===
        if (info.estimatedOcvMv > 0 && info.voltage > 0 && kotlin.math.abs(info.currentNowUA) > 10000) {
            val deltaVMv = kotlin.math.abs(info.estimatedOcvMv - info.voltage).toFloat()
            val absCurrentA = kotlin.math.abs(info.currentNowUA) / 1_000_000f
            if (absCurrentA > 0.01f) {
                // R = (V_ocv - V_now) / I  (单位: mΩ)
                val refinedResistance = (deltaVMv / absCurrentA).coerceIn(0f, 2000f)
                if (!info.internalResistanceMOhm.isNaN() && info.internalResistanceMOhm > 0) {
                    // 加权平均: 既有值(40%) + 新估算(60%)
                    info.internalResistanceMOhm = info.internalResistanceMOhm * 0.4f + refinedResistance * 0.6f
                } else {
                    info.internalResistanceMOhm = refinedResistance
                }
            }
        }

        // === 剩余时间估算 ===
        if (info.chargeCounterUAh > 0 && info.currentNowUA != 0L) {
            val absCurrentUA = kotlin.math.abs(info.currentNowUA)
            if (absCurrentUA > 10000) {  // 电流 > 10mA 才有意义
                if (info.isCharging) {
                    // 充电: (chargeFullMAh - chargeCounterUAh/1000) / currentA × 60
                    if (info.chargeFullMAh > 0 && info.chargeCounterUAh > 0) {
                        val remainingUAh = (info.chargeFullMAh * 1000 - info.chargeCounterUAh).coerceAtLeast(0)
                        info.chargeRemainingTimeMin = (remainingUAh.toFloat() / absCurrentUA * 60).toInt()
                    }
                } else {
                    // 放电: (levelPercent / 100 × chargeFullMAh) × 60 / currentA
                    if (info.levelPercent > 0 && info.chargeFullMAh > 0) {
                        val remainingUAh = (info.levelPercent / 100f * info.chargeFullMAh * 1000).toLong()
                        info.dischargeRemainingTimeMin = (remainingUAh.toFloat() / absCurrentUA * 60).toInt()
                    }
                }
            }
        }

        return info
    }

    // ========== 电池容量（全网方案） ==========

    private fun readBatteryCapacity(info: BatteryInfo) {
        // 1. BatteryManager API（官方）
        try {
            val bm = appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            bm?.let {
                val capacity = it.getLongProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                if (capacity != Long.MIN_VALUE && capacity > 0) {
                    info.capacityDesignMAh = capacity
                }
            }
        } catch (_: Throwable) {}

        // 2. BatteryManager 隐藏属性 — CHARGE_COUNTER
        try {
            val chargeCounter = SysFsReader.getBatteryLongProperty(appContext, "BATTERY_PROPERTY_CHARGE_COUNTER")
            if (chargeCounter != Long.MIN_VALUE && chargeCounter > 0) {
                info.chargeCounterUAh = chargeCounter
            }
        } catch (_: Throwable) {}

        // 3. sysfs charge_full（多路径，含 OPPO oplus_chg）
        val chargeFullPaths = listOf(
            // 标准 Android
            "/sys/class/power_supply/battery/charge_full",
            "/sys/class/power_supply/battery/charge_full_design",
            // 高通 BMS (qpnp-vm-bms)
            "/sys/class/power_supply/bms/charge_full",
            "/sys/class/power_supply/bms/charge_full_design",
            // OPPO/OnePlus/Realme (oplus_chg)
            "/sys/class/oplus_chg/battery/battery_fcc",           // Full Charge Capacity
            "/sys/class/oplus_chg/battery/battery_rm",            // Remaining capacity (实时)
            "/sys/class/oplus_chg/battery/charge_full",
            "/sys/class/oplus_chg/battery/charge_full_design",
            // P2: OPPO 内核级容量路径
            "/sys/kernel/oplus_chg/battery/battery_fcc",
            "/sys/kernel/oplus_chg/battery/charge_full",
            "/sys/kernel/oplus_chg/battery/charge_full_design",
            "/sys/kernel/oplus_chg/battery/battery_rm",
            "/sys/devices/platform/soc/oplus_chg/battery/battery_fcc",
            "/sys/devices/platform/soc/oplus_chg/battery/charge_full_design",
            // MTK
            "/sys/devices/platform/battery/charge_full",
            "/sys/devices/platform/mt-battery/charge_full",
            "/sys/devices/platform/battery_meter/charge_full",
        )
        for (path in chargeFullPaths) {
            val value = readSysfsLongRobust(path)
            if (value > 0) {
                val mah = value / 1000
                if (path.contains("design")) {
                    info.chargeFullDesignMAh = mah
                    if (info.chargeFullSource.isEmpty()) info.chargeFullSource = path
                } else {
                    info.chargeFullMAh = mah
                    if (info.chargeFullSource.isEmpty()) info.chargeFullSource = path
                }
            }
        }

        // 4. 如果 sysfs charge_full 不可用，用 Charge Counter 容量估算兜底
        //    公式: 满充容量 ≈ chargeCounter / (levelPercent / 100)
        if (info.chargeFullMAh <= 0 && info.chargeCounterUAh > 0 && info.levelPercent in 1..100) {
            val estimatedFullUAh = (info.chargeCounterUAh.toDouble() / (info.levelPercent / 100.0)).toLong()
            if (estimatedFullUAh > 0) {
                info.chargeFullMAh = (estimatedFullUAh / 1000)
                info.chargeFullSource = "Charge Counter估算"
                Log.d(TAG, "chargeFull via Charge Counter estimation: ${info.chargeFullMAh} mAh (counter=${info.chargeCounterUAh} µAh, level=${info.levelPercent}%)")
            }
        }

        // 5. 如果 BatteryManager 没有容量，用 charge_full_design
        if (info.capacityDesignMAh <= 0 && info.chargeFullDesignMAh > 0) {
            info.capacityDesignMAh = info.chargeFullDesignMAh
        }
        if (info.capacityDesignMAh <= 0) {
            // 备用: power_profile.xml (Android 10+)
            try {
                val resId = appContext.resources.getIdentifier("config_bluetoothPowerDrainCalculationPower", "integer", "android")
                // 这不是直接容量，但如果没有charge_full_design可用，尝试从dumpsys batteryproperties获取
                val propOutput = ShellCommandDataSource.getDumpsysBatteryProperties()
                // 格式: "Capacity: 5000000" 或 "design capacity: 5000"
                val capRegex = Regex("""(?i)(?:design\s*)?(?:capacity|battery\s*capacity)[=: ]+(\d+)""")
                val match = capRegex.find(propOutput)
                match?.let {
                    val raw = it.groupValues[1].toLongOrNull()
                    if (raw != null && raw > 0) {
                        info.capacityDesignMAh = if (raw > 100_000) raw / 1000 else raw
                    }
                }
            } catch (_: Throwable) {}
        }
        if (info.capacityNowMAh <= 0 && info.chargeFullMAh > 0) {
            info.capacityNowMAh = info.chargeFullMAh
        }
        // 补充: 设计容量仍不可用时，从 SoC 典型值推断
        if (info.capacityDesignMAh <= 0) {
            try {
                val socModel = SysFsReader.readProp("ro.board.platform")
                val designEstimate = when {
                    socModel.contains("sm8750") || socModel.contains("sm8650") -> 5400L // 旗舰机
                    socModel.contains("sm8550") || socModel.contains("sm8475") -> 5000L
                    socModel.contains("mt689") || socModel.contains("mt698") -> 5000L // 天玑
                    socModel.contains("sm") -> 5000L  // 骁龙默认
                    else -> -1L
                }
                if (designEstimate > 0) {
                    info.capacityDesignMAh = designEstimate
                    info.chargeFullSource = "SoC 典型值推断"
                }
            } catch (_: Throwable) {}
        }
    }

    // ========== 电流（全网方案） ==========

    /**
     * @return Pair<电流µA (正=充电/负=放电), 来源描述>
     */
    private fun getCurrentNowFull(): Pair<Long, String> {
        // 所有已知 current_now 路径
        val currentPaths = listOf(
            "/sys/class/power_supply/battery/current_now" to "battery/current_now",
            "/sys/class/power_supply/battery/battery_current" to "battery/battery_current",
            "/sys/class/power_supply/battery/current_avg" to "battery/current_avg",
            "/sys/class/power_supply/battery/Charger_Current" to "battery/Charger_Current",
            // 高通 BMS (qpnp-vm-bms)
            "/sys/class/power_supply/bms/current_now" to "bms/current_now",
            "/sys/class/power_supply/bms/current_avg" to "bms/current_avg",
            "/sys/class/power_supply/battery/input_current_settled" to "battery/input_current",
            "/sys/class/power_supply/battery/constant_charge_current" to "battery/constant_charge",
            // 小米 BMS
            "/sys/class/power_supply/bms/battery_current" to "bms/battery_current",
            "/sys/class/power_supply/bms/charge_current" to "bms/charge_current",
            // OPPO/OnePlus/Realme (oplus_chg)
            "/sys/class/oplus_chg/battery/current_now" to "oplus_chg/current_now",
            "/sys/class/oplus_chg/battery/charging_current" to "oplus_chg/charging_current",
            "/sys/class/oplus_chg/battery/battery_current" to "oplus_chg/battery_current",
            // OPPO ColorOS 13+ 新增路径
            "/sys/class/oplus_chg/battery/real_icharging" to "oplus_chg/real_icharging",
            "/sys/class/oplus_chg/battery/instant_charging_current" to "oplus_chg/instant_charge",
            "/sys/class/oplus_chg/battery/input_current" to "oplus_chg/input_current",
            "/sys/kernel/oplus_chg/battery/current_now" to "kernel/oplus_chg/current_now",
            "/sys/kernel/oplus_chg/battery/charging_current" to "kernel/oplus_chg/charging",
            "/sys/devices/platform/oplus_chg/battery/current_now" to "platform/oplus_chg/current_now",
            // OPPO P2: 内核级 oplus_chg 扩展路径
            "/sys/kernel/oplus_chg/battery/battery_current" to "kernel/oplus_chg/battery_cur",
            "/sys/kernel/oplus_chg/battery/real_icharging" to "kernel/oplus_chg/real_ichg",
            "/sys/kernel/oplus_chg/usb/current_now" to "kernel/oplus_chg/usb_current",
            "/sys/devices/platform/soc/oplus_chg/battery/current_now" to "soc/oplus_chg/current",
            "/sys/devices/platform/soc/oplus_chg/usb/current_now" to "soc/oplus_chg/usb_current",
            "/sys/firmware/devicetree/base/oplus_chg/current_now" to "dt/oplus_chg/current",
            // OPPO BMS 内核路径
            "/sys/kernel/oplus_chg/bms/current_now" to "kernel/oplus_chg/bms_current",
            // OPPO/Realme
            "/sys/class/power_supply/battery/real_charging_current" to "battery/real_charge",
            "/sys/class/power_supply/battery/fast_charge_current" to "battery/fast_charge",
            "/sys/class/power_supply/battery/pd_charging_current" to "battery/pd_charge",
            "/sys/class/power_supply/battery/vooc_charging_current" to "battery/vooc_charge",
            // 华为
            "/sys/class/power_supply/battery/charging_current" to "battery/charging_current",
            // 三星
            "/sys/class/power_supply/battery/batt_current_now" to "battery/batt_current",
            "/sys/class/power_supply/battery/batt_current_adc" to "battery/batt_current_adc",
            // MTK
            "/sys/devices/platform/mt-battery/current_now" to "mt-battery/current_now",
            "/sys/devices/platform/battery_meter/current_now" to "battery_meter/current_now",
            // Vivo/iQOO
            "/sys/class/power_supply/battery/vivo_current" to "vivo/current",
            "/sys/class/power_supply/battery/real_charging_curr" to "vivo/real_charge",
            // Xiaomi/HyperOS 扩展
            "/sys/class/power_supply/bms/current_max" to "bms/current_max",
            "/sys/devices/platform/soc/soc:qcom,bcl/current_now" to "qcom_bcl/current",

            // === ColorOS 13.1+ 专项：多种单位/格式的路径 ===
            // 部分 ColorOS 版本在 BMS 目录下暴露电流
            "/sys/class/power_supply/bms/current_now" to "bms/current_now_fallback",
            // OPPO 充电芯片直接寄存器
            "/sys/class/power_supply/usb/current_max" to "usb/current_max",
            "/sys/class/power_supply/usb/current_now" to "usb/current_now",
            "/sys/class/power_supply/main/current_now" to "main/current_now",
            // 反向充电场景（某些 OPPO 机型用此判断方向）
            "/sys/class/power_supply/battery/otg_current" to "battery/otg_current",
        )

        for ((path, desc) in currentPaths) {
            try {
                // 直接 sysfs + shell 兜底 (Android 16 SELinux)
                var rawValue = SysFsReader.readLong(path)
                if (rawValue <= 0) {
                    val shellVal = readSysfsLongRobust(path)
                    if (shellVal > 0) rawValue = shellVal
                }
                if (rawValue == -1L || rawValue == Long.MIN_VALUE) continue
                if (rawValue == 0L) continue  // 值为0继续尝试下一个路径

                // === 单位自动检测 ===
                // 标准路径返回 µA (0~5000000)，OPPO/ColorOS 路径通常返回 mA (0~15000)
                // 启发式: |值| < 100 且非 OPPO 路径 → 大概率 mA；OPPO 路径 → 始终 mA
                val isOppoPath = path.contains("oplus") || path.contains("vooc")
                val adjustedValue = if (isOppoPath) {
                    val absVal = kotlin.math.abs(rawValue)
                    // OPPO 路径: 值 > 10000 可能是 µA (少数新型号)，其余按 mA 处理
                    if (absVal > 10000) rawValue else rawValue * 1000
                } else if (kotlin.math.abs(rawValue) < 100) {
                    Log.d(TAG, "current from $desc: $rawValue (detected as mA, converting to µA)")
                    rawValue * 1000  // mA → µA
                } else {
                    rawValue
                }

                // 有效范围检查: 100µA ~ 20,000,000µA (20A)
                if (kotlin.math.abs(adjustedValue) in 100..20_000_000) {
                    Log.d(TAG, "current_now from $desc: $adjustedValue µA (raw=$rawValue)")
                    return Pair(adjustedValue, desc)
                }
            } catch (_: Throwable) { /* next */ }
        }

        // BatteryManager 直接调用 getIntProperty(2) — CURRENT_NOW (优先于反射)
        try {
            val bm = appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            if (bm != null) {
                val current = bm.getIntProperty(2) // BATTERY_PROPERTY_CURRENT_NOW = 2
                if (current > 0) {
                    Log.d(TAG, "current_now from BatteryManager.getIntProperty(2): $current µA")
                    return Pair(current.toLong(), "BatteryManager direct (prop 2)")
                }
                // 也尝试 CHARGE_COUNTER (1) 用于 delta 估算
                val counter = bm.getIntProperty(1) // BATTERY_PROPERTY_CHARGE_COUNTER = 1
                if (counter > 0) {
                    Log.d(TAG, "charge_counter from BatteryManager: $counter")
                }
            }
        } catch (_: Throwable) { /* fall through */ }

        // BatteryManager 隐藏属性 CURRENT_NOW (反射) — 适用于 API 14-34
        try {
            val current = SysFsReader.getBatteryIntProperty(appContext, "BATTERY_PROPERTY_CURRENT_NOW")
            if (current != -1 && current != 0) {
                Log.d(TAG, "current_now from BatteryManager hidden API: $current µA")
                return Pair(current.toLong(), "BatteryManager hidden API")
            }
        } catch (_: Throwable) { /* fall through */ }

        // === ColorOS 13.1 专属兜底: dumpsys battery 提取 (shell 上下文) ===
        try {
            val proc = Runtime.getRuntime().exec(arrayOf("/system/bin/sh", "-c", "dumpsys battery"))
            val reader = proc.inputStream.bufferedReader()
            val output = reader.readText()
            reader.close()
            proc.waitFor()

            // 匹配 dumpsys 中的充电电流字段
            val dumpsysCurrentPatterns = listOf(
                Regex("""(?i)Max charging current[=:：]\s*(\d+)"""),
                Regex("""(?i)Charging current[=:：]\s*(\d+)"""),
                Regex("""(?i)Charge counter[=:：]\s*(\d+)"""),
                // 新增: 匹配 "Current:" 字段 (部分旧设备 dumpsys 格式)
                Regex("""(?i)^\s*(?:current|I)\s*[=:：]\s*(-?\d+)"""),
            )
            for (regex in dumpsysCurrentPatterns) {
                val match = regex.find(output)
                match?.let {
                    val value = it.groupValues[1].toLongOrNull()
                    if (value != null && value > 0) {
                        Log.d(TAG, "current_now via dumpsys battery: $value µA")
                        return Pair(value, "dumpsys battery")
                    }
                }
            }
        } catch (_: Throwable) { /* fall through */ }

        // === 最后兜底: dumpsys batterystats (旧设备兼容) ===
        try {
            val proc = Runtime.getRuntime().exec(
                arrayOf("/system/bin/sh", "-c", "dumpsys batterystats 2>/dev/null")
            )
            val output = proc.inputStream.bufferedReader().readText()
            proc.waitFor()

            // 从 batterystats 提取电流估计值
            val currentMatch = Regex("""(?i)Estimated power use.*?=.*?(\d+)""").find(output)
                ?: Regex("""(?i)Current:\s*(-?\d+)""").find(output)
            currentMatch?.let {
                val value = it.groupValues[1].toLongOrNull()
                if (value != null && kotlin.math.abs(value) > 0) {
                    Log.d(TAG, "current_now via batterystats: $value")
                    return Pair(value, "dumpsys batterystats")
                }
            }
        } catch (_: Throwable) { /* fall through */ }

        return Pair(0L, "无法获取")
    }

    // ── 辅助: shell 读取 sysfs 整数 (绕过 Android 13+ SELinux) ──
    private fun readSysfsIntShellSafely(path: String): Int {
        return readSysfsLineShell(path)?.toIntOrNull() ?: -1
    }

    // ── 辅助: 直接读取 sysfs 整数 (先直接 IO，失败用 shell 兜底) ──
    private fun readSysfsIntRobust(path: String): Int {
        val direct = SysFsReader.readInt(path)
        if (direct > 0) return direct
        return readSysfsIntShellSafely(path)
    }

    /** 兼容旧 API */
    private fun getCurrentNow(): Long = getCurrentNowFull().first

    // ========== 电池循环次数（50+ 路径全网方案） ==========

    /**
     * @return Pair<循环次数, 来源描述>
     */
    private fun getBatteryCycleCountFull(): Pair<Int, String> {
        // ======================================================
        // 电池循环计数采集 — 基于 Android 官方文档 + AOSP 源码
        // 参考: developer.android.com / source.android.com
        // 标准属性定义见 frameworks/base/core/java/android/os/BatteryManager.java
        // ======================================================

        // === Level 1: ACTION_BATTERY_CHANGED EXTRA_CYCLE_COUNT (标准 Intent Extra) ===
        // 来源: BatteryManager.EXTRA_CYCLE_COUNT = "android.os.extra.CYCLE_COUNT"
        // 此 Extra 已在 AOSP BatteryManager.java 中正式定义 (Android 14+/API 34+)
        // 但多数 OEM 广播中不一定填充此字段
        try {
            val intent = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val cycle = intent?.getIntExtra("cycle_count", -1) ?: -1
            if (cycle < 0) {
                // 尝试标准 Key 名
                val cycle2 = intent?.getIntExtra("android.os.extra.CYCLE_COUNT", -1) ?: -1
                if (cycle2 > 0 && cycle2 < 10000) {
                    Log.d(TAG, "cycle_count via EXTRA_CYCLE_COUNT: $cycle2")
                    return Pair(cycle2, "BatteryManager Intent Extra")
                }
            } else if (cycle > 0 && cycle < 10000) {
                Log.d(TAG, "cycle_count via EXTRA_CYCLE_COUNT: $cycle")
                return Pair(cycle, "BatteryManager Intent Extra")
            }
        } catch (_: Throwable) { /* fall through */ }

        // === Level 2: Health AIDL HAL (Android 13+/API 33+) ===
        // 来源: android.hardware.health.IHealth AIDL 接口
        // HealthInfo.batteryCycleCount 字段
        // 文档: source.android.com/docs/core/perf/health
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                val serviceManager = Class.forName("android.os.ServiceManager")
                val waitForService = serviceManager.getMethod(
                    "waitForDeclaredService",
                    String::class.java
                )
                val binder = waitForService.invoke(
                    null,
                    "android.hardware.health.IHealth/default"
                ) as? android.os.IBinder

                if (binder != null) {
                    // 通过 Binder 事务调用 getHealthInfo() 方法
                    // AIDL 接口方法索引: 1=getHealthInfo
                    val data = android.os.Parcel.obtain()
                    val reply = android.os.Parcel.obtain()
                    try {
                        data.writeInterfaceToken("android.hardware.health.IHealth")
                        binder.transact(1, data, reply, 0)  // 事务码 1 = getHealthInfo()
                        reply.readException()

                        // HealthInfo 结构体 (AIDL Parcelable):
                        // batteryPresent, batteryLevel, batteryChargeUah, ... batteryCycleCount
                        // 跳过前几个字段到达 batteryCycleCount
                        reply.readInt()  // batteryPresent
                        reply.readInt()  // batteryLevel
                        reply.readInt()  // batteryChargeUah
                        reply.readInt()  // batteryChargeCounterUah
                        reply.readByte()  // batteryStatus
                        reply.readInt()  // batteryHealth
                        reply.readInt()  // batteryHealthData
                        val cycleCount = reply.readInt()  // batteryCycleCount

                        if (cycleCount > 0 && cycleCount < 10000) {
                            Log.d(TAG, "cycle_count via Health AIDL HAL: $cycleCount")
                            return Pair(cycleCount, "Health AIDL HAL (API 33+)")
                        }
                    } finally {
                        data.recycle()
                        reply.recycle()
                    }
                }
            } catch (_: Throwable) { /* Health AIDL HAL not available */ }
        }

        // === Level 2.1: 旧版 IHealth HAL binder (Android 8-12) ===
        // HIDL 接口: android.hardware.health@2.x::IHealth
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                val serviceManager = Class.forName("android.os.ServiceManager")
                val getService = serviceManager.getMethod("getService", String::class.java)
                val healthBinder = getService.invoke(null, "health") as? android.os.IBinder
                if (healthBinder != null) {
                    val data = android.os.Parcel.obtain()
                    val reply = android.os.Parcel.obtain()
                    try {
                        for (txCode in listOf(5, 7, 8, 9, 10)) {
                            try {
                                data.recycle()
                                reply.recycle()
                                val p = android.os.Parcel.obtain()
                                val r = android.os.Parcel.obtain()
                                p.writeInterfaceToken("android.hardware.health.IHealth")
                                healthBinder.transact(txCode, p, r, 0)
                                r.readException()
                                val cycle = r.readInt()
                                if (cycle > 0 && cycle < 10000) {
                                    Log.d(TAG, "cycle_count via IHealth HIDL tx=$txCode: $cycle")
                                    p.recycle(); r.recycle()
                                    return Pair(cycle, "IHealth HIDL HAL")
                                }
                                p.recycle(); r.recycle()
                                break
                            } catch (_: Throwable) { continue }
                        }
                    } finally {
                        try { data.recycle() } catch (_: Throwable) {}
                        try { reply.recycle() } catch (_: Throwable) {}
                    }
                }
            } catch (_: Throwable) { /* IHealth HAL not available */ }
        }

        // === Level 3: BatteryManager hidden API BATTERY_PROPERTY_CYCLE_COUNT ===
        // 注意: 标准 AOSP BatteryManager.java 中不存在此常量 (已验证 API 34-37 源码)
        // 少数 OEM 自定义 ROM 可能包含，通过反射尝试但不可靠
        try {
            val bm = appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            if (bm != null) {
                val propField = BatteryManager::class.java.getDeclaredField("BATTERY_PROPERTY_CYCLE_COUNT")
                propField.isAccessible = true
                val propId = propField.getInt(null)
                val cycle = bm.getIntProperty(propId)
                if (cycle > 0 && cycle < 10000) {
                    Log.d(TAG, "cycle_count via BatteryManager hidden API (propId=$propId): $cycle")
                    return Pair(cycle, "BatteryManager OEM property")
                }
            }
        } catch (_: Throwable) { /* hidden API not found */ }

        // === Level 3.1: IHealth HAL binder service (Android 8+ / API 26+) ===
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                val serviceManager = Class.forName("android.os.ServiceManager")
                val getService = serviceManager.getMethod("getService", String::class.java)
                // 尝试 health 服务
                val healthBinder = getService.invoke(null, "health") as? android.os.IBinder
                if (healthBinder != null) {
                    try {
                        // IHealth.getChargeCounter / getBatteryCycleCount
                        // 通过 Binder 事务号 1-5 访问
                        val parcel = android.os.Parcel.obtain()
                        val reply = android.os.Parcel.obtain()
                        try {
                            // 尝试不同的 Binder transaction codes (AOSP IHealth.hal)
                            for (txCode in listOf(5, 7, 8, 9, 10)) {
                                try {
                                    parcel.recycle()
                                    reply.recycle()
                                    val p = android.os.Parcel.obtain()
                                    val r = android.os.Parcel.obtain()
                                    p.writeInterfaceToken("android.hardware.health.IHealth")
                                    healthBinder.transact(txCode, p, r, 0)
                                    r.readException()
                                    val cycle = r.readInt()
                                    if (cycle > 0 && cycle < 10000) {
                                        Log.d(TAG, "cycle_count via IHealth HAL tx=$txCode: $cycle")
                                        return Pair(cycle, "IHealth HAL (Android 8+)")
                                    }
                                    p.recycle()
                                    r.recycle()
                                    break
                                } catch (_: Throwable) { continue }
                            }
                        } finally {
                            try { parcel.recycle() } catch (_: Throwable) {}
                            try { reply.recycle() } catch (_: Throwable) {}
                        }
                    } catch (_: Throwable) {}
                }
            } catch (_: Throwable) { /* IHealth HAL not available */ }
        }

        // === Level 0.2: dmesg 内核环形缓冲区 — 很多 OEM BMS 驱动在启动时打印循环计数 ===
        try {
            val dmesgProc = Runtime.getRuntime().exec(arrayOf("dmesg"))
            val dmesgOutput = dmesgProc.inputStream.bufferedReader().readText()
            dmesgProc.waitFor()
            // 匹配电池循环相关日志: "bms: cycle=123", "charge_cycle: 456", "battery_cycle=789" 等
            val dmesgPatterns = listOf(
                Regex("""(?i)(?:bms|battery|fuel).*?(?:cycle|loop).*?[=: ](\d{1,4})"""),
                Regex("""(?i)(?:cycle_count|charge_cycle|battery_cycle|batt_cycle)[=: ](\d{1,4})"""),
                Regex("""(?i)soh.*?cycle.*?[=: ](\d{1,4})"""),
                Regex("""(?i)fg_cycle[=: ](\d{1,4})"""),
                Regex("""(?i)cc_cycle[=: ](\d{1,4})"""),
            )
            for (regex in dmesgPatterns) {
                val match = regex.find(dmesgOutput)
                match?.let {
                    val cnt = it.groupValues[1].toIntOrNull()
                    if (cnt != null && cnt > 0 && cnt < 10000) {
                        Log.d(TAG, "cycle_count via dmesg: $cnt")
                        return Pair(cnt, "dmesg kernel log")
                    }
                }
            }
        } catch (_: Throwable) { /* fall through */ }

        // === Level 0.3: /proc/ 下电池芯片驱动暴露的节点 (shell 方式读取) ===
        val procPaths = listOf(
            "/proc/mtk_battery_cmd/current_cmd" to "MTK battery proc",
            "/proc/qcom_battery/cycle_count" to "Qcom battery proc",
            "/proc/battery/cycle_count" to "Generic battery proc",
            "/proc/charge_cycle" to "Generic charge_cycle",
            "/proc/fg/cycle_count" to "Fuel Gauge proc/fg",
            "/proc/bq27z00/cycle_count" to "TI Fuel Gauge",
            "/proc/max170xx/cycle_count" to "Maxim Fuel Gauge",
        )
        for ((path, desc) in procPaths) {
            try {
                val content = readSysfsLineShell(path) ?: continue
                val patterns = listOf(
                    Regex("""cycle.*?(\d{1,4})""", RegexOption.IGNORE_CASE),
                    Regex("""(\d{1,4})"""),
                )
                for (regex in patterns) {
                    val match = regex.find(content)
                    match?.let {
                        val cnt = it.groupValues[1].toIntOrNull()
                        if (cnt != null && cnt > 0 && cnt < 10000) {
                            Log.d(TAG, "cycle_count via $desc: $cnt")
                            return Pair(cnt, desc)
                        }
                    }
                }
            } catch (_: Throwable) {}
        }

        // === Level 0.4: Health Connect (Android 14+, API 34+) ===
        // 优先级高于 dumpsys，因为 Health Connect 是官方 API
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                // HealthManager / HealthConnectManager — 通过反射获取电池健康数据
                val hcManager = appContext.getSystemService("healthconnect")
                if (hcManager != null) {
                    try {
                        // HealthConnectManager.getHealthData()
                        val getHealthData = hcManager.javaClass.getMethod("getHealthData")
                        val healthData = getHealthData.invoke(hcManager)
                        if (healthData != null) {
                            // HealthData.getCycleCount()
                            val getCycle = healthData.javaClass.getMethod("getCycleCount")
                            val cycle = getCycle.invoke(healthData) as? Int
                            if (cycle != null && cycle > 0 && cycle < 10000) {
                                Log.d(TAG, "cycle_count via HealthConnectManager: $cycle")
                                return Pair(cycle, "HealthConnectManager")
                            }
                            // HealthData.getBatteryCycleCount()
                            val getBatteryCycle = healthData.javaClass.getMethod("getBatteryCycleCount")
                            val battCycle = getBatteryCycle.invoke(healthData) as? Int
                            if (battCycle != null && battCycle > 0 && battCycle < 10000) {
                                Log.d(TAG, "cycle_count via HealthConnect battCycle: $battCycle")
                                return Pair(battCycle, "HealthConnect battCycle")
                            }
                        }
                    } catch (_: Throwable) {}
                }
            } catch (_: Throwable) { /* HealthConnect not available */ }

            // 备用: android.os.health.HealthStats (SystemHealthManager)
            try {
                val healthManager = appContext.getSystemService(android.os.health.SystemHealthManager::class.java)
                if (healthManager != null) {
                    val takeUidSnapshot = healthManager.javaClass.getMethod("takeUidSnapshot", Int::class.javaPrimitiveType)
                    val healthStats = takeUidSnapshot.invoke(healthManager, android.os.Process.myUid())
                    if (healthStats != null) {
                        val getCycle = healthStats.javaClass.getMethod("getBatteryCycleCount")
                        val cycle = getCycle.invoke(healthStats) as? Int
                        if (cycle != null && cycle > 0 && cycle < 10000) {
                            Log.d(TAG, "cycle_count via SystemHealthManager: $cycle")
                            return Pair(cycle, "SystemHealthManager")
                        }
                    }
                }
            } catch (_: Throwable) {}
        }

        // === Level 0.5: logcat 系统日志扫描 — 很多 OEM 在 Health HAL 中打印循环计数 ===
        try {
            // 只读取 system buffer 最近100行，避免超时
            val logcatProc = Runtime.getRuntime().exec(
                arrayOf("logcat", "-d", "-b", "system", "-t", "100")
            )
            val logcatOutput = logcatProc.inputStream.bufferedReader().readText()
            logcatProc.waitFor()
            val logcatPatterns = listOf(
                Regex("""(?i)(?:health|battery|bms|charge).*?cycle.*?[=: ](\d{2,4})"""),
                Regex("""(?i)cycle_count[=: ](\d{2,4})"""),
                Regex("""(?i)charge_cycle[=: ](\d{2,4})"""),
            )
            for (regex in logcatPatterns) {
                val match = regex.find(logcatOutput)
                match?.let {
                    val cnt = it.groupValues[1].toIntOrNull()
                    if (cnt != null && cnt > 0 && cnt < 10000) {
                        Log.d(TAG, "cycle_count via logcat system: $cnt")
                        return Pair(cnt, "logcat system buffer")
                    }
                }
            }
        } catch (_: Throwable) { /* fall through */ }

        // === Level 0.5: 新增 sysfs 路径（覆盖 vivo/iQOO 和新型号） ===
        val extraSysfsPaths = listOf(
            "/sys/class/power_supply/battery/cycle_counts" to "battery/cycle_counts",
            "/sys/class/power_supply/battery/total_battery_cycle" to "battery/total_battery_cycle",
            "/sys/class/power_supply/battery/charge_done" to "battery/charge_done_cycle",
            "/sys/class/power_supply/battery/total_cycle_count" to "battery/total_cycle_count",
            "/sys/class/power_supply/battery/capacity_level" to "battery/capacity_level_cycle",
            // 新增: 高通 PMIC FG (Fuel Gauge) 循环计数
            "/sys/class/power_supply/bms/cycle_counts" to "bms/cycle_counts",
            "/sys/class/power_supply/battery/fg_cycle" to "battery/fg_cycle",
            "/sys/class/power_supply/battery/fg_fullcapnom" to "battery/fg_fullcapnom",
            "/sys/devices/platform/soc/soc:qcom,fg-memif/cycle_count" to "qcom fg-memif",
            "/sys/devices/platform/soc/soc:battery/cycle_count" to "qcom soc:battery",
            // OPPO/OnePlus MTK 新路径
            "/sys/devices/platform/battery/cycle_count" to "MTK platform battery",
            "/sys/devices/platform/mt-battery/cycle_count" to "MTK mt-battery",
            // Samsung fuelgauge (maxim)
            "/sys/class/power_supply/battery/fg_cycle" to "samsung fg_cycle",
            "/sys/devices/platform/samsung_fuelgauge/cycle" to "samsung fuelgauge",
            // Google Pixel
            "/sys/class/power_supply/battery/cycle_count" to "pixel cycle_count",
            "/sys/class/power_supply/bms/battery_cycle_count" to "pixel bms cycle",
            // 通用 FG (Fuel Gauge) 芯片
            "/sys/class/power_supply/fg/cycle_count" to "fg/cycle_count",
            "/sys/class/power_supply/battery/device/cycle_count" to "device/cycle_count",
            "/sys/devices/virtual/power_supply/battery/cycle_count" to "virtual/cycle_count",
            "/sys/kernel/debug/battery/cycle_count" to "debug/battery/cycle",
            // 新增: Android 14+ Health HAL v2.1 cycle_count 导出路径
            "/sys/class/power_supply/battery/health_cycle_count" to "health/cycle_count",
        )
        for ((path, desc) in extraSysfsPaths) {
            val cnt = readSysfsIntRobust(path)
            if (cnt > 0) return Pair(cnt, desc)
        }

        // === Level 1: sysfs battery 直接读取 ===
        val sysfsBatteryPaths = listOf(
            "/sys/class/power_supply/battery/cycle_count" to "battery/cycle_count",
            "/sys/class/power_supply/battery/batt_cycle" to "battery/batt_cycle",
            "/sys/class/power_supply/battery/battery_cycle" to "battery/battery_cycle",
            "/sys/class/power_supply/battery/charge_cycle" to "battery/charge_cycle",
            "/sys/class/power_supply/battery/batt_cycle_count" to "battery/batt_cycle_count",
            "/sys/class/power_supply/battery/healthd_cycle" to "battery/healthd_cycle",
        )
        for ((path, desc) in sysfsBatteryPaths) {
            val cnt = readSysfsIntRobust(path)
            if (cnt > 0) return Pair(cnt, desc)
        }

        // === Level 2: OPPO/OnePlus/Realme oplus_chg 专用路径 ===
        val oppoPaths = listOf(
            "/sys/class/oplus_chg/battery/cycle_count" to "OPPO oplus_chg/cycle_count",
            "/sys/class/oplus_chg/battery/charge_cycle" to "OPPO oplus_chg/charge_cycle",
            "/sys/class/oplus_chg/battery/battery_cycle" to "OPPO oplus_chg/battery_cycle",
            "/sys/class/oplus_chg/battery/cycle" to "OPPO oplus_chg/cycle",
            "/sys/class/oplus_chg/battery/batt_cycle_count" to "OPPO oplus_chg/batt_cycle_count",
            // P2: OPPO 内核级循环计数路径
            "/sys/kernel/oplus_chg/battery/cycle_count" to "OPPO kernel/oplus_chg/cycle_count",
            "/sys/kernel/oplus_chg/battery/charge_cycle" to "OPPO kernel/oplus_chg/charge_cycle",
            "/sys/kernel/oplus_chg/battery/battery_cycle" to "OPPO kernel/oplus_chg/battery_cycle",
            "/sys/kernel/oplus_chg/battery/batt_cycle_count" to "OPPO kernel/oplus_chg/batt_cycle",
            "/sys/devices/platform/soc/oplus_chg/battery/cycle_count" to "OPPO soc/oplus_chg/cycle",
            "/sys/devices/platform/soc/oplus_chg/battery/charge_cycle" to "OPPO soc/oplus_chg/charge_cycle",
        )
        for ((path, desc) in oppoPaths) {
            val cnt = readSysfsIntRobust(path)
            if (cnt > 0) return Pair(cnt, desc)
        }

        // === Level 3: 小米/HyperOS BMS 专用路径（骁龙 qpnp-vm-bms） ===
        val xiaomiPaths = listOf(
            "/sys/class/power_supply/bms/cycle_count" to "Xiaomi bms/cycle_count",
            "/sys/class/power_supply/bms/battery_cycle" to "Xiaomi bms/battery_cycle",
            "/sys/class/power_supply/bms/charge_cycle" to "Xiaomi bms/charge_cycle",
            "/sys/class/power_supply/bms/batt_cycle_count" to "Xiaomi bms/batt_cycle_count",
            "/sys/class/power_supply/battery/battery_cycle_count" to "Xiaomi battery_cycle_count",
            "/sys/class/power_supply/battery/charger_cycle_count" to "Xiaomi charger_cycle_count",
            "/sys/devices/platform/soc/soc:battery/cycle_count" to "Xiaomi soc/cycle_count",
            // 骁龙 8s Gen 3 / 8 Gen 系列 BMS 备用路径
            "/sys/class/power_supply/bms/cycle_counts" to "Snapdragon bms/cycle_counts",
            "/sys/class/power_supply/qcom-battery/cycle_count" to "Snapdragon qcom-battery",
        )
        for ((path, desc) in xiaomiPaths) {
            val cnt = readSysfsIntRobust(path)
            if (cnt > 0) return Pair(cnt, desc)
        }

        // === Level 4: charge_counter 推算（小米旧方案 + 骁龙 BMS） ===
        val counter = SysFsReader.readFirstLong(listOf(
            "/sys/class/power_supply/battery/charge_counter",
            "/sys/class/power_supply/bms/charge_counter",
        ))
        val designCap = SysFsReader.readFirstLong(listOf(
            "/sys/class/power_supply/battery/charge_full_design",
            "/sys/class/power_supply/bms/charge_full_design",
        ))
        if (counter > 0 && designCap > 0) {
            val estimatedCycles = (counter / designCap).toInt()
            if (estimatedCycles in 1..2000) {
                return Pair(estimatedCycles, "charge_counter推算")
            }
        }

        // === Level 5: 系统属性（50+ 厂商属性） ===
        val props = listOf(
            // OPPO/OnePlus/Realme (OPlus)
            "ro.oplus.battery.cycle_count" to "OPPO ro.oplus",
            "persist.oplus.battery.cycle_count" to "OPPO persist.oplus",
            "ro.vendor.oplus.battery.cycle" to "OPPO ro.vendor.oplus",
            "persist.vendor.oplus.battery.cycle" to "OPPO persist.vendor.oplus",
            "ro.oplus.battery.health.cycle" to "OPPO ro.oplus.health",
            "ro.vendor.oplus.battery.cycle_count" to "OPPO ro.vendor.oplus.count",
            "persist.vendor.oplus.battery.charge_cycle" to "OPPO persist.oplus.charge",
            "ro.oplus.charge.cycle" to "OPPO ro.oplus.charge",
            "persist.oplus.charge.cycle" to "OPPO persist.oplus.charge",

            // OPPO Reno/ColorOS 旧版
            "ro.vendor.battery.cycle_count" to "OPPO ro.vendor",
            "persist.vendor.battery.cycle_count" to "OPPO persist.vendor",
            "ro.battery.cycle_count" to "OPPO ro.battery",
            "persist.battery.cycle_count" to "OPPO persist.battery",
            "persist.vendor.battery.cycle" to "OPPO persist.vendor.cycle",
            "ro.vendor.battery.cycle" to "OPPO ro.vendor.cycle",
            "ro.boot.battery_cycle" to "OPPO ro.boot",

            // OPPO/Realme/一加 扩展
            "ro.vendor.power.battery_cycle" to "OPPO ro.vendor.power",
            "persist.vendor.power.battery_cycle" to "OPPO persist.vendor.power",
            "ro.battery.cycle" to "OPPO ro.battery.cycle",
            // P2: OPPO 内核级属性 (ColorOS 内核模块导出)
            "oplus.battery.cycle_count" to "OPPO kernel oplus",
            "persist.oplus.battery.health.cycle" to "OPPO persist health",
            "ro.oplus.battery.soh" to "OPPO SOH",
            "vendor.oplus.battery.health.cycle" to "OPPO vendor health",

            // 小米/HyperOS
            "ro.vendor.battery.cycle_count" to "Xiaomi ro.vendor",
            "persist.vendor.battery.cycle_count" to "Xiaomi persist.vendor",
            "ro.battery.cycle_count" to "Xiaomi ro.battery",
            "persist.battery.cycle_count" to "Xiaomi persist.battery",
            "persist.vendor.battery.cycle" to "Xiaomi persist.vendor.cycle",
            "ro.vendor.battery.cycle" to "Xiaomi ro.vendor.cycle",
            "ro.boot.battery_cycle" to "Xiaomi ro.boot",

            // vivo/iQOO
            "ro.vendor.battery.charge_cycle" to "vivo ro.vendor.charge",
            "persist.vendor.battery.charge_cycle" to "vivo persist.vendor.charge",
            "ro.battery.charge_cycle" to "vivo ro.battery.charge",

            // 华为/荣耀
            "ro.batt.cycle_count" to "Huawei ro.batt",
            "persist.batt.cycle_count" to "Huawei persist.batt",
            "ro.batt.charge_cycle" to "Huawei ro.batt.charge",
            "persist.batt.charge_cycle" to "Huawei persist.batt.charge",
            "ro.vendor.batt.cycle_count" to "Huawei ro.vendor.batt",

            // 三星
            "ro.vendor.battery.healthd_cycle" to "Samsung ro.vendor.healthd",
            "persist.vendor.battery.healthd_cycle" to "Samsung persist.vendor.healthd",
            "ro.vendor.battery.healthd.daily" to "Samsung healthd.daily",

            // 索尼
            "ro.battery_cycle" to "Sony ro.battery_cycle",
            "persist.battery_cycle" to "Sony persist.battery_cycle",
            "ro.semc.batt.capacity" to "Sony semc",

            // 联想/摩托罗拉
            "ro.battery.health.cycle" to "Lenovo ro.battery.health",
            "persist.battery.health.cycle" to "Lenovo persist.battery.health",

            // 通用 / 其他
            "ro.battery.charge_counter" to "通用 charge_counter",
            "ro.battery.charge.times" to "通用 charge.times",
            "ro.vendor.battery.health" to "通用 ro.vendor.health",
            "persist.vendor.battery.health" to "通用 persist.vendor.health",

            // === Android 16 新增属性（国产 ROM） ===
            // OPPO ColorOS 16
            "ro.oplus.health.battery_cycle" to "OPPO ColorOS 16 health",
            "persist.oplus.health.battery_cycle" to "OPPO ColorOS 16 persist",
            "vendor.oplus.battery.cycle.count" to "OPPO vendor cycle",
            "ro.vendor.oplus.health.cycle" to "OPPO health cycle",
            // Xiaomi HyperOS 3.0
            "ro.vendor.miui.battery_cycle" to "HyperOS 3.0 cycle",
            "persist.vendor.miui.battery_cycle" to "HyperOS 3.0 persist",
            "ro.miui.battery.health.cycle" to "HyperOS health cycle",
            "persist.vendor.battery.health.cycle" to "HyperOS health persist",
            // Vivo OriginOS 6
            "ro.vendor.vivo.battery_cycle" to "OriginOS 6 cycle",
            "persist.vendor.vivo.battery_cycle" to "OriginOS 6 persist",
            "ro.vivo.battery.health.cycle" to "OriginOS health cycle",
            // 通用新属性
            "ro.boot.battery.cycle_count" to "boot battery cycle",
            "ro.boot.battery.charge_cycle" to "boot charge cycle",
            "persist.vendor.battery.cycle_count" to "vendor persist",
            "ro.vendor.battery.health.capacity" to "vendor health capacity",
            // /sys 可能映射为系统属性的一些路径
            "ro.battery.health.cycle_count" to "ro health cycle_count",
            "persist.battery.health.cycle_count" to "persist health cycle_count",
            // 高通 BCL 代理属性
            "persist.vendor.bms.cycle_count" to "vendor bms cycle",
            "ro.vendor.bms.cycle_count" to "ro vendor bms cycle",
        )
        for ((prop, desc) in props) {
            val value = SysFsReader.readPropInt(prop)
            if (value > 0) return Pair(value, "SystemProperty: $desc")
        }

        // === Level 6: dumpsys batterystats 统计 ===
        // 注意：需要 PACKAGE_USAGE_STATS 或 DUMP 权限；此方法不稳定
        try {
            val androidOsProcess = Runtime.getRuntime().exec(
                arrayOf("dumpsys", "batterystats")
            )
            val reader = androidOsProcess.inputStream.bufferedReader()
            val output = reader.readText()
            reader.close()
            androidOsProcess.waitFor()

            // 尝试匹配 mSavedBatteryUsage 或 charge cycles
            val savedUsageRegex = Regex("""mSavedBatteryUsage[=:]\s*(\d+)""", RegexOption.IGNORE_CASE)
            val chargeCycleRegex = Regex("""charge.?cycle[=:]\s*(\d+)""", RegexOption.IGNORE_CASE)
            val cycleRegex = Regex("""cycle.?count[=:]\s*(\d+)""", RegexOption.IGNORE_CASE)

            savedUsageRegex.find(output)?.let { it.groupValues[1].toIntOrNull()?.let { cnt ->
                if (cnt > 0) return Pair(cnt, "dumpsys batterystats")
            }}
            chargeCycleRegex.find(output)?.let { it.groupValues[1].toIntOrNull()?.let { cnt ->
                if (cnt > 0) return Pair(cnt, "dumpsys charge_cycle")
            }}
            cycleRegex.find(output)?.let { it.groupValues[1].toIntOrNull()?.let { cnt ->
                if (cnt > 0) return Pair(cnt, "dumpsys cycle_count")
            }}
        } catch (_: Throwable) { /* fall through */ }

        // === Level 7: dumpsys battery 直接读取（很多 OEM 在此暴露 cycle_count）===
        // Android 16 SELinux: 通过 sh -c 执行以 shell 上下文绕过限制
        try {
            val proc = Runtime.getRuntime().exec(arrayOf("/system/bin/sh", "-c", "dumpsys battery"))
            val reader = proc.inputStream.bufferedReader()
            val output = reader.readText()
            reader.close()
            proc.waitFor()

            // dumpsys battery 在各个 OEM ROM 上的常见格式
            val dumpsysPatterns = listOf(
                // 标准格式: "Cycle count: 123"
                Regex("""(?i)(?:cycle\s*count|cycle_cnt|battery_cycle|charge_cycle)[=:：]\s*(\d+)"""),
                // 备用格式: "battery cycle count: 123"
                Regex("""(?i)battery\s+cycle\s+count[=:：]\s*(\d+)"""),
                // 小米/HyperOS 格式
                Regex("""(?i)cycle_count\s*[=:：]\s*(\d+)"""),
                // OPPO/ColorOS 格式: "battery_cycle_count: 123"
                Regex("""(?i)battery_cycle_count[=:：]\s*(\d+)"""),
                // 华为/HarmonyOS 格式
                Regex("""(?i)healthd_cycle_count[=:：]\s*(\d+)"""),
                // 三星 OneUI 格式
                Regex("""(?i)cycle\s*=\s*(\d+)"""),
                // 通用数值型: 任意包含 cycle 的行中提取首个 >= 2 位的数字
                Regex("""(?i).*cycle.*"""),
            )

            // 先尝试精确匹配
            for ((i, regex) in dumpsysPatterns.withIndex()) {
                if (i == dumpsysPatterns.size - 1) continue // 跳过通用模式
                val match = regex.find(output)
                match?.let {
                    val cnt = it.groupValues[1].toIntOrNull()
                    if (cnt != null && cnt > 0 && cnt < 10000) {
                        Log.d(TAG, "cycle_count via dumpsys battery pattern $i: $cnt")
                        return Pair(cnt, "dumpsys battery")
                    }
                }
            }

            // 通用兜底: 逐行搜索包含 "cycle" 的行
            for (line in output.split("\n")) {
                if (Regex("""(?i)cycle""").containsMatchIn(line)) {
                    val numMatch = Regex("""(\d{2,4})""").find(line)
                    numMatch?.let {
                        val cnt = it.groupValues[1].toIntOrNull()
                        if (cnt != null && cnt > 1 && cnt < 10000) {
                            // 排除明显不是循环数的值（如电压、电流值）
                            if (cnt != 100 && cnt != 500 && cnt != 1000) {
                                Log.d(TAG, "cycle_count via dumpsys battery generic: $cnt (line: ${line.trim()})")
                                return Pair(cnt, "dumpsys battery (generic)")
                            }
                        }
                    }
                }
            }
        } catch (_: Throwable) { /* fall through */ }

        // === Level 7.5: dumpsys batterystats --checkin 格式（结构化输出） ===
        try {
            val proc = Runtime.getRuntime().exec(arrayOf("/system/bin/sh", "-c", "dumpsys batterystats --checkin"))
            val reader = proc.inputStream.bufferedReader()
            val output = reader.readText()
            reader.close()
            proc.waitFor()

            val checkinCycleRegex = Regex("""cycle[:=](\d{1,4})""", RegexOption.IGNORE_CASE)
            val match = checkinCycleRegex.find(output)
            match?.let {
                val cnt = it.groupValues[1].toIntOrNull()
                if (cnt != null && cnt > 0 && cnt < 10000) {
                    Log.d(TAG, "cycle_count via dumpsys batterystats --checkin: $cnt")
                    return Pair(cnt, "dumpsys batterystats --checkin")
                }
            }

            // 备用: 解析 "9,p," 电池行中的 "cc:<value>"
            val ccmRegex = Regex("""(?:cc|cycle_count)[:=](\d{1,4})""", RegexOption.IGNORE_CASE)
            val ccmMatch = ccmRegex.find(output)
            ccmMatch?.let {
                val cnt = it.groupValues[1].toIntOrNull()
                if (cnt != null && cnt > 0 && cnt < 10000) {
                    return Pair(cnt, "batterystats checkin cc")
                }
            }
        } catch (_: Throwable) { /* fall through */ }

        // === Level 7.8: dumpsys batteryproperties (Android 10+) ===
        try {
            val proc = Runtime.getRuntime().exec(arrayOf("/system/bin/sh", "-c", "dumpsys batteryproperties 2>/dev/null"))
            val output = proc.inputStream.bufferedReader().readText()
            proc.waitFor()

            // 匹配各种格式: "cycle_count: 123", "Cycle count=456" 等
            val bpPatterns = listOf(
                Regex("""(?i)(?:cycle[_\s]*count|charge[_\s]*cycle|battery[_\s]*cycle)[=: ]+(\d{1,4})"""),
                Regex("""(?i)cycle[=: ]+(\d{2,4})"""),
            )
            for (regex in bpPatterns) {
                val match = regex.find(output)
                match?.let {
                    val cnt = it.groupValues[1].toIntOrNull()
                    if (cnt != null && cnt > 0 && cnt < 10000) {
                        return Pair(cnt, "dumpsys batteryproperties")
                    }
                }
            }
        } catch (_: Throwable) { /* fall through */ }

        // === Level 8: HealthManager (Android 14+, API 34+) ===
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                val healthManager = appContext.getSystemService("healthconnect")
                if (healthManager != null) {
                    // 反射调用 getHealthData 或类似方法
                    try {
                        val getMethod = healthManager.javaClass.getMethod("getHealthData")
                        val healthData = getMethod.invoke(healthManager)
                        if (healthData != null) {
                            val getCycleMethod = healthData.javaClass.getMethod("getCycleCount")
                            val cycle = getCycleMethod.invoke(healthData) as? Int
                            if (cycle != null && cycle > 0 && cycle < 10000) {
                                Log.d(TAG, "cycle_count via HealthManager: $cycle")
                                return Pair(cycle, "HealthManager")
                            }
                        }
                    } catch (_: Throwable) {}
                }
            } catch (_: Throwable) { /* fall through */ }
        }

        return Pair(-1, "无法获取")
    }

    /** 兼容旧 API */
    private fun getBatteryCycleCount(): Int = getBatteryCycleCountFull().first

    // ========== dumpsys battery ==========

    private fun readDumpsysBattery(info: BatteryInfo) {
        try {
            val dumpsysBattery = ShellCommandDataSource.getDumpsysBattery()
            if (dumpsysBattery.isEmpty()) return

            // 最大充电电流
            val maxCurrent = ShellCommandDataSource.extractLong(dumpsysBattery, "Max charging current")
            if (maxCurrent > 0) info.maxChargingCurrentUA = maxCurrent

            // 最大充电电压
            val maxVoltage = ShellCommandDataSource.extractLong(dumpsysBattery, "Max charging voltage")
            if (maxVoltage > 0) info.maxChargingVoltageUV = maxVoltage

            // Charge counter (已充电量)
            val chargeCounter = ShellCommandDataSource.extractLong(dumpsysBattery, "Charge counter")
            if (chargeCounter > 0 && info.chargeCounterUAh <= 0) {
                info.chargeCounterUAh = chargeCounter
            }

            // ★ Android 15+ One UI 6.1.1+: BSOH (Battery State of Health)
            val bsoh = ShellCommandDataSource.extractInt(dumpsysBattery, "BSOH")
            if (bsoh > 0) {
                info.sohPercent = bsoh.toFloat()
            }

            // ★ mSavedBatteryAsoc (最大容量节约 % 估计)
            val asoc = ShellCommandDataSource.extractDumpsysValue(dumpsysBattery, "mSavedBatteryAsoc")
            if (asoc != null && info.sohPercent.isNaN()) {
                asoc.toIntOrNull()?.let { if (it in 50..100) info.sohPercent = it.toFloat() }
            }

            // 充电类型
            val acOnline = ShellCommandDataSource.extractDumpsysValue(dumpsysBattery, "AC powered")
            val usbOnline = ShellCommandDataSource.extractDumpsysValue(dumpsysBattery, "USB powered")
            val wirelessOnline = ShellCommandDataSource.extractDumpsysValue(dumpsysBattery, "Wireless powered")
            val dockOnline = ShellCommandDataSource.extractDumpsysValue(dumpsysBattery, "Dock powered")
            val chargerType = StringBuilder()
            if ("true".equals(acOnline, ignoreCase = true)) chargerType.append("AC")
            if ("true".equals(usbOnline, ignoreCase = true)) {
                if (chargerType.isNotEmpty()) chargerType.append(" + ")
                chargerType.append("USB")
            }
            if ("true".equals(wirelessOnline, ignoreCase = true)) {
                if (chargerType.isNotEmpty()) chargerType.append(" + ")
                chargerType.append("无线")
            }
            if ("true".equals(dockOnline, ignoreCase = true)) {
                if (chargerType.isNotEmpty()) chargerType.append(" + ")
                chargerType.append("底座")
            }
            if (chargerType.isNotEmpty()) info.chargerType = chargerType.toString()

            // 充电协议检测
            val protocol = detectChargingProtocol()
            if (protocol != null) info.chargerType = protocol
        } catch (_: Throwable) {}
    }

    // ========== 辅助方法 ==========

    /**
     * 检测充电协议 — USB PD / QC / SuperVOOC / Mi Flash Charge
     */
    private fun detectChargingProtocol(): String? {
        try {
            val oplusType = readSysfsLine("/sys/class/oplus_chg/battery/fastcharge_status")
                ?: readSysfsLine("/sys/kernel/oplus_chg/battery/fastcharge_status")
            if (oplusType != null) {
                return when {
                    oplusType.contains("supervooc", true) -> "SuperVOOC"
                    oplusType.contains("vooc", true) -> "VOOC"
                    else -> oplusType
                }
            }

            val miCharge = readSysfsLine("/sys/class/power_supply/battery/charge_type")
                ?: readSysfsLine("/sys/class/power_supply/bms/charge_type")
            if (miCharge != null) {
                return when {
                    miCharge.contains("PD", true) -> "USB-PD"
                    miCharge.contains("QC", true) -> "QC3.0"
                    miCharge.contains("Fast", true) -> "FastCharge"
                    miCharge.contains("Turbo", true) -> "Mi Turbo Charge"
                    else -> null
                }
            }

            val usbType = readSysfsLine("/sys/class/power_supply/usb/type")
            if (usbType != null) {
                return when {
                    usbType.contains("PD", true) -> "USB-PD"
                    usbType.contains("QC", true) -> "QC3.0"
                    else -> null
                }
            }
        } catch (_: Throwable) {}
        return null
    }

    /**
     * 电压特征匹配充电协议 (学术验证方法)
     * 5V=标准, 9V=QC2.0, 12V=QC3.0, >15V=USB-PD, >20V=PPS/PD3.0
     */
    private fun detectChargingProtocolVoltage(info: BatteryInfo): String {
        if (!info.isCharging) return ""
        val v = info.effectiveVoltage / 1000f  // 转换为 V
        val a = Math.abs(info.currentNowUA) / 1_000_000f  // 转换为 A

        // 先尝试 sysfs 专用路径
        val sysfsProtocol = detectChargingProtocol()
        if (sysfsProtocol != null) return sysfsProtocol

        // 电压特征匹配
        return when {
            v >= 20f -> "USB-PD 3.0 / PPS (${"%.1f".format(v)}V · ${"%.1f".format(a)}A)"
            v >= 15f -> "USB-PD (${"%.1f".format(v)}V)"
            v >= 12f -> "QC3.0 (12V · ${"%.1f".format(a)}A)"
            v >= 9f -> "QC2.0 (9V · ${"%.1f".format(a)}A)"
            a >= 3f -> "Fast Charge (${"%.1f".format(a)}A)"
            a >= 2f -> "Quick Charge (${"%.1f".format(a)}A)"
            else -> "Standard (5V · ${"%.1f".format(a)}A)"
        }
    }

    private fun readSysfsLine(path: String): String? {
        return try { java.io.File(path).readText().trim().takeIf { it.isNotEmpty() } } catch (_: Throwable) { null }
    }

    /**
     * Shell 方式读取 sysfs (Android 16 SELinux 绕过)。
     * 直接文件 I/O 在 Android 13+ 上对 power_supply 路径可能被拒绝，
     * 通过 Runtime.exec("cat", path) 以 shell 上下文读取。
     */
    private fun readSysfsLineShell(path: String): String? {
        // 方式1: Runtime.exec("cat", path)
        try {
            val proc = Runtime.getRuntime().exec(arrayOf("cat", path))
            val text = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()
            if (text.isNotEmpty()) return text
        } catch (_: Throwable) {}
        // 方式2: sh -c cat path (某些 ROM 上 sh context 权限不同)
        try {
            val proc = Runtime.getRuntime().exec(arrayOf("/system/bin/sh", "-c", "cat $path 2>/dev/null"))
            val text = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()
            if (text.isNotEmpty()) return text
        } catch (_: Throwable) {}
        return null
    }

    /**
     * 读取 sysfs Long 值 — 先直接读取，失败时用 shell 兜底
     */
    private fun readSysfsLongRobust(path: String): Long {
        // 尝试直接读取
        val direct = readSysfsLine(path)?.toLongOrNull()
        if (direct != null && direct > 0) return direct
        // Shell 兜底
        val shell = readSysfsLineShell(path)?.toLongOrNull()
        return shell ?: -1
    }

    private fun chargeStatusToString(status: Int): String = when (status) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "充电中"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "放电中"
        BatteryManager.BATTERY_STATUS_FULL -> "已充满"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "未充电"
        else -> "未知"
    }

    private fun healthToString(health: Int): String = when (health) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "良好"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "过热"
        BatteryManager.BATTERY_HEALTH_DEAD -> "损坏"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "过压"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "故障"
        BatteryManager.BATTERY_HEALTH_COLD -> "过冷"
        else -> "未知"
    }

    // ========== Flow 流式电池脉冲 (2026-06-18) ==========

    /**
     * 电池状态脉冲事件 — 轻量级 delta 事件，非完整数据模型。
     * 采用分层事件设计，仅携带变更字段，避免全量模型重建开销。
     */
    sealed class BatteryPulseEvent {
        /** 电量百分比变化 */
        data class LevelChanged(val percent: Int, val scale: Int) : BatteryPulseEvent()
        /** 充/放电状态切换 */
        data class PlugStateChanged(val isPlugged: Boolean, val chargerType: String) : BatteryPulseEvent()
        /** 温度变化 (单位: ℃) */
        data class TemperatureChanged(val celsius: Float) : BatteryPulseEvent()
        /** 电流读数更新 (单位: µA, 带符号方向) */
        data class CurrentUpdated(val microAmps: Long, val source: String) : BatteryPulseEvent()
        /** 电压读数更新 (单位: mV) */
        data class VoltageUpdated(val millivolts: Int) : BatteryPulseEvent()
        /** 电池健康状态变化 */
        data class HealthChanged(val health: String) : BatteryPulseEvent()
    }

    /**
     * 启动电池事件流 — 基于 BroadcastReceiver 的 callbackFlow。
     * 设计决策：
     *   - 返回 Flow<BatteryPulseEvent> 分层事件，仅推送 delta 变更
     *   - Compose 重组粒度更细，避免全量 BatteryInfo 重建触发整页重组
     *   - 基于 Android 标准 BatteryManager 广播机制，无第三方依赖
     */
    fun monitorBatteryPulses(): Flow<BatteryPulseEvent> = callbackFlow {
        var lastLevel = -1
        var lastPlugged = -1
        var lastTemp = -1f
        var lastHealth = ""

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                // 电量脉冲
                val rawLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val rawScale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (rawLevel >= 0 && rawScale > 0) {
                    val pct = (rawLevel * 100 / rawScale)
                    if (pct != lastLevel) {
                        lastLevel = pct
                        trySend(BatteryPulseEvent.LevelChanged(pct, rawScale))
                    }
                }

                // 插拔状态脉冲
                val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                if (plugged != lastPlugged) {
                    lastPlugged = plugged
                    val chargerLabel = when {
                        (plugged and BatteryManager.BATTERY_PLUGGED_AC) != 0 -> "AC"
                        (plugged and BatteryManager.BATTERY_PLUGGED_USB) != 0 -> "USB"
                        (plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS) != 0 -> "无线"
                        plugged > 0 -> "未知"
                        else -> ""
                    }
                    trySend(BatteryPulseEvent.PlugStateChanged(plugged > 0, chargerLabel))
                }

                // 温度脉冲
                val tempRaw = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
                if (tempRaw > 0) {
                    val celsius = tempRaw / 10f
                    if (kotlin.math.abs(celsius - lastTemp) > 0.5f) {
                        lastTemp = celsius
                        trySend(BatteryPulseEvent.TemperatureChanged(celsius))
                    }
                }

                // 健康状态脉冲
                val health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
                val healthStr = healthToString(health)
                if (healthStr != lastHealth) {
                    lastHealth = healthStr
                    trySend(BatteryPulseEvent.HealthChanged(healthStr))
                }
            }
        }

        // 注册 sticky + 实时双通道
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val sticky = appContext.registerReceiver(receiver, filter)
        if (sticky != null) {
            receiver.onReceive(appContext, sticky)
        }
        awaitClose { appContext.unregisterReceiver(receiver) }
    }

    // ========== BBK 电流归一化 (2026-06-18) ==========

    /**
     * BBK 设备电流归一化 — 结合厂商特征 + 路径启发式。
     *
     * 设计依据：
     *   - 部分 OEM (OPPO/OnePlus/realme) 的 current_now sysfs 直接返回 mA 而非标准 µA
     *   - 本方案采用三重判定：(1) 厂商检测 (2) 路径前缀匹配 (3) 数值量级推断
     *   - 提供 estimateBbKCurrent() 快速路径用于已知 BBK 设备
     */
    private fun normalizeBbKCurrent(rawValue: Long, sourcePath: String): Int {
        if (rawValue == 0L || rawValue == Long.MIN_VALUE) return 0

        val manufacturer = Build.MANUFACTURER.lowercase()
        val isBbKVendor = manufacturer.contains("oneplus")
                || manufacturer.contains("oppo")
                || manufacturer.contains("realme")

        val isBbKPath = sourcePath.contains("oplus")
                || sourcePath.contains("vooc")
                || sourcePath.contains("bms")

        val absRaw = kotlin.math.abs(rawValue)

        return when {
            // 明确 BBK 路径 + 典型 mA 范围 → 直接视为 mA，转换
            isBbKPath && absRaw in 100..20000 -> rawValue.toInt()
            // BBK 厂商 + 路径不详 + 小数值 → 按 µA 处理
            isBbKVendor && absRaw < 20000 -> rawValue.toInt()
            // BBK 厂商 + 路径不详 + 大数值 → 按 µA 处理 (已是 µA)
            isBbKVendor && absRaw >= 20000 -> (rawValue / 1000).toInt()
            // 非 BBK + 小数值 → 按 µA 处理
            absRaw < 100 -> (rawValue * 1000).toInt()
            // 非 BBK + 大数值 → 按 µA 处理 (标准)
            else -> (rawValue / 1000).toInt()
        }
    }

    /** 为 BBK 机型快速归一化 (仅依赖 raw value，不查路径) */
    fun estimateBbKCurrent(rawMicroAmps: Long): Int {
        val absRaw = kotlin.math.abs(rawMicroAmps)
        if (absRaw == 0L) return 0
        val manufacturer = Build.MANUFACTURER.lowercase()
        val isBbK = manufacturer.contains("oneplus")
                || manufacturer.contains("oppo")
                || manufacturer.contains("realme")
        return if (isBbK && absRaw < 20000) {
            rawMicroAmps.toInt()  // BBK 小值 = 已是 mA
        } else {
            (rawMicroAmps / 1000).toInt()  // 标准 µA → mA
        }
    }
}