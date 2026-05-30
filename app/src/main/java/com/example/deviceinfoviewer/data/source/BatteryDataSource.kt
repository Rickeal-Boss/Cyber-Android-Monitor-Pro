package com.example.deviceinfoviewer.data.source

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
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

        // === 充电状态 ===
        val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        info.chargeStatus = chargeStatusToString(status)
        info.isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL

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
                info.chargingPowerMw = powerMw.toLong()
                info.isCharging = true
            } else {
                info.dischargingPowerMw = powerMw.toLong()
            }
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
            // OPPO/Realme
            "/sys/class/power_supply/battery/real_charging_current" to "battery/real_charge",
            "/sys/class/power_supply/battery/fast_charge_current" to "battery/fast_charge",
            // 华为
            "/sys/class/power_supply/battery/charging_current" to "battery/charging_current",
            // 三星
            "/sys/class/power_supply/battery/batt_current_now" to "battery/batt_current",
            "/sys/class/power_supply/battery/batt_current_adc" to "battery/batt_current_adc",
            // MTK
            "/sys/devices/platform/mt-battery/current_now" to "mt-battery/current_now",
            "/sys/devices/platform/battery_meter/current_now" to "battery_meter/current_now",
        )

        for ((path, desc) in currentPaths) {
            try {
                val value = SysFsReader.readLong(path)
                if (value != -1L && value != Long.MIN_VALUE && value != 0L) {
                    Log.d(TAG, "current_now from $desc: $value µA")
                    return Pair(value, desc)
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

        return Pair(0L, "无法获取")
    }

    /** 兼容旧 API */
    private fun getCurrentNow(): Long = getCurrentNowFull().first

    // ========== 电池循环次数（50+ 路径全网方案） ==========

    /**
     * @return Pair<循环次数, 来源描述>
     */
    private fun getBatteryCycleCountFull(): Pair<Int, String> {
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
