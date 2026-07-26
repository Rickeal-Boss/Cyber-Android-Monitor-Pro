package com.rb.cybermonitorpro.data.source

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 电流单位判定逻辑单元测试
 *
 * 验证 BatteryDataSource.CurrentPathRegistry.convertCurrentToMicroamps()
 * 的 UnitHint 阈值逻辑在不同输入下的正确性。
 *
 * 测试覆盖:
 *   1. oplus 路径 (ASSUME_MA): raw=1234 → mA→µA (=1,234,000)
 *   2. oplus 路径 (ASSUME_MA): raw=1234567 → µA (保留原值)
 *   3. 标准路径 (ASSUME_UA): raw=500000 → µA (保留原值)
 *   4. 标准路径 (ASSUME_UA): raw=30 → mA→µA (=30,000)
 *   5. AUTO 路径 (阈值判定)
 *   6. 零值/负值边界
 */
class BatteryDataSourceCurrentUnitTest {

    // ========================
    // 核心验证 (code2.md 指定的测试数据)
    // ========================

    @Test
    fun `oplus路径 raw=1234 应识别为mA 转换后=1234000µA`() {
        // 1234 mA → 1,234,000 µA (= 1.234 A，正常充电电流)
        val result = BatteryDataSource.convertCurrentToMicroamps(1234L, "/sys/class/oplus_chg/battery/current_now")
        assertEquals("oplus 1234 should be converted from mA to µA", 1_234_000L, result)
    }

    @Test
    fun `oplus路径 raw=1234567 应识别为µA 保留原值`() {
        // 1,234,567 → 超过 OPPO_UA_THRESHOLD (15,000)
        // 1,234,567 µA = 1.235 A (正常充电电流)
        // 如果误判为 mA → 1,234 A → 不可能
        val result = BatteryDataSource.convertCurrentToMicroamps(1_234_567L, "/sys/class/oplus_chg/battery/real_icharging")
        assertEquals("oplus 1234567 >15k threshold, should stay as µA", 1_234_567L, result)
    }

    @Test
    fun `标准路径 raw=500000 应识别为µA 保留原值`() {
        // 500,000 µA = 0.5 A，标准充电电流
        val result = BatteryDataSource.convertCurrentToMicroamps(500_000L, "/sys/class/power_supply/battery/current_now")
        assertEquals("standard path 500000 should be µA as-is", 500_000L, result)
    }

    @Test
    fun `标准路径 raw=30 应识别为mA 转换后=30000µA`() {
        // 30 µA (=0.03 mA) 正常采集中几乎不可见 → 按 mA 处理
        val result = BatteryDataSource.convertCurrentToMicroamps(30L, "/sys/class/power_supply/battery/current_now")
        assertEquals("standard path 30 below threshold, should be mA→µA", 30_000L, result)
    }

    // ========================
    // UnitHint 路径匹配验证
    // ========================

    @Test
    fun `oplus路径应匹配ASSUME_MA`() {
        assertEquals("ASSUME_MA", BatteryDataSource.resolveUnitHint("/sys/class/oplus_chg/battery/current_now"))
        assertEquals("ASSUME_MA", BatteryDataSource.resolveUnitHint("/sys/kernel/oplus_chg/battery/charging_current"))
        assertEquals("ASSUME_MA", BatteryDataSource.resolveUnitHint("vooc_charging_current"))
    }

    @Test
    fun `标准power_supply路径应匹配ASSUME_UA`() {
        assertEquals("ASSUME_UA", BatteryDataSource.resolveUnitHint("/sys/class/power_supply/battery/current_now"))
        assertEquals("ASSUME_UA", BatteryDataSource.resolveUnitHint("/sys/class/power_supply/bms/current_now"))
    }

    @Test
    fun `未注册路径应匹配AUTO`() {
        assertEquals("AUTO", BatteryDataSource.resolveUnitHint("/sys/devices/unknown/chip/current"))
    }

    // ========================
    // 阈值边界测试
    // ========================

    @Test
    fun `AUTO路径 raw=20000 超过15k阈值 应识别为µA`() {
        val result = BatteryDataSource.convertCurrentToMicroamps(20_000L, "/sys/class/power_supply/battery/vivo_current")
        assertEquals("AUTO 20000 >15k → µA", 20_000L, result)
    }

    @Test
    fun `AUTO路径 raw=40 低于50阈值 应识别为mA`() {
        val result = BatteryDataSource.convertCurrentToMicroamps(40L, "/sys/class/power_supply/battery/vivo_current")
        assertEquals("AUTO 40 <50 → mA→µA", 40_000L, result)
    }

    @Test
    fun `AUTO路径 raw=500 在50和15k之间 默认识别为µA`() {
        val result = BatteryDataSource.convertCurrentToMicroamps(500L, "/sys/class/power_supply/battery/batt_current_now")
        assertEquals("AUTO 500 in gray zone → default µA", 500L, result)
    }

    // ========================
    // 零值与负数
    // ========================

    @Test
    fun `raw=0 直接返回0`() {
        val result = BatteryDataSource.convertCurrentToMicroamps(0L, "/sys/class/power_supply/battery/current_now")
        assertEquals(0L, result)
    }

    @Test
    fun `负值 放电电流处理 (ASSUME_UA)`() {
        // -300,000 µA = 放电 0.3A
        val result = BatteryDataSource.convertCurrentToMicroamps(-300_000L, "/sys/class/power_supply/battery/current_now")
        assertEquals("negative discharge current should be preserved", -300_000L, result)
    }

    @Test
    fun `负值 放电电流处理 (ASSUME_MA oplus)`() {
        // -800 mA = 放电 0.8A → -800,000 µA
        val result = BatteryDataSource.convertCurrentToMicroamps(-800L, "/sys/class/oplus_chg/battery/current_now")
        assertEquals("oplus negative -800mA → -800,000µA", -800_000L, result)
    }

    // ========================
    // 原因A修复回归防护: normalizeBinderCurrent 的 BatteryManager 分支
    // (BBK 厂商 + 小值 <20mA 视为 mA 直出；其余 µA→mA ÷1000；非 BBK 一律 ÷1000)
    // 注: 直接传入 isBbKVendor 以在 CI 上确定性覆盖厂商分支，不依赖 Build.MANUFACTURER
    // ========================

    @Test
    fun `非BBK设备 BatteryManager源 大值应按µA÷1000`() {
        // 三星/小米/Pixel 等: getLongProperty 返回合法 µA (1500mA = 1,500,000 µA)
        // 非 BBK → 必须 ÷1000，不能误放大 (回归防护)
        val result = BatteryDataSource.normalizeBinderCurrent(1_500_000L, "BatteryManager binder", false)
        assertEquals("non-BBK long µA → 1500 mA", 1500, result)
    }

    @Test
    fun `BBK设备 BatteryManager源 小值mA应直出`() {
        // ColorOS/OPlus: getLongProperty 返回 mA 量级 (充电 1500mA → raw=1500)
        // BBK 厂商 + 1500<20000 → 视为 mA 直出，不 ÷1000
        val result = BatteryDataSource.normalizeBinderCurrent(1500L, "BatteryManager binder", true)
        assertEquals("BBK small mA-scale → 1500 mA (no /1000)", 1500, result)
    }

    @Test
    fun `BBK设备 BatteryManager源 大值µA应÷1000`() {
        // BBK 固件若返回合法 µA (1,500,000) → ≥20000 → ÷1000 → 1500 mA
        val result = BatteryDataSource.normalizeBinderCurrent(1_500_000L, "BatteryManager binder", true)
        assertEquals("BBK large µA → 1500 mA", 1500, result)
    }

    @Test
    fun `BBK设备 放电负值mA 应保留符号直出`() {
        // ColorOS 放电 500mA → raw=-500 (mA 量级) → BBK 小值 → -500 mA
        val result = BatteryDataSource.normalizeBinderCurrent(-500L, "BatteryManager binder", true)
        assertEquals("BBK negative mA-scale → -500 mA", -500, result)
    }

    @Test
    fun `BBK设备 快充5000mA量级 应直出不÷1000`() {
        // 快充 5000mA → raw=5000 (<20000) → BBK 视为 mA 直出
        val result = BatteryDataSource.normalizeBinderCurrent(5000L, "BatteryManager binder", true)
        assertEquals("BBK 5000mA-scale → 5000 mA", 5000, result)
    }

    // ========================
    // Fix1 回归防护: shouldFallbackToSocDelta (SoC-Δ 门控放宽)
    // 原门控写死 source=="无法获取", 导致 ColorOS binder 返回 0 时被旁路
    // ========================

    @Test
    fun `全路径失败 无法获取 应触发SoCΔ`() {
        // 原行为: source=="无法获取" + 0 电流 → 触发
        val result = BatteryDataSource.shouldFallbackToSocDelta(0L, "无法获取", 50, 5000)
        assertTrue("source=无法获取 should trigger SoC-Δ", result)
    }

    @Test
    fun `ColorOS binder返回0 应触发SoCΔ_Fix1`() {
        // Fix1: source=="BatteryManager binder" + 0 电流 (ColorOS property 恒为0) → 触发
        val result = BatteryDataSource.shouldFallbackToSocDelta(0L, "BatteryManager binder", 50, 5000)
        assertTrue("ColorOS binder-0 should trigger SoC-Δ", result)
    }

    @Test
    fun `未知source的0 不应触发_防御性`() {
        // 防御: 其他 source 的 0 不触发 (理论上不会以 0 收口, 但保持严格)
        val result = BatteryDataSource.shouldFallbackToSocDelta(0L, "sysfs/battery/current_now", 50, 5000)
        assertFalse("unknown source 0 should NOT trigger", result)
    }

    @Test
    fun `非零电流 不应触发`() {
        val result = BatteryDataSource.shouldFallbackToSocDelta(1500_000L, "BatteryManager binder", 50, 5000)
        assertFalse("non-zero current should NOT trigger", result)
    }

    @Test
    fun `电量无效 不应触发`() {
        val result = BatteryDataSource.shouldFallbackToSocDelta(0L, "无法获取", 0, 5000)
        assertFalse("levelPercent=0 invalid → NOT trigger", result)
    }

    @Test
    fun `容量未知 不应触发`() {
        val result = BatteryDataSource.shouldFallbackToSocDelta(0L, "BatteryManager binder", 50, 0)
        assertFalse("capacity unknown → NOT trigger", result)
    }
}
