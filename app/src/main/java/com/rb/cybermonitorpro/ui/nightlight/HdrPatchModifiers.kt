package com.rb.cybermonitorpro.ui.nightlight

import android.content.res.Resources
import android.graphics.RectF
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement

/**
 * 局部 HDR 增亮的 Compose API — 通过 Modifier / Composable 把 UI 元素注册为 HDR 贴片。
 *
 * 使用方式：
 * ```kotlin
 * // 1. 卡片描边辉光
 * Card(Modifier.hdrRectGlowPatch(color = NeonPurple, intensity = 0.5f)) { ... }
 *
 * // 2. 温度数字 bloom（只画光晕不画字形）
 * HdrTextBloom(text = "58.7°C", color = Color(0xFFA855F7), peakNits = 1200f) {
 *     Text("58.7°C", ...)  // SDR 层正常渲染，HDR 浮层叠加辉光
 * }
 *
 * // 3. 折线图高亮
 * Canvas(Modifier.hdrLineGlowPatch(color = Cyan, peakNits = 1500f)) { ... }
 *
 * // 4. Tab 选中指示器
 * Tab(Modifier.hdrTabIndicatorPatch(color = NeonPurpleBright)) { ... }
 * ```
 *
 * 坐标转换：所有 API 内部通过 `onGloballyPositioned` 获取 `positionInWindow()`，
 * 自动转换为窗口坐标（与 GLSurfaceView viewport 一致），滚动时自动更新 → **零抖动**。
 *
 * 实现说明：RECT_GLOW / LINE_GLOW / TAB_INDICATOR 走 Modifier.Node（无重组开销）；
 * TEXT_BLOOM 因需包裹 content 用 @Composable Box + LaunchedEffect 实现。
 */

// dp → px 转换（系统密度，全 App 一致）
private fun dpToPx(dp: Float): Float = dp * Resources.getSystem().displayMetrics.density

// ════════════════════════════════════════════
// 1. RECT_GLOW — 矩形边框辉光（卡片描边等）
// ════════════════════════════════════════════

/**
 * 将此元素注册为 [PatchType.RECT_GLOW] 类型的 HDR 贴片。
 * 在元素周围绘制圆角矩形边框辉光（如卡片描边、选中框等）。
 */
fun Modifier.hdrRectGlowPatch(
    color: Color = Color(PatchType.RECT_GLOW.defaultColor),
    intensity: Float = 0.5f,
    peakNits: Float = PatchType.RECT_GLOW.peakNits,
    cornerRadiusDp: Float = 0f,
    borderWidthDp: Float = 0f,
    patchId: String? = null
): Modifier = this.then(
    HdrPatchElementModifier(
        patchType = PatchType.RECT_GLOW,
        color = color,
        intensity = intensity,
        peakNits = peakNits,
        paramsFactory = {
            PatchParams(
                borderWidthDp = if (borderWidthDp > 0f) dpToPx(borderWidthDp) else 0f,
                cornerRadiusPx = if (cornerRadiusDp > 0f) dpToPx(cornerRadiusDp) else 0f
            )
        },
        patchId = patchId
    )
)

// ════════════════════════════════════════════
// 2. TEXT_BLOOM — 文字区域高斯辉光
// ════════════════════════════════════════════

/**
 * 为包裹的文字内容添加 HDR bloom 辉光效果。
 *
 * **核心设计**：只画光晕不画字形。文字本身由 Compose SDR 层保持锐利渲染，
 * HDR 浮层只在文字周围绘制柔和的高斯扩散辉光，避免模糊影响可读性。
 */
@Composable
fun HdrTextBloom(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color(PatchType.TEXT_BLOOM.defaultColor),
    intensity: Float = 0.6f,
    peakNits: Float = PatchType.TEXT_BLOOM.peakNits,
    bloomRadiusFactor: Float = 1.5f,
    patchId: String? = null,
    content: @Composable () -> Unit
) {
    val id = remember(text, patchId) { patchId ?: "text_bloom_${text.hashCode()}" }
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    LaunchedEffect(id, coords) {
        val c = coords ?: return@LaunchedEffect
        val pos = c.positionInWindow()
        val size = c.size
        if (size.width <= 0 || size.height <= 0) return@LaunchedEffect
        val rect = RectF(pos.x, pos.y, pos.x + size.width, pos.y + size.height)
        val patch = HdrPatch(
            id = id,
            type = PatchType.TEXT_BLOOM,
            rect = rect,
            color = color,
            intensity = intensity,
            peakNits = peakNits,
            params = PatchParams(bloomRadiusFactor = bloomRadiusFactor, textLabel = text)
        )
        HdrPatchRegistry.register(patch)
    }

    DisposableEffect(id) {
        onDispose { HdrPatchRegistry.unregister(id) }
    }

    Box(
        modifier = modifier.onGloballyPositioned { coords = it },
        content = content
    )
}

// ════════════════════════════════════════════
// 3. LINE_GLOW — 折线图高亮
// ════════════════════════════════════════════

/**
 * 将此元素注册为 [PatchType.LINE_GLOW] 类型的 HDR 贴片。
 * 用于温度曲线、频率曲线等折线图区域的 HDR 高亮（shader 内绘水平辉光带）。
 *
 * 注意：SDR 原体折线应降低 alpha（避免与 HDR 辉光重影过曝），建议配合 0.3f 透明度。
 */
fun Modifier.hdrLineGlowPatch(
    color: Color = Color(PatchType.LINE_GLOW.defaultColor),
    intensity: Float = 0.5f,
    peakNits: Float = PatchType.LINE_GLOW.peakNits,
    lineWidthDp: Float = 2f,
    patchId: String? = null
): Modifier = this.then(
    HdrPatchElementModifier(
        patchType = PatchType.LINE_GLOW,
        color = color,
        intensity = intensity,
        peakNits = peakNits,
        paramsFactory = {
            PatchParams(borderWidthDp = if (lineWidthDp > 0f) dpToPx(lineWidthDp) else 0f)
        },
        patchId = patchId
    )
)

// ════════════════════════════════════════════
// 4. TAB_INDICATOR — Tab 选中指示器
// ════════════════════════════════════════════

/**
 * 将此 Tab 元素注册为 [PatchType.TAB_INDICATOR] 类型的 HDR 贴片。
 * 绘制底部高亮条 + 微弱背景辉光，表示当前选中的 Tab 页。
 */
fun Modifier.hdrTabIndicatorPatch(
    color: Color = Color(PatchType.TAB_INDICATOR.defaultColor),
    intensity: Float = 0.4f,
    peakNits: Float = PatchType.TAB_INDICATOR.peakNits,
    patchId: String? = null
): Modifier = this.then(
    HdrPatchElementModifier(
        patchType = PatchType.TAB_INDICATOR,
        color = color,
        intensity = intensity,
        peakNits = peakNits,
        paramsFactory = { PatchParams() },
        patchId = patchId
    )
)

// ════════════════════════════════════════════
// 内部实现：通用贴片 Modifier.Node
// ════════════════════════════════════════════

private data class HdrPatchElementModifier(
    private val patchType: PatchType,
    private val color: Color,
    private val intensity: Float,
    private val peakNits: Float,
    private val paramsFactory: () -> PatchParams,
    private val patchId: String? = null
) : ModifierNodeElement<HdrPatchModifierNode>() {

    override fun create(): HdrPatchModifierNode =
        HdrPatchModifierNode(
            patchType = patchType,
            color = color,
            intensity = intensity,
            peakNits = peakNits,
            paramsFactory = paramsFactory,
            patchId = patchId
        )

    override fun update(node: HdrPatchModifierNode) {
        node.patchType = patchType
        node.color = color
        node.intensity = intensity
        node.peakNits = peakNits
        node.paramsFactory = paramsFactory
        node.patchId = patchId
        node.refresh() // 参数变化（颜色/强度/ID）立即重注册，无需等待下次布局
    }
}

private class HdrPatchModifierNode(
    var patchType: PatchType,
    var color: Color,
    var intensity: Float,
    var peakNits: Float,
    var paramsFactory: () -> PatchParams,
    var patchId: String?
) : Modifier.Node(), GlobalPositionAwareModifierNode {

    private var resolvedId: String = ""
    private var lastCoordinates: LayoutCoordinates? = null

    override fun onAttach() {
        resolvedId = patchId ?: "${patchType.name}_${hashCode()}"
        super.onAttach()
    }

    override fun onDetach() {
        if (resolvedId.isNotEmpty()) runCatching { HdrPatchRegistry.unregister(resolvedId) }
        super.onDetach()
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        lastCoordinates = coordinates
        registerNow()
    }

    /** 用最近一次布局坐标注册/更新贴片（滚动或参数变化时调用）。 */
    private fun registerNow() {
        val c = lastCoordinates ?: return
        val pos = c.positionInWindow()
        val size = c.size
        if (size.width <= 0 || size.height <= 0) return

        val rect = RectF(pos.x, pos.y, pos.x + size.width, pos.y + size.height)
        val patch = HdrPatch(
            id = resolvedId,
            type = patchType,
            rect = rect,
            color = color,
            intensity = intensity,
            peakNits = peakNits,
            params = paramsFactory()
        )
        HdrPatchRegistry.register(patch)
    }

    /** update() 调用：若 ID 变了先注销旧 ID，再按当前参数重注册。 */
    fun refresh() {
        val newId = patchId ?: "${patchType.name}_${hashCode()}"
        if (resolvedId.isNotEmpty() && resolvedId != newId) {
            runCatching { HdrPatchRegistry.unregister(resolvedId) }
        }
        resolvedId = newId
        registerNow()
    }
}
