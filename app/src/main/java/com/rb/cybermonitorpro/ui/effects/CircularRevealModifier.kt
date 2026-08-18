package com.rb.cybermonitorpro.ui.effects

import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlin.math.hypot

/**
 * F3 水波纹圆形展开 — 零重组圆形裁剪 Modifier。
 *
 * progress/origin 以 () -> T lambda 捕获 Animatable/State 引用而非解构值，
 * 数值变化只触发 draw 失效（项目 StaggeredPageTransition v5.1 / EntranceReveal
 * 已验证模式；clipPath 渲染先例 CardRipple.kt）。
 *
 * 半径取 origin 到四角的最大对角线，保证圆形覆盖全屏任意触发点。
 */
fun Modifier.circularReveal(
    progress: () -> Float,
    origin: () -> Offset,
): Modifier = drawWithCache {
    val path = Path()
    val p = origin()
    val maxRadius = floatArrayOf(
        hypot(p.x, p.y),
        hypot(size.width - p.x, p.y),
        hypot(p.x, size.height - p.y),
        hypot(size.width - p.x, size.height - p.y),
    ).max()   // 覆盖四角的最长对角线

    onDrawWithContent {
        val t = progress()   // draw 阶段读 Animatable.value → 零重组
        when {
            t <= 0f -> Unit                                  // 完全收起：不绘制
            t >= 1f -> drawContent()                          // 快速路径：无裁剪
            else -> {
                path.rewind()
                path.addOval(Rect(center = origin(), radius = maxRadius * t))
                clipPath(path) { this@onDrawWithContent.drawContent() }
            }
        }
    }
}
