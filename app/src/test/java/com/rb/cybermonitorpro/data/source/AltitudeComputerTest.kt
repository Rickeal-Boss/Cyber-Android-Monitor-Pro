package com.rb.cybermonitorpro.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * AltitudeComputer 国际气压标高公式单测（样例值见方案 §4.5）
 */
class AltitudeComputerTest {

    @Test
    fun `海平面气压换算海拔为0`() {
        assertEquals(0.0, AltitudeComputer.pressureToAltitudeMeters(1013.25, 1013.25), 0.01)
    }

    @Test
    fun `1000hPa 对应约111米`() {
        val h = AltitudeComputer.pressureToAltitudeMeters(1000.00, 1013.25)
        assertEquals(111.0, h, 1.0)
    }

    @Test
    fun `900hPa 对应约988米`() {
        val h = AltitudeComputer.pressureToAltitudeMeters(900.00, 1013.25)
        assertEquals(988.4, h, 2.0)
    }

    @Test
    fun `参考气压低于当前气压时海拔为负`() {
        val h = AltitudeComputer.pressureToAltitudeMeters(1013.25, 1000.00)
        assertEquals(-111.0, h, 1.0)
    }

    @Test
    fun `非正气压返回NaN`() {
        assertTrue(AltitudeComputer.pressureToAltitudeMeters(0.0, 1013.25).isNaN())
        assertTrue(AltitudeComputer.pressureToAltitudeMeters(Double.NaN, 1013.25).isNaN())
        assertTrue(AltitudeComputer.pressureToAltitudeMeters(1000.0, 0.0).isNaN())
    }

    @Test
    fun `GPS海拔反解P0与正算互逆`() {
        val p0 = AltitudeComputer.altitudeToP0Hpa(1000.00, 111.0)
        assertEquals(1013.25, p0, 0.5)
        // 互逆: 反解出的 P0 再正算应回到原海拔
        val h = AltitudeComputer.pressureToAltitudeMeters(1000.00, p0)
        assertEquals(111.0, h, 0.01)
    }

    @Test
    fun `BARO-01 海拔超过标高上界反解返回NaN`() {
        // altGPS ≥ 44330.77 → ratio ≤ 0 → NaN（不得产生非法值/负数开方）
        assertTrue(AltitudeComputer.altitudeToP0Hpa(1000.0, 44330.77).isNaN())
        assertTrue(AltitudeComputer.altitudeToP0Hpa(1000.0, 50000.0).isNaN())
    }

    @Test
    fun `EMA 首样本透传后续加权`() {
        assertEquals(1000.0, AltitudeComputer.ema(1000.0, null), 0.001)
        val next = AltitudeComputer.ema(2000.0, 1000.0, alpha = 0.15)
        assertEquals(0.15 * 2000.0 + 0.85 * 1000.0, next, 0.001)
        assertTrue(abs(next - 1150.0) < 0.001)
    }
}
