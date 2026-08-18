package com.rb.cybermonitorpro.data.source

import kotlin.math.pow

/**
 * 气压 → 海拔换算（国际标准大气压高公式）— 纯函数，可单测。
 *
 *   h = 44330.77 × (1 − (P / P0)^0.190263)   [米]
 *
 * - P0 = 1013.25 hPa → 绝对海拔（海平面基准）
 * - P0 = 用户标定气压 → 相对高度（±0.3 m 短时精度，适合爬楼/登山）
 * - GPS 反算（绝对模式标定）: P0 = P / (1 − altGPS / 44330.77)^(1 / 0.190263)
 *
 * BARO-01 边界守卫: altGPS ≥ 44330.77 时根号底数 ratio ≤ 0 → 返回 NaN（不产生非法值）。
 */
object AltitudeComputer {
    const val SEA_LEVEL_HPA = 1013.25
    const val SCALE = 44330.77
    const val EXPONENT = 0.190263

    /** 气压(hPa) + 参考气压 P0(hPa) → 海拔(米)；输入非正时返回 NaN */
    fun pressureToAltitudeMeters(pressureHpa: Double, p0Hpa: Double): Double {
        if (pressureHpa <= 0 || p0Hpa <= 0 || pressureHpa.isNaN() || p0Hpa.isNaN()) return Double.NaN
        return SCALE * (1 - (pressureHpa / p0Hpa).pow(EXPONENT))
    }

    /** 已知海拔(米) + 当前气压(hPa) → 反解参考气压 P0(hPa)；ratio<=0 边界返回 NaN */
    fun altitudeToP0Hpa(pressureHpa: Double, altitudeMeters: Double): Double {
        if (pressureHpa <= 0 || pressureHpa.isNaN() || altitudeMeters.isNaN()) return Double.NaN
        val ratio = 1 - altitudeMeters / SCALE
        return if (ratio <= 0) Double.NaN   // ★ BARO-01: altGPS ≥ 44330.77 时底数非正
        else pressureHpa / ratio.pow(1.0 / EXPONENT)
    }

    /** EMA 指数滤波（α=0.15 ≈ 1s 窗口），首次样本直接透传 */
    fun ema(newValue: Double, prevEma: Double?, alpha: Double = 0.15): Double =
        prevEma?.let { alpha * newValue + (1 - alpha) * it } ?: newValue
}
