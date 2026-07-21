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
import com.example.deviceinfoviewer.DeviceApplication
import com.example.deviceinfoviewer.MainActivity
import com.example.deviceinfoviewer.R
import com.example.deviceinfoviewer.RefreshPolicy
import com.example.deviceinfoviewer.data.model.BatteryInfo
import com.example.deviceinfoviewer.data.model.CpuInfo
import com.example.deviceinfoviewer.data.model.GpuInfo
import com.example.deviceinfoviewer.data.model.MemoryInfo
import com.example.deviceinfoviewer.data.repository.DeviceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * 悬浮窗前台服务 v6 — 订阅 DeviceRepository SharedFlow
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
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var repo: DeviceRepository

    private var lastFrameTimeNanos = 0L
    private var currentFps = 0
    private var fpsCallback: android.view.Choreographer.FrameCallback? = null  // ★ 保存引用用于 remove
    // 预缓存静态标签
    private lateinit var gpuLabel: String
    private lateinit var cpuLabel: String
    private lateinit var ramLabel: String
    private lateinit var batLabel: String
    private lateinit var freqLabel: String
    private lateinit var chargingLabel: String
    private lateinit var dischargingLabel: String
    private lateinit var currentLabel: String
    private lateinit var powerLabel: String
    private lateinit var powerUp: String
    private lateinit var powerDown: String
    private lateinit var fpsLabel: String
    // ★ 性能优化 (2026-06-23): 预计算 CPU 频率标签数组 — 原 refreshAllMetrics 每 tick
    //   调用 8 次 getString(R.string.float_svc_cpu_freq_header, idx) 进行格式化，
    //   改为预计算标签数组后每次直接数组索引，减少 String.format 开销
    private val cpuFreqHeaders = Array(
        if (Runtime.getRuntime().availableProcessors() < 8) 8
        else Runtime.getRuntime().availableProcessors()
    ) { "" }
    private var collectionJob: kotlinx.coroutines.Job? = null

    // ★ 统一 baseTickMs，由 RefreshPolicy 驱动
    private var baseTickMs = RefreshPolicy.Tier.HIGH.defaultMs

    override fun onCreate() {
        super.onCreate()
        try {
            FloatingWindowConfig.init(this)
            repo = (application as DeviceApplication).deviceRepository
            // 预缓存静态标签
            gpuLabel = getString(R.string.float_svc_gpu_label)
            cpuLabel = getString(R.string.float_svc_cpu_label)
            ramLabel = getString(R.string.float_svc_ram_label)
            batLabel = getString(R.string.float_svc_battery_label)
            freqLabel = getString(R.string.float_svc_freq_label)
            chargingLabel = getString(R.string.float_svc_charging_label)
            dischargingLabel = getString(R.string.float_svc_discharging_label)
            currentLabel = getString(R.string.float_svc_current_label)
            powerLabel = getString(R.string.float_svc_power_label)
            powerUp = getString(R.string.float_svc_power_up)
            powerDown = getString(R.string.float_svc_power_down)
            fpsLabel = getString(R.string.float_svc_fps_label)
            // ★ 预计算 CPU 频率标签 — 按实际核心数（下限 8）填充，避免每 tick 重复 getString 格式化
            for (i in cpuFreqHeaders.indices) {
                cpuFreqHeaders[i] = getString(R.string.float_svc_cpu_freq_header, i)
            }

            createNotificationChannel()
            wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager

            // ★ 事件驱动: 观察启用/禁用开关
            serviceScope.launch {
                FloatingWindowConfig.enabledFlow.collect { isEnabled ->
                    if (!isEnabled) {
                        Log.d(TAG, "悬浮窗禁用，停止服务")
                        stopSelf()
                    }
                }
            }
            // ★ 事件驱动: 观察可见指标变更 → 立即刷新可见性
            serviceScope.launch {
                FloatingWindowConfig.visibleMetricsFlow.collect {
                    handler.post { refreshVisibility() }
                }
            }
            // ★ 综合刷新间隔: 用户设置 × 省电模式 (后台不再降频)
            serviceScope.launch {
                combine(
                    FloatingWindowConfig.refreshIntervalFlow,
                    RefreshPolicy.state,
                    RefreshPolicy.powerSaveModeFlow
                ) { userMs: Long, state: RefreshPolicy.RefreshState, powerSave: Boolean ->
                    val policyFloor = when {
                        powerSave -> RefreshPolicy.BACKGROUND_CAP_MS  // 省电模式: 强制 5s
                        else -> 500L  // 前后台均不降频
                    }
                    val effective = userMs.coerceIn(policyFloor, 5000L)
                    Log.d(TAG, "间隔更新: user=${userMs}ms state=$state powerSave=$powerSave → effective=${effective}ms")
                    effective
                }.collect { newTick ->
                    if (newTick != baseTickMs) {
                        baseTickMs = newTick
                        stopDataCollection()
                        startDataCollection()
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "onCreate failed", t)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startForegroundSafe()
            createAllWindows()
            startDataCollection()
            startFpsMonitor()
        } catch (t: Throwable) {
            Log.e(TAG, "onStartCommand failed", t)
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopFpsMonitor()  // ★ 必须在 serviceScope.cancel 前移除 Choreographer 回调
        windows.forEach { (key, view) ->
            val lp = view?.layoutParams as? WindowManager.LayoutParams ?: return@forEach
            FloatingWindowConfig.setWindowX(key, lp.x)
            FloatingWindowConfig.setWindowY(key, lp.y)
        }
        stopDataCollection()
        serviceScope.cancel()
        removeAllWindows()
        super.onDestroy()
    }

    override fun onBind(p0: Intent?): IBinder? = null

    private fun startForegroundSafe() {
        try { startForeground(NOTIF_ID, buildNotification()) }
        catch (t: Throwable) { Log.w(TAG, "startForeground failed", t) }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val ch = NotificationChannel(CHANNEL_ID, getString(R.string.float_svc_channel_name), NotificationManager.IMPORTANCE_LOW)
                getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
            } catch (e: Throwable) { Log.w(TAG, "通知渠道创建失败", e) }
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

    // ── 9 个独立窗口 ──
    private val itemDefs = listOf(
        "gpu_usage"    to { makeItem("gpu_usage", "$gpuLabel --%", 16, 200) },
        "cpu_temp"     to { makeItem("cpu_temp", "$cpuLabel --°C", 16, 320) },
        "gpu_temp"     to { makeItem("gpu_temp", "$gpuLabel --°C", 16, 380) },
        "cpu_freq"     to { makeItem("cpu_freq", cpuFreqHeaders.take(4).joinToString("\n") { "$it --MHz" }, 16, 440) },
        "ram"          to { makeItem("ram", "$ramLabel --%", 16, 620) },
        "battery_temp" to { makeItem("battery_temp", "$batLabel --°C", 16, 680) },
        "battery_cur"  to { makeItem("battery_cur", "$currentLabel --mA", 16, 740) },
        "battery_pow"  to { makeItem("battery_pow", "$powerLabel --W", 16, 800) },
        "fps"          to { makeItem("fps", "$fpsLabel --", 16, 860) }
    )

    @SuppressLint("MissingPermission")
    private fun createAllWindows() {
        itemDefs.forEach { (key, create) ->
            try {
                val view = create()
                windows[key] = view
                if (view != null) {
                    val lp = view.layoutParams as? WindowManager.LayoutParams
                    if (lp != null) {
                        lp.x = FloatingWindowConfig.getWindowX(key, lp.x)
                        lp.y = FloatingWindowConfig.getWindowY(key, lp.y)
                        wm?.updateViewLayout(view, lp)
                    }
                }
            } catch (t: Throwable) { Log.w(TAG, "创建窗口失败 $key", t) }
        }
        refreshVisibility()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun makeItem(key: String, initialText: String, x: Int, y: Int): View? {
        val tv = TextView(this).apply {
            text = initialText; textSize = 11f
            setTextColor(TEXT_COLOR); setBackgroundColor(BG_COLOR)
            setPadding(12, 6, 12, 6); alpha = 0.85f
        }
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    or WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; this.x = x; this.y = y }

        var initialX = 0; var initialY = 0; var initialTouchX = 0f; var initialTouchY = 0f
        tv.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x; initialY = params.y
                    initialTouchX = event.rawX; initialTouchY = event.rawY; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX; val dy = event.rawY - initialTouchY
                    if (kotlin.math.abs(dx) > 5 || kotlin.math.abs(dy) > 5) {
                        params.x = (initialX + dx).toInt()
                        params.y = (initialY + dy).toInt()
                        wm?.updateViewLayout(tv, params)
                    }; true
                }
                MotionEvent.ACTION_UP -> {
                    FloatingWindowConfig.setWindowX(key, params.x)
                    FloatingWindowConfig.setWindowY(key, params.y); false
                }
                else -> false
            }
        }
        try { wm?.addView(tv, params) } catch (t: Throwable) { Log.w(TAG, "addView 失败", t); return null }
        return tv
    }

    private fun removeAllWindows() {
        windows.values.filterNotNull().forEach { try { wm?.removeView(it) } catch (e: Throwable) {} }
        windows.clear()
    }

    private fun startDataCollection() {
        collectionJob = serviceScope.launch {
            combine(
                repo.cpuFlow,
                repo.gpuFlow,
                repo.batteryFlow,
                repo.memoryFlow
            ) { cpu, gpu, bat, mem ->
                refreshAllMetrics(cpu, gpu, bat, mem)
            }.collect()
        }
    }

    private fun stopDataCollection() {
        collectionJob?.cancel()
        collectionJob = null
    }

    private fun refreshAllMetrics(
        cpu: CpuInfo,
        gpu: GpuInfo,
        bat: BatteryInfo,
        mem: MemoryInfo
    ) {
        val gpuLoad = if (!gpu.loadPercentage.isNaN()) gpu.loadPercentage.toInt() else -1
        setText("gpu_usage", if (gpuLoad >= 0) "$gpuLabel $gpuLoad%" else "$gpuLabel --%")

        val cpuTemp = if (!cpu.temperatureCelsius.isNaN()) cpu.temperatureCelsius.toInt() else -1
        setText("cpu_temp", if (cpuTemp > 0) "$cpuLabel ${cpuTemp}°C" else "$cpuLabel --°C")

        val gpuTemp = if (!gpu.temperatureCelsius.isNaN()) gpu.temperatureCelsius.toInt() else -1
        setText("gpu_temp", if (gpuTemp > 0) "$gpuLabel ${gpuTemp}°C" else "$gpuLabel --°C")

        val allFreqs = cpu.cores.mapIndexed { idx, core ->
            val freqMHz = core.currentFreqKHz / 1000
            // ★ 使用预计算标签数组替代 getString 格式化
            val header = cpuFreqHeaders.getOrElse(idx) { "CPU$idx" }
            if (freqMHz > 0) "$header ${freqMHz}MHz"
            else "$header --MHz"
        }.take(8)
        setText("cpu_freq", if (allFreqs.isNotEmpty()) allFreqs.joinToString("\n") else "$freqLabel --MHz")

        val ramPct = if (mem.totalKB > 0) (mem.usedKB * 100 / mem.totalKB).toInt() else -1
        setText("ram", if (ramPct >= 0) {
            "$ramLabel $ramPct% (${mem.usedKB / 1024}MB/${mem.totalKB / 1024}MB)"
        } else "$ramLabel --%")

        val batTemp = if (!bat.temperatureCelsius.isNaN()) bat.temperatureCelsius.toInt() else -1
        setText("battery_temp", if (batTemp > 0) "$batLabel ${batTemp}°C" else "$batLabel --°C")

        // ★ 仅使用 currentNowUA (微安→毫安) — 这是真正的电流值
        //   chargingPowerMw/dischargingPowerMw 是功率 (毫瓦)，不可显示为 mA!
        setText("battery_cur", when {
            bat.currentNowUA > 0 -> "$chargingLabel ${bat.currentNowUA / 1000}mA"
            bat.currentNowUA < 0 -> "$dischargingLabel ${-bat.currentNowUA / 1000}mA"
            else -> "$currentLabel --mA"
        })

        val effV = bat.effectiveVoltage; val curUA = bat.currentNowUA
        if (effV > 0 && curUA != 0L) {
            val powerW = Math.abs(effV.toDouble() * curUA.toDouble()) / 1_000_000_000.0
            setText("battery_pow", "$powerLabel ${"%.2f".format(powerW)}W" +
                if (bat.isCharging) " $powerUp" else " $powerDown")
        } else setText("battery_pow", "$powerLabel --W")
    }

    // ── FPS (Choreographer 驱动) ──
    private fun startFpsMonitor() {
        handler.post {
            try {
                val choreographer = android.view.Choreographer.getInstance()
                fpsCallback = object : android.view.Choreographer.FrameCallback {
                    override fun doFrame(frameTimeNanos: Long) {
                        if (lastFrameTimeNanos > 0) {
                            currentFps = (1_000_000_000.0 / (frameTimeNanos - lastFrameTimeNanos)).toInt().coerceIn(0, 120)
                            (windows["fps"] as? TextView)?.text = "$fpsLabel $currentFps"
                        }
                        lastFrameTimeNanos = frameTimeNanos
                        choreographer.postFrameCallback(this)
                    }
                }
                choreographer.postFrameCallback(fpsCallback!!)
            } catch (t: Throwable) { Log.w(TAG, "FPS 不可用", t) }
        }
    }

    /** ★ 停止 FPS 监控 — 必须调用 removeFrameCallback 防止 Service 泄漏 */
    private fun stopFpsMonitor() {
        fpsCallback?.let { cb ->
            try { android.view.Choreographer.getInstance().removeFrameCallback(cb) }
            catch (t: Throwable) { Log.w(TAG, "removeFrameCallback failed", t) }
        }
        fpsCallback = null
    }

    private fun setText(key: String, text: String) {
        (windows[key] as? TextView)?.text = text
    }

    private fun refreshVisibility() {
        mapOf(
            "gpu_usage"    to FloatingWindowConfig.isVisible("gpu_usage"),
            "cpu_temp"     to FloatingWindowConfig.isVisible("cpu_temp"),
            "gpu_temp"     to FloatingWindowConfig.isVisible("gpu_temp"),
            "cpu_freq"     to FloatingWindowConfig.isVisible("cpu_freq"),
            "ram"          to FloatingWindowConfig.isVisible("ram"),
            "battery_temp" to FloatingWindowConfig.isVisible("battery_temp"),
            "battery_cur"  to FloatingWindowConfig.isVisible("battery_cur"),
            "battery_pow"  to FloatingWindowConfig.isVisible("battery_pow"),
            "fps"          to FloatingWindowConfig.isVisible("fps")
        ).forEach { (k, v) -> windows[k]?.visibility = if (v) View.VISIBLE else View.GONE }
    }
}
