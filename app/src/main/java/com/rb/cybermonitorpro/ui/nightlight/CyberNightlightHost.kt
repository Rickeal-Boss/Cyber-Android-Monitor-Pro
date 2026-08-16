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
 * CyberNightlight TurboXDR 的 Compose 宿主：把 HdrLumeSurfaceView 挂进组合树。
 *
 * 点亮真 HDR 的最终闸门 = 开关开启 && 覆盖层不可见 && 未被门控抑制：
 *  - CyberNightlightSwitch.enabled：设置页开关（默认关）。
 *  - hidden：设置/悬浮窗/HDR实验室/传感器详情等覆盖层打开时隐藏（避免盖在覆盖层上）。
 *  - NightlightState.suppressed：省电/发热时抑制。
 *
 * 通过 AndroidView 挂载 GLSurfaceView；其 setZOrderOnTop(true) 使浮层位于 SDR UI 之上，
 * 但触摸穿透（isClickable=false）。可见性完全由 setActive 控制（关时画一帧纯透明）。
 */
@Composable
fun CyberNightlightHost(
    hidden: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val enabled = CyberNightlightSwitch.enabled
    val intensity = CyberNightlightSwitch.intensity
    val suppressed by NightlightState.suppressed

    val viewRef = remember { mutableStateOf<HdrLumeSurfaceView?>(null) }
    var lastActive by remember { mutableStateOf<Boolean?>(null) }

    DisposableEffect(context) {
        NightlightState.attach(context)
        onDispose { NightlightState.detach(context) }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx: Context ->
            HdrLumeSurfaceView(ctx).also { viewRef.value = it }
        }
    )

    // 闸门：三者都满足才点亮
    val effective = enabled && !hidden && !suppressed
    LaunchedEffect(effective) {
        if (lastActive != effective) {
            viewRef.value?.setActive(effective)
            lastActive = effective
        }
    }
    LaunchedEffect(intensity) {
        viewRef.value?.setIntensity(intensity)
    }
}
