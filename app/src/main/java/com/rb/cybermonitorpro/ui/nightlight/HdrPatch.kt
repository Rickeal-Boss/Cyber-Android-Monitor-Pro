package com.rb.cybermonitorpro.ui.nightlight

import android.graphics.RectF
import androidx.compose.ui.graphics.Color

/**
 * HDR 局部增亮贴片描述符 — Compose 元素通过 `onGloballyPositioned` 上报给 [HdrPatchRegistry]，
 * 由 [PatchRenderer] 在 PQ 浮层对应窗口坐标绘制 HDR 辉光。
 *
 * 设计原则：
 * - **字只画辉光不画字形**：TEXT_BLOOM 类型只在文字周围画 bloom/glow，字形本身由
 *   Compose SDR 层保持锐利渲染，避免 HDR 模糊文字影响可读性。
 * - **折线本体走 HDR**：LINE_GLOW 类型的 SDR 原体应降透明度（避免重影过曝），
 *   HDR 浮层绘制带 PQ OETF 编码的高亮折线。
 * - **峰值亮度建议**：各类型有不同 peakNits 建议（600–2000 nits），
 *   shader 内用 ST 2084 PQ OETF 把"尼特"编成 PQ 码值。
 *
 * 坐标系：所有 RectF 为**窗口坐标**（window coordinate），由 `onGloballyPositioned`
 * 的 `LayoutCoordinates.positionInWindow()` 转换而来，与 GLSurfaceView 的 viewport 一致。
 */
data class HdrPatch(

    /** 贴片唯一 ID（用于 register/update/unregister 匹配） */
    val id: String,

    /** 贴片类型，决定 shader 渲染策略 */
    val type: PatchType,

    /** 窗口坐标矩形（left/top/right/bottom，像素单位） */
    val rect: RectF,

    /** 主题色（Compose Color，运行时转 RGB 归一化送 shader） */
    val color: Color,

    /**
     * 强度系数 ∈ [0, 1]，由用户「局部 HDR 增亮」滑块控制基础强度，
     * 各贴片可在此基础上微调（如温度数字比卡片边框更亮）。
     */
    val intensity: Float = 0.6f,

    /**
     * 峰值亮度（尼特），shader 内经 ST 2084 PQ OETF 编码为 PQ 码值。
     * 各类型建议值见 [PatchType.peakNits]。
     */
    val peakNits: Float = 1000f,

    /** 类型特定参数 */
    val params: PatchParams = PatchParams()
)

/**
 * 四类 HDR 贴片类型 — 每种对应不同的 shader 渲染路径。
 */
enum class PatchType(
    /** 该类型的默认峰值亮度建议（尼特） */
    val peakNits: Float,
    /** 默认颜色（Neon 风格，与 app 整体视觉一致；@ColorInt Int，供 Compose Color() 直接消费） */
    val defaultColor: Int
) {
    /** 卡片描边 / Tab 选中框等矩形辉光（细边框发光效果） */
    RECT_GLOW(800f, 0xFF7C3AED.toInt()),

    /** 文字/数字周围的 bloom 辉光（如 58.7°C、61%）— 只画光晕不画字形 */
    TEXT_BLOOM(1200f, 0xFFA855F7.toInt()),

    /** 折线图 / 图表线的高亮（温度曲线、频率曲线等） */
    LINE_GLOW(1500f, 0xFF06B6D4.toInt()),

    /** 顶部 Tab 选中指示器（底部高亮条 + 微弱背景辉光） */
    TAB_INDICATOR(600f, 0xFF8B5CF6.toInt())
}

/**
 * 类型特定参数 — 扩展字段，避免 HdrPatch 本身过于臃肿。
 */
data class PatchParams(
    /** LINE_GLOW：折线点序列（窗口坐标，至少 2 点） */
    val linePoints: List<FloatArray> = emptyList(),

    /** RECT_GLOW / TAB_INDICATOR：边框宽度（像素），0=自动（取 rect 短边的 2%） */
    val borderWidthDp: Float = 0f,

    /** TEXT_BLOOM：辉光扩散半径系数（相对于 rect 高度），1.0=与文字等高 */
    val bloomRadiusFactor: Float = 1.5f,

    /** 通用：圆角半径（像素），0=自动 */
    val cornerRadiusPx: Float = 0f,

    /** TEXT_BLOOM：文字内容（可选，用于诊断/QA 显示） */
    val textLabel: String = ""
)
