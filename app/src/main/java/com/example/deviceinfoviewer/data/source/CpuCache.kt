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

        // ═══ Snapdragon 8 Gen 3 (SM8650) — pineapple ═══
        "sm8650" to KnownChip(
            platformId = "pineapple",
            chipName = "Snapdragon 8 Gen 3",
            cpuModel = "Kryo (Cortex-X4 + A720 + A520)",
            processNode = "4nm TSMC N4P",
            releaseDate = "2023-10",
            clusters = listOf(
                ClusterSpec("Cortex-X4 Prime", 1, 3.30f),
                ClusterSpec("Cortex-A720", 3, 3.15f),
                ClusterSpec("Cortex-A720", 2, 2.96f),
                ClusterSpec("Cortex-A520", 2, 2.27f),
            ),
            l1iPerBig = "64 KB", l1dPerBig = "64 KB", l2PerBig = "512 KB",
            l1iPerSmall = "32 KB", l1dPerSmall = "32 KB", l2PerSmall = "256 KB",
            l3Shared = "12 MB",
            gpuModel = "Adreno 750",
            gpuClockMhz = 903,
            gpuAlus = 1536,
            gpuFp32Tflops = 4.43f,
            isp = "Spectra Triple 18-bit",
            npu = "Hexagon (Qualcomm AI Engine)",
            modem = "Snapdragon X75",
        ),

        // ═══ Snapdragon 7+ Gen 3 (SM7675) ═══
        "sm7675" to KnownChip(
            platformId = "pineapple",
            chipName = "Snapdragon 7+ Gen 3",
            cpuModel = "Kryo (Cortex-X4 + A720 + A520)",
            processNode = "4nm TSMC N4P",
            releaseDate = "2024-03",
            clusters = listOf(
                ClusterSpec("Cortex-X4 Prime", 1, 2.80f),
                ClusterSpec("Cortex-A720", 4, 2.60f),
                ClusterSpec("Cortex-A520", 3, 1.90f),
            ),
            l1iPerBig = "64 KB", l1dPerBig = "64 KB", l2PerBig = "512 KB",
            l1iPerSmall = "32 KB", l1dPerSmall = "32 KB", l2PerSmall = "256 KB",
            l3Shared = "4 MB",
            gpuModel = "Adreno 732",
            gpuClockMhz = 950,
            gpuAlus = 768,
            gpuFp32Tflops = 2.33f,
            isp = "Spectra Triple 18-bit",
            npu = "Hexagon (Qualcomm AI Engine)",
            modem = "Snapdragon X63",
        ),

        // ═══ Snapdragon 7 Gen 3 (SM7550) ═══
        "sm7550" to KnownChip(
            platformId = "crow",
            chipName = "Snapdragon 7 Gen 3",
            cpuModel = "Kryo (Cortex-A715 + A510)",
            processNode = "4nm TSMC N4P",
            releaseDate = "2023-11",
            clusters = listOf(
                ClusterSpec("Cortex-A715", 1, 2.63f),
                ClusterSpec("Cortex-A715", 3, 2.40f),
                ClusterSpec("Cortex-A510", 4, 1.80f),
            ),
            l1iPerBig = "64 KB", l1dPerBig = "64 KB", l2PerBig = "512 KB",
            l1iPerSmall = "32 KB", l1dPerSmall = "32 KB", l2PerSmall = "128 KB",
            l3Shared = "4 MB",
            gpuModel = "Adreno 720",
            gpuClockMhz = 900,
            gpuAlus = 512,
            gpuFp32Tflops = 1.47f,
            isp = "Spectra Triple 12-bit",
            npu = "Hexagon (Qualcomm AI Engine)",
            modem = "Snapdragon X63",
        ),

        // ═══ Snapdragon 6 Gen 3 (SM6475) ═══
        "sm6475" to KnownChip(
            platformId = "holi",
            chipName = "Snapdragon 6 Gen 3",
            cpuModel = "Kryo (Cortex-A78 + A55)",
            processNode = "4nm Samsung 4LPP",
            releaseDate = "2024-03",
            clusters = listOf(
                ClusterSpec("Cortex-A78", 4, 2.40f),
                ClusterSpec("Cortex-A55", 4, 1.80f),
            ),
            l1iPerBig = "64 KB", l1dPerBig = "64 KB", l2PerBig = "256 KB",
            l1iPerSmall = "32 KB", l1dPerSmall = "32 KB", l2PerSmall = "128 KB",
            l3Shared = "2 MB",
            gpuModel = "Adreno 710",
            gpuClockMhz = 900,
            gpuAlus = 384,
            gpuFp32Tflops = 1.10f,
            isp = "Spectra Triple 12-bit",
            npu = "Hexagon (Qualcomm AI Engine)",
            modem = "Snapdragon X62",
        ),

        // ═══ Snapdragon 6 Gen 1 (SM6450) ═══
        "sm6450" to KnownChip(
            platformId = "holi",
            chipName = "Snapdragon 6 Gen 1",
            cpuModel = "Kryo (Cortex-A78 + A55)",
            processNode = "4nm Samsung 4LPE",
            releaseDate = "2022-09",
            clusters = listOf(
                ClusterSpec("Cortex-A78", 4, 2.20f),
                ClusterSpec("Cortex-A55", 4, 1.80f),
            ),
            l1iPerBig = "64 KB", l1dPerBig = "64 KB", l2PerBig = "256 KB",
            l1iPerSmall = "32 KB", l1dPerSmall = "32 KB", l2PerSmall = "128 KB",
            l3Shared = "2 MB",
            gpuModel = "Adreno 710",
            gpuClockMhz = 676,
            gpuAlus = 384,
            gpuFp32Tflops = 0.83f,
            isp = "Spectra Triple 12-bit",
            npu = "Hexagon (Qualcomm AI Engine)",
            modem = "Snapdragon X62",
        ),

        // ═══ Dimensity 9300+ (MT6989) ═══
        "mt6989" to KnownChip(
            platformId = "mt6989",
            chipName = "Dimensity 9300+",
            cpuModel = "Cortex-X4 + A720 (全大核)",
            processNode = "4nm TSMC N4P",
            releaseDate = "2024-05",
            clusters = listOf(
                ClusterSpec("Cortex-X4", 1, 3.40f),
                ClusterSpec("Cortex-X4", 3, 2.85f),
                ClusterSpec("Cortex-A720", 4, 2.00f),
            ),
            l1iPerBig = "64 KB", l1dPerBig = "64 KB", l2PerBig = "1 MB",
            l1iPerSmall = "64 KB", l1dPerSmall = "64 KB", l2PerSmall = "512 KB",
            l3Shared = "18 MB",
            gpuModel = "Immortalis-G720 MC12",
            gpuClockMhz = 1300,
            gpuAlus = 768,
            gpuFp32Tflops = 3.99f,
            isp = "Imagiq 990",
            npu = "APU 790",
            modem = "5G R16 (MediaTek T830)",
        ),

        // ═══ Dimensity 8400 (MT6899) ═══
        "mt6899" to KnownChip(
            platformId = "mt6899",
            chipName = "Dimensity 8400",
            cpuModel = "Cortex-A725 (全大核)",
            processNode = "4nm TSMC N4P",
            releaseDate = "2024-12",
            clusters = listOf(
                ClusterSpec("Cortex-A725", 1, 3.25f),
                ClusterSpec("Cortex-A725", 3, 3.00f),
                ClusterSpec("Cortex-A725", 4, 2.10f),
            ),
            l1iPerBig = "64 KB", l1dPerBig = "64 KB", l2PerBig = "512 KB",
            l1iPerSmall = "64 KB", l1dPerSmall = "64 KB", l2PerSmall = "512 KB",
            l3Shared = "8 MB",
            gpuModel = "Mali-G720 MC7",
            gpuClockMhz = 1300,
            gpuAlus = 448,
            gpuFp32Tflops = 2.33f,
            isp = "Imagiq 980",
            npu = "APU 780",
            modem = "5G R16 (MediaTek T830)",
        ),

        // ═══ Dimensity 8300 (MT6897) ═══
        "mt6897" to KnownChip(
            platformId = "mt6897",
            chipName = "Dimensity 8300",
            cpuModel = "Cortex-A715 + A510",
            processNode = "4nm TSMC N4P",
            releaseDate = "2023-11",
            clusters = listOf(
                ClusterSpec("Cortex-A715", 1, 3.35f),
                ClusterSpec("Cortex-A715", 3, 3.20f),
                ClusterSpec("Cortex-A510", 4, 2.20f),
            ),
            l1iPerBig = "64 KB", l1dPerBig = "64 KB", l2PerBig = "512 KB",
            l1iPerSmall = "32 KB", l1dPerSmall = "32 KB", l2PerSmall = "256 KB",
            l3Shared = "8 MB",
            gpuModel = "Mali-G615 MC6",
            gpuClockMhz = 1400,
            gpuAlus = 384,
            gpuFp32Tflops = 1.72f,
            isp = "Imagiq 980",
            npu = "APU 780",
            modem = "5G R16 (MediaTek T830)",
        ),

        // ═══ Dimensity 7300 (MT6878) ═══
        "mt6878" to KnownChip(
            platformId = "mt6878",
            chipName = "Dimensity 7300",
            cpuModel = "Cortex-A78 + A55",
            processNode = "4nm TSMC N4P",
            releaseDate = "2024-03",
            clusters = listOf(
                ClusterSpec("Cortex-A78", 4, 2.50f),
                ClusterSpec("Cortex-A55", 4, 2.00f),
            ),
            l1iPerBig = "64 KB", l1dPerBig = "64 KB", l2PerBig = "256 KB",
            l1iPerSmall = "32 KB", l1dPerSmall = "32 KB", l2PerSmall = "128 KB",
            l3Shared = "4 MB",
            gpuModel = "Mali-G615 MC2",
            gpuClockMhz = 950,
            gpuAlus = 128,
            gpuFp32Tflops = 0.39f,
            isp = "Imagiq 950",
            npu = "APU 650",
            modem = "5G R16 (MediaTek T750)",
        ),
    )

    // ═══════════════ 方法 ═══════════════

    fun lookup(platform: String): KnownChip? {
        val key = platform.lowercase().trim()
        // 精确 key 匹配
        KNOWN_CHIPS[key]?.let { return it }
        // 精确 platformId 匹配
        KNOWN_CHIPS.values.firstOrNull { it.platformId == key }?.let { return it }
        // MTK 平台特殊处理: 移除 "mt" 前缀和 "mediatek/" 前缀后匹配
        val strippedMt = key.removePrefix("mediatek/").removePrefix("mt")
        if (strippedMt != key) {
            KNOWN_CHIPS.values.firstOrNull {
                it.platformId.removePrefix("mt").contains(strippedMt)
            }?.let { return it }
        }
        return null
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
                        coreType = cluster.coreName,
                    ))
                }
            }
            info.coreCount = info.cores.size
        } else {
            // sysfs 已有核心数据时，用缓存知识库覆盖 coreType (比频率推断更准确)
            var i = 0
            for (cluster in chip.clusters) {
                for (j in 0 until cluster.count) {
                    if (i < info.cores.size) {
                        info.cores[i].coreType = cluster.coreName
                    }
                    i++
                }
            }
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
