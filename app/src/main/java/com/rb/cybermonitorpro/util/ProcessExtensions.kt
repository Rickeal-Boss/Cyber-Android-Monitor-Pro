package com.rb.cybermonitorpro.util

import android.os.Build
import java.util.concurrent.TimeUnit

/**
 * 超时安全的 Process 等待。
 *
 * 背景：项目 minSdk=21 且未启用 coreLibraryDesugaring，
 * 故 [Process.waitFor] 的无参版本会**永久阻塞**（进程挂起即死锁/ANR），
 * 而带超时版本 [Process.waitFor] 仅 API 26+ 可用。
 * 这里用「后台线程 + join(timeout)」实现全 API 等级兼容的超时等待，
 * 超时后销毁进程，避免调用线程被无限期挂起。
 *
 * @param timeoutMs 超时毫秒，默认 15s
 * @return true=进程在超时前正常结束；false=超时（已尝试销毁）
 */
fun Process.waitForWithTimeout(timeoutMs: Long = 15_000L): Boolean {
    val waiter = Thread {
        try { waitFor() } catch (_: Throwable) { /* 进程已被销毁等 */ }
    }
    waiter.start()
    waiter.join(timeoutMs)
    return if (waiter.isAlive) {
        // 超时：强制结束进程并中断等待线程
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) destroyForcibly() else destroy()
        waiter.interrupt()
        false
    } else {
        true
    }
}
