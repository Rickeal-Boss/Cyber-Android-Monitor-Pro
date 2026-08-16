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
 * ★ 2026-08-16 HDR 实验室 — 局部 EDR 测试用 PQ 白场 SurfaceView（真机保险验证版，策略修正 v2）。
 *
 * 原理（对齐 android15-local-hdr-window 调研 + TurboXDR 方案 v3/v4 铁律）：
 *  - surface 标记 BT.2020 PQ colorspace（EGL_GL_COLORSPACE_BT2020_PQ_EXT）→ 系统识别为 HDR 图层；
 *  - 渲染恒定 PQ 峰值白（码值 1.0 = 10000 nits），glClear 即可，无需 shader；
 *  - API 35+ 经 SurfaceView.setDesiredHdrHeadroom() 申请 HDR 余量
 *    （0.0 = 交还系统自动；1.0 = 明确无 HDR；>1 = 请求提亮；上限 10000）；
 *    实际生效受环境光 / 面板能力 / 位深限制，以 Display.getHdrSdrRatio() 为准（>1.01 判定真正点亮）。
 *  - 绝不使用 Window.setColorMode(COLOR_MODE_HDR) / Window.setDesiredHdrHeadroom()（会让整窗 SDR 褪色）。
 *
 * ⚠️ 策略修正 v2（修复 Android 16 上"直接回退 8bit SDR"）：
 *  旧实现用 `eglQueryString(EGL_EXTENSIONS)` 是否包含 "EGL_EXT_gl_colorspace_bt2020_pq"
 * 作为"是否尝试 PQ"的闸门——现代 Android（ANGLE 默认后端）往往不在扩展串里暴露该扩展，
 * 于是"还没尝试就放弃"，直接走 8-bit SDR。
 *  新实现：不再用扩展串做闸门。只要能拿到 10-bit EGL config，就**直接尝试**注入 PQ colorspace，
 *  以 `eglCreateWindowSurface(..., attribs)` 的**实际创建结果**作为 pqSurfaceActive 的真相；
 *  扩展串仅作诊断信息保留。这样在 ANGLE 设备上也能真正点亮 PQ 图层。
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
     *
     * 策略 v2：chooseConfig 只负责"能否拿到 10-bit config"（chose10bit 标记），
     * 是否注入 PQ colorspace 由 createWindowSurface 用"实际创建结果"决定——不再预读扩展串做闸门。
     */
    private inner class PqEglHelper : EGLConfigChooser, EGLWindowSurfaceFactory {
        @Volatile var pqActive = false
        @Volatile var chose10bit = false
        @Volatile var pqExtListed = false

        override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig? {
            // 扩展串仅作诊断信息，不再作为"是否尝试 PQ"的闸门（v2 修正）
            val exts = runCatching { egl.eglQueryString(display, EGL10.EGL_EXTENSIONS) }
                .getOrNull().orEmpty()
            pqExtListed = exts.contains(PQ_EXTENSION)

            // 首选 10/10/10/2（PQ 候选配置）；失败再回退 8/8/8/8（SDR 白场）
            val cfg10 = pickConfig(egl, display, 10, 10, 10, 2)
            if (cfg10 != null) {
                chose10bit = true
                eglSummary = "10-bit cfg" + if (pqExtListed) " (PQ ext listed)" else " (PQ ext NOT listed)"
                return cfg10
            }
            chose10bit = false
            val cfg8 = pickConfig(egl, display, 8, 8, 8, 8)
            eglSummary = if (cfg8 != null) "8-bit SDR (no 10-bit cfg)" else "no EGL config"
            return cfg8
        }

        override fun createWindowSurface(
            egl: EGL10, display: EGLDisplay, config: EGLConfig, nativeWindow: Any
        ): EGLSurface? {
            // 关键修正 v2：拿到 10-bit config 就直接尝试注入 BT.2020 PQ colorspace，
            // 以 eglCreateWindowSurface 的**实际返回值**作为 pqSurfaceActive 真相——
            // 不依赖扩展串（ANGLE 设备常不暴露该串却仍接受属性）。
            if (chose10bit) {
                val attribs = intArrayOf(
                    EGL_GL_COLORSPACE_KHR, EGL_GL_COLORSPACE_BT2020_PQ_EXT, EGL10.EGL_NONE
                )
                val s = runCatching {
                    egl.eglCreateWindowSurface(display, config, nativeWindow, attribs)
                }.getOrNull()
                if (s != null && s != EGL10.EGL_NO_SURFACE) {
                    pqActive = true
                    pqSurfaceActive = true
                    eglSummary = "PQ surface active" + if (!pqExtListed) " (ext not advertised)" else ""
                    return s
                }
                // PQ 注入被拒（极少）：回退默认 surface（仍 10-bit，但按 SDR 处理）
                eglSummary = "PQ inject failed -> 10-bit SDR"
            }
            pqActive = false
            pqSurfaceActive = false
            return runCatching {
                egl.eglCreateWindowSurface(display, config, nativeWindow, null)
            }.getOrNull()
        }

        override fun destroySurface(egl: EGL10, display: EGLDisplay, surface: EGLSurface) {
            runCatching { egl.eglDestroySurface(display, surface) }
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
            // EGL10.eglChooseConfig 签名（Android，5 参，无 attrib_listOffset）：
            //   (display, attrib_list, configs, configs_size, num_config)
            // 先传 configs=null 仅取数量，再取实例
            val num = IntArray(1)
            if (!egl.eglChooseConfig(display, attribs, null, 0, num) || num[0] <= 0) return null
            val configs = arrayOfNulls<EGLConfig>(num[0])
            if (!egl.eglChooseConfig(display, attribs, configs, num[0], num)) return null
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
