package com.rb.cybermonitorpro.ui.nightlight

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 线程安全的 HDR 贴片注册表。
 *
 * Compose 元素经 onGloballyPositioned 上报窗口坐标贴片；HdrPatchSurfaceView 订阅 [flow]
 * 把最新快照推给 PatchRenderer。单 EGL 线程消费，避免多 SurfaceView 并发 HDR overlay 上限问题。
 *
 * 铁律：所有上报/注销走 [upsert]/[remove]/[clear]，绝不在渲染线程外直接改 rendered 列表。
 */
object HdrPatchRegistry {
    private val mutex = Any()
    private val map = LinkedHashMap<String, HdrPatch>()
    private val _flow = MutableStateFlow<List<HdrPatch>>(emptyList())
    val flow: StateFlow<List<HdrPatch>> = _flow.asStateFlow()

    fun upsert(p: HdrPatch) = synchronized(mutex) {
        map[p.id] = p
        _flow.value = map.values.toList()
    }

    fun remove(id: String) = synchronized(mutex) {
        if (map.remove(id) != null) _flow.value = map.values.toList()
    }

    fun clear() = synchronized(mutex) {
        if (map.isNotEmpty()) {
            map.clear()
            _flow.value = emptyList()
        }
    }

    /** 渲染线程读取用：返回当前快照副本。 */
    fun snapshot(): List<HdrPatch> = synchronized(mutex) { map.values.toList() }
}
