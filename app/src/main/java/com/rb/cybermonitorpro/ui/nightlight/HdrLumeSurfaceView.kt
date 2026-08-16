package com.rb.cybermonitorpro.ui.nightlight

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.opengl.GLSurfaceView
import android.os.Build
import android.util.AttributeSet
import android.view.Display

/**
 * CyberNightlight TurboXDR 的局部 HDR 增亮浮层（全屏呼吸光晕）。
 *
 * 关键约束（与已验证的 HdrTestSurfaceView 完全一致，这是 Android 16 / 8–12bit 屏上
 * 真正激发 HDR 的钥匙）：
 *  1. EGL 选 10/10/10/2 config + 注入 BT.2020 PQ colorspace，常量必须用 **0x3340**
 *     （旧值 0x3531 是错的，EGL 静默忽略，surface 永不标 PQ → 永远 8-bit SDR）。
 *  2. PixelFormat.RGBA_1010102（HDR 设备）提供 10-bit + 2-bit alpha 半透明合成。
 *  3. setZOrderOnTop(true) + isClickable=false：浮层盖在 SDR UI 之上但不拦截触摸
 *     （沿用 bright-qr v3 T5）。
 *  4. preserveEGLContextOnPause = true：离开-返回不丢 EGL/PQ 状态。
 *  5. 构造即不申请 HDR 余量；由 setActive(true) 按需 setDesiredHdrHeadroom。
 *
 * 渲染策略：setActive(true) 时 RENDERMODE_CONTINUOUSLY 跑呼吸动画；关闭时切回
 * RENDERMODE_WHEN_DIRTY 并补一帧纯透明（黑色地板=0，不留残影）。
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

    init {
        val fmt = if (displaySupportsHdr) PixelFormat.RGBA_1010102 else PixelFormat.RGBA_8888
        if (Build.VERSION.SDK_INT >= 33) runCatching { holder.setFormat(fmt) }

        setEGLContextClientVersion(2)
        setEGLConfigChooser(hdrEgl)
        setEGLWindowSurfaceFactory(hdrEgl)
        setRenderer(lumeRenderer)
        renderMode = RENDERMODE_WHEN_DIRTY
        preserveEGLContextOnPause = true

        // 浮层盖在 SDR UI 之上但不拦截触摸
        setZOrderOnTop(true)
        isClickable = false
    }

    /**
     * 由 CyberNightlightHost 调用：开启 = 申请 HDR 余量并连续渲染呼吸光晕；
     * 关闭 = 余量归 1.0 并补一帧纯透明。
     */
    fun setActive(enabled: Boolean) {
        active = enabled
        lumeRenderer.setEnabled(enabled)
        if (Build.VERSION.SDK_INT >= 35) {
            val headroom = if (enabled) {
                HdrCapabilityDetector.computeHeadroom(display, true)
            } else 1f
            runCatching { setDesiredHdrHeadroom(headroom) }
        }
        if (enabled) {
            renderMode = RENDERMODE_CONTINUOUSLY
        } else {
            renderMode = RENDERMODE_WHEN_DIRTY
            requestRender() // 补一帧透明，清掉残影
        }
    }

    fun setIntensity(v: Float) {
        lumeRenderer.setIntensity(v)
    }

    /** PQ 表面是否真正激活（运行时真相，可上送诊断/QA）。 */
    fun isPqActive(): Boolean = hdrEgl.pqSurfaceActive

    fun isActive(): Boolean = active

    override fun onDetachedFromWindow() {
        if (Build.VERSION.SDK_INT >= 35) runCatching { setDesiredHdrHeadroom(1f) }
        super.onDetachedFromWindow()
    }
}
