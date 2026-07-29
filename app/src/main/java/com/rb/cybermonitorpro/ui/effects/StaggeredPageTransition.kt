package com.rb.cybermonitorpro.ui.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocal
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.pager.PagerState
import kotlin.math.abs

/**
 * 页面滑动交错变换 — 左右切换页面时, 卡片从上到下逐个跟随滑动方向做级联位移/缩放/透明度变换
 *
 * 视觉效果参考 Android 12+ 通知中心 / 快捷设置面板的左右滑动过渡动画:
 *   - 顶部卡片最先响应滑动方向, 底部卡片滞后跟随 → 形成波浪级联
 *   - 每张卡片有独立的水平视差位移 + 轻微缩放 + 透明度渐变 + 垂直微偏移
 *
 * 使用方式:
 * ```
 * // 1) 在 HorizontalPager 外层提供 PagerState:
 * StaggeredPageProvider(pagerState = pagerState) {
 *     HorizontalPager(state = pagerState) { ... }
 * }
 *
 * // 2) 每张卡片按从上到下顺序编号并包裹:
 * MetricCard(
 *     modifier = Modifier.staggeredSwipe(cardIndex = 0),  // 最顶部卡片
 *     ...
 * )
 * ```
 */

/** ★ 页面状态 CompositionLocal — 由 MainTabs 在 HorizontalPager 层提供 */
val LocalPagerState: ProvidableCompositionLocal<Lazy<PagerState>> =
    compositionLocalOf { error("LocalPagerState not provided — wrap HorizontalPager with StaggeredPageProvider") }

// ═══════════════════════════════════════════════════════════════
//  可调参数 (可在后续通过参数化 Modifier 或 theme token 统一管理)
// ═══════════════════════════════════════════════════════════════

/** 水平视差强度 — 卡片随页面滑动的水平偏移倍率 (相对于屏幕宽度) */
private const val SWIPE_PARALLAX_X = 0.28f

/** 级联步进 — 每递增一张卡片增加的交错延迟系数 (越大=底部卡片越滞后) */
private const val STAGGER_STEP = 0.07f

/** 缩放衰减 — 滑动过程中卡片的缩小幅度基数 */
private const val SCALE_DECAY_BASE = 0.04f

/** 透明度衰减 — 滑动过程中卡片的淡出幅度基数 */
private const val ALPHA_DECAY_BASE = 0.18f

/** 垂直微偏移 — 滑动过程中卡片的垂直偏移 (增强"波浪"感, 相对于屏幕高度) */
private const val VERTICAL_WAVE = 0.008f

// ═══════════════════════════════════════════════════════════════

/**
 * 页面滑动交错变换 Modifier
 *
 * 通过 composed 工厂在组合上下文中读取 LocalPagerState,
 * 将 [pagerState.currentPageOffsetFraction] 驱动的级联变换应用到每张卡片。
 *
 * @param cardIndex 卡片在页面中的垂直序号 (从上到下: 0, 1, 2, ...)
 *   — 序号越小(越靠上)响应越快, 序号越大(越靠下)滞后越多, 形成级联波浪
 */
@Stable
fun Modifier.staggeredSwipe(cardIndex: Int): Modifier = composed {
    val pagerState = LocalPagerState.current.value
    // currentPageOffsetFraction: 当前页偏离 settled 位置的比例
    //   向左滑到下一页: 0 → -1    向右滑到上一页: 0 → +1
    val pageOffset = pagerState.currentPageOffsetFraction

    // ★ 级联因子: 越靠下的卡片交错延迟越大
    val stagger = cardIndex.toFloat() * STAGGER_STEP
    val cascade = 1f + stagger

    this.graphicsLayer {
        // settled 态零开销 — 直接跳过所有计算
        if (pageOffset == 0f) return@graphicsLayer

        // 水平视差: 顶部卡片跟随最紧, 底部卡片被"拖拽"得更远
        translationX = size.width * pageOffset * SWIPE_PARALLAX_X * cascade

        // 垂直微偏移: 增强波浪感 (顶部卡片几乎不动Y, 底部卡片略上下浮动)
        translationY = size.height * abs(pageOffset) * VERTICAL_WAVE * stagger *
            if (pageOffset > 0) 1f else -1f

        // 缩放: 滑动中轻微缩小, 底部卡片缩小更多 (透视深度感)
        val scaleDecay = SCALE_DECAY_BASE * cascade
        scaleX = 1f - abs(pageOffset) * scaleDecay
        scaleY = scaleX

        // 透明度: 滑动中轻微淡出, 底部卡片更透明 (景深层次感)
        alpha = (1f - abs(pageOffset) * ALPHA_DECAY_BASE * (1f + stagger * 0.4f))
            .coerceIn(0f, 1f)
    }
}

/**
 * 页面滑动交错变换 Provider — 包裹 HorizontalPager 以提供 PagerState 给子卡片
 *
 * 使用方式 (在 MainTabs 中):
 * ```
 * StaggeredPageProvider(pagerState = pagerState) {
 *     HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
 *         when (page) {
 *             0 -> DashboardScreen(onNavigate = navigate)
 *             ...
 *         }
 *     }
 * }
 * ```
 */
@Composable
fun StaggeredPageProvider(
    pagerState: PagerState,
    content: @Composable () -> Unit
) {
    val lazyPagerState = remember { lazy { pagerState } }
    CompositionLocalProvider(LocalPagerState provides lazyPagerState) {
        content()
    }
}
