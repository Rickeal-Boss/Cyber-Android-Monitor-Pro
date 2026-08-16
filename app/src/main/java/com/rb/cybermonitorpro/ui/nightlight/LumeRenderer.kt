package com.rb.cybermonitorpro.ui.nightlight

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.SystemClock
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * CyberNightlight TurboXDR 边缘闪光渲染器 —— 仿电子表夜光（一次性）。
 *
 * 与 HdrTestSurfaceView 验证过的真 HDR 路径配合：
 *  - 边缘薄霓虹环（紫→青），alpha 由 uFlash 驱动 1 → 0 缓出（约 800ms），
 *    播完即停；不再持续呼吸。
 *  - 黑色地板 = 0：闪光外区域与空闲帧输出 vec4(0,0,0,0)（纯透明黑），不抬升 SDR UI；
 *    仅闪光期间输出高亮度 HDR alpha，配合 PQ surface 真正超过 SDR 峰值亮度。
 *  - 开启后靠 SurfaceView 持续请求 RENDERMODE_CONTINUOUSLY 渲染闪光；
 *    闪光结束后由 SurfaceView 切回 RENDERMODE_WHEN_DIRTY 待机。
 *
 * 强度语义：uFlash 仅承担"闪光 alpha"曲线，与 HDR 亮度倍数（1.0×–8.0×）无关。
 * 亮度倍数由 HdrLumeSurfaceView.setDesiredHdrHeadroom 控制（System 级 headroom）。
 */
class LumeRenderer : GLSurfaceView.Renderer {

    /** 渲染总闸（仅 toggle 开启时为 true）。 */
    @Volatile var enabled: Boolean = false
        private set

    /** 闪光开始时间锚（毫秒，SystemClock.uptimeMillis 基准）。0L 表示当前无闪光。 */
    @Volatile private var flashStartMs: Long = 0L

    /** 闪光持续时间（毫秒）。 */
    var flashDurationMs: Long = 800L

    private var program: Int = 0
    private var aPos: Int = 0
    private var uFlash: Int = 0
    private var vbo: Int = 0

    /** 是否正在播闪光（surface 何时切回 WHEN_DIRTY 用）。 */
    fun isFlashing(): Boolean = flashStartMs != 0L

    /** 是否已经检测到当前闪光播完（用于通知 SurfaceView 切回 WHEN_DIRTY 节能）。 */
    @Volatile private var flashExpiredFlag: Boolean = false

    // 全屏四边形（两个三角形组成的 TRIANGLE_STRIP）
    private val quad = floatArrayOf(
        -1f, -1f,
        1f, -1f,
        -1f, 1f,
        1f, 1f
    )

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // 黑色地板 = 0：透明黑清空，确保未点亮区域完全不显示
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        // 半透明合成：与下方 SDR UI 混合
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        program = buildProgram(VERT, FRAG)
        aPos = GLES20.glGetAttribLocation(program, "aPos")
        uFlash = GLES20.glGetUniformLocation(program, "uFlash")

        // 上传全屏四边形到 VBO
        val buf = IntArray(1)
        GLES20.glGenBuffers(1, buf, 0)
        vbo = buf[0]
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glBufferData(
            GLES20.GL_ARRAY_BUFFER,
            quad.size * 4,
            java.nio.ByteBuffer.allocateDirect(quad.size * 4).apply {
                order(java.nio.ByteOrder.nativeOrder())
                asFloatBuffer().put(quad)
            },
            GLES20.GL_STATIC_DRAW
        )
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        // 关闭时只清成纯透明黑（黑色地板=0），不留任何残影
        if (!enabled || flashStartMs == 0L) return

        val now = SystemClock.uptimeMillis()
        val dt = now - flashStartMs
        if (dt >= flashDurationMs) {
            // 闪光已结束：清理状态，标记过期（SurfaceView 会切回 WHEN_DIRTY 节能）
            flashStartMs = 0L
            flashExpiredFlag = true
            return
        }

        val t = dt.toFloat() / flashDurationMs.toFloat()  // 0..1
        // 缓出三次方：起手强亮，尾段快速消失，模拟电子表夜光按一下闪一下
        val flash = (1f - t) * (1f - t) * (1f - t)

        GLES20.glUseProgram(program)
        GLES20.glUniform1f(uFlash, flash)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    /** 触发一次边缘闪光（toggle on / 切页面时调用）。 */
    fun fireFlash() {
        flashStartMs = SystemClock.uptimeMillis()
        flashExpiredFlag = false
    }

    /**
     * 轮询：当前闪光是否已播完（SurfaceView 在 onDrawFrame 后调用，
     * 若返回 true 则把渲染模式切回 WHEN_DIRTY 节能）。
     */
    fun consumeFlashExpired(): Boolean {
        if (flashExpiredFlag) {
            flashExpiredFlag = false
            return true
        }
        return false
    }

    fun setEnabled(on: Boolean) {
        enabled = on
        if (!on) {
            flashStartMs = 0L
            flashExpiredFlag = false
        }
    }

    private fun buildProgram(vsrc: String, fsrc: String): Int {
        val vs = compile(GLES20.GL_VERTEX_SHADER, vsrc)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, fsrc)
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, vs)
        GLES20.glAttachShader(p, fs)
        GLES20.glLinkProgram(p)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(p)
            GLES20.glDeleteProgram(p)
            throw RuntimeException("Lume program link failed: $log")
        }
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        return p
    }

    private fun compile(type: Int, src: String): Int {
        val sh = GLES20.glCreateShader(type)
        GLES20.glShaderSource(sh, src)
        GLES20.glCompileShader(sh)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(sh, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(sh)
            GLES20.glDeleteShader(sh)
            throw RuntimeException("Lume shader compile failed ($type): $log")
        }
        return sh
    }

    companion object {
        private const val VERT = """
attribute vec2 aPos;
varying vec2 vUv;
void main() {
    vUv = aPos * 0.5 + 0.5;
    gl_Position = vec4(aPos, 0.0, 1.0);
}
"""

        // 边缘薄霓虹环（紫→青）；闪光外与空闲帧输出 vec4(0,0,0,0)。
        private const val FRAG = """
precision highp float;
varying vec2 vUv;
uniform float uFlash;

const vec3 NEON_PURPLE = vec3(0.486, 0.227, 0.929);
const vec3 NEON_CYAN   = vec3(0.157, 0.824, 0.851);

void main() {
    float r = distance(vUv, vec2(0.5));
    // 边缘环：中心留空保证可读性；外侧贴近屏幕边缘
    float rim = smoothstep(0.36, 0.74, r) * (1.0 - smoothstep(0.74, 0.995, r));
    vec3 col = mix(NEON_CYAN, NEON_PURPLE, smoothstep(0.36, 0.95, r));
    float alpha = clamp(rim * uFlash, 0.0, 0.85);
    gl_FragColor = vec4(col, alpha);
}
"""
    }
}