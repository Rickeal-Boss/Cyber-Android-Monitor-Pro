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
 */
class HistoryCache {

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
        cache.getOrPut(seriesName) { LinkedList() }.add(point)
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
