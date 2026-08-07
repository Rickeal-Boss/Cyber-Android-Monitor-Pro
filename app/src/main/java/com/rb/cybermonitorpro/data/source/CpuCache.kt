package com.rb.cybermonitorpro.data.source

import com.rb.cybermonitorpro.data.model.CpuCoreInfo
import com.rb.cybermonitorpro.data.model.CpuInfo
import com.rb.cybermonitorpro.data.model.GpuInfo
import kotlin.math.abs

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

    // lookup 策略4 别名表：提升为常量，避免每次调用重建 map
    //   Qualcomm 芯片同一平台可能有多个 codename（如 SM8635 → "sun" 或 "pineapple"）
    //   OPPO/Xiaomi 等 OEM 可能返回非标准 codename
    private val CODENAME_ALIASES = mapOf(
        "sun" to "sm8750"   // Snapdragon 8 Elite (之前误标为 sm8635)
    )

    // 天玑家族兜底模式：提升为常量，避免每次 lookup 重新编译正则
    private val DIMENSITY_PATTERN = Regex("mt(67|68|69)\\d{2}")

    // ═══ 天玑营销后缀归一化 (Ultra/Max/+/Ultimate/Turbo/Apex/Energy/Elite/Pro/X/e) ═══
    //   仅用于查找, 不用于显示 (UI 保留 OEM 营销名)。
    //   背景: 三星/OEM 在 ro.chipname / ro.hardware.chipname / ro.chipset 写入带后缀营销名
    //   (如 Dimensity 8300-Ultra / mt6897-ultra), 归一化到基础硅片号再查表。
    //   映射表仅含项目 KNOWN_CHIPS 中真实存在的天玑型号 (硅片号已逐条核对),
    //   不为每个 Ultra 变体新增条目 — 它们与基础型号同规格。

    /** 营销名 → 硅片号 (层2): 仅映射 KNOWN_CHIPS 中真实存在的型号, 避免错配 */
    internal val DIMENSITY_MARKETING_TO_SILICON = mapOf(
        "dimensity 9300" to "mt6989",  // 9300 / 9300+
        "dimensity 8400" to "mt6899",
        "dimensity 8300" to "mt6897",
        "dimensity 7300" to "mt6878",
        "dimensity 9200" to "mt6983",
        "dimensity 9000" to "mt6985",  // 9000 / 9000+
        "dimensity 8200" to "mt6896",
        "dimensity 7200" to "mt6886",
        "dimensity 1080" to "mt6879",
        "dimensity 1200" to "mt6893",
        "dimensity 8100" to "mt6895",
    )

    /** 营销后缀剥离正则 (层1): -ultra / -max / ultra / x / e / plus ... (空格或短横可选) */
    private val MARKETING_SUFFIX_PATTERN = Regex(
        """[\s\-]?(?:ultra|ultimate|turbo|apex|energy|elite|pro|max|plus|x|e)$""",
        RegexOption.IGNORE_CASE
    )

    /** 紧贴数字的 U 后缀 (如 "MT6989U" → "MT6989") */
    private val TRAILING_U_PATTERN = Regex("""(?<=\d)u$""", RegexOption.IGNORE_CASE)

    /** "+" 后缀 (如 "Dimensity 9300+") */
    private val PLUS_SUFFIX_PATTERN = Regex("""\+$""")

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

        // ═══ Snapdragon 870 (SM8250-AC) — kona ═══
        "sm8250-ac" to KnownChip(
            platformId = "kona",
            chipName = "Snapdragon 870",
            cpuModel = "Kryo 585 (Cortex-A77 + A55)",
            processNode = "7nm TSMC N7P",
            releaseDate = "2021-01",
            clusters = listOf(
                ClusterSpec("Cortex-A77 Prime", 1, 3.19f),
                ClusterSpec("Cortex-A77 Gold",  3, 2.42f),
                ClusterSpec("Cortex-A55 Silver", 4, 1.80f),
            ),
            l1iPerBig = "64 KB", l1dPerBig = "64 KB", l2PerBig = "512 KB",
            l1iPerSmall = "32 KB", l1dPerSmall = "32 KB", l2PerSmall = "128 KB",
            l3Shared = "4 MB",
            gpuModel = "Adreno 650",
            gpuClockMhz = 670,
            gpuAlus = 512,
            gpuFp32Tflops = 1.37f,
            isp = "Spectra 480",
            npu = "Hexagon 698",
            modem = "Snapdragon X55",
        ),

        // ═══ Snapdragon 8s Gen 3 (SM8635) — pineapple ═══
        "sm8635" to KnownChip(
            platformId = "pineapple",
            chipName = "Snapdragon 8s Gen 3",
            cpuModel = "Kryo (Cortex-X4 + A720 + A520)",
            processNode = "4nm TSMC N4P",
            releaseDate = "2024-03",
            clusters = listOf(
                ClusterSpec("Cortex-X4",   1, 3.0f, 0.6f),
                ClusterSpec("Cortex-A720", 4, 2.8f, 0.6f),
                ClusterSpec("Cortex-A520", 3, 2.0f, 0.5f),
            ),
            l1iPerBig = "64 KB", l1dPerBig = "64 KB",
            l2PerBig = "1 MB (X4) / 256 KB shared (A720)",
            l1iPerSmall = "32 KB", l1dPerSmall = "32 KB",
            l2PerSmall = "256 KB shared (A520)",
            l3Shared = "4 MB",
            gpuModel = "Adreno 735",
            gpuClockMhz = 1100,
            gpuAlus = 768,
            gpuFp32Tflops = 1.69f,
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
            processNode = "4nm TSMC N4",
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
            gpuAlus = 896,
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
            gpuClockMhz = 1047,
            gpuAlus = 128,
            gpuFp32Tflops = 0.27f,
            isp = "Imagiq 950",
            npu = "APU 650",
            modem = "5G R16 (MediaTek T750)",
        ),

        // ═══ Dimensity 9200 (MT6983) ═══
        "mt6983" to KnownChip(
            platformId = "mt6983",
            chipName = "Dimensity 9200",
            cpuModel = "Cortex-X3 + A715 + A510",
            processNode = "4nm TSMC N4P",
            releaseDate = "2022-11",
            clusters = listOf(
                ClusterSpec("Cortex-X3", 1, 3.05f),
                ClusterSpec("Cortex-A715", 3, 2.85f),
                ClusterSpec("Cortex-A510", 4, 1.80f),
            ),
            l1iPerBig = "64 KB", l1dPerBig = "64 KB", l2PerBig = "1 MB",
            l1iPerSmall = "64 KB", l1dPerSmall = "64 KB", l2PerSmall = "512 KB",
            l3Shared = "8 MB",
            gpuModel = "Immortalis-G715 MC11",
            gpuClockMhz = 1300,
            gpuAlus = 1024,
            gpuFp32Tflops = 3.50f,
            isp = "Imagiq 890",
            npu = "APU 690",
            modem = "5G R16 (MediaTek M80)",
        ),

        // ═══ Dimensity 9000+ (MT6985) / 9000 (MT6983 同族) ═══
        "mt6985" to KnownChip(
            platformId = "mt6985",
            chipName = "Dimensity 9000+",
            cpuModel = "Cortex-X2 + A710 + A510",
            processNode = "4nm TSMC N4",
            releaseDate = "2022-11",
            clusters = listOf(
                ClusterSpec("Cortex-X2", 1, 3.20f),
                ClusterSpec("Cortex-A710", 3, 2.85f),
                ClusterSpec("Cortex-A510", 4, 1.80f),
            ),
            l1iPerBig = "64 KB", l1dPerBig = "64 KB", l2PerBig = "1 MB",
            l1iPerSmall = "32 KB", l1dPerSmall = "32 KB", l2PerSmall = "256 KB",
            l3Shared = "8 MB",
            gpuModel = "Mali-G710 MC10",
            gpuClockMhz = 1300,
            gpuAlus = 640,
            gpuFp32Tflops = 2.78f,
            isp = "Imagiq 790",
            npu = "APU 590",
            modem = "5G R16 (MediaTek M80)",
        ),

        // ═══ Dimensity 8200 (MT6896) ═══
        "mt6896" to KnownChip(
            platformId = "mt6896",
            chipName = "Dimensity 8200",
            cpuModel = "Cortex-A78 + A55",
            processNode = "4nm TSMC N4P",
            releaseDate = "2022-12",
            clusters = listOf(
                ClusterSpec("Cortex-A78", 1, 3.10f),
                ClusterSpec("Cortex-A78", 3, 3.00f),
                ClusterSpec("Cortex-A55", 4, 2.00f),
            ),
            l1iPerBig = "64 KB", l1dPerBig = "64 KB", l2PerBig = "512 KB",
            l1iPerSmall = "32 KB", l1dPerSmall = "32 KB", l2PerSmall = "128 KB",
            l3Shared = "4 MB",
            gpuModel = "Mali-G610 MC6",
            gpuClockMhz = 950,
            gpuAlus = 384,
            gpuFp32Tflops = 1.72f,
            isp = "Imagiq 785",
            npu = "APU 580",
            modem = "5G R16 (MediaTek M80)",
        ),

        // ═══ Dimensity 7200 (MT6886) ═══
        "mt6886" to KnownChip(
            platformId = "mt6886",
            chipName = "Dimensity 7200",
            cpuModel = "Cortex-A715 + A510",
            processNode = "4nm TSMC N4P",
            releaseDate = "2023-02",
            clusters = listOf(
                ClusterSpec("Cortex-A715", 2, 2.80f),
                ClusterSpec("Cortex-A510", 6, 2.00f),
            ),
            l1iPerBig = "64 KB", l1dPerBig = "64 KB", l2PerBig = "512 KB",
            l1iPerSmall = "64 KB", l1dPerSmall = "64 KB", l2PerSmall = "512 KB",
            l3Shared = "8 MB",
            gpuModel = "Mali-G610 MC4",
            gpuClockMhz = 1130,
            gpuAlus = 256,
            gpuFp32Tflops = 1.15f,
            isp = "Imagiq 765",
            npu = "APU 550",
            modem = "5G R16 (MediaTek M80)",
        ),

        // ═══ Dimensity 1080 (MT6879) ═══
        "mt6879" to KnownChip(
            platformId = "mt6879",
            chipName = "Dimensity 1080",
            cpuModel = "Cortex-A78 + A55",
            processNode = "6nm TSMC N6",
            releaseDate = "2022-10",
            clusters = listOf(
                ClusterSpec("Cortex-A78", 2, 2.60f),
                ClusterSpec("Cortex-A55", 6, 2.00f),
            ),
            l1iPerBig = "64 KB", l1dPerBig = "64 KB", l2PerBig = "256 KB",
            l1iPerSmall = "32 KB", l1dPerSmall = "32 KB", l2PerSmall = "128 KB",
            l3Shared = "2 MB",
            gpuModel = "Mali-G68 MC4",
            gpuClockMhz = 950,
            gpuAlus = 128,
            gpuFp32Tflops = 0.56f,
            isp = "Imagiq 355",
            npu = "APU 550",
            modem = "5G R16 (MediaTek M80)",
        ),

        // ═══ Dimensity 1200 (MT6893) — 修正: 原误标为 Dimensity 8100 ═══
        "mt6893" to KnownChip(
            platformId = "mt6893",
            chipName = "Dimensity 1200",
            cpuModel = "Cortex-A78 + A55",
            processNode = "6nm TSMC N6",
            releaseDate = "2021-01",
            clusters = listOf(
                ClusterSpec("Cortex-A78", 1, 3.00f),
                ClusterSpec("Cortex-A78", 3, 2.60f),
                ClusterSpec("Cortex-A55", 4, 2.00f),
            ),
            l1iPerBig = "64 KB", l1dPerBig = "64 KB", l2PerBig = "512 KB",
            l1iPerSmall = "32 KB", l1dPerSmall = "32 KB", l2PerSmall = "128 KB",
            l3Shared = "8 MB",
            gpuModel = "Mali-G77 MC9",
            gpuClockMhz = 850,
            gpuAlus = 576,
            gpuFp32Tflops = 0.98f,
            isp = "Imagiq 590",
            npu = "APU 3.0",
            modem = "5G R16 (MediaTek M80)",
        ),

        // ═══ Dimensity 8100 (MT6895) — 新增: 之前误挂在 MT6893 ═══
        "mt6895" to KnownChip(
            platformId = "mt6895",
            chipName = "Dimensity 8100",
            cpuModel = "Cortex-A78 + A55",
            processNode = "5nm TSMC N5",
            releaseDate = "2022-03",
            clusters = listOf(
                ClusterSpec("Cortex-A78", 4, 2.85f),
                ClusterSpec("Cortex-A55", 4, 2.00f),
            ),
            l1iPerBig = "64 KB", l1dPerBig = "64 KB", l2PerBig = "512 KB",
            l1iPerSmall = "32 KB", l1dPerSmall = "32 KB", l2PerSmall = "128 KB",
            l3Shared = "4 MB",
            gpuModel = "Mali-G610 MC6",
            gpuClockMhz = 860,
            gpuAlus = 384,
            gpuFp32Tflops = 1.56f,
            isp = "Imagiq 780",
            npu = "APU 580",
            modem = "5G R16 (MediaTek M80)",
        ),
    )

    // ═══════════════ 方法 ═══════════════

    /**
     * 剥离天玑营销后缀, 归一化到基础标识 (层1)。
     *   "Dimensity 9300-Ultra" → "dimensity 9300"
     *   "mt6897-ultra"         → "mt6897"
     *   "MT6989U"              → "mt6989"
     *   "MediaTek Dimensity 8300-Ultra" → "dimensity 8300"
     * 仅用于查找, 不用于显示 (UI 保留 OEM 营销名)。
     */
    internal fun stripMarketingSuffix(input: String): String {
        var s = input.trim().lowercase()
        // 厂商前缀 (ro.chipset 常返回 "MediaTek Dimensity 8300-Ultra")
        s = s.removePrefix("mediatek ").removePrefix("mtk ")
        // "+" 后缀 (Dimensity 9300+)
        s = PLUS_SUFFIX_PATTERN.replace(s, "")
        // 营销后缀 (-ultra / -max / ultra / x / e ...)
        s = MARKETING_SUFFIX_PATTERN.replace(s, "")
        // 紧贴数字的 U 后缀 (MT6989U → mt6989)
        s = TRAILING_U_PATTERN.replace(s, "")
        return s.trim()
    }

    /**
     * 营销名 → 硅片号 (层2)。不匹配返回 null。
     *   "dimensity 9300" → "mt6989"
     *   "dimensity 8300" → "mt6897"
     */
    internal fun marketingToSiliconId(input: String): String? {
        val normalized = stripMarketingSuffix(input)
        DIMENSITY_MARKETING_TO_SILICON[normalized]?.let { return it }
        // 去空格形式 (dimensity9300) 兜底
        DIMENSITY_MARKETING_TO_SILICON[normalized.replace(" ", "")]?.let { return it }
        return null
    }

    fun lookup(platform: String): KnownChip? {
        // ★ 规范化: 去空格/换行 + 小写
        val raw = platform.lowercase().trim()
        if (raw.isEmpty()) return null

        // ★ 天玑营销后缀归一化 (Ultra/Max/+/Ultimate/Turbo...):
        //   三星/OEM 在 ro.chipname 等属性写入带后缀营销名 (Dimensity 8300-Ultra / mt6897-ultra),
        //   归一化到基础硅片号后再走既有匹配链, 避免 "mt6897-ultra" 落入 DIMENSITY 兜底分支
        //   显示错误芯片名 (Dimensity 6897) 与空白制程。仅用于查找, UI 显示保留 OEM 营销名。
        val normalizedInput = stripMarketingSuffix(raw)
        if (normalizedInput != raw) {
            // 硅片号形式 (mt6897-ultra → mt6897) 直命中
            KNOWN_CHIPS[normalizedInput]?.let { return it }
            KNOWN_CHIPS.values.firstOrNull { it.platformId == normalizedInput }?.let { return it }
            // 营销名形式 (dimensity 8300-ultra → dimensity 8300 → mt6897) 经映射命中
            marketingToSiliconId(normalizedInput)?.let { sid ->
                KNOWN_CHIPS[sid]?.let { return it }
                KNOWN_CHIPS.values.firstOrNull { it.platformId == sid }?.let { return it }
            }
        }

        // ★ 按优先级依次尝试匹配
        // 策略1: 精确 key 匹配
        KNOWN_CHIPS[raw]?.let { return it }

        // 策略2: 精确 platformId 匹配
        KNOWN_CHIPS.values.firstOrNull { it.platformId == raw }?.let { return it }

        // ★ 策略3: 规范化 — 去除厂商前缀 (qcom/mediatek/qti/qualcomm) 后匹配
        val normalized = raw
            .removePrefix("qcom,")
            .removePrefix("qti ")
            .removePrefix("qualcomm ")
            .removePrefix("mediatek/")
            .removePrefix("mt")
        if (normalized != raw) {
            // 规范化后的精确 key 匹配
            KNOWN_CHIPS[normalized]?.let { return it }
            // 规范化后的 platformId 匹配
            KNOWN_CHIPS.values.firstOrNull { it.platformId == normalized }?.let { return it }
            // MTK: platformId (如 "mt6989") 的数值部分匹配 (如 "6989" → "mt6989")
            KNOWN_CHIPS.values.firstOrNull {
                it.platformId.startsWith("mt") && it.platformId.removePrefix("mt") == normalized
            }?.let { return it }
        }

        // ★ 策略4: 已知 codename 别名映射（常量表见文件顶部 CODENAME_ALIASES）
        CODENAME_ALIASES[raw]?.let { aliasKey ->
            KNOWN_CHIPS[aliasKey]?.let { return it }
        }

        // ★ 天玑家族兜底: mt67xx / mt68xx / mt69xx 全系
        //   表内已显式列出的型号（mt6989/6899/6897/6878 等）会在策略1/2 命中，
        //   此处仅兜底未逐型号录入的中低端天玑（如 mt6768/mt6833/mt6873/mt6885...），
        //   保证任意 MediaTek 天玑平台都能被识别为 MediaTek 天玑，而非"识别覆没"。
        //   注意：高通/三星/麒麟等既有匹配逻辑完全不受影响，仅新增此兜底分支。
        DIMENSITY_PATTERN.find(raw)?.let { match ->
            val num = match.value.removePrefix("mt")
            return KnownChip(
                platformId = raw,
                chipName = "Dimensity $num",
                cpuModel = "",
                processNode = "",
                releaseDate = "",
                clusters = emptyList(),
                l1iPerBig = "", l1dPerBig = "", l2PerBig = "",
                l1iPerSmall = "", l1dPerSmall = "", l2PerSmall = "",
                l3Shared = "",
                gpuModel = "",
                gpuClockMhz = 0,
                gpuAlus = 0,
                gpuFp32Tflops = 0f,
                isp = "",
                npu = "",
                modem = "",
            )
        }

        return null
    }

    /**
     * kona 平台细分 Snapdragon 865 / 870（SM8250 / SM8250-AC）。
     * 四级判定，任一命中即定；默认回退 865：
     *  1) ro.soc.model 含 "sm8250-ac" 或以 "-ac" 结尾 → 870
     *  2) ro.soc.id == 341 (870) / 356 (865)
     *  3) 任一 CPU 核心 cpuinfo_max_freq > 3.04GHz（870 Prime 3.19GHz / 865 2.84GHz）→ 870
     *  4) ro.chipname / ro.hardware.chipname 含 "870" / "ac" → 870
     */
    fun resolveKonaVariant(): String {
        val socModel = SysFsReader.readProp("ro.soc.model").lowercase()
        if (socModel.contains("sm8250-ac") || socModel.endsWith("-ac")) return "870"

        val socId = SysFsReader.readProp("ro.soc.id").toIntOrNull()
        if (socId == 341) return "870"   // SM8250-AC
        if (socId == 356) return "865"   // SM8250

        var maxPrimeHz = 0L
        for (i in 0..7) {
            val f = SysFsReader.readLong("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
            if (f > maxPrimeHz) maxPrimeHz = f
        }
        if (maxPrimeHz > 3_040_000L) return "870"

        val chip = SysFsReader.readProp("ro.chipname").lowercase()
        if (chip.contains("870") || chip.contains("ac")) return "870"

        return "865"
    }

    fun injectCpuInfo(chip: KnownChip, info: CpuInfo) {
        // ★ 硬件固化参数仅首次写入，跳过后续冗余覆盖
        //   os.arch 返回 "aarch64" 等内核架构字符串 (非空)，原 isEmpty() 条件永远跳过注入。
        //   改为判断是否为内核架构字符串再覆盖。
        val isKernelArch = info.architecture.isEmpty() ||
            info.architecture in setOf("aarch64", "armv7l", "armv8l", "x86", "x86_64", "riscv64", "unknown")
        if (isKernelArch) {
            info.architecture = chip.chipName + "\n" + chip.cpuModel
        }
        if (info.cacheL1.isEmpty()) {
            info.cacheL1 = "I:${chip.l1iPerBig} D:${chip.l1dPerBig} (大核) · I:${chip.l1iPerSmall} D:${chip.l1dPerSmall} (小核)"
        }
        if (info.cacheL2.isEmpty()) {
            info.cacheL2 = "${chip.l2PerBig} (大核) · ${chip.l2PerSmall} (小核)"
        }
        if (info.cacheL3.isEmpty()) {
            info.cacheL3 = chip.l3Shared
        }

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
            // Match each core to the nearest chip cluster by max frequency (15% tolerance)
            for (core in info.cores) {
                val bestCluster = chip.clusters.minByOrNull { cluster ->
                    val clusterFreqKHz = (cluster.maxFreqGHz * 1_000_000).toLong()
                    abs(core.maxFreqKHz - clusterFreqKHz)
                }
                if (bestCluster != null) {
                    val diffPercent = if (core.maxFreqKHz > 0) {
                        abs(core.maxFreqKHz - (bestCluster.maxFreqGHz * 1_000_000).toLong()).toFloat() / core.maxFreqKHz
                    } else 1f
                    if (diffPercent < 0.15f) {  // 15% 容差: DVFS scaling 允许一定误差
                        core.coreType = bestCluster.coreName
                    }
                }
            }
        }
    }

    fun injectGpuInfo(chip: KnownChip, info: GpuInfo) {
        if (info.model.isEmpty() || info.model.contains("kgsl", true)) {
            info.model = chip.gpuModel
        }
        if (info.maxFreqKHz <= 0) info.maxFreqKHz = chip.gpuClockMhz * 1000L
        if (info.minFreqKHz <= 0) info.minFreqKHz = (chip.gpuClockMhz * 1000L * 0.2).toLong()
    }
}
