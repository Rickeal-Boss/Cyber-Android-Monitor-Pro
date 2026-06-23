package com.example.deviceinfoviewer.data.source

import android.util.Log

/** Base class for sysfs data sources with IO + shell fallback */
abstract class BaseSysFsDataSource(protected val tag: String = "SysFsDS") {

    /**
     * Reads sysfs file content (direct IO + shell fallback).
     *
     * Android 13+ tightened SELinux may prevent direct reads; falls back to
     * Runtime.exec("cat $path") to bypass file-level restrictions.
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
     * Probes: find the first existing file from a candidate list.
     * Called only at first startup; results cache to PathRegistry.
     * Max probes per startup: 3 (exact → oem+soc → generic).
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
