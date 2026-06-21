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
        // 最后保存所有窗口位置
        windows.forEach { (key, view) ->
            val lp = view?.layoutParams as? WindowManager.LayoutParams ?: return@forEach
            FloatingWindowConfig.setWindowX(key, lp.x)
            FloatingWindowConfig.setWindowY(key, lp.y)
        }
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
                val ch = NotificationChannel(CHANNEL_ID, getString(R.string.float_svc_channel_name), NotificationManager.IMPORTANCE_LOW)
                getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
            } catch (e: Throwable) { Log.w(TAG, "创建通知渠道失败", e) }
        }
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle(getString(R.string.float_svc_notif_title))
        .setContentText(getString(R.string.float_svc_notif_text))
        .setSmallIcon(R.drawable.ic_app_logo)
        .setContentIntent(PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        .setOngoing(true).build()

    // ── 8 + 1 个独立窗口 ──
    private val itemDefs = listOf(
        "gpu_usage"    to { makeItem("gpu_usage", "${getString(R.string.float_svc_gpu_label)} --%", 16, 200) },
        "cpu_temp"     to { makeItem("cpu_temp", "${getString(R.string.float_svc_cpu_label)} --°C", 16, 320) },
        "gpu_temp"     to { makeItem("gpu_temp", "${getString(R.string.float_svc_gpu_label)} --°C", 16, 380) },
        "cpu_freq"     to { makeItem("cpu_freq", "${getString(R.string.float_svc_cpu_freq_header, 0)} --MHz\n${getString(R.string.float_svc_cpu_freq_header, 1)} --MHz\n${getString(R.string.float_svc_cpu_freq_header, 2)} --MHz\n${getString(R.string.float_svc_cpu_freq_header, 3)} --MHz", 16, 440) },
        "ram"          to { makeItem("ram", "${getString(R.string.float_svc_ram_label)} --%", 16, 620) },
        "battery_temp" to { makeItem("battery_temp", "${getString(R.string.float_svc_battery_label)} --°C", 16, 680) },
        "battery_cur"  to { makeItem("battery_cur", "${getString(R.string.float_svc_current_label)} --mA", 16, 740) },
        "battery_pow"  to { makeItem("battery_pow", "${getString(R.string.float_svc_power_label)} --W", 16, 800) },
        "fps"          to { makeItem("fps", "${getString(R.string.float_svc_fps_label)} --", 16, 860) }
    )

    @SuppressLint("MissingPermission")
    private fun createAllWindows() {
        itemDefs.forEach { (key, create) ->
            try {
                val view = create()
                windows[key] = view
                // 读取上次保存的位置
                if (view != null) {
                    val lp = view.layoutParams as? WindowManager.LayoutParams
                    if (lp != null) {
                        lp.x = FloatingWindowConfig.getWindowX(key, lp.x)
                        lp.y = FloatingWindowConfig.getWindowY(key, lp.y)
                        wm?.updateViewLayout(view, lp)
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to create window $key", t)
            }
        }
        refreshVisibility()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun makeItem(key: String, initialText: String, x: Int, y: Int): View? {
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
                MotionEvent.ACTION_UP -> {
                    // 保存拖拽后的位置
                    FloatingWindowConfig.setWindowX(key, params.x)
                    FloatingWindowConfig.setWindowY(key, params.y)
                    false
                }
                else -> false
            }
        }

        try { wm?.addView(tv, params) } catch (t: Throwable) {
            Log.w(TAG, "addView failed for $initialText", t); return null
        }
        return tv
    }

    private fun removeAllWindows() {
        windows.values.filterNotNull().forEach { try { wm?.removeView(it) } catch (e: Throwable) { Log.w(TAG, "移除悬浮窗失败", e) } }
        windows.clear()
    }

    // ── 数据采集 & 更新（每指标独立刷新间隔）──
    // 策略: 单基循环跑在最小配置间隔上，每轮采集全部数据，仅更新到期指标
    // 间隔变化下次循环自动生效，无需重启服务
    private val lastUpdateTimes = mutableMapOf<String, Long>()
    private var baseTickMs = 200L  // 基循环间隔，动态计算

    private fun startUpdating() {
        refreshRunnable = object : Runnable {
            override fun run() {
                if (!FloatingWindowConfig.enabled) { stopSelf(); return }
                // 重新计算基循环间隔 = 所有可见指标配置间隔的最小值（≥200ms 防过度采集）
                baseTickMs = computeBaseTickMs()
                Thread {
                    try {
                        val cpu = cpuDs.getCpuInfo()
                        val gpu = gpuDs.getGpuInfo()
                        val bat = batteryDs.getBatteryInfo()
                        val mem = memoryDs.getMemoryInfo()
                        handler.post { refreshDataSelective(cpu, gpu, bat, mem) }
                    } catch (e: Throwable) { Log.w(TAG, "悬浮窗数据刷新失败", e) }
                }.start()
                handler.postDelayed(this, baseTickMs)
            }
        }
        handler.post(refreshRunnable!!)
    }

    /** 计算所有可见指标的最小配置间隔 (下限 200ms) */
    private fun computeBaseTickMs(): Long {
        val intervals = mutableListOf<Long>()
        if (FloatingWindowConfig.showGpuUsage) intervals.add(FloatingWindowConfig.gpuUsageRefreshMs.toLong())
        if (FloatingWindowConfig.showCpuTemp) intervals.add(FloatingWindowConfig.cpuTempRefreshMs.toLong())
        if (FloatingWindowConfig.showGpuTemp) intervals.add(FloatingWindowConfig.gpuTempRefreshMs.toLong())
        if (FloatingWindowConfig.showCpuFreq) intervals.add(FloatingWindowConfig.cpuFreqRefreshMs.toLong())
        if (FloatingWindowConfig.showRam) intervals.add(FloatingWindowConfig.ramRefreshMs.toLong())
        if (FloatingWindowConfig.showBatteryTemp) intervals.add(FloatingWindowConfig.batteryTempRefreshMs.toLong())
        if (FloatingWindowConfig.showBatteryCurrent) intervals.add(FloatingWindowConfig.batteryCurRefreshMs.toLong())
        if (FloatingWindowConfig.showBatteryPower) intervals.add(FloatingWindowConfig.batteryPowRefreshMs.toLong())
        if (FloatingWindowConfig.showFps) intervals.add(FloatingWindowConfig.fpsRefreshMs.toLong())
        if (intervals.isEmpty()) return 1000L
        return intervals.min().coerceIn(200L, 30000L)
    }

    /** 检查某指标是否到达刷新时间，是则更新 lastUpdateTime 并返回 true */
    private fun shouldUpdate(key: String, intervalMs: Int): Boolean {
        val now = System.currentTimeMillis()
        val last = lastUpdateTimes[key] ?: 0L
        if (now - last >= intervalMs) {
            lastUpdateTimes[key] = now
            return true
        }
        return false
    }

    /** 仅更新已到期指标（替代原 refreshData） */
    private fun refreshDataSelective(
        cpu: com.example.deviceinfoviewer.data.model.CpuInfo,
        gpu: com.example.deviceinfoviewer.data.model.GpuInfo,
        bat: com.example.deviceinfoviewer.data.model.BatteryInfo,
        mem: com.example.deviceinfoviewer.data.model.MemoryInfo
    ) {
        refreshVisibility()

        // FPS 由 Choreographer 独立驱动，不受间隔控制
        // 其他 8 个指标按各自配置间隔选择性更新

        val gpuLoad = if (!gpu.loadPercentage.isNaN()) gpu.loadPercentage.toInt() else -1
        val gpuLabel = getString(R.string.float_svc_gpu_label)
        if (shouldUpdate("gpu_usage", FloatingWindowConfig.gpuUsageRefreshMs))
            setText("gpu_usage", if (gpuLoad >= 0) "$gpuLabel $gpuLoad%" else "$gpuLabel --%")

        val cpuTemp = if (!cpu.temperatureCelsius.isNaN()) cpu.temperatureCelsius.toInt() else -1
        val cpuLabel = getString(R.string.float_svc_cpu_label)
        if (shouldUpdate("cpu_temp", FloatingWindowConfig.cpuTempRefreshMs))
            setText("cpu_temp", if (cpuTemp > 0) "$cpuLabel ${cpuTemp}°C" else "$cpuLabel --°C")

        val gpuTemp = if (!gpu.temperatureCelsius.isNaN()) gpu.temperatureCelsius.toInt() else -1
        if (shouldUpdate("gpu_temp", FloatingWindowConfig.gpuTempRefreshMs))
            setText("gpu_temp", if (gpuTemp > 0) "$gpuLabel ${gpuTemp}°C" else "$gpuLabel --°C")

        val allFreqs = cpu.cores.mapIndexed { idx, core ->
            val freqMHz = core.currentFreqKHz / 1000
            if (freqMHz > 0) "${getString(R.string.float_svc_cpu_freq_header, idx)} ${freqMHz}MHz"
            else "${getString(R.string.float_svc_cpu_freq_header, idx)} --MHz"
        }.take(8)
        if (shouldUpdate("cpu_freq", FloatingWindowConfig.cpuFreqRefreshMs))
            setText("cpu_freq", if (allFreqs.isNotEmpty()) {
                allFreqs.joinToString("\n") } else "${getString(R.string.float_svc_freq_label)} --MHz")

        val ramPct = if (mem.totalKB > 0) (mem.usedKB * 100 / mem.totalKB).toInt() else -1
        val ramLabel = getString(R.string.float_svc_ram_label)
        if (shouldUpdate("ram", FloatingWindowConfig.ramRefreshMs))
            setText("ram", if (ramPct >= 0) {
                val usedMB = mem.usedKB / 1024; val totalMB = mem.totalKB / 1024
                "$ramLabel $ramPct% (${usedMB}MB/${totalMB}MB)"
            } else "$ramLabel --%")

        val batTemp = if (!bat.temperatureCelsius.isNaN()) bat.temperatureCelsius.toInt() else -1
        val batLabel = getString(R.string.float_svc_battery_label)
        if (shouldUpdate("battery_temp", FloatingWindowConfig.batteryTempRefreshMs))
            setText("battery_temp", if (batTemp > 0) "$batLabel ${batTemp}°C" else "$batLabel --°C")

        val chargingLabel = getString(R.string.float_svc_charging_label)
        val dischargingLabel = getString(R.string.float_svc_discharging_label)
        val currentLabel = getString(R.string.float_svc_current_label)
        if (shouldUpdate("battery_cur", FloatingWindowConfig.batteryCurRefreshMs)) {
            val curText = when {
                bat.currentNowUA > 0 -> "$chargingLabel ${bat.currentNowUA / 1000}mA"
                bat.currentNowUA < 0 -> "$dischargingLabel ${-bat.currentNowUA / 1000}mA"
                bat.chargingPowerMw > 0 -> "$chargingLabel ${bat.chargingPowerMw}mA"
                bat.dischargingPowerMw > 0 -> "$dischargingLabel ${bat.dischargingPowerMw}mA"
                else -> "$currentLabel --mA"
            }
            setText("battery_cur", curText)
        }

        val powerLabel = getString(R.string.float_svc_power_label)
        val powerUp = getString(R.string.float_svc_power_up)
        val powerDown = getString(R.string.float_svc_power_down)
        if (shouldUpdate("battery_pow", FloatingWindowConfig.batteryPowRefreshMs)) {
            val effV = bat.effectiveVoltage
            val curUA = bat.currentNowUA
            if (effV > 0 && curUA != 0L) {
                val powerW = Math.abs(effV.toDouble() * curUA.toDouble()) / 1_000_000_000.0
                val isChargingBat = bat.isCharging
                setText("battery_pow", "$powerLabel ${"%.2f".format(powerW)}W" +
                    if (isChargingBat) " $powerUp" else " $powerDown")
            } else {
                setText("battery_pow", "$powerLabel --W")
            }
        }
    }

    // ── FPS (Choreographer 驱动，按配置间隔更新视图) ──
    private fun startFpsMonitor() {
        handler.post {
            try {
                val choreographer = android.view.Choreographer.getInstance()
                val frameCallback = object : android.view.Choreographer.FrameCallback {
                    override fun doFrame(frameTimeNanos: Long) {
                        if (lastFrameTimeNanos > 0) {
                            val deltaNs = frameTimeNanos - lastFrameTimeNanos
                            currentFps = (1_000_000_000.0 / deltaNs).toInt().coerceIn(0, 120)
                            // 按配置间隔节流 TextView 更新，避免每帧无效 UI 刷新
                            if (shouldUpdate("fps", FloatingWindowConfig.fpsRefreshMs)) {
                                (windows["fps"] as? TextView)?.text = "FPS: $currentFps"
                            }
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
            "gpu_usage"    to FloatingWindowConfig.showGpuUsage,
            "cpu_temp"     to FloatingWindowConfig.showCpuTemp,
            "gpu_temp"     to FloatingWindowConfig.showGpuTemp,
            "cpu_freq"     to FloatingWindowConfig.showCpuFreq,
            "ram"          to FloatingWindowConfig.showRam,
            "battery_temp" to FloatingWindowConfig.showBatteryTemp,
            "battery_cur"  to FloatingWindowConfig.showBatteryCurrent,
            "battery_pow"  to FloatingWindowConfig.showBatteryPower,
            "fps"          to FloatingWindowConfig.showFps
        ).forEach { (k, v) ->
            windows[k]?.visibility = if (v) View.VISIBLE else View.GONE
        }
    }

    private fun stopUpdating() {
        refreshRunnable?.let { handler.removeCallbacks(it) }
    }
}
