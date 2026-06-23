package com.example.deviceinfoviewer.data.source

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
}
