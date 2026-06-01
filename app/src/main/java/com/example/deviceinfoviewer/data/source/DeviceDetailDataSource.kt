package com.example.deviceinfoviewer.data.source

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
import android.telephony.TelephonyManager
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.example.deviceinfoviewer.data.model.CameraSensorInfo
import com.example.deviceinfoviewer.data.model.DeviceDetailInfo
import java.io.File
import java.util.UUID

// 设备详细信息数据源 - v3 (Sciverse + Android API + sysfs/procfs)
// 覆盖: CPU缓存/内存/存储/USB/蓝牙/WiFi/SoC制程/GPU显存/色深/热区
class DeviceDetailDataSource(private val context: Context) {

    companion object {
        private const val TAG = "DeviceDetailDS"
        private val WIDEVINE_UUID = UUID.fromString("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed")

        /** SoC 型号 → 制程工艺 查找表 */
        private val SOC_PROCESS_MAP = mapOf(
            // Qualcomm Snapdragon
            "SM8650" to "4nm", "SM8550" to "4nm", "SM8635" to "4nm",
            "SM8475" to "4nm", "SM8450" to "4nm",
            "SM8350" to "5nm", "SM7475" to "4nm", "SM7450" to "4nm",
            "SM7325" to "6nm", "SM7315" to "6nm",
            "SM8250" to "7nm", "SM7250" to "7nm", "SM7225" to "6nm",
            "SM8150" to "7nm", "SM7150" to "8nm",
            "SDM845" to "10nm", "SDM710" to "10nm", "SDM660" to "14nm",
            "SDM636" to "14nm", "MSM8998" to "10nm",
            // MediaTek Dimensity
            "MT6989" to "4nm", "MT6983" to "4nm", "MT6897" to "4nm",
            "MT6895" to "4nm", "MT6877" to "6nm", "MT6855" to "6nm",
            "MT6889" to "7nm", "MT6885" to "7nm",
            // Samsung Exynos
            "exynos2400" to "4nm", "exynos2200" to "5nm",
            "exynos2100" to "5nm", "exynos990" to "7nm",
            // HiSilicon Kirin
            "kirin9000" to "5nm", "kirin9000s" to "7nm",
            "kirin990" to "7nm", "kirin820" to "7nm",
        )

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

    private fun detectDisplayTechnology(): String {
        return try {
            val panel = SysFsReader.readProp("ro.display.series")
            if (panel.contains("oled", ignoreCase = true) || panel.contains("amoled", ignoreCase = true))
                return if (panel.contains("ltpo", ignoreCase = true)) "LTPO AMOLED" else "AMOLED"
            val tech = SysFsReader.readProp("ro.vendor.display.type")
            if (tech.isNotEmpty()) return tech
            if (context.packageManager.hasSystemFeature("android.hardware.display.oled")) "OLED"
            else if (context.packageManager.hasSystemFeature("android.software.live_wallpaper")) "LCD"
            else ""
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

    /** 色深检测: /sys/class/graphics/fb0/bits_per_pixel 或 sysfs 属性 */
    private fun detectColorDepth(): String {
        return try {
            // 方法1: fb0 bits_per_pixel
            val bpp = SysFsReader.readFile("/sys/class/graphics/fb0/bits_per_pixel")
                .trim().toIntOrNull() ?: 0
            if (bpp >= 30) return "${bpp / 3}-bit"  // 30bpp ≈ 10-bit, 36bpp ≈ 12-bit
            if (bpp in 24..29) return "8-bit"

            // 方法2: SystemProperties
            val depth = SysFsReader.readProp("ro.display.bpp")
            if (depth.isNotEmpty()) {
                val d = depth.toIntOrNull() ?: 0
                if (d >= 30) return "${d / 3}-bit"
                if (d in 24..29) return "8-bit"
            }

            // 方法3: HDR 存在则推断 10-bit
            if (context.packageManager.hasSystemFeature("android.hardware.vulkan.level"))
                return "10-bit (推断)"

            ""
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
    //  GPU (OpenGL ES) — v3: +GPU显存
    // ═══════════════════════════════════════════
    private fun collectGpu(info: DeviceDetailInfo) {
        try {
            val pm = context.packageManager
            val reqGlVersion = try {
                pm.systemAvailableFeatures
                    .filter { it.name?.startsWith("android.hardware.opengles") == true }
                    .maxOfOrNull { f ->
                        f.name?.removePrefix("android.hardware.opengles.aep")?.toIntOrNull() ?: 0
                    } ?: 2
            } catch (_: Throwable) { 2 }
            info.glEsVersion = "OpenGL ES $reqGlVersion"

            // GPU 型号: 安全反射 GLES20
            try {
                val gles20 = Class.forName("android.opengl.GLES20")
                info.glRenderer = gles20.getMethod("glGetString", Int::class.javaPrimitiveType!!)
                    .invoke(null, 0x1F01 /* GL_RENDERER */)?.toString() ?: ""
                info.glVendor = gles20.getMethod("glGetString", Int::class.javaPrimitiveType!!)
                    .invoke(null, 0x1F00 /* GL_VENDOR */)?.toString() ?: ""
                val extStr = gles20.getMethod("glGetString", Int::class.javaPrimitiveType!!)
                    .invoke(null, 0x1F03 /* GL_EXTENSIONS */)?.toString() ?: ""
                info.glExtensions = extStr.split(" ").filter { it.isNotBlank() }
                info.gpuDriverVersion = try {
                    gles20.getMethod("glGetString", Int::class.javaPrimitiveType!!)
                        .invoke(null, 0x1F02 /* GL_VERSION */)?.toString() ?: ""
                } catch (_: Throwable) { "" }
            } catch (_: Throwable) {
                Log.w(TAG, "GLES20 GPU检测失败，回退到基本方案")
            }

            // GPU 专用显存 (高通 Adreno kgsl)
            info.gpuLocalMemoryKb = detectGpuLocalMemory()
        } catch (e: Throwable) { Log.w(TAG, "GPU采集失败", e) }
    }

    /** GPU 显存检测: 高通 kgsl / Mali / 通用 */
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
    //  Vulkan — 扩展
    // ═══════════════════════════════════════════
    private fun collectVulkan(info: DeviceDetailInfo) {
        try {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val features = pm.systemAvailableFeatures
                for (f in features) {
                    val name = f.name ?: continue
                    when {
                        name == "android.hardware.vulkan.version" -> info.vulkanVersion = extractVulkanVersion(f)
                        name == "android.hardware.vulkan.level" -> info.vulkanApiLevel = extractVulkanLevel(f)
                        name.contains("vulkan") && name.contains("ray") -> info.rayTracingSupported = true
                    }
                }
            }
            if (info.vulkanVersion.isEmpty()) {
                val vkProp = SysFsReader.readProp("ro.hardware.vulkan")
                if (vkProp.isNotEmpty()) info.vulkanVersion = vkProp
            }
            if (info.vulkanApiLevel.isEmpty()) {
                val level = SysFsReader.readPropInt("ro.vulkan.api.level")
                if (level > 0) info.vulkanApiLevel = when (level) {
                    1 -> "Vulkan 1.0"; 2 -> "Vulkan 1.1"; 3 -> "Vulkan 1.3"
                    else -> "Level $level"
                }
            }
            if (!info.rayTracingSupported) {
                info.rayTracingSupported = pm.hasSystemFeature("android.hardware.vulkan.ray_tracing")
            }
            if (!info.rayTracingSupported) {
                val rtProp = SysFsReader.readProp("ro.vendor.gpu.ray_tracing")
                if (rtProp == "1" || rtProp == "true") info.rayTracingSupported = true
            }
        } catch (e: Throwable) { Log.w(TAG, "Vulkan采集失败", e) }
    }

    private fun extractVulkanVersion(feature: android.content.pm.FeatureInfo): String {
        return try {
            val ver = feature.version
            val major = ver shr 22
            val minor = (ver shr 12) and 0x3FF
            if (major > 0) "Vulkan $major.$minor" else ""
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

            info.cpuCacheSource = "不可用"
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

    private fun readCpuPartFromProcCpuinfo(): String {
        return try {
            val cpuInfo = File("/proc/cpuinfo").readText()
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
            val cpuInfo = try { File("/proc/cpuinfo").readText() } catch (_: Throwable) { "" }
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
            info.bigLITTLE = detectBigLITTLETopology()
        } catch (e: Throwable) { Log.w(TAG, "CPU拓扑采集失败", e) }
    }

    private fun detectBigLITTLETopology(): String {
        return try {
            val freqGroups = mutableMapOf<Long, Int>()  // maxFreq → coreCount
            var cpuIndex = 0
            while (true) {
                val maxFreq = SysFsReader.readFile("/sys/devices/system/cpu/cpu$cpuIndex/cpufreq/cpuinfo_max_freq")
                    .trim().toLongOrNull() ?: break
                val current = SysFsReader.readFile("/sys/devices/system/cpu/cpu$cpuIndex/topology/cluster_id")
                    .trim().toIntOrNull() ?: 0
                val key = maxFreq
                freqGroups[key] = (freqGroups[key] ?: 0) + 1
                cpuIndex++
                if (cpuIndex > 16) break  // 安全上限
            }
            if (freqGroups.size >= 2) {
                val sorted = freqGroups.entries.sortedByDescending { it.key }
                val big = sorted[0].value
                val little = sorted[1].value
                val extra = if (sorted.size > 2) sorted.drop(2).joinToString("+") { "${it.value}×${it.key / 1000}MHz" } else ""
                val base = "${big}大核+${little}小核"
                if (extra.isNotEmpty()) "$base+${extra}" else base
            } else if (freqGroups.size == 1) {
                "${freqGroups.values.first()}核同频"
            } else ""
        } catch (_: Throwable) { "" }
    }

    // ═══════════════════════════════════════════
    //  SoC Process Node (新增)
    // ═══════════════════════════════════════════
    private fun collectSocProcess(info: DeviceDetailInfo) {
        try {
            // 策略0: CpuCache 已知芯片数据库 (最精确)
            val platform = SysFsReader.readProp("ro.board.platform")
            val knownChip = CpuCache.lookup(platform)
            if (knownChip != null && knownChip.processNode.isNotEmpty()) {
                info.socProcessNode = knownChip.processNode
                info.socProcessNodeSource = "chipdb:${knownChip.chipName}"
                return
            }

            val socModel = SysFsReader.readProp("ro.soc.model").ifEmpty { platform }

            // 策略1: 直接查表
            val processNode = SOC_PROCESS_MAP[socModel]
            if (processNode != null) {
                info.socProcessNode = processNode
                info.socProcessNodeSource = "lookup:$socModel"
                return
            }

            // 策略2: 模糊匹配
            for ((key, value) in SOC_PROCESS_MAP) {
                if (socModel.contains(key, ignoreCase = true)) {
                    info.socProcessNode = value
                    info.socProcessNodeSource = "lookup:~$key"
                    return
                }
            }

            // 策略3: SystemProperties 直接读取 (极少设备)
            val directProp = SysFsReader.readProp("ro.soc.process_node")
            if (directProp.isNotEmpty()) {
                info.socProcessNode = directProp
                info.socProcessNodeSource = "property"
                return
            }

            info.socProcessNodeSource = "不可用"
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
                val ufsVer = SysFsReader.readFile("/sys/devices/platform/soc/*.ufs/versions")
                    .ifEmpty { SysFsReader.readFile("/sys/class/scsi_device/*/device/versions") }
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

            // USB 版本推断
            info.usbVersion = try {
                val usbConfig = SysFsReader.readProp("ro.usb.config")
                    .ifEmpty { SysFsReader.readProp("persist.sys.usb.config") }

                // Type-C 检测
                info.usbTypeC = try {
                    val hasTypeC = SysFsReader.readProp("ro.hardware.usb.typec")
                    hasTypeC == "1" || hasTypeC == "true" ||
                        pm.hasSystemFeature("android.hardware.usb.accessory")
                } catch (_: Throwable) { true }  // 大多数现代设备是 Type-C

                // USB 版本: 基于 feature flags + 属性推断
                when {
                    pm.hasSystemFeature("android.hardware.usb.host") -> {
                        // USB Host 模式通常意味着 USB 3.x 或更高
                        val speed = SysFsReader.readProp("ro.boot.usb_speed")
                        when {
                            speed.contains("super", ignoreCase = true) -> "USB 3.0"
                            speed.contains("high", ignoreCase = true) -> "USB 2.0"
                            else -> {
                                // 高端骁龙设备通常 USB 3.x
                                val socModel = SysFsReader.readProp("ro.soc.model")
                                if (socModel.contains("SM8650") || socModel.contains("SM8550") ||
                                    socModel.contains("SM8635") || socModel.contains("SM8475"))
                                    "USB 3.2 (推断)"
                                else if (socModel.contains("SM8350") || socModel.contains("SM8250"))
                                    "USB 3.1 (推断)"
                                else "USB 2.0+"
                            }
                        }
                    }
                    else -> "USB 2.0"
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
                info.bluetoothName = adapter.name ?: ""
                @Suppress("MissingPermission")
                info.bluetoothAddress = try { adapter.address } catch (_: SecurityException) { "" }

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

    private fun detectBluetoothVersion(): String {
        return try {
            val pm = context.packageManager
            // API 级别 → 最低蓝牙版本推断
            // BT 5.0: Android 8.0+; BT 5.1: Android 10+; BT 5.2: Android 11+; BT 5.3: Android 13+
            val sdkInt = Build.VERSION.SDK_INT

            // SystemProperties 直接读取
            val btVer = SysFsReader.readProp("ro.bluetooth.version")
            if (btVer.isNotEmpty()) return "BT $btVer"

            // sysfs
            val hciVer = SysFsReader.readFile("/sys/class/bluetooth/hci0/version").trim()
            if (hciVer.isNotEmpty()) return "BT HCI $hciVer"

            // 基于 API 级别 + feature flags 推断
            val hasLE = pm.hasSystemFeature("android.hardware.bluetooth_le")
            val hasLEAudio = sdkInt >= 33 && pm.hasSystemFeature("android.hardware.bluetooth_le_audio")

            when {
                hasLEAudio -> "BT 5.2+"  // LE Audio 需要 5.2+
                hasLE && sdkInt >= 30 -> "BT 5.0+"  // BLE + Android 11+
                hasLE && sdkInt >= 29 -> "BT 5.0+"  // BLE + Android 10+
                hasLE -> "BT 4.0+"  // 最低 BLE
                else -> "BT 3.0"
            }
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

            // 策略2: Feature flags 推断
            if (info.wifiStandard.isEmpty()) {
                val has6 = pm.hasSystemFeature("android.hardware.wifi.ax") ||
                    SysFsReader.readProp("ro.boot.wifi.ax") == "1"
                val has6E = SysFsReader.readProp("ro.boot.wifi.6e") == "1" ||
                    SysFsReader.readProp("ro.vendor.wifi.6e_support") == "1"
                val has7 = SysFsReader.readProp("ro.boot.wifi.be") == "1" ||
                    SysFsReader.readProp("ro.vendor.wifi.be_support") == "1"

                info.wifiStandard = when {
                    has7 -> "Wi-Fi 7 (推断)"
                    has6E -> "Wi-Fi 6E (推断)"
                    has6 -> "Wi-Fi 6 (推断)"
                    pm.hasSystemFeature("android.hardware.wifi.direct") -> "Wi-Fi 5+"
                    pm.hasSystemFeature("android.hardware.wifi") -> "Wi-Fi 4+"
                    else -> ""
                }
                info.wifiStandardSource = "feature_flags"
            }

            // 6GHz 支持
            info.wifi6EEnabled = SysFsReader.readProp("ro.vendor.wifi.6e_support") == "1" ||
                SysFsReader.readProp("ro.boot.wifi.6e") == "1"

            // Wi-Fi Aware (NAN)
            info.wifiAware = pm.hasSystemFeature("android.hardware.wifi.aware")
        } catch (e: Throwable) { Log.w(TAG, "Wi-Fi标准采集失败", e) }
    }

    // ═══════════════════════════════════════════
    //  Media Codecs
    // ═══════════════════════════════════════════
    private fun collectCodecs(info: DeviceDetailInfo) {
        try {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            val audio = mutableListOf<String>()
            val video = mutableListOf<String>()
            for (codecInfo in codecList.codecInfos) {
                if (codecInfo.isEncoder) continue
                val name = codecInfo.name
                when {
                    codecInfo.isAudioCodec() -> audio.add(name)
                    codecInfo.isVideoCodec() -> video.add(name)
                }
            }
            info.audioCodecs = audio
            info.videoCodecs = video
        } catch (e: Throwable) { Log.w(TAG, "编解码器采集失败", e) }
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
        } catch (_: Throwable) { info.widevineLevel = "不支持" }

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
                else -> "未知"
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
            val cm = context.packageManager
            val hasFlash = cm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
            info.cameraIds = buildList {
                if (cm.hasSystemFeature(PackageManager.FEATURE_CAMERA)) add("后置")
                if (cm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)) add("前置")
                if (hasFlash) add("闪光灯")
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                collectCameraSensors(info)
            }
        } catch (e: Throwable) { Log.w(TAG, "相机采集失败", e) }
    }

    private fun collectCameraSensors(info: DeviceDetailInfo) {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
            val camIds = cameraManager.cameraIdList
            val sensors = mutableListOf<CameraSensorInfo>()

            for (id in camIds) {
                try {
                    val chars = cameraManager.getCameraCharacteristics(id)
                    val sensor = CameraSensorInfo(id = id)

                    val facing = chars.get(CameraCharacteristics.LENS_FACING)
                    sensor.facing = when (facing) {
                        CameraCharacteristics.LENS_FACING_BACK -> "后置"
                        CameraCharacteristics.LENS_FACING_FRONT -> "前置"
                        else -> "外置"
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

                    sensors.add(sensor)
                } catch (_: Throwable) {}
            }
            info.cameraSensors = sensors
        } catch (_: Throwable) { Log.w(TAG, "相机传感器详细检测失败") }
    }

    // ═══════════════════════════════════════════
    //  Audio — 能力检测
    // ═══════════════════════════════════════════
    private fun collectAudio(info: DeviceDetailInfo) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return

            val sampleRate = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
            info.audioSampleRate = if (sampleRate.isNullOrEmpty() || sampleRate == "0") "-" else "${sampleRate}Hz"

            val channels = am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
            info.audioOutputChannels = if (channels.isNullOrEmpty() || channels == "0") "-" else {
                val chanInt = channels.toIntOrNull() ?: 0
                when {
                    chanInt >= 768 -> "立体声 (${chanInt} 帧/缓冲)"
                    chanInt >= 256 -> "立体声"
                    else -> "单声道"
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    val speakerCount = devices.count { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    info.stereoSpeakers = speakerCount >= 2
                } catch (_: Throwable) {}
            }

            info.supportsHiResAudio = try {
                val rate = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
                (rate?.toIntOrNull() ?: 0) >= 96000
            } catch (_: Throwable) { false }

            info.headphoneJack = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    devices.any { it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET }
                } else false
            } catch (_: Throwable) { false }

            info.audioFormats = buildList {
                if (context.packageManager.hasSystemFeature("android.hardware.audio.output")) add("PCM")
                val dolbyFeature = SysFsReader.readProp("ro.vendor.audio.dolby")
                if (dolbyFeature == "1" || dolbyFeature == "true") add("Dolby Atmos")
            }
        } catch (e: Throwable) { Log.w(TAG, "音频能力采集失败", e) }
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
                SysFsReader.readProp("ro.crypto.type").contains("file") -> "FBE (文件级加密)"
                SysFsReader.readProp("ro.crypto.state").contains("encrypted") -> "FDE (全盘加密)"
                else -> "未检测到"
            }

            info.selinuxEnforcing = try {
                File("/sys/fs/selinux/enforce").readText().trim() == "1"
            } catch (_: Throwable) {
                SysFsReader.readProp("ro.build.selinux") == "1"
            }

            info.bootloaderUnlocked = try {
                val unlocked = Runtime.getRuntime().exec(arrayOf("getprop", "ro.boot.flash.locked"))
                    .inputStream.bufferedReader().readText().trim()
                unlocked == "0"
            } catch (_: Throwable) {
                SysFsReader.readProp("ro.boot.verifiedbootstate") == "orange"
            }
        } catch (e: Throwable) { Log.w(TAG, "安全信息采集失败", e) }
    }

    // ═══════════════════════════════════════════
    //  Miscellaneous
    // ═══════════════════════════════════════════
    private fun collectMisc(info: DeviceDetailInfo) {
        val pm = context.packageManager
        info.hasNfc = pm.hasSystemFeature(PackageManager.FEATURE_NFC)
        info.hasKeyboard = context.resources.configuration.keyboard != android.content.res.Configuration.KEYBOARD_NOKEYS

        info.touchscreenType = when {
            pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_JAZZHAND) -> "5指以上多点触控"
            pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT) -> "多点触控"
            pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH) -> "多点触控(基础)"
            pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN) -> "支持"
            else -> "不支持"
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
