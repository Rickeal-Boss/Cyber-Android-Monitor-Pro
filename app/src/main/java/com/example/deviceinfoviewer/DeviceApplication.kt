package com.example.deviceinfoviewer

import android.app.Application
import android.os.Build
import android.os.Process
import android.util.Log
import com.example.deviceinfoviewer.di.appModule
import com.example.deviceinfoviewer.service.FloatingWindowConfig
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
    }

    override fun onCreate() {
        super.onCreate()

        // == 启动诊断: 仅主线程 Log.i，文件写入异步化 ==
        val startTime = System.currentTimeMillis()
        Log.i(TAG, "▶ STARTUP: enter onCreate | device=${Build.MODEL} sdk=${Build.VERSION.SDK_INT}")

        // ★ 崩溃 handler 提前设置 — 确保 Koin/FloatingWindowConfig 初始化异常也能捕获
        setupCrashHandler()

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

        // 悬浮窗配置初始化 (异步化 — 仅 SharedPreferences 读取，可后台)
        startupScope.launch {
            try {
                FloatingWindowConfig.init(this@DeviceApplication)
                Log.i(TAG, "▶ STARTUP: FloatingWindowConfig OK (async)")
            } catch (e: Throwable) {
                Log.e(TAG, "FloatingWindowConfig init failed", e)
            }
            // 异步写入启动阶段汇总日志
            writeStartupStage(startTime)
        }

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
            } catch (_: Throwable) {}

            oldHandler?.uncaughtException(t, e)
                ?: Process.killProcess(Process.myPid())
        }
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
