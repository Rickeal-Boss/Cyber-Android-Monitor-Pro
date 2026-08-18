package com.rb.cybermonitorpro.ui.effects

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset

/**
 * F3 水波纹圆形展开状态机 — 进入/退出双弹簧。
 * 半径只由 showXxx 状态机驱动的 expand/collapse 改变；
 * 预测性返回手势只驱动压暗 alpha（backProgress），两套时钟互不干扰、无竞态。
 */
@Stable
class CircularRevealState(
    initialProgress: Float = 0f,
    private val expandSpec: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow,
    ),
    private val collapseSpec: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium,
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
