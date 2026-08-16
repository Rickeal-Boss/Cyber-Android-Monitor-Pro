package com.rb.cybermonitorpro.ui.nightlight

import android.os.Build
import android.view.Display
import kotlin.math.min

/**
 * 二阶段 HDR 能力探测（与 HdrTestSurfaceView 验证过的路径一致）。
 *
 * - predictedCapable：事前快速预测（SDK≥35 && display.isHdr()），可误判。
 * - isHdrLayerObserved：运行时权威判定（getHdrSdrRatio() > 1.01），必须用来确认真正点亮。
 *
 * 命名约定：真 HDR 全程 `TurboXdr` 前缀。
 */
object HdrCapabilityDetector {

    data class Capability(
        /** SDK≥35 且 display.isHdr() 预测可用（事前，可能误判） */
        val predictedCapable: Boolean,
        /** Display.isHdr()（API 34+） */
        val displayHdrSupported: Boolean,
        /** 设备理论最高 HDR/SDR 比值（getHighestHdrSdrRatio，API 36+） */
        val highestHdrSdrRatio: Float,
        /** 支持的 HDR 类型名列表 */
        val supportedFormats: List<String>,
    )

    fun read(display: Display?): Capability = runCatching {
        val supported = if (Build.VERSION.SDK_INT >= 34) runCatching { display?.isHdr == true }.getOrDefault(false) else false
        val highest = if (Build.VERSION.SDK_INT >= 36) {
            runCatching { display?.highestHdrSdrRatio ?: 1f }.getOrDefault(1f)
        } else 1f
        val formats = if (Build.VERSION.SDK_INT >= 34) {
            runCatching { display?.mode?.supportedHdrTypes?.map { hdrTypeToName(it) }.orEmpty() }.getOrDefault(emptyList())
        } else emptyList()
        Capability(
            predictedCapable = Build.VERSION.SDK_INT >= 35 && supported,
            displayHdrSupported = supported,
            highestHdrSdrRatio = highest,
            supportedFormats = formats,
        )
    }.getOrDefault(Capability(false, false, 1f, emptyList()))

    /**
     * 运行时权威：系统 HDR 图层是否真正激活（阈值 1.01，滤浮点噪声）。
     * 这是 QA 通过的唯一判据——仅"调用成功"不算（v4 R4）。
     */
    fun isHdrLayerObserved(display: Display?): Boolean {
        if (Build.VERSION.SDK_INT < 34 || display == null) return false
        val avail = runCatching { display.isHdrSdrRatioAvailable() }.getOrDefault(false)
        val ratio = runCatching { display.hdrSdrRatio }.getOrDefault(1f)
        return avail && ratio > 1.01f
    }

    /**
     * 计算 HDR 余量：开启时需要抬升 HBM，取设备最高比与保守上限（10×）的较小值，
     * 避免无意义高请求触发热/电限流；否则 1.0（明确无 HDR）。
     */
    fun computeHeadroom(display: Display?, enabled: Boolean): Float {
        if (Build.VERSION.SDK_INT < 35 || !enabled) return 1f
        val cap = read(display)
        return minOf(cap.highestHdrSdrRatio, 10f).coerceAtLeast(1.5f)
    }

    private fun hdrTypeToName(t: Int): String = when (t) {
        1 -> "Dolby Vision"; 2 -> "HDR10"; 3 -> "HLG"; 4 -> "HDR10+"; else -> "HDR($t)"
    }
}
