package com.rb.cybermonitorpro.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 天玑营销后缀归一化单元测试
 *
 * 验证 CpuCache.stripMarketingSuffix / marketingToSiliconId / lookup 对
 * Ultra/Max/+/Ultimate/Turbo/Apex/Energy/Elite/Pro/X/e 等 OEM 营销后缀的正确处理。
 *
 * 测试覆盖:
 *   1. stripMarketingSuffix 剥离各类后缀 (Ultra/Max/+/Ultimate/厂商前缀)
 *   2. marketingToSiliconId 营销名 → 硅片号映射
 *   3. lookup 带后缀的营销名/硅片号能命中基础硅片 (修复 "mt6897-ultra" 落兜底分支显示空白制程)
 *   4. lookup 干净硅片号仍正常命中 (回归)
 *   5. 孤立后缀/无数字安全返回 null 或正确归一化
 */
class CpuCacheTest {

    // ========================
    // 层1: 后缀剥离
    // ========================

    @Test
    fun `stripMarketingSuffix 剥离 Ultra 后缀`() {
        assertEquals("dimensity 9300", CpuCache.stripMarketingSuffix("Dimensity 9300-Ultra"))
        assertEquals("mt6897", CpuCache.stripMarketingSuffix("mt6897-ultra"))
        assertEquals("mt6989", CpuCache.stripMarketingSuffix("MT6989U"))
        assertEquals("dimensity 8100", CpuCache.stripMarketingSuffix("Dimensity 8100-Max"))
        assertEquals("dimensity 9300", CpuCache.stripMarketingSuffix("Dimensity 9300+"))
        assertEquals("dimensity 7300", CpuCache.stripMarketingSuffix("Dimensity 7300-Ultimate"))
        assertEquals("dimensity 8300", CpuCache.stripMarketingSuffix("MediaTek Dimensity 8300-Ultra"))
    }

    // ========================
    // 层2: 营销名 → 硅片号
    // ========================

    @Test
    fun `marketingToSiliconId 营销名转硅片号`() {
        assertEquals("mt6989", CpuCache.marketingToSiliconId("Dimensity 9300"))
        assertEquals("mt6897", CpuCache.marketingToSiliconId("Dimensity 8300"))
        assertEquals("mt6896", CpuCache.marketingToSiliconId("Dimensity 8200"))
        assertEquals("mt6878", CpuCache.marketingToSiliconId("Dimensity 7300"))
        assertEquals("mt6895", CpuCache.marketingToSiliconId("Dimensity 8100"))
    }

    // ========================
    // lookup: 带后缀应命中基础硅片
    // ========================

    @Test
    fun `lookup 带后缀的营销名能命中基础硅片`() {
        // 营销名 + Ultra 后缀
        val chip = CpuCache.lookup("Dimensity 9300-Ultra")
        assertNotNull(chip)
        assertEquals("mt6989", chip?.platformId)
        assertEquals("4nm TSMC N4P", chip?.processNode)

        // 硅片号 + Ultra 后缀 (文档原策略2.5 的缺口, 此处应直命中)
        val chip2 = CpuCache.lookup("mt6897-ultra")
        assertNotNull(chip2)
        assertEquals("mt6897", chip2?.platformId)

        // 紧贴数字的 U 后缀
        val chip3 = CpuCache.lookup("MT6989U")
        assertNotNull(chip3)
        assertEquals("mt6989", chip3?.platformId)

        // 厂商前缀 + 后缀组合
        val chip4 = CpuCache.lookup("MediaTek Dimensity 8300-Ultra")
        assertNotNull(chip4)
        assertEquals("mt6897", chip4?.platformId)
    }

    // ========================
    // 回归: 干净输入不受影响
    // ========================

    @Test
    fun `lookup 干净硅片号仍正常命中`() {
        val chip = CpuCache.lookup("mt6989")
        assertNotNull(chip)
        assertEquals("Dimensity 9300+", chip?.chipName)
        assertEquals("4nm TSMC N4P", chip?.processNode)
    }

    // ========================
    // 边界: 孤立后缀 / 无数字
    // ========================

    @Test
    fun `lookup 孤立后缀与无数字安全返回空或正确归一化`() {
        assertNull(CpuCache.lookup("ultra"))        // 无法映射 → null
        assertNull(CpuCache.lookup("dimensity"))   // 无数字 → null
        val chip = CpuCache.lookup("mt6897-x")     // -x 剥离 → mt6897
        assertNotNull(chip)
        assertEquals("mt6897", chip?.platformId)
    }
}
