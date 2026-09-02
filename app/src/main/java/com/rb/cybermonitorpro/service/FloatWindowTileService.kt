package com.rb.cybermonitorpro.service

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.rb.cybermonitorpro.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * ★ 悬浮窗快捷磁贴 (2026-09-02) — 状态栏 QS 面板总开关, API 24+
 *
 * 语义: 磁贴 = 悬浮窗总开关 (FloatingWindowConfig.enabled 持久化),
 * 与 App 内开关完全同权; 与通知栏 ACTION_TOGGLE 的"临时显隐"是两层语义
 * (临时显隐不写 enabled, 见 FloatingWindowService 注释), 磁贴不追踪后者。
 *
 * 启停管线复用现有链路:
 * - 开: enabled=true + 启动 FloatingWindowService → onStartCommand 裸 intent 路径
 *   (startForegroundSafe → createAllWindows → startDataCollection → startFpsMonitor 全链就位)
 * - 关: enabled=false + stopService 双路径幂等 (Service 侧 enabledFlow 观察者自动 stopSelf 兜底)
 *
 * ① 后台启动: 磁贴点击时 App 多半在后台 → API 26+ 用 startForegroundService
 *   (onStartCommand 首行即 startForegroundSafe, 5 秒规则天然满足);
 *   API 24/25 无 FGS 后台限制, 走 startService。失败 (个别 OEM 后台限制) 时
 *   回滚 enabled 并 Toast 提示, 不留"开关已开但窗口没出现"的假状态。
 *
 * ② Overlay 权限降级: 磁贴无法复用 UI 的"跳设置页 + ON_RESUME 复查"流程
 *   (磁贴无 Activity 生命周期), 无权限时置 STATE_UNAVAILABLE 灰掉 +
 *   Toast 复用现有 float_permission_toast 文案引导进 App 授权一次, 之后永久可用。
 *
 * ③ 状态同步: onStartListening (面板展开) 读 enabled 刷新 + 订阅 enabledFlow
 *   (App 内开关与磁贴状态实时一致); onStopListening (面板收起) 取消订阅零开销。
 *
 * ④ minSdk 21: TileService 为 API 24+ 类 — @RequiresApi(24) 满足 lint;
 *   API 21-23 设备系统不识别 QS_TILE intent-filter, 类永不加载, 声明无副作用。
 */
@RequiresApi(24)
class FloatWindowTileService : TileService() {

    companion object {
        private const val TAG = "FloatTile"
    }

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var syncJob: Job? = null

    /** 面板展开: 刷新状态 + 订阅 enabledFlow (App 内开关变化 → 磁贴实时跟随) */
    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
        syncJob?.cancel()
        syncJob = scope.launch {
            FloatingWindowConfig.enabledFlow.collect { refreshTile() }
        }
    }

    /** 面板收起: 取消订阅 (面板关闭期间磁贴不渲染, 订阅是纯开销) */
    override fun onStopListening() {
        super.onStopListening()
        syncJob?.cancel()
        syncJob = null
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    @SuppressLint("StartActivityAndCollapseIfNeeded") // 本类不跳 Activity, 仅为 lint 明确意图
    override fun onClick() {
        super.onClick()
        // 锁屏态不切换: 锁屏上方无法安全展示 overlay (TYPE_APPLICATION_OVERLAY 不可越 keyguard)
        if (isLocked) return
        val tile = qsTile ?: return

        // ② 权限未授予: 灰掉 + Toast 引导 (UI 层那套 ON_RESUME 复查在磁贴上不可用)
        if (!runCatching { Settings.canDrawOverlays(this) }.getOrDefault(false)) {
            tile.state = Tile.STATE_UNAVAILABLE
            runCatching { tile.updateTile() }
            runCatching {
                Toast.makeText(
                    applicationContext,
                    getString(R.string.float_permission_toast),
                    Toast.LENGTH_SHORT
                ).show()
            }
            return
        }

        val enable = !FloatingWindowConfig.enabled
        FloatingWindowConfig.enabled = enable  // 持久化 + StateFlow (App 内开关同步刷新)

        if (enable) startWindowService() else {
            // 关闭: stopService 与 Service 侧 enabledFlow 观察者双路径幂等
            runCatching { stopService(Intent(this, FloatingWindowService::class.java)) }
                .onFailure { Log.w(TAG, "stopService 失败", it) }
        }
        refreshTile()
    }

    /**
     * ① 启动悬浮窗前台服务 — 镜像 FloatingWindowScreen 的启停路径,
     * 唯一差异: 后台场景 (磁贴点击) API 26+ 必须 startForegroundService
     */
    private fun startWindowService() {
        val intent = Intent(this, FloatingWindowService::class.java)
        runCatching {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent)
            else startService(intent)
        }.onFailure {
            Log.w(TAG, "磁贴启动悬浮窗服务失败", it)
            // 回滚: 不留"开关已开但窗口未出现"的假状态
            FloatingWindowConfig.enabled = false
            runCatching {
                Toast.makeText(
                    applicationContext,
                    getString(R.string.float_permission_toast),
                    Toast.LENGTH_SHORT
                ).show()
            }
            refreshTile()
        }
    }

    /** 磁贴三态: 无权限灰 / 总开关开高亮 / 关熄灭 */
    private fun refreshTile() {
        val tile = qsTile ?: return
        val hasPerm = runCatching { Settings.canDrawOverlays(this) }.getOrDefault(false)
        tile.state = when {
            !hasPerm -> Tile.STATE_UNAVAILABLE
            FloatingWindowConfig.enabled -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        runCatching { tile.updateTile() }
            .onFailure { Log.w(TAG, "updateTile 失败", it) }
    }
}
