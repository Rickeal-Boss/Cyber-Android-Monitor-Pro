package com.rb.cybermonitorpro.ui.components

/**
 * 旋转角度策略: 1080° = 3 整圈首尾同角图标回正;
 * 离散滑块相邻档跳变 ≤ 180° 原则自动降圈。
 */
object FancySliderRotation {
    const val DEFAULT = 1080f
    fun forSteps(steps: Int): Float = when {
        steps >= 4 -> DEFAULT   // 5+ 档, 每档 ≤216°
        steps == 3 -> 720f      // 5 档,  每档 180°
        steps == 2 -> 540f      // 4 档,  每档 180°
        steps == 1 -> 360f      // 3 档,  每档 180°
        else -> DEFAULT         // 连续
    }
}
