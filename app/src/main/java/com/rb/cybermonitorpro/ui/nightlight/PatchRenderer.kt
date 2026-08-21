package com.rb.cybermonitorpro.ui.nightlight

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.GLSurfaceView
import android.os.SystemClock
import android.view.Display
import com.rb.cybermonitorpro.ui.effects.CyberNightlightSwitch
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
    // ★ pre21：由宿主 HdrPatchSurfaceView 注入（GLSurfaceView.requestRender 线程安全，
    //   GL 线程 onDrawFrame 内调用安排下一帧）——pendingUploads 自驱动渲染用。
    var requestFrame: (() -> Unit)? = null
    // ★ pre14：patches 由 StateFlow collect(Main 协程)写入、GL 线程读取，加 @Volatile 保可见性。
    @Volatile private var patches: List<HdrPatch> = emptyList()
    private var surfaceW = 1
    private var surfaceH = 1
    // ★ pre14-G2：翻页门控。true 时 onDrawFrame 只输出透明帧（不遍历贴片/不碰位图/不上传纹理），
    //   由 HdrPatchSurfaceView.setScrollGated 在 PagerState.isScrollInProgress 变化时写入。
    @Volatile var scrollGated: Boolean = false
    // ★ pre14-G4：GL 线程连续异常计数，≥3 次才禁用贴片渲染（单次毛刺自动恢复）。
    private var consecutiveErrors = 0

    // 字形纹理缓存：key = text|sizePx。★ pre13-R1：LRU 上限 64，淘汰时删 GL 纹理，
    // 避免监控数字每秒刷新生成新字形 → GL 内存无限增长 → 驱动 reset/OOM → 闪退。
    private val GLYPH_CACHE_MAX = 64
    private val glyphCache = object : LinkedHashMap<String, Int>(GLYPH_CACHE_MAX, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Int>): Boolean {
            if (size > GLYPH_CACHE_MAX) {
                runCatching { GLES20.glDeleteTextures(1, intArrayOf(eldest.value), 0) }
                return true
            }
            return false
        }
    }
    // 位图掩码纹理缓存（文字/图标本体）：key = patch.id → (源 Bitmap, 上传副本, GL 纹理 id)
    // ★ pre13-R3：上传副本 copy 而非源 Bitmap，使 GL 纹理与 Compose 可能 recycle 的源解耦，
    //   避免 DisposableEffect.onDispose 回收源时恰逢 GL 线程 texImage2D → IllegalStateException。
    // 用源 Bitmap 引用判等，文本/图标变化时自动重传并删旧纹理 + 回收副本，避免泄漏。
    private data class BitmapTex(val original: Bitmap, val copy: Bitmap, val tex: Int)
    private val bitmapTexCache = HashMap<String, BitmapTex>()
    // 折线几何缓存：key = patch.id → (源 points 引用, 几何)
    private val lineCache = HashMap<String, Pair<FloatArray, LineGeom>>()
    // ★ 现象A-3：顶点 FloatBuffer 缓存（按 patch.id），避免每帧 allocateDirect（治 GC 风暴）
    private val quadBufCache = HashMap<String, FloatBuffer>()

    // ★ 现象A-3：isHdrLayerObserved 结果缓存 1 秒，避免每帧跨进程/驱动查询
    @Volatile private var cachedRatioOk = false
    @Volatile private var cachedRatioTs = 0L
    private val RATIO_CACHE_MS = 1000L

    // ★ pre19-A：纹理上传帧预算（治滚动停止瞬间闪断）。
    //   滚动停止触发最后一次布局 pass，所有刚进视野的元素同时首次 upsert →
    //   收尾帧同步 copy+texImage2D 15-30 张纹理（50-150ms）+ 整层清屏重绘 → 视觉闪断。
    //   预算内每帧先补传上一帧入队任务、再允许本帧新上传，超出入队后续帧补传；
    //   SDR 文本/图标本体始终在，缺一帧 HDR 亮起肉眼不可辨，绝不缺字。
    //   key 前缀 "g:" = 字形纹理（共享 LRU），否则 = patch.id（位图掩码纹理）。
    private val pendingUploads = ArrayDeque<Pair<String, HdrPatch>>()
    private val pendingKeys = HashSet<String>()
    private var uploadBudgetLeft = 0

    private val emptyGeom = LineGeom(floatArrayOf(), RectF(), 0f)

    // ★ R2 修复：rectVerts 写入复用数组（8 元素）并返回，消除每贴片 new FloatArray(8)。
    private val tmpRectVerts = FloatArray(8)
    // ★ R2 修复：drawFrameInternal 复用 liveIdSet，消除每帧 patches.map{it.id}.toSet() 分配。
    private val liveIdSet = HashSet<String>()
    // ★ R2 修复：lineGeom 顶点缓冲按 patch.id 复用，仅在容量变化（verts.size != n*4）时重分配。
    private val lineVertsBuf = HashMap<String, FloatArray>()

    fun setEnabled(v: Boolean) { _enabled = v }
    fun isActive(): Boolean = _enabled
    fun setPatches(list: List<HdrPatch>) { patches = list }

    /**
     * 当前 TurboXDR 强度（1.0×–8.0×），由设置页 / HDR 实验室滑块实时写入 [CyberNightlightSwitch.intensity]。
     * 渲染器每帧读取 → 滑块拖动即时反映到局部 HDR 贴片亮度（无需重编码/重组）。
     */
    private val turboIntensity: Float get() = CyberNightlightSwitch.intensity

    /**
     * 线性光（相对 SDR 白，已不含任何类型倍率，由 [encodePq] 产出 SDR 白基准）→ ST 2084 PQ 码值。
     *
     * 亮度倍率 = 类型设计峰值 [bias] 与滑块 [turboIntensity] 的线性插值：
     *   effMult = 1f + (bias - 1f) * (turboIntensity - 1f) / 7f
     *  → 滑块 1.0× 时 effMult = 1（恰 SDR 白，真·关闭，与开关关闭一致）
     *  → 滑块 8.0× 时 effMult = bias（保留原设计峰值，不再撞面板 8× 上限）
     * 这样滑块从 1.0× 拉到 8.0× 时贴片亮度线性连续变化，肉眼可辨（治 现象B）。
     */
    private fun pqEnc(lin: Float, bias: Float): Float {
        val effMult = 1f + (bias - 1f) * (turboIntensity - 1f) / 7f
        return pqOETF(lin * effMult)
    }

    override fun onSurfaceCreated(gl: javax.microedition.khronos.opengles.GL10?, config: javax.microedition.khronos.egl.EGLConfig?) {
        buildProgram()
        // ★ 现象A-2：EGL context（含旧 GL 纹理 id）已重建，清空所有 CPU 侧缓存，
        //   避免引用悬空旧纹理 id 导致 GL 错误累积 → native 崩。
        clearGpuCaches()
    }

    override fun onSurfaceChanged(gl: javax.microedition.khronos.opengles.GL10?, w: Int, h: Int) {
        surfaceW = w
        surfaceH = h
        // ★ pre13-F：尺寸变化(旋转/分屏/窗口化)不意味 EGL context 丢失
        //   (preserveEGLContextOnPause=true 下 context 持久)，纹理仍有效，不再全量删纹理重建，
        //   避免每次尺寸微变触发全量重传的卡顿尖峰与空帧（pre12 复查 R4）。
    }

    /**
     * ★ 现象A-2：清空并删除全部 GL 资源缓存。
     *  - glyphCache / bitmapTexCache / lineCache / quadBufCache：删对应 GL 纹理 + 清空 map。
     *  在 onSurfaceCreated / onSurfaceChanged（context 重建）以及 surface 销毁时调用，
     *  防止旧纹理 id 在 context 丢失后变成悬空引用 → GL 错误积累 → 闪退。
     */
    internal fun clearGpuCaches() {
        for ((_, e) in bitmapTexCache) {
            runCatching { GLES20.glDeleteTextures(1, intArrayOf(e.tex), 0) }
            runCatching { e.copy.recycle() }
        }
        for ((_, tex) in glyphCache) runCatching { GLES20.glDeleteTextures(1, intArrayOf(tex), 0) }
        bitmapTexCache.clear()
        glyphCache.clear()
        lineCache.clear()
        quadBufCache.clear()
        lineVertsBuf.clear()
        // ★ pre19-A：context 重建后旧 pending 任务引用的纹理 id / 位图均失效，一并清空
        pendingUploads.clear()
        pendingKeys.clear()
    }

    override fun onDrawFrame(gl: javax.microedition.khronos.opengles.GL10?) {
        try {
            drawFrameInternal(gl)
            // ★ pre21：替换任务自驱动——pendingUploads 非空且不在门控/失活态 → 安排下一帧，
            //   队列持续推进直到清空。pre20 的 bug：drain 只在 requestRender 触发（WHEN_DIRTY），
            //   若无新的 flow 发射（垂直滚动停止后数据刷新前的静止期），替换任务积压 →
            //   下一次刷新时旧纹理被替换/新纹理点亮"跳变" → 滚动停止瞬间的闪烁。
            //   自驱动后：滚动中队列消化快、停止时队列必空 → 停止后零渲染零闪烁。
            if (pendingUploads.isNotEmpty() && _enabled && egl.pqSurfaceActive && !scrollGated) {
                requestFrame?.invoke()
            }
            // 本帧无异常 → 清零连续错误计数（连续 ≥3 次异常才禁用，单次毛刺自动恢复）。
            consecutiveErrors = 0
        } catch (t: Throwable) {
            // ★ pre14-G4：GL 线程异常兜底——任何意外（位图异常/GL 错误升级/OOM）都不杀进程。
            //   清掉可能处于坏状态的缓存 + 输出透明帧；连续 ≥3 次才禁用贴片渲染，下次 toggle 恢复。
            consecutiveErrors++
            runCatching {
                GLES20.glClearColor(0f, 0f, 0f, 0f)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            }
            runCatching { clearGpuCaches() }
            if (consecutiveErrors >= 3) _enabled = false
            android.util.Log.e("PatchRenderer", "onDrawFrame error #$consecutiveErrors, HDR disabled=$_enabled", t)
        }
    }

    private fun drawFrameInternal(gl: javax.microedition.khronos.opengles.GL10?) {
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        val pq = egl.pqSurfaceActive
        val active = _enabled && pq && patches.isNotEmpty()
        // ★ 现象A-3：isHdrLayerObserved 结果缓存 1 秒（避免每帧跨进程/驱动查询加重抖动）。
        //   权威确认：Display.getHdrSdrRatio() > 1.01（API 34+）。
        val now = System.currentTimeMillis()
        if (now - cachedRatioTs >= RATIO_CACHE_MS) {
            cachedRatioOk = HdrCapabilityDetector.isHdrLayerObserved(display)
            cachedRatioTs = now
        }
        val ratioOk = pq && cachedRatioOk
        onState(pq, ratioOk)

        // ★ pre14-G2：翻页门控——只输出透明帧，不碰贴片/位图/纹理（关闭翻页期竞态窗口 + 纹理风暴）。
        if (scrollGated || !active) return

        // ★ pre20-c：预算自适应——垂直滚动中放大（消化速度 > 入队速度 → 停止时队列必空，
        //   停止后无"陆续入场"）；静止恢复 3/帧。
        // ★ pre19-A：本帧上传预算先用于补传上一帧入队的纹理任务，剩余额度供本帧新上传。
        uploadBudgetLeft =
            if (NightlightState.verticalScrolling) MAX_UPLOADS_PER_FRAME_SCROLLING else MAX_UPLOADS_PER_FRAME
        drainPendingUploads()

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
        // ★ pre19-C：删除分帧——每帧最多删 MAX_TEX_DELETES_PER_FRAME 个，
        //   避免收尾帧「上传 N + 删除 M + 绘制 ~40 贴片」三重叠加在同一帧。
        // ★ R2 修复：复用 liveIdSet（clear+add），消除每帧 patches.map{it.id}.toSet() 分配。
        liveIdSet.clear()
        for (p in patches) liveIdSet.add(p.id)
        var deletedThisFrame = 0
        val bit = bitmapTexCache.entries.iterator()
        while (bit.hasNext() && deletedThisFrame < MAX_TEX_DELETES_PER_FRAME) {
            val e = bit.next()
            if (e.key !in liveIdSet) {
                GLES20.glDeleteTextures(1, intArrayOf(e.value.tex), 0)
                runCatching { e.value.copy.recycle() }
                bit.remove()
                deletedThisFrame++
            }
        }
    }

    // ── ★ pre19-A：待上传纹理队列 ──

    private fun enqueueUpload(key: String, p: HdrPatch) {
        if (pendingKeys.add(key)) pendingUploads.addLast(Pair(key, p))
    }

    /** 补传上一帧入队的纹理任务（GL 线程，与绘制共用帧预算）。
     *  ★ pre20-a：替换任务（有旧纹理）传 budgeted=false 强制「先传新后删旧」无缝替换——
     *    若传 true 会命中"有旧纹理→入队返回旧"分支，导致任务永远不真正执行（入队→处理→再入队死循环）。
     *    全新任务同样强制上传（drain 本身已受 while(uploadBudgetLeft>0) 帧预算约束）。 */
    private fun drainPendingUploads() {
        while (uploadBudgetLeft > 0 && pendingUploads.isNotEmpty()) {
            val (key, ref) = pendingUploads.removeFirst()
            pendingKeys.remove(key)
            if (key.startsWith("g:")) {
                val gk = key.substring(2)
                // 字形纹理为共享 LRU：期间已被其他贴片补传则无需再传
                if (!glyphCache.containsKey(gk) && ref.text != null) {
                    if (ensureGlyph(ref, false) != null) uploadBudgetLeft--
                }
            } else {
                // 位图掩码纹理按 patch.id 键控：取当前快照里最新版本（期间坐标/文本可能已变）
                val cur = patches.firstOrNull { it.id == key } ?: continue
                if (cur.bitmap != null) {
                    if (ensureBitmapTex(cur, false) != null) uploadBudgetLeft--
                }
            }
        }
    }

    // ── 绘制 ──

    private fun drawSolid(p: HdrPatch) {
        bindQuad(p.id, rectVerts(p.bounds))
        GLES20.glUniform4f(uColor, pqEnc(p.color0[0], p.bias), pqEnc(p.color0[1], p.bias), pqEnc(p.color0[2], p.bias), 1f)
        GLES20.glUniform4f(uColor1, pqEnc(p.color0[0], p.bias), pqEnc(p.color0[1], p.bias), pqEnc(p.color0[2], p.bias), 1f)
        if (p.cornerRadiusPx > 0f) {
            // 圆角实心填充（进度条等）：复用 SDF round box，填充内部而非描边
            GLES20.glUniform1i(uMode, 3)
            GLES20.glUniform4f(uRect, p.bounds.left, p.bounds.top, p.bounds.width(), p.bounds.height())
            GLES20.glUniform1f(uCorner, p.cornerRadiusPx)
        } else {
            GLES20.glUniform1i(uMode, 0)
        }
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun drawSdfBorder(p: HdrPatch) {
        val b = p.bounds
        bindQuad(p.id, rectVerts(b))
        GLES20.glUniform4f(uColor, pqEnc(p.color0[0], p.bias), pqEnc(p.color0[1], p.bias), pqEnc(p.color0[2], p.bias), 1f)
        GLES20.glUniform4f(uColor1, pqEnc(p.color1[0], p.bias), pqEnc(p.color1[1], p.bias), pqEnc(p.color1[2], p.bias), 1f)
        GLES20.glUniform1i(uMode, 1)
        GLES20.glUniform4f(uRect, b.left, b.top, b.width(), b.height())
        GLES20.glUniform1f(uCorner, p.cornerRadiusPx)
        GLES20.glUniform1f(uStroke, p.strokeWidthPx)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun drawGlyph(p: HdrPatch) {
        // 优先使用 Compose 侧精确栅格化的位图掩码（文字/图标本体）；否则回退现场字形生成。
        // ★ pre14-G1：ensureBitmapTex 现可返回 null（源 recycle/copy 异常/预算耗尽入队），统一在此判空跳过。
        val texId = if (p.bitmap != null) ensureBitmapTex(p, true) else ensureGlyph(p, true)
        if (texId == null) return
        bindQuad(p.id, rectVerts(p.bounds))
        GLES20.glUniform4f(uColor, pqEnc(p.color0[0], p.bias), pqEnc(p.color0[1], p.bias), pqEnc(p.color0[2], p.bias), 1f)
        GLES20.glUniform1i(uMode, 2)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glUniform4f(uRect, p.bounds.left, p.bounds.top, p.bounds.width(), p.bounds.height())
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    /** 上传/复用 Compose 侧传入的位图掩码纹理（白色=不透明）。仅在 p.bitmap != null 时调用。
     *  ★ pre14-G1：返回 Int?，源位图已 recycle 或 copy 异常时返回 null（跳过该贴片而非闪退）。
     *  ★ pre19-A：budgeted=true（绘制路径）时受帧上传预算约束——未缓存且预算耗尽 → 入队返回 null
     *    （本帧跳过，SDR 本体仍在，后续帧补传点亮）；budgeted=false（drain 路径）强制实际上传/替换。
     *  ★ pre20-a：有旧纹理的贴片永不因预算离场——新位图到达时本帧继续画旧纹理（零离场）、
     *    新纹理入队后台替换；替换顺序「先传新、后删旧」，任何异常路径旧纹理仍在。
     *    消除 pre19 副作用：停止瞬间数字刷新/布局变化 → 旧纹理被删（离场）→ 新纹理等预算
     *    1-3 帧才点亮（入场）→ 视觉与水平翻页相同的"旧离场+新入场"。 */
    private fun ensureBitmapTex(p: HdrPatch, budgeted: Boolean): Int? {
        val bmp = p.bitmap ?: return null
        // 防御：源位图已被 recycle（交 GC 后不应发生，双保险）→ 跳过该贴片。
        if (bmp.isRecycled) return null
        val cached = bitmapTexCache[p.id]
        if (cached != null && cached.original === bmp) return cached.tex

        if (budgeted) {
            if (cached == null) {
                // 仅「从未上传过」的全新贴片受预算约束：耗尽则入队，本帧跳过
                if (uploadBudgetLeft <= 0) {
                    enqueueUpload(p.id, p)
                    return null
                }
                uploadBudgetLeft--
            } else {
                // ★ pre20-a：有旧纹理 → 本帧继续画旧纹理（零离场），新纹理入队后台替换
                if (!pendingKeys.contains(p.id)) enqueueUpload(p.id, p)
                return cached.tex
            }
        }
        // ★ pre13-R3：上传副本，与 Compose 生命周期解耦（源可能被 onDispose recycle，
        //   否则主线程 recycle 的实例恰是 GL 线程即将 texImage2D 的实例 → IllegalStateException）。
        val copy = try {
            bmp.copy(Bitmap.Config.ARGB_8888, false)
        } catch (t: Throwable) {
            // ★ pre14-G1：copy 瞬间源被回收等异常 → 有旧纹理则继续画旧，否则跳过该贴片
            return cached?.tex
        }
        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, copy, 0)
        // ★ pre20-a：先传新、后删旧——新纹理上传成功后才删旧纹理并回收旧副本
        if (cached != null) {
            GLES20.glDeleteTextures(1, intArrayOf(cached.tex), 0)
            runCatching { cached.copy.recycle() }
        }
        bitmapTexCache[p.id] = BitmapTex(bmp, copy, tex[0])
        return tex[0]
    }

    private fun drawGrid(p: HdrPatch) {
        val pts = p.points ?: return
        bindQuad(p.id, pts)
        GLES20.glUniform4f(uColor, pqEnc(p.color0[0], p.bias), pqEnc(p.color0[1], p.bias), pqEnc(p.color0[2], p.bias), 1f)
        GLES20.glUniform4f(uColor1, pqEnc(p.color0[0], p.bias), pqEnc(p.color0[1], p.bias), pqEnc(p.color0[2], p.bias), 1f)
        GLES20.glUniform1i(uMode, 0)
        GLES20.glLineWidth(1f)
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, pts.size / 2)
    }

    private fun drawLine(p: HdrPatch) {
        val geom = lineGeom(p)
        if (geom.lineVerts.isEmpty()) return
        bindQuad(p.id, geom.lineVerts)
        GLES20.glUniform4f(uColor, pqEnc(p.color0[0], p.bias), pqEnc(p.color0[1], p.bias), pqEnc(p.color0[2], p.bias), 1f)
        GLES20.glUniform4f(uColor1, pqEnc(p.color0[0], p.bias), pqEnc(p.color0[1], p.bias), pqEnc(p.color0[2], p.bias), 1f)
        GLES20.glUniform1i(uMode, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, geom.lineVerts.size / 2)
        // 尾点（实心圆 via SDF 全填充）
        if (geom.dotR > 0f) {
            bindQuad(p.id + ".dot", rectVerts(geom.dotBounds))
            GLES20.glUniform4f(uColor, pqEnc(p.color0[0], p.bias), pqEnc(p.color0[1], p.bias), pqEnc(p.color0[2], p.bias), 1f)
            GLES20.glUniform4f(uColor1, pqEnc(p.color0[0], p.bias), pqEnc(p.color0[1], p.bias), pqEnc(p.color0[2], p.bias), 1f)
            GLES20.glUniform1i(uMode, 1)
            GLES20.glUniform4f(uRect, geom.dotBounds.left, geom.dotBounds.top, geom.dotBounds.width(), geom.dotBounds.height())
            GLES20.glUniform1f(uCorner, geom.dotR)
            GLES20.glUniform1f(uStroke, geom.dotR * 2f)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }
    }

    // ── 几何 ──

    private fun rectVerts(b: RectF): FloatArray {
        tmpRectVerts[0] = b.left
        tmpRectVerts[1] = b.top
        tmpRectVerts[2] = b.right
        tmpRectVerts[3] = b.top
        tmpRectVerts[4] = b.left
        tmpRectVerts[5] = b.bottom
        tmpRectVerts[6] = b.right
        tmpRectVerts[7] = b.bottom
        return tmpRectVerts
    }

    private fun lineGeom(p: HdrPatch): LineGeom {
        val pts = p.points ?: return emptyGeom
        val cached = lineCache[p.id]
        if (cached != null && cached.first === pts) return cached.second

        val n = pts.size / 2
        if (n < 2) return emptyGeom
        val hw = (p.strokeWidthPx * 0.5f).coerceAtLeast(1.5f)
        // ★ R2 修复：顶点缓冲按 patch.id 复用，仅在容量变化（size != n*4）时重分配；
        //   注意用 != 而非 <（size 只可能等于或小于 n*4，> 会因重分配条件恒真而退化为每帧分配）。
        var verts = lineVertsBuf[p.id]
        if (verts == null || verts.size != n * 4) {
            verts = FloatArray(n * 4)
            lineVertsBuf[p.id] = verts
        }
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

    /**
     * ★ 现象A-3：绑定顶点缓冲。按 [id] 复用 FloatBuffer，仅在容量不足时 allocateDirect，
     *   否则 rewind + 覆盖内容 —— 杜绝每帧分配 direct buffer（GC 风暴主因）。
     */
    private fun bindQuad(id: String, verts: FloatArray) {
        var buf = quadBufCache[id]
        if (buf == null || buf.capacity() < verts.size) {
            val bb = ByteBuffer.allocateDirect(verts.size * 4).order(ByteOrder.nativeOrder())
            buf = bb.asFloatBuffer()
            quadBufCache[id] = buf
        }
        buf.clear()
        buf.put(verts)
        buf.position(0)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, buf)
    }

    // ── 字形纹理 ──

    private fun ensureGlyph(p: HdrPatch, budgeted: Boolean): Int? {
        val key = "${p.text}|${p.textSizePx.toInt()}"
        glyphCache[key]?.let { return it }
        val text = p.text ?: return null
        if (text.isEmpty()) return null
        // ★ pre19-A：未缓存需生成上传；预算耗尽 → 入队，本帧跳过（SDR 文本仍在）
        if (budgeted) {
            if (uploadBudgetLeft <= 0) {
                enqueueUpload("g:$key", p)
                return null
            }
            uploadBudgetLeft--
        }

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
        /** ★ pre19-A：静止时每帧允许的纹理上传数（含补传与新上传）。收尾帧风暴被摊到后续 3-5 帧，
         *  单帧上传成本封顶 ~10ms，肉眼不可辨。 */
        private const val MAX_UPLOADS_PER_FRAME = 3
        /** ★ pre20-c：垂直滚动中每帧上传预算放大（滚动中消化 > 入队 → 停止时队列必空）。 */
        private const val MAX_UPLOADS_PER_FRAME_SCROLLING = 6
        /** ★ pre19-C：每帧允许删除的离屏纹理数，避免删除风暴与上传/绘制同帧叠加。 */
        private const val MAX_TEX_DELETES_PER_FRAME = 2

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
                } else if (uMode == 3) {
                    vec2 center = uRect.xy + uRect.zw * 0.5;
                    vec2 p = vPix - center;
                    vec2 halfb = uRect.zw * 0.5;
                    float d = sdRoundBox(p, max(halfb, vec2(0.0)), max(uCorner, 0.0));
                    float cov = 1.0 - smoothstep(0.0, 1.5, d);
                    if (cov <= 0.001) discard;
                    outc = vec4(uColor.rgb, uColor.a * cov);
                }
                gl_FragColor = outc;
            }
        """
    }
}
