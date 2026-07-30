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
 * 页面滑动交错变换 v3 — 多卡片左右平移甩尾(tail-whip) + 逐卡片独立弹簧
 *
 * 相比 v2 的关键变化 (丝滑度):
 *   v2: StaggeredPageProvider 算一次 spring → 解包成 Float 提供给子卡片
 *       → 整个页面每帧全量重组 (DeviceScreen 27 卡片的所有 Text/Row 每帧重算) → 卡顿
 *   v3: 仅提供 pagerState + page 给子卡片, 每张卡片在 staggeredSwipe 内部跑自己的
 *       animateFloatAsState → 动画只更新各自的 graphicsLayer 绘制层, 不触发卡片
 *       content 重组 → 滑动丝滑
 *
 * 核心机制:
 *   rawOffset = page - (currentPage + currentPageOffsetFraction)  // 每页自身居中偏移
 *   eff = spring(rawOffset) * cascade(cardIndex)                  // 逐卡片弹簧 + 级联
 *   translationX = width * eff * HORIZONTAL_PARALLAX              // ★ 水平甩尾主导
 *   cascade = (1 + cardIndex*STAGGER_STEP).coerceAtMost(MAX_CASCADE)  // 越靠下摆幅越大=鞭梢
 */

/** 页面 PagerState — 由 StaggeredPageProvider 提供 */
val LocalPagerState: ProvidableCompositionLocal<PagerState> =
    compositionLocalOf { error("LocalPagerState not provided — wrap content with StaggeredPageProvider") }

/** 当前页索引 — 由 StaggeredPageProvider 提供, 用于计算每页自身偏移 */
val LocalPageIndex: ProvidableCompositionLocal<Int> =
    compositionLocalOf { 0 }

// ═══════════════════════════════════════════════════════════════
//  可调参数 — 甩尾(tail-whip)动效: 多卡片左右平移 + 横向级联弯曲
// ═══════════════════════════════════════════════════════════════

/** 级联步进 — 每递增一张卡片增加的交错系数 (越大=越靠下的卡片摆幅越大=鞭梢) */
private const val STAGGER_STEP = 0.13f

/** cascade 上限 — 防止底部(鞭梢)卡片位移过大飞出屏幕 (DeviceScreen 27 卡时尤需) */
private const val MAX_CASCADE = 3.0f

/** ★ 水平甩尾主导强度 — 卡片左右平移位移倍率 (相对屏宽), 这是动效的主运动轴 */
private const val HORIZONTAL_PARALLAX = 0.20f

/** 极弱垂直余量 — 保持结构对称, 几乎不可见 (0=纯水平甩尾) */
private const val VERTICAL_WAVE = 0.0f

/** 缩放衰减基数 — 过程中卡片缩小幅度 (辅助景深) */
private const val SCALE_DECAY = 0.05f

/** 透明度衰减基数 — 过程中卡片淡出幅度 (辅助景深) */
private const val ALPHA_DECAY = 0.15f

/** 弹簧阻尼比 — 0.78 略欠阻尼 → 落定轻微回弹甩动, 同时比 0.72 更顺滑 */
private const val SPRING_DAMPING = 0.78f

/** 弹簧刚度 — 400 适中跟手, 不过硬也不过慵懒 */
private const val SPRING_STIFFNESS = 400f

// ═══════════════════════════════════════════════════════════════

/**
 * 卡片级联滑动 Modifier — 甩尾(tail-whip)风格
 *
 * 每张卡片独立运行弹簧动画:
 *   - 顶部卡片领动, 越靠下的卡片 [cascade] 越大 → 左右平移摆幅越大(鞭梢)
 *   - 多张卡片以不同水平位移率跟随 → 页面横向弯曲成"甩尾"形状
 *   - 动画仅更新 graphicsLayer 绘制层, 不重组卡片 content → 丝滑
 */
@Stable
fun Modifier.staggeredSwipe(cardIndex: Int): Modifier = composed {
    val pagerState = LocalPagerState.current
    val page = LocalPageIndex.current

    // 本页自身相对于屏幕中心的原始偏移 (pager 的 snapshot state, 自动观察)
    val rawOffset = page - (pagerState.currentPage + pagerState.currentPageOffsetFraction)

    // ★ 逐卡片独立弹簧 — 只驱动本卡片的 graphicsLayer, 不触发 content 重组
    val smoothed by animateFloatAsState(
        targetValue = rawOffset,
        animationSpec = spring(
            dampingRatio = SPRING_DAMPING,
            stiffness = SPRING_STIFFNESS
        ),
        label = "cardStagger"
    )

    // 级联因子: 序号越大(越靠下)摆幅越大 → 鞭梢效应 (tip of the whip)
    val stagger = cardIndex.toFloat() * STAGGER_STEP
    val cascade = (1f + stagger).coerceAtMost(MAX_CASCADE)

    this.graphicsLayer {
        // ★ 不做 early return — smoothed=0 时所有变换自动为 identity, 无突变/跳变

        val eff = smoothed * cascade // 每张卡片的实际有效偏移

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
 * 仅提供 [pagerState] 与 [page] 给子卡片, 由每张卡片的 [staggeredSwipe]
 * 自行计算偏移并运行独立弹簧 (避免整页重组, 保证丝滑)。
 */
@Composable
fun StaggeredPageProvider(
    pagerState: PagerState,
    page: Int,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalPagerState provides pagerState,
        LocalPageIndex provides page
    ) {
        content()
    }
}
