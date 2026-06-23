package com.example.deviceinfoviewer.data.repository

import androidx.lifecycle.MutableLiveData

/**
 * [Architect Note] 数据源健康状态追踪器 — 从 DeviceRepository 提取
 *
 * 单一职责: 管理 13 个数据源的健康状态（OK / WARN / ERROR），
 * 通过 MutableLiveData 广播给 UI 层。内置脏检查路径避免无意义的 copy+postValue。
 *
 * 重构动机: 原 DeviceRepository 中 SourceHealth + markHealth() 占用 ~100 行，
 * 包含了维护成本高的 when(name) 分支。提取后 DeviceRepository 仅调用 tracker.mark()。
 */
class HealthTracker {

    data class SourceHealth(
        val cpu: Health = Health.OK,
        val gpu: Health = Health.OK,
        val battery: Health = Health.OK,
        val memory: Health = Health.OK,
        val storage: Health = Health.OK,
        val wifi: Health = Health.OK,
        val mobileNetwork: Health = Health.OK,
        val networkInterface: Health = Health.OK,
        val gps: Health = Health.OK,
        val sensors: Health = Health.OK,
        val system: Health = Health.OK,
        val deviceDetail: Health = Health.OK,
        val oem: Health = Health.OK
    ) {
        enum class Health { OK, WARN, ERROR }
    }

    val liveData = MutableLiveData(SourceHealth())

    // [Architect Note] 脏路径检查: 95%+ 的 tick 无需写 LiveData，零分配
    fun mark(health: SourceHealth.Health, vararg sourceNames: String) {
        val current = liveData.value ?: return
        val needsUpdate = sourceNames.any { n ->
            when (n) {
                "cpu" -> current.cpu != health
                "gpu" -> current.gpu != health
                "battery" -> current.battery != health
                "memory" -> current.memory != health
                "storage" -> current.storage != health
                "wifi" -> current.wifi != health
                "mobile" -> current.mobileNetwork != health
                "netif" -> current.networkInterface != health
                "gps" -> current.gps != health
                "sensors" -> current.sensors != health
                "system" -> current.system != health
                "device" -> current.deviceDetail != health
                "oem" -> current.oem != health
                else -> false
            }
        }
        if (!needsUpdate) return

        var h = current
        sourceNames.forEach { n ->
            h = when (n) {
                "cpu" -> h.copy(cpu = health)
                "gpu" -> h.copy(gpu = health)
                "battery" -> h.copy(battery = health)
                "memory" -> h.copy(memory = health)
                "storage" -> h.copy(storage = health)
                "wifi" -> h.copy(wifi = health)
                "mobile" -> h.copy(mobileNetwork = health)
                "netif" -> h.copy(networkInterface = health)
                "gps" -> h.copy(gps = health)
                "sensors" -> h.copy(sensors = health)
                "system" -> h.copy(system = health)
                "device" -> h.copy(deviceDetail = health)
                "oem" -> h.copy(oem = health)
                else -> h
            }
        }
        liveData.postValue(h)
    }
}
