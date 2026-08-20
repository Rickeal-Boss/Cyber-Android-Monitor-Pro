package com.rb.cybermonitorpro.ui.nightlight

import android.os.Build

/**
 * TurboXDR 局部 HDR 依赖全屏透明 PQ SurfaceView（setZOrderOnTop + RGBA_1010102）。
 * 该 SurfaceView 在部分 ROM（小米 / Redmi / POCO，HyperOS，Android 16 = API 36+）上
 * 会让窗口合成层变透明、系统桌面从孔洞透出（pre18 根因：背景板整块消失、SDR 内容不可见）。
 * 此类设备优雅降级为 SDR（卡片描边走普通 Compose 渲染，由改动1保证可见）。
 * 真 HDR PQ 合成修复列为独立大议题（2-B 主修），本轮仅做 ROM 守卫。
 */
object TurboXdrCompat {
    /** 当前 ROM 是否支持 TurboXDR 局部 HDR。false = 受影响 ROM，应降级 SDR。 */
    val supported: Boolean = !isUnsupportedRom()

    private fun isUnsupportedRom(): Boolean {
        val mf = Build.MANUFACTURER?.lowercase().orEmpty()
        val br = Build.BRAND?.lowercase().orEmpty()
        val isXiaomiFamily = listOf("xiaomi", "redmi", "poco").any { mf.contains(it) || br.contains(it) }
        val isAndroid16Plus = Build.VERSION.SDK_INT >= 36
        return isXiaomiFamily && isAndroid16Plus
    }
}
