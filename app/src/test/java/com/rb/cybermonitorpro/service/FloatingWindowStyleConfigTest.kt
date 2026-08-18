package com.rb.cybermonitorpro.service

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * FloatingWindowConfig 样式项单测 — 纯内存路径（不调 init(), prefs==null 时 SP 写入跳过）。
 * P2-2: 每个测试前 @Before 重置默认值, 不依赖测试执行顺序、不在测试末尾手动还原。
 */
class FloatingWindowStyleConfigTest {

    @Before
    fun resetToDefaults() {
        FloatingWindowConfig.textSizeSp = FloatingWindowConfig.DEFAULT_TEXT_SIZE_SP
        FloatingWindowConfig.textColor = FloatingWindowConfig.DEFAULT_TEXT_COLOR
        FloatingWindowConfig.windowAlpha = FloatingWindowConfig.DEFAULT_WINDOW_ALPHA
        FloatingWindowConfig.bgColor = FloatingWindowConfig.DEFAULT_BG_COLOR
    }

    @Test
    fun `默认文字大小与旧硬编码一致 11sp`() {
        assertEquals(11f, FloatingWindowConfig.textSizeSp, 0.001f)
    }

    @Test
    fun `默认文字颜色与旧硬编码一致 A05CFF`() {
        assertEquals(0xFFA05CFF.toInt(), FloatingWindowConfig.textColor)
    }

    @Test
    fun `默认窗口透明度与旧硬编码一致 085`() {
        assertEquals(0.85f, FloatingWindowConfig.windowAlpha, 0.001f)
    }

    @Test
    fun `默认背景色与旧硬编码一致 DC0A0A0F`() {
        assertEquals(0xDC0A0A0F.toInt(), FloatingWindowConfig.bgColor)
    }

    @Test
    fun `文字大小下界越界钳制到 9sp`() {
        FloatingWindowConfig.textSizeSp = 5f
        assertEquals(9f, FloatingWindowConfig.textSizeSp, 0.001f)
    }

    @Test
    fun `文字大小上界越界钳制到 22sp`() {
        FloatingWindowConfig.textSizeSp = 30f
        assertEquals(22f, FloatingWindowConfig.textSizeSp, 0.001f)
    }

    @Test
    fun `透明度下界越界钳制到 02`() {
        FloatingWindowConfig.windowAlpha = 0.05f
        assertEquals(0.2f, FloatingWindowConfig.windowAlpha, 0.001f)
    }

    @Test
    fun `透明度上界越界钳制到 10`() {
        FloatingWindowConfig.windowAlpha = 1.5f
        assertEquals(1f, FloatingWindowConfig.windowAlpha, 0.001f)
    }

    @Test
    fun `颜色不钳制任意 ARGB 合法`() {
        FloatingWindowConfig.textColor = 1
        assertEquals(1, FloatingWindowConfig.textColor)
        FloatingWindowConfig.bgColor = -0x1
        assertEquals(-0x1, FloatingWindowConfig.bgColor)
    }
}
