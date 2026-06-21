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
        val recent = points.takeLast(takeCount)

        val safeMax = if (maxValue > 0 && maxValue.isFinite()) maxValue else 1f
        return recent.map { (it.value / safeMax).coerceIn(0f, 1f) }
    }

    /**
     * 信号强度专用归一化 (dBm → 0..1)
     * 信号值为负 (-59 dBm 至 -120 dBm), 进行线性映射
     */
    fun normalizeSignalStrength(points: List<HistoryDataPoint>?, takeCount: Int = 80): List<Float> {
        if (points.isNullOrEmpty()) return List(15) { 0f }
        return points.takeLast(takeCount).map { ((it.value + 130) / 100f).coerceIn(0f, 1f) }
    }

    // ═══════ refreshInterval label ═══════

    /**
     * 将毫秒刷新间隔格式化为人类可读标签
     * 示例: 500 → "0.5s", 2000 → "2s", 30000 → "30s"
     */
    fun formatIntervalMs(ms: Long): String = when {
        ms < 1000 -> "0.${ms / 100}s"
        ms < 60000 -> "${ms / 1000}s"
        else -> "${ms / 60000}min"
    }
}
