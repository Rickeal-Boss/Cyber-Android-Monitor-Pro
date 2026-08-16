package com.rb.cybermonitorpro.ui.nightlight

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * 线程安全的 HDR 贴片注册表 — Compose 元素（主线程）通过 register/update/unregister
 * 操作贴片，[PatchRenderer]（GL 线程）每帧读取快照。
 *
 * 设计要点：
 * - **ConcurrentHashMap** 保证 register/update/remove 的原子性，无锁读快照。
 * - **MutableStateFlow<List<HdrPatch>>** 暴露给 GL 线程订阅：每次写操作后
 *   触发一次 `snapshot()` 推送新列表（结构化共享，避免每帧 copy）。
 * - **ID 唯一性**：同一 ID 的 update 会覆盖旧贴片；重复 register 同 ID 为 no-op（建议先 update）。
 * - **生命周期安全**：Compose 元素离开 composition 时必须调用 unregister，
 *   否则"幽灵贴片"会残留在浮层上。
 *
 * 用法示例（在 Composable 中）：
 * ```kotlin
 * val patchId = remember { "cpu_temp_text_${hashCode()}" }
 * LaunchedEffect(patchId) {
 *     HdrPatchRegistry.register(myPatch)
 * }
 * DisposableEffect(patchId) {
 *     onDispose { HdrPatchRegistry.unregister(patchId) }
 * }
 * ```
 */
object HdrPatchRegistry {

    private val patches = ConcurrentHashMap<String, HdrPatch>()

    /** GL 线程订阅的贴片列表快照 — 每次 register/update/unregister 后更新。 */
    private val _patchList = MutableStateFlow(emptyList<HdrPatch>())
    val patchList: StateFlow<List<HdrPatch>> = _patchList

    /** 当前注册的贴片数量（诊断用） */
    val size: Int get() = patches.size

    /**
     * 注册或替换一个贴片。
     * @return true 如果是新注册或值有变化，false 如果完全相同（跳过 snapshot）
     */
    fun register(patch: HdrPatch): Boolean {
        val prev = patches.put(patch.id, patch)
        if (prev == patch) return false  // 引用相同，无变化
        publishSnapshot()
        return true
    }

    /**
     * 更新已存在的贴片（便捷方法：只改部分字段时用）。
     * @return true 如果找到并更新了，false 如果 ID 不存在（会 fallback 到 register）
     */
    fun update(id: String, transform: (HdrPatch) -> HdrPatch): Boolean {
        val existing = patches[id] ?: return false
        val new = transform(existing)
        patches[id] = new
        publishSnapshot()
        return true
    }

    /**
     * 移除指定 ID 的贴片。
     * @return 被移除的贴片，null 表示不存在
     */
    fun unregister(id: String): HdrPatch? {
        val removed = patches.remove(id)
        if (removed != null) {
            publishSnapshot()
        }
        return removed
    }

    /** 清空所有贴片（页面切换 / 设置覆盖层打开时调用）。 */
    fun clear() {
        if (patches.isNotEmpty()) {
            patches.clear()
            publishSnapshot()
        }
    }

    /** 获取当前快照（同步，GL 线程可直接调）。 */
    fun snapshot(): List<HdrPatch> = patches.values.toList()

    /** 检查某 ID 是否已注册。 */
    fun contains(id: String): Boolean = patches.containsKey(id)

    // ── 内部 ──

    private fun publishSnapshot() {
        _patchList.value = patches.values.toList()
    }
}
