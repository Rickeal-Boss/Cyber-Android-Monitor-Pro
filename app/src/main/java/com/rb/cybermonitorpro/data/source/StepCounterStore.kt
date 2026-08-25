package com.rb.cybermonitorpro.data.source

import android.content.SharedPreferences
import android.util.Log

/**
 * 步数账本结算结果
 */
data class StepLedger(
    val totalSteps: Long,      // 跨重启累计总步数 = offset + (原始读数 − bootBaseline)
    val todaySteps: Long,      // 今日步数 = 总步数 − 当日起始总步数
    val stepsSinceBoot: Long   // 本次开机以来的步数（原始读数 − 基线）
)

/**
 * StepCounterStore — STEP_COUNTER(19) 硬件累积读数的持久化账本，跨重启递增。
 *
 * 核心不变量: 总步数 = offset + (当前硬件读数 − bootBaseline)
 * - STEP_COUNTER 硬件计数自开机累积、重启归零；
 * - 重启检测: 新原始读数 < 上次原始读数 → 硬件计数器已归零，上次已知总量并入
 *   offset、基线重置为本次读数 → 跨重启累计不丢；
 * - 跨日检测: 当日编号变化 → 当日起始总步数重置为昨日最后总量
 *   （本分支执行于写 LAST_TOTAL 之前，此处读到的仍是昨日总量，即今日起点）。
 *
 * 持久化经 read/write lambda 注入（SharedPreferences / 测试用 Map 均可），
 * 全部写操作 apply 异步、读操作容错降级（OEM ROM SP 偶发异常防御）。
 */
class StepCounterStore(
    private val read: (key: String, default: Long) -> Long,
    private val write: (key: String, value: Long) -> Unit,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    companion object {
        const val KEY_OFFSET = "step_offset"            // 前几次开机累计并入的基量
        const val KEY_BOOT_BASELINE = "step_boot_base"  // 本次开机首次原始读数
        const val KEY_LAST_TOTAL = "step_last_total"    // 上次已知总步数
        const val KEY_LAST_RAW = "step_last_raw"        // 上次原始读数（重启检测用）
        const val KEY_DAY_START = "step_day_start"      // 当日起始总步数
        const val KEY_DAY_STAMP = "step_day_stamp"      // 当日编号（epoch day）
        private const val DAY_MS = 24L * 60 * 60 * 1000
        private const val TAG = "StepStore"

        /** SharedPreferences 适配（命名空间独立，不与 AppSettings 混用） */
        fun fromPrefs(prefs: SharedPreferences): StepCounterStore = StepCounterStore(
            read = { k, d -> try { prefs.getLong(k, d) } catch (_: Throwable) { d } },
            write = { k, v -> try { prefs.edit().putLong(k, v).apply() } catch (_: Throwable) {} },
        )
    }

    /**
     * 送入一次 STEP_COUNTER 硬件原始读数（自开机累积），返回结算后的账本。
     */
    fun onHardwareReading(rawSinceBoot: Long, now: Long = nowMillis()): StepLedger {
        val raw = rawSinceBoot.coerceAtLeast(0L)
        var offset = read(KEY_OFFSET, 0L)
        var baseline = read(KEY_BOOT_BASELINE, -1L)
        val lastRaw = read(KEY_LAST_RAW, 0L)

        // 首次记录或重启（原始读数倒退 → 硬件计数器归零）
        var rebooted = false
        if (baseline < 0 || raw < lastRaw) {
            offset = read(KEY_LAST_TOTAL, 0L)
            baseline = raw
            rebooted = true
            // F-13: 重启/首次记录关键分支日志
            Log.i(TAG, "step counter reboot detected: raw=$raw < lastRaw=$lastRaw, offset=$offset")
        }
        val sinceBoot = (raw - baseline).coerceAtLeast(0L)
        val total = offset + sinceBoot

        // 跨日检测 — 本地日 (F-07): 旧 now/DAY_MS 是 UTC 日期编号, 中国用户 (UTC+8)
        // 本地 00:00–08:00 之间 dayStamp 仍是前一天, "今日步数"延迟 8 小时才重置。
        // minSdk=21 且无 coreLibraryDesugaring → 禁用 java.time, 用时区偏移修正为本地日。
        val tzOffsetMs = java.util.TimeZone.getDefault().getOffset(now)
        val dayStamp = (now + tzOffsetMs) / DAY_MS
        var dayStart = read(KEY_DAY_START, -1L)
        var dayChanged = false
        if (dayStart < 0 || read(KEY_DAY_STAMP, -1L) != dayStamp) {
            // 当日起点 = 昨日最后总量（此处 LAST_TOTAL 尚未被下方覆盖, 读到的仍是旧值）
            dayStart = read(KEY_LAST_TOTAL, 0L)
            write(KEY_DAY_STAMP, dayStamp)
            dayChanged = true
            // F-13: 跨日重置关键分支日志
            Log.i(TAG, "day rollover: new dayStamp=$dayStamp, dayStart=$dayStart")
        }
        val today = (total - dayStart).coerceAtLeast(0L)

        // F-12: 只在重启/跨日时写 offset/baseline/dayStart；每次都写 last_total/last_raw
        // (高频步行时减少 SharedPreferences 排队 I/O, 不变量 total=offset+(raw-baseline) 不变)
        if (rebooted) {
            write(KEY_OFFSET, offset)
            write(KEY_BOOT_BASELINE, baseline)
        }
        if (dayChanged) write(KEY_DAY_START, dayStart)
        write(KEY_LAST_TOTAL, total)
        write(KEY_LAST_RAW, raw)
        return StepLedger(totalSteps = total, todaySteps = today, stepsSinceBoot = sinceBoot)
    }

    /** 上次已知总步数（进入页面前先展示，避免空白） */
    fun lastKnownTotal(): Long = read(KEY_LAST_TOTAL, 0L)

    /**
     * 只读预览账本 — 平时不写、不动账本不变量。
     * 供进入 STEP_COUNTER 详情页时预填初始值：首次回调前显示上次已知总量，
     * 避免 on-change 语义下用户不走路就不触发 onSensorChanged 导致卡片恒 "---" / 假 0。
     *
     * F-07 跨日自愈例外: peek 时若本地日已变 (次日首次进页面但尚未触发 onHardwareReading),
     * "今日步数"会显示昨日值 → 此处重置 dayStart, **仅在 dayStamp 变化时**写 day_stamp/day_start。
     */
    fun peekLedger(): StepLedger {
        val total = read(KEY_LAST_TOTAL, 0L)
        var dayStart = read(KEY_DAY_START, 0L)
        val baseline = read(KEY_BOOT_BASELINE, -1L)
        // 跨日自愈: 本地日 (时区偏移修正), 仅变化时写
        val now = nowMillis()
        val tzOffsetMs = java.util.TimeZone.getDefault().getOffset(now)
        val todayStamp = (now + tzOffsetMs) / DAY_MS
        if (read(KEY_DAY_STAMP, -1L) != todayStamp) {
            dayStart = total
            write(KEY_DAY_STAMP, todayStamp)
            write(KEY_DAY_START, dayStart)
        }
        val sinceBoot = if (baseline >= 0) (read(KEY_LAST_RAW, 0L) - baseline).coerceAtLeast(0L) else 0L
        return StepLedger(
            totalSteps = total,
            todaySteps = (total - dayStart).coerceAtLeast(0L),
            stepsSinceBoot = sinceBoot
        )
    }
}
