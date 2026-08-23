package com.rb.cybermonitorpro.ui.device

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.rb.cybermonitorpro.data.model.DeviceDetailInfo
import com.rb.cybermonitorpro.data.repository.DeviceRepository
import com.rb.cybermonitorpro.data.source.StepCounterStore
import com.rb.cybermonitorpro.ui.sensors.SensorDetailViewModel
import com.rb.cybermonitorpro.ui.sensors.StepUiState

class DeviceViewModel(
    private val repo: DeviceRepository,
    private val stepStore: StepCounterStore,
) : ViewModel() {

    val detail: LiveData<DeviceDetailInfo> get() = repo.deviceDetailLiveData

    // ── 步数状态 ──
    private val _stepUi = MutableLiveData<StepUiState>()
    val stepUi: LiveData<StepUiState> get() = _stepUi

    private val stepRateWindow = ArrayDeque<Pair<Long, Long>>()

    fun hasStepCounter(): Boolean = repo.hasStepCounter()

    /**
     * 进入页面调用 — 先 peek 上次已知账本（STEP_COUNTER 为 on-change，
     * 不走路不触发回调，避免卡片恒 "---"），再启动监听。
     */
    fun startStepMonitoring() {
        val ledger = stepStore.peekLedger()
        pushStepUi(ledger.totalSteps, ledger.todaySteps, ledger.stepsSinceBoot)
        repo.enableStepCounter { rawSinceBoot ->
            val l = stepStore.onHardwareReading(rawSinceBoot)
            pushStepUi(l.totalSteps, l.todaySteps, l.stepsSinceBoot)
        }
    }

    /** 离开页面调用 — 注销监听，避免后台耗电 */
    fun stopStepMonitoring() = repo.disableStepCounter()

    fun refreshDetail() = repo.refreshDeviceDetail()

    // ── 内部：账本结算 + 步频窗口 + 派生指标 ──
    private fun pushStepUi(total: Long, today: Long, sinceBoot: Long) {
        val now = System.currentTimeMillis()
        stepRateWindow.addLast(now to total)
        while (stepRateWindow.size > 64 ||
            (stepRateWindow.isNotEmpty() && now - stepRateWindow.first().first > 60_000L)) {
            stepRateWindow.removeFirst()
        }
        val ratePerMin = stepRateWindow.firstOrNull()?.let { (t0, total0) ->
            val dtMin = (now - t0) / 60000.0
            if (dtMin >= 0.05) (((total - total0) / dtMin).toInt().coerceAtLeast(0)) else 0
        } ?: 0
        val (distanceKm, caloriesKcal, activeMinutes) = estimateStepHealth(today)
        _stepUi.postValue(StepUiState(
            totalSteps = total, todaySteps = today, stepsSinceBoot = sinceBoot,
            fromDetector = false, ratePerMin = ratePerMin,
            distanceKm = distanceKm, caloriesKcal = caloriesKcal, activeMinutes = activeMinutes,
        ))
    }

    private fun estimateStepHealth(todaySteps: Long): Triple<Float?, Int?, Int?> {
        if (todaySteps <= 0L) return Triple(null, null, null)
        val f = todaySteps.toFloat()
        return Triple(f * SensorDetailViewModel.AVG_STRIDE_M / 1000f,
            (f * SensorDetailViewModel.KCAL_PER_STEP).toInt(),
            (f / SensorDetailViewModel.STEPS_PER_MIN).toInt())
    }

    override fun onCleared() {
        super.onCleared()
        repo.disableStepCounter()
    }
}
