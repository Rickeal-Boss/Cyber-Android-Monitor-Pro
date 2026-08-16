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
 * 闪光触发条件（仿电子表夜光"按一下闪一下"）：
 *  - toggle on（CyberNightlightSwitch.enabled 由 false → true）：setActive(true) 内部自带一次 fireFlash。
 *  - 切页面（currentPage 变化）：fireFlash（图表/数字/卫星/图标/文字所在的页面切换即"对应页面时同样触发"）。
 *
 * 通过 AndroidView 挂载 GLSurfaceView；其 setZOrderOnTop(true) 使浮层位于 SDR UI 之上，
 * 但触摸穿透（isClickable=false）。可见性完全由 setActive 控制（关时画一帧纯透明）。
 */
@Composable
fun CyberNightlightHost(
    hidden: Boolean,
    currentPage: Int,
    modifier: Modifier = Modifier,
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

    // 主动作：开关 on / 覆盖层切换 → setActive（内部触发一次性边缘闪光）
    val effective = enabled && !hidden && !suppressed
    LaunchedEffect(effective) {
        val view = viewRef.value ?: return@LaunchedEffect
        if (lastActive != effective) {
            view.setActive(effective)
            lastActive = effective
        }
    }

    // 强度同步：slider 实时写入 headroom（1.0×–8.0×）
    LaunchedEffect(intensity) {
        viewRef.value?.setIntensity(intensity)
    }

    // 切页面触发闪光：图表/数字/卫星分布图色点/Tab 栏图标+文字所在的页面
    // "对应页面时同样触发" 的语义：currentPage 变化在 effective=true 期间都播一次边缘闪光
    var lastPage by remember { mutableStateOf(currentPage) }
    LaunchedEffect(currentPage, effective) {
        val view = viewRef.value ?: return@LaunchedEffect
        if (effective && lastPage != currentPage) {
            view.fireFlash()
        }
        lastPage = currentPage
    }
}