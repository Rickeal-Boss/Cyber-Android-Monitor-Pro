package com.rb.cybermonitorpro.ui.device

import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLSurfaceView.EGLConfigChooser
import android.opengl.GLSurfaceView.EGLWindowSurfaceFactory
import android.opengl.GLSurfaceView.Renderer
import android.os.Build
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay
import javax.microedition.khronos.egl.EGLSurface
import javax.microedition.khronos.opengles.GL10

/**
 * ★ 2026-08-16 HDR 实验室 — 局部 EDR 测试用 PQ 白场 SurfaceView（真机保险验证版）。
 *
 * 原理（对齐 android15-local-hdr-window 调研 + TurboXDR 方案 v3/v4 铁律）：
 *  - surface 标记 BT.2020 PQ colorspace（EGL_EXT_gl_colorspace_bt2020_pq）→ 系统识别为 HDR 图层；
 *  - 渲染恒定 PQ 峰值白（码值 1.0 = 10000 nits），glClear 即可，无需 shader；
 *  - API 35+ 经 SurfaceView.setDesiredHdrHeadroom() 申请 HDR 余量
 *    （≥1.0；1.0 = 无 HDR；0.0 = 交还系统默认）；
 *    实际生效受环境光 / 面板能力 / 位深限制，以 Display.getHdrSdrRatio() 为准（>1.01 判定真正点亮）。
 *  - 绝不使用 Window.setColorMode(COLOR_MODE_HDR) / Window.setDesiredHdrHeadroom()（会让整窗 SDR 褪色）。
 *
 * 铁律：所有 EGL / 框架调用 runCatching / catch(Throwable)，任何失败静默回退 SDR 8-bit 白场。
 */
class HdrTestSurfaceView(context: Context) : GLSurfaceView(context) {

    /** PQ colorspace surface 是否真正激活（GL 线程写 / UI 线程读，诊断用） */
    @Volatile
    var pqSurfaceActive = false
        private set

    /** EGL 配置摘要（诊断行显示） */
    @Volatile
    var eglSummary: String = "pending"
        private set

    private val pqEgl = PqEglHelper()

    init {
        // 10-bit buffer（PixelFormat.RGBA_1010102，API 33+）；低版本 / 失败由 config chooser 回退 8-bit
        if (Build.VERSION.SDK_INT >= 33) {
            runCatching { holder.setFormat(PixelFormat.RGBA_1010102) }
        }
        setEGLContextClientVersion(2)
        setEGLConfigChooser(pqEgl)
        setEGLWindowSurfaceFactory(pqEgl)
        setRenderer(WhiteRenderer())
        // 静态白场：仅在 surface 创建 / requestRender 时绘制，不持续渲染，省电
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    /** 申请 HDR 余量（API 35+）。低版本静默 no-op（降级铁律）。 */
    fun applyHeadroom(headroom: Float) {
        if (Build.VERSION.SDK_INT < 35) return
        runCatching { setDesiredHdrHeadroom(headroom) }
    }

    /**
     * EGL 10-bit 配置 + PQ colorspace 注入（单一类持有共享状态）。
     * 注意：GLSurfaceView 回调体系为 javax.microedition.khronos.egl.*（EGL10），
     * 不可与 android.opengl.EGL*（EGL14 体系）混用，否则无法编译。
     */
    private inner class PqEglHelper : EGLConfigChooser, EGLWindowSurfaceFactory {
        @Volatile var pqActive = false

        override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig? {
            val exts = runCatching { egl.eglQueryString(display, EGL10.EGL_EXTENSIONS) }
                .getOrNull().orEmpty()
            val pqSupported = exts.contains(PQ_EXTENSION)

            // 首选 10/10/10/2（PQ 候选配置）
            if (pqSupported) {
                val cfg10 = pickConfig(egl, display, 10, 10, 10, 2)
                if (cfg10 != null) {
                    eglSummary = "10-bit + PQ ext"
                    return cfg10
                }
                eglSummary = "PQ ext, no 10-bit cfg"
            } else {
                eglSummary = "no PQ ext"
            }
            // 回退 8/8/8/8（SDR 白场，功能不缺失只是无 HDR 提亮）
            val cfg8 = pickConfig(egl, display, 8, 8, 8, 8)
            if (cfg8 != null) eglSummary = "8-bit SDR (${
                if (pqSupported) "no 10-bit cfg" else "no PQ ext"
            })"
            return cfg8
        }

        override fun createWindowSurface(
            egl: EGL10, display: EGLDisplay, config: EGLConfig, nativeWindow: Any
        ): EGLSurface? {
            // 已选 10-bit config → 注入 BT.2020 PQ colorspace；失败静默回退默认 surface
            // （注意：surface 重建时 chooseConfig 会重跑并刷新 eglSummary，此处不得再叠加 pqActive 状态位判断，
            //   否则重建后会错误地走回退分支丢失 PQ）
            if (eglSummary.startsWith("10-bit")) {
                val attribs = intArrayOf(
                    EGL_GL_COLORSPACE_KHR, EGL_GL_COLORSPACE_BT2020_PQ_EXT, EGL10.EGL_NONE
                )
                val s = runCatching {
                    egl.eglCreateWindowSurface(display, config, nativeWindow, attribs)
                }.getOrNull()
                if (s != null && s != EGL10.EGL_NO_SURFACE) {
                    pqActive = true
                    pqSurfaceActive = true
                    return s
                }
            }
            pqActive = false
            pqSurfaceActive = false
            return runCatching {
                egl.eglCreateWindowSurface(display, config, nativeWindow, null)
            }.getOrNull()
        }

        private fun pickConfig(
            egl: EGL10, display: EGLDisplay, r: Int, g: Int, b: Int, a: Int
        ): EGLConfig? {
            val attribs = intArrayOf(
                EGL10.EGL_SURFACE_TYPE, EGL10.EGL_WINDOW_BIT,
                EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                EGL10.EGL_RED_SIZE, r, EGL10.EGL_GREEN_SIZE, g,
                EGL10.EGL_BLUE_SIZE, b, EGL10.EGL_ALPHA_SIZE, a,
                EGL10.EGL_NONE
            )
            // EGL10.eglChooseConfig 签名：(display, attrib_list, attrib_listOffset, configs, configs_size, num_config)
            // 共 6 参 —  attrib_listOffset=0；先传 configs=null 仅取数量，再取实例
            val num = IntArray(1)
            if (!egl.eglChooseConfig(display, attribs, 0, null, 0, num) || num[0] <= 0) return null
            val configs = arrayOfNulls<EGLConfig>(num[0])
            if (!egl.eglChooseConfig(display, attribs, 0, configs, num[0], num)) return null
            return configs.firstOrNull()
        }
    }

    /** 纯白场渲染器 — glClear 输出 PQ 峰值白（码值 1.0），无需 shader/program */
    private class WhiteRenderer : Renderer {
        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            GLES20.glClearColor(1f, 1f, 1f, 1f)
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            GLES20.glViewport(0, 0, width, height)
        }

        override fun onDrawFrame(gl: GL10?) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        }
    }

    companion object {
        private const val EGL_OPENGL_ES2_BIT = 4           // EGL10 未定义 ES2 位，EGL11 起才有
        private const val EGL_GL_COLORSPACE_KHR = 0x309D
        private const val EGL_GL_COLORSPACE_BT2020_PQ_EXT = 0x3531
        private const val PQ_EXTENSION = "EGL_EXT_gl_colorspace_bt2020_pq"
    }
}
