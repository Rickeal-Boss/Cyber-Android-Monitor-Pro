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
 * 悬浮窗前台服务 v2 — 8 种实时指标 + FPS
 * 修复: Choreographer 必须在主线程获取
 */
class FloatingWindowService : Service() {

    companion object {
        private const val TAG = "FloatWinSvc"
        private const val CHANNEL_ID = "floating_window"
        private const val NOTIF_ID = 1001
        private val BG_COLOR = android.graphics.Color.argb(220, 10, 10, 15)
        private val TEXT_COLOR = android.graphics.Color.argb(255, 160, 92, 255)
    }

    private var wm: WindowManager? = null
    private val windows = mutableMapOf<String, View?>()
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
            startFpsMonitor()
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
            startForeground(NOTIF_ID, buildNotification())
        } catch (t: Throwable) {
            Log.w(TAG, "startForeground failed", t)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val ch = NotificationChannel(CHANNEL_ID, "悬浮窗监控", NotificationManager.IMPORTANCE_LOW)
                getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
            } catch (_: Throwable) {}
        }
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("设备监控运行中")
        .setContentText("实时指标悬浮窗")
        .setSmallIcon(R.drawable.ic_app_logo)
        .setContentIntent(PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        .setOngoing(true).build()

    // ── 8 + 1 个独立窗口 ──
    private val itemDefs = listOf(
        "cpu_usage"    to { makeItem("CPU: --%", 16, 200) },
        "gpu_usage"    to { makeItem("GPU: --%", 16, 260) },
        "cpu_temp"     to { makeItem("CPU: --°C", 16, 320) },
        "gpu_temp"     to { makeItem("GPU: --°C", 16, 380) },
        "cpu_freq"     to { makeItem("频率: --MHz", 16, 440) },
        "ram"          to { makeItem("内存: --%", 16, 500) },
        "battery_temp" to { makeItem("电池: --°C", 16, 560) },
        "battery_cur"  to { makeItem("电流: --mA", 16, 620) },
        "fps"          to { makeItem("FPS: --", 16, 680) }
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
    private fun makeItem(initialText: String, x: Int, y: Int): View? {
        val tv = TextView(this).apply {
            text = initialText
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

        // 拖拽
        var initialX = 0; var initialY = 0; var initialTouchX = 0f; var initialTouchY = 0f
        tv.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x; initialY = params.y
                    initialTouchX = event.rawX; initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX; val dy = event.rawY - initialTouchY
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

        try { wm?.addView(tv, params) } catch (t: Throwable) {
            Log.w(TAG, "addView failed for $initialText", t); return null
        }
        return tv
    }

    private fun removeAllWindows() {
        windows.values.filterNotNull().forEach { try { wm?.removeView(it) } catch (_: Throwable) {} }
        windows.clear()
    }

    // ── 数据采集 & 更新 ──
    private fun startUpdating() {
        refreshRunnable = object : Runnable {
            override fun run() {
                if (!FloatingWindowConfig.enabled) { stopSelf(); return }
                Thread {
                    try {
                        val cpu = cpuDs.getCpuInfo()
                        val gpu = gpuDs.getGpuInfo()
                        val bat = batteryDs.getBatteryInfo()
                        val mem = memoryDs.getMemoryInfo()
                        handler.post { refreshData(cpu, gpu, bat, mem) }
                    } catch (_: Throwable) {}
                }.start()
                handler.postDelayed(this, 1000L)
            }
        }
        handler.post(refreshRunnable!!)
    }

    private fun refreshData(
        cpu: com.example.deviceinfoviewer.data.model.CpuInfo,
        gpu: com.example.deviceinfoviewer.data.model.GpuInfo,
        bat: com.example.deviceinfoviewer.data.model.BatteryInfo,
        mem: com.example.deviceinfoviewer.data.model.MemoryInfo
    ) {
        refreshVisibility()

        // CPU 利用率 — /proc/stat 差值计算
        val cpuUsage = cpu.cpuUsagePercent
        setText("cpu_usage", if (!cpuUsage.isNaN()) "CPU: ${cpuUsage.toInt()}%" else "CPU: --%")

        // GPU 利用率
        val gpuLoad = if (!gpu.loadPercentage.isNaN()) gpu.loadPercentage.toInt() else -1
        setText("gpu_usage", if (gpuLoad >= 0) "GPU: $gpuLoad%" else "GPU: --%")

        // CPU 温度
        val cpuTemp = if (!cpu.temperatureCelsius.isNaN()) cpu.temperatureCelsius.toInt() else -1
        setText("cpu_temp", if (cpuTemp > 0) "CPU: ${cpuTemp}°C" else "CPU: --°C")

        // GPU 温度
        val gpuTemp = if (!gpu.temperatureCelsius.isNaN()) gpu.temperatureCelsius.toInt() else -1
        setText("gpu_temp", if (gpuTemp > 0) "GPU: ${gpuTemp}°C" else "GPU: --°C")

        // CPU 频率 — 显示最高核心频率
        val maxCoreFreq = cpu.cores.maxOfOrNull { it.currentFreqKHz } ?: 0L
        setText("cpu_freq", if (maxCoreFreq > 0) {
            "CPU: ${"%.1f".format(maxCoreFreq / 1_000_000f)}GHz" } else "频率: --MHz")

        // 内存
        val ramPct = if (mem.totalKB > 0) (mem.usedKB * 100 / mem.totalKB).toInt() else -1
        setText("ram", if (ramPct >= 0) {
            val usedMB = mem.usedKB / 1024; val totalMB = mem.totalKB / 1024
            "内存: $ramPct% (${usedMB}MB/${totalMB}MB)"
        } else "内存: --%")

        // 电池温度
        val batTemp = if (!bat.temperatureCelsius.isNaN()) bat.temperatureCelsius.toInt() else -1
        setText("battery_temp", if (batTemp > 0) "电池: ${batTemp}°C" else "电池: --°C")

        // 电池电流 — 充放电功率
        val curText = when {
            bat.chargingPowerMw > 0 -> "充电: ${bat.chargingPowerMw}mA"
            bat.dischargingPowerMw > 0 -> "放电: ${bat.dischargingPowerMw}mA"
            bat.currentNow > 0 -> "电流: ${bat.currentNow}mA"
            else -> "电流: --mA"
        }
        setText("battery_cur", curText)
    }

    // ── FPS (修复: Choreographer 必须从主线程获取) ──
    private fun startFpsMonitor() {
        handler.post {
            try {
                val choreographer = android.view.Choreographer.getInstance()
                val frameCallback = object : android.view.Choreographer.FrameCallback {
                    override fun doFrame(frameTimeNanos: Long) {
                        if (lastFrameTimeNanos > 0) {
                            val deltaNs = frameTimeNanos - lastFrameTimeNanos
                            currentFps = (1_000_000_000.0 / deltaNs).toInt().coerceIn(0, 120)
                            (windows["fps"] as? TextView)?.text = "FPS: $currentFps"
                        }
                        lastFrameTimeNanos = frameTimeNanos
                        choreographer.postFrameCallback(this)
                    }
                }
                choreographer.postFrameCallback(frameCallback)
            } catch (t: Throwable) {
                Log.w(TAG, "FPS monitoring unavailable", t)
            }
        }
    }

    private fun setText(key: String, text: String) {
        (windows[key] as? TextView)?.text = text
    }

    private fun refreshVisibility() {
        mapOf(
            "cpu_usage"    to FloatingWindowConfig.showCpuUsage,
            "gpu_usage"    to FloatingWindowConfig.showGpuUsage,
            "cpu_temp"     to FloatingWindowConfig.showCpuTemp,
            "gpu_temp"     to FloatingWindowConfig.showGpuTemp,
            "cpu_freq"     to FloatingWindowConfig.showCpuFreq,
            "ram"          to FloatingWindowConfig.showRam,
            "battery_temp" to FloatingWindowConfig.showBatteryTemp,
            "battery_cur"  to FloatingWindowConfig.showBatteryCurrent,
            "fps"          to FloatingWindowConfig.showFps
        ).forEach { (k, v) ->
            windows[k]?.visibility = if (v) View.VISIBLE else View.GONE
        }
    }

    private fun stopUpdating() {
        refreshRunnable?.let { handler.removeCallbacks(it) }
    }
}
