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

        // Koin DI 初始化
        startKoin {
            androidLogger()
            androidContext(this@DeviceApplication)
            modules(appModule)
        }

        // 悬浮窗配置初始化
        FloatingWindowConfig.init(this)

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
                // 覆盖写入，不累积
                File(filesDir, "crash.log").writeText(sw.toString())
            } catch (_: Throwable) {}

            oldHandler?.uncaughtException(t, e)
                ?: Process.killProcess(Process.myPid())
        }
    }
}
