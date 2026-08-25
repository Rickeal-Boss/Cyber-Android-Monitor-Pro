package com.rb.cybermonitorpro.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DumpsysMeminfoParser 单测 — 覆盖 F-08 解析器鲁棒化:
 * 小数 (123.5K) / 多单位 (1.2M) / 千分位 (1,234K) /
 * OOM 段起始宽松匹配 (大小写/空格变体) / extractNamedValue 字段顺序变体。
 *
 * 入口为公开的 parse(meminfo 文本), 内部 private 解析方法经完整样本串间接覆盖。
 */
class DumpsysMeminfoParserTest {

    @Test
    fun `解析小数千分位与多单位 OOM 行`() {
        val sample = """
            Total PSS by OOM adjustment:
                123.5K: Native
                302,883K: System
                1.2M: Persistent
                1,234K: Foreground
                0K: B Services
            Total RAM: 7,500,000K (status normal)
        """.trimIndent()
        val (oom, summary) = DumpsysMeminfoParser.parse(sample)
        assertTrue(oom.isAvailable)
        // 123.5K → 123 KB; 302,883K → 302883; 1.2M → 1.2*1024=1228.8 → 1228 KB
        assertEquals(123L + 302_883L + 1228L, oom.systemPssKB)
        // 1,234K → 1234 KB; B Services 允许 0K
        assertEquals(1234L + 0L, oom.appPssKB)
        assertEquals(0L, oom.cachedPssKB)
        assertEquals(7_500_000L, summary.totalRamKB)
    }

    @Test
    fun `OOM 段起始宽松匹配大小写与空格变体`() {
        val sample = """
               total pss by oom adjustment :
                1,000K: Native
                2,000K: Foreground
            Total RAM: 4,000,000K (status normal)
        """.trimIndent()
        val (oom, _) = DumpsysMeminfoParser.parse(sample)
        assertTrue(oom.isAvailable)
        assertEquals(1_000L, oom.systemPssKB)
        assertEquals(2_000L, oom.appPssKB)
    }

    @Test
    fun `extractNamedValue 支持数值在前与名称在前两种顺序`() {
        // 数值在前: "1,234K cached pss"
        val sampleValueFirst = """
            Free RAM: 3,500,000K (1,234K cached pss + 5,678K cached kernel + 2,000,000K free)
            Used RAM: 4,000,000K (2,500,000K used pss + 1,500,000K kernel)
            Lost RAM: 500,000K
        """.trimIndent()
        val (_, s1) = DumpsysMeminfoParser.parse(sampleValueFirst)
        assertEquals(1_234L, s1.cachedPssKB)
        assertEquals(5_678L, s1.cachedKernelKB)
        assertEquals(2_500_000L, s1.usedPssKB)
        assertEquals(1_500_000L, s1.kernelUsedKB)

        // 名称在前: "cached pss: 1,234K" / "kernel: 1,500,000K"
        val sampleNameFirst = """
            Free RAM: 3,500,000K (cached pss: 1,234K + cached kernel: 5,678K + free: 2,000,000K)
            Used RAM: 4,000,000K (used pss: 2,500,000K + kernel: 1,500,000K)
            Lost RAM: 500,000K
        """.trimIndent()
        val (_, s2) = DumpsysMeminfoParser.parse(sampleNameFirst)
        assertEquals(1_234L, s2.cachedPssKB)
        assertEquals(5_678L, s2.cachedKernelKB)
        assertEquals(2_500_000L, s2.usedPssKB)
        assertEquals(1_500_000L, s2.kernelUsedKB)
    }

    @Test
    fun `空输入返回不可用`() {
        val (oom, summary) = DumpsysMeminfoParser.parse(null)
        assertFalse(oom.isAvailable)
        assertEquals(-1L, oom.systemPssKB)
        assertEquals(-1L, summary.totalRamKB)
    }
}
