package com.rb.cybermonitorpro.ui.effects

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

// ═══════════════════════════════════════════════════════════════
//  首屏入场 — 每卡错峰 Animatable reveal (draw 域)
//
//  复用 LineChart 既有写法 (LineChart.kt:77-79):
//      val reveal = remember { Animatable(0f) }
//      LaunchedEffect(Unit) { reveal.animateTo(1f, tween(400)) }
//  区别: LineChart 用 revealProgress 控制"画多少折线"; 这里用 graphicsLayer
//        控制卡片整体 alpha + 轻微上移 → 动画只更新绘制层, 不重组卡片 content。
//
//  一次解决两件事 (最省力路径):
//    1) 动画别撞冷启动首帧 — 首帧延后 FIRST_FRAME_DEFER_MS(≈一帧) 再启动错峰序列
//    2) 首帧本身别画太多 — 卡片从 alpha=0 起步, 按 order 错峰渐显,
//       任意时刻只有少数卡处于非零 alpha → GPU 绘制开销被摊薄
//
//  order: 入场顺序 (越小越先显). 概览页自上而下赋序 →
//         顶部关键卡(设备信息 / 实时指标)先于底部快捷入口。
// ═══════════════════════════════════════════════════════════════

/** 单卡渐显时长 */
private const val REVEAL_DURATION_MS = 420

/** 错峰步进 — 相邻 order 的启动间隔 (越大=卡与卡之间间隔越明显) */
private const val REVEAL_STAGGER_MS = 70f

/** 首帧延后 — 让冷启动首帧(布局/Pager 初始化)先完成, 再启动整条错峰序列 (≈60fps 一帧) */
private const val FIRST_FRAME_DEFER_MS = 16L

/** 上移距离 — 从下方 18dp 抬升归位, 配合 alpha 形成"升起渐显" */
private val REVEAL_OFFSET = 18.dp

/**
 * 首屏入场错峰 reveal — draw 域 (graphicsLayer) 应用, 不触发卡片 content 重组。
 *
 * @param order 入场顺序, 0 = 最先显. 概览页按视觉/重要度自上而下赋值。
 */
fun Modifier.entranceReveal(order: Int = 0): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        // ① 首帧延后一帧: 冷启动首帧(布局/Pager 初始化)先过, 避免入场动画与首帧抢占
        delay(FIRST_FRAME_DEFER_MS)
        // ② 错峰: 按 order 递延各自启动时刻
        delay((order * REVEAL_STAGGER_MS).toLong())
        // ③ 渐显 + 抬升归位
        progress.animateTo(1f, tween(REVEAL_DURATION_MS, easing = FastOutSlowInEasing))
    }
    val offsetPx = REVEAL_OFFSET.toPx()
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * offsetPx
    }
}
