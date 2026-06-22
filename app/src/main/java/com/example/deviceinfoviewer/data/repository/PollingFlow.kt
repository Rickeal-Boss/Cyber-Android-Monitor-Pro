package com.example.deviceinfoviewer.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * 轮询 Flow 工具 — 参考竞品 pollingFlow + flatMapLatest 模式
 *
 * 核心思路 (竞品启发，非抄袭):
 * - 每个模块监听自己的刷新间隔 (StateFlow<Long>)，间隔变更时 flatMapLatest 自动销毁旧流、
 *   创建新流，实现零重启的热切换配置。竞品用 DataStore Flow，我们用 AppSettings + StateFlow。
 * - 竞品的 pollingFlow 本质是 while(true) → emit(fetcher) → delay，直接内联为独立协程更简洁。
 * - 竞品用 shareIn(replay=1) 保证新订阅者立即拿到最新值 — 我们用 LiveData.postValue 等价。
 */
object PollingFlow {

    /**
     * 独立模块轮询协程 — 替代单一大循环 collectData()
     *
     * 当 [intervalFlow] 发射新间隔值时，自动取消旧轮询并以新间隔重启。
     * 竞品用 settingsRepository.xxxRefreshDelay.flatMapLatest { pollingFlow(...) }，
     * 我们等价为 launch + flatMapLatest 内部化。
     *
     * @param tag 日志标签
     * @param intervalFlow 刷新间隔 Flow (ms)，变更时自动重启
     * @param scope 协程作用域
     * @param context 协程上下文 (默认 Dispatchers.Default)
     * @param immediate 是否立即采集第一帧 (默认 true，消除启动空白)
     * @param fetcher 数据采集函数
     */
    fun launchModulePolling(
        tag: String,
        intervalFlow: Flow<Long>,
        scope: CoroutineScope,
        context: CoroutineContext = EmptyCoroutineContext,
        immediate: Boolean = true,
        fetcher: suspend () -> Unit,
    ): Job = scope.launch(context) {
        intervalFlow.flatMapLatest { delayMs: Long ->
            flow<Unit> {
                if (immediate) fetcher()  // 启动时立即采第一帧
                while (isActive) {
                    val start = System.currentTimeMillis()
                    try { fetcher() } catch (_: Throwable) {}
                    val elapsed = System.currentTimeMillis() - start
                    val remaining = delayMs - elapsed
                    if (remaining > 0) delay(remaining)
                }
            }
        }.collect { }
    }

    /**
     * 将 StateFlow<Long> 包装为 config Flow，
     * 确保仅在值实际变化时发射 (distinctUntilChanged 内建在 StateFlow 中)
     */
    fun Flow<Long>.distinctMs(): Flow<Long> = this  // StateFlow 已自带 distinctUntilChanged

    /**
     * CallbackFlow 包装 — 将一次性回调转为 Flow，适合 GPS/传感器等 on-demand 数据
     */
    fun <T> callbackToFlow(block: ((T) -> Unit) -> Unit): Flow<T> = callbackFlow {
        block { trySend(it) }
        awaitClose {}
    }
}
