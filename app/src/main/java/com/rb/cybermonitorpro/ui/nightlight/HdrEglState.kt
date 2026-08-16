package com.rb.cybermonitorpro.ui.nightlight

import android.opengl.GLSurfaceView.EGLConfigChooser
import android.opengl.GLSurfaceView.EGLWindowSurfaceFactory
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay
import javax.microedition.khronos.egl.EGLSurface

/**
 * 选 10/10/10/2 + 注入 BT.2020 PQ colorspace；无扩展则静默回退 8/8/8/8。
 *
 * 与 HdrTestSurfaceView 验证过的路径一致（这是 Android 16 / 8–12bit 屏上真正激发 HDR 的钥匙）：
 *  - EGL_GL_COLORSPACE_BT2020_PQ_EXT 必须写 **0x3340**（旧值 0x3531 是错的，EGL 静默忽略，
 *    surface 实际从未标 PQ → 永远 8-bit SDR）。
 *  - 用 Display.isHdr() 作 PQ 前置闸门（构造时传入），比单纯依赖 EGL 扩展串更稳。
 *  - eglChooseConfig 后严格比对 R/G/B/A 实际位深，避免选到 8+8+8+2 之类伪 10-bit config。
 *
 * 10/10/10/2 的 alpha=2 也支持半透明合成，供 TurboXdr 浮层作为整体 HDR 增亮层覆盖在 SDR UI 之上。
 *
 * `pqSurfaceActive` 是运行时真相：仅当真正拿到 10-bit config 且 EGL 暴露 PQ 扩展并注入成功才为 true。
 */
class HdrEglState(
    private val displaySupportsHdr: Boolean
) : EGLConfigChooser, EGLWindowSurfaceFactory {

    @Volatile var pqSurfaceActive: Boolean = false
        private set
    @Volatile var tenBitSelected: Boolean = false
        private set

    override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig {
        val canUsePq = displaySupportsHdr && hasExtension(egl, display, EXT_BT2020_PQ)
        val cfg10 = if (canUsePq) pickConfig(egl, display, 10, 10, 10, 2) else null
        tenBitSelected = cfg10 != null
        val chosen = cfg10 ?: pickConfig(egl, display, 8, 8, 8, 8)
            ?: throw IllegalArgumentException("No compatible EGL window config")
        return chosen
    }

    override fun createWindowSurface(
        egl: EGL10,
        display: EGLDisplay,
        config: EGLConfig,
        nativeWindow: Any
    ): EGLSurface {
        // PQ 注入：仅当真拿到 10-bit config 且 EGL 暴露 PQ 扩展。常量 0x3340（正确值）。
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
        }.getOrNull() ?: EGL10.EGL_NO_SURFACE
    }

    override fun destroySurface(egl: EGL10, display: EGLDisplay, surface: EGLSurface) {
        pqSurfaceActive = false
        runCatching { egl.eglDestroySurface(display, surface) }
    }

    private fun pickConfig(egl: EGL10, display: EGLDisplay, r: Int, g: Int, b: Int, a: Int): EGLConfig? {
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

    private fun getComponentSize(egl: EGL10, display: EGLDisplay, config: EGLConfig, attr: Int): Int {
        val v = IntArray(1)
        return if (egl.eglGetConfigAttrib(display, config, attr, v)) v[0] else -1
    }

    private fun hasExtension(egl: EGL10, display: EGLDisplay, wanted: String): Boolean {
        val ext = runCatching { egl.eglQueryString(display, EGL10.EGL_EXTENSIONS) }.getOrNull() ?: return false
        for (token in ext.split(' ')) if (wanted == token) return true
        return false
    }

    companion object {
        private const val EGL_OPENGL_ES2_BIT = 4
        private const val EGL_GL_COLORSPACE_KHR = 0x309D
        // 必须写 0x3340（Android NDK <EGL/eglext.h> 定义）。0x3531 是错的。
        private const val EGL_GL_COLORSPACE_BT2020_PQ_EXT = 0x3340
        private const val EXT_BT2020_PQ = "EGL_EXT_gl_colorspace_bt2020_pq"
    }
}
