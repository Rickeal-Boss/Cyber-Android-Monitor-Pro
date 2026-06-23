package com.example.deviceinfoviewer.data.repository

import com.example.deviceinfoviewer.data.model.HistoryDataPoint
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.*

/**
 * 历史数据缓存 — 协程驱动的自动裁剪
 *
 * 性能优化 (2026-06-19):
 * - 新增 getRecentSeries(name, maxPoints)：图表只需最近 N 点，避免全量拷贝
 *   （原 getSeries 返回 1 小时全量 ~1800 点，每轮 15 series × 1800 = 27000 点拷贝；
 *    图表实际只 takeLast(80)，getRecentSeries 直接返回 80 点快照，拷贝量降至 1200）
 * - getSeries 改用 ArrayList 拷贝（比 LinkedList 拷贝略快）
 *
 * 性能优化 (2026-06-21):
 * - ★ 数据点上限 300/系列：超出时移除最旧数据，防止长时间采集内存膨胀
 *   （原仅时间裁剪 1h，2s 间隔 = 1800 点；加数量上限 300  ≈ 10 分钟窗口 @2s）
 */
class HistoryCache {

    companion object {
        /**
         * 每系列最大数据点数量
         *
         * [Architect Note] 容量设计依据 (2026-06-23):
         *   刷新间隔 → 窗口时长:
         *     0.5s → 300 × 0.5 = 150s  (2.5 分钟, 快采样短窗口, 可接受)
         *     1.0s → 300 × 1.0 = 300s  (5 分钟, 合理平衡)
         *     2.0s → 300 × 2.0 = 600s  (10 分钟, 默认间隔, 良好历史回溯)
         *     5.0s → 300 × 5.0 = 1500s (25 分钟, 长趋势观察)
         *
         *   图表仅显示最近 80 点 (getRecentSeries(name, 80)),
         *   300 点缓冲区提供 2.5~25 分钟回溯窗口，足够用户拖动查看历史。
         *   15 个系列 × 300 点 × ~40 bytes/点 = ~180KB 内存占用，可忽略。
         *
         *   不改为 120: 120 @ 0.5s = 仅 60s 窗口，太短；
         *   120 @ 2s = 240s 可接受但与 300 差异不大，保留 300 更安全。
         */
        const val MAX_POINTS_PER_SERIES = 300
    }

    private val cache = ConcurrentHashMap<String, LinkedList<HistoryDataPoint>>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val maxAgeMs = 60 * 60 * 1000L // 1 小时

    init {
        scope.launch {
            while (isActive) {
                delay(60_000L)
                prune()
            }
        }
    }

    fun addPoint(seriesName: String, value: Float) {
        val point = HistoryDataPoint(System.currentTimeMillis(), value, seriesName)
        val series = cache.getOrPut(seriesName) { LinkedList() }
        // ★ 线程安全修复 (P0-3): add/remove 与 getSeries/prune 的 synchronized 对齐
        //   原方案 series.add/removeFirst 无同步, 与 getSeries 的 synchronized(series) 不一致,
        //   并发修改可能抛 ConcurrentModificationException 或数据错乱
        synchronized(series) {
            series.add(point)
            // ★ 数量上限裁剪 (2026-06-21): 防止长时间采集内存膨胀
            while (series.size > MAX_POINTS_PER_SERIES) {
                series.removeFirst()
            }
        }
    }

    /**
     * 返回完整序列快照（1 小时窗口）。仅用于需要全量历史的场景。
     */
    fun getSeries(seriesName: String): List<HistoryDataPoint> {
        val series = cache[seriesName] ?: return emptyList()
        return synchronized(series) { ArrayList(series) }
    }

    /**
     * ★ 性能优化：返回最近 [maxPoints] 个点的快照，避免全量拷贝。
     * 图表组件实际只显示最近 80 点，用此方法将每轮拷贝量从 ~1800 点降至 80 点。
     */
    fun getRecentSeries(seriesName: String, maxPoints: Int): List<HistoryDataPoint> {
        val series = cache[seriesName] ?: return emptyList()
        return synchronized(series) {
            val size = series.size
            if (size <= maxPoints) {
                ArrayList(series)
            } else {
                // subList 视图 + ArrayList 拷贝，只复制 maxPoints 个元素
                ArrayList(series.subList(size - maxPoints, size))
            }
        }
    }

    private fun prune() {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        for (series in cache.values) {
            synchronized(series) {
                series.removeAll { it.timestampMillis < cutoff }
            }
        }
    }

    fun clear() = cache.clear()

    /**
     * 清除所有传感器系列数据（切换传感器时调用）
     */
    fun clearSensorSeries() {
        val keysToRemove = cache.keys.filter { it.startsWith("sensor_") }
        keysToRemove.forEach { cache.remove(it) }
    }

    fun shutdown() = scope.cancel()
}
