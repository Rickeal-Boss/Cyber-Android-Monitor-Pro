package com.rb.cybermonitorpro

import android.app.Application
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.widget.Toast
import android.util.Log
import androidx.core.content.edit
import com.rb.cybermonitorpro.data.repository.DeviceRepository
import com.rb.cybermonitorpro.data.source.SysFsCapabilityProbe
import com.rb.cybermonitorpro.di.appModule
import com.rb.cybermonitorpro.service.FloatingWindowConfig
import com.rb.cybermonitorpro.ui.effects.CyberNightlightSwitch
import com.rb.cybermonitorpro.ui.effects.GlobalLightSwitch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * Application — Koin DI + 崩溃日志
 * DeviceRepository 由 Koin single{} 统一管理，不再手动单例
 *
 * 启动性能优化 (2026-06-19):
 * - startup_stage.txt / koin_error.log 文件 IO 异步化（后台 IO 协程），不阻塞 onCreate 主线程
 * - 崩溃 handler 提前到 Koin 之前（确保初始化异常也能捕获）
 * - FloatingWindowConfig.init 异步化（仅 SharedPreferences 读取，可后台）
 * - 移除每次 logStage 同步 writeText 的主线程阻塞（原方案 5 次同步文件写入）
 */
class DeviceApplication : Application() {

    companion object {
        private const val TAG = "DeviceApp"
        // ★ 启动诊断协程 scope — 后台 IO，不阻塞主线程
        private val startupScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        // ── 崩溃盾（治「闪退一次永久崩」现象A）──
        // 连续崩溃次数：每次未捕获崩溃 +1，每次 onCreate 干净跑完归零。
        private const val PREF_TURBOXDR_CRASH_STREAK = "turboxdr_crash_streak"
        // 触发自动关闭的连续崩溃阈值
        private const val CRASH_STREAK_THRESHOLD = 2
        // 距上次崩溃 5min 内再次启动也算崩溃循环候选（原 30s 过短，正常测试场景易被误杀）
        private const val CRASH_RECENT_MS = 300_000L
    }

    val deviceRepository: DeviceRepository by lazy {
        org.koin.core.context.GlobalContext.get().get()
    }

    // ★ 崩溃盾：共享 SP（与 AppSettings 同一文件，便于读写 TurboXDR/夜光条开关与崩溃计数）
    private val prefs: android.content.SharedPreferences by lazy {
        getSharedPreferences("device_info_viewer_settings", android.content.Context.MODE_PRIVATE)
    }

    override fun onCreate() {
        // ★ HCP-1 修复: 崩溃 handler 提到 super.onCreate 之前（最早生效点）。
        //   原位置在 super.onCreate 之后，导致 super.onCreate 及其之前的异常无法被
        //   自定义 handler 捕获、也不写 crash.log，表现为"静默闪退、无日志"。
        //   handler 仅注册 Thread.setDefaultUncaughtExceptionHandler（纯 JVM API，
        //   super 之前调用安全；其内部写 filesDir 的逻辑仅在崩溃发生时执行，彼时 context 已就绪）。
        setupCrashHandler()

        super.onCreate()

        // 电池 sysfs 探针 DataStore 后端注入 + 启动期异步预载 (P2原)
        // attach 仅取引用(主线程安全); preload 在 IO 协程读盘, 不阻塞 onCreate
        try {
            SysFsCapabilityProbe.attach(this@DeviceApplication)
            startupScope.launch { SysFsCapabilityProbe.preload() }
        } catch (e: Throwable) {
            Log.w(TAG, "SysFsCapabilityProbe attach/preload skipped", e)
        }

        // == 启动诊断: 仅主线程 Log.i，文件写入异步化 ==
        val startTime = System.currentTimeMillis()
        Log.i(TAG, "▶ STARTUP: enter onCreate | device=${Build.MODEL} sdk=${Build.VERSION.SDK_INT}")

        // Koin DI 初始化 (主线程同步，必须先于其他初始化)
        try {
            startKoin {
                androidLogger()
                androidContext(this@DeviceApplication)
                modules(appModule)
            }
            Log.i(TAG, "▶ STARTUP: Koin DI OK | ${System.currentTimeMillis() - startTime}ms")
        } catch (e: Throwable) {
            Log.e(TAG, "❌ Koin initialization FAILED", e)
            // 异步写入错误日志
            startupScope.launch { writeErrorLog("koin_error.log", "Koin init failed", e) }
        }

        // 悬浮窗配置初始化 — 必须同步调用，确保 UI 层访问 FloatingWindowConfig 时 prefs 已就绪
        // （异步化会导致竞态：setter 中的 prefs?.edit() 在 prefs==null 时静默跳过 SP 写入）
        try {
            FloatingWindowConfig.init(this@DeviceApplication)
            Log.i(TAG, "▶ STARTUP: FloatingWindowConfig OK (sync)")
        } catch (e: Throwable) {
            Log.e(TAG, "FloatingWindowConfig init failed", e)
        }

        // 全局光照总开关 — 从 AppSettings 注入 (SP 读取, 微秒级, 主线程安全)
        GlobalLightSwitch.enabled = AppSettings.getInstance(this@DeviceApplication).globalLightEnabled

        // ★ CyberNightlight TurboXDR — 从 AppSettings 注入运行期状态（局部 HDR 增亮贴片）
        val nightlightSettings = AppSettings.getInstance(this@DeviceApplication)
        // ★ 崩溃盾：若此前 TurboXDR/夜光条进入崩溃循环（连续崩溃或 30s 内崩溃），
        //   强制关掉两者并写回 SP + 注入 false，一次性治愈「闪退一次永久崩」。
        maybeAutoDisableTurboXdrOnCrashLoop(nightlightSettings)
        CyberNightlightSwitch.enabled = nightlightSettings.cyberNightlightTurboXdrEnabled
        CyberNightlightSwitch.intensity = nightlightSettings.cyberNightlightTurboXdrIntensity
        // 顶部夜光条独立开关（与 TurboXDR 解耦）
        com.rb.cybermonitorpro.ui.effects.NightlightBarSwitch.enabled =
            nightlightSettings.cyberNightlightBarEnabled

        // 启动阶段汇总日志异步写入（不阻塞主线程）
        startupScope.launch {
            writeStartupStage(startTime)
        }

        // ★ 崩溃盾：本次 onCreate 干净跑完 → 清除连续崩溃计数，并删除 crash.log，
        //   确保下次启动的 recentCrash 只反映真正的新崩溃（与清零对称，治 pre13-A 误杀）。
        prefs.edit { putInt(PREF_TURBOXDR_CRASH_STREAK, 0) }
        runCatching { File(filesDir, "crash.log").delete() }

        Log.i(TAG, "▶ STARTUP: onCreate done | ${System.currentTimeMillis() - startTime}ms")
    }

    /**
     * 全局崩溃日志 (仅记录最近一次，不累积)
     * 提前设置，确保后续初始化异常能捕获
     */
    private fun setupCrashHandler() {
        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            Log.e(TAG, "=== FATAL CRASH ===", e)
            Log.e(TAG, "Thread: ${t.name} | SDK: ${Build.VERSION.SDK_INT}")

            // 崩溃日志写入（崩溃流程中同步写，确保写入完成）
            try {
                val sw = StringWriter()
                PrintWriter(sw).use { pw ->
                    pw.println("=== CRASH ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())} ===")
                    pw.println("SDK=${Build.VERSION.SDK_INT} Device=${Build.MODEL}")
                    e.printStackTrace(pw)
                }
                File(filesDir, "crash.log").writeText(sw.toString())
                // ★ 崩溃盾：累计连续崩溃次数（每次崩溃 +1；干净跑完 onCreate 归零）
                val streak = prefs.getInt(PREF_TURBOXDR_CRASH_STREAK, 0) + 1
                prefs.edit { putInt(PREF_TURBOXDR_CRASH_STREAK, streak) }
            } catch (_: Throwable) {}

            oldHandler?.uncaughtException(t, e)
                ?: Process.killProcess(Process.myPid())
        }
    }

    /**
     * ★ 崩溃盾（治「闪退一次永久崩」现象A）：
     * 检测 TurboXDR/夜光条是否进入崩溃循环——连续崩溃次数 ≥ 阈值，或距上次崩溃 30s 内又启动。
     * 命中则强制关闭两者并写回 SP（保留用户原 SP 值由开关本身已持久化），注入 false，
     * 通过主线程 Toast 一次性提示「已自动关闭 TurboXDR」。返回 true=已强制关闭。
     *
     * 仅当原本开启时才动手（避免全新安装/本就关闭时误伤）；且只在真正命中循环时提示一次。
     */
    private fun maybeAutoDisableTurboXdrOnCrashLoop(s: AppSettings): Boolean {
        val streak = prefs.getInt(PREF_TURBOXDR_CRASH_STREAK, 0)
        val crashLog = File(filesDir, "crash.log")
        // ★ 仅「HDR 相关崩溃」才计入 recentCrash：避免任何无关崩溃(网络/IO/其他模块)误杀 HDR。
        val recentCrash = crashLog.exists() &&
            (System.currentTimeMillis() - crashLog.lastModified()) < CRASH_RECENT_MS &&
            isCrashHdrRelated(crashLog)
        val inLoop = streak >= CRASH_STREAK_THRESHOLD || recentCrash

        val wasOn = s.cyberNightlightTurboXdrEnabled || s.cyberNightlightBarEnabled
        if (!inLoop || !wasOn) return false
        // ★ 即便命中循环，若本次崩溃栈与 HDR 无关(不含 nightlight/PatchRenderer/GLSurfaceView 等)，
        //   也不强制关闭 HDR —— 只治「真·HDR 崩溃循环」，不误伤（治 pre12 复查 F1）。
        if (!isCrashHdrRelated(crashLog)) return false

        // 强制关闭，写回 SP；cyberNightlightTurboXdrIntensity 保留（用户可手动重开时沿用）
        s.cyberNightlightTurboXdrEnabled = false
        s.cyberNightlightBarEnabled = false
        // 注意：不重置 streak —— 保留抑制直到用户手动重开开关，避免「开了→崩→自动关→用户没动→下次又开」反复
        Handler(Looper.getMainLooper()).post {
            runCatching {
                Toast.makeText(
                    this@DeviceApplication,
                    "已自动关闭 TurboXDR（检测到 HDR 相关反复崩溃，可在设置中手动重新开启）",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        Log.w(TAG, "★ 崩溃盾触发：强制关闭 TurboXDR/夜光条 | streak=$streak recentCrash=$recentCrash")
        // ★ 自动关闭成功后删除 crash.log：避免 5min 窗口内反复测试被 recentCrash 反复触发（治 pre13-A）。
        runCatching { crashLog.delete() }
        return true
    }

    /**
     * ★ 崩溃盾（pre13 增强）：判断最近一次崩溃是否与局部 HDR 相关。
     * 读取 [crash.log] 文本，若栈中含 nightlight / PatchRenderer / HdrPatch / GLSurfaceView /
     * GLThread / Lume 等关键字，则视为 HDR 相关崩溃，才允许触发自动关闭；
     * 否则视为无关崩溃（网络/IO/其他模块），不误杀 HDR（治 pre12 复查 F1）。
     * crash.log 不存在或读取失败 → 返回 false（如 native 崩溃不会写 Java crash.log，不误触发）。
     */
    private fun isCrashHdrRelated(crashLog: File): Boolean {
        return runCatching {
            val lower = crashLog.readText().lowercase()
            lower.contains("nightlight") || lower.contains("patchrenderer") ||
                lower.contains("hdrpatch") || lower.contains("hdrlu") ||
                lower.contains("glsurfaceview") || lower.contains("glthread") ||
                lower.contains("lumerenderer") || lower.contains("hdr")
        }.getOrDefault(false)
    }

    /**
     * 异步写入启动阶段日志（诊断用，不阻塞主线程）
     */
    private fun writeStartupStage(startTime: Long) {
        try {
            val elapsed = System.currentTimeMillis() - startTime
            val content = "${SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())} | " +
                    "onCreate done | total=${elapsed}ms | device=${Build.MODEL} sdk=${Build.VERSION.SDK_INT}"
            File(filesDir, "startup_stage.txt").writeText(content)
        } catch (_: Throwable) {}
    }

    /**
     * 异步写入错误日志
     */
    private fun writeErrorLog(fileName: String, prefix: String, e: Throwable) {
        try {
            val content = "$prefix at ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}\n" +
                    "Error: ${e.message}\n${e.stackTraceToString()}"
            File(filesDir, fileName).writeText(content)
        } catch (_: Throwable) {}
    }
}
