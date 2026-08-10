package com.rb.cybermonitorpro

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

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

    /** 拖拽拾起 — 重反馈，提示抓取成功 */
    fun dragStart(context: Context) = heavyTap(context)

    /** 拖拽落下 — 标准反馈，提示放置完成 */
    fun dragEnd(context: Context) = standardTap(context)

    /** 滑动刻度 — Slider 档位切换 (自定义阶梯感)
     *
     *  重要: 不使用 VibrationEffect.createOneShot(long, int)
     *  D8 API desugaring 对双参数 createOneShot 会生成 ExternalSyntheticApiModelOutline0,
     *  R8 tableswitch 优化可能导致 amplitude=0 传入 → IllegalArgumentException 崩溃。
     *
     *  方案: API 29+ 用 createPredefined (EFFECT_TICK/CLICK/HEAVY_CLICK)
     *       API 26-28 用 deprecated vibrate(duration) 简单脉冲降级
     */
    fun stepTick(context: Context) {
        if (!cachedEnabled) return
        // ★ #10c 节流: Slider 连续拖动 tick 最小间隔 50ms,
        //   避免高频合并事件触发振动器队列过载 (线性马达起停损耗 + 听感糊成一片)
        val now = System.currentTimeMillis()
        if (now - lastStepTickAt < STEP_TICK_MIN_INTERVAL_MS) return
        lastStepTickAt = now

        val vibe = getVibrator(context) ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // API 29+: 预定义效果 — 零 amplitude 参数, 彻底规避 D8 bug
            val effectId = when (cachedIntensity) {
                1 -> VibrationEffect.EFFECT_TICK          // 轻柔
                2 -> VibrationEffect.EFFECT_CLICK         // 标准
                else -> VibrationEffect.EFFECT_HEAVY_CLICK // 强烈
            }
            kotlin.runCatching {
                vibe.vibrate(VibrationEffect.createPredefined(effectId))
            }.onFailure { e ->
                Log.w(TAG, "stepTick predefined failed: ${e.message}")
            }
        } else {
            // ★ #10b: API 21-28 显式低版本分支 (pre-createPredefined)
            //   原实现仅覆盖 26-28, API 21-25 stepTick 静默失效; 统一走 deprecated 单参脉冲。
            //   三档强度按时长缩放 (转子马达无 amplitude 控制, 时长即强度)。
            val dur = when (cachedIntensity) {
                1 -> 10L; 2 -> 20L; else -> 35L
            }
            @Suppress("DEPRECATION")
            kotlin.runCatching { vibe.vibrate(dur) }
                .onFailure { e -> Log.w(TAG, "stepTick legacy failed: ${e.message}") }
        }
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
            // ★ 三档强度补齐: 原实现固定时长, 完全忽略用户强度设置;
            //   无 amplitude 控制的马达上「时长 × 强度系数」即等效强度分级
            val base = when { type == HEAVY -> 30L; type == STANDARD -> 15L; else -> 8L }
            val scale = when (cachedIntensity) { 1 -> 0.6f; 3 -> 1.4f; else -> 1.0f }
            val dur = (base * scale).toLong().coerceAtLeast(1L)
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

    // ★ #10c: stepTick 节流时间戳 (uptime 语义即可, 会话内单调)
    @Volatile
    private var lastStepTickAt = 0L
    private const val STEP_TICK_MIN_INTERVAL_MS = 50L

    // ★ #10a: Vibrator 实例会话级缓存 — getSystemService 每次 tap 都走 binder 查询,
    //   探测结果缓存后后续调用零 IPC 开销。null 同样缓存 (无振动硬件设备彻底静默)。
    @Volatile
    private var cachedVibrator: Vibrator? = null
    @Volatile
    private var vibratorProbed = false

    private fun getVibrator(context: Context): Vibrator? {
        if (vibratorProbed) return cachedVibrator
        val vibe: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        // ★ #10a: 硬件能力探测 — 无振动马达的设备 (部分平板/模拟器/TV) 直接返回 null,
        //   不创建 VibrationEffect, 全链路静默。hasVibrator() API 11+ 全版本可用。
        //   三档马达强度说明: hasAmplitudeControl() (API 26+) 仅表示支持幅度调节,
        //   不参与存在性判定 — 转子马达靠时长分级, 线性马达靠 EFFECT_* 预定义分级。
        cachedVibrator = vibe?.takeIf { it.hasVibrator() }
        vibratorProbed = true
        return cachedVibrator
    }

    private const val TAG = "HapticUtils"
}
