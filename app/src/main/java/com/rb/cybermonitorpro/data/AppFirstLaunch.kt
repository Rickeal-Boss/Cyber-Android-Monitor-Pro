package com.rb.cybermonitorpro.data

import android.content.Context

/**
 * 首次启动权限引导一次性标记。
 * 与悬浮窗设置页的 floatsettings_first_opened 独立（那是"首次打开悬浮窗页"，
 * 这是"首次启动 App"），命名空间分开互不干扰。
 */
object AppFirstLaunch {
    private const val PREFS = "first_launch"
    private const val KEY = "first_launch_v1"

    fun shouldShow(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)

    fun markShown(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, false).apply()
    }
}
