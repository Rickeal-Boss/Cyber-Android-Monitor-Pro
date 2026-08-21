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
 * - 停止瞬间：`false` → 渲染器自驱动 + 上传预算自适应自然达成「垂直滚动停止 = 不刷新」：
 *   滚动中队列消化快、停止时队列必空 → 零渲染（pre21 已移除 pre20 的停止抑制窗口）；
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
