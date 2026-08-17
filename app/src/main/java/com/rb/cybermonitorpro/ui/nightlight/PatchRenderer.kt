package com.rb.cybermonitorpro.ui.nightlight

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.GLSurfaceView
import android.view.Display
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.hypot

/**
 * 局部 HDR 贴片渲染器（GLES20）。
 *
 * 色彩编码：surface 已是 BT.2020 PQ，shader 直接输出 PQ 码值（[HdrPatch.color0]/[HdrPatch.color1]
 * 已由 Compose 侧 [encodePq] 预编码）。输出黑色地板=0（不抬升下方 SDR），叠加混合 [GL_SRC_ALPHA,
 * GL_ONE_MINUS_SRC_ALPHA]。
 *
 * 单 program + uMode 分三类绘制：
 *  - 0 实心（Tab 指示条 / 折线三角带 / 网格线段）
 *  - 1 SDF 圆角描边（卡片描边 / 尾点实心圆）
 *  - 2 字形纹理采样（大数字本体，Canvas 字形 → Bitmap → GL 纹理）
 *
 * 屏外裁剪 + 单 surface 规避并发 HDR overlay 上限；纹理/几何按 key 缓存，避免每帧分配。
 */
class PatchRenderer(
    private val egl: HdrEglState,
    private val display: Display?,
    private val onState: (pq: Boolean, ratio: Boolean) -> Unit
) : GLSurfaceView.Renderer {

    private var program: Int = 0
    private var aPos = 0
    private var uRes = 0
    private var uColor = 0
    private var uColor1 = 0
    private var uMode = 0
    private var uRect = 0
    private var uCorner = 0
    private var uStroke = 0
    private var uTex = 0
    private var uOffset = 0

    @Volatile private var _enabled = false
    private var patches: List<HdrPatch> = emptyList()
    private var surfaceW = 1
    private var surfaceH = 1

    // 字形纹理缓存：key = text|sizePx
    private val glyphCache = LinkedHashMap<String, Int>()
    // 位图掩码纹理缓存（文字/图标本体）：key = patch.id → (源 Bitmap 引用, GL 纹理 id)
    // 用 Bitmap 引用判等，文本/图标变化时自动重传并删旧纹理，避免泄漏。
    private val bitmapTexCache = HashMap<String, Pair<Bitmap, Int>>()
    // 折线几何缓存：key = patch.id → (源 points 引用, 几何)
    private val lineCache = HashMap<String, Pair<FloatArray, LineGeom>>()

    private val emptyGeom = LineGeom(floatArrayOf(), RectF(), 0f)

    fun setEnabled(v: Boolean) { _enabled = v }
    fun isActive(): Boolean = _enabled
    fun setPatches(list: List<HdrPatch>) { patches = list }

    override fun onSurfaceCreated(gl: javax.microedition.khronos.opengles.GL10?, config: javax.microedition.khronos.egl.EGLConfig?) {
        buildProgram()
    }

    override fun onSurfaceChanged(gl: javax.microedition.khronos.opengles.GL10?, w: Int, h: Int) {
        surfaceW = w
        surfaceH = h
    }

    override fun onDrawFrame(gl: javax.microedition.khronos.opengles.GL10?) {
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        val pq = egl.pqSurfaceActive
        val active = _enabled && pq && patches.isNotEmpty()
        // 权威确认：Display.getHdrSdrRatio() > 1.01（API 34+）
        val ratioOk = pq && HdrCapabilityDetector.isHdrLayerObserved(display)
        onState(pq, ratioOk)

        if (!active) return

        GLES20.glViewport(0, 0, surfaceW, surfaceH)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glUseProgram(program)
        GLES20.glUniform2f(uRes, surfaceW.toFloat(), surfaceH.toFloat())
        // surface 根偏移：把根坐标贴片转为 surface 像素坐标（消除状态栏/内边距下移）
        GLES20.glUniform2f(uOffset, HdrPatchRegistry.surfaceRootX, HdrPatchRegistry.surfaceRootY)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glUniform1i(uTex, 0)

        val ox = HdrPatchRegistry.surfaceRootX
        val oy = HdrPatchRegistry.surfaceRootY

        // 屏外裁剪（surface 相对坐标）：与 surface 矩形不相交则跳过
        fun offscreen(b: RectF): Boolean {
            val l = b.left - ox; val t = b.top - oy
            val r = b.right - ox; val bo = b.bottom - oy
            return r < 0 || bo < 0 || l > surfaceW || t > surfaceH
        }

        fun drawPatch(p: HdrPatch) {
            when (p.type) {
                HdrPatchType.CARD_BORDER -> drawSdfBorder(p)
                HdrPatchType.TAB_INDICATOR -> drawSolid(p)
                HdrPatchType.TEXT_GLYPH -> drawGlyph(p)
                HdrPatchType.CHART_LINE -> drawLine(p)
                HdrPatchType.CHART_GRID -> drawGrid(p)
            }
        }

        // 第一段：顶部 Tab 区贴片（topZone），不做 scissor 裁剪，确保指示条/图标/标签正常点亮
        for (p in patches) {
            if (!p.visible || !p.topZone) continue
            if (offscreen(p.bounds)) continue
            drawPatch(p)
        }

        // 第二段：内容贴片，scissor 裁剪掉顶部 Tab 区（contentClipTop 以上），
        // 避免垂直滚动时卡片描边顶撞/盖过固定 Tab 栏。
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
        val clipTop = (HdrPatchRegistry.contentClipTop - oy).coerceAtLeast(0f)
        val clipH = (surfaceH - clipTop).toInt().coerceAtLeast(0)
        GLES20.glScissor(0, 0, surfaceW, clipH)
        for (p in patches) {
            if (!p.visible || p.topZone) continue
            if (offscreen(p.bounds)) continue
            drawPatch(p)
        }
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST)

        // 清理已消失的位图纹理（图标/文字离开屏幕或文本变化时），避免 GL 纹理泄漏
        val liveIds = patches.map { it.id }.toSet()
        val bit = bitmapTexCache.entries.iterator()
        while (bit.hasNext()) {
            val e = bit.next()
            if (e.key !in liveIds) {
                GLES20.glDeleteTextures(1, intArrayOf(e.value.second), 0)
                bit.remove()
            }
        }
    }

    // ── 绘制 ──

    private fun drawSolid(p: HdrPatch) {
        bindQuad(rectVerts(p.bounds))
        GLES20.glUniform4f(uColor, p.color0[0], p.color0[1], p.color0[2], 1f)
        GLES20.glUniform4f(uColor1, p.color0[0], p.color0[1], p.color0[2], 1f)
        GLES20.glUniform1i(uMode, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun drawSdfBorder(p: HdrPatch) {
        val b = p.bounds
        bindQuad(rectVerts(b))
        GLES20.glUniform4f(uColor, p.color0[0], p.color0[1], p.color0[2], 1f)
        GLES20.glUniform4f(uColor1, p.color1[0], p.color1[1], p.color1[2], 1f)
        GLES20.glUniform1i(uMode, 1)
        GLES20.glUniform4f(uRect, b.left, b.top, b.width(), b.height())
        GLES20.glUniform1f(uCorner, p.cornerRadiusPx)
        GLES20.glUniform1f(uStroke, p.strokeWidthPx)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun drawGlyph(p: HdrPatch) {
        // 优先使用 Compose 侧精确栅格化的位图掩码（文字/图标本体）；否则回退现场字形生成。
        val texId = if (p.bitmap != null) ensureBitmapTex(p) else ensureGlyph(p) ?: return
        bindQuad(rectVerts(p.bounds))
        GLES20.glUniform4f(uColor, p.color0[0], p.color0[1], p.color0[2], 1f)
        GLES20.glUniform1i(uMode, 2)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glUniform4f(uRect, p.bounds.left, p.bounds.top, p.bounds.width(), p.bounds.height())
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    /** 上传/复用 Compose 侧传入的位图掩码纹理（白色=不透明）。仅在 p.bitmap != null 时调用。 */
    private fun ensureBitmapTex(p: HdrPatch): Int {
        val bmp = p.bitmap!!
        val cached = bitmapTexCache[p.id]
        if (cached != null && cached.first === bmp) return cached.second
        if (cached != null) GLES20.glDeleteTextures(1, intArrayOf(cached.second), 0)
        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
        bitmapTexCache[p.id] = bmp to tex[0]
        return tex[0]
    }

    private fun drawGrid(p: HdrPatch) {
        val pts = p.points ?: return
        bindQuad(pts)
        GLES20.glUniform4f(uColor, p.color0[0], p.color0[1], p.color0[2], 1f)
        GLES20.glUniform4f(uColor1, p.color0[0], p.color0[1], p.color0[2], 1f)
        GLES20.glUniform1i(uMode, 0)
        GLES20.glLineWidth(1f)
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, pts.size / 2)
    }

    private fun drawLine(p: HdrPatch) {
        val geom = lineGeom(p)
        if (geom.lineVerts.isEmpty()) return
        bindQuad(geom.lineVerts)
        GLES20.glUniform4f(uColor, p.color0[0], p.color0[1], p.color0[2], 1f)
        GLES20.glUniform4f(uColor1, p.color0[0], p.color0[1], p.color0[2], 1f)
        GLES20.glUniform1i(uMode, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, geom.lineVerts.size / 2)
        // 尾点（实心圆 via SDF 全填充）
        if (geom.dotR > 0f) {
            bindQuad(rectVerts(geom.dotBounds))
            GLES20.glUniform4f(uColor, p.color0[0], p.color0[1], p.color0[2], 1f)
            GLES20.glUniform4f(uColor1, p.color0[0], p.color0[1], p.color0[2], 1f)
            GLES20.glUniform1i(uMode, 1)
            GLES20.glUniform4f(uRect, geom.dotBounds.left, geom.dotBounds.top, geom.dotBounds.width(), geom.dotBounds.height())
            GLES20.glUniform1f(uCorner, geom.dotR)
            GLES20.glUniform1f(uStroke, geom.dotR * 2f)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }
    }

    // ── 几何 ──

    private fun rectVerts(b: RectF): FloatArray =
        floatArrayOf(b.left, b.top, b.right, b.top, b.left, b.bottom, b.right, b.bottom)

    private fun lineGeom(p: HdrPatch): LineGeom {
        val pts = p.points ?: return emptyGeom
        val cached = lineCache[p.id]
        if (cached != null && cached.first === pts) return cached.second

        val n = pts.size / 2
        if (n < 2) return emptyGeom
        val hw = (p.strokeWidthPx * 0.5f).coerceAtLeast(1.5f)
        val verts = FloatArray(n * 4)
        var lx = 0f
        var ly = 0f
        for (i in 0 until n) {
            val x = pts[2 * i]
            val y = pts[2 * i + 1]
            val pxp = if (i > 0) pts[2 * (i - 1)] else x - (if (n > 1) pts[2 * (i + 1)] - x else 0f)
            val pyp = if (i > 0) pts[2 * (i - 1) + 1] else y - (if (n > 1) pts[2 * (i + 1) + 1] - y else 0f)
            val nxp = if (i < n - 1) pts[2 * (i + 1)] else x + (x - pxp)
            val nyp = if (i < n - 1) pts[2 * (i + 1) + 1] else y + (y - pyp)
            var tx = nxp - pxp
            var ty = nyp - pyp
            val len = hypot(tx, ty)
            if (len < 1e-3f) {
                tx = 1f
                ty = 0f
            } else {
                tx /= len
                ty /= len
            }
            val nx = -ty
            val ny = tx
            val o = i * 4
            verts[o] = x + nx * hw
            verts[o + 1] = y + ny * hw
            verts[o + 2] = x - nx * hw
            verts[o + 3] = y - ny * hw
            lx = x
            ly = y
        }
        val dotR = p.tailDotRadiusPx.coerceAtLeast(hw)
        val dotBounds = RectF(lx - dotR, ly - dotR, lx + dotR, ly + dotR)
        val g = LineGeom(verts, dotBounds, dotR)
        lineCache[p.id] = pts to g
        return g
    }

    private fun bindQuad(verts: FloatArray) {
        val buf = floatBuffer(verts)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, buf)
    }

    private fun floatBuffer(arr: FloatArray): FloatBuffer {
        val bb = ByteBuffer.allocateDirect(arr.size * 4).order(ByteOrder.nativeOrder())
        val fb = bb.asFloatBuffer()
        fb.put(arr)
        fb.position(0)
        return fb
    }

    // ── 字形纹理 ──

    private fun ensureGlyph(p: HdrPatch): Int? {
        val key = "${p.text}|${p.textSizePx.toInt()}"
        glyphCache[key]?.let { return it }
        val text = p.text ?: return null
        if (text.isEmpty()) return null

        val scale = 2.0f
        val ts = p.textSizePx * scale
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = ts
            isFakeBoldText = p.textBold
            if (p.textMonospace) typeface = Typeface.MONOSPACE
            letterSpacing = p.letterSpacingEm
        }
        val fm = paint.fontMetrics
        val w = (paint.measureText(text) + 4f).coerceAtLeast(1f)
        val h = ((fm.descent - fm.ascent) + 4f).coerceAtLeast(1f)
        val bw = w.toInt()
        val bh = h.toInt()
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawText(text, 2f, -fm.ascent + 2f, paint)
        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
        bmp.recycle()
        glyphCache[key] = tex[0]
        return tex[0]
    }

    // ── program 构建 ──

    private fun buildProgram() {
        program = GLES20.glCreateProgram()
        val vs = compile(GLES20.GL_VERTEX_SHADER, VERT)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, FRAG)
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        aPos = GLES20.glGetAttribLocation(program, "aPos")
        uRes = GLES20.glGetUniformLocation(program, "uRes")
        uColor = GLES20.glGetUniformLocation(program, "uColor")
        uColor1 = GLES20.glGetUniformLocation(program, "uColor1")
        uMode = GLES20.glGetUniformLocation(program, "uMode")
        uRect = GLES20.glGetUniformLocation(program, "uRect")
        uCorner = GLES20.glGetUniformLocation(program, "uCorner")
        uStroke = GLES20.glGetUniformLocation(program, "uStroke")
        uTex = GLES20.glGetUniformLocation(program, "uTex")
        uOffset = GLES20.glGetUniformLocation(program, "uOffset")
    }

    private fun compile(type: Int, src: String): Int {
        val sh = GLES20.glCreateShader(type)
        GLES20.glShaderSource(sh, src)
        GLES20.glCompileShader(sh)
        return sh
    }

    private data class LineGeom(val lineVerts: FloatArray, val dotBounds: RectF, val dotR: Float)

    companion object {
        private const val VERT = """
            attribute vec2 aPos;
            uniform vec2 uRes;
            uniform vec2 uOffset;
            varying vec2 vPix;
            void main() {
                vPix = aPos;
                // 贴片坐标为根坐标，减去 surface 根偏移得到 surface 像素坐标
                vec2 sp = aPos - uOffset;
                vec2 clip = vec2(sp.x / uRes.x * 2.0 - 1.0, 1.0 - sp.y / uRes.y * 2.0);
                gl_Position = vec4(clip, 0.0, 1.0);
            }
        """

        private const val FRAG = """
            precision highp float;
            uniform vec4 uColor;
            uniform vec4 uColor1;
            uniform int uMode;
            uniform vec4 uRect;
            uniform float uCorner;
            uniform float uStroke;
            uniform sampler2D uTex;
            varying vec2 vPix;
            float sdRoundBox(vec2 p, vec2 b, float r) {
                vec2 q = abs(p) - b + r;
                return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
            }
            void main() {
                vec4 outc = uColor;
                if (uMode == 1) {
                    vec2 center = uRect.xy + uRect.zw * 0.5;
                    vec2 p = vPix - center;
                    vec2 halfb = uRect.zw * 0.5;
                    float d = sdRoundBox(p, max(halfb - uStroke * 0.5, vec2(0.0)), max(uCorner - uStroke * 0.5, 0.0));
                    float cov = 1.0 - smoothstep(0.0, 1.5, abs(d) - uStroke * 0.5);
                    float t = clamp((vPix.x - uRect.x) / max(uRect.z, 1.0), 0.0, 1.0);
                    vec3 col = mix(uColor.rgb, uColor1.rgb, t);
                    if (cov <= 0.001) discard;
                    outc = vec4(col, uColor.a * cov);
                } else if (uMode == 2) {
                    vec2 uv = (vPix - uRect.xy) / max(uRect.zw, vec2(1.0));
                    float a = texture2D(uTex, uv).a;
                    if (a <= 0.001) discard;
                    outc = vec4(uColor.rgb, uColor.a * a);
                }
                gl_FragColor = outc;
            }
        """
    }
}
