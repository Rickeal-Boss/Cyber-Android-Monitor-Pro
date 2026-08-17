package com.rb.cybermonitorpro.ui.nightlight

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.runtime.mutableStateOf

/**
 * HDR 贴片类型（行业首创：把 UI 元素"本体"画进 PQ surface，而非盖光晕）。
 *
 * - CARD_BORDER    : 卡片渐变描边（SDF 圆角描边）
 * - TAB_INDICATOR  : 顶部 Tab 选中指示条（实心亮条）
 * - TEXT_GLYPH     : 大数字字形本体（纹理采样掩码）
 * - CHART_LINE     : 折线本体（三角带粗线 + 尾点）
 * - CHART_GRID     : 图表网格线（1px 横线）
 */
enum class HdrPatchType { CARD_BORDER, TAB_INDICATOR, TEXT_GLYPH, CHART_LINE, CHART_GRID }

/**
 * 运行时真 HDR 点亮真相桥。
 *
 * - [pqActive] : PQ surface 真正拿到 10-bit config 且 EGL 注入 PQ 成功（由 PatchRenderer 首帧回调写入主线程）。
 * - [ratioOk]  : Display.getHdrSdrRatio() > 1.01（API 34+，权威确认真正点亮）。
 *
 * Compose 侧只读取它做可选抑制/诊断；本实现采用"叠加"策略（HDR 浮层在 SDR 之上增亮），
 * 即使 pqActive=false 也绝不隐藏任何 SDR 元素，确保 HDR 设备上 UI 元素永不消失。
 */
object HdrOverlayState {
    val pqActive = mutableStateOf(false)
    val ratioOk = mutableStateOf(false)
}

/**
 * 单个 HDR 贴片描述符。
 *
 * @param bounds  窗口坐标像素（onGloballyPositioned → positionInWindow）。surface 全屏覆盖根 Box，
 *                 故窗口坐标 == surface 像素坐标。
 * @param color0/color1 线性光经 ST 2084 PQ OETF 编码后的码值（0..1，>~0.02 即超 SDR 白场）。
 *                 单色时 color1 缺省等于 color0。由 [encodePq] 在 Compose 侧预先编码。
 * @param intensity 亮度倍率（相对 SDR 白），仅用于诊断/微调。
 */
data class HdrPatch(
    val id: String,
    val type: HdrPatchType,
    val bounds: RectF,
    val color0: FloatArray,
    val color1: FloatArray = color0,
    val intensity: Float = 1f,
    val cornerRadiusPx: Float = 0f,
    val strokeWidthPx: Float = 0f,
    val text: String? = null,
    val textSizePx: Float = 0f,
    val textBold: Boolean = true,
    val textMonospace: Boolean = true,
    val letterSpacingEm: Float = 0f,
    /**
     * 预栅格化位图（文字/图标本体掩码：白色=不透明，背景透明）。非空时渲染器直接上传该位图，
     * 由 Compose 侧用 TextMeasurer / Drawable 精确栅格化，保证与 SDR 布局像素级对齐；
     * 否则回退到按 [text] 现场生成字形纹理。位图随文本/图标变化而重建。
     */
    val bitmap: Bitmap? = null,
    /** CHART_LINE/CHART_GRID：窗口坐标点 [x0,y0,x1,y1,...] */
    val points: FloatArray? = null,
    val tailDotRadiusPx: Float = 0f,
    val visible: Boolean = true,
    /**
     * 是否位于顶部 Tab 区。true 时两段式渲染中绕过"顶撞裁剪"（scissor），
     * 用于 TAB_INDICATOR / 选中 Tab 图标光环 / 选中 Tab 标签字形；其余内容贴片默认裁剪。
     */
    val topZone: Boolean = false
) {
    // 注册表按 id 键控、整体替换，故 equals/hashCode 仅依赖引用/id，避免 FloatArray 内容参与比对。
    override fun equals(other: Any?) = this === other
    override fun hashCode() = id.hashCode()
}
