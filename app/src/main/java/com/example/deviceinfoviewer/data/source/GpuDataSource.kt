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

    // ===== GPU 频率 (动态优先 + 负载估算兜底) =====
    private fun resolveGpuFrequency(info: GpuInfo) {
        // Phase 1: 先尝试获取最大频率 (SELinux 对 max_freq 限制可能比 cur_freq 松)
        resolveMaxFrequency(info)

        // Phase 2: 动态当前频率
        // ═══════════════ 高通 Adreno ═══════════════
        if (tryQualcommKgsl(info)) return
        if (tryQualcommExpanded(info)) return

        // ═══════════════ ARM Mali devfreq ═══════════════
        if (tryMaliDevfreq(info)) return

        // ═══════════════ Mali 扩展路径 ═══════════════
        if (tryMaliDirect(info)) return

        // ═══════════════ MTK 天玑 ═══════════════
        if (tryMtk(info)) return

        // ═══════════════ 通用 /sys/kernel/gpu/ ═══════════════
        if (tryKernelGpu(info)) return

        // ═══════════════ 系统属性 (优先动态 sys.* 属性) ═══════════════
        if (tryDynamicProperty(info)) return

        // ═══════════════ 兜底: 负载率 × 最大频率 估算 ═══════════════
        if (estimateFromLoad(info)) return

        // ═══════════════ 静态属性最后兜底 ═══════════════
        tryPropertyFallback(info)
    }

    // ─── 高通 Adreno ───

    /** Phase 1: 在所有已知路径中获取最大 GPU 频率 (用于负载估算) */
    private fun resolveMaxFrequency(info: GpuInfo) {
        val maxPaths = arrayOf(
            "/sys/class/kgsl/kgsl-3d0/max_gpuclk" to "Hz",
            "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq" to "Hz",
            "/sys/class/kgsl/kgsl-3d0/max_clock_mhz" to "MHz",
            "/sys/class/kgsl/kgsl-3d1/max_gpuclk" to "Hz",
            "/sys/class/kgsl/kgsl-3d1/devfreq/max_freq" to "Hz"
        )
        for ((path, unit) in maxPaths) {
            val freq = readFreqAuto(path, unit)
            if (freq > 0) { info.maxFreqKHz = freq / 1000; break }
        }
        // devfreq 扫描 max_freq
        if (info.maxFreqKHz <= 0) {
            val dirs = SysFsReader.listDir("/sys/class/devfreq/")
            for (dir in dirs) {
                if (!dir.lowercase().let { it.contains("gpu") || it.contains("mali") || it.contains("kgsl") }) continue
                val maxF = readFreqAuto("/sys/class/devfreq/$dir/max_freq", "Hz")
                if (maxF > 0) { info.maxFreqKHz = maxF / 1000; break }
            }
        }
        // 属性回退
        if (info.maxFreqKHz <= 0) {
            val prop = SysFsReader.readProp("ro.vendor.gpu.max_freq")
                .ifEmpty { SysFsReader.readProp("ro.boot.gpu.max_freq") }
                .ifEmpty { SysFsReader.readProp("ro.gpu.max_freq") }
            val maxHz = parseToHz(prop, "Hz")
            if (maxHz > 0) info.maxFreqKHz = maxHz / 1000
        }
    }

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

    /** 高通 Adreno 扩展路径 (kgsl-3d1, 不同内核命名) */
    private fun tryQualcommExpanded(info: GpuInfo): Boolean {
        val altBases = arrayOf(
            "/sys/class/kgsl/kgsl-3d1/",
            "/sys/devices/platform/kgsl-3d0.0/kgsl/kgsl-3d0/",
            "/sys/devices/soc/1c00000.qcom,kgsl-3d0/kgsl/kgsl-3d0/",
            "/sys/kernel/gpu/",
            "/sys/devices/virtual/kgsl/kgsl-3d0/"
        )
        for (base in altBases) {
            if (!SysFsReader.fileExists(base)) continue
            var curFreq = readFreqAuto(base + "gpuclk", "Hz")
            if (curFreq <= 0) curFreq = readFreqAuto(base + "devfreq/cur_freq", "Hz")
            if (curFreq <= 0) curFreq = readFreqAuto(base + "cur_freq", "Hz")
            if (curFreq <= 0) continue

            var maxFreq = readFreqAuto(base + "max_gpuclk", "Hz")
            if (maxFreq <= 0) maxFreq = readFreqAuto(base + "devfreq/max_freq", "Hz")
            return applyFreqInfo(info, curFreq, -1, maxFreq)
        }
        return false
    }

    /** 动态系统属性 — sys.*/vendor.* 可能在运行时更新 */
    private fun tryDynamicProperty(info: GpuInfo): Boolean {
        // 这些属性名以 sys./vendor./debug. 开头，可能是动态更新
        val props = arrayOf(
            "sys.gpu.cur_freq", "vendor.gpu.freq",
            "debug.sf.gpu_clock", "sys.gpu.freq",
            "vendor.gpu.cur_freq", "sys.gpu.clock"
        )
        for (prop in props) {
            val value = SysFsReader.readProp(prop)
            if (value.isEmpty()) continue
            val freqHz = parseToHz(value, "Hz")
            if (freqHz > 0) {
                info.frequencyKHz = freqHz / 1000
                return true
            }
        }
        return false
    }

    /** 负载率兜底估算: current = maxFreq × load% (无法读实际频率时的动态近似) */
    private fun estimateFromLoad(info: GpuInfo): Boolean {
        if (info.maxFreqKHz <= 0) return false
        if (info.loadPercentage.isNaN() || info.loadPercentage < 0f) return false

        // 根据当前负载估算频率 (非精确, 但比固定静态值好):
        // idle(0%) → minFreqKHz or ~30% max
        // loaded(100%) → maxFreqKHz
        val minEstimated = (info.maxFreqKHz * 0.3f).toLong()
        val estimated = minEstimated + ((info.maxFreqKHz - minEstimated) * info.loadPercentage / 100f).toLong()
        info.frequencyKHz = estimated.coerceIn(minEstimated, info.maxFreqKHz)
        return true
    }

    /** 静态系统属性回退 (ro.*) */
    private fun tryPropertyFallback(info: GpuInfo) {
        // 仅 ro.* 静态属性 (动态属性已在 tryDynamicProperty 中处理)
        val props = arrayOf(
            "ro.vendor.gpu.freq", "ro.gpu.chip",
            "ro.boot.gpu.freq", "ro.gpu.freq"
        )
        for (prop in props) {
            val value = SysFsReader.readProp(prop)
            if (value.isEmpty()) continue
            val freqHz = parseToHz(value, "Hz")
            if (freqHz > 0) {
                info.frequencyKHz = freqHz / 1000
                return
            }
        }
    }

    // ═══════════════ 辅助读取方法 — 单位感知版 ═══════════════

    /** 读取原始文本 (数值+可能的单位后缀) */
    private fun tryReadRaw(path: String): String {
        try {
            if (!SysFsReader.fileExists(path)) return ""
            return SysFsReader.readLine(path).trim()
        } catch (_: Throwable) { return "" }
    }

    /** 从原始文本中提取数值和单位，返回 Hz */
    private fun parseToHz(raw: String, defaultUnit: String = "Hz"): Long {
        if (raw.isEmpty()) return -1
        // 匹配 "587000000", "587 MHz", "587.5 MHz", "587000 KHz" 等
        val match = Regex("""(\d+\.?\d*)\s*(MHz|GHz|KHz|kHz|Hz|Mhz|Ghz|Khz)?""", RegexOption.IGNORE_CASE).find(raw.trim())
            ?: return -1
        val num = match.groupValues[1].toDoubleOrNull() ?: return -1
        val unit = match.groupValues.getOrNull(2)?.lowercase()?.takeIf { it.isNotEmpty() } ?: defaultUnit.lowercase()

        return when (unit) {
            "ghz" -> (num * 1_000_000_000).toLong()
            "mhz" -> (num * 1_000_000).toLong()
            "khz" -> (num * 1_000).toLong()
            else   -> num.toLong()  // raw Hz
        }
    }

    /** 从文件读取频率（自动检测单位），并验证范围 (100MHz ~ 2GHz) */
    private fun readFreqAuto(path: String, defaultUnit: String = "Hz"): Long {
        val raw = tryReadRaw(path)
        if (raw.isEmpty()) return -1
        val hz = parseToHz(raw, defaultUnit)
        // 验证: 移动 GPU 合理范围 100MHz ~ 2GHz
        return if (hz in 50_000_000..2_500_000_000) hz else -1
    }

    /** 读取 Hz 值 (legacy — 保持向后兼容，内部调用 readFreqAuto) */
    private fun readFreqHz(path: String): Long = readFreqAuto(path, "Hz")

    /** 读取假定为 KHz 的值 */
    private fun readFreqKHz(path: String): Long = readFreqAuto(path, "KHz")

    /** 读取假定为 MHz 的值 */
    private fun tryReadMhz(path: String): Long = readFreqAuto(path, "MHz")

    /** 解析系统属性中的频率值 (可能是 "675000000" 或 "675 MHz") */
    private fun parsePropFreq(value: String): Long {
        return parseToHz(value, "Hz")
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

        // === P1: dumpsys gfxinfo 帧率兜底 ===
        // 当所有 sysfs 路径都无法获取 GPU 负载时，使用 Jank 帧率估算
        try {
            val gfxOutput = ShellCommandDataSource.getDumpsysGfxinfo()
            val estimatedLoad = ShellCommandDataSource.estimateGpuLoadFromGfx(gfxOutput)
            if (!estimatedLoad.isNaN()) {
                info.loadPercentage = estimatedLoad
                info.loadSource = "dumpsys gfxinfo (Jank rate)"
            }
        } catch (_: Throwable) {}
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
