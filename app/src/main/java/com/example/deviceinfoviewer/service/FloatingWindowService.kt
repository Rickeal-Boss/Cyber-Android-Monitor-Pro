package com.example.deviceinfoviewer.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.deviceinfoviewer.MainActivity
import com.example.deviceinfoviewer.R
import com.example.deviceinfoviewer.data.source.BatteryDataSource
import com.example.deviceinfoviewer.data.source.CpuDataSource
import com.example.deviceinfoviewer.data.source.GpuDataSource
import com.example.deviceinfoviewer.data.source.MemoryDataSource

/**
 * 悬浮窗前台服务 — 独立窗口拖拽 + FPS
 * 修复: 移除Compose Color依赖 + startForeground安全守卫
 */
class FloatingWindowService : Service() {

    companion object {
        private const val TAG = "FloatWinSvc"
        private const val CHANNEL_ID = "floating_window"
        private const val NOTIF_ID = 1001
        // 硬编码 Android 颜色（不依赖 Compose）
        private val BG_COLOR = android.graphics.Color.argb(220, 10, 10, 15)    // CyberBackground
        private val TEXT_COLOR = android.graphics.Color.argb(255, 160, 92, 255) // NeonPurpleBright
    }

    private var wm: WindowManager? = null
    private val windows = mutableMapOf<String, View>()
    private val handler = Handler(Looper.getMainLooper())
    private var refreshRunnable: Runnable? = null

    private lateinit var cpuDs: CpuDataSource
    private lateinit var gpuDs: GpuDataSource
    private lateinit var batteryDs: BatteryDataSource
    private lateinit var memoryDs: MemoryDataSource

    // FPS
    private var lastFrameTimeNanos = 0L
    private var currentFps = 0

    override fun onCreate() {
        super.onCreate()
        try {
            FloatingWindowConfig.init(this)
            cpuDs = CpuDataSource(applicationContext)
            gpuDs = GpuDataSource()
            batteryDs = BatteryDataSource(applicationContext)
            memoryDs = MemoryDataSource()
            createNotificationChannel()
            wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        } catch (t: Throwable) {
            Log.e(TAG, "onCreate failed", t)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startForegroundSafe()
            createAllWindows()
            startUpdating()
        } catch (t: Throwable) {
            Log.e(TAG, "onStartCommand failed", t)
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopUpdating()
        removeAllWindows()
        super.onDestroy()
    }

    override fun onBind(p0: Intent?): IBinder? = null

    // ── 安全 startForeground ──
    private fun startForegroundSafe() {
        try {
            val notif = buildNotification()
            startForeground(NOTIF_ID, notif)
        } catch (t: Throwable) {
            Log.w(TAG, "startForeground failed, continuing without notification", t)
        }
    }

    // ── 通知 ──
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val ch = NotificationChannel(CHANNEL_ID, "悬浮窗监控", NotificationManager.IMPORTANCE_LOW)
                getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
            } catch (_: Throwable) {}
        }
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("设备监控运行中").setContentText("各指标可独立拖动")
        .setSmallIcon(R.drawable.ic_app_logo)
        .setContentIntent(PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        .setOngoing(true).build()

    // ── 创建独立窗口 ──
    private val itemDefs = listOf(
        "cpu" to { makeItem("CPU:", x = 16, y = 200) },
        "gpu" to { makeItem("GPU:", x = 16, y = 260) },
        "battery" to { makeItem("电池:", x = 16, y = 320) },
        "memory" to { makeItem("内存:", x = 16, y = 380) },
        "temp" to { makeItem("温度:", x = 16, y = 440) },
        "network" to { makeItem("网络:", x = 16, y = 500) },
        "refresh" to { makeItem("Hz:", x = 16, y = 560) },
        "fps" to { makeItem("FPS:", x = 16, y = 620) }
    )

    @SuppressLint("MissingPermission")
    private fun createAllWindows() {
        itemDefs.forEach { (key, create) ->
            try { windows[key] = create() } catch (t: Throwable) {
                Log.w(TAG, "Failed to create window $key", t)
            }
        }
        refreshVisibility()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun makeItem(label: String, x: Int, y: Int): View? {
        val tv = TextView(this).apply {
            text = "$label ---"
            textSize = 11f
            setTextColor(TEXT_COLOR)
            setBackgroundColor(BG_COLOR)
            setPadding(12, 6, 12, 6)
            alpha = 0.85f
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    or WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; this.x = x; this.y = y }

        // 拖拽处理
        var initialX = 0; var initialY = 0; var initialTouchX = 0f; var initialTouchY = 0f

        tv.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x; initialY = params.y
                    initialTouchX = event.rawX; initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (kotlin.math.abs(dx) > 5 || kotlin.math.abs(dy) > 5) {
                        params.x = (initialX + dx).toInt()
                        params.y = (initialY + dy).toInt()
                        wm?.updateViewLayout(tv, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> false
                else -> false
            }
        }

        try {
            wm?.addView(tv, params)
        } catch (t: Throwable) {
            Log.w(TAG, "addView failed for $label — SYSTEM_ALERT_WINDOW may not be granted", t)
            return null
        }
        return tv
    }

    private fun removeAllWindows() {
        windows.values.forEach { try { wm?.removeView(it) } catch (_: Throwable) {} }
        windows.clear()
    }

    // ── 更新内容 ──
    private fun startUpdating() {
        refreshRunnable = object : Runnable {
            override fun run() {
                if (!FloatingWindowConfig.enabled) { stopSelf(); return }
                Thread {
                    val cpu = try { cpuDs.getCpuInfo() } catch (_: Throwable) { null }
                    val gpu = try { gpuDs.getGpuInfo() } catch (_: Throwable) { null }
                    val bat = try { batteryDs.getBatteryInfo() } catch (_: Throwable) { null }
                    val mem = try { memoryDs.getMemoryInfo() } catch (_: Throwable) { null }
                    handler.post { refreshData(cpu, gpu, bat, mem) }
                }.start()
                handler.postDelayed(this, 1000L)
            }
        }
        handler.post(refreshRunnable!!)

        // FPS
        Thread {
            try {
                val choreographer = android.view.Choreographer.getInstance()
                val frameCallback = object : android.view.Choreographer.FrameCallback {
                    override fun doFrame(frameTimeNanos: Long) {
                        if (lastFrameTimeNanos > 0) {
                            val deltaNs = frameTimeNanos - lastFrameTimeNanos
                            currentFps = (1_000_000_000.0 / deltaNs).toInt().coerceIn(0, 120)
                        }
                        lastFrameTimeNanos = frameTimeNanos
                        handler.post { updateFps() }
                        choreographer.postFrameCallback(this)
                    }
                }
                choreographer.postFrameCallback(frameCallback)
            } catch (_: Throwable) {}
        }.start()
    }

    private fun refreshData(cpu: Any?, gpu: Any?, bat: Any?, mem: Any?) {
        refreshVisibility()

        val ci = cpu as? com.example.deviceinfoviewer.data.model.CpuInfo
        val gi = gpu as? com.example.deviceinfoviewer.data.model.GpuInfo
        val bi = bat as? com.example.deviceinfoviewer.data.model.BatteryInfo
        val mi = mem as? com.example.deviceinfoviewer.data.model.MemoryInfo

        setText("cpu", ci?.let { "${it.architecture} ${it.coreCount}核" } ?: "CPU: ---")
        setText("gpu", "GPU: ${gi?.model ?: "---"}")
        setText("battery", bi?.let {
            val ch = if (it.isCharging) "\u2191" else "\u2193"
            "电池: ${it.levelPercent}% $ch"
        } ?: "电池: ---")
        setText("memory", mi?.let {
            "内存: ${it.usedKB * 100 / it.totalKB.coerceAtLeast(1)}%"
        } ?: "内存: ---")
        setText("temp", ci?.temperatureCelsius?.let {
            if (!it.isNaN()) "温度: ${it.toInt()}\u00b0C" else "温度: ---"
        } ?: "温度: ---")
        setText("network", "网络: ---")
        setText("refresh", "Hz: ---")
    }

    private fun updateFps() {
        (windows["fps"] as? TextView)?.text = "FPS: $currentFps"
    }

    private fun setText(key: String, text: String) {
        (windows[key] as? TextView)?.text = text
    }

    private fun refreshVisibility() {
        mapOf(
            "cpu" to FloatingWindowConfig.showCpu,
            "gpu" to FloatingWindowConfig.showGpu,
            "battery" to FloatingWindowConfig.showBattery,
            "memory" to FloatingWindowConfig.showMemory,
            "temp" to FloatingWindowConfig.showTemp,
            "network" to FloatingWindowConfig.showNetwork,
            "refresh" to FloatingWindowConfig.showRefreshRate,
            "fps" to FloatingWindowConfig.showFps
        ).forEach { (k, v) ->
            windows[k]?.visibility = if (v) View.VISIBLE else View.GONE
        }
    }

    private fun stopUpdating() {
        refreshRunnable?.let { handler.removeCallbacks(it) }
    }
}
