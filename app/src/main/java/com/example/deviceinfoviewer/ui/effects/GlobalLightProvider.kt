package com.example.deviceinfoviewer.ui.effects

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import kotlinx.coroutines.delay

/**
 * 全局光照提供者 — Windows 10 Fluent Design 风格
 *
 * 包裹应用的根 Composable 内容, 在整窗口范围内:
 * 1. 捕获鼠标悬停移动 — 桌面/DeX/触控板模式
 * 2. 捕获手指触摸按下/滑动 — 手机/平板触摸模式
 * 3. 将指针坐标注入 GlobalLightState → CompositionLocal
 * 4. 空闲超时自动淡出光照
 *
 * 使用方式:
 * ```
 * GlobalLightProvider {
 *     // 应用内容 — 其中任意可组合项可通过 revealLight() 获得光照效果
 *     YourAppContent()
 * }
 * ```
 *
 * 设计参考:
 * - Microsoft Fluent Design System — Reveal Highlight (指针跟随径向渐变光照)
 * - Walterlv WPF RevealBrush 实现 — 全局光照模式 (CompositionTarget.Rendering + Mouse.GetPosition)
 * - Windows 10 Start Menu / Settings 页面光照交互效果
 *
 * @param idleTimeoutMs 空闲超时时间 (ms), 超过此时间无交互则渐隐光照, 默认 5000ms
 * @param content 应用内容
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GlobalLightProvider(
    idleTimeoutMs: Long = 5000L,
    content: @Composable () -> Unit
) {
    val lightState = remember { GlobalLightState() }
    val animatedPosition = rememberAnimatedLightPosition(lightState)

    // ── 空闲超时检测: 超过 idleTimeoutMs 无交互则切换到 IDLE 模式 ──
    LaunchedEffect(lightState.lastEventTime, lightState.mode) {
        if (lightState.mode != GlobalLightState.Mode.IDLE) {
            delay(idleTimeoutMs)
            // 只有在 timeout 期间确实没有新事件时才切换
            val elapsed = System.currentTimeMillis() - lightState.lastEventTime
            if (elapsed >= idleTimeoutMs) {
                lightState.toIdle()
            }
        }
    }

    // ── 全局坐标跟踪: 记录 Box 在窗口中的偏移, 将本地坐标转换为窗口坐标 ──
    var windowOffset by remember { mutableStateOf(Offset.Zero) }
    // ★ 性能优化 (2026-06-19): 用 rememberUpdatedState 让 pointerInput 用固定 key(Unit)
    //   原方案 pointerInput(windowOffset) 在 onGloballyPositioned 每次触发时重启 pointerInput，
    //   导致事件监听器频繁重建。改为固定 key + rememberUpdatedState 读取最新值。
    val currentWindowOffset by rememberUpdatedState(windowOffset)
    // ★ 节流: 桌面鼠标 Move 事件高频触发，限制最多每 16ms(一帧) 更新一次，避免 Spring 动画频繁重组
    var lastUpdateTime by remember { mutableLongStateOf(0L) }

    CompositionLocalProvider(LocalLightState provides lightState) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    windowOffset = coordinates.positionInWindow()
                }
                // ── 全局指针捕获 ──
                // 固定 key(Unit) 避免重启；windowOffset 通过 rememberUpdatedState 读取
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: continue
                            val now = System.currentTimeMillis()

                            when (event.type) {
                                PointerEventType.Move -> {
                                    // 节流: Move 事件最多每 16ms 更新一次
                                    if (now - lastUpdateTime < 16) continue
                                    lastUpdateTime = now
                                    // 鼠标/触控笔悬停移动
                                    val localPos = change.position
                                    val windowPos = Offset(
                                        x = localPos.x + currentWindowOffset.x,
                                        y = localPos.y + currentWindowOffset.y
                                    )
                                    lightState.update(windowPos, isTouch = false)
                                }
                                PointerEventType.Press -> {
                                    lastUpdateTime = now
                                    // 手指按下 / 鼠标点击
                                    val localPos = change.position
                                    val windowPos = Offset(
                                        x = localPos.x + currentWindowOffset.x,
                                        y = localPos.y + currentWindowOffset.y
                                    )
                                    // 手指触摸 vs 鼠标点击: 通过压力或触控类型区分
                                    val isTouch = change.pressure > 0.3f ||
                                            event.type == PointerEventType.Press
                                    lightState.update(windowPos, isTouch = isTouch)
                                }
                                PointerEventType.Release -> {
                                    // 手指抬起 — 暂不立即切换到 IDLE
                                    // 让空闲超时机制自然淡出
                                }
                                PointerEventType.Exit -> {
                                    lightState.toIdle()
                                }
                                PointerEventType.Enter -> {
                                    // 重新进入窗口 — 恢复
                                }
                                else -> { /* 忽略其他事件类型 */ }
                            }

                            // ★ 不消费事件 — 仅观察, 让事件继续传递给子组件
                            // 避免干扰 Tab 点击、BackHandler 手势等现有交互
                        }
                    }
                }
        ) {
            content()
        }
    }
}
