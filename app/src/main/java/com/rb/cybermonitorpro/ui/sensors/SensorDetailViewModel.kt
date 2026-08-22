package com.rb.cybermonitorpro.ui.sensors

import android.hardware.Sensor
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import com.rb.cybermonitorpro.AppSettings
import com.rb.cybermonitorpro.data.model.HistoryDataPoint
import com.rb.cybermonitorpro.data.model.SensorItemInfo
import com.rb.cybermonitorpro.data.model.SensorLiveData
import com.rb.cybermonitorpro.data.model.SensorTypeMeta
import com.rb.cybermonitorpro.data.repository.DeviceRepository
import com.rb.cybermonitorpro.data.source.AltitudeComputer
import com.rb.cybermonitorpro.data.source.StepCounterStore

/** 气压海拔 UI 状态（PRESSURE 详情页 PressureAltimeterCard 消费） */
data class AltitudeUiState(
    val emaPressureHpa: Double,
    val relativeAltitudeM: Double,   // 相对模式海拔（参考点 = 用户标定 EMA 气压，未标定回退海平面）
    val absoluteAltitudeM: Double,   // 绝对模式海拔（P0 = GPS 反算标定值，未标定回退 1013.25 hPa）
    val rateMPerMin: Double?,        // 升降速率 (m/min)，样本不足时 null
    val referenceSet: Boolean,
    val gpsCalibrated: Boolean,
)

/** 步数 UI 状态（STEP_COUNTER / STEP_DETECTOR 详情页 StepCounterCard 消费） */
data class StepUiState(
    val totalSteps: Long,            // 跨重启累计
    val todaySteps: Long,            // 今日
    val stepsSinceBoot: Long,        // 本次开机
    val fromDetector: Boolean,       // true = STEP_DETECTOR 降级（仅监听期计数）
    val ratePerMin: Int,             // 最近 60s 步频（步/分钟）
)

/**
 * 传感器详情页 ViewModel
 * 管理单个传感器的实时数据采集和静态信息展示；
 * PRESSURE 追加气压海拔分支，STEP_COUNTER/STEP_DETECTOR 追加步数账本分支（按需启停，
 * 生命周期跟随详情页进入/离开的 startListening/stopListening）。
 */
class SensorDetailViewModel(
    private val repo: DeviceRepository,
    private val stepStore: StepCounterStore,
    private val settings: AppSettings,
) : ViewModel() {

    val liveData: LiveData<SensorLiveData> get() = repo.sensorLiveData
    val sensorHistoryData get() = repo.sensorHistoryData

    private var currentSensor: SensorItemInfo? = null
    private var currentMeta: SensorTypeMeta? = null

    // ── 气压海拔状态（PRESSURE）──
    private val _altitudeUi = MutableLiveData<AltitudeUiState>()
    val altitudeUi: LiveData<AltitudeUiState> get() = _altitudeUi
    private var pressureEma: Double? = null
    // 升降速率基准: 仅在每秒计算速率时更新, 不随每个样本刷新
    private var rateBaseAltM: Double? = null
    private var rateBaseAtMs: Long = 0L
    private var rateEma: Double? = null   // 速率 EMA 平滑 (α=0.3), 抑制气压噪声抖动

    // ── 步数状态（STEP_COUNTER / STEP_DETECTOR）──
    private val _stepUi = MutableLiveData<StepUiState>()
    val stepUi: LiveData<StepUiState> get() = _stepUi
    private var detectorBaseTotal: Long = 0L   // DETECTOR 降级：进入页时的账本总量
    private var detectorSessionSteps: Long = 0L
    // 60s 步频窗口: (timestampMs, totalSteps)
    private val stepRateWindow = ArrayDeque<Pair<Long, Long>>()

    // GPS 校准按需开启的监听（离开页面时回停，避免常驻耗电）
    private var gpsEnabledForCalibration = false

    fun getSensorInfo(): SensorItemInfo? = currentSensor
    fun getSensorMeta(): SensorTypeMeta? = currentMeta

    /**
     * 进入详情页时调用 — 启动传感器实时监听
     */
    fun startListening(sensor: SensorItemInfo) {
        currentSensor = sensor
        currentMeta = SensorTypeMeta.fromTypeId(sensor.type)
        if (sensor.type == Sensor.TYPE_STEP_DETECTOR) {
            detectorBaseTotal = stepStore.lastKnownTotal()
            detectorSessionSteps = 0L
        }
        repo.enableSensor(sensor.type)
    }

    /**
     * 离开详情页时调用 — 立即停止传感器监听
     */
    fun stopListening() {
        if (gpsEnabledForCalibration) {
            gpsEnabledForCalibration = false
            runCatching { repo.disableGps() }
        }
        repo.disableSensor()
        currentSensor = null
        currentMeta = null
    }

    /**
     * 详情页采样回调 — 由 Screen 的 snapshotFlow 消费管线按类型分发（按需启停：页面在才喂样）
     */
    fun onSample(data: SensorLiveData) {
        when (data.sensorType) {
            Sensor.TYPE_PRESSURE -> onPressureSample(data.x.toDouble(), data.timestampMs)
            Sensor.TYPE_STEP_COUNTER -> {
                val ledger = stepStore.onHardwareReading(data.x.toLong())
                pushStepUi(ledger.totalSteps, ledger.todaySteps, ledger.stepsSinceBoot, fromDetector = false)
            }
            Sensor.TYPE_STEP_DETECTOR -> {
                // 降级路径: 检测器每步一事件(值 1.0)，仅监听期计数
                // TODO(语义): 降级路径显示的为会话内步数, 非真实今日步数
                detectorSessionSteps++
                val total = detectorBaseTotal + detectorSessionSteps
                pushStepUi(total, detectorSessionSteps, detectorSessionSteps, fromDetector = true)
            }
        }
    }

    /**
     * 获取格式化的实时数值字符串
     */
    fun formatValue(data: SensorLiveData?, index: Int): String {
        val meta = currentMeta ?: return "---"
        data ?: return "---"
        if (index >= data.valueCount) return "---"
        val v = data.values[index]
        if (v.isNaN()) return "---"

        return when (meta) {
            SensorTypeMeta.ORIENTATION,
            SensorTypeMeta.GYROSCOPE,
            SensorTypeMeta.GYROSCOPE_UNCALIBRATED,
            SensorTypeMeta.GYROSCOPE_LIMITED_AXES,
            SensorTypeMeta.GYROSCOPE_LIMITED_AXES_UNCALIBRATED -> "%.4f".format(v)
            SensorTypeMeta.ROTATION_VECTOR,
            SensorTypeMeta.GAME_ROTATION_VECTOR,
            SensorTypeMeta.GEOMAGNETIC_ROTATION_VECTOR -> "%.6f".format(v)
            SensorTypeMeta.STEP_COUNTER,
            SensorTypeMeta.STEP_DETECTOR,
            SensorTypeMeta.SIGNIFICANT_MOTION,
            SensorTypeMeta.HEART_RATE,
            SensorTypeMeta.STATIONARY_DETECT,
            SensorTypeMeta.MOTION_DETECT,
            SensorTypeMeta.LOW_LATENCY_OFFBODY_DETECT -> "%.0f".format(v)
            SensorTypeMeta.PRESSURE,
            SensorTypeMeta.HUMIDITY,
            SensorTypeMeta.AMBIENT_TEMPERATURE,
            SensorTypeMeta.TEMPERATURE,
            SensorTypeMeta.HINGE_ANGLE,
            SensorTypeMeta.HEADING -> "%.1f".format(v)
            else -> "%.2f".format(v)
        }
    }

    // ═══════ 气压海拔分支 ═══════

    private fun onPressureSample(pHpa: Double, eventTsMs: Long) {
        if (pHpa.isNaN() || pHpa <= 0.0) return
        val ema = AltitudeComputer.ema(pHpa, pressureEma)
        pressureEma = ema

        val refP0 = settings.baroReferenceP0Hpa.takeIf { it > 0f }?.toDouble()
            ?: AltitudeComputer.SEA_LEVEL_HPA
        val relative = AltitudeComputer.pressureToAltitudeMeters(ema, refP0)

        val gpsP0 = settings.baroGpsCalibratedP0Hpa.takeIf { it > 0f }?.toDouble()
            ?: AltitudeComputer.SEA_LEVEL_HPA
        val absolute = AltitudeComputer.pressureToAltitudeMeters(ema, gpsP0)

        // 使用传感器事件时间戳 (elapsedRealtime 基准, 单调递增), 避免墙钟 NTP 跳变影响速率
        val now = if (eventTsMs > 0L) eventTsMs else System.currentTimeMillis()
        var rate: Double? = null
        // ★ 修复: 原实现 lastAltitudeAtMs 每样本刷新, 导致 1s 阈值永远不满足,
        //   速率恒为 null (UI 显示 "---"). 改为独立基准点, 每秒计算一次.
        val baseAlt = rateBaseAltM
        if (baseAlt == null) {
            rateBaseAltM = relative
            rateBaseAtMs = now
        } else if (now - rateBaseAtMs >= 1000L) {
            val dtMin = (now - rateBaseAtMs) / 60000.0
            if (dtMin > 0.0) {
                val rawRate = (relative - baseAlt) / dtMin
                // EMA 平滑 (α=0.3): 1s 间隔原始速率仍有 ±0.1 hPa 噪声 → ±8 m/min 抖动
                rateEma = rateEma?.let { 0.3 * rawRate + 0.7 * it } ?: rawRate
                rate = rateEma
            }
            rateBaseAltM = relative
            rateBaseAtMs = now
        } else {
            rate = rateEma   // 间隔内复用上一次平滑值, UI 不闪 "---"
        }

        _altitudeUi.postValue(
            AltitudeUiState(
                emaPressureHpa = ema,
                relativeAltitudeM = relative,
                absoluteAltitudeM = absolute,
                rateMPerMin = rate,
                referenceSet = settings.baroReferenceP0Hpa > 0f,
                gpsCalibrated = settings.baroGpsCalibratedP0Hpa > 0f,
            )
        )
    }

    /** 「设为参考点」: 当前 EMA 气压 → 相对零点。返回 false = 尚无气压样本 */
    fun calibrateRelative(): Boolean {
        val ema = pressureEma ?: return false
        settings.baroReferenceP0Hpa = ema.toFloat()
        recomputeAltitude()
        return true
    }

    /**
     * 「GPS 校准」: GPS 海拔反算 P0 → 绝对海拔基准。
     * 返回 false = 无 GPS 海拔（会顺手按需开启 GPS 监听，稍后重试）或反算失败。
     */
    fun calibrateFromGps(): Boolean {
        val ema = pressureEma ?: run {
            return false
        }
        val gps = repo.gpsLiveData.value
        val gpsAlt = gps?.altitude ?: Double.NaN
        if (gps == null || gpsAlt.isNaN() || !gps.fixAcquired) {
            // 按需开启 GPS（离开页面时回停），下次定点成功后再校准
            if (!gpsEnabledForCalibration) {
                gpsEnabledForCalibration = true
                runCatching { repo.enableGps() }
            }
            return false
        }
        // ★ GPS 海拔精度守卫: 水平精度 > 20m 时垂直误差通常 > 30m,
        //   反算 P0 偏差可达 0.4 hPa → 海拔偏差 > 3m, 拒绝标定并提示重试.
        //   accuracy=NaN (部分 NETWORK_PROVIDER 定位) 同样拒绝.
        if (gps.accuracy.isNaN() || gps.accuracy > GPS_CALIB_MAX_ACCURACY_M) {
            if (!gpsEnabledForCalibration) {
                gpsEnabledForCalibration = true
                runCatching { repo.enableGps() }
            }
            return false
        }
        val p0 = AltitudeComputer.altitudeToP0Hpa(ema, gpsAlt)
        if (p0.isNaN()) return false
        settings.baroGpsCalibratedP0Hpa = p0.toFloat()
        recomputeAltitude()
        return true
    }

    /** 校准后立即用当前 EMA 重算（不引入新样本） */
    private fun recomputeAltitude() {
        val ema = pressureEma ?: return
        val refP0 = settings.baroReferenceP0Hpa.takeIf { it > 0f }?.toDouble()
            ?: AltitudeComputer.SEA_LEVEL_HPA
        val relative = AltitudeComputer.pressureToAltitudeMeters(ema, refP0)
        // 校准改变了海拔零点, 重置速率基准避免下一帧产生假爬升/下降
        // 使用 elapsedRealtime 与 SensorEvent.timestamp 同一时钟域
        rateBaseAltM = relative
        rateBaseAtMs = android.os.SystemClock.elapsedRealtime()
        rateEma = null
        val gpsP0 = settings.baroGpsCalibratedP0Hpa.takeIf { it > 0f }?.toDouble()
            ?: AltitudeComputer.SEA_LEVEL_HPA
        _altitudeUi.postValue(
            AltitudeUiState(
                emaPressureHpa = ema,
                relativeAltitudeM = relative,
                absoluteAltitudeM = AltitudeComputer.pressureToAltitudeMeters(ema, gpsP0),
                rateMPerMin = null,
                referenceSet = settings.baroReferenceP0Hpa > 0f,
                gpsCalibrated = settings.baroGpsCalibratedP0Hpa > 0f,
            )
        )
    }

    // ═══════ 步数分支 ═══════

    private fun pushStepUi(total: Long, today: Long, sinceBoot: Long, fromDetector: Boolean) {
        val now = System.currentTimeMillis()
        stepRateWindow.addLast(now to total)
        while (stepRateWindow.size > 64 || (stepRateWindow.isNotEmpty() && now - stepRateWindow.first().first > 60_000L)) {
            stepRateWindow.removeFirst()
        }
        val ratePerMin = stepRateWindow.firstOrNull()?.let { (t0, total0) ->
            val dtMin = (now - t0) / 60000.0
            if (dtMin >= 0.05) (((total - total0) / dtMin).toInt().coerceAtLeast(0)) else 0
        } ?: 0
        _stepUi.postValue(
            StepUiState(
                totalSteps = total,
                todaySteps = today,
                stepsSinceBoot = sinceBoot,
                fromDetector = fromDetector,
                ratePerMin = ratePerMin,
            )
        )
    }

    override fun onCleared() {
        super.onCleared()
        repo.disableSensor()
    }

    companion object {
        /** GPS 校准允许的最大水平精度 (m); 超过此值认为 GPS 海拔不可靠 */
        private const val GPS_CALIB_MAX_ACCURACY_M = 20f
    }
}
