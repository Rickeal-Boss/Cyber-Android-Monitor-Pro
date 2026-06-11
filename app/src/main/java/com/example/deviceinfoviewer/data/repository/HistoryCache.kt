package com.example.deviceinfoviewer.data.repository

import com.example.deviceinfoviewer.data.model.HistoryDataPoint
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.*

/**
 * 历史数据缓存 — 协程驱动的自动裁剪
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

    fun getSeries(seriesName: String): List<HistoryDataPoint> {
        val series = cache[seriesName] ?: return emptyList()
        return synchronized(series) { LinkedList(series) }
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
