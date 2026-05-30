package com.example.deviceinfoviewer.data.source

import com.example.deviceinfoviewer.data.model.GpuInfo

/**
 * GPU 数据源 — 增强版
 * 高通 Adreno + ARM Mali + PowerVR + 全设备通用路径
 * 新增：负载率、频率范围、调速器、OpenGL Renderer
 */
class GpuDataSource {

    fun getGpuInfo(): GpuInfo {
        val info = GpuInfo()
        info.timestamp = System.currentTimeMillis()

        // 1. 型号 & 厂商 & OpenGL 渲染器
        resolveGpuModel(info)

        // 2. 频率 (当前 + 最小 + 最大)
        resolveGpuFrequency(info)

        // 3. 调速器信息
        resolveGovernor(info)

        // 4. 负载率
        resolveLoad(info)

        // 5. 温度
        info.temperatureCelsius = getGpuTemperature()

        return info
    }

    // ===== GPU 型号 & 厂商 & 渲染器 =====
    private fun resolveGpuModel(info: GpuInfo) {
        // 系统属性 → 型号
        val modelProps = arrayOf(
            "ro.gpu.chip", "ro.gfx.driver", "ro.hardware.egl",
            "ro.board.platform", "ro.chipname", "ro.soc.manufacturer"
        )
        for (prop in modelProps) {
            val value = SysFsReader.readProp(prop)
            if (value.isNotEmpty()) {
                info.model = value
                break
            }
        }
        // 厂商
        val vendor = SysFsReader.readProp("ro.soc.manufacturer")
        if (vendor.isNotEmpty()) info.vendor = vendor

        // OpenGL ES 渲染器 (如 "Adreno (TM) 730")
        val renderer = SysFsReader.readProp("ro.gles.version")
        if (renderer.isNotEmpty() && info.model.isEmpty()) {
            info.model = renderer
        }
        // EGL 信息
        val eglVendor = SysFsReader.readProp("ro.hardware.egl")
        if (eglVendor.isNotEmpty()) {
            // 提取 Adreno/Mali/PowerVR 名称
            info.renderer = eglVendor
        }

        // Exynos / ARM Mali 文件
        val model = SysFsReader.readLine("/sys/kernel/gpu/gpu_model")
        if (model.isNotEmpty()) info.model = model.trim()

        // Mali gpuinfo
        val gpuInfoLine = SysFsReader.readLine("/sys/class/misc/mali0/device/gpuinfo")
        if (gpuInfoLine.isNotEmpty() && info.model.isEmpty()) {
            info.model = gpuInfoLine.trim()
        }
    }

    // ===== GPU 频率 (多平台, 增强) =====
    private fun resolveGpuFrequency(info: GpuInfo) {
        var curFreq: Long = -1
        var minFreq: Long = -1
        var maxFreq: Long = -1

        // --- 高通 Adreno: /sys/class/kgsl/kgsl-3d0/ ---
        if (SysFsReader.fileExists("/sys/class/kgsl/kgsl-3d0/")) {
            curFreq = tryReadFreqHz("/sys/class/kgsl/kgsl-3d0/gpuclk")
            if (curFreq <= 0) curFreq = tryReadFreqHz("/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq")
            if (curFreq <= 0) curFreq = tryReadFreqHz("/sys/class/kgsl/kgsl-3d0/clock_mhz")

            minFreq = tryReadFreqHz("/sys/class/kgsl/kgsl-3d0/devfreq/min_freq")
            maxFreq = tryReadFreqHz("/sys/class/kgsl/kgsl-3d0/devfreq/max_freq")

            if (curFreq > 0) { info.frequencyKHz = curFreq / 1000 }
            if (minFreq > 0) { info.minFreqKHz = minFreq / 1000 }
            if (maxFreq > 0) { info.maxFreqKHz = maxFreq / 1000 }
            if (curFreq > 0) return
        }

        // --- ARM Mali (通用 devfreq) ---
        val devfreqDirs = SysFsReader.listDir("/sys/class/devfreq/")
        for (dir in devfreqDirs) {
            if (dir.lowercase().let { it.contains("gpu") || it.contains("mali")
                    || it.contains("sgpu") || it.contains("gpufreq") }) {
                val base = "/sys/class/devfreq/$dir/"
                curFreq = tryReadFreqHz(base + "cur_freq")
                if (curFreq > 0) {
                    info.frequencyKHz = curFreq / 1000
                    minFreq = tryReadFreqHz(base + "min_freq")
                    maxFreq = tryReadFreqHz(base + "max_freq")
                    if (minFreq > 0) info.minFreqKHz = minFreq / 1000
                    if (maxFreq > 0) info.maxFreqKHz = maxFreq / 1000
                    return
                }
            }
        }

        // --- Mali debugfs ---
        curFreq = tryReadFreqHz("/sys/kernel/gpu/gpu_freq_max")
        if (curFreq <= 0) curFreq = tryReadFreqHz("/sys/kernel/gpu/gpu_clock")
        if (curFreq > 0) { info.frequencyKHz = curFreq / 1000; return }

        // --- MTK ---
        curFreq = tryReadFreqHz("/sys/module/ged/parameters/gpu_freq")
        if (curFreq > 0) { info.frequencyKHz = curFreq / 1000; return }

        // --- PowerVR ---
        curFreq = tryReadFreqHz("/sys/kernel/gpu/gpu_freq")
        if (curFreq > 0) { info.frequencyKHz = curFreq / 1000; return }

        // --- Mali /proc/mali ---
        curFreq = tryReadFreqHz("/proc/mali/gpu_freq")
        if (curFreq > 0) info.frequencyKHz = curFreq / 1000
    }

    // ===== 调速器信息 =====
    private fun resolveGovernor(info: GpuInfo) {
        // 高通 Adreno
        var gov = SysFsReader.readLine("/sys/class/kgsl/kgsl-3d0/devfreq/governor")
        if (gov.isNotEmpty()) {
            info.governor = gov.trim()
            val availGovs = SysFsReader.readAll("/sys/class/kgsl/kgsl-3d0/devfreq/available_governors")
            if (availGovs.isNotEmpty()) {
                info.availableGovernors = availGovs.replace('\n', ' ').trim()
            }
            return
        }

        // 通用 devfreq
        val dirs = SysFsReader.listDir("/sys/class/devfreq/")
        for (dir in dirs) {
            if (dir.lowercase().let { it.contains("gpu") || it.contains("mali") }) {
                gov = SysFsReader.readLine("/sys/class/devfreq/$dir/governor")
                if (gov.isNotEmpty()) {
                    info.governor = gov.trim()
                    val availGovs = SysFsReader.readAll("/sys/class/devfreq/$dir/available_governors")
                    if (availGovs.isNotEmpty()) {
                        info.availableGovernors = availGovs.replace('\n', ' ').trim()
                    }
                    return
                }
            }
        }

        // CPU GPU 调速器属性
        gov = SysFsReader.readProp("ro.gpu.governor")
        if (gov.isNotEmpty()) info.governor = gov
    }

    // ===== GPU 负载率 =====
    private fun resolveLoad(info: GpuInfo) {
        // 高通 Adreno
        val load = SysFsReader.readFloat("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage")
        if (!load.isNaN() && load > 0) {
            info.loadPercentage = load
            return
        }

        // 高通 gpubusy (格式: "used total" 如 "12345678 98765432")
        val gpuBusy = SysFsReader.readLine("/sys/class/kgsl/kgsl-3d0/gpubusy")
        if (gpuBusy.isNotEmpty()) {
            val parts = gpuBusy.trim().split("\\s+".toRegex())
            if (parts.size >= 2) {
                try {
                    val used = parts[0].toLong()
                    val total = parts[1].toLong()
                    if (total > 0) {
                        info.loadPercentage = used.toFloat() / total.toFloat() * 100f
                        return
                    }
                } catch (_: NumberFormatException) {}
            }
        }

        // Mali: /sys/class/devfreq/*gpu*/load
        val dirs = SysFsReader.listDir("/sys/class/devfreq/")
        for (dir in dirs) {
            if (dir.lowercase().let { it.contains("gpu") || it.contains("mali") }) {
                val loadStr = SysFsReader.readLine("/sys/class/devfreq/$dir/load")
                if (loadStr.isNotEmpty()) {
                    // 格式: "frequency load%" 如 "675000000 45%"
                    var parts = loadStr.split("@")
                    if (parts.size == 1) parts = loadStr.split("\\s+".toRegex())
                    for (part in parts) {
                        val trimmed = part.replace("%", "").trim()
                        trimmed.toFloatOrNull()?.let { value ->
                            if (value in 0.0..100.0) {
                                info.loadPercentage = value
                                return
                            }
                        }
                    }
                }
            }
        }
    }

    private fun tryReadFreqHz(path: String): Long {
        if (!SysFsReader.fileExists(path)) return -1
        var value = SysFsReader.readLong(path)
        if (value <= 0) return -1
        // 自动检测单位：> 1e8 可能已是 Hz；< 1e3 可能是 MHz
        if (value < 1000) value *= 1_000_000  // MHz → Hz
        return value
    }

    // ===== GPU 温度 (全平台 thermal zone 扫描) =====
    private fun getGpuTemperature(): Float {
        val thermalBases = arrayOf("/sys/class/thermal/", "/sys/devices/virtual/thermal/")
        for (base in thermalBases) {
            val zones = SysFsReader.listDir(base)
            for (zone in zones) {
                val type = SysFsReader.readLine(base + zone + "/type").lowercase().trim()
                if (isGpuThermal(type)) {
                    val temp = SysFsReader.readFloat(base + zone + "/temp")
                    if (!temp.isNaN()) {
                        return if (temp > 1000f) temp / 1000f else temp
                    }
                }
            }
        }
        return Float.NaN
    }

    private fun isGpuThermal(type: String): Boolean {
        return type.contains("gpu") || type.contains("kgsl") || type.contains("mali")
                || type.contains("mtktsgpu") || type.contains("tztsgpu")
                || type.contains("sgpu") || type.contains("gpuss")
    }
}
