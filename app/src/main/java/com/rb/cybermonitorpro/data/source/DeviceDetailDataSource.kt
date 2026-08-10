package com.rb.cybermonitorpro.data.source

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.display.DisplayManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaDrm
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.rb.cybermonitorpro.data.model.CameraSensorInfo
import com.rb.cybermonitorpro.data.model.DeviceDetailInfo
import com.rb.cybermonitorpro.util.waitForWithTimeout
import java.io.File
import java.util.UUID

// 设备详细信息数据源 - v3 (Sciverse + Android API + sysfs/procfs)
// 覆盖: CPU缓存/内存/存储/USB/蓝牙/WiFi/SoC制程/GPU显存/色深/热区
class DeviceDetailDataSource(private val context: Context) {

    companion object {
        private const val TAG = "DeviceDetailDS"
        private val WIDEVINE_UUID = UUID.fromString("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed")

        /** Vulkan 版本串 sanity 校验: 1.3 / 1.3.128 形态; 用于过滤 ro.hardware.vulkan 的 HAL 名 */
        private val VK_VERSION_REGEX = Regex("""^\d+\.\d+(\.\d+)?$""")

        /** BluetoothAdapter.getAddress() 自 Android 6.0 (API 23) 起对所有第三方应用返回的常量假值
         *  依据: developer.android.com/about/versions/marshmallow/android-6.0-changes (核验 2026-08-07)
         *  该限制无权限可豁免 —— 即便持有 BLUETOOTH_CONNECT 也仍返回此值。 */
        private const val BT_ADDRESS_PLACEHOLDER = "02:00:00:00:00:00"

        /** 语义键 — 与 strings.xml 中同名条目一一对应，由 UI 解析为本地化文案 */
        private const val BT_NAME_NEEDS_PERMISSION = "bt_name_needs_permission"
        private const val BT_ADDRESS_RESTRICTED = "bt_address_restricted"

        // ═══════════════════════════════════════════
        //  相机朝向语义键 — 替代旧实现直接写中文字面
        // ═══════════════════════════════════════════
        /** 与 strings.xml 同名条目一一对应: camera_facing_back / front / external / unknown */
        private const val CAMERA_FACING_BACK = "camera_facing_back"
        private const val CAMERA_FACING_FRONT = "camera_facing_front"
        private const val CAMERA_FACING_EXTERNAL = "camera_facing_external"
        private const val CAMERA_FACING_UNKNOWN = "camera_facing_unknown"
        private const val CAMERA_FLASH = "camera_flash"
        /** 3 级兜底: 系统声明有前置但两层枚举都拿不到时填充的占位 id */
        private const val LEGACY_FRONT_PLACEHOLDER_ID = "front_unknown"

        /** SoC 型号 → 制程工艺 查找表
         *
         * 数据来源: 高通/联发科/三星/海思官方文档 + 维基百科交叉验证
         * 制程标注格式: "节点 + 代工厂" (代工厂仅标注有差异的关键节点)
         * 本表仅覆盖 Android 设备 SoC, 不含 Apple A/M 系列
         */
        private val SOC_PROCESS_MAP = mapOf(
            // ═══════════════════════════════════════════
            //  Qualcomm Snapdragon — 8 Gen 旗舰系列
            // ═══════════════════════════════════════════
            "SM8850-5-AC" to "3nm TSMC N3P",     // Snapdragon 8 Elite Gen 5 (2026)
            "SM8850-AC" to "3nm TSMC N3P",      // 同上 — Qualcomm 官方 P/N 无 "-5-" 中缀，保留双 key 兼容不同读取路径
            "SM8750" to "3nm TSMC N3E",     // Snapdragon 8 Elite (Gen 4, 2024)
            "SM8650" to "4nm TSMC N4P",     // Snapdragon 8 Gen 3 (2023)
            "SM8635" to "4nm TSMC N4P",     // Snapdragon 8s Gen 3 (2024)
            "QTI SM8635" to "4nm TSMC N4P", // Snapdragon 8s Gen 3 (QTI 前缀) — Android 16 返回格式
            "SM8550" to "4nm TSMC N4P",     // Snapdragon 8 Gen 2 (2022) — Notebookcheck: N4P
            "SM8475" to "4nm TSMC N4",      // Snapdragon 8+ Gen 1 (2022) — TechInsights: N4
            "SM8450" to "4nm Samsung",      // Snapdragon 8 Gen 1 (2021) — 三星代工
            "SM8350" to "5nm Samsung",      // Snapdragon 888/888+ (2020/2021) — 三星代工
            "SM8250-AC" to "7nm TSMC N7P",     // Snapdragon 870 (2021)
            "SM8250" to "7nm TSMC N7P",     // Snapdragon 865/865+ (2019/2020)
            "SM8150" to "7nm TSMC N7",      // Snapdragon 855/855+ (2018/2019)
            "SDM855" to "7nm TSMC N7",      // Snapdragon 855 alternate
            "SDM845" to "10nm Samsung LPP",   // Snapdragon 845 (2018) — 三星代工
            "MSM8998" to "10nm Samsung",       // Snapdragon 835 (2017)
            "MSM8996" to "14nm Samsung",    // Snapdragon 820/821 (2016)
            "MSM8994" to "20nm",            // Snapdragon 810 (2015)
            "MSM8992" to "20nm",            // Snapdragon 808 (2015)
            "APQ8084" to "28nm",            // Snapdragon 805 (2014)
            "MSM8974AC" to "28nm",          // Snapdragon 801 (2014)
            "MSM8974" to "28nm",            // Snapdragon 800 (2013)

            // Qualcomm Snapdragon — 6xx 系列
            "SDM670" to "10nm Samsung LPP",     // Snapdragon 670 (2018)
            "SDM660" to "14nm Samsung",     // Snapdragon 660 (2017)
            "SDM636" to "14nm",             // Snapdragon 636 (2017)
            "SDM630" to "14nm",             // Snapdragon 630 (2017)
            "SDM625" to "14nm",             // Snapdragon 625 (2016)
            "SDM652" to "28nm",             // Snapdragon 652 (2016)
            "SDM650" to "28nm",             // Snapdragon 650 (2016)

            // Qualcomm Snapdragon — 4xx 系列
            "SDM439" to "12nm",             // Snapdragon 439 (2018)
            "SDM450" to "14nm",             // Snapdragon 450 (2017)
            "SDM430" to "28nm",             // Snapdragon 430 (2016)
            "SDM435" to "28nm",             // Snapdragon 435 (2016)

            // Qualcomm Snapdragon — 7 Gen 系列
            "SM7750" to "4nm TSMC",         // Snapdragon 7 Gen 4 (2025)
            "SM7550" to "4nm TSMC N4P",         // Snapdragon 7+ Gen 3 (2024)
            "SM7475" to "4nm TSMC",         // Snapdragon 7 Gen 3 (2023)
            "SM7450" to "4nm TSMC",         // Snapdragon 7 Gen 2 (2023)
            "SM7435" to "4nm TSMC",         // Snapdragon 7s Gen 3 (2024)
            "SM7350" to "5nm",              // Snapdragon 7 Gen 1 (2022)
            "SM7375" to "4nm Samsung",       // Snapdragon 7 Gen 1 (三星代工批次)
            "SM7325" to "6nm TSMC N6",      // Snapdragon 778G/778G+ (2021)
            "SM7315" to "6nm TSMC N6",      // Snapdragon 780G (2021)
            "SM7250" to "7nm TSMC N7",      // Snapdragon 765G (2019)
            "SM7225" to "6nm TSMC N6",      // Snapdragon 695 (2021)
            "SM7150" to "8nm TSMC N8",      // Snapdragon 730G (2019)

            // Qualcomm Snapdragon — 6 Gen 系列
            "SM6650" to "4nm TSMC N4P",      // Snapdragon 6 Gen 4 (2025)
            "SM6475" to "4nm Samsung",      // Snapdragon 6 Gen 3 (2024)
            "SM6450" to "4nm Samsung 4LPE", // Snapdragon 6 Gen 1 (2022) — 三星代工
            "SM6375" to "6nm TSMC N6",      // Snapdragon 695 (2022)
            "SM6365" to "6nm",              // Snapdragon 6s Gen 3 (2024)
            "SM6225" to "6nm TSMC N6",      // Snapdragon 680/685 (2021)
            "SM6115" to "11nm",             // Snapdragon 662/460 (2020)

            // Qualcomm Snapdragon — 4 Gen 系列
            "SM4460" to "4nm TSMC N4",      // Snapdragon 4 Gen 3 (2025)
            "SM4450" to "4nm TSMC N4",      // Snapdragon 4 Gen 2 (2023)
            "SM4375" to "6nm TSMC N6",      // Snapdragon 4 Gen 1 (2022)
            "SM4350" to "8nm",              // Snapdragon 480 (2021)
            "SM4250" to "11nm",             // Snapdragon 460 (2021)

            // ═══════════════════════════════════════════
            //  MediaTek Dimensity — 旗舰/高端
            // ═══════════════════════════════════════════
            "MT9400" to "3nm TSMC N3E",     // Dimensity 9400 (2024)
            "MT9300" to "4nm TSMC N4P",     // Dimensity 9300 (2023)
            "MT9200" to "4nm TSMC N4P",     // Dimensity 9200 (2022) — 营销号别名
            "MT6983" to "4nm TSMC N4P",     // Dimensity 9200 (2022) — 硅片号 (ro.soc.model 实际返回)
            "MT6896" to "4nm TSMC N4P",     // Dimensity 8200 (2022) — 硅片号
            "MT6886" to "4nm TSMC N4P",     // Dimensity 7200 (2023) — 硅片号
            "MT6989" to "4nm TSMC N4P",     // Dimensity 9300+ (2024)
            "MT6985" to "4nm TSMC N4",      // Dimensity 9000 (2022)
            "MT6897" to "4nm TSMC N4P",     // Dimensity 8300 (2023)
            "MT6893" to "6nm TSMC N6",      // Dimensity 1200/1300 (2021)
            "MT6895" to "5nm TSMC N5",      // Dimensity 8100 (2022) — 之前误标为 MT6893
            "MT6889" to "6nm TSMC N6",      // Dimensity 1100 (2021)
            "MT6885" to "7nm TSMC N7",      // Dimensity 1000+ (2020)
            "MT6880" to "7nm TSMC N7",      // Dimensity 1000 (2019)

            // MediaTek Dimensity — 中端
            "MT6879" to "4nm TSMC N4",      // Dimensity 1080 (2022)
            "MT6877" to "6nm TSMC N6",      // Dimensity 7050/1300 (2022)
            "MT6875" to "6nm TSMC N6",      // Dimensity 1200 (2021)
            "MT6855" to "6nm TSMC N6",      // Dimensity 6080/6100+ (2023)
            "MT6835" to "6nm TSMC N6",      // Dimensity 6020/7020 (2023)
            "MT6789" to "6nm TSMC N6",      // Helio G99 (2022)
            "MT6785" to "12nm",             // Helio G95/G90 (2019)
            "MT6781" to "6nm TSMC N6",      // Helio G96 (2021)
            "MT6779" to "12nm",             // Helio P90 (2018)
            "MT6771" to "12nm",             // Helio P70/P60 (2018)

            // MediaTek — 入门
            "MT6768" to "12nm",             // Helio P65/G70 (2019)
            "MT6765" to "12nm",             // Helio P35/G35 (2018)
            "MT6762" to "12nm",             // Helio P22 (2018)
            "MT6739" to "28nm",             // MT6739 (2017)

            // ═══════════════════════════════════════════
            //  Samsung Exynos
            // ═══════════════════════════════════════════
            "exynos2600" to "2nm Samsung",  // Exynos 2600 (2026, Gate-All-Around)
            "exynos2500" to "3nm Samsung GAA", // Exynos 2500 (2025)
            "exynos2400" to "4nm Samsung",  // Exynos 2400 (2024)
            "exynos2200" to "4nm Samsung",  // Exynos 2200 (2022)
            "exynos2100" to "5nm Samsung",  // Exynos 2100 (2021)
            "exynos1080" to "5nm Samsung",  // Exynos 1080 (2020)
            "exynos990" to "7nm Samsung",   // Exynos 990 (2020)
            "exynos9820" to "8nm Samsung",  // Exynos 9820 (2019)
            "exynos9810" to "10nm Samsung", // Exynos 9810 (2018)
            "exynos8890" to "14nm Samsung", // Exynos 8890 (2016)
            "exynos7420" to "14nm Samsung", // Exynos 7420 (2015)

            // ═══════════════════════════════════════════
            //  HiSilicon Kirin (华为海思)
            // ═══════════════════════════════════════════
            "kirin9020" to "7nm (SMIC)",    // Kirin 9020 (2025) — 中芯国际代工
            "kirin9010" to "7nm (SMIC)",    // Kirin 9010 (2024)
            "kirin9000s" to "7nm (SMIC)",   // Kirin 9000S (2023) — 中芯 N+2
            "kirin9000" to "5nm TSMC N5",   // Kirin 9000 (2020)
            "kirin990" to "7nm TSMC N7",    // Kirin 990 4G/5G (2019)
            "kirin985" to "7nm TSMC N7",    // Kirin 985 (2020)
            "kirin980" to "7nm TSMC N7",    // Kirin 980 (2018)
            "kirin820" to "7nm TSMC N7",    // Kirin 820 (2020)
            "kirin810" to "7nm TSMC N7",    // Kirin 810 (2019)
            "kirin710" to "12nm TSMC",      // Kirin 710 (2018)
            "kirin960" to "16nm TSMC",      // Kirin 960 (2016)
            "kirin950" to "16nm TSMC",      // Kirin 950 (2015)

            // ═══════════════════════════════════════════
            //  Google Tensor
            // ═══════════════════════════════════════════
            "gsp" to "3nm TSMC N3E",        // Tensor G5 (2025) — 台积电代工，弃三星
            "gs201" to "5nm Samsung",       // Tensor G4 (2024) / G2 (2022)
            "gs101" to "5nm Samsung",       // Tensor G1 (2021)
            "zuma" to "4nm Samsung",        // Tensor G3 (2023) — 代号 zuma

            // ═══════════════════════════════════════════
            //  UNISOC (紫光展锐)
            // ═══════════════════════════════════════════
            "t770" to "6nm TSMC N6",        // Tiger T770 (2022)
            "t760" to "6nm TSMC N6",        // Tiger T760 (2022)
            "t620" to "12nm",               // Tiger T620 (2022)
            "t618" to "12nm",               // Tiger T618 (2020)
            "t610" to "12nm",               // Tiger T610 (2020)
            "t606" to "12nm",               // Tiger T606 (2021)
            "t310" to "28nm",               // Tiger T310 (2019)
        )

        // ★ S3 (2026-08-10): 小写键查找表 — 策略1a 大小写不敏感精确查表恢复 O(1),
        //   替代原 entries.firstOrNull{ equals(ignoreCase) } 的 O(条目数) 全扫。
        //   lazy 构建避免类加载期额外开销; mapKeys 遇重复小写键保留最后一个 (表内无冲突)。
        private val SOC_PROCESS_MAP_LC: Map<String, String> by lazy {
            SOC_PROCESS_MAP.mapKeys { it.key.lowercase() }
        }

        /**
         * 平台 codename → 制程查找表
         * 用于 ro.board.platform 返回 codename 时的二次推断
         * 数据来源: 各平台内核 dts 配置 + OEM 固件分析
         */
        private val PLATFORM_PROCESS_MAP = mapOf(
            // Qualcomm — 旗舰 codename
            "kona" to "7nm TSMC N7P",           // SM8250 — Snapdragon 865
            "lahaina" to "4nm Samsung",         // SM8450 — Snapdragon 8 Gen 1
            "taro" to "4nm TSMC N4",            // SM8475 — Snapdragon 8+ Gen 1
            "cape" to "4nm TSMC N4",            // SM8475 — Snapdragon 8+ Gen 1 (alternate)
            "kalama" to "4nm TSMC N4P",         // SM8550 — Snapdragon 8 Gen 2 (修正拼写 kailua→kalama)
            "pineapple" to "4nm TSMC N4P",      // SM
            "sun" to "3nm TSMC N3E",         // SM8750 — Snapdragon 8 Elite (修正制程 N4P→N3E)
            "shima" to "5nm Samsung",           // SM8350 — Snapdragon 888
            "lito" to "7nm TSMC N7",            // SM7250 — Snapdragon 765G
            // Qualcomm — 中端 codename 
            "parrot" to "4nm TSMC",             // SM7475 — Snapdragon 7 Gen 3
            "crow" to "4nm TSMC N4P",               // SM7550 — Snapdragon 7+ Gen 3
            "diwali" to "4nm TSMC",             // SM7450 — Snapdragon 7 Gen 2
            "yupik" to "6nm TSMC N6",           // SM7325 — Snapdragon 778G
            "palima" to "6nm TSMC N6",          // SM7325 — Snapdragon 778G+
            "holi" to "6nm TSMC N6",            // SM6225 — Snapdragon 680/685
            "bengal" to "8nm TSMC N8",          // SM6115 — Snapdragon 662/460
            // Qualcomm — 旧 codename
            "sdm660" to "14nm Samsung",
            "sdm845" to "10nm Samsung LPP",
            "msm8998" to "10nm Samsung",
            // ★ #6a: 补齐缺失平台代号 (注: msm8996/8994/8974 已在 SOC_PROCESS_MAP, 不重复搬入)
            "msmnile" to "7nm TSMC N7",         // SM8150 — Snapdragon 855/855+/860
            "sm6150" to "8nm Samsung LPP",      // Snapdragon 730/730G
            // MediaTek
            "mt6983" to "4nm TSMC N4",
            "mt6895" to "5nm TSMC N5",
            "mt6877" to "6nm TSMC N6",
            "mt6853" to "7nm TSMC N7",
            // Samsung
            "exynos2200" to "4nm Samsung",
            "universal2400" to "4nm Samsung",
            "universal2100" to "5nm Samsung",
            // HiSilicon
            "kirin9000" to "5nm TSMC N5",
        )

        /** ★ #6e: 已知短键白名单 — 合法标识符长度 < 5, 需放行模糊匹配 (避免被长度守卫误杀) */
        private val SHORT_KEY_WHITELIST = setOf("gsp", "t310")

        /** ARM CPU Part → 架构 + 缓存 映射 */
        private val ARM_CPU_PART_MAP = mapOf(
            // Cortex-A 系列
            "0xd05" to CpuArchInfo("Cortex-A72", "ARMv8-A", 48, 32, 1024, 0),
            "0xd07" to CpuArchInfo("Cortex-A73", "ARMv8-A", 64, 64, 512, 0),
            "0xd08" to CpuArchInfo("Cortex-A75", "ARMv8.2-A", 64, 64, 256, 0),
            "0xd09" to CpuArchInfo("Cortex-A76", "ARMv8.2-A", 64, 64, 512, 0),
            "0xd0a" to CpuArchInfo("Cortex-A76AE", "ARMv8.2-A", 64, 64, 512, 0),
            "0xd0b" to CpuArchInfo("Cortex-A77", "ARMv8.2-A", 64, 64, 512, 0),
            "0xd0d" to CpuArchInfo("Cortex-A78", "ARMv8.2-A", 64, 64, 512, 0),
            "0xd41" to CpuArchInfo("Cortex-A78C", "ARMv8.2-A", 64, 64, 512, 0),
            "0xd44" to CpuArchInfo("Cortex-X1", "ARMv8.2-A", 64, 64, 512, 0),
            "0xd4c" to CpuArchInfo("Cortex-X2", "ARMv9-A", 64, 64, 512, 0),
            "0xd4e" to CpuArchInfo("Cortex-X3", "ARMv9-A", 64, 64, 1024, 0),
            "0xd4f" to CpuArchInfo("Cortex-X4", "ARMv9.2-A", 64, 64, 1024, 0),
            "0xd47" to CpuArchInfo("Cortex-A710", "ARMv9-A", 64, 64, 512, 0),
            "0xd48" to CpuArchInfo("Cortex-A715", "ARMv9-A", 64, 64, 512, 0),
            "0xd4b" to CpuArchInfo("Cortex-A720", "ARMv9.2-A", 64, 64, 512, 0),
            // Cortex-A 小核
            "0xd03" to CpuArchInfo("Cortex-A53", "ARMv8-A", 16, 16, 128, 0),
            "0xd04" to CpuArchInfo("Cortex-A35", "ARMv8-A", 16, 16, 128, 0),
            "0xd06" to CpuArchInfo("Cortex-A55", "ARMv8.2-A", 16, 16, 128, 0),
            "0xd46" to CpuArchInfo("Cortex-A510", "ARMv9-A", 32, 32, 256, 0),
            "0xd49" to CpuArchInfo("Cortex-A510r1", "ARMv9-A", 32, 32, 256, 0),
            "0xd4a" to CpuArchInfo("Cortex-A520", "ARMv9.2-A", 32, 32, 256, 0),
            // Qualcomm Kryo
            // ★ #6b: 初代 Kryo (SD820/821) — 缺失时 SD820 会落入频率猜测被错标 A78/A55
            //   L1/L2 为社区共识值 (Kryo 200 系: 32K I$ / 24K D$, 大核簇 L2 更大)
            "0x201" to CpuArchInfo("Kryo Gold", "ARMv8-A", 32, 24, 512, 0),
            "0x205" to CpuArchInfo("Kryo Silver", "ARMv8-A", 32, 24, 256, 0),
            "0x802" to CpuArchInfo("Kryo 585 Gold", "ARMv8.2-A", 64, 64, 512, 0),
            "0x803" to CpuArchInfo("Kryo 585 Silver", "ARMv8.2-A", 16, 16, 128, 0),
            "0x804" to CpuArchInfo("Kryo 670 Gold", "ARMv8.2-A", 64, 64, 512, 0),
            "0x805" to CpuArchInfo("Kryo 670 Silver", "ARMv8.2-A", 16, 16, 128, 0),
        )

        // Camera2 API 28+ 常量反射 — 避免低版本设备 dex 验证崩溃
        private var cachedOisKey: Any? = null
        private var cachedOisModeOn: Int = -1
        private var camera2Resolved = false

        private fun resolveCamera2Constants() {
            if (camera2Resolved) return
            camera2Resolved = true
            try {
                val f = CameraCharacteristics::class.java.getField("LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION")
                cachedOisKey = f.get(null)
            } catch (_: Throwable) {}
            try {
                val f = CameraCharacteristics::class.java.getField("LENS_OPTICAL_STABILIZATION_MODE_ON")
                cachedOisModeOn = f.getInt(null)
            } catch (_: Throwable) {}
        }
    }

    data class CpuArchInfo(
        val name: String, val arch: String,
        val l1iKb: Int, val l1dKb: Int, val l2Kb: Int, val l3Kb: Int
    )

    fun collect(): DeviceDetailInfo {
        val info = DeviceDetailInfo()
        collectDisplay(info)
        collectGpu(info)
        collectVulkan(info)
        collectCpuCache(info)
        collectCpuTopology(info)
        collectSocProcess(info)
        collectMemoryType(info)
        collectStorageType(info)
        collectUsb(info)
        collectBluetooth(info)
        collectWifiStandard(info)
        collectCodecs(info)
        collectDrm(info)
        collectTelephony(info)
        collectCamera(info)
        collectAudio(info)
        collectThermal(info)
        collectSecurity(info)
        collectIdentifiers(info)  // Android ID / 序列号
        collectRuntimeEnv(info)   // Java Runtime / OpenSSL / Build timestamp
        collectMisc(info)
        return info
    }

    // ═══════════════════════════════════════════
    //  Display — v3: +色深/色域
    // ═══════════════════════════════════════════
    private fun collectDisplay(info: DeviceDetailInfo) {
        try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            info.resolution = "${metrics.widthPixels} × ${metrics.heightPixels}"
            info.densityDpi = metrics.densityDpi
            info.density = metrics.density

            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            dm?.getDisplay(android.view.Display.DEFAULT_DISPLAY)?.let { display ->
                val mode = display.mode
                info.refreshRateHz = mode?.refreshRate ?: 0f

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    info.hdrCapabilities = getHdrTypesReflective(display)
                }

                info.displayTechnology = detectDisplayTechnology()
            }

            val xdpi = metrics.xdpi
            val ydpi = metrics.ydpi
            if (xdpi > 0 && ydpi > 0) {
                val w = metrics.widthPixels / xdpi
                val h = metrics.heightPixels / ydpi
                info.physicalSizeInches = kotlin.math.sqrt(w * w + h * h).toFloat()
            }

            info.maxBrightnessNits = detectMaxBrightness()
            info.colorDepth = detectColorDepth()
            info.colorGamut = detectColorGamut()
        } catch (e: Throwable) { Log.w(TAG, "Display采集失败", e) }
    }

    /** 显示面板技术检测: 仅使用 sysfs 属性和系统 feature (OLED) */
    private fun detectDisplayTechnology(): String {
        return try {
            val panel = SysFsReader.readProp("ro.display.series")
            if (panel.contains("oled", ignoreCase = true) || panel.contains("amoled", ignoreCase = true))
                return if (panel.contains("ltpo", ignoreCase = true)) "LTPO AMOLED" else "AMOLED"
            val tech = SysFsReader.readProp("ro.vendor.display.type")
            if (tech.isNotEmpty()) return tech
            if (context.packageManager.hasSystemFeature("android.hardware.display.oled")) "OLED"
            else ""  // 不做推断，不确定时留空
        } catch (_: Throwable) { "" }
    }

    private fun detectMaxBrightness(): Int {
        return try {
            ShellCommandDataSource.getDumpsysDisplay()
                .lines()
                .firstOrNull { it.contains("brightness", ignoreCase = true) && it.contains("nit") }
                ?.let { line ->
                    Regex("""(\d+)\s*nit""").find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                } ?: 0
        } catch (_: Throwable) { 0 }
    }

    /**
     * 色深检测 — 修复 (2026-08-10, 审查 C1 🔴):
     *
     * 旧实现把帧缓冲 bpp/3 当作面板每通道色深, 而 fb0 几乎所有机型都是 RGBA8888=32bpp,
     * 32/3=10 → 把标准 8-bit 面板系统性误报为 "10-bit" 并上屏 (DeviceScreen:238)。
     *
     * 修正: Android 无公开可靠的面板每通道色深 API, 只能分级近似:
     *   1) 厂商明确声明的面板色深属性 (最权威, 多数未设置)
     *   2) 帧缓冲位宽的「无歧义」精确映射 (24=RGB888→8bit, 30=RGBX1010102→10bit, 36→12bit)
     *      — 32bpp 同时覆盖 8-bit(RGBA8) 与 10-bit(ABGR2101010), 不可断言, 一律留空
     *   3) 留空: UI 以 takeIf{isNotEmpty()} 隐藏该行, 不谎报
     */
    private fun detectColorDepth(): String {
        return try {
            // 1) 厂商明确声明的面板色深 (最权威)
            val vendorBit = SysFsReader.readProp("ro.vendor.display.bit")
            if (vendorBit == "10") return "10-bit"
            if (vendorBit == "12") return "12-bit"
            if (vendorBit == "8") return "8-bit"

            // 2) 帧缓冲位宽 — 仅映射无歧义值; 32bpp 不推断 (同时覆盖 RGBA8 与 ABGR2101010)
            val bpp = SysFsReader.readFile("/sys/class/graphics/fb0/bits_per_pixel")
                .trim().toIntOrNull() ?: 0
            when (bpp) {
                24 -> "8-bit"     // RGB888
                30 -> "10-bit"    // RGBX1010102
                36 -> "12-bit"    // 罕见
                else -> ""        // 32 及其它: 不推断, 留空 (不谎报)
            }

            // 注: 旧方法2 (ro.display.bpp) 沿用同一 bpp/3 缺陷, 已删除 —
            //     该属性多数 ROM 未设置且同样不可靠, 保留只会重复误报。
        } catch (_: Throwable) { "" }
    }

    /** 色域检测: 基于属性或 HDR 推断 */
    private fun detectColorGamut(): String {
        return try {
            val gamut = SysFsReader.readProp("ro.vendor.display.gamut")
            if (gamut.isNotEmpty()) return gamut

            val wideColor = SysFsReader.readProp("ro.surface_flinger.has_wide_color_display")
            if (wideColor == "true") return "DCI-P3"

            // HDR 支持的设备通常支持 P3
            val p3 = SysFsReader.readProp("ro.vendor.display.color_mode")
            if (p3.contains("p3", ignoreCase = true) || p3.contains("dci", ignoreCase = true))
                return "DCI-P3"

            ""
        } catch (_: Throwable) { "" }
    }

    // ═══════════════════════════════════════════
    //  GPU (OpenGL ES) — v4: +GLSL版本/最大纹理/压缩纹理/扩展详情
    // ═══════════════════════════════════════════
    private fun collectGpu(info: DeviceDetailInfo) {
        try {
            // GL ES 版本 — 使用 ActivityManager 官方 API
            // systemAvailableFeatures 中只有 "android.hardware.opengles.aep"(扩展包)，不含版本号
            var reqGlVersion = try {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
                val config = am?.deviceConfigurationInfo
                val glVerInt = config?.reqGlEsVersion ?: 0x00030000
                // 格式: 0x00030002 → "3.2", 0x00030001 → "3.1", 0x00030000 → "3.0"
                val major = (glVerInt shr 16) and 0xFFFF
                val minor = glVerInt and 0xFFFF
                if (major >= 2) "$major.$minor" else ""
            } catch (_: Throwable) { "" }

            // 回退: EGL14.eglInitialize 获取版本
            if (reqGlVersion.isEmpty()) {
                try {
                    val display = android.opengl.EGL14.eglGetDisplay(android.opengl.EGL14.EGL_DEFAULT_DISPLAY)
                    if (display != android.opengl.EGL14.EGL_NO_DISPLAY) {
                        val vers = IntArray(2)
                        if (android.opengl.EGL14.eglInitialize(display, vers, 0, vers, 1)) {
                            reqGlVersion = "${vers[0]}.${vers[1]}"
                        }
                        android.opengl.EGL14.eglTerminate(display)
                    }
                } catch (_: Throwable) {}
            }

            info.glEsVersion = if (reqGlVersion.isNotEmpty()) "OpenGL ES $reqGlVersion" else "OpenGL ES"

            // GPU 型号: 安全获取 — 使用 EGL14 + 系统属性 (避免 GLES20 非GL线程 SIGSEGV)
            resolveGpuInfoSafe(info)

            // GPU 专用显存 (高通 Adreno kgsl)
            info.gpuLocalMemoryKb = detectGpuLocalMemory()
        } catch (e: Throwable) { Log.w(TAG, "GPU采集失败", e) }
    }

    /** GPU 信息安全获取 — 使用 EGL14 + 系统属性，避免非GL线程调用 GLES20 导致 SIGSEGV */
    private fun resolveGpuInfoSafe(info: DeviceDetailInfo) {
        // 策略1: EGL14 — 可在任意线程安全调用 (只需 display，不需 context)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                val display = android.opengl.EGL14.eglGetDisplay(android.opengl.EGL14.EGL_DEFAULT_DISPLAY)
                if (display != android.opengl.EGL14.EGL_NO_DISPLAY) {
                    val vers = IntArray(2)
                    if (android.opengl.EGL14.eglInitialize(display, vers, 0, vers, 1)) {
                        info.glVendor = android.opengl.EGL14.eglQueryString(display, android.opengl.EGL14.EGL_VENDOR) ?: ""
                        // EGL_VERSION 含 "1.4 Android META-EGL" 等
                        val eglVer = android.opengl.EGL14.eglQueryString(display, android.opengl.EGL14.EGL_VERSION) ?: ""
                        if (eglVer.isNotEmpty()) info.gpuDriverVersion = "EGL $eglVer"
                        val eglExt = android.opengl.EGL14.eglQueryString(display, android.opengl.EGL14.EGL_EXTENSIONS) ?: ""
                        if (eglExt.isNotEmpty()) {
                            info.glExtensions = eglExt.split(" ").filter { it.isNotBlank() }
                        }

                        // 提取关键 EGL 扩展信息
                        val eglExts = info.glExtensions.toSet()
                        val keyExts = eglExts.filter { ext ->
                            ext.startsWith("GL_") || ext.startsWith("EGL_KHR") ||
                            ext.contains("texture_compression") || ext.contains("astc") ||
                            ext.contains("etc2") || ext.contains("s3tc")
                        }
                        if (keyExts.isNotEmpty()) {
                            info.glKeyExtensions = keyExts
                        }

                        android.opengl.EGL14.eglTerminate(display)
                    }
                }
            }
        } catch (_: Throwable) {}

        // 策略2: 系统属性 GPU 型号 (主要来源)
        if (info.glRenderer.isEmpty()) {
            info.glRenderer = SysFsReader.readProp("ro.gpu.chip")
                .ifEmpty { SysFsReader.readProp("ro.hardware.egl") }
                .ifEmpty { SysFsReader.readProp("ro.board.platform") }
        }
        if (info.glVendor.isEmpty()) {
            info.glVendor = SysFsReader.readProp("ro.soc.manufacturer")
        }
        if (info.gpuDriverVersion.isEmpty()) {
            info.gpuDriverVersion = SysFsReader.readProp("ro.gles.version")
                .ifEmpty { SysFsReader.readProp("ro.hardware.egl") }
        }
    }
    private fun detectGpuLocalMemory(): Int {
        return try {
            // 高通 Adreno: /sys/class/kgsl/kgsl-3d0/gmem
            val kgslGmem = SysFsReader.readFile("/sys/class/kgsl/kgsl-3d0/gmem")
                .trim().toIntOrNull() ?: 0
            if (kgslGmem > 0) return kgslGmem

            // Mali: /sys/class/misc/mali/device/gpu_mem
            val maliMem = SysFsReader.readFile("/sys/class/misc/mali/device/gpu_mem")
                .trim().toIntOrNull() ?: 0
            if (maliMem > 0) return maliMem

            // OpenCL 检测: CL_DEVICE_GLOBAL_MEM_SIZE (仅 log)
            0
        } catch (_: Throwable) { 0 }
    }

    // ═══════════════════════════════════════════
    //  Vulkan — 真实扩展枚举 (native) + OEM 提示兜底
    // ═══════════════════════════════════════════
    //  ★ 重构 (2026-08-07):
    //    此前 vulkanExtensions / vulkanDeviceCount 从未被赋值，DeviceScreen 的
    //    Vulkan 扩展区块 (DeviceScreen.kt:172-199) 是永不执行的死代码。
    //    现由 VulkanProbe (JNI + dlopen libvulkan.so) 提供真实枚举。
    //
    //    光追判据链为 OR 语义，任一命中即判定支持；OEM 提示判据全部保留不移除：
    //      0) 真实扩展枚举命中 ray_tracing_pipeline / ray_query / VK_NV_ray_tracing → "vulkan-ext"  (实测)
    //      1) systemAvailableFeatures 里含 vulkan+ray 的 feature 名                 → "feature-name" (推断)
    //      2) hasSystemFeature("android.hardware.vulkan.ray_tracing")               → "system-feature"(推断)
    //      3) ro.vendor.gpu.ray_tracing == 1/true                                   → "vendor-prop"  (推断)
    //    真实枚举不可用时 (API<24 / 非 arm64-v8a / 无 Vulkan 驱动) 自动退化为原有
    //    1)~3) 链，行为与重构前完全一致。
    private fun collectVulkan(info: DeviceDetailInfo) {
        try {
            val pm = context.packageManager

            // ── 0. native 真实枚举（第 0 优先级，唯一可信来源）──
            val probe = VulkanProbe.probe()
            if (probe != null) {
                info.vulkanExtensions = probe.extensions
                info.vulkanDeviceCount = probe.deviceCount
                if (probe.gpuName.isNotEmpty()) info.vulkanGpuName = probe.gpuName
                // VkPhysicalDeviceProperties.apiVersion 精确到 patch，优于 feature 的 major.minor
                if (probe.apiVersion.isNotEmpty()) info.vulkanVersion = probe.apiVersion
                if (VulkanProbe.isRayTracingCapable(probe)) {
                    info.rayTracingSupported = true
                    info.rayTracingSource = "vulkan-ext"
                }
            }

            // ── 1. PackageManager features（版本/硬件级别来源 + 光追判据 1）──
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val features = pm.systemAvailableFeatures
                for (f in features) {
                    val name = f.name ?: continue
                    when {
                        // native 已给出精确版本时不回退覆盖
                        name == "android.hardware.vulkan.version" ->
                            if (info.vulkanVersion.isEmpty()) info.vulkanVersion = extractVulkanVersion(f)
                        name == "android.hardware.vulkan.level" -> info.vulkanApiLevel = extractVulkanLevel(f)
                        name.contains("vulkan") && name.contains("ray") -> {
                            info.rayTracingSupported = true
                            if (info.rayTracingSource.isEmpty()) info.rayTracingSource = "feature-name"
                        }
                    }
                }
            }
            if (info.vulkanVersion.isEmpty()) {
                // ⚠️ ro.hardware.vulkan 在多数 ROM 上是 Vulkan HAL 名 (adreno / mali)，
                //    并非版本号；直接赋值会让 UI 显示成 "Vulkan adreno"。
                //    保留该 fallback，但加 sanity 校验只接受数字版本串。
                val vkProp = SysFsReader.readProp("ro.hardware.vulkan")
                if (VK_VERSION_REGEX.matches(vkProp)) info.vulkanVersion = vkProp
            }
            if (info.vulkanApiLevel.isEmpty()) {
                val level = SysFsReader.readPropInt("ro.vulkan.api.level")
                if (level > 0) info.vulkanApiLevel = when (level) {
                    1 -> "Vulkan 1.0"; 2 -> "Vulkan 1.1"; 3 -> "Vulkan 1.3"
                    else -> "Level $level"
                }
            }

            // ── 2/3. OEM 提示兜底（按需求保留，不移除）──
            if (!info.rayTracingSupported) {
                if (pm.hasSystemFeature("android.hardware.vulkan.ray_tracing")) {
                    info.rayTracingSupported = true
                    info.rayTracingSource = "system-feature"
                }
            }
            if (!info.rayTracingSupported) {
                val rtProp = SysFsReader.readProp("ro.vendor.gpu.ray_tracing")
                if (rtProp == "1" || rtProp == "true") {
                    info.rayTracingSupported = true
                    info.rayTracingSource = "vendor-prop"
                }
            }
        } catch (e: Throwable) { Log.w(TAG, "Vulkan采集失败", e) }
    }

    /** ★ 语义修正 (2026-08-07): 返回纯数字版本串，"Vulkan " 前缀由 UI 拼接，
     *  否则 DeviceScreen 二次拼接会显示成 "Vulkan Vulkan 1.3"。 */
    private fun extractVulkanVersion(feature: android.content.pm.FeatureInfo): String {
        return try {
            val ver = feature.version
            val major = ver shr 22
            val minor = (ver shr 12) and 0x3FF
            val patch = ver and 0xFFF
            if (major > 0) "$major.$minor.$patch" else ""
        } catch (_: Throwable) { "" }
    }

    private fun extractVulkanLevel(feature: android.content.pm.FeatureInfo): String {
        return try {
            when (feature.version) {
                0 -> "No Vulkan"; 1 -> "Vulkan 1.0"; 2 -> "Vulkan 1.1"; 3 -> "Vulkan 1.3"
                else -> "Level ${feature.version}"
            }
        } catch (_: Throwable) { "" }
    }

    // ═══════════════════════════════════════════
    //  CPU Cache Architecture (新增)
    // ═══════════════════════════════════════════
    private fun collectCpuCache(info: DeviceDetailInfo) {
        try {
            // 策略1: /sys/devices/system/cpu/cpu0/cache/index*/size
            val sysCache = readCpuCacheFromSysfs()
            if (sysCache.l1iKb > 0) {
                info.cpuCacheL1iKb = sysCache.l1iKb
                info.cpuCacheL1dKb = sysCache.l1dKb
                info.cpuCacheL2Kb = sysCache.l2Kb
                info.cpuCacheL3Kb = sysCache.l3Kb
                info.cpuCacheSource = "sysfs"
                return
            }

            // 策略2: /proc/cpuinfo "CPU part" → 查表
            val part = SysFsReader.readProp("ro.soc.model")
            val cpuPartFromProc = readCpuPartFromProcCpuinfo()
            val lookupPart = cpuPartFromProc.ifEmpty { part.lowercase() }
            val archInfo = ARM_CPU_PART_MAP[lookupPart]
            if (archInfo != null) {
                info.cpuCacheL1iKb = archInfo.l1iKb
                info.cpuCacheL1dKb = archInfo.l1dKb
                info.cpuCacheL2Kb = archInfo.l2Kb
                info.cpuCacheL3Kb = archInfo.l3Kb
                info.cpuCacheSource = "lookup:${archInfo.name}"
                return
            }

            info.cpuCacheSource = "common_unavailable"
        } catch (e: Throwable) { Log.w(TAG, "CPU缓存采集失败", e) }
    }

    private data class CacheInfo(val l1iKb: Int, val l1dKb: Int, val l2Kb: Int, val l3Kb: Int)

    private fun readCpuCacheFromSysfs(): CacheInfo {
        var l1i = 0; var l1d = 0; var l2 = 0; var l3 = 0
        try {
            val cpuDir = File("/sys/devices/system/cpu/cpu0/cache")
            if (!cpuDir.exists()) return CacheInfo(0, 0, 0, 0)
            for (indexDir in cpuDir.listFiles()?.filter { it.name.startsWith("index") } ?: emptyList()) {
                val level = SysFsReader.readFile("${indexDir.absolutePath}/level").trim().toIntOrNull() ?: continue
                val type = SysFsReader.readFile("${indexDir.absolutePath}/type").trim()
                val sizeStr = SysFsReader.readFile("${indexDir.absolutePath}/size").trim()
                val sizeKb = parseCacheSize(sizeStr)
                when {
                    level == 1 && type == "Instruction" -> l1i = sizeKb
                    level == 1 && type == "Data" -> l1d = sizeKb
                    level == 2 -> l2 = sizeKb
                    level == 3 -> l3 = sizeKb
                }
            }
        } catch (_: Throwable) {}
        return CacheInfo(l1i, l1d, l2, l3)
    }

    private fun parseCacheSize(sizeStr: String): Int {
        return try {
            when {
                sizeStr.endsWith("K", ignoreCase = true) -> sizeStr.dropLast(1).toIntOrNull() ?: 0
                sizeStr.endsWith("M", ignoreCase = true) -> (sizeStr.dropLast(1).toFloatOrNull() ?: 0f).toInt() * 1024
                sizeStr.endsWith("G", ignoreCase = true) -> (sizeStr.dropLast(1).toFloatOrNull() ?: 0f).toInt() * 1024 * 1024
                else -> sizeStr.toIntOrNull() ?: 0
            }
        } catch (_: Throwable) { 0 }
    }

    // ★ 修复(P2): cpuinfo 读一次复用 — 原 readCpuPart + collectCpuTopology 各读一次
    private var cachedCpuinfo: String? = null
    private fun readProcCpuinfo(): String {
        cachedCpuinfo?.let { return it }
        return try { File("/proc/cpuinfo").readText().also { cachedCpuinfo = it } } catch (_: Throwable) { "" }
    }

    private fun readCpuPartFromProcCpuinfo(): String {
        return try {
            val cpuInfo = readProcCpuinfo()
            // 取第一个 CPU part 行
            val partMatch = Regex("""CPU part\s*:\s*(\S+)""", RegexOption.IGNORE_CASE)
                .find(cpuInfo)
            partMatch?.groupValues?.get(1)?.lowercase() ?: ""
        } catch (_: Throwable) { "" }
    }

    // ═══════════════════════════════════════════
    //  CPU Topology (新增)
    // ═══════════════════════════════════════════
    private fun collectCpuTopology(info: DeviceDetailInfo) {
        try {
            // /proc/cpuinfo 中提取
            val cpuInfo = readProcCpuinfo()
            val implMatch = Regex("""CPU implementer\s*:\s*(\S+)""", RegexOption.IGNORE_CASE).find(cpuInfo)
            val partMatch = Regex("""CPU part\s*:\s*(\S+)""", RegexOption.IGNORE_CASE).find(cpuInfo)

            info.cpuImplementer = implMatch?.groupValues?.get(1) ?: ""
            info.cpuPart = partMatch?.groupValues?.get(1) ?: ""

            // 架构推断
            val impl = info.cpuImplementer.lowercase()
            info.cpuArchitecture = when {
                impl == "0x41" || impl == "41" -> { // ARM
                    val part = info.cpuPart.lowercase()
                    val archInfo = ARM_CPU_PART_MAP[part]
                    archInfo?.arch ?: "ARMv8-A"
                }
                impl == "0x51" || impl == "51" -> "ARMv8-A (Qualcomm)"
                impl == "0x48" || impl == "48" -> "ARMv8-A (HiSilicon)"
                impl == "0x53" || impl == "53" -> "ARMv8-A (Samsung)"
                impl == "0x4d" || impl == "4d" -> "ARMv8-A (MediaTek)"
                else -> ""
            }

            // big.LITTLE 拓扑: 统计不同频率组的核心数
            detectBigLITTLETopology(info)
        } catch (e: Throwable) { Log.w(TAG, "CPU拓扑采集失败", e) }
    }

    /**
     * 检测 big.LITTLE 拓扑 — 同时填入结构化字段供 UI 层 i18n 参数化。
     * info.bigLITTLE 存语义 key ("cpu_topology" / "cpu_same_freq" / "")，
     * info.cpuBigCores / cpuLittleCores 存整数值。
     */
    private fun detectBigLITTLETopology(info: DeviceDetailInfo) {
        try {
            val freqGroups = mutableMapOf<Long, Int>()
            var cpuIndex = 0
            while (true) {
                val maxFreq = SysFsReader.readFile("/sys/devices/system/cpu/cpu$cpuIndex/cpufreq/cpuinfo_max_freq")
                    .trim().toLongOrNull() ?: break
                val current = SysFsReader.readFile("/sys/devices/system/cpu/cpu$cpuIndex/topology/cluster_id")
                    .trim().toIntOrNull() ?: 0
                val key = maxFreq
                freqGroups[key] = (freqGroups[key] ?: 0) + 1
                cpuIndex++
                if (cpuIndex > 16) break
            }
            when {
                freqGroups.size >= 2 -> {
                    val sorted = freqGroups.entries.sortedByDescending { it.key }
                    info.cpuBigCores = sorted[0].value
                    info.cpuLittleCores = sorted[1].value
                    info.bigLITTLE = "cpu_topology"
                }
                freqGroups.size == 1 -> {
                    info.cpuBigCores = freqGroups.values.first()
                    info.cpuLittleCores = 0
                    info.bigLITTLE = "cpu_same_freq"
                }
                else -> {
                    info.bigLITTLE = ""
                }
            }
        } catch (_: Throwable) {
            info.bigLITTLE = ""
        }
    }

    // ═══════════════════════════════════════════
    //  SoC Process Node (新增)
    // ═══════════════════════════════════════════
    private fun collectSocProcess(info: DeviceDetailInfo) {
        try {
            // 策略0: CpuCache 已知芯片数据库 (最精确)
            val platform = SysFsReader.readProp("ro.board.platform")
            // kona 平台需细分 865 / 870（SM8250 / SM8250-AC），否则 870 会被误判为 865
            val lookupKey = if (platform.equals("kona", ignoreCase = true)) {
                if (CpuCache.resolveKonaVariant() == "870") "sm8250-ac" else "sm8250"
            } else platform
            val knownChip = CpuCache.lookup(lookupKey)
            if (knownChip != null && knownChip.processNode.isNotEmpty()) {
                info.socProcessNode = knownChip.processNode
                info.socProcessNodeSource = "chipdb:${knownChip.chipName}"
                return
            }

            // 构建 SoC 识别标识符列表 — 多个属性联合判断
            val socCandidates = listOfNotNull(
                SysFsReader.readProp("ro.soc.model"),
                platform,
                SysFsReader.readProp("ro.chipname"),
                SysFsReader.readProp("ro.hardware.chipname"),
                SysFsReader.readProp("ro.chipset"),
                SysFsReader.readProp("ro.board.chipname"),
                SysFsReader.readProp("ro.product.board"),
                SysFsReader.readProp("ro.mediatek.platform"),
                SysFsReader.readProp("ro.hardware"),
            ).filter { it.isNotBlank() }.distinct()

            // ★ #11e: 移除 Log.d 全表打印 (每次采集 dump 整个 SOC_PROCESS_MAP keys, release 包白跑字符串拼接)

            // 策略1: 精确查表 (每项标识符分别尝试)
            for (socModel in socCandidates) {
                // 策略1a: 精确查表 (硅片号/营销号直接命中) — ★ #6d: 大小写不敏感查表
                // ★ S3 (2026-08-10): 改用 SOC_PROCESS_MAP_LC 小写键 O(1) 查找,
                //   替代原 entries.firstOrNull{ equals(ignoreCase) } 的 O(条目数) 全扫。
                SOC_PROCESS_MAP_LC[socModel.lowercase()]?.let { v ->
                    info.socProcessNode = v
                    info.socProcessNodeSource = "lookup:$socModel"
                    return
                }
                // 策略1b: 天玑营销后缀归一化 (Dimensity 8300-Ultra / mt6897-ultra → MT6897)
                //   SOC_PROCESS_MAP key 为大写硅片号 (MT6897), 故归一化后转大写再查。
                //   仅用于查找; UI 显示仍保留 OEM 营销名。
                val stripped = CpuCache.stripMarketingSuffix(socModel)
                val normalizedKey = (CpuCache.marketingToSiliconId(stripped) ?: stripped).uppercase()
                SOC_PROCESS_MAP[normalizedKey]?.let {
                    info.socProcessNode = it
                    info.socProcessNodeSource = "lookup:$normalizedKey (from $socModel)"
                    return
                }
            }

            // 策略2: 模糊匹配 (每项标识符分别尝试)
            for (socModel in socCandidates) {
                for ((key, value) in SOC_PROCESS_MAP) {
                    // 双向 contains 匹配，避免误伤
                    if (socModel.contains(key, ignoreCase = true) || key.contains(socModel, ignoreCase = true)) {
                        // 防止短数字误匹配（如 "820" 匹配到 "SM820" 而非 "SDM820"）
                        // ★ #6e: 放行已知短键白名单 (gsp=3, t310=4), 其余短键仍按长度守卫拦截
                        if (key.length >= 5 || socModel.length >= 5 || key.lowercase() in SHORT_KEY_WHITELIST) {
                            info.socProcessNode = value
                            info.socProcessNodeSource = "lookup:~$key"
                            Log.d(TAG, "SoC模糊匹配: $socModel → $key → $value")
                            return
                        }
                    }
                }
            }

            // 策略3: 根据平台 codename 二次推断
            if (platform.isNotBlank()) {
                val platformProcess = PLATFORM_PROCESS_MAP[platform.lowercase()]
                if (platformProcess != null) {
                    info.socProcessNode = platformProcess
                    info.socProcessNodeSource = "platform:$platform"
                    return
                }
            }

            // 策略4: SystemProperties 直接读取 (极少设备)
            val directProp = SysFsReader.readProp("ro.soc.process_node")
            if (directProp.isNotEmpty()) {
                info.socProcessNode = directProp
                info.socProcessNodeSource = "property"
                return
            }

            info.socProcessNodeSource = "common_unavailable"
        } catch (e: Throwable) { Log.w(TAG, "SoC制程采集失败", e) }
    }

    // ═══════════════════════════════════════════
    //  Memory Type (新增) — LPDDR4X/5/5X
    // ═══════════════════════════════════════════
    private fun collectMemoryType(info: DeviceDetailInfo) {
        try {
            // 策略1: SystemProperties
            val ddrType = SysFsReader.readProp("ro.boot.ddr_type")
                .ifEmpty { SysFsReader.readProp("ro.ddr_type") }
                .ifEmpty { SysFsReader.readProp("ro.vendor.ddr_type") }
            if (ddrType.isNotEmpty()) {
                info.memoryType = parseDdrType(ddrType)
                info.memoryTypeSource = "property"
            }

            // 策略2: sysfs (高通)
            if (info.memoryType.isEmpty()) {
                val ddrInfo = SysFsReader.readFile("/sys/devices/platform/soc/soc:qcom,ddr_type")
                    .trim()
                if (ddrInfo.isNotEmpty()) {
                    info.memoryType = parseDdrType(ddrInfo)
                    info.memoryTypeSource = "sysfs"
                }
            }

            // 策略3: dumpsys (间接推断)
            if (info.memoryType.isEmpty()) {
                val memInfo = ShellCommandDataSource.getDumpsysMeminfo()
                if (memInfo.contains("LPDDR5", ignoreCase = true)) {
                    info.memoryType = "LPDDR5"
                    info.memoryTypeSource = "dumpsys"
                }
            }

            // 内存频率
            info.memorySpeedMhz = try {
                val freq = SysFsReader.readProp("ro.boot.ddr_freq")
                    .ifEmpty { SysFsReader.readFile("/sys/devices/platform/soc/soc:qcom,ddr_freq").trim() }
                freq.toIntOrNull() ?: 0
            } catch (_: Throwable) { 0 }
        } catch (e: Throwable) { Log.w(TAG, "内存类型采集失败", e) }
    }

    private fun parseDdrType(raw: String): String {
        val v = raw.lowercase()
        return when {
            v.contains("lpddr5x") || v == "5x" || v == "6" -> "LPDDR5X"
            v.contains("lpddr5") || v == "5" -> "LPDDR5"
            v.contains("lpddr4x") || v == "4x" || v == "4" -> "LPDDR4X"
            v.contains("lpddr4") -> "LPDDR4"
            v.contains("lpddr3") -> "LPDDR3"
            else -> raw
        }
    }

    // ═══════════════════════════════════════════
    //  Storage Type (新增) — UFS/eMMC
    // ═══════════════════════════════════════════
    private fun collectStorageType(info: DeviceDetailInfo) {
        try {
            // 策略1: SystemProperties
            val storageType = SysFsReader.readProp("ro.boot.bootstorage")
                .ifEmpty { SysFsReader.readProp("ro.boot.storage_type") }
                .ifEmpty { SysFsReader.readProp("ro.vendor.storage_type") }
                .ifEmpty { SysFsReader.readProp("ro.boot.hwstorage") }
            if (storageType.isNotEmpty()) {
                info.storageType = parseStorageType(storageType)
                info.storageTypeSource = "property"
            }

            // 策略2: /sys/block/sda/ 设备类型
            if (info.storageType.isEmpty()) {
                val deviceType = SysFsReader.readFile("/sys/block/sda/device/type").trim()
                val deviceModel = SysFsReader.readFile("/sys/block/sda/device/model").trim()
                if (deviceModel.contains("UFS", ignoreCase = true)) {
                    info.storageType = deviceModel
                    info.storageTypeSource = "sysfs"
                } else if (deviceType.isNotEmpty()) {
                    info.storageType = when (deviceType) {
                        "0" -> "UFS"
                        "1" -> "eMMC"  // SCSI type
                        else -> deviceType
                    }
                    info.storageTypeSource = "sysfs"
                }
            }

            // 策略3: 检查 UFS 特征路径
            if (info.storageType.isEmpty()) {
                val ufsPath = File("/sys/devices/platform/soc").listFiles()
                    ?.any { it.name.contains("ufs", ignoreCase = true) }
                if (ufsPath == true) {
                    info.storageType = "UFS"
                    info.storageTypeSource = "sysfs:ufs_path"
                }
            }

            // 策略4: 读取 UFS 版本
            if (info.storageType.contains("UFS", ignoreCase = true) && !info.storageType.contains("3.") && !info.storageType.contains("2.")) {
                // ★ 修复(F2): 通配符路径不展开 glob，改用 listFiles 枚举真实路径 (参考策略3)
                val ufsVer = File("/sys/devices/platform/soc").listFiles()
                    ?.firstOrNull { it.name.contains("ufs", ignoreCase = true) }
                    ?.let { SysFsReader.readFile("${it.absolutePath}/versions") }
                    .orEmpty()
                if (ufsVer.contains("3.1")) info.storageType = "UFS 3.1"
                else if (ufsVer.contains("3.0")) info.storageType = "UFS 3.0"
                else if (ufsVer.contains("4.0")) info.storageType = "UFS 4.0"
            }

            // SCSI 协议标识
            info.storageProtocol = try {
                val proto = SysFsReader.readFile("/sys/block/sda/device/scsi_level").trim()
                if (proto.isNotEmpty()) "SCSI Level $proto" else ""
            } catch (_: Throwable) { "" }
        } catch (e: Throwable) { Log.w(TAG, "存储类型采集失败", e) }
    }

    private fun parseStorageType(raw: String): String {
        val v = raw.lowercase()
        return when {
            v.contains("ufs4") || v == "4" -> "UFS 4.0"
            v.contains("ufs3.1") || v == "7" -> "UFS 3.1"
            v.contains("ufs3.0") || v == "6" -> "UFS 3.0"
            v.contains("ufs2.2") || v == "5" -> "UFS 2.2"
            v.contains("ufs2.1") || v == "4" -> "UFS 2.1"
            v.contains("ufs") -> "UFS"
            v.contains("emmc5.1") -> "eMMC 5.1"
            v.contains("emmc") -> "eMMC"
            else -> raw
        }
    }

    // ═══════════════════════════════════════════
    //  USB (增强) — 版本/Type-C
    // ═══════════════════════════════════════════
    private fun collectUsb(info: DeviceDetailInfo) {
        try {
            val pm = context.packageManager
            info.usbHostMode = pm.hasSystemFeature("android.hardware.usb.host")

            // Type-C 检测：仅基于物理口型相关信号（不与配件模式混淆）
            // 1) OEM 板级属性（主信号）
            val typecProp = SysFsReader.readProp("ro.hardware.usb.typec")
            // 2) 内核 typec class 注册（OEM 无关；新内核迁移至 /sys/bus/typec，两路都查）
            val typecSysfs = SysFsReader.fileExists("/sys/class/typec")
                || SysFsReader.fileExists("/sys/bus/typec")
            // 注意：android.hardware.usb.accessory 是 USB 配件模式(AOA 协议)，与物理口型无关，不可作 Type-C 判据
            info.usbTypeC = typecProp == "1" || typecProp == "true" || typecSysfs

            // MicroUSB：无干净负向 sysprop；非 Type-C 的 Android 手机口型几乎都是 MicroUSB（推断）
            info.usbMicroUsb = !info.usbTypeC

            // USB 版本: 仅基于实际 sysfs/usb_speed 属性
            info.usbVersion = try {
                // USB 版本: 仅基于实际属性读取
                when {
                    pm.hasSystemFeature("android.hardware.usb.host") -> {
                        val speed = SysFsReader.readProp("ro.boot.usb_speed")
                            .ifEmpty { SysFsReader.readProp("ro.usb.speed") }
                            .ifEmpty { SysFsReader.readProp("persist.sys.usb.speed") }
                        when {
                            speed.contains("super_speed_plus", ignoreCase = true) -> "USB 3.2"
                            speed.contains("super_speed", ignoreCase = true) || speed.contains("super", ignoreCase = true) -> "USB 3.0"
                            speed.contains("high", ignoreCase = true) -> "USB 2.0"
                            else -> ""  // 无法确定时不推断
                        }
                    }
                    else -> ""  // ★ 修复(F3): 原写死 "USB 2.0" 与上面哲学矛盾，改为不推断
                }
            } catch (_: Throwable) { "" }

            // 充电连接检测 (已有)
            try {
                val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                val batteryStatus = context.registerReceiver(null, filter)
                val plugged = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
                info.usbConnected = (plugged == BatteryManager.BATTERY_PLUGGED_USB)
            } catch (_: Throwable) {}
        } catch (e: Throwable) { Log.w(TAG, "USB采集失败", e) }
    }

    // ═══════════════════════════════════════════
    //  Bluetooth (增强) — 版本/LE
    // ═══════════════════════════════════════════
    private fun collectBluetooth(info: DeviceDetailInfo) {
        try {
            val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
            if (adapter != null) {
                info.bluetoothSupported = true

                // ★ 重构 (2026-08-07): 名称/地址的权限与系统限制处理
                val hasConnectPerm = hasBluetoothConnectPermission()
                info.bluetoothPermissionGranted = hasConnectPerm
                info.bluetoothName = readBluetoothName(adapter, hasConnectPerm)
                info.bluetoothAddress = readBluetoothAddress(adapter)

                // BLE 支持
                info.bleSupported = context.packageManager.hasSystemFeature("android.hardware.bluetooth_le")

                // 蓝牙版本推断
                info.bluetoothVersion = detectBluetoothVersion()

                // LE Audio (Android 13+)
                info.bluetoothLeAudio = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    context.packageManager.hasSystemFeature("android.hardware.bluetooth_le_audio")
            }
        } catch (_: Throwable) {}
    }

    /**
     * BLUETOOTH_CONNECT 是否已授予。
     *
     * 官方依据 (核验 2026-08-07, developer.android.com/develop/connectivity/bluetooth/bt-permissions):
     *   · API 31 (Android 12) 起 BLUETOOTH_CONNECT 为**运行时**权限，属「附近的设备」权限组，
     *     必须经 ActivityResultContracts.RequestPermission 显式申请。
     *   · API 30 及以下由 manifest 的 BLUETOOTH（安装时权限）覆盖，恒为已授予。
     */
    private fun hasBluetoothConnectPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return try {
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) { false }
    }

    /**
     * 蓝牙本机名称。三级链：
     *   1) 已授权 → BluetoothAdapter.getName()（权威）
     *   2) 未授权 / 个别 ROM 仍拦截 → Settings.Secure "bluetooth_name"
     *      该键是系统设置项，读取不需要任何蓝牙权限，多数 ROM 上可用（⚠️ 非公开常量，属社区实测路径）
     *   3) 都拿不到且未授权 → 返回语义键，UI 渲染成可点击的「点击授权」
     */
    private fun readBluetoothName(
        adapter: android.bluetooth.BluetoothAdapter,
        hasPermission: Boolean
    ): String {
        if (hasPermission) {
            try {
                val n = adapter.name
                if (!n.isNullOrBlank()) return n
            } catch (_: SecurityException) {
                // 个别 ROM 即便已授权仍拦截，落下一级
            } catch (_: Throwable) {}
        }
        try {
            val n = Settings.Secure.getString(context.contentResolver, "bluetooth_name")
            if (!n.isNullOrBlank()) return n
        } catch (_: Throwable) {}
        return if (hasPermission) "" else BT_NAME_NEEDS_PERMISSION
    }

    /**
     * 蓝牙本机 MAC。
     *
     * ⚠️ 官方原文 (developer.android.com/about/versions/marshmallow/android-6.0-changes，核验 2026-08-07):
     *    自 Android 6.0 (API 23) 起 BluetoothAdapter.getAddress() 对所有第三方应用
     *    恒返回常量 02:00:00:00:00:00。**该限制无任何权限可豁免** —— 持有
     *    BLUETOOTH_CONNECT 同样拿不到真实地址。
     *
     * 因此这里仍实际调用而非按 SDK_INT 硬判（万一某 ROM 给出真值就照实显示），
     * 但一旦识别出占位值就返回语义键，由 UI 渲染成「系统限制」，不展示假 MAC 误导用户。
     */
    private fun readBluetoothAddress(adapter: android.bluetooth.BluetoothAdapter): String {
        val raw = try {
            @Suppress("MissingPermission")
            adapter.address
        } catch (_: SecurityException) { null } catch (_: Throwable) { null }
        return when {
            raw.isNullOrBlank() -> BT_ADDRESS_RESTRICTED
            raw == BT_ADDRESS_PLACEHOLDER -> BT_ADDRESS_RESTRICTED
            else -> raw
        }
    }

    /** 蓝牙版本检测: 仅使用 sysfs 和系统属性实际值，不做 API 级别推断 */
    private fun detectBluetoothVersion(): String {
        return try {
            // SystemProperties 直接读取
            val btVer = SysFsReader.readProp("ro.bluetooth.version")
            if (btVer.isNotEmpty()) return "BT $btVer"

            // sysfs HCI 版本
            val hciVer = SysFsReader.readFile("/sys/class/bluetooth/hci0/version").trim()
            if (hciVer.isNotEmpty()) return "BT HCI $hciVer"

            // 扩展 sysfs 路径
            val btChipVer = SysFsReader.readProp("ro.boot.bt.version")
                .ifEmpty { SysFsReader.readProp("ro.vendor.bt.version") }
                .ifEmpty { SysFsReader.readProp("persist.vendor.bt.version") }
            if (btChipVer.isNotEmpty()) return "BT $btChipVer"

            // 无实际数据时不推断
            ""
        } catch (_: Throwable) { "" }
    }

    // ═══════════════════════════════════════════
    //  Wi-Fi Standard (新增) — Wi-Fi 4/5/6/6E/7
    // ═══════════════════════════════════════════
    private fun collectWifiStandard(info: DeviceDetailInfo) {
        try {
            val pm = context.packageManager

            // 策略1: WifiInfo.getWifiStandard() (API 33+) — 使用反射避免 OEM ROM dex 验证崩溃
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val wm = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val connectionInfo = wm?.connectionInfo
                if (connectionInfo != null) {
                    val standard = getWifiStandardReflective(connectionInfo)
                    if (standard > 0) {
                        info.wifiStandard = when (standard) {
                            1 -> "Wi-Fi 4 (802.11n)"
                            2 -> "Wi-Fi 5 (802.11ac)"
                            3 -> "Wi-Fi 6 (802.11ax)"
                            4 -> "Wi-Fi 6E"
                            5 -> "Wi-Fi 7 (802.11be)"
                            else -> "标准 $standard"
                        }
                        if (info.wifiStandard.isNotEmpty()) info.wifiStandardSource = "API 33+"
                    }
                }
            }

            // 策略2: 仅使用实际属性值，不做推断
            if (info.wifiStandard.isEmpty()) {
                val has6 = SysFsReader.readProp("ro.boot.wifi.ax") == "1" ||
                    SysFsReader.readProp("ro.vendor.wifi.ax") == "1"
                val has6E = SysFsReader.readProp("ro.boot.wifi.6e") == "1" ||
                    SysFsReader.readProp("ro.vendor.wifi.6e_support") == "1"
                val has7 = SysFsReader.readProp("ro.boot.wifi.be") == "1" ||
                    SysFsReader.readProp("ro.vendor.wifi.be_support") == "1"

                info.wifiStandard = when {
                    has7 -> "Wi-Fi 7 (802.11be)"
                    has6E -> "Wi-Fi 6E"
                    has6 -> "Wi-Fi 6 (802.11ax)"
                    else -> ""  // 不做推测
                }
                info.wifiStandardSource = "property"
            }

            // 6GHz 支持
            info.wifi6EEnabled = SysFsReader.readProp("ro.vendor.wifi.6e_support") == "1" ||
                SysFsReader.readProp("ro.boot.wifi.6e") == "1"

            // Wi-Fi Aware (NAN)
            info.wifiAware = pm.hasSystemFeature("android.hardware.wifi.aware")
        } catch (e: Throwable) { Log.w(TAG, "Wi-Fi标准采集失败", e) }
    }

    // ═══════════════════════════════════════════
    //  Media Codecs — v4: 细化格式识别
    // ═══════════════════════════════════════════
    private fun collectCodecs(info: DeviceDetailInfo) {
        try {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            val audio = mutableListOf<String>()
            val video = mutableListOf<String>()
            val hwCodecs = mutableListOf<String>()

            for (codecInfo in codecList.codecInfos) {
                if (codecInfo.isEncoder) continue
                val name = codecInfo.name
                val formatName = extractCodecFormat(name, codecInfo.supportedTypes)
                val display = if (formatName != name) formatName else simplifyCodecName(name)

                when {
                    codecInfo.isAudioCodec() -> {
                        if (display.isNotEmpty() && !audio.contains(display)) audio.add(display)
                        // 检测 Hi-Res 音频编解码器
                        if (formatName in listOf("FLAC", "ALAC", "LDAC", "aptX HD")) {
                            // 这些是 Hi-Res 编解码器，已通过音频格式检测覆盖
                        }
                    }
                    codecInfo.isVideoCodec() -> {
                        if (display.isNotEmpty() && !video.contains(display)) video.add(display)
                    }
                }

                // 硬件加速检测
                if (codecInfo.name.startsWith("OMX.") || codecInfo.name.startsWith("c2.")) {
                    val simpleName = formatName.ifEmpty { simplifyCodecName(name) }
                    if (simpleName.isNotEmpty() && !hwCodecs.contains(simpleName)) hwCodecs.add(simpleName)
                }
            }

            info.audioCodecs = audio.ifEmpty { extractRawCodecs(codecList, true) }
            info.videoCodecs = video.ifEmpty { extractRawCodecs(codecList, false) }
            info.hwAcceleratedCodecs = hwCodecs
        } catch (e: Throwable) { Log.w(TAG, "编解码器采集失败", e) }
    }

    /** 从 raw codec name 提取人类可读格式名 */
    private fun extractCodecFormat(codecName: String, types: Array<String>): String {
        val name = codecName.lowercase()
        val mimeTypes = types.map { it.split("/").lastOrNull() ?: "" }

        // 视频格式
        for (mime in mimeTypes) {
            when {
                mime.contains("avc") || mime.contains("h264") -> return "H.264 (AVC)"
                mime.contains("hevc") || mime.contains("h265") -> return "HEVC (H.265)"
                mime.contains("vp9") -> return "VP9"
                mime.contains("vp8") -> return "VP8"
                mime.contains("av1") || mime.contains("av01") -> return "AV1"
                mime.contains("mpeg4") -> return "MPEG-4"
                mime.contains("h263") -> return "H.263"
                mime.contains("mpeg2") -> return "MPEG-2"
                mime.contains("vc1") -> return "VC-1"
                mime.contains("vp6") -> return "VP6"
                mime.contains("divx") -> return "DivX"
                mime.contains("xvid") -> return "Xvid"
                mime.contains("wmv") -> return "WMV"
            }
        }

        // 音频格式
        for (mime in mimeTypes) {
            when {
                mime.contains("aac") -> return "AAC"
                mime.contains("mp3") || mime.contains("mpeg") -> return "MP3"
                mime.contains("flac") -> return "FLAC"
                mime.contains("opus") -> return "Opus"
                mime.contains("vorbis") -> return "Vorbis"
                mime.contains("amr-wb") -> return "AMR-WB"
                mime.contains("amr-nb") || mime.contains("amr.") -> return "AMR-NB"
                mime.contains("raw") || mime.contains("pcm") -> return "PCM"
                mime.contains("ac4") -> return "AC-4"
                mime.contains("ac3") || mime.contains("eac3") -> return "Dolby (AC-3/E-AC3)"
                mime.contains("alac") -> return "ALAC"
                mime.contains("ape") -> return "APE"
                mime.contains("wma") -> return "WMA"
                mime.contains("g711") || mime.contains("mulaw") || mime.contains("alaw") -> return "G.711"
                mime.contains("gsm") -> return "GSM"
            }
        }

        // 从 codec name 推断
        return simplifyCodecName(codecName)
    }

    /** 从 codec 名称简化*/
    private fun simplifyCodecName(name: String): String {
        val n = name.lowercase()
        return when {
            n.contains("avc") || n.contains("h264") -> "H.264"
            n.contains("hevc") || n.contains("h265") -> "HEVC"
            n.contains("vp9.dec") -> "VP9"
            n.contains("vp8.dec") -> "VP8"
            n.contains("av1.dec") -> "AV1"
            n.contains("mpeg4") -> "MPEG-4"
            n.contains("aac.dec") -> "AAC"
            n.contains("mp3.dec") -> "MP3"
            n.contains("flac.dec") -> "FLAC"
            n.contains("opus.dec") -> "Opus"
            n.contains("vorbis") -> "Vorbis"
            n.contains("amrnb") || n.contains("amr.nb") -> "AMR-NB"
            n.contains("amrwb") -> "AMR-WB"
            n.contains("raw.dec") || n.contains("pcm") -> "PCM"
            n.contains("ac4") -> "AC-4"
            n.contains("ac3") -> "AC-3"
            n.contains("alac") -> "ALAC"
            else -> name  // 保持原名
        }
    }

    /** 回退: 提取原始 codec 名 */
    private fun extractRawCodecs(codecList: MediaCodecList, audio: Boolean): List<String> {
        val result = mutableListOf<String>()
        for (c in codecList.codecInfos) {
            if (c.isEncoder) continue
            val isAudio = try { c.supportedTypes.any { it.startsWith("audio/") } } catch (_: Throwable) { false }
            if (audio == isAudio) result.add(c.name)
        }
        return result
    }

    private fun MediaCodecInfo.isAudioCodec(): Boolean {
        return try { supportedTypes.any { it.startsWith("audio/") } } catch (_: Throwable) { false }
    }

    private fun MediaCodecInfo.isVideoCodec(): Boolean {
        return try { supportedTypes.any { it.startsWith("video/") } } catch (_: Throwable) { false }
    }

    // ═══════════════════════════════════════════
    //  DRM / Widevine
    // ═══════════════════════════════════════════
    private fun collectDrm(info: DeviceDetailInfo) {
        try {
            val drm = MediaDrm(WIDEVINE_UUID)
            info.widevineLevel = drm.getPropertyString("securityLevel") ?: ""
            drm.release()
        } catch (_: Throwable) { info.widevineLevel = "widevine_unsupported" }

        val schemes = mutableListOf<String>()
        try {
            if (MediaDrm.isCryptoSchemeSupported(WIDEVINE_UUID)) schemes.add("Widevine")
            if (MediaDrm.isCryptoSchemeSupported(UUID.fromString("9a04f079-9840-4286-ab92-e65be0885f95")))
                schemes.add("PlayReady")
            info.drmSchemes = schemes
        } catch (_: Throwable) {}
    }

    // ═══════════════════════════════════════════
    //  Telephony / SIM
    // ═══════════════════════════════════════════
    private fun collectTelephony(info: DeviceDetailInfo) {
        try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return
            info.simOperator = tm.simOperatorName ?: ""
            info.simMccMnc = "${tm.simOperator}"
            info.networkCountryIso = tm.networkCountryIso ?: ""
            info.phoneType = when (tm.phoneType) {
                TelephonyManager.PHONE_TYPE_GSM -> "GSM"
                TelephonyManager.PHONE_TYPE_CDMA -> "CDMA"
                TelephonyManager.PHONE_TYPE_SIP -> "SIP"
                else -> "common_unknown"
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                try { info.isDualSim = tm.phoneCount > 1 } catch (_: Throwable) {}
            }
        } catch (e: Throwable) { Log.w(TAG, "SIM采集失败", e) }
    }

    // ═══════════════════════════════════════════
    //  Camera — 真实传感器检测
    // ═══════════════════════════════════════════
    private fun collectCamera(info: DeviceDetailInfo) {
        try {
            // minSdk 21 == LOLLIPOP，camera2 恒可用，原有的 SDK_INT 守卫是死条件，已移除
            collectCameraSensors(info)
            info.cameraIds = buildCameraSummary(info)
        } catch (e: Throwable) { Log.w(TAG, "相机采集失败", e) }
    }

    /**
     * 相机摘要行。
     * ★ 修改 (2026-08-07): 优先由 camera2 实测朝向反推，实测为空才退回 PackageManager feature。
     *   旧实现只看 feature，而 FEATURE_CAMERA / FEATURE_CAMERA_FRONT 在部分 ROM 上缺声明，
     *   导致明明枚举到了前置、摘要里却不显示。
     *   返回值为**语义键**，由 UI 解析成本地化文案（旧实现直接写中文，英文环境会串语言）。
     */
    private fun buildCameraSummary(info: DeviceDetailInfo): List<String> {
        val pm = context.packageManager
        val facings = info.cameraSensors.mapTo(HashSet()) { it.facing }
        val hasFlash = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH) ||
                info.cameraSensors.any { it.flashSupported }
        return buildList {
            if (facings.isNotEmpty()) {
                if (CAMERA_FACING_BACK in facings) add(CAMERA_FACING_BACK)
                if (CAMERA_FACING_FRONT in facings) add(CAMERA_FACING_FRONT)
                if (CAMERA_FACING_EXTERNAL in facings) add(CAMERA_FACING_EXTERNAL)
                if (CAMERA_FACING_UNKNOWN in facings) add(CAMERA_FACING_UNKNOWN)
            } else {
                // camera2 完全不可用时的兜底（保留旧判据）
                if (pm.hasSystemFeature(PackageManager.FEATURE_CAMERA)) add(CAMERA_FACING_BACK)
                if (pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)) add(CAMERA_FACING_FRONT)
                if (this@buildList.isEmpty() && pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) add(CAMERA_FACING_UNKNOWN)
            }
            if (hasFlash) add(CAMERA_FLASH)
        }
    }

    /**
     * 相机传感器枚举 — 前置检测 5 级 fallback 链 (★ 重构 2026-08-07)
     *
     * 加强动机与官方依据 (核验 2026-08-07):
     *   · CameraManager.getCameraIdList() 官方原文:
     *     "This list doesn't contain physical cameras that can only be used as part of
     *      a logical multi-camera device." → 逻辑多摄机型上部分镜头在这一层不可见。
     *   · CameraManager.getCameraCharacteristics() 官方原文:
     *     "From API level 29, this function can also be used to query the capabilities of
     *      physical cameras that can only be used as part of logical multi-camera."
     *     → API 29+ 可用 getPhysicalCameraIds() 展开物理子摄。
     *
     * 链路:
     *   1) cameraIdList + LENS_FACING                       (逻辑相机，主路径)
     *   2) API 29+ getPhysicalCameraIds() 展开物理子摄        (逻辑多摄机型的隐藏镜头)
     *   3) HAL1 android.hardware.Camera.getCameraInfo()      (camera2 藏前置的 ROM)
     *   4) PackageManager.FEATURE_CAMERA_FRONT               (系统声明有前置但无法枚举)
     *   5) camera id 启发式 "0"=后置 / "1"=前置               (⚠️ 社区惯例，仅纠正 unknown)
     */
    private fun collectCameraSensors(info: DeviceDetailInfo) {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
            // LinkedHashMap: 按 id 去重且保持枚举顺序
            val sensors = LinkedHashMap<String, CameraSensorInfo>()

            // ── 1 级: 逻辑相机 ──
            val camIds = try { cameraManager.cameraIdList } catch (_: Throwable) { emptyArray() }
            for (id in camIds) {
                readCameraSensor(cameraManager, id)?.let { sensors[id] = it }
            }

            // ── 2 级: API 29+ 展开逻辑多摄的物理子摄 ──
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                for (id in camIds) {
                    val physicalIds = try {
                        cameraManager.getCameraCharacteristics(id).physicalCameraIds
                    } catch (_: Throwable) { emptySet<String>() }
                    for (pid in physicalIds) {
                        if (sensors.containsKey(pid)) continue
                        readCameraSensor(cameraManager, pid)?.let { sensors[pid] = it }
                    }
                }
            }

            // ── 3/4 级: camera2 一个前置都没枚举到时的兜底 ──
            if (sensors.values.none { it.facing == CAMERA_FACING_FRONT }) {
                val legacyFrontId = detectLegacyFrontCameraId()
                when {
                    legacyFrontId != null -> {
                        val existing = sensors[legacyFrontId]
                        if (existing != null) {
                            // camera2 把它读成 external/unknown，用 HAL1 的朝向纠正
                            existing.facing = CAMERA_FACING_FRONT
                        } else {
                            sensors[legacyFrontId] =
                                CameraSensorInfo(id = legacyFrontId).apply { facing = CAMERA_FACING_FRONT }
                        }
                    }
                    context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT) -> {
                        // 系统声明有前置但两层枚举都拿不到 — 只补朝向，不编造参数
                        sensors[LEGACY_FRONT_PLACEHOLDER_ID] =
                            CameraSensorInfo(id = LEGACY_FRONT_PLACEHOLDER_ID).apply { facing = CAMERA_FACING_FRONT }
                    }
                }
            }

            // ── 5 级: id 启发式，仅用于把 unknown 补成具体朝向 ──
            //   ⚠️ "0"=后置 / "1"=前置 属社区惯例而非官方保证，故排在最后且不覆盖已知值
            for (s in sensors.values) {
                if (s.facing == CAMERA_FACING_UNKNOWN) {
                    when (s.id) {
                        "0" -> s.facing = CAMERA_FACING_BACK
                        "1" -> s.facing = CAMERA_FACING_FRONT
                    }
                }
            }

            info.cameraSensors = sensors.values.toList()
        } catch (_: Throwable) { Log.w(TAG, "相机传感器详细检测失败") }
    }

    /** 读取单个相机（逻辑或物理）的完整特性；任一环节异常返回 null */
    private fun readCameraSensor(cameraManager: CameraManager, id: String): CameraSensorInfo? {
        return try {
            val chars = cameraManager.getCameraCharacteristics(id)
            val sensor = CameraSensorInfo(id = id)

            // ★ 修复 (2026-08-07): 原实现 `else -> "外置"` 把 LENS_FACING 为 null 的相机
            //   一律判成外置。null 在部分 ROM 与物理子摄上确实会出现，导致前置被错标。
            //   现在 null 单列 unknown，交由 3~5 级链纠正。
            sensor.facing = when (chars.get(CameraCharacteristics.LENS_FACING)) {
                CameraCharacteristics.LENS_FACING_BACK -> CAMERA_FACING_BACK
                CameraCharacteristics.LENS_FACING_FRONT -> CAMERA_FACING_FRONT
                CameraCharacteristics.LENS_FACING_EXTERNAL -> CAMERA_FACING_EXTERNAL
                else -> CAMERA_FACING_UNKNOWN
            }

            val streamMap = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            if (streamMap != null) {
                val sizes = streamMap.getOutputSizes(android.graphics.ImageFormat.JPEG)
                val maxSize = sizes?.maxByOrNull { it.width * it.height }
                if (maxSize != null) {
                    val mp = (maxSize.width * maxSize.height) / 1_000_000f
                    sensor.resolution = "${maxSize.width}×${maxSize.height} (${"%.1f".format(mp)}MP)"
                }
            }

            val aperture = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
            if (aperture != null && aperture.isNotEmpty()) {
                sensor.aperture = "f/${"%.1f".format(aperture[0])}"
            }

            val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            if (focalLengths != null && focalLengths.isNotEmpty()) {
                sensor.focalLength = "${focalLengths[0].toInt()}mm"
            }

            val pixelSize = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
            if (pixelSize != null) {
                val sensorSize = chars.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
                if (sensorSize != null && pixelSize.width > 0) {
                    val pxSize = (pixelSize.width / sensorSize.width) * 1000f
                    sensor.pixelSize = "%.1f".format(pxSize) + "µm"
                }
            }

            resolveCamera2Constants()
            if (cachedOisKey != null) {
                @Suppress("UNCHECKED_CAST")
                val oisModes = chars.get(cachedOisKey as CameraCharacteristics.Key<IntArray>)
                sensor.oisSupported = oisModes?.any { it == cachedOisModeOn } == true
            }

            val eisModes = chars.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)
            sensor.eisSupported = eisModes?.any { it == CameraCharacteristics.CONTROL_VIDEO_STABILIZATION_MODE_ON } == true

            sensor.flashSupported = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true

            sensor
        } catch (_: Throwable) { null }
    }

    /**
     * HAL1 前置枚举 — 第 3 级兜底。
     *
     * android.hardware.Camera.getNumberOfCameras() / getCameraInfo() 都是**静态**方法，
     * 不需要 CAMERA 权限（只有 Camera.open() 才需要）。API 21 起标记 deprecated 但至今可用，
     * 在把前置藏进逻辑多摄的 ROM 上，这是唯一还能拿到前置朝向的公开路径。
     *
     * ⚠️ HAL1 的相机序号与 camera2 的 id 字符串只是**惯例上**一致，非官方保证；
     *    因此仅用来纠正朝向 / 补一条记录，不用来推翻 camera2 已给出的朝向。
     */
    @Suppress("DEPRECATION")
    private fun detectLegacyFrontCameraId(): String? {
        return try {
            val count = android.hardware.Camera.getNumberOfCameras()
            for (i in 0 until count) {
                val ci = android.hardware.Camera.CameraInfo()
                android.hardware.Camera.getCameraInfo(i, ci)
                if (ci.facing == android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT) return i.toString()
            }
            null
        } catch (_: Throwable) { null }
    }

    // ═══════════════════════════════════════════
    //  Audio — v4: 声道数修正 + Hi-Res 增强
    // ═══════════════════════════════════════════
    private fun collectAudio(info: DeviceDetailInfo) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return

            val sampleRate = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
            info.audioSampleRate = if (sampleRate.isNullOrEmpty() || sampleRate == "0") "-" else "${sampleRate}Hz"

            // 声道数: 物理扬声器数量 + AudioDeviceInfo.getChannelCounts()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    val speakers = devices.filter {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    }
                    val physicalCount = speakers.size

                    if (physicalCount >= 2) {
                        // 多个物理扬声器 → 确定立体声或多声道
                        val allChannelCounts = speakers.flatMap { it.channelCounts.toList() }
                        val maxCh = allChannelCounts.maxOrNull() ?: 2
                        info.audioOutputChannels = when {
                            maxCh >= 8 -> "audio_7.1_surround|$maxCh"
                            maxCh >= 6 -> "audio_5.1_surround|$maxCh"
                            maxCh >= 4 -> "audio_4ch"
                            else -> "audio_stereo|$physicalCount"
                        }
                        info.stereoSpeakers = true
                    } else if (physicalCount == 1) {
                        // 单个扬声器 → 基于支持的声道数判断
                        val channelCounts = speakers[0].channelCounts
                        val maxCh = channelCounts.maxOrNull() ?: 1
                        val minCh = channelCounts.minOrNull() ?: 1
                        info.audioOutputChannels = when {
                            maxCh >= 8 -> "audio_7.1_single|$physicalCount|$maxCh"
                            maxCh >= 6 -> "audio_5.1_single|$physicalCount|$maxCh"
                            maxCh >= 4 -> "audio_4ch_single|$physicalCount"
                            maxCh >= 2 -> "audio_mono_stereo"
                            else -> "audio_mono"
                        }
                        info.stereoSpeakers = maxCh >= 2
                    } else {
                        // 无内置扬声器，检查所有输出设备
                        val allChannels = devices.flatMap { it.channelCounts.toList() }
                        val maxAll = allChannels.maxOrNull() ?: 2
                        info.audioOutputChannels = when {
                            maxAll >= 8 -> "audio_7.1_surround|$maxAll"
                            maxAll >= 6 -> "audio_5.1_surround|$maxAll"
                            maxAll >= 4 -> "audio_4ch|$maxAll"
                            maxAll >= 2 -> "audio_stereo"
                            else -> "audio_mono"
                        }
                        info.stereoSpeakers = maxAll >= 2
                    }
                } catch (_: Throwable) {
                    // 回退仅用于致命异常，不读PROPERTY_OUTPUT_FRAMES_PER_BUFFER(那是帧数不是声道)
                    info.audioOutputChannels = ""
                    info.stereoSpeakers = false
                }
            } else {
                info.audioOutputChannels = "audio_unavailable_api"
            }

            // Hi-Res 音频检测 — 多策略
            info.supportsHiResAudio = detectHiResAudio(am)

            // 耳机孔
            info.headphoneJack = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    devices.any {
                        it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                        it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
                    }
                } else false
            } catch (_: Throwable) { false }

            // 音频格式
            info.audioFormats = buildList {
                if (context.packageManager.hasSystemFeature("android.hardware.audio.output")) add("PCM")
                val dolbyFeature = SysFsReader.readProp("ro.vendor.audio.dolby")
                if (dolbyFeature == "1" || dolbyFeature == "true") add("Dolby Atmos")
                // 检测 LDAC/aptX HD 等 Hi-Res 蓝牙编解码
                val btCodec = SysFsReader.readProp("persist.vendor.bt.a2dp.codec_cap")
                    .ifEmpty { SysFsReader.readProp("ro.vendor.bt.codec") }
                if (btCodec.contains("ldac", ignoreCase = true)) add("LDAC")
                if (btCodec.contains("aptx_hd", ignoreCase = true) || btCodec.contains("aptx-hd", ignoreCase = true)) add("aptX HD")
                if (btCodec.contains("aptx_adaptive", ignoreCase = true)) add("aptX Adaptive")
            }
        } catch (e: Throwable) { Log.w(TAG, "音频能力采集失败", e) }
    }

    /** Hi-Res 音频检测: AudioDeviceInfo + 系统属性 + 蓝牙编解码 */
    private fun detectHiResAudio(am: AudioManager): Boolean {
        // 策略1: AudioDeviceInfo 实际采样率 + 编码
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                for (device in devices) {
                    // 检查采样率 >= 96kHz
                    val rates = device.sampleRates
                    if (rates.any { it >= 96000 }) return true
                    // 检查 24-bit PCM 编码
                    if (device.encodings.any {
                        it == android.media.AudioFormat.ENCODING_PCM_24BIT_PACKED ||
                        it == android.media.AudioFormat.ENCODING_PCM_FLOAT
                    }) return true
                }
            } catch (_: Throwable) {}
        }

        // 策略2: 系统属性 Hi-Res 标记
        val hiResProps = arrayOf(
            "ro.vendor.audio.hi_res", "ro.boot.audio.hi_res",
            "ro.vendor.audio.hires", "persist.vendor.audio.hires",
            "ro.audio.hi_res", "audio.hi.res.support"
        )
        for (prop in hiResProps) {
            val value = SysFsReader.readProp(prop)
            if (value == "1" || value == "true") return true
        }

        // 策略3: LDAC 编解码支持 (Hi-Res 设备几乎都支持 LDAC)
        val btCodec = SysFsReader.readProp("persist.vendor.bt.a2dp.codec_cap")
            .ifEmpty { SysFsReader.readProp("ro.vendor.bt.codec") }
            .ifEmpty { SysFsReader.readProp("persist.bt.a2dp.codec") }
        if (btCodec.contains("ldac", ignoreCase = true)) return true

        // 策略4: 系统是否支持高采样率蓝牙音频
        val hasHiResBt = SysFsReader.readProp("ro.vendor.bluetooth.ldac") == "1" ||
            SysFsReader.readProp("persist.vendor.bt.ldac_enable") == "1"
        if (hasHiResBt) return true

        return false
    }

    // ═══════════════════════════════════════════
    //  Thermal Zones (新增) — 热区统计
    // ═══════════════════════════════════════════
    private fun collectThermal(info: DeviceDetailInfo) {
        try {
            val thermalDir = File("/sys/class/thermal")
            if (!thermalDir.exists()) return

            val zones = thermalDir.listFiles()
                ?.filter { it.name.startsWith("thermal_zone") }
                ?: emptyList()

            info.thermalZoneCount = zones.size

            val types = mutableListOf<String>()
            for (zone in zones) {
                try {
                    val type = SysFsReader.readFile("${zone.absolutePath}/type").trim()
                    if (type.isNotEmpty()) types.add(type)
                } catch (_: Throwable) {}
            }
            info.thermalZoneTypes = types
        } catch (e: Throwable) { Log.w(TAG, "热区采集失败", e) }
    }

    // ═══════════════════════════════════════════
    //  Security — v2
    // ═══════════════════════════════════════════
    private fun collectSecurity(info: DeviceDetailInfo) {
        try {
            val pm = context.packageManager

            info.teeSupported = pm.hasSystemFeature("android.hardware.strongbox") ||
                SysFsReader.readProp("ro.tee.version").isNotEmpty() ||
                pm.hasSystemFeature("android.hardware.keymaster")

            info.secureBootEnabled = try {
                val vbState = SysFsReader.readProp("ro.boot.verifiedbootstate")
                vbState == "green" || vbState == "yellow"
            } catch (_: Throwable) { false }

            info.fileEncryption = when {
                SysFsReader.readProp("ro.crypto.type").contains("file") -> "enc_fbe"
                SysFsReader.readProp("ro.crypto.state").contains("encrypted") -> "enc_fde"
                else -> "common_unavailable"
            }

            info.selinuxEnforcing = try {
                File("/sys/fs/selinux/enforce").readText().trim() == "1"
            } catch (_: Throwable) {
                SysFsReader.readProp("ro.build.selinux") == "1"
            }

            info.bootloaderUnlocked = try {
                // ★ 修复(N1): 改走 ShellCommandDataSource.exec (8s 超时 + destroyForcibly)，原 Runtime.exec 无超时致挂起
                val unlocked = ShellCommandDataSource.exec("getprop", "ro.boot.flash.locked").trim()
                unlocked == "0"
            } catch (_: Throwable) {
                SysFsReader.readProp("ro.boot.verifiedbootstate") == "orange"
            }
        } catch (e: Throwable) { Log.w(TAG, "安全信息采集失败", e) }
    }

    // ═══════════════════════════════════════════
    //  Identifiers — Android ID / 序列号 (可重置标识符)
    // ═══════════════════════════════════════════

    /**
     * 通过 shell 执行 getprop 读取系统属性.
     *
     * 比 SystemProperties 反射更可靠: 绕过 Android 9+ non-SDK interface 限制
     * 和 OEM hidden API 黑名单. getprop 是 /system/bin/getprop, 普通进程可执行.
     */
    // ★ 修复(N1): 改走 ShellCommandDataSource.exec (8s 超时 + destroyForcibly)，原 Runtime.exec 无超时致挂起
    private fun runGetProp(key: String): String =
        try { ShellCommandDataSource.exec("getprop", key).trim() } catch (_: Throwable) { "" }

    // ★ #11d: 序列号会话缓存 — 硬件序列号属设备固化值, 首次读取后缓存,
    //   避免每 tick 重复 fork shell 执行 getprop (ro.serialno/sys.serialno)。
    private var cachedSerial: String? = null
    private fun readCachedSerialProp(): String {
        cachedSerial?.let { return it }
        val s = runGetProp("ro.serialno").ifBlank { runGetProp("sys.serialno") }
        cachedSerial = s.ifBlank { null }
        return s
    }

    private fun collectIdentifiers(info: DeviceDetailInfo) {
        try {
            // Android ID — Settings.Secure 无需额外权限
            info.androidId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: ""
            if (info.androidId.length > 16) {
                info.androidId = info.androidId.substring(0, 16)
            }
        } catch (e: Throwable) { Log.w(TAG, "Android ID读取失败", e) }

        // ══ 序列号 — 多源 fallback 链 (shell优先) ══
        //
        // 根因分析 (3 层拦截):
        //   L1: Build.getSerial() → OEM ROM framework 层拦截, 即使授予权限也抛 SecurityException
        //   L2: SystemProperties.get() 反射 → Android 9+ non-SDK interface 限制 + OEM hidden API 黑名单,
        //       SysFsReader.readProp() 静默返回空串 (不抛异常!)
        //   L3: 某些设备 SELinux 限制普通 app 执行 getprop shell
        //
        // 策略: shell 命令优先 (绕过 hidden API 限制) → 文件读取 → 反射降级
        try {
            var serial = ""

            // ── Path ①: Build.getSerial() (标准 API, 在 Pixel/Stock 上可用) ──
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                serial = try {
                    val s = Build.getSerial()
                    if (s.isNotBlank() && s != "unknown") s else ""
                } catch (_: SecurityException) { "" }
                catch (_: Throwable) { "" }
            }

            // ── Path ②: getprop shell (最可靠! 绕过 hidden API 限制) — ★ #11d: 会话缓存
            if (serial.isBlank()) {
                serial = readCachedSerialProp()
            }

            // ── Path ③: 直接读 sysfs 文件 (部分设备可从 USB 节点获取) ──
            if (serial.isBlank()) {
                serial = SysFsReader.readAll("/sys/class/android_usb/android0/iSerial").trim()
                    .ifBlank { SysFsReader.readAll("/sys/class/android_usb/android0/iSerialNumber").trim() }
            }

            // ── Path ④: SystemProperties 反射 (作为最后手段) ──
            if (serial.isBlank()) {
                serial = SysFsReader.readProp("ro.serialno")
                    .ifBlank { SysFsReader.readProp("sys.serialno") }
            }

            // ── 最终兜底 ──
            info.serialNumber = serial.ifBlank { "不可用" }
        } catch (e: Throwable) { Log.w(TAG, "序列号读取失败", e) }

        // 硬件序列号 — 同样用 shell 优先策略 — ★ #11d: shell 部分复用会话缓存, 避免重复 fork
        try {
            info.hardwareSerial = readCachedSerialProp()
                .ifEmpty { SysFsReader.readProp("ro.serialno") }
                .ifEmpty { SysFsReader.readProp("sys.serialno") }
        } catch (e: Throwable) { Log.w(TAG, "硬件序列号读取失败", e) }

        // 设备指纹 — Build.FINGERPRINT (ROM 签名唯一标识)
        try {
            info.deviceFingerprint = Build.FINGERPRINT
        } catch (e: Throwable) { Log.w(TAG, "设备指纹读取失败", e) }
    }

    // ═══════════════════════════════════════════
    //  Runtime Environment (运行环境)
    // ═══════════════════════════════════════════
    private fun collectRuntimeEnv(info: DeviceDetailInfo) {
        try {
            // ── Java Runtime (优先用 VMRuntime 获取真实版本，Android 上 java.version 常返回 0) ──
            val javaVm = System.getProperty("java.vm.name", "")
            val javaVmVersion = System.getProperty("java.vm.version", "")
            val javaVendor = System.getProperty("java.vendor", "")
            val javaVersion = System.getProperty("java.version", "")

            info.javaVmName = when {
                javaVm.contains("Dalvik", ignoreCase = true) -> "Android Runtime (Dalvik)"
                javaVm.contains("ART", ignoreCase = true) || javaVm.contains("art", ignoreCase = true) -> "Android Runtime (ART)"
                javaVm.isNotEmpty() -> javaVm
                else -> "Android Runtime"
            }

            // Java 版本 — 优先用 VMRuntime.vmVersion() 而非 java.version (后者常见返回 0)
            val artVersion: String = try {
                val vmRuntime = Class.forName("dalvik.system.VMRuntime")
                val getRuntime = vmRuntime.getMethod("getRuntime")
                val runtime = getRuntime.invoke(null)
                val getVersion = runtime.javaClass.getMethod("vmVersion")
                getVersion.invoke(runtime)?.toString()?.takeIf { it.isNotEmpty() && it != "0" } ?: ""
            } catch (_: Throwable) { "" }

            val versionParts = mutableListOf<String>()
            if (javaVersion.isNotEmpty() && javaVersion != "0") versionParts.add("JRE $javaVersion")
            if (artVersion.isNotEmpty()) versionParts.add("ART $artVersion")
            if (javaVmVersion.isNotEmpty() && javaVmVersion != "0") versionParts.add("VM $javaVmVersion")
            if (javaVendor.isNotEmpty()) versionParts.add(javaVendor)
            info.javaRuntimeVersion = versionParts.joinToString(" | ").ifEmpty { "Android Runtime" }

            // ── OpenSSL 版本 ──
            try {
                // 方式1: 系统属性 (Android 10+)
                info.opensslVersion = SysFsReader.readProp("ro.openssl.version")
                    .ifEmpty { SysFsReader.readProp("ro.vendor.openssl.version") }

                // 方式2: 反射安全提供者 (BoringSSL 标准 API)
                if (info.opensslVersion.isEmpty()) {
                    try {
                        val sslCtx = javax.net.ssl.SSLContext.getDefault()
                        val provider = sslCtx.provider
                        val name = provider.name  // "AndroidOpenSSL" / "BoringSSL"
                        val ver = try { provider.version.toString() } catch (_: Throwable) { "" }
                        info.opensslVersion = if (ver.isNotEmpty()) "$name $ver" else name
                    } catch (_: Throwable) {
                        val provider = java.security.Security.getProvider("AndroidOpenSSL")
                            ?: java.security.Security.getProvider("BC")
                        provider?.let {
                            val ver = try { it.version.toString() } catch (_: Throwable) { "" }
                            info.opensslVersion = if (ver.isNotEmpty()) "${it.name} $ver" else it.name
                        }
                    }
                }

                // 方式3: 通过 exec 获取 (Android 5+)
                if (info.opensslVersion.isEmpty()) {
                    try {
                        // ★ 修复(N2): 改走 ShellCommandDataSource.exec (8s 超时 + destroyForcibly)，原 readText 先于 waitFor 致卡死
                        val output = ShellCommandDataSource.exec("/system/bin/sh", "-c", "openssl version 2>/dev/null").trim()
                        if (output.isNotEmpty()) {
                            info.opensslVersion = output.removePrefix("OpenSSL").trim()
                                .let { if (it.isNotEmpty()) "OpenSSL $it" else output }
                        }
                    } catch (_: Throwable) {}
                }
            } catch (_: Throwable) {
                info.opensslVersion = ""
            }

            // ── 构建时间戳 ──
            try {
                val buildTimeMs = try {
                    com.rb.cybermonitorpro.BuildConfig::class.java.getField("BUILD_TIMESTAMP").getLong(null)
                } catch (_: Throwable) { 0L }
                if (buildTimeMs > 0) {
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                    val formatted = sdf.format(java.util.Date(buildTimeMs))
                    info.buildTimestamp = "$formatted UTC"
                } else {
                    info.buildTimestamp = "common_unknown"
                }
            } catch (_: Throwable) {
                info.buildTimestamp = "common_unknown"
            }
        } catch (_: Throwable) {
            Log.w(TAG, "运行环境采集异常", null)
        }
    }

    // ═══════════════════════════════════════════
    //  Miscellaneous
    // ═══════════════════════════════════════════
    private fun collectMisc(info: DeviceDetailInfo) {
        val pm = context.packageManager
        info.hasNfc = pm.hasSystemFeature(PackageManager.FEATURE_NFC)
        info.hasKeyboard = context.resources.configuration.keyboard != android.content.res.Configuration.KEYBOARD_NOKEYS

        info.touchscreenType = when {
            pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_JAZZHAND) -> "touch_jazzyhand"
            pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT) -> "touch_distinct"
            pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH) -> "touch_basic"
            pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN) -> "touch_single"
            else -> "touch_none"
        }

        info.hasInfrared = pm.hasSystemFeature(PackageManager.FEATURE_CONSUMER_IR) ||
            pm.hasSystemFeature("android.hardware.consumerir")
        info.hasFmRadio = pm.hasSystemFeature("android.hardware.fm") ||
            SysFsReader.readProp("ro.fm.transmitter") == "true"
        info.hasUwb = pm.hasSystemFeature("android.hardware.uwb") ||
            pm.hasSystemFeature("android.hardware.uwb.ranging")
        info.hasWirelessCharging = try {
            val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val status = context.registerReceiver(null, batteryFilter)
            status?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) == BatteryManager.BATTERY_PLUGGED_WIRELESS
        } catch (_: Throwable) { false }
    }

    // ═══════════════════════════════════════════
    //  反射辅助方法 — 避免 OEM ROM ART 先行验证崩溃
    // ═══════════════════════════════════════════

    /** 通过反射获取 HDR 能力 — 避免 API 33+ display.hdrCapabilities 在低版本设备上的 dex 验证崩溃 */
    private fun getHdrTypesReflective(display: Any): List<String> {
        return try {
            val getHdr = display.javaClass.getMethod("getHdrCapabilities")
            val hdr = getHdr.invoke(display) ?: return emptyList()
            val getTypes = hdr.javaClass.getMethod("getSupportedHdrTypes")
            val types = getTypes.invoke(hdr) as? IntArray ?: return emptyList()
            types.map { type ->
                when (type) {
                    1 -> "Dolby Vision"   // HDR_TYPE_DOLBY_VISION
                    2 -> "HDR10"          // HDR_TYPE_HDR10
                    4 -> "HDR10+"         // HDR_TYPE_HDR10_PLUS
                    3 -> "HLG"            // HDR_TYPE_HLG
                    else -> "HDR-TYPE-$type"
                }
            }
        } catch (_: Throwable) { emptyList() }
    }

    /** 通过反射获取 Wi-Fi 标准 — 避免 API 33+ WifiInfo.getWifiStandard() 在低版本设备上的 dex 验证崩溃 */
    private fun getWifiStandardReflective(wifiInfo: Any): Int {
        return try {
            val method = wifiInfo.javaClass.getMethod("getWifiStandard")
            method.invoke(wifiInfo) as? Int ?: 0
        } catch (_: Throwable) { 0 }
    }
}
