package com.rb.cybermonitorpro.data.source

import android.util.Log
import com.rb.cybermonitorpro.util.waitForWithTimeout
import java.io.File

/**
 * root 能力判定闸门 (将"真 root 私有节点深读"收口到 root)。
 *
 * 背景: 三方 App 在 ColorOS 等 OEM 下, 私有节点 (如 /sys/class/oplus_chg/...) 被
 *   SELinux (untrusted_app 域) 拦截, 无法读取; 而 `dumpsys battery` / `dumpsys batterystats`
 *   属于**普通三方 App 即可执行**的命令 (不需要 root), 已在 getCurrentNowFull ④ 段作为
 *   非 root 兜底保留。
 *
 * 本 RootGate 仅用于判定"真 root 才可读的特权来源" (如 magisk/root 环境下的私有 sysfs
 * 节点), 当前电流链路中尚无调用点 —— 保留为扩展位, 待接入 root 专属读数时启用。
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
