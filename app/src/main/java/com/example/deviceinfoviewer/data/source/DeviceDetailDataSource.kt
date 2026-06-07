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
        collectCoolingDevices(info)
        collectIoScheduler(info)
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

    /** 色深检测: 仅读取 sysfs/属性中的实际值，不做推断 */
    private fun detectColorDepth(): String {
        return try {
            // 方法1: fb0 bits_per_pixel (内核直接暴露的实际值)
            val bpp = SysFsReader.readFile("/sys/class/graphics/fb0/bits_per_pixel")
                .trim().toIntOrNull() ?: 0
            if (bpp >= 30) return "${bpp / 3}-bit"
            if (bpp in 24..29) return "8-bit"

            // 方法2: SystemProperties 实际值
            val depth = SysFsReader.readProp("ro.display.bpp")
            if (depth.isNotEmpty()) {
                val d = depth.toIntOrNull() ?: 0
                if (d >= 30) return "${d / 3}-bit"
                if (d in 24..29) return "8-bit"
            }

            // 无实际数据时返回空（不推断）
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

            // USB 版本: 仅基于实际 sysfs/usb_speed 属性
            info.usbVersion = try {
                val usbConfig = SysFsReader.readProp("ro.usb.config")
                    .ifEmpty { SysFsReader.readProp("persist.sys.usb.config") }

                // Type-C 检测
                info.usbTypeC = try {
                    val hasTypeC = SysFsReader.readProp("ro.hardware.usb.typec")
                    hasTypeC == "1" || hasTypeC == "true" ||
                        pm.hasSystemFeature("android.hardware.usb.accessory")
                } catch (_: Throwable) { true }  // 大多数现代设备是 Type-C

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
                            maxCh >= 8 -> "7.1 环绕 ($maxCh 声道)"
                            maxCh >= 6 -> "5.1 环绕 ($maxCh 声道)"
                            maxCh >= 4 -> "4 声道"
                            else -> "立体声 (${physicalCount}扬声器)"
                        }
                        info.stereoSpeakers = true
                    } else if (physicalCount == 1) {
                        // 单个扬声器 → 基于支持的声道数判断
                        val channelCounts = speakers[0].channelCounts
                        val maxCh = channelCounts.maxOrNull() ?: 1
                        val minCh = channelCounts.minOrNull() ?: 1
                        info.audioOutputChannels = when {
                            maxCh >= 8 -> "7.1 环绕 (${physicalCount}扬声器, $maxCh 声道)"
                            maxCh >= 6 -> "5.1 环绕 (${physicalCount}扬声器, $maxCh 声道)"
                            maxCh >= 4 -> "4 声道 (${physicalCount}扬声器)"
                            maxCh >= 2 -> "单扬声器 · 支持立体声混音"  // 1 speaker, but supports 2.0 output
                            else -> "单声道"
                        }
                        info.stereoSpeakers = maxCh >= 2
                    } else {
                        // 无内置扬声器，检查所有输出设备
                        val allChannels = devices.flatMap { it.channelCounts.toList() }
                        val maxAll = allChannels.maxOrNull() ?: 2
                        info.audioOutputChannels = when {
                            maxAll >= 8 -> "7.1 ($maxAll 声道)"
                            maxAll >= 6 -> "5.1 ($maxAll 声道)"
                            maxAll >= 4 -> "4 声道 ($maxAll 声道)"
                            maxAll >= 2 -> "立体声 ($maxAll 声道)"
                            else -> "单声道"
                        }
                        info.stereoSpeakers = maxAll >= 2
                    }
                } catch (_: Throwable) {
                    // 回退仅用于致命异常，不读PROPERTY_OUTPUT_FRAMES_PER_BUFFER(那是帧数不是声道)
                    info.audioOutputChannels = ""
                    info.stereoSpeakers = false
                }
            } else {
                info.audioOutputChannels = "不可用 (API < 23)"
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
    //  Cooling Devices — 冷却设备状态 (学术依据: DATE 2019 热管理论文)
    // ═══════════════════════════════════════════
    private fun collectCoolingDevices(info: DeviceDetailInfo) {
        try {
            val thermalDir = File("/sys/class/thermal")
            if (!thermalDir.exists()) return

            val devices = thermalDir.listFiles()
                ?.filter { it.name.startsWith("cooling_device") }
                ?: emptyList()

            info.coolingDeviceCount = devices.size

            val states = mutableListOf<String>()
            for (dev in devices) {
                try {
                    val type = SysFsReader.readFile("${dev.absolutePath}/type").trim()
                    val curState = SysFsReader.readFile("${dev.absolutePath}/cur_state").trim()
                    val maxState = SysFsReader.readFile("${dev.absolutePath}/max_state").trim()
                    if (type.isNotEmpty()) {
                        states.add("$type: $curState/$maxState")
                    }
                } catch (_: Throwable) {}
            }
            info.coolingDeviceStates = states
        } catch (e: Throwable) { Log.w(TAG, "冷却设备采集失败", e) }
    }

    // ═══════════════════════════════════════════
    //  IO Scheduler — 存储 IO 调度器检测 (内核文档标准接口)
    // ═══════════════════════════════════════════
    private fun collectIoScheduler(info: DeviceDetailInfo) {
        try {
            // 读取主存储设备的 IO 调度器 (通常是 mmcblk0 或 sda)
            val blockDir = File("/sys/block")
            if (!blockDir.exists()) return

            val candidates = blockDir.listFiles()?.filter {
                it.isDirectory && (it.name.startsWith("mmcblk") || it.name.startsWith("sda") || it.name.startsWith("nvme"))
            }?.sortedBy { it.name } ?: return

            for (dev in candidates) {
                val schedulerFile = File(dev, "queue/scheduler")
                if (!schedulerFile.exists()) continue

                val content = schedulerFile.readText().trim()
                // 格式: "noop [mq-deadline] cfq" — 方括号内为当前调度器
                val match = Regex("""\[(\w+(-\w+)*)\]""").find(content)
                if (match != null) {
                    info.ioScheduler = "${dev.name}: ${match.groupValues[1]}"
                    return
                }
            }
        } catch (e: Throwable) { Log.w(TAG, "IO调度器采集失败", e) }
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
