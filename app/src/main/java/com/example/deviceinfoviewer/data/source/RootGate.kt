package com.example.deviceinfoviewer.data.source

import android.util.Log
import com.example.deviceinfoviewer.util.waitForWithTimeout
import java.io.File

/**
 * root 能力判定闸门 (将"深读"收口到 root)。
 *
 * 背景: 原 getCurrentNowFull 在每 tick 无条件执行 `dumpsys battery` 与
 *   `dumpsys batterystats` (~2 子进程/tick), 在非 root 三方 App 下既拿不到特权节点又
 *   白白浪费子进程。此处将私有节点 / dumpsys 深读收口到 root 判定, 非 root 直接跳过,
 *   避免无谓子进程 (仅 root 设备才值得尝试特权节点读取)。
 */
internal object RootGate {
    private const val TAG = "RootGate"

    @Volatile
    private var checked = false
    @Volatile
    private var isRoot: Boolean? = null

    fun isRoot(): Boolean {
        if (checked) return isRoot == true
        synchronized(this) {
            if (checked) return isRoot == true
            isRoot = detectRoot()
            checked = true
            Log.d(TAG, "root detection -> $isRoot")
            return isRoot == true
        }
    }

    private fun detectRoot(): Boolean {
        // 1) 常见 su 二进制
        for (p in listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su")) {
            if (File(p).exists()) return true
        }
        // 2) 尝试 su -c id (带超时)
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("/system/bin/sh", "-c", "su -c id 2>/dev/null"))
            val out = proc.inputStream.bufferedReader().readText()
            proc.waitForWithTimeout()
            out.contains("uid=0")
        } catch (_: Throwable) {
            false
        }
    }

    /** 仅测试/调试用: 重置判定状态 */
    fun reset() {
        checked = false
        isRoot = null
    }
}
