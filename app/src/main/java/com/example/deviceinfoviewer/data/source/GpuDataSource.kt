package com.example.deviceinfoviewer.data.source

import com.example.deviceinfoviewer.data.model.GpuInfo

/**
 * GPU 数据源 — v3 重写版
 *
 * 增强: 单位精准检测 + 35+ 路径覆盖现代设备 + 系统属性回退 + Throwable 全兜底
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
        val modelProps = arrayOf(
            "ro.gpu.chip", "ro.gfx.driver", "ro.hardware.egl",
            "ro.board.platform", "ro.chipname", "ro.soc.manufacturer"
        )
        for (prop in modelProps) {
            val value = SysFsReader.readProp(prop)
            if (value.isNotEmpty()) { info.model = value; break }
        }
        val vendor = SysFsReader.readProp("ro.soc.manufacturer")
        if (vendor.isNotEmpty()) info.vendor = vendor

        val renderer = SysFsReader.readProp("ro.gles.version")
        if (renderer.isNotEmpty() && info.model.isEmpty()) info.model = renderer
        val eglVendor = SysFsReader.readProp("ro.hardware.egl")
        if (eglVendor.isNotEmpty()) info.renderer = eglVendor

        val gpuModel = SysFsReader.readLine("/sys/kernel/gpu/gpu_model")
        if (gpuModel.isNotEmpty()) info.model = gpuModel.trim()
        val maliGpu = SysFsReader.readLine("/sys/class/misc/mali0/device/gpuinfo")
        if (maliGpu.isNotEmpty() && info.model.isEmpty()) info.model = maliGpu.trim()
    }

    // ===== GPU 频率 (35+ 路径, 精准单位检测) =====
    private fun resolveGpuFrequency(info: GpuInfo) {
        // ═══════════════ 高通 Adreno ═══════════════
        if (tryQualcommKgsl(info)) return

        // ═══════════════ ARM Mali devfreq ═══════════════
        if (tryMaliDevfreq(info)) return

        // ═══════════════ Mali 扩展路径 ═══════════════
        if (tryMaliDirect(info)) return

        // ═══════════════ MTK 天玑 ═══════════════
        if (tryMtk(info)) return

        // ═══════════════ 通用 /sys/kernel/gpu/ ═══════════════
        if (tryKernelGpu(info)) return

        // ═══════════════ 系统属性回退 ═══════════════
        tryPropertyFallback(info)
    }

    // ─── 高通 Adreno ───

    private fun tryQualcommKgsl(info: GpuInfo): Boolean {
        val base = "/sys/class/kgsl/kgsl-3d0/"
        if (!SysFsReader.fileExists(base)) return false

        // 当前频率 — 多路径尝试
        var curFreq = readFreqHz(base + "gpuclk")
        if (curFreq <= 0) curFreq = readFreqHz(base + "devfreq/cur_freq")
        if (curFreq <= 0) curFreq = tryReadMhz(base + "clock_mhz") // clock_mhz 是 MHz 值
        if (curFreq <= 0) curFreq = readFreqKHz(base + "clockspeed_khz") // 某些设备
        if (curFreq <= 0) curFreq = readFreqHz(base + "clock") // 无单位扩展名

        // 最小/最大频率
        var minFreq = readFreqHz(base + "devfreq/min_freq")
        var maxFreq = readFreqHz(base + "devfreq/max_freq")
        if (maxFreq <= 0) maxFreq = readFreqHz(base + "max_gpuclk")
        if (maxFreq <= 0) maxFreq = tryReadMhz(base + "max_clock_mhz")
        if (minFreq <= 0) minFreq = readFreqHz(base + "min_clock_mhz")

        return applyFreqInfo(info, curFreq, minFreq, maxFreq)
    }

    // ─── ARM Mali devfreq ───

    private fun tryMaliDevfreq(info: GpuInfo): Boolean {
        val dirs = SysFsReader.listDir("/sys/class/devfreq/")
        for (dir in dirs) {
            val lower = dir.lowercase()
            if (!lower.contains("gpu") && !lower.contains("mali")
                && !lower.contains("sgpu") && !lower.contains("gpufreq") && !lower.contains("g3d"))
                continue

            val base = "/sys/class/devfreq/$dir/"
            var curFreq = readFreqHz(base + "cur_freq")
            if (curFreq <= 0) curFreq = readFreqHz(base + "current_frequency")
            var minFreq = readFreqHz(base + "min_freq")
            var maxFreq = readFreqHz(base + "max_freq")
            if (maxFreq <= 0) maxFreq = readFreqHz(base + "available_frequencies") // 某些 Mali

            if (curFreq > 0) return applyFreqInfo(info, curFreq, minFreq, maxFreq)
        }
        return false
    }

    // ─── Mali 直接路径 (debugfs / proc) ───

    private fun tryMaliDirect(info: GpuInfo): Boolean {
        // /sys/kernel/gpu/
        var curFreq = readFreqKHz("/sys/kernel/gpu/gpu_freq_max")
        if (curFreq <= 0) curFreq = readFreqKHz("/sys/kernel/gpu/gpu_clock")
        if (curFreq <= 0) curFreq = readFreqKHz("/sys/kernel/gpu/gpu_cur_freq")
        if (curFreq <= 0) curFreq = readFreqKHz("/sys/kernel/gpu/gpu_freq")

        // /proc/mali/
        if (curFreq <= 0) curFreq = readFreqKHz("/proc/mali/gpu_freq")
        if (curFreq <= 0) curFreq = readFreqKHz("/proc/mali/gpu_clock")

        // Samsung Exynos
        if (curFreq <= 0) curFreq = readFreqKHz("/sys/devices/platform/11800000.mali/clock")
        if (curFreq <= 0) curFreq = readFreqKHz("/sys/devices/platform/14ac0000.mali/devfreq/cur_freq")

        if (curFreq > 0) return applyFreqInfo(info, curFreq, -1, -1)

        // PowerVR
        curFreq = readFreqKHz("/sys/kernel/gpu/gpu_freq")
        return applyFreqInfo(info, curFreq, -1, -1)
    }

    // ─── MTK 天玑 ───

    private fun tryMtk(info: GpuInfo): Boolean {
        var curFreq = readFreqKHz("/sys/module/ged/parameters/gpu_freq")
        if (curFreq <= 0) curFreq = readFreqKHz("/proc/gpufreq/gpufreq_var")
        if (curFreq <= 0) curFreq = readFreqKHz("/proc/gpufreq/gpufreq_opp_freq")
        if (curFreq <= 0) curFreq = readFreqKHz("/sys/devices/platform/mtk-gpu/cur_freq")

        // MTK 也暴露在 devfreq 中
        if (curFreq <= 0) {
            val dirs = SysFsReader.listDir("/sys/class/devfreq/")
            for (dir in dirs) {
                if (dir.lowercase().contains("mtk") || dir.lowercase().contains("ged")) {
                    curFreq = readFreqHz("/sys/class/devfreq/$dir/cur_freq")
                    if (curFreq > 0) break
                }
            }
        }

        return applyFreqInfo(info, curFreq, -1, -1)
    }

    // ─── 通用 /sys/kernel/gpu/ ───

    private fun tryKernelGpu(info: GpuInfo): Boolean {
        val gpuFiles = SysFsReader.listDir("/sys/kernel/gpu/")
        var curFreq: Long = -1
        var maxFreq: Long = -1

        for (file in gpuFiles) {
            val lower = file.lowercase()
            if (lower.contains("freq") && !lower.contains("table") && !lower.contains("available")) {
                when {
                    lower.contains("max") || lower.contains("highest") -> {
                        val v = readFreqKHz("/sys/kernel/gpu/$file")
                        if (v > maxFreq) maxFreq = v
                    }
                    else -> {
                        val v = readFreqKHz("/sys/kernel/gpu/$file")
                        if (v > 0 && (curFreq < 0 || v < curFreq)) curFreq = v
                    }
                }
            }
        }

        return applyFreqInfo(info, curFreq, -1, maxFreq)
    }

    // ─── 系统属性回退 ───

    private fun tryPropertyFallback(info: GpuInfo) {
        val props = arrayOf(
            "sys.gpu.cur_freq", "vendor.gpu.freq", "ro.vendor.gpu.freq",
            "debug.sf.gpu_clock", "persist.vendor.gpu.freq", "sys.gpu.freq"
        )
        for (prop in props) {
            val value = SysFsReader.readProp(prop)
            if (value.isEmpty()) continue
            val freqKHz = parsePropFreq(value)
            if (freqKHz > 0) {
                info.frequencyKHz = freqKHz
                return
            }
        }
    }

    // ═══════════════ 辅助读取方法 ═══════════════

    /** 读取 Hz 值 (值 >= 100000 且在 GHz 范围内) */
    private fun readFreqHz(path: String): Long {
        val raw = tryRead(path)
        if (raw <= 0) return -1
        return when {
            // > 1 GHz → 已是 Hz (如 675000000 = 675 MHz)
            raw > 100_000_000 -> raw
            // 100K~100M → 疑似 KHz (如 675000 = 675 MHz in KHz)
            raw in 100_000..100_000_000 -> raw * 1000
            // 100~100K → 疑似 MHz (如 675 = 675 MHz)
            raw in 100..100_000 -> raw * 1_000_000
            // < 100 → 太小，忽略
            else -> -1
        }
    }

    /** 读取 KHz/Hz 混合值 (适用于 MTK/Mali proc 路径)，始终返回 Hz */
    private fun readFreqKHz(path: String): Long {
        val raw = tryRead(path)
        if (raw <= 0) return -1
        return when {
            raw > 10_000_000 -> raw               // 已是 Hz
            raw > 100 -> raw * 1000               // KHz → Hz
            else -> -1
        }
    }

    /** 读取 MHz 值 (含 "mhz" 的路径) */
    private fun tryReadMhz(path: String): Long {
        val raw = tryRead(path)
        if (raw <= 0) return -1
        // MHz → Hz
        return raw * 1_000_000
    }

    private fun tryRead(path: String): Long {
        try {
            if (!SysFsReader.fileExists(path)) return -1
            val line = SysFsReader.readLine(path)
            if (line.isEmpty()) return -1
            // 去除 " KHz", " MHz" 等后缀
            val cleaned = line.replace(Regex("[^0-9]"), "")
            return cleaned.toLongOrNull() ?: -1
        } catch (_: Throwable) { return -1 }
    }

    /** 解析系统属性中的频率值 (可能是 "675000000" 或 "675 MHz") */
    private fun parsePropFreq(value: String): Long {
        val cleaned = value.trim().replace(Regex("[^0-9.]"), "")
        val num = cleaned.toDoubleOrNull() ?: return -1
        return when {
            num > 1_000_000 -> num.toLong() / 1000         // Hz → KHz
            num > 100 -> (num * 1000).toLong()              // MHz → KHz
            else -> (num * 1_000_000).toLong()              // GHz → KHz
        }
    }

    private fun applyFreqInfo(info: GpuInfo, curFreqHz: Long, minFreqHz: Long, maxFreqHz: Long): Boolean {
        if (curFreqHz > 0) info.frequencyKHz = curFreqHz / 1000
        if (minFreqHz > 0) info.minFreqKHz = minFreqHz / 1000
        if (maxFreqHz > 0) info.maxFreqKHz = maxFreqHz / 1000
        return curFreqHz > 0
    }

    // ===== 调速器信息 =====
    private fun resolveGovernor(info: GpuInfo) {
        // 高通 Adreno
        var gov = SysFsReader.readLine("/sys/class/kgsl/kgsl-3d0/devfreq/governor")
        if (gov.isNotEmpty()) {
            info.governor = gov.trim()
            val avail = SysFsReader.readAll("/sys/class/kgsl/kgsl-3d0/devfreq/available_governors")
            if (avail.isNotEmpty()) info.availableGovernors = avail.replace('\n', ' ').trim()
            return
        }
        // 通用 devfreq
        val dirs = SysFsReader.listDir("/sys/class/devfreq/")
        for (dir in dirs) {
            if (dir.lowercase().let { it.contains("gpu") || it.contains("mali") }) {
                gov = SysFsReader.readLine("/sys/class/devfreq/$dir/governor")
                if (gov.isNotEmpty()) {
                    info.governor = gov.trim()
                    val avail = SysFsReader.readAll("/sys/class/devfreq/$dir/available_governors")
                    if (avail.isNotEmpty()) info.availableGovernors = avail.replace('\n', ' ').trim()
                    return
                }
            }
        }
        gov = SysFsReader.readProp("ro.gpu.governor")
        if (gov.isNotEmpty()) info.governor = gov
    }

    // ===== GPU 负载率 =====
    private fun resolveLoad(info: GpuInfo) {
        // 高通 Adreno
        val load = SysFsReader.readFloat("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage")
        if (!load.isNaN() && load > 0) { info.loadPercentage = load; return }

        // 高通 gpubusy
        val gpuBusy = SysFsReader.readLine("/sys/class/kgsl/kgsl-3d0/gpubusy")
        if (gpuBusy.isNotEmpty()) {
            val parts = gpuBusy.trim().split("\\s+".toRegex())
            if (parts.size >= 2) {
                try {
                    val used = parts[0].toLong()
                    val total = parts[1].toLong()
                    if (total > 0) { info.loadPercentage = used.toFloat() / total * 100f; return }
                } catch (_: Throwable) {}
            }
        }

        // Mali devfreq load
        val dirs = SysFsReader.listDir("/sys/class/devfreq/")
        for (dir in dirs) {
            if (!dir.lowercase().let { it.contains("gpu") || it.contains("mali") }) continue
            val loadStr = SysFsReader.readLine("/sys/class/devfreq/$dir/load")
            if (loadStr.isEmpty()) continue
            var parts = loadStr.split("@")
            if (parts.size == 1) parts = loadStr.split("\\s+".toRegex())
            for (part in parts) {
                part.replace("%", "").trim().toFloatOrNull()?.let { v ->
                    if (v in 0.0..100.0) { info.loadPercentage = v; return }
                }
            }
        }
    }

    // ===== GPU 温度 =====
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
