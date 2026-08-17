package com.rb.cybermonitorpro.ui.nightlight

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.opengl.GLSurfaceView
import android.os.Build
import android.util.AttributeSet
import android.view.Display
import com.rb.cybermonitorpro.ui.effects.CyberNightlightSwitch
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
                // ★ pre13-D：无条件 requestRender。onDrawFrame 内部已有 active 门控会 early-return，
                //   去掉 isActive 判断可避免「setActive 的 requestRender 早于首帧注册表数据到达」时
                //   停在空透明帧 → HDR 层偶发不显示（治 pre13-D）。
                requestRender()
            }
        }
    }

    fun detachRegistry() {
        registryJob?.cancel()
        registryJob = null
    }

    /**
     * 由 HdrPatchHost 调用：开启 = 申请 HDR 余量 + 进入事件驱动渲染；关闭 = 余量归 1.0 并补一帧透明。
     *
     * 注意：贴片 surface 的 HDR 余量现在**跟随滑块**([CyberNightlightSwitch.intensity])，
     * 而非 [HdrCapabilityDetector.computeHeadroom] 的常数（Xiaomi 上恒=8×，与滑块无关）。
     * 渲染模式改回 [RENDERMODE_WHEN_DIRTY]，仅由注册表流/滑块变化 [requestRender] 驱动，
     * 砍掉持续合成的帧成本（治 现象A 卡顿 + 双 surface 并发）。
     */
    fun setActive(enabled: Boolean) {
        val headroom = if (enabled) CyberNightlightSwitch.intensity else 1f
        if (Build.VERSION.SDK_INT >= 35) runCatching { setDesiredHdrHeadroom(headroom.coerceIn(1f, 8f)) }
        renderer.setEnabled(enabled)
        // 事件驱动：贴片注册表变化 / 滑块变化才会 requestRender（见 attachRegistry / setIntensity）
        renderMode = RENDERMODE_WHEN_DIRTY
        requestRender()
    }

    /**
     * 设定贴片 surface 的 HDR 强度倍率 ∈ [1.0×, 8.0×]，直接对应 SurfaceView.setDesiredHdrHeadroom
     * （对齐 [HdrLumeSurfaceView.setIntensity]）。仅当 active 时实时改 headroom，否则只是记住。
     *
     * 由于渲染模式为 [RENDERMODE_WHEN_DIRTY]，滑块变化后必须 [requestRender] 一帧，
     * 让 [PatchRenderer.pqEnc] 用新 [CyberNightlightSwitch.intensity] 重算贴片亮度
     * （否则滑块拉不动贴片亮度，现象B 修复失效）。
     */
    fun setIntensity(v: Float) {
        val clamped = v.coerceIn(1.0f, 8.0f)
        // ★ pre13-E：headroom 仅在 API≥35 生效，但无论 API 高低，active 时都需 requestRender 一帧，
        //   让 PatchRenderer.pqEnc 用新 intensity 重算贴片亮度（API<35 原因 requestRender 被门控而空操作）。
        if (Build.VERSION.SDK_INT >= 35 && renderer.isActive()) {
            runCatching { setDesiredHdrHeadroom(clamped) }
        }
        if (renderer.isActive()) requestRender()
    }

    override fun onDetachedFromWindow() {
        if (Build.VERSION.SDK_INT >= 35) runCatching { setDesiredHdrHeadroom(1f) }
        // ★ pre13-C：在 GL 线程删除 GL 纹理 + 清空缓存（queueEvent 串行于 onDrawFrame），
        //   既真删纹理治泄漏（原 releaseGpuResources 只清 CPU HashMap，preserveEGLContextOnPause
        //   下 onSurfaceCreated 不回调 → 纹理永久泄漏 → 耗尽 GL → native crash），
        //   又避免 UI 线程 clear 与 GL 线程并发改 HashMap 的竞态（pre12 复查 R2）。
        runCatching { queueEvent { renderer.clearGpuCaches() } }
        detachRegistry()
        super.onDetachedFromWindow()
    }
}
