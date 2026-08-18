package com.rb.cybermonitorpro.ui.nightlight

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collect

/**
 * ★ pre20-b：可上报垂直滚动状态的 [ScrollState]。
 *
 * 语义对齐需求：**垂直滚动 = 纯跟随（不换贴片）；水平翻页 = 才做整套离场/入场交换**。
 * - 滚动中：`NightlightState.verticalScrolling = true` → PatchRenderer 上传预算放大（6/帧）、
 *   贴片坐标跟随卡片移动平滑渲染；
 * - 停止瞬间：`false` 沿沿 → HdrPatchSurfaceView 120ms 窗口抑制 requestRender，
 *   保留滚动最后一帧已到位贴片（垂直滚动停止 = 不刷新）；
 * - 水平翻页仍走 pre14 的 scrollGated 门控（独立标志，两者可叠加）。
 *
 * 所有带 `verticalScroll` 的 Screen 一律用本函数替代 `rememberScrollState()`。
 */
@Composable
fun rememberHdrScrollState(): ScrollState {
    val state = rememberScrollState()
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }
            .collect { NightlightState.setVerticalScrolling(it) }
    }
    return state
}
