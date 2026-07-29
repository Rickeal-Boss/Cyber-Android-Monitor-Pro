package com.rb.cybermonitorpro.ui.effects

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.pager.PagerState
import kotlin.math.abs

/**
 * 页面滑动交错变换 v2 — 多卡片左右平移甩尾(tail-whip)动效
 *
 * 核心机制:
 * ┌──────────────────────────────────────────────────────────────┐
 * │  要点              │  实现                                    │
 * ├───────────────────┼──────────────────────────────────────────┤
 * │  主运动轴          │  ★ 水平主导 translationX (HORIZONTAL_PARALLAX) │
 * │  多卡片级联        │  ★ cascade = 1 + idx*STAGGER_STEP        │
 * │  (甩尾/鞭梢)       │    越靠下摆幅越大 → 横向弯曲成鞭尾         │
 * │  每页独立偏移      │  ★ per-page offset: page-(cur+frac)       │
 * │  无跳变            │  ★ animateFloatAsState(spring) 平滑 + 无 early return │
 * │  辅助景深          │  scale + alpha 轻度衰减                   │
 * └───────────────────┴──────────────────────────────────────────┘
 *
 * v1 教训: 水平视差绑定全局 pager 偏移 → 各页同值 → 看起来像原生滑动。
 *          v2 改用每页独立偏移 + 逐卡片 cascade 差异 → 卡片以不同率左右平移 → 甩尾。
 *
 * 使用方式:
 * ```
 * // 在 HorizontalPager 内容 lambda 内按页包裹:
 * HorizontalPager(state = pagerState) { page ->
 *     StaggeredPageProvider(pagerState = pagerState, page = page) {
 *         when (page) { 0 -> DashboardScreen() ... }
 *     }
 * }
 *
 * // 每张卡片按从上到下顺序编号:
 * MetricCard(modifier = Modifier.staggeredSwipe(cardIndex = 0), ...)
 * ```
 */

/** 当前页面相对于屏幕中心的平滑偏移量: 0=居中激活, ±1=偏离一屏 */
val LocalPageOffset: ProvidableCompositionLocal<Float> =
    compositionLocalOf { 0f }

// ═══════════════════════════════════════════════════════════════
//  可调参数 — 甩尾(tail-whip)动效: 多卡片左右平移 + 横向级联弯曲
// ═══════════════════════════════════════════════════════════════

/** 级联步进 — 每递增一张卡片增加的交错系数 (越大=越靠下的卡片摆幅越大=鞭梢) */
private const val STAGGER_STEP = 0.11f

/** ★ 水平甩尾主导强度 — 卡片左右平移位移倍率 (相对屏宽), 这是动效的主运动轴 */
private const val HORIZONTAL_PARALLAX = 0.19f

/** 极弱垂直余量 — 保持结构对称, 几乎不可见 (0=纯水平甩尾) */
private const val VERTICAL_WAVE = 0.0f

/** 缩放衰减基数 — 过程中卡片缩小幅度 (辅助景深) */
private const val SCALE_DECAY = 0.05f

/** 透明度衰减基数 — 过程中卡片淡出幅度 (辅助景深) */
private const val ALPHA_DECAY = 0.15f

// ═══════════════════════════════════════════════════════════════

/**
 * 卡片级联滑动 Modifier — 甩尾(tail-whip)风格
 *
 * 通过 [LocalPageOffset] 读取本页的弹簧平滑偏移,
 * 按 [cardIndex] 施加横向级联:
 *   - 顶部卡片领动, 越靠下的卡片 [cascade] 越大 → 左右平移摆幅越大(鞭梢)
 *   - 多张卡片以不同水平位移率跟随 → 页面横向弯曲成"甩尾"形状
 *   - 配合 spring 欠阻尼, 落定时轻微回弹 → 鞭尾甩动感
 */
@Stable
fun Modifier.staggeredSwipe(cardIndex: Int): Modifier = composed {
    // 本页的平滑偏移: 由 StaggeredPageProvider 提供, 已经过 spring 平滑
    val pageOffset = LocalPageOffset.current

    // 级联因子: 序号越大(越靠下)摆幅越大 → 鞭梢效应 (tip of the whip)
    val stagger = cardIndex.toFloat() * STAGGER_STEP
    val cascade = 1f + stagger

    this.graphicsLayer {
        // ★ 不做 early return — pageOffset=0 时所有变换自动为 identity (0 × 任何值 = 0)
        // 这消除了 v1 的跳变根因

        val eff = pageOffset * cascade // 每张卡片的实际有效偏移

        // ── ★ 主运动: 水平甩尾 (多卡片左右平移, 越靠下摆幅越大 → 横向鞭尾弯曲) ──
        translationX = size.width * eff * HORIZONTAL_PARALLAX

        // ── 极弱垂直余量 (几乎为 0, 保留结构对称) ──
        translationY = size.height * eff * VERTICAL_WAVE

        // ── 缩放: 过渡中轻微缩小 (景深层次感) ──
        val s = (1f - abs(eff) * SCALE_DECAY).coerceIn(0.82f, 1f)
        scaleX = s
        scaleY = s

        // ── 透明度: 过渡中轻微淡出 ──
        alpha = (1f - abs(eff) * ALPHA_DECAY).coerceIn(0f, 1f)
    }
}

// ═══════════════════════════════════════════════════════════════

/**
 * 页面级联变换 Provider — 在 [HorizontalPager] 内容 lambda 内按页调用
 *
 * 计算本页 [page] 相对于屏幕中心的原始偏移,
 * 经弹簧物理 ([spring]) 平滑后通过 [LocalPageOffset] 提供给子卡片。
 *
 * 弹簧参数调优:
 * - dampingRatio=0.72: 轻微欠阻尼 → 澎湃OS 的弹性回弹感
 * - stiffness=380: 中等刚度 → 跟手灵敏但不生硬
 */
@Composable
fun StaggeredPageProvider(
    pagerState: PagerState,
    page: Int,
    content: @Composable () -> Unit
) {
    // 原始偏移: page - (currentPage + currentPageOffsetFraction)
    //   = 0  → 本页完全居中 (激活态)
    //   = +1 → 本页在右侧一屏外
    //   = -1 → 本页在左侧一屏外
    val rawOffset = page - (pagerState.currentPage + pagerState.currentPageOffsetFraction)

    // ★ 弹簧物理平滑 — 消除跳变的根本手段
    // 当手指释放/页面落定时, rawOffset 从非零过渡到 0,
    // spring 自然地将其弹回零位 → 卡片平滑归位, 无突变
    val smoothed by animateFloatAsState(
        targetValue = rawOffset,
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = 380f
        ),
        label = "staggeredPageOffset"
    )

    CompositionLocalProvider(LocalPageOffset provides smoothed) {
        content()
    }
}
