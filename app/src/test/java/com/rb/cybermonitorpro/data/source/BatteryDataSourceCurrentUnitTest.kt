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
    // 原因A修复: Binder 电流单位解析 (getLongProperty × getIntProperty 交叉验证)
    // ========================

    @Test
    fun `AOSP标准 long返回µA int同量级1000倍 应直出`() {
        // AOSP 合规: 充电 1500mA → longUa=1,500,000 (µA), intMa=1500 (mA)
        // ratio≈1000 → 判定为合法 µA，保持不变
        val result = BatteryDataSource.resolveBinderCurrentMicroamps(1_500_000L, 1500)
        assertEquals("AOSP long µA should pass through", 1_500_000L, result)
    }

    @Test
    fun `ColorOS long返回mA量级 应与int同量级并×1000`() {
        // ColorOS 偏离 AOSP: 充电 1500mA，但 getLongProperty 返回 1500 (实为 mA)
        // longUa=1500, intMa=1500 → ratio≈1 <50 → 判定为 mA → ×1000 = 1,500,000 µA
        val result = BatteryDataSource.resolveBinderCurrentMicroamps(1500L, 1500)
        assertEquals("ColorOS mA-scale long should be ×1000 normalized to µA", 1_500_000L, result)
    }

    @Test
    fun `ColorOS 放电负值 mA量级 应保留符号并×1000`() {
        // 放电 500mA → getLongProperty 返回 -500 (mA 量级), getIntProperty 返回 -500
        // ratio≈1 → 判定为 mA → -500 ×1000 = -500,000 µA (放电)
        val result = BatteryDataSource.resolveBinderCurrentMicroamps(-500L, -500)
        assertEquals("ColorOS negative mA-scale should keep sign and ×1000", -500_000L, result)
    }

    @Test
    fun `ColorOS 大电流 mA量级 5000 应×1000`() {
        // 快充 5000mA → longUa=5000, intMa=5000 → ratio≈1 → 5,000,000 µA
        val result = BatteryDataSource.resolveBinderCurrentMicroamps(5000L, 5000)
        assertEquals("ColorOS high-current mA-scale → 5,000,000 µA", 5_000_000L, result)
    }

    @Test
    fun `long为MIN_VALUE 用intmA兜底转µA`() {
        // getLongProperty 不支持(返回 MIN_VALUE)，退回 getIntProperty(mA) → ×1000
        val result = BatteryDataSource.resolveBinderCurrentMicroamps(Long.MIN_VALUE, 800)
        assertEquals("long unsupported → int(mA)×1000 fallback", 800_000L, result)
    }

    @Test
    fun `long为MIN_VALUE 且int为0 应返回0`() {
        val result = BatteryDataSource.resolveBinderCurrentMicroamps(Long.MIN_VALUE, 0)
        assertEquals(0L, result)
    }

    @Test
    fun `int为0无法交叉验证 大值应按标准µA直出`() {
        // getIntProperty 返回 0 (可能不支持或真为0)，long 为大值 → 默认 µA 直出
        val result = BatteryDataSource.resolveBinderCurrentMicroamps(500_000L, 0)
        assertEquals("cannot cross-check → treat large long as µA", 500_000L, result)
    }

    @Test
    fun `long为0 直出0 不误判`() {
        // 有效 0 电流: longUa=0, intMa=0 → ratio=MAX → 直出 0 (不×1000)
        val result = BatteryDataSource.resolveBinderCurrentMicroamps(0L, 0)
        assertEquals(0L, result)
    }
}
