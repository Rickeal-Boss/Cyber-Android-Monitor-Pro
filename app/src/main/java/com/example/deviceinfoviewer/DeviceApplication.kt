package com.example.deviceinfoviewer

import android.app.Application
import android.os.Build
import android.os.Process
import android.util.Log
import com.example.deviceinfoviewer.di.appModule
import com.example.deviceinfoviewer.service.FloatingWindowConfig
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
 */
class DeviceApplication : Application() {

    companion object {
        private const val TAG = "DeviceApp"
    }

    override fun onCreate() {
        super.onCreate()

        // == 阶段日志: 启动诊断 ==
        val startupFile = File(filesDir, "startup_stage.txt")
        fun logStage(msg: String) {
            Log.i(TAG, "▶ STARTUP: $msg")
            try { startupFile.writeText("${SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())} | $msg") } catch (_: Throwable) {}
        }
        logStage("enter onCreate")
        logStage("device=${Build.MODEL} sdk=${Build.VERSION.SDK_INT}")

        // Koin DI 初始化 (增强异常捕获)
        try {
            startKoin {
                androidLogger()
                androidContext(this@DeviceApplication)
                modules(appModule)
            }
            logStage("Koin DI OK")
        } catch (e: Throwable) {
            Log.e(TAG, "❌ Koin initialization FAILED", e)
            try {
                File(filesDir, "koin_error.log").writeText(
                    "Koin init failed at ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}\n" +
                    "Error: ${e.message}\n${e.stackTraceToString()}"
                )
            } catch (_: Throwable) {}
            logStage("Koin FAILED: ${e.message}")
        }

        // 悬浮窗配置初始化 (增强异常捕获)
        try {
            FloatingWindowConfig.init(this)
            logStage("FloatingWindowConfig OK")
        } catch (e: Throwable) {
            Log.e(TAG, "FloatingWindowConfig init failed", e)
            logStage("FloatingWindowConfig FAILED: ${e.message}")
        }

        logStage("crash handler setup")

        // 全局崩溃日志 (仅记录最近一次，不累积)
        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            Log.e(TAG, "=== FATAL CRASH ===", e)
            Log.e(TAG, "Thread: ${t.name} | SDK: ${Build.VERSION.SDK_INT}")

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
        logStage("onCreate done")
    }
}
