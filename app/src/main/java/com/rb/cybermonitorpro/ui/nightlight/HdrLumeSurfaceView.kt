package com.rb.cybermonitorpro.ui.nightlight

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Display

/**
 * CyberNightlight TurboXDR 的局部 HDR 增亮浮层 —— 全屏一次性边缘闪光 + 持续 headroom。
 *
 * 关键约束（与已验证的 HdrTestSurfaceView 完全一致，是 Android 16 / 8–12bit 屏上
 * 真正激发 HDR 的钥匙）：
 *  1. EGL 选 10/10/10/2 config + 注入 BT.2020 PQ colorspace，常量必须用 **0x3340**
 *     （旧值 0x3531 是错的，EGL 静默忽略，surface 永不标 PQ → 永远 8-bit SDR）。
 *  2. PixelFormat.RGBA_1010102（HDR 设备）提供 10-bit + 2-bit alpha 半透明合成。
 *  3. setZOrderMediaOverlay(true) + isClickable=false：浮层位于 SDR UI 之上但不拦截触摸。
 *  4. preserveEGLContextOnPause = true：离开-返回不丢 EGL/PQ 状态。
 *  5. 构造即不申请 HDR 余量；由 setActive(true) 按需 setDesiredHdrHeadroom(slider)。
 *
 * 渲染策略（仿电子表夜光）：
 *  - setActive(true)：触发一次性边缘闪光（fireFlash）；HDR 余量 = slider (1.0×–8.0×)；
 *    切到 RENDERMODE_CONTINUOUSLY 驱动闪光播放；闪光播完自动切回 RENDERMODE_WHEN_DIRTY。
 *  - setActive(false)：余量归 1.0、闪光清零、补一帧透明。
 *  - fireFlash()：可由 Compose 层在 toggle on / 切页面时调用，复用同一边缘闪光。
 *
 * 空闲帧：Surface 输出 vec4(0,0,0,0)（纯透明黑）；PQ surface 自身保留，使
 * setDesiredHdrHeadroom 持续作用于底层 SDR UI —— slider 调多少，整屏 HDR 化多少倍。
 */
class HdrLumeSurfaceView @JvmOverloads constructor(
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
    private val lumeRenderer = LumeRenderer()

    @Volatile private var active: Boolean = false

    /** ★ pre14-G4：翻页门控——期间抑制闪光，到位后由 Host 恢复并播一次。 */
    @Volatile private var flashGated = false

    /** 当前 HDR 强度倍数 ∈ [1.0, 8.0]（来自 slider）；toggle 关闭时强制 1.0。 */
    @Volatile private var intensityMultiplier: Float = 1.0f

    init {
        val fmt = if (displaySupportsHdr) PixelFormat.RGBA_1010102 else PixelFormat.RGBA_8888
        if (Build.VERSION.SDK_INT >= 33) runCatching { holder.setFormat(fmt) }

        setEGLContextClientVersion(2)
        setEGLConfigChooser(hdrEgl)
        setEGLWindowSurfaceFactory(hdrEgl)
        setRenderer(lumeRenderer)
        renderMode = RENDERMODE_WHEN_DIRTY
        preserveEGLContextOnPause = true

        // ★ pre13-P0-B / big-fix2：Lume 层保持 mediaOverlay（位于 SDR UI 之上、但在 Patch 的 onTop 之下），
        //   避免与 Patch 的 onTop 10-bit PQ surface 叠加为两个 onTop 争抢 HDR overlay 平面
        //   （上限通常为 1）→ SurfaceFlinger native crash。Lume 仅边缘闪光，无需盖在最顶层。
        setZOrderMediaOverlay(true)
        isClickable = false
    }

    /**
     * 由 CyberNightlightHost 调用：开启 = 申请 HDR 余量 + 触发一次性边缘闪光；
     * 关闭 = 余量归 1.0 并补一帧透明。
     */
    fun setActive(enabled: Boolean) {
        active = enabled
        lumeRenderer.setEnabled(enabled)
        if (Build.VERSION.SDK_INT >= 35) {
            // ★ pre15：闪光 headroom 固定 LUME_HEADROOM，不再跟随滑块（避免与 Patch 一起把
            //   headroom 拉到 8×，触发屏幕级 HDR 合成、把底层 SDR 背景"整屏 HDR 化"导致失真）。
            val headroom = if (enabled) LUME_HEADROOM else 1f
            runCatching { setDesiredHdrHeadroom(headroom) }
        }
        if (enabled) {
            lumeRenderer.fireFlash()
            renderMode = RENDERMODE_CONTINUOUSLY
        } else {
            renderMode = RENDERMODE_WHEN_DIRTY
            requestRender() // 补一帧透明，清掉残影
        }
    }

    /**
     * 设定 HDR 强度倍数 ∈ [1.0×, 8.0×]，直接对应 SurfaceView.setDesiredHdrHeadroom。
     * 参考 qr.txt: Xiaomi 24069RA21C / Android 16 上 "Maximum supported: 8.0×"。
     */
    fun setIntensity(v: Float) {
        // ★ pre15：闪光 headroom 固定 LUME_HEADROOM，滑块不再改 headroom（multiplier 仅保留供诊断）。
        intensityMultiplier = v.coerceIn(1.0f, 8.0f)
    }

    /**
     * 触发一次性边缘闪光（页面切换、toggle on 时由 Compose 调用）。
     * 仅在 active=true 时生效；空闲态只是丢弃。闪光播完自动切回 RENDERMODE_WHEN_DIRTY 节能。
     */
    fun fireFlash() {
        if (!active || flashGated) return  // ★ pre14-G4：翻页门控期间抑制闪光
        lumeRenderer.fireFlash()
        renderMode = RENDERMODE_CONTINUOUSLY
        // 闪光播完后切回节能模式（PQ surface 仍保留，headroom 持续作用于 UI）
        mainHandler.removeCallbacksAndMessages(FLASH_END_TOKEN)
        mainHandler.postDelayed({
            if (active && !lumeRenderer.isFlashing()) {
                renderMode = RENDERMODE_WHEN_DIRTY
            }
        }, lumeRenderer.flashDurationMs + 50L)
    }

    /**
     * ★ pre14-G4：翻页门控。true 时抑制闪光（丢弃后续 fireFlash + 停止当前闪光），
     * 降低翻页期间夜光条 surface 的持续合成负载（与 Patch 层联动治双 surface 并发）。
     */
    fun setFlashGated(gated: Boolean) {
        flashGated = gated
        if (gated) {
            // 停止当前正在播放的闪光并清空重排回调，切回节能模式
            mainHandler.removeCallbacksAndMessages(FLASH_END_TOKEN)
            lumeRenderer.stopFlash()
            renderMode = RENDERMODE_WHEN_DIRTY
            requestRender()  // 补一帧透明清场
        }
    }

    /** PQ 表面是否真正激活（运行时真相，可上送诊断/QA）。 */
    fun isPqActive(): Boolean = hdrEgl.pqSurfaceActive

    fun isActive(): Boolean = active

    override fun onDetachedFromWindow() {
        if (Build.VERSION.SDK_INT >= 35) runCatching { setDesiredHdrHeadroom(1f) }
        mainHandler.removeCallbacksAndMessages(FLASH_END_TOKEN)
        super.onDetachedFromWindow()
    }

    private companion object {
        private val mainHandler = Handler(Looper.getMainLooper())
        private const val FLASH_END_TOKEN = 0xCAFE_F1A5.toInt()
        /** ★ pre15：闪光 surface 的 HDR 余量固定（边缘闪光亮度 2× SDR 白足够，避免过度 HDR 化背景）。 */
        private const val LUME_HEADROOM = 2f
    }
}