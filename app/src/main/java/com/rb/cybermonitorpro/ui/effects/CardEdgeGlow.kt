package com.rb.cybermonitorpro.ui.effects

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ═══════════════════════════════════════════════════════════════
//  固定软件背景光晕 — 全页面统一浮光效果 (一次性渲染)
//
//  视觉目标 (参考图): 柔和蓝紫渐变光晕从界面右下边缘向外扩散,
//  营造"卡片/内容浮在光上"的悬浮感。细腻不抢眼, 与阴影交织体现层次。
//
//  实现方式 (v2 — 固定背景):
//  - 旧版 (v1) 在每个卡片上挂 cardEdgeGlow() Modifier, 随卡片重组/滚动
//    反复重绘, 且有 12 处接入点难以统一。
//  - 新版改为 App 根层一次性渲染的全屏固定背景层: 只在根 Composable 渲染
//    一次 (drawBehind 绘制), 不随任何卡片、页面滚动或切 Tab 重绘 →
//    显著降 GPU 负载, 且所有页面 (含概览页/共享组件) 自动共享同一背景样式。
//
//  绘制内容: 3 层径向渐变 (右下主环境光 + 左上柔和补光 + 右下角边缘高光)
//  + 1 层屏幕四周非均匀描边光晕 (柔光边框, 右下最强、左上渐隐),
//  仅 GPU 绘制层操作, 零额外 Composable 开销, 零对象分配。
//
//  接入点: MainActivity.kt 的 SystemMonitorApp 根 Box 内, 作为首个子项
//  (位于 Surface 不透明底色之上、GlobalLightProvider/内容之下)。
// ═══════════════════════════════════════════════════════════════

/** 光晕主色 — 柔和天蓝 (介于参考图淡蓝与项目 NeonCyan 之间, 去饱和以显细腻) */
private val GLOW_COLOR = Color(0xFF5599CC)

/** 主环境光 alpha — 大范围极淡径向渐变, 营造整体浮光氛围 */
private const val AMBIENT_ALPHA_CENTER = 0.07f
private const val AMBIENT_ALPHA_MID    = 0.04f

/** 边缘高光 alpha — 较小范围稍亮, 集中于底部/右侧边角 */
private const val EDGE_ALPHA_CENTER = 0.11f
private const val EDGE_ALPHA_MID    = 0.045f

/** 主环境光半径系数 — 相对屏幕长边的倍数 (扩散范围, 越大越广) */
private const val AMBIENT_RADIUS_SCALE = 1.30f

/** 副环境光 (左上柔和补光) 半径系数 — 保证全屏浮光均匀 */
private const val AMBIENT_SECONDARY_RADIUS_SCALE = 1.10f

/** 边缘高光半径系数 — 较紧凑, 集中于边角 */
private const val EDGE_RADIUS_SCALE = 0.85f

/** 主环境光中心 — 界面右下 (0~1, 越大越靠右/下) */
private const val AMBIENT_CX = 0.85f
private const val AMBIENT_CY = 0.90f

/** 副环境光中心 — 界面左上 (柔和补光) */
private const val AMBIENT2_CX = 0.15f
private const val AMBIENT2_CY = 0.12f

/** 边缘高光中心 — 更贴右下角 */
private const val EDGE_CX = 0.92f
private const val EDGE_CY = 0.93f

/** 边缘描边光晕 — 屏幕四周柔光边框 (第 4 层, 非均匀) */
private const val RIM_INSET_DP = 3f        // 描边距屏幕边缘内缩
private const val RIM_CORNER_DP = 22f      // 描边圆角
private const val RIM_CORE_WIDTH_DP = 2.5f // 核心亮线宽度
private const val RIM_PEAK_ALPHA = 0.28f   // 右下柔光斑峰值亮度 (已调亮)
private const val RIM_BASE_ALPHA = 0.05f   // 其余边缘微弱底光 (已调亮, 保证整圈仍可见)

/**
 * 固定软件背景光晕 — App 根层一次性渲染的全屏浮光背景。
 *
 * 直接作为根 Box 的首个子 Composable 使用; 绘制于 drawBehind 域,
 * 仅在根组合时渲染一次, 后续卡片重组/页面滚动/切 Tab 均不触发重绘。
 *
 * 性能: 每次布局仅 3 次 drawCircle + 2 次 drawRoundRect, 且为静态绘制
 * (无动画/无 InfiniteTransition) → 保持"只渲染一次"零持续重绘开销。
 */
@Composable
fun AppGlowBackground() {
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                val w = size.width
                val h = size.height
                if (w <= 0f || h <= 0f) return@drawBehind
                val dimen = maxOf(w, h)

                // ── Layer 1: 主环境光 (右下, 较强) ──
                val ambientRadius = dimen * AMBIENT_RADIUS_SCALE
                val ambientCenter = Offset(w * AMBIENT_CX, h * AMBIENT_CY)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            GLOW_COLOR.copy(alpha = AMBIENT_ALPHA_CENTER),
                            GLOW_COLOR.copy(alpha = AMBIENT_ALPHA_MID),
                            Color.Transparent
                        ),
                        center = ambientCenter,
                        radius = ambientRadius
                    ),
                    radius = ambientRadius,
                    center = ambientCenter
                )

                // ── Layer 2: 副环境光 (左上, 柔和补光, 保证全屏浮光) ──
                val ambient2Radius = dimen * AMBIENT_SECONDARY_RADIUS_SCALE
                val ambient2Center = Offset(w * AMBIENT2_CX, h * AMBIENT2_CY)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            GLOW_COLOR.copy(alpha = AMBIENT_ALPHA_CENTER * 0.6f),
                            GLOW_COLOR.copy(alpha = AMBIENT_ALPHA_MID * 0.6f),
                            Color.Transparent
                        ),
                        center = ambient2Center,
                        radius = ambient2Radius
                    ),
                    radius = ambient2Radius,
                    center = ambient2Center
                )

                // ── Layer 3: 边缘高光 (右下角, 更亮更聚) ──
                val edgeRadius = dimen * EDGE_RADIUS_SCALE
                val edgeCenter = Offset(w * EDGE_CX, h * EDGE_CY)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            GLOW_COLOR.copy(alpha = EDGE_ALPHA_CENTER),
                            GLOW_COLOR.copy(alpha = EDGE_ALPHA_MID),
                            Color.Transparent
                        ),
                        center = edgeCenter,
                        radius = edgeRadius
                    ),
                    radius = edgeRadius,
                    center = edgeCenter
                )

                // ── Layer 4: 边缘描边光晕 (屏幕四周柔光边框, 非均匀) ──
                // 非均匀扫光渐变: 整圈微弱底光 (RIM_BASE_ALPHA) + 右下象限 2~3 处离散柔光斑
                // (buildRimBrush: 强度递减, 似跑马灯节点) → 右下最强、向左上渐隐, 强化"边缘"
                // 且非均匀, 静止、仍是柔光晕 (非硬线)。静态 → 保持"只渲染一次"零持续开销。
                val rimInset = RIM_INSET_DP * density
                val rimCorner = RIM_CORNER_DP * density
                val rimCoreW = RIM_CORE_WIDTH_DP * density
                val rimSize = Size(w - 2f * rimInset, h - 2f * rimInset)
                val rimTopLeft = Offset(rimInset, rimInset)
                val rimHalo = buildRimBrush(w, h, 0.5f)
                val rimCore = buildRimBrush(w, h, 1.0f)
                // 柔光晕 (宽, 低叠加) + 核心亮线 (窄) — 两层描边制造光晕扩散
                drawRoundRect(
                    brush = rimHalo,
                    topLeft = rimTopLeft,
                    size = rimSize,
                    cornerRadius = CornerRadius(rimCorner),
                    style = Stroke(width = rimCoreW * 3f)
                )
                drawRoundRect(
                    brush = rimCore,
                    topLeft = rimTopLeft,
                    size = rimSize,
                    cornerRadius = CornerRadius(rimCorner),
                    style = Stroke(width = rimCoreW)
                )
            }
    )
}

/** 构建边缘描边扫光渐变 — 右下象限 2~3 处离散柔光斑 (非均匀, 似跑马灯节点但静止柔光) */
private fun buildRimBrush(w: Float, h: Float, alphaScale: Float): Brush {
    val base = GLOW_COLOR.copy(alpha = RIM_BASE_ALPHA * alphaScale)
    val peak = RIM_PEAK_ALPHA * alphaScale
    // 三处柔光斑: (中心角度, 强度系数) — 集中于右下象限, 强度递减 → 非均匀
    val spots = listOf(
        0.07f to 0.60f,
        0.125f to 1.00f,
        0.19f to 0.70f
    )
    val stops = mutableListOf<Pair<Float, Color>>()
    stops.add(0.0f to base)
    for ((center, k) in spots) {
        val c = GLOW_COLOR.copy(alpha = peak * k)
        val half = GLOW_COLOR.copy(alpha = peak * k * 0.5f)
        stops.add((center - 0.020f) to base)
        stops.add((center - 0.010f) to half)
        stops.add(center to c)
        stops.add((center + 0.010f) to half)
        stops.add((center + 0.020f) to base)
    }
    stops.add(1.0f to base)
    return Brush.sweepGradient(*stops.toTypedArray(), center = Offset(w / 2f, h / 2f))
}

// ═══════════════════════════════════════════════════════════════
//  卡片微放大 + 霓虹光边框 — cardEnlargeBevel
//
//  作用: 在所有卡片四周外扩 BEVEL_ENLARGE 一圈"发光环带":
//  - 卡片本体(背景/内容)尺寸与原来完全一致 —— LayoutModifier 仅把"对外报告的尺寸"
//    向外扩 2*enlarge, 但用【原始约束】测量卡片并把内容内缩放置 (内容布局尺寸不变)
//    → 父布局自动为环带让出空间, 兄弟卡片被推开, 不会重叠。
//  - 环带内绘制: sweep 渐变霓虹光边框 (沿卡片外围一周的彩色发光描边),
//    颜色基于 GLOW_COLOR 的青蓝→蓝紫→亮青变化, 营造"浮起发光"的立体感。
//  性能: 单次 sweepGradient drawRoundRect 填充 + 单次高光线描边, 静态无动画。
// ═══════════════════════════════════════════════════════════════

/** 外扩环带宽度 (每侧) — 8dp 在典型 16dp 页面内边距内不会溢出屏幕 */
private val BEVEL_ENLARGE = 8.dp

/** 卡片圆角 — 须与所有卡片 RoundedCornerShape(12.dp) 保持一致 */
private val BEVEL_CARD_CORNER = 12.dp

/** 霓虹光边框基础 alpha */
private const val BEVEL_GLOW_ALPHA = 0.65f

/** 环带背景光晕 alpha (sweep 渐变底色, 降低以突出前景描边) */
private const val BEVEL_GLOW_BG_ALPHA = 0.20f

/** 内缘高光线 alpha (白色/浅色细线, 增强"玻璃边缘"浮起感) */
private const val BEVEL_HIGHLIGHT_ALPHA = 0.12f

/**
 * 环带渐变描边色组 — 左上(紫) → 右下(蓝) 线性渐变.
 * 契合赛博霓虹风格, 沿卡片四边形成连续的紫→蓝过渡.
 */
private val BEVEL_BORDER_COLORS = listOf(
    Color(0xFF9966DD),  // 霓虹紫 (左上)
    Color(0xFF5599CC),  // 赛博青蓝 (右下)
)

/** 动态边框色 — 高温黄 */
private val BORDER_WARN_YELLOW = Color(0xFFFFAA00)

/** 动态边框色 — 危险红 */
private val BORDER_DANGER_RED = Color(0xFFFF3344)

/**
 * 霓虹光边框渐变色组 (基于 GLOW_COLOR 0xFF5599CC 青蓝的变体).
 * 按 sweep 方向排列: 顶(青蓝) → 右(蓝紫) → 底(亮青) → 左(靛蓝) → 回到顶.
 * 5 色确保过渡平滑且首尾衔接.
 */
private val BEVEL_GLOW_COLORS = listOf(
    Color(0xFF5599CC),  // 青蓝 (顶) — GLOW_COLOR 本体
    Color(0xFF6655DD),  // 蓝紫 (右偏)
    Color(0xFF77BBEE),  // 亮青 (底偏)
    Color(0xFF4477BB),  // 靛蓝 (左偏)
    Color(0xFF5599CC),  // 回到青蓝 (闭合)
)

/**
 * 卡片微放大 + 霓虹光边框。
 *
 * 在卡片四周各外扩 [enlarge] 一圈环带; 卡片本体(背景/内容)尺寸保持不变,
 * 环带内绘制 sweep 渐变霓虹发光边框。所有接入点卡片统一生效。
 *
 * @param borderColor 动态边框色 (可选). 传入时覆盖默认紫→蓝渐变描边,
 *   用于语义化状态指示 (如温度过高→红/黄边). 传 null 使用默认渐变.
 */
fun Modifier.cardEnlargeBevel(
    enlarge: Dp = BEVEL_ENLARGE,
    borderColor: Color? = null
): Modifier = cardEnlargeBevel(horizontal = enlarge, vertical = enlarge, borderColor)

/**
 * 卡片微放大 + 霓虹光边框（非对称版）。
 *
 * 水平方向外扩 [horizontal], 垂直方向外扩 [vertical].
 * 适用场景: 全宽卡片在 LazyColumn 中滚动时, 水平方向收窄以避免光晕被父容器裁切,
 * 同时保持垂直方向的立体感.
 *
 * @param borderColor 动态边框色 (可选). 传入时覆盖默认紫→蓝渐变描边.
 */
fun Modifier.cardEnlargeBevel(
    horizontal: Dp = BEVEL_ENLARGE,
    vertical: Dp = BEVEL_ENLARGE,
    borderColor: Color? = null
): Modifier = this then CardEnlargeBevelElement(horizontal, vertical, borderColor)

private data class CardEnlargeBevelElement(
    val horizontal: Dp,
    val vertical: Dp,
    val borderColor: Color?
) : ModifierNodeElement<CardEnlargeBevelNode>() {
    override fun create() = CardEnlargeBevelNode(horizontal, vertical, borderColor)
    override fun update(node: CardEnlargeBevelNode) {
        node.horizontal = horizontal
        node.vertical = vertical
        node.borderColor = borderColor
    }
}

private class CardEnlargeBevelNode(
    var horizontal: Dp,
    var vertical: Dp,
    var borderColor: Color?
) : Modifier.Node(),
    LayoutModifierNode,
    DrawModifierNode {

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureResult {
        val hPx = horizontal.roundToPx()
        val vPx = vertical.roundToPx()
        // 子项(卡片)按【原始约束】测量 → 卡片本体尺寸不变 (内容不变大、不重叠)
        val placeable = measurable.measure(constraints)
        // 对外报告非对称外扩后的尺寸 → 父布局为环带让出空间
        return layout(placeable.width + 2 * hPx, placeable.height + 2 * vPx) {
            placeable.place(hPx, vPx)
        }
    }

    override fun ContentDrawScope.draw() {
        val hPx = horizontal.toPx()
        val vPx = vertical.toPx()
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) {
            drawContent()
            return
        }
        // 外圆角取较大值确保渐变覆盖完整
        val outerCorner = maxOf(
            (BEVEL_CARD_CORNER + horizontal).toPx(),
            (BEVEL_CARD_CORNER + vertical).toPx()
        )
        val innerCorner = BEVEL_CARD_CORNER.toPx()

        // ── 1) 环带背景光晕 (sweep 渐变, 降低 alpha 作为底色) ──
        drawRoundRect(
            brush = Brush.sweepGradient(
                colors = BEVEL_GLOW_COLORS.map { it.copy(alpha = BEVEL_GLOW_BG_ALPHA) }
            ),
            topLeft = Offset.Zero,
            size = Size(w, h),
            cornerRadius = CornerRadius(outerCorner)
        )

        // ── 2) 卡片本体 (含其 elevation 阴影/背景/内容) ──
        drawContent()

        // ── 3) 渐变描边 (环带中央, 紫→蓝 左上→右下, 或动态 borderColor) ──
        //    在内缘高光线与外层光晕之间绘制线性渐变描边,
        //    形成参考图红色区域所示的"霓虹边框管".
        if (hPx > 1f || vPx > 1f) {
            val midOffset = Offset(hPx / 2f, vPx / 2f)
            val midSize = Size(w - hPx, h - vPx)
            val midCorner = (innerCorner + outerCorner) / 2f
            val strokeWidth = minOf(hPx, vPx).coerceAtLeast(1.5f)

            val bc = borderColor
            val borderBrush = if (bc != null) {
                Brush.horizontalGradient(
                    listOf(bc.copy(alpha = 0.9f), bc.copy(alpha = 0.6f))
                )
            } else {
                Brush.linearGradient(
                    colors = BEVEL_BORDER_COLORS.map { it.copy(alpha = BEVEL_GLOW_ALPHA) },
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                )
            }

            drawRoundRect(
                brush = borderBrush,
                topLeft = midOffset,
                size = midSize,
                cornerRadius = CornerRadius(midCorner),
                style = Stroke(width = strokeWidth)
            )
        }

        // ── 4) 内缘高光线 (白色极细描边, 卡片边界处) ──
        drawRoundRect(
            color = Color.White.copy(alpha = BEVEL_HIGHLIGHT_ALPHA),
            topLeft = Offset(hPx, vPx),
            size = Size(w - 2f * hPx, h - 2f * vPx),
            cornerRadius = CornerRadius(innerCorner),
            style = Stroke(width = 1f)
        )
    }
}
