package com.rb.cybermonitorpro.ui.nightlight

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    /**
     * 顶部 Tab 区底部在【内容根坐标】(localToRoot) 下的 y 值。由主界面顶部药丸头部经
     * onGloballyPositioned 写入。渲染器据此对内容贴片做 scissor 裁剪，避免内容（卡片描边等）
     * 在垂直滚动时顶撞/盖过固定 Tab 栏。默认 0 = 不裁剪。
     */
    @Volatile var contentClipTop: Float = 0f

    /**
     * HDR surface（HdrPatchHost）左上角在【内容根坐标】(localToRoot) 下的值。由宿主经
     * onGloballyPositioned 写入。贴片上报的是根坐标，渲染器绘制时统一减去该偏移得到
     * surface 像素坐标（与 surface 像素原点对齐，消除状态栏/内边距导致的整体下移）。
     */
    @Volatile var surfaceRootX: Float = 0f
    @Volatile var surfaceRootY: Float = 0f

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
