package com.example.deviceinfoviewer.ui.components.charts

import com.example.deviceinfoviewer.data.model.HistoryDataPoint

/**
 * 图表数据规范化工具 — 共享 normalizeChartData 避免 6 份重复
 *
 * 原分布: BatteryScreen / CpuScreen / DashboardScreen / GpuScreen /
 *          MemoryScreen / NetworkScreen 各自定义相同逻辑
 */
object ChartUtils {

    /**
     * 将历史数据点归一化为 [0..1] 的浮点列表，供图表组件消费
     *
     * ★ 性能优化 (2026-06-21): 单次遍历替代 takeLast+map 两次列表分配
     *
     * @param points 原始历史数据 (可为 null)
     * @param maxValue 归一化上限 (>0 时启用缩放), 特殊值 NaN/Inf 按 1f 处理
     * @param takeCount 取最近多少个点, 默认 80
     * @param emptyFill 数据为空时填充的默认值, 默认 0f
     * @return 归一化后的 Float 列表, 数据为空时返回 15 个 emptyFill
     */
    fun normalizeChartData(
        points: List<HistoryDataPoint>?,
        maxValue: Float,
        takeCount: Int = 80,
        emptyFill: Float = 0f
    ): List<Float> {
        if (points.isNullOrEmpty()) return List(15) { emptyFill }

        val safeMax = if (maxValue > 0 && maxValue.isFinite()) maxValue else 1f
        val start = (points.size - takeCount).coerceAtLeast(0)
        val out = ArrayList<Float>(points.size - start)
        for (i in start until points.size) {
            val v = points[i].value
            val safeV = if (v.isNaN() || v.isInfinite()) 0f else v
            out.add((safeV / safeMax).coerceIn(0f, 1f))
        }
        return out
    }

    /**
     * 信号强度专用归一化 (dBm → 0..1)
     * ★ 映射: (dBm + SIGNAL_OFFSET) / SIGNAL_SCALE, 区间 [-130, -30] dBm → [0, 1]
     */
    fun normalizeSignalStrength(points: List<HistoryDataPoint>?, takeCount: Int = 80): List<Float> {
        if (points.isNullOrEmpty()) return List(15) { 0f }
        val start = (points.size - takeCount).coerceAtLeast(0)
        val out = ArrayList<Float>(points.size - start)
        for (i in start until points.size) {
            out.add(((points[i].value + 130) / 100f).coerceIn(0f, 1f))
        }
        return out
    }

    // ═══════ refreshInterval label ═══════

    /**
     * 将毫秒刷新间隔格式化为人类可读标签
     * 示例: 200 → "200ms", 500 → "500ms", 2000 → "2s", 30000 → "30s"
     */
    fun formatIntervalMs(ms: Long): String = when {
        ms < 1000 -> "${ms}ms"   // ★ 修复: 用 ms 避免 50→"0.0s" 和 999→"0.9s" 精度丢失
        ms < 60000 -> "${ms / 1000}s"
        else -> "${ms / 60000}min"
    }
}
