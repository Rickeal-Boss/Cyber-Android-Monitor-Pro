package com.rb.cybermonitorpro.ui.nightlight

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

/**
 * CyberNightlight TurboXDR 的运行时门控：发热 / 省电模式下主动抑制 HDR，
 * 避免局部 HDR 增亮加剧整机发热与耗电（与 RefreshPolicy 的省电意识一致）。
 *
 * - 省电模式 (PowerManager.isPowerSaveMode)：开启即抑制。
 * - 发热等级 (API 29+ getCurrentThermalStatus)：≥ THERMAL_STATUS_MODERATE 抑制。
 *
 * 通过 Compose `State<Boolean>` 暴露 `suppressed`，宿主层将其与开关态、覆盖层可见性
 * 做 AND，只有三者都满足才真正点亮真 HDR。
 */
object NightlightState {

    private val _suppressed = mutableStateOf(false)
    val suppressed: State<Boolean> get() = _suppressed

    private var powerManager: PowerManager? = null
    private var thermalListener: Any? = null
    private var receiver: BroadcastReceiver? = null
    private var attachedCount = 0

    // ★ pre20-b：垂直滚动状态广播（任意 Screen 的 scrollState.isScrollInProgress 实时写入）。
    //   渲染器据此：滚动中预算放大（P2）、停止瞬间 120ms 窗口抑制 requestRender（P1）——
    //   垂直滚动 = 纯跟随不换贴片；只有水平翻页（scrollGated）才做整套离场/入场。
    @Volatile var verticalScrolling: Boolean = false
        private set
    fun setVerticalScrolling(v: Boolean) { verticalScrolling = v }

    /** 在宿主 Composable 挂载时调用（可重入，计数引用）。 */
    fun attach(context: Context) {
        attachedCount++
        if (attachedCount > 1) return
        runCatching {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            powerManager = pm
            reevaluate(pm)

            // 省电模式变化广播
            val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            val r = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    reevaluate(powerManager)
                }
            }
            receiver = r
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(r, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(r, filter)
            }

            // 发热等级监听（API 29+）
            if (Build.VERSION.SDK_INT >= 29 && pm != null) {
                val listener = PowerManager.OnThermalStatusChangedListener { status ->
                    reevaluate(pm, forcedThermal = status)
                }
                thermalListener = listener
                pm.addThermalStatusListener(listener)
            }
        }.onFailure { _suppressed.value = false }
    }

    /** 在宿主 Composable 卸载时调用（计数引用，归零才真正反注册）。 */
    fun detach(context: Context) {
        attachedCount--
        if (attachedCount > 0) return
        runCatching {
            receiver?.let { context.unregisterReceiver(it) }
            receiver = null
            if (Build.VERSION.SDK_INT >= 29) {
                (thermalListener as? PowerManager.OnThermalStatusChangedListener)?.let { l ->
                    powerManager?.removeThermalStatusListener(l)
                }
            }
            thermalListener = null
            powerManager = null
        }
        _suppressed.value = false
    }

    fun isSuppressed(): Boolean = _suppressed.value

    private fun reevaluate(pm: PowerManager?, forcedThermal: Int? = null) {
        val powerSave = runCatching { pm?.isPowerSaveMode == true }.getOrDefault(false)
        val thermal = if (forcedThermal != null) {
            forcedThermal
        } else if (Build.VERSION.SDK_INT >= 29) {
            runCatching { pm?.currentThermalStatus ?: 0 }.getOrDefault(0)
        } else 0
        // ≥ THERMAL_STATUS_MODERATE(2)：开始发热，抑制 HDR 以防加剧。
        val hot = thermal >= 2
        _suppressed.value = powerSave || hot
    }

    /** 诊断用：导出当前门控原因（供设置页/QA 展示）。 */
    fun reason(pm: PowerManager?): String = runCatching {
        val powerSave = pm?.isPowerSaveMode == true
        val thermal = if (Build.VERSION.SDK_INT >= 29) (pm?.currentThermalStatus ?: 0) else 0
        buildString {
            if (powerSave) append("省电模式")
            if (thermal >= 2) {
                if (isNotEmpty()) append(" + ")
                append("发热等级$thermal")
            }
            if (isEmpty()) append("正常")
        }
    }.getOrDefault("未知")
}
