package com.rb.cybermonitorpro.ui

import androidx.lifecycle.ViewModel
import com.rb.cybermonitorpro.RefreshPolicy
import com.rb.cybermonitorpro.data.repository.DeviceRepository
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AppViewModel — 全局监控生命周期管理
 * 在 SystemMonitorApp 级别启动，确保所有 Tab 都能获得实时数据
 *
 * 前后台统一刷新 (2026-06-21):
 * - RefreshPolicy 接管前后台调速，不再需要手动 changeInterval()
 * - DeviceRepository 内部观察 RefreshPolicy.state 自动调整
 * - changeInterval() 保留用于 Settings UI 模块间隔覆盖
 *
 * ★ F5 (2026-07-27): 监控生命周期绑定到 ViewModel (onCleared) 而非 Composable。
 *   屏幕旋转(配置变更)时 AppViewModel 实例保留但 DisposableEffect 会 dispose→re-enter,
 *   旧代码的 onDispose{ stopMonitoring() } 会触发 repo 全量重启 + 重读静态数据,
 *   导致数据空窗/闪烁。加 AtomicBoolean 去重守卫使 start 幂等、stop 仅真正退出时生效。
 */
class AppViewModel(
    private val repo: DeviceRepository
) : ViewModel() {

    // 去重守卫: 旋转重入 startMonitoring 时直接返回, 不重启采集、不重读静态数据
    private val started = AtomicBoolean(false)

    fun startMonitoring(intervalMs: Long = RefreshPolicy.Tier.NORMAL.defaultMs) {
        // 已启动直接返回 — 旋转(配置变更)重入时保证采集连续, 无空窗
        if (!started.compareAndSet(false, true)) return
        try {
            repo.startMonitoring(intervalMs)
            repo.loadStaticData()
        } catch (e: Throwable) {
            started.set(false)  // 启动失败回滚, 允许后续重试
            android.util.Log.w("AppVM", "监控启动失败", e)
        }
    }

    /**
     * 仅真正退出 (App/ViewModel 销毁) 时由 onCleared 调用。
     * 屏幕旋转等配置变更不调用本方法 — 监控持续运行, 避免数据空窗。
     */
    fun stopMonitoring() {
        if (!started.compareAndSet(true, false)) return
        repo.stopMonitoring()
    }

    /**
     * 动态调整采样间隔 — 用于 Settings UI 模块级别覆盖
     * 注意: 前后台切换已由 DeviceRepository 内部 RefreshPolicy 观察者自动处理
     */
    fun changeInterval(ms: Long) {
        try {
            repo.setIntervalMs(ms)
        } catch (e: Throwable) { android.util.Log.w("AppVM", "间隔调整失败", e) }
    }

    // 按 Tab 智能控制 GPS — 仅在 GPS/网络页面启用，离开时关闭以省电
    fun setGpsEnabled(enabled: Boolean) {
        if (enabled) repo.enableGps() else repo.disableGps()
    }

    override fun onCleared() {
        super.onCleared()
        repo.stopMonitoring()
    }
}
