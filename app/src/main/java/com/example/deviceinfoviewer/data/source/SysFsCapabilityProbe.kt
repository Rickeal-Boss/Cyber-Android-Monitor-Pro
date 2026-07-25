package com.example.deviceinfoviewer.data.source

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
 * 进程内单例缓存 + DataStore 持久化 (P2 原):
 *   - attach(): Application.onCreate 注入 DataStore 后端 (轻量, 不读盘)
 *   - preload(): 启动期异步协程把持久化结果读回进程内缓存, 跳过首个 tick 重探测
 *   - isReadable() 同步探测后 fire-and-forget 写回 DataStore (跨冷启动/跨进程存活)
 *   - isReadable() 本身保持【同步】, 不阻塞调用线程; DataStore 读只在 preload 异步发生
 */
internal object SysFsCapabilityProbe {
    private const val TAG = "SysFsCapabilityProbe"
    private const val PROBE_PATH = "/sys/class/power_supply/battery/current_now"

    @Volatile
    private var probed = false
    @Volatile
    private var readable: Boolean? = null

    // DataStore 后端 (跨进程/冷启动存活); 由 Application.onCreate.attach() 注入
    @Volatile
    private var dataStore: DataStore<Preferences>? = null
    private val persistScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Application.onCreate 调用一次: 注入 DataStore 后端 (仅取引用, 不读盘, 主线程安全) */
    fun attach(context: Context) {
        dataStore = context.applicationContext.batteryDataStore
    }

    /**
     * 启动期预载: 把已持久化的探测结果读回进程内缓存, 避免首个 tick 重新 open+read 探测。
     * 由 DeviceApplication.startupScope(Dispatchers.IO) 非阻塞调用。
     * 若 DataStore 尚未写入过(首次安装)则保持未探测, 交给首次 isReadable() 同步探测。
     */
    suspend fun preload() {
        val ds = dataStore ?: return
        try {
            val persisted = ds.data.first()[KEY_SYSFS_CURRENT_READABLE]
            if (persisted != null) {
                readable = persisted
                probed = true
                Log.d(TAG, "preload from DataStore -> readable=$persisted")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "preload failed, fall back to lazy probe", t)
        }
    }

    /** 首次调用触发探测, 之后直接返回缓存结果 (始终保持同步) */
    fun isReadable(): Boolean {
        if (probed) return readable == true
        synchronized(this) {
            if (probed) return readable == true
            readable = tryProbe()
            probed = true
            Log.d(TAG, "probe $PROBE_PATH -> readable=$readable")
            persist() // fire-and-forget 写回 DataStore (跨冷启动存活)
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

    /** 探测结果写回 DataStore (fire-and-forget, 不阻塞调用线程) */
    private fun persist() {
        val ds = dataStore ?: return
        val value = readable ?: return
        persistScope.launch {
            try {
                ds.edit { it[KEY_SYSFS_CURRENT_READABLE] = value }
            } catch (t: Throwable) {
                Log.w(TAG, "persist failed", t)
            }
        }
    }

    /** 仅测试/调试用: 重置探测状态 + 清空持久化结果 */
    fun reset() {
        probed = false
        readable = null
        val ds = dataStore
        if (ds != null) {
            persistScope.launch {
                try {
                    ds.edit { it.remove(KEY_SYSFS_CURRENT_READABLE) }
                } catch (_: Throwable) {
                }
            }
        }
    }
}
