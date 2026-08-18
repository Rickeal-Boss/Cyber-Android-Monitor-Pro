package com.rb.cybermonitorpro.ui.nightlight

import android.graphics.RectF
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

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
        // ★ pre19-B：去重判定补全（pre18c 只比 bounds/bitmap/color 有三个口子）：
        //   1) bounds 改亚像素容差比较——localToRoot 变换链/布局取整的 ±0.0x px 抖动
        //      会击穿逐位 float 相等，滚动停止时照样全量发射重渲染；
        //   2) points（折线/网格几何）参与比较——否则静止期 HDR 折线随数据刷新被吞
        //      （去重误判为"相同"），折线冻结、下次滚动才跳到最新 → 视觉跳变；
        //   3) 补全 bias/visible/color1/cornerRadius/strokeWidth/text 等几何与样式字段。
        //   真实变化（坐标移动 >0.5px / 数据刷新 / 颜色/样式变化）仍正常发射。
        val old = map[p.id]
        if (old != null && samePatch(old, p)) return
        map[p.id] = p
        _flow.value = map.values.toList()
    }

    /** 贴片内容是否可视为未变（注册表去重）。仅在 [mutex] 内调用。 */
    private fun samePatch(a: HdrPatch, b: HdrPatch): Boolean {
        if (a.bitmap !== b.bitmap) return false
        if (!sameBounds(a.bounds, b.bounds)) return false
        if (!a.color0.contentEquals(b.color0)) return false
        if (!a.color1.contentEquals(b.color1)) return false
        if (a.bias != b.bias) return false
        if (a.visible != b.visible) return false
        if (a.cornerRadiusPx != b.cornerRadiusPx) return false
        if (a.strokeWidthPx != b.strokeWidthPx) return false
        if (a.text != b.text) return false
        if (a.textSizePx != b.textSizePx) return false
        if (a.textBold != b.textBold) return false
        if (a.textMonospace != b.textMonospace) return false
        if (a.letterSpacingEm != b.letterSpacingEm) return false
        if (a.tailDotRadiusPx != b.tailDotRadiusPx) return false
        if (a.topZone != b.topZone) return false
        // points（折线/网格）：任一为空或内容不同 → 视为变化
        if (a.points == null || b.points == null) return a.points == null && b.points == null
        return a.points.contentEquals(b.points)
    }

    /** 亚像素容差包围盒比较：0.5px 内视为相同。滚动中真实移动远超此值，仍正常发射。 */
    private fun sameBounds(a: RectF, b: RectF): Boolean {
        val eps = 0.5f
        return abs(a.left - b.left) <= eps && abs(a.top - b.top) <= eps &&
            abs(a.right - b.right) <= eps && abs(a.bottom - b.bottom) <= eps
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
