package com.rb.cybermonitorpro.data.source

import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * StepCounterStore 步数账本单测 — Map 持久化替身 + 假时钟
 */
class StepCounterStoreTest {

    private class FakeStore {
        val map = HashMap<String, Long>()
        var now = 1_700_000_000_000L   // 固定起点
        val store = StepCounterStore(
            read = { k, d -> map[k] ?: d },
            write = { k, v -> map[k] = v },
            nowMillis = { now },
        )
    }

    @Test
    fun `首次读数建立基线不计步`() {
        val f = FakeStore()
        val r = f.store.onHardwareReading(500, now = f.now)
        assertEquals(0L, r.totalSteps)
        assertEquals(0L, r.stepsSinceBoot)
        assertEquals(0L, r.todaySteps)
    }

    @Test
    fun `同开机周期内累积`() {
        val f = FakeStore()
        f.store.onHardwareReading(100, now = f.now)
        val r = f.store.onHardwareReading(160, now = f.now + 1000)
        assertEquals(60L, r.totalSteps)
        assertEquals(60L, r.stepsSinceBoot)
        assertEquals(60L, r.todaySteps)
    }

    @Test
    fun `重启后总量不丢`() {
        val f = FakeStore()
        f.store.onHardwareReading(100, now = f.now)
        f.store.onHardwareReading(350, now = f.now + 1000)   // 本次开机 250 步
        // 重启: 硬件计数器归零后重新走到 40
        val r = f.store.onHardwareReading(40, now = f.now + 2000)
        assertEquals(250L, r.totalSteps)
        assertEquals(0L, r.stepsSinceBoot)
        // 重启后再走 30 步
        val r2 = f.store.onHardwareReading(70, now = f.now + 3000)
        assertEquals(280L, r2.totalSteps)
        assertEquals(30L, r2.stepsSinceBoot)
    }

    @Test
    fun `跨日重置今日计数累计保留`() {
        val f = FakeStore()
        f.store.onHardwareReading(0, now = f.now)
        f.store.onHardwareReading(1000, now = f.now + 3600_000)   // 今日 1000 步
        // 次日同一时刻, 硬件继续累积
        val r = f.store.onHardwareReading(1500, now = f.now + 3600_000 + 24L * 3600_000)
        assertEquals(1500L, r.totalSteps)      // 累计不重置
        assertEquals(500L, r.todaySteps)       // 今日只算新一天走的
    }

    @Test
    fun `负读数防御按0处理`() {
        val f = FakeStore()
        val r = f.store.onHardwareReading(-5, now = f.now)
        assertEquals(0L, r.totalSteps)
    }

    @Test
    fun `lastKnownTotal 返回上次总量`() {
        val f = FakeStore()
        assertEquals(0L, f.store.lastKnownTotal())
        f.store.onHardwareReading(100, now = f.now)
        f.store.onHardwareReading(300, now = f.now + 1000)
        // lastKnownTotal 返回账本总量 (offset 0 + sinceBoot 300-100 = 200), 非硬件原始读数 300
        assertEquals(200L, f.store.lastKnownTotal())
    }

    // ========================================================
    // F-07 本地日语义 (时区修正版 dayStamp) 单测
    // 说明: 用例内临时改 JVM 时区并 try-finally 恢复, 防止污染其它测试。
    // 基准时刻 t1 = 1_767_276_000_000 ms = UTC 2026-01-01 14:00,
    //   Asia/Shanghai (UTC+8) 下为本地 2026-01-01 22:00;
    //   t1 + 9h = 1_767_285_000_000 ms = UTC 2026-01-01 16:30,
    //   本地已是 2026-01-02 00:30 — 恰在 F-07 修复的"本地 00:00–08:00"窗口内。
    // ========================================================

    @Test
    fun `本地日翻转时今日步数重置`() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
            val f = FakeStore()
            val t1 = 1_767_276_000_000L   // 本地 2026-01-01 22:00
            f.store.onHardwareReading(1000, now = t1)               // 首次读数建基线, 今日 0
            f.store.onHardwareReading(2000, now = t1 + 3_600_000)   // 本地 23:00, 今日 1000
            // 本地 2026-01-02 00:30 (UTC 仍是 2026-01-01) — 本地日已翻
            val r = f.store.onHardwareReading(2200, now = t1 + 9_000_000)
            assertEquals(1200L, r.totalSteps)   // 累计不丢
            assertEquals(200L, r.todaySteps)    // 今日只算本地 00:30 后走的 2200-2000=200 步
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun `UTC 时区下同一时刻不跨日今日步数不重置`() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val f = FakeStore()
            val t1 = 1_767_276_000_000L   // UTC 2026-01-01 14:00
            f.store.onHardwareReading(1000, now = t1)
            f.store.onHardwareReading(2000, now = t1 + 3_600_000)   // UTC 15:00, 今日 1000
            // UTC 2026-01-01 16:30 — UTC 日未翻, 不做跨日重置
            val r = f.store.onHardwareReading(2200, now = t1 + 9_000_000)
            assertEquals(1200L, r.totalSteps)
            assertEquals(1200L, r.todaySteps)   // 对比: 同刻在 UTC 下仍是同一天 → 今日 = 全部
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun `peekLedger 跨日自愈按本地日重置今日步数`() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
            val f = FakeStore()
            val t1 = 1_767_276_000_000L   // 本地 2026-01-01 22:00
            f.store.onHardwareReading(1000, now = t1)               // 建基线
            f.store.onHardwareReading(2000, now = t1 + 3_600_000)   // 本地 23:00, 今日 1000
            // 次日本地 00:30 首次进页面, 尚未触发 onHardwareReading — peek 自愈
            f.now = t1 + 9_000_000
            val r = f.store.peekLedger()
            assertEquals(1000L, r.totalSteps)
            assertEquals(1000L, r.stepsSinceBoot)
            assertEquals(0L, r.todaySteps)   // 本地日已翻 → dayStart 自愈为昨日总量, 今日归 0
        } finally {
            TimeZone.setDefault(original)
        }
    }

    // ========================================================
    // P2 STEP_DETECTOR 降级账本 (独立命名空间键) 单测
    // ========================================================

    @Test
    fun `detectorTotal 默认0且写入读回`() {
        val f = FakeStore()
        assertEquals(0L, f.store.readDetectorTotal())
        f.store.writeDetectorTotal(1234L)
        assertEquals(1234L, f.store.readDetectorTotal())
        // 负值防御: 不入账
        f.store.writeDetectorTotal(-5L)
        assertEquals(0L, f.store.readDetectorTotal())
    }

    @Test
    fun `detector 账本与 STEP_COUNTER 账本完全隔离`() {
        val f = FakeStore()
        // 先写入 detector 账本, 不污染 STEP_COUNTER 结算 (onHardwareReading 不读该键)
        f.store.writeDetectorTotal(999L)
        f.store.onHardwareReading(100, now = f.now)
        f.store.onHardwareReading(260, now = f.now + 1000)
        assertEquals(160L, f.store.lastKnownTotal())
        // STEP_COUNTER 结算也不改写 detector 账本 (STEP_COUNTER 键全在 step_* 命名空间)
        assertEquals(999L, f.store.readDetectorTotal())
    }
}
