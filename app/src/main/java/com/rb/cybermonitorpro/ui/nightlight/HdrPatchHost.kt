package com.rb.cybermonitorpro.ui.nightlight

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.rb.cybermonitorpro.ui.effects.CyberNightlightSwitch

/**
 * 局部 HDR 增亮的 Compose 宿主 — 把 [HdrPatchSurfaceView] 挂进组合树。
 *
 * 闸门设计（与 [CyberNightlightHost] **完全一致**，三重 AND）：
 * 1. `CyberNightlightSwitch.enabled`：TurboXDR 总开关。
 * 2. `hidden`：覆盖层可见性（设置/悬浮窗/HDR实验室等打开时隐藏）。
 * 3. `NightlightState.suppressed`：省电/发热运行时门控。
 *
 * 额外控制：
 * - `CyberNightlightSwitch.patchIntensity`：「局部 HDR 增亮」子滑块强度倍率（1.0x–8.0x），
 *   独立于 CyberNightlight 的呼吸强度（0–1）。两者叠加：
 *   - CyberNightlightHost → 边缘环境光呼吸晕
 *   - HdrPatchHost → 局部 UI 元素点亮
 *
 * 挂载位置：与 CyberNightlightHost 并列，同在 MainActivity 根 Box 中。
 * 两者共享同一 TurboXDR 开关闸门、同一 headroom 请求通道。
 */
@Composable
fun HdrPatchHost(
    hidden: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // ── 三重闸门（与 CyberNightlightHost 一致）──
    val enabled = CyberNightlightSwitch.enabled
    val patchIntensity = CyberNightlightSwitch.patchIntensity
    val suppressed by NightlightState.suppressed

    val viewRef = remember { mutableStateOf<HdrPatchSurfaceView?>(null) }
    var lastActive by remember { mutableStateOf<Boolean?>(null) }

    // 运行时门控生命周期（复用 NightlightState，引用计数安全）
    DisposableEffect(context) {
        NightlightState.attach(context)
        onDispose { NightlightState.detach(context) }
    }

    // 挂载 GLSurfaceView
    AndroidView(
        modifier = modifier,
        factory = { ctx: Context ->
            HdrPatchSurfaceView(ctx).also { viewRef.value = it }
        }
    )

    // 闸门生效
    val effective = enabled && !hidden && !suppressed
    LaunchedEffect(effective) {
        if (lastActive != effective) {
            viewRef.value?.setActive(effective)
            lastActive = effective
        }
    }

    // 子滑块强度即时生效
    LaunchedEffect(patchIntensity) {
        viewRef.value?.setIntensityMultiplier(patchIntensity)
    }
}
