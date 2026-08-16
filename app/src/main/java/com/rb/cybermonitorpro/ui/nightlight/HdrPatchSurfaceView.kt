package com.rb.cybermonitorpro.ui.nightlight

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.opengl.GLSurfaceView
import android.os.Build
import android.util.AttributeSet
import android.view.Display
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 行业首创「局部 UI 元素级真 HDR」浮层 —— 全屏透明 PQ GLSurfaceView。
 *
 * 底座直接复用项目内 bright-qr 同款 [HdrEglState]（0x3340 PQ / 10-bit / 8-bit 静默回退），
 * 仅替换渲染器为 [PatchRenderer]，把卡片描边 / Tab 指示条 / 大数字字形 / 折线+网格的
 * 「本体」画进 BT.2020 PQ surface，使其亮度超越 SDR 白场（叠加在 SDR UI 之上增亮）。
 *
 * 铁律继承（与 HdrLumeSurfaceView 完全一致）：
 *  - EGL 选 10/10/10/2 + 注入 BT.2020 PQ（常量 0x3340），失败回退 8/8/8/8。
 *  - PixelFormat.RGBA_1010102（HDR 设备）提供 10-bit + 2-bit alpha 半透明合成。
 *  - setZOrderOnTop(true) + isClickable=false：盖在 SDR UI 之上但不拦截触摸。
 *  - preserveEGLContextOnPause = true；构造即不申请余量，由 setActive(true) 按需 setDesiredHdrHeadroom。
 */
class HdrPatchSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    private val display: Display? = if (Build.VERSION.SDK_INT >= 17) {
        (context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)
            ?.getDisplay(Display.DEFAULT_DISPLAY)
    } else null

    private val displaySupportsHdr: Boolean = Build.VERSION.SDK_INT >= 34 &&
        runCatching { display?.isHdr == true }.getOrDefault(false)

    private val hdrEgl = HdrEglState(displaySupportsHdr)
    private val renderer = PatchRenderer(hdrEgl, display) { pq, ratio ->
        // 仅状态变化时回写主线程，避免每帧 post 引发重组风暴
        if (pq != lastPq || ratio != lastRatio) {
            lastPq = pq
            lastRatio = ratio
            post {
                HdrOverlayState.pqActive.value = pq
                HdrOverlayState.ratioOk.value = ratio
            }
        }
    }

    @Volatile private var lastPq = false
    @Volatile private var lastRatio = false

    private var registryJob: Job? = null

    init {
        val fmt = if (displaySupportsHdr) PixelFormat.RGBA_1010102 else PixelFormat.RGBA_8888
        if (Build.VERSION.SDK_INT >= 33) runCatching { holder.setFormat(fmt) }

        setEGLContextClientVersion(2)
        setEGLConfigChooser(hdrEgl)
        setEGLWindowSurfaceFactory(hdrEgl)
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
        preserveEGLContextOnPause = true

        // 浮层盖在 SDR UI 之上但不拦截触摸
        setZOrderOnTop(true)
        isClickable = false
        isFocusable = false
        isFocusableInTouchMode = false
    }

    /** 订阅贴片注册表流（由宿主在 factory 中调用，传入组合的 CoroutineScope）。 */
    fun attachRegistry(scope: CoroutineScope) {
        registryJob?.cancel()
        registryJob = scope.launch {
            HdrPatchRegistry.flow.collect { list ->
                renderer.setPatches(list)
                if (renderer.isActive()) requestRender()
            }
        }
    }

    fun detachRegistry() {
        registryJob?.cancel()
        registryJob = null
    }

    /**
     * 由 HdrPatchHost 调用：开启 = 申请 HDR 余量 + 进入持续渲染；关闭 = 余量归 1.0 并补一帧透明。
     */
    fun setActive(enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= 35) runCatching {
            setDesiredHdrHeadroom(
                if (enabled) HdrCapabilityDetector.computeHeadroom(display, true) else 1f
            )
        }
        renderer.setEnabled(enabled)
        renderMode = if (enabled) RENDERMODE_CONTINUOUSLY else RENDERMODE_WHEN_DIRTY
        requestRender()
    }

    override fun onDetachedFromWindow() {
        if (Build.VERSION.SDK_INT >= 35) runCatching { setDesiredHdrHeadroom(1f) }
        detachRegistry()
        super.onDetachedFromWindow()
    }
}
