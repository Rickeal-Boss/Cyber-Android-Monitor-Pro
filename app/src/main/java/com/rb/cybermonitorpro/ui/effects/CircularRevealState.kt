package com.rb.cybermonitorpro.ui.effects

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset

/**
 * F3 水波纹圆形展开状态机 — 进入/退出双 tween（精确控时，无弹簧过冲）。
 * 半径只由 showXxx 状态机驱动的 expand/collapse 改变；
 * 预测性返回手势只驱动压暗 alpha（backProgress），两套时钟互不干扰、无竞态。
 *
 * 时长对齐 CAMP 动画三问题修复方案：
 *   进入 700ms（FastOutSlowIn，Material 标准圆形展开曲线）
 *   退出 560ms（FastOutSlowIn，快速开始缓慢结束）
 *   原 spring(StiffnessMediumLow=400) ≈ 300ms → 翻倍至 700ms
 *   原 spring(StiffnessMedium=1500) ≈ 216ms → 翻倍至 560ms
 */
@Stable
class CircularRevealState(
    initialProgress: Float = 0f,
    private val expandSpec: AnimationSpec<Float> = tween(
        durationMillis = 700,
        easing = FastOutSlowInEasing,
    ),
    private val collapseSpec: AnimationSpec<Float> = tween(
        durationMillis = 560,
        easing = FastOutSlowInEasing,
    ),
) {
    val progress = Animatable(initialProgress)
    var origin: Offset = Offset.Zero; internal set
    val isRevealing: Boolean get() = progress.value > 0.01f

    suspend fun expand(o: Offset) {
        origin = o
        progress.animateTo(1f, expandSpec)
    }

    suspend fun collapse() {
        progress.animateTo(0f, collapseSpec)
    }
}

@Composable
fun rememberCircularRevealState(initialProgress: Float = 0f) =
    remember { CircularRevealState(initialProgress) }
