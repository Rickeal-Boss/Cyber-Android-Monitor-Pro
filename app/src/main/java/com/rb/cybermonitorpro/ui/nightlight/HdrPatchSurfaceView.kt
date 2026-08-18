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
 *  - preserveEGLContextOnPause = true；★ pre16 不再调用 setDesiredHdrHeadroom（PQ surface 由系统自动分配余量）。
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
                // ★ pre14-G2：翻页门控期间不 requestRender——setPatches 已保持最新列表，
                //   ungate 时 setScrollGated(false) 会 requestRender 一帧点亮到位后的贴片，
                //   省掉中间页全部渲染（关闭翻页期纹理风暴与 bitmap 竞态窗口）。
                if (!renderer.scrollGated) requestRender()
            }
        }
    }

    fun detachRegistry() {
        registryJob?.cancel()
        registryJob = null
    }

    /**
     * 由 HdrPatchHost 调用：开启 = 进入事件驱动渲染；关闭 = 补一帧透明。
     *
     * ★ pre16：移除 [setDesiredHdrHeadroom] —— 官方文档确认 headroom > 1 会让系统为 HDR 内容
     *   预留余量、压低底层 SDR 内容亮度，本项目淡蓝光晕背景（AppGlowBackground）因此"透明消失"；
     *   且 pre15 把余量固定为 2×/6× 后真机现象不变（任何 > 1 的值都会触发）。
     *   PQ surface（EGL 0x3340）已由系统按内容自动分配余量，无需手动抬升；滑块亮度改由
     *   [PatchRenderer.pqEnc] 的 effMult 控制。
     * 渲染模式为 [RENDERMODE_WHEN_DIRTY]，仅由注册表流/滑块变化 [requestRender] 驱动。
     */
    fun setActive(enabled: Boolean) {
        renderer.setEnabled(enabled)
        // 事件驱动：贴片注册表变化 / 滑块变化才会 requestRender（见 attachRegistry / setIntensity）
        renderMode = RENDERMODE_WHEN_DIRTY
        requestRender()
    }

    /**
     * ★ pre16：滑块只经 [PatchRenderer.pqEnc] 的 effMult 控制贴片亮度（不再触碰 headroom）。
     *   仅 requestRender 一帧，让 pqEnc 用新 [CyberNightlightSwitch.intensity] 重算贴片亮度。
     */
    fun setIntensity(v: Float) {
        // 滑块变化只重绘（pqEnc 重算亮度），不再 setDesiredHdrHeadroom。
        if (renderer.isActive()) requestRender()
    }

    /**
     * ★ pre14-G2：翻页门控。gated=true 时 GL 只输出透明帧（不遍历贴片、不上传纹理），
     * gated=false 时立即 requestRender 一帧绘制最新贴片。
     *
     * 由 HdrPatchHost 在 PagerState.isScrollInProgress 变化时调用：
     *  - 翻页开始 → gate(true)：消除翻页期间纹理上传风暴与 bitmap 竞态窗口
     *  - 翻页到位后延迟 80ms → gate(false)：等旧页 dispose/新页布局完成后一帧点亮
     */
    fun setScrollGated(gated: Boolean) {
        renderer.scrollGated = gated
        requestRender()  // gate=true 时补一帧透明清场；gate=false 时绘制最新贴片
    }

    override fun onDetachedFromWindow() {
        // ★ pre13-C：在 GL 线程删除 GL 纹理 + 清空缓存（queueEvent 串行于 onDrawFrame），
        //   既真删纹理治泄漏（原 releaseGpuResources 只清 CPU HashMap，preserveEGLContextOnPause
        //   下 onSurfaceCreated 不回调 → 纹理永久泄漏 → 耗尽 GL → native crash），
        //   又避免 UI 线程 clear 与 GL 线程并发改 HashMap 的竞态（pre12 复查 R2）。
        runCatching { queueEvent { renderer.clearGpuCaches() } }
        detachRegistry()
        super.onDetachedFromWindow()
    }

}
