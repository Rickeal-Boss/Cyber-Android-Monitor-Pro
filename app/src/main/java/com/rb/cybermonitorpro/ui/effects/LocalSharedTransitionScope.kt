@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.rb.cybermonitorpro.ui.effects

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.compositionLocalOf

/**
 * F5 阶段 2: SharedTransitionLayout 的 scope 经 CompositionLocal 下发，
 * SensorsScreen 卡片与 SensorDetailContent 标题在同 key 下做 sharedElement 形变。
 * scope 为 null 时（未包裹/降级）调用侧以 then(Modifier) 优雅降级。
 */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
