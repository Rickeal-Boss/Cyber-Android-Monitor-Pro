package com.rb.cybermonitorpro.ui.effects

import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════════
//  卡片边缘静态光晕 — 全页面统一浮光效果
//
//  视觉目标 (参考图): 柔和蓝紫渐变光晕从卡片底部/右侧边缘向外扩散,
//  营造"卡片浮在光上"的悬浮感。细腻不抢眼, 与阴影交织体现层次。
//
//  实现方式: drawBehind 绘制双层径向渐变 (底层大范围环境光 + 上层边缘高光),
//  仅 GPU 绘制层操作, 不触发重组, 零额外 Composable 开销。
//
//  接入点: 所有公共卡片组件 (InfoCard / MetricCard / MemoryDistributionCard /
//  QuickLinkCard / SectionCard 等) 的 Card modifier 链中, 位于 fillMaxWidth 之后、
//  shadow/revealLight 之前 → 光晕作为最底层, 阴影叠加其上产生深度。
// ═══════════════════════════════════════════════════════════════

/** 光晕主色 — 柔和天蓝 (介于参考图淡蓝与项目 NeonCyan 之间, 去饱和以显细腻) */
private val GLOW_COLOR = Color(0xFF5599CC)

/** 底层环境光 — 大范围极淡径向渐变, 营造整体浮光氛围 */
private const val AMBIENT_ALPHA_CENTER = 0.06f
private const val AMBIENT_ALPHA_MID    = 0.025f

/** 上层边缘高光 — 较小范围稍亮, 集中于底部/右侧边缘 */
private const val EDGE_ALPHA_CENTER = 0.10f
private const val EDGE_ALPHA_MID    = 0.035f

/** 环境光中心偏移 — 相对卡片的右下位置 (0~1, 越大越靠右/下) */
private const val AMBIENT_CENTER_X_FRACTION = 0.82f
private const val AMBIENT_CENTER_Y_FRACTION = 0.88f

/** 边缘高光中心偏移 — 更贴近右下角 */
private const val EDGE_CENTER_X_FRACTION = 0.90f
private const val EDGE_CENTER_Y_FRACTION = 0.92f

/** 环境光半径系数 — 相对卡片长边的倍数 (越大=越扩散) */
private const val AMBIENT_RADIUS_SCALE = 0.80f

/** 边缘高光半径系数 — 更紧凑 */
private const val EDGE_RADIUS_SCALE = 0.52f

/**
 * 卡片边缘静态光晕 — drawBehind 双层径向渐变。
 *
 * 无参数调用即可使用; 所有可调常量在本文件顶部集中管理。
 * 性能: 每次 drawBehind 仅 2 次 drawCircle (Brush.radialGradient), 零对象分配。
 */
fun Modifier.cardEdgeGlow(): Modifier = composed {
    drawBehind {
        if (size.width <= 0f || size.height <= 0f) return@drawBehind

        val dimen = maxOf(size.width, size.height)

        // ── Layer 1: 底层环境光 (大范围, 极淡) ──
        val ambientRadius = dimen * AMBIENT_RADIUS_SCALE
        val ambientCenter = Offset(
            size.width * AMBIENT_CENTER_X_FRACTION,
            size.height * AMBIENT_CENTER_Y_FRACTION
        )
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

        // ── Layer 2: 上层边缘高光 (较小范围, 稍亮, 集中于边角) ──
        val edgeRadius = dimen * EDGE_RADIUS_SCALE
        val edgeCenter = Offset(
            size.width * EDGE_CENTER_X_FRACTION,
            size.height * EDGE_CENTER_Y_FRACTION
        )
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
    }
}
