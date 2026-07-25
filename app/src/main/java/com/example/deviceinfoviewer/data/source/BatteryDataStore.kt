package com.example.deviceinfoviewer.data.source

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

/**
 * 电池探针持久化后端 (Preferences DataStore)。
 *
 * 文件后端跨进程/冷启动存活 —— 进程被杀后重新拉起时, SysFsCapabilityProbe
 * 可直接从 DataStore 读回上次探测结果, 跳过一次 open+read 重探测。
 * 相比 AppSettings 的 SharedPreferences: 类型安全 + 默认异步(Flow), 无主线程阻塞风险。
 */
val Context.batteryDataStore: DataStore<Preferences> by preferencesDataStore(name = "battery_probe")

/** sysfs current_now 节点可读性探针持久化键 (true=可读, false=被 SELinux 拦截) */
val KEY_SYSFS_CURRENT_READABLE = booleanPreferencesKey("sysfs_current_now_readable")
