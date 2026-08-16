package com.rb.cybermonitorpro.ui.nightlight

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.opengl.GLSurfaceView
import android.os.Build
import android.util.AttributeSet
import android.view.Display
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 局部 HDR 增亮浮层 — 全屏、透明、触摸穿透的 PQ [GLSurfaceView]。
 *
 * 与 [HdrLumeSurfaceView]（CyberNightlight 呼吸光晕）**完全同构**：
 * - 复用 [HdrEglState] 做 10/10/10/2 + BT.2020_PQ EGL 配置。
 * - 复用 [HdrCapabilityDetector] 做 HDR 余量计算。
 * - `setZOrderOnTop(true)` + `isClickable=false`：浮层在 SDR UI 之上但不拦截触摸。
 * - `preserveEGLContextOnPause = true`：离开-返回不丢 EGL/PQ 状态。
 *
 * 差异点：
 * - 渲染器为 [PatchRenderer]（按贴片列表绘制，而非全屏呼吸光晕）。
 * - 从 [HdrPatchRegistry.patchList] StateFlow 订阅贴片数据，每帧更新。
 * - 无贴片时 1 帧透明后停渲（零开销）。
 * - 独立的 intensityMultiplier（「局部 HDR 增亮」子滑块 1.0x–8.0x），
 *   与 CyberNightlight 的呼吸强度（0–1）独立控制。
 */
class HdrPatchSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    private val display: Display? = run {
        if (Build.VERSION.SDK_INT >= 17) {
            (context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)
                ?.getDisplay(Display.DEFAULT_DISPLAY)
        } else null
    }

    private val displaySupportsHdr: Boolean = run {
        if (Build.VERSION.SDK_INT >= 34) {
            runCatching { display?.isHdr == true }.getOrDefault(false)
        } else false
    }

    private val hdrEgl = HdrEglState(displaySupportsHdr)
    private val patchRenderer = PatchRenderer()

    /** 协程作用域（用于订阅 Registry 的 StateFlow），随 View 生命周期销毁。 */
    private val viewScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Volatile private var active: Boolean = false
    @Volatile private var collectionJob: kotlinx.coroutines.Job? = null

    init {
        val fmt = if (displaySupportsHdr) PixelFormat.RGBA_1010102 else PixelFormat.RGBA_8888
        if (Build.VERSION.SDK_INT >= 33) runCatching { holder.setFormat(fmt) }

        setEGLContextClientVersion(2)
        setEGLConfigChooser(hdrEgl)
        setEGLWindowSurfaceFactory(hdrEgl)
        setRenderer(patchRenderer)
        renderMode = RENDERMODE_WHEN_DIRTY
        preserveEGLContextOnPause = true

        setZOrderOnTop(true)
        isClickable = false
    }

    /**
     * 由 [HdrPatchHost] 调用：
     * - 开启：申请 HDR 余量 → 启动 Registry 订阅 → 连续渲染。
     * - 关闭：余量归 1.0 → 停止订阅 → 补一帧透明清残影。
     */
    fun setActive(enabled: Boolean) {
        active = enabled
        patchRenderer.setEnabled(enabled)

        // HDR 余量（与 CyberNightlight 共享同一 headroom 请求通道）
        if (Build.VERSION.SDK_INT >= 35) {
            val headroom = if (enabled) {
                HdrCapabilityDetector.computeHeadroom(display, true)
            } else 1f
            runCatching { setDesiredHdrHeadroom(headroom) }
        }

        if (enabled) {
            renderMode = RENDERMODE_CONTINUOUSLY
            startCollectingPatches()
        } else {
            stopCollectingPatches()
            renderMode = RENDERMODE_WHEN_DIRTY
            requestRender() // 补一帧透明，清掉残影
        }
    }

    fun setIntensityMultiplier(v: Float) {
        patchRenderer.setIntensityMultiplier(v)
        if (active) requestRender()
    }

    /** PQ 表面是否真正激活（运行时真相）。 */
    fun isPqActive(): Boolean = hdrEgl.pqSurfaceActive
    fun isActive(): Boolean = active

    override fun onDetachedFromWindow() {
        if (Build.VERSION.SDK_INT >= 35) runCatching { setDesiredHdrHeadroom(1f) }
        stopCollectingPatches()
        viewScope.cancel()
        super.onDetachedFromWindow()
    }

    // ── 内部：贴片数据流订阅 ──

    private fun startCollectingPatches() {
        collectionJob?.cancel()
        collectionJob = viewScope.launch {
            HdrPatchRegistry.patchList.collectLatest { patches ->
                patchRenderer.updatePatches(patches)
                if (active) requestRender()
            }
        }
    }

    private fun stopCollectingPatches() {
        collectionJob?.cancel()
        collectionJob = null
    }
}
