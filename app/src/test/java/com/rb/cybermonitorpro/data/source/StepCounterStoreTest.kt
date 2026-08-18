package com.rb.cybermonitorpro.data.source

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
        assertEquals(300L, f.store.lastKnownTotal())
    }
}
