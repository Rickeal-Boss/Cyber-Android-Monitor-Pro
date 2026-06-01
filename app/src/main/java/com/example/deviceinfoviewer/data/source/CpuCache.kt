package com.example.deviceinfoviewer.data.source

import com.example.deviceinfoviewer.data.model.CpuCoreInfo
import com.example.deviceinfoviewer.data.model.CpuInfo
import com.example.deviceinfoviewer.data.model.GpuInfo

/**
 * 处理器预缓存知识库 — 高通骁龙系列
 *
 * 当检测到匹配的平台时，注入芯片级固定规格（CPU核心/缓存/GPU架构等）。
 * 注意: WiFi/BT、快充版本、内存型号等因 OEM 定制而不同，不在此处硬编码。
 */
object CpuCache {

    data class KnownChip(
        val platformId: String,
        val chipName: String,
        val cpuModel: String,
        val processNode: String,
        val releaseDate: String,

        val clusters: List<ClusterSpec>,

        // 缓存（芯片固定值）
        val l1iPerBig: String,
        val l1dPerBig: String,
        val l2PerBig: String,
        val l1iPerSmall: String,
        val l1dPerSmall: String,
        val l2PerSmall: String,
        val l3Shared: String,

        // GPU（芯片固定值）
        val gpuModel: String,
        val gpuClockMhz: Int,
        val gpuAlus: Int,
        val gpuFp32Tflops: Float,

        // ISP / DSP / NPU（芯片固定值）
        val isp: String,
        val npu: String,

        // 基带型号（芯片固定值）
        val modem: String,
    )

    data class ClusterSpec(
        val coreName: String,
        val count: Int,
        val maxFreqGHz: Float,
        val minFreqGHz: Float = 0.3f
    )

    // ═══════════════ 预缓存数据库 ═══════════════

    val KNOWN_CHIPS: Map<String, KnownChip> = mapOf(

        // ═══ Snapdragon 865 (SM8250) — kona ═══
        "sm8250" to KnownChip(
            platformId = "kona",
            chipName = "Snapdragon 865",
            cpuModel = "Kryo 585 (Cortex-A77 + A55)",
            processNode = "7nm TSMC N7P",
            releaseDate = "2019-12",
            clusters = listOf(
                ClusterSpec("Cortex-A77 Prime", 1, 2.84f),
                ClusterSpec("Cortex-A77 Gold",  3, 2.42f),
                ClusterSpec("Cortex-A55 Silver", 4, 1.80f),
            ),
            l1iPerBig = "64 KB", l1dPerBig = "64 KB", l2PerBig = "512 KB",
            l1iPerSmall = "32 KB", l1dPerSmall = "32 KB", l2PerSmall = "128 KB",
            l3Shared = "4 MB",
            gpuModel = "Adreno 650",
            gpuClockMhz = 587,
            gpuAlus = 512,
            gpuFp32Tflops = 1.20f,
            isp = "Spectra 480",
            npu = "Hexagon 698",
            modem = "Snapdragon X55",
        ),

        // ═══ Snapdragon 8s Gen 3 (SM8635) — pineapple ═══
        "sm8635" to KnownChip(
            platformId = "pineapple",
            chipName = "Snapdragon 8s Gen 3",
            cpuModel = "Kryo (Cortex-X4 + A720 + A520)",
            processNode = "4nm TSMC N4",
            releaseDate = "2024-03",
            clusters = listOf(
                ClusterSpec("Cortex-X4",   1, 3.0f, 0.6f),
                ClusterSpec("Cortex-A720", 4, 2.8f, 0.6f),
                ClusterSpec("Cortex-A520", 3, 2.0f, 0.5f),
            ),
            l1iPerBig = "64 KB", l1dPerBig = "64 KB",
            l2PerBig = "2 MB (X4) / 512 KB shared (A720)",
            l1iPerSmall = "32 KB", l1dPerSmall = "32 KB",
            l2PerSmall = "256 KB shared (A520)",
            l3Shared = "8 MB",
            gpuModel = "Adreno 735",
            gpuClockMhz = 750,
            gpuAlus = 786,
            gpuFp32Tflops = 3.73f,
            isp = "Spectra Triple 18-bit",
            npu = "Hexagon (Qualcomm AI Engine)",
            modem = "Snapdragon X70",
        ),
    )

    // ═══════════════ 方法 ═══════════════

    fun lookup(platform: String): KnownChip? {
        val key = platform.lowercase().trim()
        KNOWN_CHIPS[key]?.let { return it }
        return KNOWN_CHIPS.values.firstOrNull { it.platformId == key }
    }

    fun injectCpuInfo(chip: KnownChip, info: CpuInfo) {
        info.architecture = chip.chipName + "\n" + chip.cpuModel

        info.cacheL1 = "I:${chip.l1iPerBig} D:${chip.l1dPerBig} (大核) · I:${chip.l1iPerSmall} D:${chip.l1dPerSmall} (小核)"
        info.cacheL2 = "${chip.l2PerBig} (大核) · ${chip.l2PerSmall} (小核)"
        info.cacheL3 = chip.l3Shared

        // sysfs 读不到核心时用缓存补全
        if (info.cores.isEmpty()) {
            var index = 0
            for (cluster in chip.clusters) {
                for (i in 0 until cluster.count) {
                    info.cores.add(CpuCoreInfo(
                        coreIndex = index++,
                        currentFreqKHz = 0,
                        maxFreqKHz = (cluster.maxFreqGHz * 1_000_000).toLong(),
                        minFreqKHz = (cluster.minFreqGHz * 1_000_000).toLong(),
                    ))
                }
            }
            info.coreCount = info.cores.size
        }
    }

    fun injectGpuInfo(chip: KnownChip, info: GpuInfo) {
        if (info.model.isEmpty() || info.model.contains("kgsl", true)) {
            info.model = chip.gpuModel
        }
        if (info.frequencyKHz <= 0) info.frequencyKHz = chip.gpuClockMhz * 1000L
        if (info.maxFreqKHz <= 0) info.maxFreqKHz = chip.gpuClockMhz * 1000L
        if (info.minFreqKHz <= 0) info.minFreqKHz = (chip.gpuClockMhz * 1000L * 0.2).toLong()
    }
}
