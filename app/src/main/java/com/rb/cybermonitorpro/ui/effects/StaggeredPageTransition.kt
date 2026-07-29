package com.rb.cybermonitorpro.ui.effects

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.pager.PagerState
import kotlin.math.abs

/**
 * 页面滑动交错变换 v2 — 澎湃OS 3.0 通知栏风格
 *
 * 核心改进 (v1→v2):
 * ┌──────────────────────────────────────────────────────────────┐
 * │  v1 问题          │  v2 修复                                 │
 * ├───────────────────┼──────────────────────────────────────────┤
 * │  全局 pager 偏移   │  ★ 每页独立偏移 (per-page offset)        │
 * │  pageOffset==0    │  ★ 弹簧平滑 animateFloatAsState(spring)  │
 * │  → return 跳变    │  ★ 无 early return，自然归零              │
 * │  水平视差主导      │  ★ 垂直级联为主 (translationY > X)       │
 * │  VERTICAL=0.008   │  ★ VERTICAL_CASCADE=0.10 (可见波浪)       │
 * │  无时间差          │  ★ 空间级联 cascade = 1+idx*STAGGER_STEP  │
 * └───────────────────┴──────────────────────────────────────────┘
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
//  可调参数 — 参考澎湃OS 3.0 通知栏/快捷设置面板动效参数
// ═══════════════════════════════════════════════════════════════

/** 级联步进 — 每递增一张卡片增加的交错系数 (越大底部卡片越滞后) */
private const val STAGGER_STEP = 0.08f

/** 垂直级联强度 — 过渡中卡片的垂直位移倍率 (相对自身高度) */
private const val VERTICAL_CASCADE = 0.10f

/** 缩放衰减基数 — 过程中卡片缩小幅度 */
private const val SCALE_DECAY = 0.07f

/** 透明度衰减基数 — 过程中卡片淡出幅度 */
private const val ALPHA_DECAY = 0.22f

/** 微弱水平视差 — 辅助增强方向感 (不主导) */
private const val HORIZONTAL_PARALLAX = 0.05f

// ═══════════════════════════════════════════════════════════════

/**
 * 卡片级联滑动 Modifier
 *
 * 通过 [LocalPageOffset] 读取本页的弹簧平滑偏移,
 * 按 [cardIndex] 施加空间级联 (越靠下的卡片位移/缩放/透明度变化越大),
 * 形成从上到下的波浪式跟随效果。
 */
@Stable
fun Modifier.staggeredSwipe(cardIndex: Int): Modifier = composed {
    // 本页的平滑偏移: 由 StaggeredPageProvider 提供, 已经过 spring 平滑
    val pageOffset = LocalPageOffset.current

    // 级联因子: 序号越大(越靠下)滞后越多 → 波浪从上到下传播
    val stagger = cardIndex.toFloat() * STAGGER_STEP
    val cascade = 1f + stagger

    this.graphicsLayer {
        // ★ 不做 early return — pageOffset=0 时所有变换自动为 identity (0 × 任何值 = 0)
        // 这消除了 v1 的跳变根因

        val eff = pageOffset * cascade // 每张卡片的实际有效偏移

        // ── 主运动: 垂直级联 (卡片上下浮动形成波浪) ──
        translationY = size.height * eff * VERTICAL_CASCADE

        // ── 辅助: 微弱水平视差 (增强滑动方向感, 但不主导) ──
        translationX = size.width * eff * HORIZONTAL_PARALLAX

        // ── 缩放: 过渡中轻微缩小 (景深层次感) ──
        val s = (1f - abs(eff) * SCALE_DECAY).coerceIn(0.75f, 1f)
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
