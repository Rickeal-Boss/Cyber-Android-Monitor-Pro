package com.example.deviceinfoviewer

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * 多级震动反馈工具 — 适配线性马达 (Linear Resonant Actuator)
 *
 * 通过 Android VibrationEffect API 调用系统预定义触觉效果，
 * 利用设备线性马达 (LRA) 提供 Hi-Fi 震感，而非简单的转子马达脉冲。
 */
object HapticUtils {

    private var cachedIntensity = 2 // 1=弱 2=中 3=强
    private var cachedEnabled = true

    fun refreshSettings(settings: AppSettings) {
        cachedEnabled = settings.hapticEnabled
        cachedIntensity = settings.hapticIntensity
    }

    /** 轻触 — 屏幕点击反馈 (Tick) */
    fun lightTap(context: Context) = vibrate(context, LIGHT)

    /** 标准点击 — 按钮确认感 (Click) */
    fun standardTap(context: Context) = vibrate(context, STANDARD)

    /** 重按 — 重要操作确认 (Heavy Click) */
    fun heavyTap(context: Context) = vibrate(context, HEAVY)

    /** 滑动刻度 — Slider 档位切换 (自定义阶梯感) */
    fun stepTick(context: Context) {
        if (!cachedEnabled) return
        val vibe = getVibrator(context) ?: return
        val effect = when (cachedIntensity) {
            1 -> VibrationEffect.createOneShot(8, 64)   // ~25% 振幅
            2 -> VibrationEffect.createOneShot(12, 128) // ~50% 振幅
            else -> VibrationEffect.createOneShot(16, 192) // ~75% 振幅
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibe.vibrate(effect)
    }

    // ── 内部 ──

    private const val LIGHT = 0
    private const val STANDARD = 1
    private const val HEAVY = 2

    private fun vibrate(context: Context, type: Int) {
        if (!cachedEnabled) return
        val vibe = getVibrator(context) ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // API < 29: createPredefined 不可用 → 降级为简单脉冲
            val dur = when { type == HEAVY -> 30L; type == STANDARD -> 15L; else -> 8L }
            @Suppress("DEPRECATION") try { vibe.vibrate(dur) } catch (_: Exception) {}
            return
        }

        val effect = when (cachedIntensity) {
            1 -> when (type) {
                HEAVY -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                STANDARD -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                else -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            }
            3 -> when (type) {
                HEAVY -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
                STANDARD -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                else -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
            }
            else -> when (type) {
                HEAVY -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                STANDARD -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                else -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            }
        }
        vibe.vibrate(effect)
    }

    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}
