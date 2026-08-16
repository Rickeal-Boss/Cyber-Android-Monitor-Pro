package com.rb.cybermonitorpro.ui.nightlight

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.min

/**
 * 局部 HDR 增亮渲染器 — 在 PQ 浮层上按 [HdrPatch] 列表绘制四类 HDR 辉光。
 *
 * 核心技术决策：
 * 1. 显式 ST 2084 PQ OETF 编码（shader 内 pow() 形式）
 * 2. 四类贴片：RECT_GLOW / TEXT_BLOOM / LINE_GLOW / TAB_INDICATOR
 * 3. 黑色地板 = 0（无贴片区域透明）
 * 4. MAX_PATCHES=32 硬上限
 */
class PatchRenderer : GLSurfaceView.Renderer {

    @Volatile var intensityMultiplier: Float = 1.0f
        private set

    @Volatile var enabled: Boolean = false
        private set

    private var program: Int = 0
    private var aPos: Int = 0
    private var uResolution: Int = 0
    private var uIntensity: Int = 0
    private var uTime: Int = 0
    private var uPatchCount: Int = 0
    private var uPatchData: IntArray = IntArray(MAX_PATCHES * U_PATCH_STRIDE)

    private var vbo: Int = 0
    private val startTime = System.nanoTime()

    @Volatile private var currentPatches: List<HdrPatch> = emptyList()
    @Volatile private var resX: Float = 1080f
    @Volatile private var resY: Float = 2400f

    private val quad = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)

    companion object {
        const val MAX_PATCHES = 32
        const val U_PATCH_STRIDE = 13 // rect(4)+color(3)+type(1)+peakNits(1)+intensity(1)+bloomRadius(1)+borderWidth(1)+cornerRadius(1)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        program = buildProgram(Shaders.VERT, Shaders.FRAG)
        aPos = GLES20.glGetAttribLocation(program, "aPos")
        uResolution = GLES20.glGetUniformLocation(program, "uResolution")
        uIntensity = GLES20.glGetUniformLocation(program, "uIntensity")
        uTime = GLES20.glGetUniformLocation(program, "uTime")
        uPatchCount = GLES20.glGetUniformLocation(program, "uPatchCount")

        for (i in 0 until MAX_PATCHES) {
            val b = i * U_PATCH_STRIDE
            uPatchData[b+0]  = uLoc(program, "uPatches[$i].rect", 0)
            uPatchData[b+1]  = uLoc(program, "uPatches[$i].rect", 1)
            uPatchData[b+2]  = uLoc(program, "uPatches[$i].rect", 2)
            uPatchData[b+3]  = uLoc(program, "uPatches[$i].rect", 3)
            uPatchData[b+4]  = uLoc(program, "uPatches[$i].color", 0)
            uPatchData[b+5]  = uLoc(program, "uPatches[$i].color", 1)
            uPatchData[b+6]  = uLoc(program, "uPatches[$i].color", 2)
            uPatchData[b+7]  = uLoc(program, "uPatches[$i].type", 0)
            uPatchData[b+8]  = uLoc(program, "uPatches[$i].peakNits", 0)
            uPatchData[b+9]  = uLoc(program, "uPatches[$i].intensity", 0)
            uPatchData[b+10] = uLoc(program, "uPatches[$i].bloomRadius", 0)
            uPatchData[b+11] = uLoc(program, "uPatches[$i].borderWidth", 0)
            uPatchData[b+12] = uLoc(program, "uPatches[$i].cornerRadius", 0)
        }

        val buf = IntArray(1)
        GLES20.glGenBuffers(1, buf, 0)
        vbo = buf[0]
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER,
            quad.size * 4,
            java.nio.ByteBuffer.allocateDirect(quad.size * 4).apply {
                order(java.nio.ByteOrder.nativeOrder())
                asFloatBuffer().put(quad)
            }, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        resX = width.toFloat(); resY = height.toFloat()
        GLES20.glViewport(0, 0, width, height)
        if (program != 0) { GLES20.glUseProgram(program); GLES20.glUniform2f(uResolution, resX, resY) }
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (!enabled || program == 0) return
        val patches = currentPatches
        if (patches.isEmpty()) return

        GLES20.glUseProgram(program)
        val t = (System.nanoTime() - startTime) / 1_000_000_000f
        GLES20.glUniform1f(uTime, t)
        GLES20.glUniform1f(uIntensity, intensityMultiplier)

        val count = minOf(patches.size, MAX_PATCHES)
        GLES20.glUniform1i(uPatchCount, count)

        for (i in 0 until count) {
            val p = patches[i]; val r = p.rect; val b = i * U_PATCH_STRIDE
            // rect → normalized [0,1]；y 翻转到 GL(y-up) 坐标系，且保证 size 为正
            //   pos = (left/resX, 1-bottom/resY)，size = ((right-left)/resX, (bottom-top)/resY) > 0
            GLES20.glUniform4f(uPatchData[b], r.left/resX, 1f-r.bottom/resY, r.right/resX, 1f-r.top/resY)
            GLES20.glUniform3f(uPatchData[b+4], p.color.red, p.color.green, p.color.blue)
            GLES20.glUniform1i(uPatchData[b+7], p.type.ordinal)
            GLES20.glUniform1f(uPatchData[b+8], p.peakNits)
            GLES20.glUniform1f(uPatchData[b+9], p.intensity)
            val params = p.params
            GLES20.glUniform1f(uPatchData[b+10], params.bloomRadiusFactor)
            GLES20.glUniform1f(uPatchData[b+11],
                if (params.borderWidthDp > 0f) params.borderWidthDp else minOf(r.width(),r.height())*0.02f/resX)
            GLES20.glUniform1f(uPatchData[b+12],
                if (params.cornerRadiusPx > 0f) params.cornerRadiusPx/resX else minOf(r.width(),r.height())*0.08f/resX)
        }

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    fun setIntensityMultiplier(v: Float) { intensityMultiplier = v.coerceIn(0.5f, 8.0f) }
    fun setEnabled(on: Boolean) { enabled = on }
    fun updatePatches(patches: List<HdrPatch>) { currentPatches = patches }

    // ── 内部 ──

    private fun buildProgram(vsrc: String, fsrc: String): Int {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vsrc)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fsrc)
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, vs); GLES20.glAttachShader(p, fs)
        GLES20.glLinkProgram(p)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) { val log = GLES20.glGetProgramInfoLog(p); GLES20.glDeleteProgram(p); throw RuntimeException("PatchRenderer link failed: $log") }
        GLES20.glDeleteShader(vs); GLES20.glDeleteShader(fs)
        return p
    }

    private fun compileShader(type: Int, src: String): Int {
        val sh = GLES20.glCreateShader(type)
        GLES20.glShaderSource(sh, src)
        GLES20.glCompileShader(sh)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(sh, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) { val log = GLES20.glGetShaderInfoLog(sh); GLES20.glDeleteShader(sh); throw RuntimeException("PatchRenderer shader compile failed ($type): $log") }
        return sh
    }

    /** 安全获取 uniform location（GLES20 返回 -1 时不会崩溃 glUniform）。 */
    private fun uLoc(program: Int, name: String, idx: Int): Int {
        // 对于数组 uniform，直接用完整名查询
        return GLES20.glGetUniformLocation(program, name)
    }

    private object Shaders {
        // ════════════════════════════════════════════
        // 顶点着色器 — 全屏四边形，输出归一化 UV
        // ════════════════════════════════════════════
        private const val VERT = """
attribute vec2 aPos;
varying vec2 vUv;
void main() {
    vUv = aPos * 0.5 + 0.5;
    gl_Position = vec4(aPos, 0.0, 1.0);
}
"""

        // ════════════════════════════════════════════
        // 片元着色器 — ST 2084 PQ OETF + 四类贴片
        // ════════════════════════════════════════════
        //
        // ST 2084 PQ OETF 常数（SMPTE 标准）：
        //   E = ((c1 + c2*L^m2) / (1 + c3*L^m2))^m1
        //   其中 L = luminance in cd/m² (nits), E ∈ [0,1]
        //   c1 = c3 - c2 + 1 = 3444/4096
        //   c2 = 2413/4096 * 32/2048 ≈ 2413/262144
        //   c3 = 2392/4096
        //   m1 = 128*1260/4096 ≈ 0.159301
        //   m2 = 32*2523/4096 ≈ 19.7143
        //
        // 渲染策略：
        //   遍历所有 patch，对每个像素计算到各 patch 形状的 SDF 距离，
        //   累积辉光贡献，最终经 PQ OETF 编码后输出。
        //   无贡献的像素 → alpha=0（黑色地板=0）。
        //
        private const val FRAG = """
precision highp float;
varying vec2 vUv;

uniform vec2  uResolution;
uniform float uIntensity;     // 全局强度倍率 (1.0–8.0x)
uniform float uTime;
uniform int   uPatchCount;

struct PatchData {
    vec4  rect;          // left,top,right,bottom [0,1]
    vec3  color;         // RGB [0,1]
    int   type;          // 0=RECT_GLOW, 1=TEXT_BLOOM, 2=LINE_GLOW, 3=TAB_INDICATOR
    float peakNits;      // 峰值亮度（尼特）
    float intensity;     // 该贴片基础强度 [0,1]
    float bloomRadius;   // TEXT_BLOOM 扩散系数
    float borderWidth;   // RECT/TAB 边框宽度（归一化）
    float cornerRadius;  // 圆角半径（归一化）
};

uniform PatchData uPatches[32];

// ── ST 2084 PQ OETF ──
float pqOETF(float nits) {
    float L = max(nits, 0.0);
    float Lm = pow(L, 19.7143);
    float num = 0.835937 + 0.00920749 * Lm;
    float den = 1.0 + 0.583984 * Lm;
    return pow(num / den, 0.159301);
}

// ── SDF 工具函数 ──

/** 圆角矩形 SDF（返回有符号距离，内部负、外部正） */
float sdRoundBox(vec2 p, vec2 b, float r) {
    vec2 d = abs(p - 0.5) * b - (b - r);
    return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - r;
}

/** 矩形边框 SDF（在 roundRect 基础上取 abs 减去 halfBorder） */
float sdRoundBoxBorder(vec2 uv, vec2 size, float radius, float borderWidth) {
    float dOuter = sdRoundBox(uv, size, radius);
    float innerSizeX = max(size.x - borderWidth * 2.0, 0.001);
    float innerSizeY = max(size.y - borderWidth * 2.0, 0.001);
    float innerRadius = max(radius - borderWidth, 0.0);
    float dInner = sdRoundBox(uv, vec2(innerSizeX, innerSizeY), innerRadius);
    // 边框区域：在外边界内 && 在内边界外
    float border = max(dOuter, -dInner);
    // 平滑边缘
    return border;
}

/** 高斯近似扩散（用于 TEXT_BLOOM） */
float gaussianFade(float dist, float sigma) {
    return exp(-(dist * dist) / (2.0 * sigma * sigma));
}

// ── 四类贴片渲染函数 ──
// 每个 return vec4(colorRGB, alpha)，alpha=0 表示该像素无贡献

/** RECT_GLOW：矩形边框辉光（卡片描边等） */
vec4 renderRectGlow(vec2 uv, PatchData p) {
    vec2 pos = p.rect.xy;
    vec2 size = p.rect.zw - p.rect.xy;
    if (size.x <= 0.0 || size.y <= 0.0) return vec4(0.0);

    vec2 localUv = (uv - pos) / size;
    float bw = p.borderWidth;
    float cr = p.cornerRadius;

    float d = sdRoundBoxBorder(localUv, size, cr, bw);
    // 边框处 d≈0，远离时 d>0；用 smoothstep 控制发光范围
    float glow = exp(-d * d * 80.0) * p.intensity * uIntensity;
    float alpha = clamp(glow, 0.0, 0.85);

    // PQ 编码：峰值尼特 → PQ 码值
    float pqSignal = pqOETF(p.peakNits * glow);
    return vec4(p.color * pqSignal, alpha);
}

/** TEXT_BLOOM：文字区域高斯辉光（只画光晕不画字形） */
vec4 renderTextBloom(vec2 uv, PatchData p) {
    vec2 pos = p.rect.xy;
    vec2 size = p.rect.zw - p.rect.xy;
    if (size.x <= 0.0 || size.y <= 0.0) return vec4(0.0);

    vec2 center = pos + size * 0.5;
    vec2 toCenter = uv - center;
    // 椭圆距离（文字通常宽>高，用 size 归一化）
    vec2 normDist = toCenter / (size * 0.5 * p.bloomRadius);
    float dist = length(normDist);

    // 高斯 bloom + 强度衰减
    float sigma = 1.2;
    float g = gaussianFade(dist, sigma) * p.intensity * uIntensity;
    // 文字中心留空（避免字形区域过曝，保持 Compose SDR 文字可读）
    float coreMask = smoothstep(0.15, 0.4, dist);
    float bloom = g * coreMask;
    float alpha = clamp(bloom * 0.7, 0.0, 0.75);

    float pqSignal = pqOETF(p.peakNits * bloom);
    return vec4(p.color * pqSignal, alpha);
}

/** LINE_GLOW：折线段带状辉光（简化版——按 rect 区域绘制水平/垂直方向辉光条） */
vec4 renderLineGlow(vec2 uv, PatchData p) {
    vec2 pos = p.rect.xy;
    vec2 size = p.rect.zw - p.rect.xy;
    if (size.x <= 0.0 || size.y <= 0.0) return vec4(0.0);

    vec2 localUv = (uv - pos) / size;

    // 折线区域内的辉光：沿短轴方向高斯扩散
    float aspect = size.x / max(size.y, 0.001);
    vec2 stretched = vec2(localUv.x, localUv.y * max(aspect, 1.0));
    float lineDist = abs(stretched.y - 0.5) * 2.0; // 到"中线"的距离 [0,1]

    float lineWidth = p.borderWidth * 3.0; // 线宽（比边框略粗以可见）
    float glow = gaussianFade(lineDist / max(lineWidth, 0.01), 0.8) * p.intensity * uIntensity;

    // X 方向渐隐（两端 fade out）
    float xFade = min(localUv.x / 0.08, (1.0 - localUv.x) / 0.08);
    xFade = clamp(xFade, 0.0, 1.0);
    glow *= xFade;

    float alpha = clamp(glow * 0.65, 0.0, 0.7);
    float pqSignal = pqOETF(p.peakNits * glow);
    return vec4(p.color * pqSignal, alpha);
}

/** TAB_INDICATOR：Tab 选中指示器（底部高亮条 + 微弱背景辉光） */
vec4 renderTabIndicator(vec2 uv, PatchData p) {
    vec2 pos = p.rect.xy;
    vec2 size = p.rect.zw - p.rect.xy;
    if (size.x <= 0.0 || size.y <= 0.0) return vec4(0.0);

    vec2 localUv = (uv - pos) / size;

    // 底部指示条（占高度 8%，位于贴片底部 = 屏幕底部）
    float barY = 0.08;
    float barGlow = (1.0 - smoothstep(barY, barY + 0.06, localUv.y)) *
                    smoothstep(barY - 0.25, barY + 0.06, localUv.y);
    barGlow *= p.intensity * uIntensity * 1.5; // 指示条更亮

    // 整体微弱背景辉光
    float bgGlow = sdRoundBox(localUv - 0.5, size, p.cornerRadius);
    bgGlow = exp(-bgGlow * bgGlow * 30.0) * p.intensity * uIntensity * 0.25;

    float total = barGlow + bgGlow;
    float alpha = clamp(total * 0.6, 0.0, 0.8);
    float pqSignal = pqOETF(p.peakNits * total);
    return vec4(p.color * pqSignal, alpha);
}

// ── 主函数 ──
void main() {
    vec3 accumColor = vec3(0.0);
    float accumAlpha = 0.0;

    for (int i = 0; i < 32; i++) {
        if (i >= uPatchCount) break;
        PatchData pd = uPatches[i];
        vec4 contribution = vec4(0.0);

        if (pd.type == 0) contribution = renderRectGlow(vUv, pd);       // RECT_GLOW
        else if (pd.type == 1) contribution = renderTextBloom(vUv, pd);  // TEXT_BLOOM
        else if (pd.type == 2) contribution = renderLineGlow(vUv, pd);   // LINE_GLOW
        else if (pd.type == 3) contribution = renderTabIndicator(vUv, pd); // TAB_INDICATOR

        // 叠加（加法混合模拟 HDR 发光效果）
        accumColor += contribution.rgb;
        accumAlpha = max(accumAlpha, contribution.a); // 取最大 alpha 避免叠加过曝
    }

    // 黑色地板 = 0：无贴片贡献时完全透明
    if (accumAlpha < 0.005) {
        gl_FragColor = vec4(0.0, 0.0, 0.0, 0.0);
    } else {
        gl_FragColor = vec4(accumColor, accumAlpha);
    }
}
"""
    }
}
