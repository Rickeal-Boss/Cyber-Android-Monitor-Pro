package com.rb.cybermonitorpro.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

object PollingFlow {

    fun launchModulePolling(
        tag: String,
        intervalFlow: Flow<Long>,
        scope: CoroutineScope,
        context: CoroutineContext = EmptyCoroutineContext,
        immediate: Boolean = true,
        fetcher: suspend () -> Unit,
    ): Job = scope.launch(context) {
        var isFirstOverallEmission = true
        intervalFlow.flatMapLatest { delayMs: Long ->
            flow<Unit> {
                while (isActive) {
                    if (isFirstOverallEmission && !immediate) {
                        if (delayMs > 0) delay(delayMs)
                    }
                    val start = System.currentTimeMillis()
                    try {
                        fetcher()
                    } catch (e: CancellationException) {
                        throw e // 官方铁律: 必须重抛, 否则破坏结构化并发的取消传播
                    } catch (_: Throwable) {
                        // 业务异常按原样吞掉, 不影响下一轮轮询
                    }
                    isFirstOverallEmission = false
                    val elapsed = System.currentTimeMillis() - start
                    val remaining = (delayMs - elapsed).coerceAtLeast(0L)
                    if (remaining > 0) delay(remaining)
                }
            }
        }.collect { }
    }
}
