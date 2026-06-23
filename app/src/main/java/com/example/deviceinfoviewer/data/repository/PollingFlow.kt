package com.example.deviceinfoviewer.data.repository

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
                    try { fetcher() } catch (_: Throwable) {}
                    isFirstOverallEmission = false
                    val elapsed = System.currentTimeMillis() - start
                    val remaining = (delayMs - elapsed).coerceAtLeast(0L)
                    if (remaining > 0) delay(remaining)
                }
            }
        }.collect { }
    }
}
