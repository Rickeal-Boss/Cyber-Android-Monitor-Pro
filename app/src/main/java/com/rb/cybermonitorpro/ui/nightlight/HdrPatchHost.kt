package com.rb.cybermonitorpro.ui.nightlight

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.rb.cybermonitorpro.ui.effects.CyberNightlightSwitch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/**
 * 行业首创「局部 HDR 增亮」的 Compose 宿主：把 [HdrPatchSurfaceView] 挂进组合树。
 *
 * 点亮真 HDR 的最终闸门 = 开关开启 && 覆盖层不可见 && 未被门控抑制：
 *  - CyberNightlightSwitch.enabled：设置页 TurboXDR 总开关（默认关）。
 *  - hidden：设置/悬浮窗/HDR实验室/传感器详情等覆盖层打开时隐藏。
 *  - NightlightState.suppressed：省电/发热时抑制。
 *
 * 与 CyberNightlightHost 同级挂载（同一根 Box，matchParentSize），共享同一 TurboXDR 开关闸门；
 * 贴片由各 UI 元素经 onGloballyPositioned 上报至 HdrPatchRegistry，本宿主订阅后推给渲染器。
 */
@Composable
fun HdrPatchHost(
    hidden: Boolean,
    pagerState: PagerState? = null,
    modifier: Modifier = Modifier,
) {
    val enabled = CyberNightlightSwitch.enabled
    val suppressed by NightlightState.suppressed
    val scope = rememberCoroutineScope()
    val viewRef = remember { mutableStateOf<HdrPatchSurfaceView?>(null) }
    // 主动作：闸门变化 → setActive（开启即进入事件驱动渲染；关闭移除 SurfaceView）
    val effective = enabled && !hidden && !suppressed && TurboXdrCompat.supported

    // ★ pre18：仅当 effective 时才挂载 HdrPatchSurfaceView，关闭时完全移除 SurfaceView。
    //   根因：全屏透明 PQ SurfaceView（setZOrderOnTop + 带 alpha 的 PixelFormat.RGBA_1010102）
    //   在部分 ROM（小米/HyperOS Android 16）上会让窗口合成层变为透明，系统桌面从孔洞透出
    //   （「背景板整块不见了」）。关闭时移除 SurfaceView 后窗口恢复不透明，背景稳定。
    if (effective) {
        AndroidView(
            modifier = modifier
                // 上报 surface 左上角的根坐标，供渲染器把贴片根坐标统一转为 surface 像素坐标
                .onGloballyPositioned { coords ->
                    val root = coords.localToRoot(Offset.Zero)
                    HdrPatchRegistry.surfaceRootX = root.x
                    HdrPatchRegistry.surfaceRootY = root.y
                },
            factory = { ctx ->
                HdrPatchSurfaceView(ctx).also {
                    viewRef.value = it
                    it.attachRegistry(scope)
                }
            }
        )
    }

    LaunchedEffect(effective) {
        if (effective) {
            viewRef.value?.setActive(true)
        } else {
            // 关闭：SurfaceView 已从组合树移除，补清全局贴片防幽灵残留
            viewRef.value?.setActive(false)
            HdrPatchRegistry.clear()
        }
    }

    // 强度同步：slider 实时写入贴片 surface 的 headroom（1.0×–8.0×），
    // 对齐 CyberNightlightHost 的 LaunchedEffect(intensity)。贴片内容亮度由
    // PatchRenderer.pqEnc 按 p.bias × intensity 线性插值施加（治 现象B）。
    val intensity = CyberNightlightSwitch.intensity
    LaunchedEffect(intensity) {
        viewRef.value?.setIntensity(intensity)
    }

    // ★ pre14-G2：翻页门控——翻页期间暂停 HDR 贴片渲染，到位延迟 80ms 后点亮。
    //   snapshotFlow 监听 isScrollInProgress（覆盖拖拽+fling+settle 全程）；
    //   collectLatest 保证延迟期间若再次翻页则取消本次 ungate。
    LaunchedEffect(pagerState) {
        val ps = pagerState ?: return@LaunchedEffect
        snapshotFlow { ps.isScrollInProgress }
            .collectLatest { scrolling ->
                if (scrolling) {
                    viewRef.value?.setScrollGated(true)
                } else {
                    delay(80)  // 等旧页 dispose(bitmap 解除引用)/新页 onGloballyPositioned 完成
                    viewRef.value?.setScrollGated(false)
                }
            }
    }

    DisposableEffect(Unit) {
        onDispose {
            // 卸载时清空全局贴片，避免幽灵贴片残留
            HdrPatchRegistry.clear()
            viewRef.value?.detachRegistry()
        }
    }
}
