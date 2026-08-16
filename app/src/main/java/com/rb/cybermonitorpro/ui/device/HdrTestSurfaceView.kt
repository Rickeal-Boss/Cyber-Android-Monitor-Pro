package com.rb.cybermonitorpro.ui.device

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Build
import android.util.AttributeSet
import android.view.Display
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay
import javax.microedition.khronos.egl.EGLSurface

/**
 * 局部 HDR 测试用的 PQ 白场 GLSurfaceView。
 *
 * 关键修复（与 bright-qr 对齐后可在 Android 16 / 8–12bit 设备上真正激发 HDR）：
 *  1. EGL_GL_COLORSPACE_BT2020_PQ_EXT 用正确常量 0x3340（旧值 0x3531 是错的，
 *     EGL 会静默忽略未识别属性，surface 实际未标 PQ → 永远 8-bit SDR）。
 *  2. 用 Display.isHdr()（API 34+）作 PQ 路径前置闸门，
 *     比单纯依赖 EGL 扩展串字符串更稳（部分设备暴露串但 config 不可用）。
 *  3. eglChooseConfig 后用 eglGetConfigAttrib 严格比对 R/G/B/A 实际位深，
 *     避免选到 "8+8+8+2" 之类的伪 10-bit config。
 *  4. 构造时立即申请 MAX 余量（setDesiredHdrHeadroom(10000)），
 *     让 PQ surface 一上来就处于"请求 HDR 提亮"状态。
 *  5. preserveEGLContextOnPause = true，离开-返回时 EGL 上下文/PQ surface 状态不丢。
 *
 * 像素渲染策略：glClearColor 全部 1.0（PQ 表面里即 ST 2084 10,000 nit endpoint），
 * 外圈黑底对照——HDR 真点亮时，整块会肉眼明显比旁边 SDR 灰卡亮得多。
 */
class HdrTestSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    @Volatile var pqSurfaceActive: Boolean = false
        private set
    @Volatile var eglSummary: String = "pending"
        private set

    private val displaySupportsHdr: Boolean = run {
        // API 34+: Display.isHdr()——该 Display 是否被报告为 HDR 支持。
        // 跑在低于 34 不会到达此 API；项目 minSdk 21 但代码路径已用 SDK_INT 守卫。
        if (Build.VERSION.SDK_INT >= 34) {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            val display = dm?.getDisplay(Display.DEFAULT_DISPLAY)
            runCatching { display?.isHdr == true }.getOrDefault(false)
        } else false
    }

    private val pqEgl = PqEglHelper(displaySupportsHdr)

    init {
        // 1) Buffer format: HDR 屏用 10-bit，否则 8-bit。注意顺序要在 setRenderer 前。
        val fmt = if (displaySupportsHdr) PixelFormat.RGBA_1010102 else PixelFormat.RGBA_8888
        if (Build.VERSION.SDK_INT >= 33) {
            runCatching { holder.setFormat(fmt) }
        }

        setEGLContextClientVersion(2)
        setEGLConfigChooser(pqEgl)
        setEGLWindowSurfaceFactory(pqEgl)
        setRenderer(WhiteRenderer())
        renderMode = RENDERMODE_WHEN_DIRTY
        preserveEGLContextOnPause = true

        // 2) 构造即申请最大余量，避免滑条默认 1.0 导致"明确不要 HDR"。
        // API 35+ 才有 setDesiredHdrHeadroom，低版本静默跳过。
        if (Build.VERSION.SDK_INT >= 35) {
            applyRequestedHeadroom(MAX_REQUESTED_HEADROOM)
        }
    }

    /**
     * 由 UI 滑条调用。Bright QR 把 1.0 视作"无 HDR"，MAX_REQUESTED_HEADROOM (10_000)
     * 才是"交还系统自动，给到当前热/电/ROM 限制允许的最大值"。
     */
    fun applyRequestedHeadroom(headroom: Float) {
        if (Build.VERSION.SDK_INT < 35) return
        runCatching { setDesiredHdrHeadroom(headroom) }
    }

    fun isPqActive(): Boolean = pqSurfaceActive
    fun isDisplaySupportsHdr(): Boolean = displaySupportsHdr
    fun eglSummaryText(): String = eglSummary

    private inner class PqEglHelper(
        private val displaySupportsHdr: Boolean
    ) : EGLConfigChooser, EGLWindowSurfaceFactory {

        @Volatile var tenBitSelected: Boolean = false
            private set

        override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig {
            val canUsePq = displaySupportsHdr && hasExtension(egl, display, EXT_BT2020_PQ)
            val cfg10 = if (canUsePq) pickConfig(egl, display, 10, 10, 10, 2) else null
            tenBitSelected = cfg10 != null
            val chosen = cfg10 ?: pickConfig(egl, display, 8, 8, 8, 8)
                ?: throw IllegalArgumentException("No compatible EGL window config")
            eglSummary = when {
                cfg10 != null -> "10-bit + PQ ext"
                canUsePq -> "PQ ext reported, no 10-bit cfg (using 8-bit)"
                displaySupportsHdr -> "display.isHdr()==true but EGL ext missing (using 8-bit)"
                else -> "display does not support HDR (using 8-bit)"
            }
            return chosen
        }

        override fun createWindowSurface(
            egl: EGL10,
            display: EGLDisplay,
            config: EGLConfig,
            nativeWindow: Any
        ): EGLSurface {
            // PQ 注入：仅当真的拿到了 10-bit config 且 EGL 暴露 PQ 扩展。
            // 用正确常量 0x3340，Android NDK <EGL/eglext.h> 定义。
            if (tenBitSelected && hasExtension(egl, display, EXT_BT2020_PQ)) {
                val pqAttribs = intArrayOf(
                    EGL_GL_COLORSPACE_KHR,
                    EGL_GL_COLORSPACE_BT2020_PQ_EXT,
                    EGL10.EGL_NONE
                )
                val pqSurface = runCatching {
                    egl.eglCreateWindowSurface(display, config, nativeWindow, pqAttribs)
                }.getOrNull()
                if (pqSurface != null && pqSurface != EGL10.EGL_NO_SURFACE) {
                    pqSurfaceActive = true
                    return pqSurface
                }
            }
            pqSurfaceActive = false
            return runCatching {
                egl.eglCreateWindowSurface(display, config, nativeWindow, null)
            }.getOrNull()
                ?: EGL10.EGL_NO_SURFACE
        }

        override fun destroySurface(egl: EGL10, display: EGLDisplay, surface: EGLSurface) {
            pqSurfaceActive = false
            runCatching { egl.eglDestroySurface(display, surface) }
        }

        private fun pickConfig(
            egl: EGL10,
            display: EGLDisplay,
            r: Int,
            g: Int,
            b: Int,
            a: Int
        ): EGLConfig? {
            val attribs = intArrayOf(
                EGL10.EGL_RED_SIZE, r,
                EGL10.EGL_GREEN_SIZE, g,
                EGL10.EGL_BLUE_SIZE, b,
                EGL10.EGL_ALPHA_SIZE, a,
                EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                EGL10.EGL_SURFACE_TYPE, EGL10.EGL_WINDOW_BIT,
                EGL10.EGL_NONE
            )
            val num = IntArray(1)
            if (!egl.eglChooseConfig(display, attribs, null, 0, num) || num[0] <= 0) return null
            val configs = arrayOfNulls<EGLConfig>(num[0])
            if (!egl.eglChooseConfig(display, attribs, configs, num[0], num)) return null
            // 严格比对：取 R/G/B/A 实际位深等于请求值的第一个 config。
            for (cfg in configs) {
                if (cfg == null) continue
                if (getComponentSize(egl, display, cfg, EGL10.EGL_RED_SIZE) == r &&
                    getComponentSize(egl, display, cfg, EGL10.EGL_GREEN_SIZE) == g &&
                    getComponentSize(egl, display, cfg, EGL10.EGL_BLUE_SIZE) == b &&
                    getComponentSize(egl, display, cfg, EGL10.EGL_ALPHA_SIZE) == a
                ) {
                    @Suppress("UNCHECKED_CAST")
                    return cfg
                }
            }
            return null
        }

        private fun getComponentSize(
            egl: EGL10,
            display: EGLDisplay,
            config: EGLConfig,
            attr: Int
        ): Int {
            val v = IntArray(1)
            return if (egl.eglGetConfigAttrib(display, config, attr, v)) v[0] else -1
        }

        private fun hasExtension(egl: EGL10, display: EGLDisplay, wanted: String): Boolean {
            val ext = runCatching { egl.eglQueryString(display, EGL10.EGL_EXTENSIONS) }.getOrNull()
                ?: return false
            // 与 bright-qr 对齐：按空格切分做精确匹配（防 "PQ" 子串误匹配）。
            for (token in ext.split(' ')) if (wanted == token) return true
            return false
        }
    }

    private class WhiteRenderer : Renderer {
        override fun onSurfaceCreated(gl: javax.microedition.khronos.opengles.GL10?, config: EGLConfig?) {
            // PQ 端点白：1.0 = ST 2084 10,000 nit（在真正 PQ 表面才成立）；
            // 若 surface 实际是 SDR，这里只是普通全白——HDR 实验室靠肉眼/诊断判定。
            GLES20.glClearColor(1f, 1f, 1f, 1f)
        }
        override fun onSurfaceChanged(gl: javax.microedition.khronos.opengles.GL10?, w: Int, h: Int) {
            GLES20.glViewport(0, 0, w, h)
        }
        override fun onDrawFrame(gl: javax.microedition.khronos.opengles.GL10?) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        }
    }

    companion object {
        /**
         * SurfaceView.setDesiredHdrHeadroom 的最大可接受值。
         * 与 bright-qr HdrPolicy.MAX_REQUESTED_HEADROOM 对齐。
         */
        const val MAX_REQUESTED_HEADROOM: Float = 10_000f

        private const val EGL_OPENGL_ES2_BIT = 4
        private const val EGL_GL_COLORSPACE_KHR = 0x309D
        // EGL_EXT_gl_colorspace_bt2020_pq 定义的颜色空间枚举值。
        // 注意：必须写 0x3340——旧版本里我曾误写 0x3531，EGL 静默忽略未识别属性，
        // 导致 surface 实际从未被标成 PQ，是 pre5/pre6 在 Android 16 上 HDR 不亮
        // 的根本原因。
        private const val EGL_GL_COLORSPACE_BT2020_PQ_EXT = 0x3340
        private const val EXT_BT2020_PQ = "EGL_EXT_gl_colorspace_bt2020_pq"
    }
}
