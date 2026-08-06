package com.rb.cybermonitorpro.ui.effects

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.pager.PagerState
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs

/**
 * 页面滑动交错变换 v5 — 父层单弹簧 + 卡片相位映射 + 速度接力
 *
 * 演进历史:
 *   v2: Provider 算一次 spring → 解包成 Float 下发 → 整页每帧全量重组 → 卡顿
 *   v3: 每张卡片各自跑 animateFloatAsState → 不重组但 DeviceScreen 27 卡 = 27 个并发弹簧
 *   v4: Provider 只跑 1 个 Animatable, 经 CompositionLocal<State<Float>> 下传;
 *       卡片只在 graphicsLayer 里读值做 cascade 相位映射, 绘制层失效, 不重组。
 *   v5 (2026-08-06): 修复"松手后卡片仍长时间漂移"的迟滞 —
 *       ① 速度接力: collectLatest 每帧取消 animateTo, 而 Animatable.endAnimation()
 *          会 velocityVector.reset() 把速度清零 (androidx Animatable.kt), 于是弹簧
 *          每帧都从 v=0 重启, 等效时间常数被拉长到 ~636ms。改为在 animateTo 的 block
 *          回调里逐帧缓存当前速度, 下一次 animateTo 以 initialVelocity 传回 —— 这正是
 *          Animatable KDoc 指定的动量续接做法。
 *       ② 双弹簧门控: 按 pagerState.isScrollInProgress 区分"跟手期"与"收尾期",
 *          跟手期高刚度临界阻尼硬跟随, 收尾期低刚度欠阻尼(0.92)做轻微弹性收敛。
 *
 * 视觉等价性:
 *   v3 所有卡片的 smoothed 目标值与弹簧参数本就相同 (rawOffset 一致),
 *   合并为 1 份共享后公式仍是 eff = smoothed * cascade(cardIndex),
 *   动画曲线 / 鞭梢幅度 / 缩放 / 透明度逐帧一致。
 *
 * 核心机制:
 *   rawOffset = page - (currentPage + currentPageOffsetFraction)  // 每页自身居中偏移
 *   eff = spring(rawOffset) * cascade(cardIndex)                  // 单弹簧 + 级联
 *   translationX = width * eff * HORIZONTAL_PARALLAX              // ★ 水平甩尾主导
 *   cascade = (1 + cardIndex*STAGGER_STEP).coerceAtMost(MAX_CASCADE)  // 越靠下摆幅越大=鞭梢
 */

/** 页面级平滑偏移 State — 由 StaggeredPageProvider 提供, 卡片在 graphicsLayer 中读取 */
val LocalStaggeredPageProgress: ProvidableCompositionLocal<State<Float>> =
    compositionLocalOf { error("LocalStaggeredPageProgress not provided — wrap content with StaggeredPageProvider") }

// ═══════════════════════════════════════════════════════════════
//  可调参数 — 与 v3 完全一致, 保证视觉无变化
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

// ── 弹簧参数 v5: 按 isScrollInProgress 分「跟手期 / 收尾期」两套 ──
//
// 稳态跟随误差(临界阻尼跟踪匀速目标) = 2v/ω, ω = √stiffness (Compose spring 单位质量)。
// 以「250ms 划过一页」即 v≈4 page/s 估算:
//   k=420  → ω=20.5 → 滞后 0.39 page ← v4 参数, 拖泥带水的主因之一
//   k=1000 → ω=31.6 → 滞后 0.25 page ← 仍保留鞭梢手感, 但明显收紧
// 收尾期收敛时间 ≈ 3/(ζω): k=600, ζ=0.92 → ω=24.5 → 约 133ms 完全静止。

/** 跟手期阻尼比 — 手指/惯性滑动进行中, 1.0 临界阻尼, 绝不过冲 */
private const val SCROLL_DAMPING = 1.0f

/** 跟手期刚度 — 1000 硬跟随 (v4 的 420 滞后 ~0.39 页, 是"迟滞"观感的来源) */
private const val SCROLL_STIFFNESS = 1000f

/**
 * 收尾期阻尼比 — 0.92 轻微欠阻尼, 过冲量 exp(-pi*z/sqrt(1-z*z)) 约 0.06%,
 * 肉眼几乎不可见但收敛更快; 且 graphicsLayer 侧已有 coerceIn(-1,1) 与 maxDx 双重限幅兜底。
 */
private const val SETTLE_DAMPING = 0.92f

/** 收尾期刚度 — 600, 约 133ms 内收敛静止 (v4 因速度归零实测漂移达 ~630ms) */
private const val SETTLE_STIFFNESS = 600f

/**
 * ★ SpringSpec 提到顶层复用 —— 滑动期每帧都要新起一次 animateTo,
 *   若在 lambda 里现 new spring() 则 120fps 下每秒多 120 次分配。
 */
private val SPEC_FOLLOW = spring<Float>(dampingRatio = SCROLL_DAMPING, stiffness = SCROLL_STIFFNESS)
private val SPEC_SETTLE = spring<Float>(dampingRatio = SETTLE_DAMPING, stiffness = SETTLE_STIFFNESS)

// ═══════════════════════════════════════════════════════════════

/**
 * 卡片级联滑动 Modifier — 甩尾(tail-whip)风格
 *
 * 只读取父层共享的弹簧状态做相位映射, 自身不运行任何动画:
 *   - 顶部卡片领动, 越靠下的卡片 [cascade] 越大 → 左右平移摆幅越大(鞭梢)
 *   - 动画仅更新 graphicsLayer 绘制层, 不重组卡片 content → 丝滑
 */
@Stable
fun Modifier.staggeredSwipe(cardIndex: Int): Modifier = composed {
    val progress = LocalStaggeredPageProgress.current

    // 级联因子只与 cardIndex 有关: 序号越大(越靠下)摆幅越大 → 鞭梢效应 (tip of the whip)
    val cascade = remember(cardIndex) {
        (1f + cardIndex * STAGGER_STEP).coerceAtMost(MAX_CASCADE)
    }

    this.graphicsLayer {
        // 在绘制阶段读状态 — 只失效 draw 层, 不触发组合
        // ★ 不做 early return — smoothed=0 时所有变换自动为 identity, 无突变/跳变;
        //   early return 会跳过赋值导致残留旧值, 回中时冻结在微小偏移上
        // ★ eff 先限幅再乘级联 — 原 rawOffset 边缘可达 ~2, cascade≤3 → eff≈6 飞出屏; 限到 [-1,1] 后最深卡 eff≤3
        val eff = progress.value.coerceIn(-1f, 1f) * cascade

        // ── ★ 主运动: 水平甩尾 — clamp 到半屏, 卡片始终"在手上" ──
        val maxDx = size.width * 0.5f
        translationX = (size.width * eff * HORIZONTAL_PARALLAX).coerceIn(-maxDx, maxDx)

        // ── 极弱垂直余量 (VERTICAL_WAVE=0, translationY 恒 0, 无需 clamp) ──
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
 * 每页只运行 1 个 Animatable 弹簧:
 *   - snapshotFlow 在协程里收集 pager offset, Provider 组合层不订阅 → 滑动期间零重组
 *   - 初始值用 snapshot{} 非观察读取当前偏移, 与 v3 animateFloatAsState 初始行为一致, 无跳变
 *   - 卡片经 [LocalStaggeredPageProgress] 读取共享 State, 各自做 cascade 相位映射
 */
@Composable
fun StaggeredPageProvider(
    pagerState: PagerState,
    page: Int,
    content: @Composable () -> Unit
) {
    // 初始 0f, 首帧后由 LaunchedEffect snapTo 当前偏移 — 组合期不读 pagerState,
    // 否则 Provider 会随滑动每帧重组 (快照读取 API 在 compose 中不可用, 用协程内读取替代)
    val animatable = remember(page) { Animatable(0f) }

    LaunchedEffect(pagerState, page) {
        // ★ 先 snapTo 当前偏移: 与 v3 animateFloatAsState 初始行为一致, 首次进入无跳变
        //   协程内读 pager state 不订阅组合, Provider 组合层零重组
        animatable.snapTo(page - (pagerState.currentPage + pagerState.currentPageOffsetFraction))

        // ★ v5 速度接力缓存 (2026-08-06)
        //   collectLatest 每来一个新目标就 cancel 上一个 animateTo; Animatable 在
        //   CancellationException 分支同样会走 endAnimation() → velocityVector.reset(),
        //   所以下一次 animateTo 的默认 initialVelocity(=velocity) 恒为 0 —— 弹簧被反复
        //   "掐死在起步阶段", 这才是松手后长时间漂移的真正来源。
        //   注: 不能改用 collect 顺序消费来"保留速度" —— collect 会等 animateTo 跑完才处理
        //   下一个 emission, 拖拽期间目标每 8ms 变一次, 会退化成台阶式跳动, 比现状更糟。
        //   正确做法(Animatable KDoc): 手动把上一段的末速度作为 initialVelocity 传回。
        var carriedVelocity = 0f

        // 目标值 + 是否处于滑动中 (含手指拖拽与松手后的 fling/snap) 一起收集,
        // snapshotFlow 自带相邻去重, 任一分量变化才发射。
        snapshotFlow {
            (page - (pagerState.currentPage + pagerState.currentPageOffsetFraction)) to
                pagerState.isScrollInProgress
        }.collectLatest { (target, scrolling) ->
            animatable.animateTo(
                targetValue = target,
                animationSpec = if (scrolling) SPEC_FOLLOW else SPEC_SETTLE,
                initialVelocity = carriedVelocity
            ) {
                // 逐帧回调: 必须在这里抓速度。animateTo 被取消时抛 CancellationException,
                // 调用点之后的代码不会执行, 且此时 velocity 已被 endAnimation() 清零。
                carriedVelocity = velocity
            }
            // 正常跑完(弹簧收敛)时速度本就趋于 0, 显式归零避免残留动量污染下一段
            carriedVelocity = 0f
        }
    }

    CompositionLocalProvider(
        LocalStaggeredPageProgress provides animatable.asState()
    ) {
        content()
    }
}
