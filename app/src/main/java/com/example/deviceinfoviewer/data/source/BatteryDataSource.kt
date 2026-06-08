package com.example.deviceinfoviewer.data.source

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.util.Log

import com.example.deviceinfoviewer.AppSettings
import com.example.deviceinfoviewer.data.model.BatteryInfo

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

        // === 健康状态 ===
        val health = batteryStatus.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
        info.health = healthToString(health)

        // === 电池技术 ===
        info.technology = batteryStatus.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: ""

        // === 容量（多路径） ===
        readBatteryCapacity(info)

        // === 电流（多路径，带符号 µA） ===
        val (currentUA, currentSource) = getCurrentNowFull()
        info.currentNowUA = currentUA
        info.currentNowSource = currentSource

        // === 功率 = |电压(V) × 电流(A)| = |电压(mV) × 电流(µA)| / 1,000,000 = mW ===
        val effVoltage = info.effectiveVoltage
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

        // === dumpsys battery 附加信息 ===
        readDumpsysBattery(info)

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
            val value = SysFsReader.readLong(path)
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

        // 4. 如果 BatteryManager 没有容量，用 charge_full_design
        if (info.capacityDesignMAh <= 0 && info.chargeFullDesignMAh > 0) {
            info.capacityDesignMAh = info.chargeFullDesignMAh
        }
        if (info.capacityNowMAh <= 0 && info.chargeFullMAh > 0) {
            info.capacityNowMAh = info.chargeFullMAh
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
                val rawValue = SysFsReader.readLong(path)
                if (rawValue == -1L || rawValue == Long.MIN_VALUE) continue
                if (rawValue == 0L) continue  // 值为0继续尝试下一个路径

                // === 单位自动检测（核心修复） ===
                // OPPO 部分路径返回 mA（0~5000），标准路径返回 µA（0~5000000）
                // 启发式：值 < 100 且非零 → 大概率是 mA（手机正常的充放电电流不会低于100µA）
                val adjustedValue = if (kotlin.math.abs(rawValue) < 100) {
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

        // BatteryManager 隐藏属性 CURRENT_NOW
        try {
            val current = SysFsReader.getBatteryIntProperty(appContext, "BATTERY_PROPERTY_CURRENT_NOW")
            if (current != -1 && current != 0) {
                Log.d(TAG, "current_now from BatteryManager hidden API: $current µA")
                return Pair(current.toLong(), "BatteryManager hidden API")
            }
        } catch (_: Throwable) { /* fall through */ }

        // === ColorOS 13.1 专属兜底: dumpsys battery 提取 ===
        try {
            val proc = Runtime.getRuntime().exec(arrayOf("dumpsys", "battery"))
            val reader = proc.inputStream.bufferedReader()
            val output = reader.readText()
            reader.close()
            proc.waitFor()

            // 匹配 dumpsys 中的充电电流字段
            val dumpsysCurrentPatterns = listOf(
                Regex("""(?i)Max charging current[=:：]\s*(\d+)"""),
                Regex("""(?i)Charging current[=:：]\s*(\d+)"""),
                Regex("""(?i)Charge counter[=:：]\s*(\d+)"""),
            )
            for (regex in dumpsysCurrentPatterns) {
                val match = regex.find(output)
                match?.let {
                    val value = it.groupValues[1].toLongOrNull()
                    if (value != null && value > 0) {
                        // dumpsys 返回通常为 µA
                        Log.d(TAG, "current_now via dumpsys battery: $value µA")
                        return Pair(value, "dumpsys battery")
                    }
                }
            }
        } catch (_: Throwable) { /* fall through */ }

        return Pair(0L, "无法获取")
    }

    /** 兼容旧 API */
    private fun getCurrentNow(): Long = getCurrentNowFull().first

    // ========== 电池循环次数（50+ 路径全网方案） ==========

    /**
     * @return Pair<循环次数, 来源描述>
     */
    private fun getBatteryCycleCountFull(): Pair<Int, String> {
        // === Level 0: BatteryManager 隐藏属性 BATTERY_PROPERTY_CYCLE_COUNT (值=10, API 14+ 新增) ===
        try {
            val bm = appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            if (bm != null) {
                try {
                    val propField = BatteryManager::class.java.getDeclaredField("BATTERY_PROPERTY_CYCLE_COUNT")
                    propField.isAccessible = true
                    val propId = propField.getInt(null)
                    val cycle = bm.getIntProperty(propId)
                    if (cycle > 0 && cycle < 10000) {
                        Log.d(TAG, "cycle_count via BatteryManager hidden API: $cycle")
                        return Pair(cycle, "BatteryManager hidden API (cycle_count)")
                    }
                } catch (_: Throwable) {
                    try {
                        val cycle = bm.getIntProperty(10)
                        if (cycle > 0 && cycle < 10000) {
                            Log.d(TAG, "cycle_count via BatteryManager.getIntProperty(10): $cycle")
                            return Pair(cycle, "BatteryManager intProperty(10)")
                        }
                    } catch (_: Throwable) {}
                }
            }
        } catch (_: Throwable) {}

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

        // === Level 0.3: /proc/ 下电池芯片驱动暴露的节点 ===
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
                val content = java.io.File(path).readText()
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

        // === Level 0.4: logcat 系统日志扫描 — 很多 OEM 在 Health HAL 中打印循环计数 ===
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
        )
        for ((path, desc) in extraSysfsPaths) {
            val cnt = SysFsReader.readInt(path)
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
            val cnt = SysFsReader.readInt(path)
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
            val cnt = SysFsReader.readInt(path)
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
            val cnt = SysFsReader.readInt(path)
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
        try {
            val proc = Runtime.getRuntime().exec(arrayOf("dumpsys", "battery"))
            val reader = proc.inputStream.bufferedReader()
            val output = reader.readText()
            reader.close()
            proc.waitFor()

            // 匹配 "Cycle count: 123" 或类似格式
            val dumpsysCycleRegex = Regex("""(?i)(?:cycle\s*count|cycle_cnt|battery_cycle|charge_cycle)[=:：]\s*(\d+)""")
            val match = dumpsysCycleRegex.find(output)
            match?.let {
                val cnt = it.groupValues[1].toIntOrNull()
                if (cnt != null && cnt > 0 && cnt < 10000) {
                    Log.d(TAG, "cycle_count via dumpsys battery: $cnt")
                    return Pair(cnt, "dumpsys battery")
                }
            }

            // 部分 OEM 使用 "battery cycle count:"
            val altRegex = Regex("""(?i)battery\s+cycle\s+count[=:：]\s*(\d+)""")
            val altMatch = altRegex.find(output)
            altMatch?.let {
                val cnt = it.groupValues[1].toIntOrNull()
                if (cnt != null && cnt > 0 && cnt < 10000) {
                    return Pair(cnt, "dumpsys battery (alt)")
                }
            }

            // 通用数值型：任意包含 cycle 的行中提取首个 >= 2 位的数字
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
            val proc = Runtime.getRuntime().exec(arrayOf("dumpsys", "batterystats", "--checkin"))
            val reader = proc.inputStream.bufferedReader()
            val output = reader.readText()
            reader.close()
            proc.waitFor()

            // checkin 格式: "9,p,电池,0,0,0,0,cycle:123,..."
            val checkinCycleRegex = Regex("""cycle[:=](\d{1,4})""", RegexOption.IGNORE_CASE)
            val match = checkinCycleRegex.find(output)
            match?.let {
                val cnt = it.groupValues[1].toIntOrNull()
                if (cnt != null && cnt > 0 && cnt < 10000) {
                    Log.d(TAG, "cycle_count via dumpsys batterystats --checkin: $cnt")
                    return Pair(cnt, "dumpsys batterystats --checkin")
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

    companion object {
        private const val TAG = "BatteryDataSource"
    }
}
