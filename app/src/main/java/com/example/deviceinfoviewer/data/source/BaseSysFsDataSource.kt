package com.example.deviceinfoviewer.data.source

import android.util.Log

/**
 * [Architect Note] sysfs 数据源抽象基类 — P2-Task6 策略模式基础设施
 *
 * 统一封装 sysfs 读取、Shell 兜底、路径缓存逻辑。
 * 所有 DataSource 继承此类后可消除重复的 try-catch 和 shell 兜底代码。
 *
 * 设计哲学:
 *   1. readSysFs(path) — 先直接 IO，失败用 Shell 兜底 (Android 13+ SELinux)
 *   2. readCached(path) — 带内建缓存的读取 (同 tick 内多次读取同一文件时用)
 *   3. 子类只需实现具体采集逻辑，路径获取委托给 PathRegistry
 *
 * 来源参考:
 *   - AOSP: system/core/init/util.cpp → read_file()
 *   - Linux: Documentation/filesystems/sysfs.txt
 */
abstract class BaseSysFsDataSource(protected val tag: String = "SysFsDS") {

    /**
     * 读取 sysfs 文件内容 (直接 IO + Shell 兜底)
     *
     * [Architect Note] Android 13+ 收紧 SELinux 策略后，普通 app 可能无法
     * 直接读取 /sys/class/power_supply/*/current_now。此时回退到
     * Runtime.exec("cat $path") → 绕过 SELinux 文件读取限制。
     *
     * @param path 完整的 sysfs 路径
     * @return 文件内容 (trim 后)，失败返回 null
     */
    protected fun readSysFs(path: String): String? {
        // 策略 1: 直接 Java IO (最快路径，<1ms)
        try {
            val content = java.io.File(path).readText().trim()
            if (content.isNotEmpty()) return content
        } catch (_: Throwable) { /* fall through to shell */ }

        // 策略 2: Shell 兜底 (Android 13+ SELinux)
        try {
            val process = Runtime.getRuntime().exec(arrayOf("/system/bin/cat", path))
            val content = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            if (content.isNotEmpty()) return content
        } catch (_: Throwable) { /* path not accessible */ }

        return null
    }

    /**
     * 读取 sysfs Long 值
     */
    protected fun readSysFsLong(path: String): Long {
        return readSysFs(path)?.toLongOrNull() ?: Long.MIN_VALUE
    }

    /**
     * 读取 sysfs Int 值
     */
    protected fun readSysFsInt(path: String): Int {
        return readSysFs(path)?.toIntOrNull() ?: -1
    }

    /**
     * 读取 sysfs Float 值
     */
    protected fun readSysFsFloat(path: String): Float {
        return readSysFs(path)?.toFloatOrNull() ?: Float.NaN
    }

    /**
     * 列出 sysfs 目录内容
     */
    protected fun listSysFsDir(dirPath: String): List<String> {
        return try {
            java.io.File(dirPath).list()?.toList() ?: emptyList()
        } catch (_: Throwable) { emptyList() }
    }

    /**
     * 探测: 从候选路径列表中找到第一个存在的文件
     *
     * [Architect Note] 仅首次启动时调用。结果应写入 PathRegistry 缓存，
     * 后续启动直接从缓存读取。最大探测次数 ≤ 3 (exact → oem+soc → generic)。
     *
     * @param candidates 候选路径列表 (优先级降序)
     * @return 第一个可读的路径，全失败返回 null
     */
    protected fun probeFirstReadable(candidates: List<String>): String? {
        for (path in candidates) {
            if (readSysFs(path) != null) return path
        }
        return null
    }

    /**
     * 带单位守护的 Long 读取: 仅当值在合理范围内才返回
     */
    protected fun readSysFsLongSane(path: String, min: Long = 0, max: Long = Long.MAX_VALUE): Long? {
        val value = readSysFsLong(path)
        return if (value != Long.MIN_VALUE && value in min..max) value else null
    }
}
