package com.rb.cybermonitorpro.ui.nightlight

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * CyberNightlight TurboXDR 的呼吸光晕渲染器。
 *
 * 设计要点（与 HdrTestSurfaceView 验证过的真 HDR 路径配合）：
 *  - 全屏四边形 + 片元着色器，绘制"中心留空、边缘呼吸"的霓虹光晕，
 *    仿电子表夜光 / 氛围背光，不遮挡中心 UI 内容。
 *  - 黑色地板 = 0：core 趋零处输出 vec4(0,0,0,0)（纯透明黑），不抬升下方 SDR UI；
 *    仅在光晕处叠加 HDR 高亮，配合 PQ surface 真正超过 SDR 峰值亮度。
 *  - uTime 驱动呼吸，uIntensity 由 CyberNightlightSwitch.intensity 注入（0..1）。
 *  - 分辨率无关：vUv 归一化，uResolution 仅用于潜在的长宽比修正（当前均匀）。
 */
class LumeRenderer : GLSurfaceView.Renderer {

    @Volatile var intensity: Float = 0.6f
        private set

    /** 渲染总闸：false 时只清成纯透明黑（黑色地板=0），不绘制任何光晕。 */
    @Volatile var enabled: Boolean = false
        private set

    private var program: Int = 0
    private var aPos: Int = 0
    private var uTime: Int = 0
    private var uIntensity: Int = 0
    private var uResolution: Int = 0
    private var vbo: Int = 0
    private val startTime = System.nanoTime()

    // 全屏四边形（两个三角形组成的 TRIANGLE_STRIP）
    private val quad = floatArrayOf(
        -1f, -1f,
        1f, -1f,
        -1f, 1f,
        1f, 1f
    )

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // 黑色地板 = 0：透明黑清空，确保未点亮区域完全不显示。
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        // 半透明合成：与下方 SDR UI 混合。
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        program = buildProgram(VERT, FRAG)
        aPos = GLES20.glGetAttribLocation(program, "aPos")
        uTime = GLES20.glGetUniformLocation(program, "uTime")
        uIntensity = GLES20.glGetUniformLocation(program, "uIntensity")
        uResolution = GLES20.glGetUniformLocation(program, "uResolution")

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
        GLES20.glUseProgram(program)
        GLES20.glUniform2f(uResolution, width.toFloat(), height.toFloat())
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        // 关闭时只清成纯透明黑（黑色地板=0），不留任何光晕残影。
        if (!enabled) return

        GLES20.glUseProgram(program)

        val t = (System.nanoTime() - startTime) / 1_000_000_000f
        GLES20.glUniform1f(uTime, t)
        GLES20.glUniform1f(uIntensity, intensity)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    fun setIntensity(v: Float) {
        intensity = v.coerceIn(0f, 1f)
    }

    fun setEnabled(on: Boolean) {
        enabled = on
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

        // 黑色地板 = 0：光晕核心之外输出纯透明黑，不抬升 SDR UI。
        private const val FRAG = """
precision highp float;
varying vec2 vUv;
uniform float uTime;
uniform float uIntensity;
uniform vec2 uResolution;

const vec3 NEON_PURPLE = vec3(0.486, 0.227, 0.929);
const vec3 NEON_CYAN   = vec3(0.157, 0.824, 0.851);

void main() {
    float r = distance(vUv, vec2(0.5));
    // 边缘呼吸光晕：中心留空保证可读性，外围霓虹氛围背光
    float rim = smoothstep(0.33, 0.72, r) * (1.0 - smoothstep(0.72, 0.98, r));
    float breath = 0.55 + 0.45 * sin(uTime * 1.1);
    float core = rim * uIntensity * breath;
    vec3 col = mix(NEON_CYAN, NEON_PURPLE, smoothstep(0.33, 0.95, r));
    // 黑色地板 = 0：core 趋零处输出纯透明黑
    float alpha = clamp(core * 0.5, 0.0, 0.5);
    gl_FragColor = vec4(col, alpha);
}
"""
    }
}
