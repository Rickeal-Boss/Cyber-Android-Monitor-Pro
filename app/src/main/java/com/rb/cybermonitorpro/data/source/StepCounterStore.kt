package com.rb.cybermonitorpro.data.source

import android.content.SharedPreferences

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
 * - 跨日检测: 当日编号变化 → 当日起始总步数重置为当前总量。
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
        if (baseline < 0 || raw < lastRaw) {
            offset = read(KEY_LAST_TOTAL, 0L)
            baseline = raw
        }
        val sinceBoot = (raw - baseline).coerceAtLeast(0L)
        val total = offset + sinceBoot

        // 跨日检测
        val dayStamp = now / DAY_MS
        var dayStart = read(KEY_DAY_START, -1L)
        if (dayStart < 0 || read(KEY_DAY_STAMP, -1L) != dayStamp) {
            dayStart = total
            write(KEY_DAY_STAMP, dayStamp)
        }
        val today = (total - dayStart).coerceAtLeast(0L)

        write(KEY_OFFSET, offset)
        write(KEY_BOOT_BASELINE, baseline)
        write(KEY_LAST_TOTAL, total)
        write(KEY_LAST_RAW, raw)
        write(KEY_DAY_START, dayStart)
        return StepLedger(totalSteps = total, todaySteps = today, stepsSinceBoot = sinceBoot)
    }

    /** 上次已知总步数（进入页面前先展示，避免空白） */
    fun lastKnownTotal(): Long = read(KEY_LAST_TOTAL, 0L)
}
