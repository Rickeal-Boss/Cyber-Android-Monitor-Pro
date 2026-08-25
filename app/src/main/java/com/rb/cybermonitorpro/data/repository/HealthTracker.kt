package com.rb.cybermonitorpro.data.repository

import androidx.lifecycle.MutableLiveData

/** Tracks health status for all data sources */
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
        val allHealthy get() = listOf(cpu, gpu, battery, memory, storage,
            wifi, mobileNetwork, networkInterface, gps, sensors, system, deviceDetail, oem)
            .all { it == Health.OK }
        val errorCount get() = listOf(cpu, gpu, battery, memory, storage,
            wifi, mobileNetwork, networkInterface, gps, sensors, system, deviceDetail, oem)
            .count { it == Health.ERROR }
        val warnCount get() = listOf(cpu, gpu, battery, memory, storage,
            wifi, mobileNetwork, networkInterface, gps, sensors, system, deviceDetail, oem)
            .count { it == Health.WARN }
    }

    val liveData = MutableLiveData(SourceHealth())

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
