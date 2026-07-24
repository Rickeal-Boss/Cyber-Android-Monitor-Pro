package com.example.deviceinfoviewer.data.source

import android.util.Log
import java.io.File

/**
 * 一次性 sysfs 电流节点可读性探测 + 进程内缓存 (消除 ColorOS cat 风暴)。
 *
 * 背景: 三方 App 在 ColorOS(SELinux untrusted_app 域)下读
 *   /sys/class/power_supply/battery/current_now 会被 EACCES 拦截,
 *   SysFsReader.readLong 吞异常返回 -1, 导致 getCurrentNowFull 的 25 路径循环每 tick
 *   spawn ~150 次 cat(每路径 6 变体兜底), 形成"cat 风暴", 拖慢 UI 并浪费 CPU。
 *
 * 本探针在【首次】调用时对 current_now 做一次直接 open + read:
 *   - 可读(无 IOException) → cached = true, 后续走完整 sysfs 链
 *   - 不可读(EACCES / IOException / 文件不存在) → cached = false, 此后 sysfs 链永久关闭
 *     (ColorOS 每 tick cat 从 ~150 → 0)
 *
 * 进程内单例缓存; 持久化到 DataStore 跨进程存活见 P2。
 */
internal object SysFsCapabilityProbe {
    private const val TAG = "SysFsCapabilityProbe"
    private const val PROBE_PATH = "/sys/class/power_supply/battery/current_now"

    @Volatile
    private var probed = false
    @Volatile
    private var readable: Boolean? = null

    /** 首次调用触发探测, 之后直接返回缓存结果 */
    fun isReadable(): Boolean {
        if (probed) return readable == true
        synchronized(this) {
            if (probed) return readable == true
            readable = tryProbe()
            probed = true
            Log.d(TAG, "probe $PROBE_PATH -> readable=$readable")
            return readable == true
        }
    }

    private fun tryProbe(): Boolean {
        val f = File(PROBE_PATH)
        if (!f.exists()) return false
        return try {
            // 仅验证"能否打开并读一行", 不关心内容(避免 -1 哨兵歧义)
            f.inputStream().bufferedReader().use { it.readLine() }
            true
        } catch (_: Throwable) {
            false
        }
    }

    /** 仅测试/调试用: 重置探测状态 */
    fun reset() {
        probed = false
        readable = null
    }
}
