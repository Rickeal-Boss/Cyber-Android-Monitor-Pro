package com.example.deviceinfoviewer

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * 语言管理器 — 基于 Android 官方 per-app language API
 *
 * 架构说明（行业惯例 / 官方推荐方案）：
 * - Android 13+ (API 33+)：通过 [AppCompatDelegate.setApplicationLocales] 委托给系统
 *   [android.os.LocaleManager]，系统设置中原生支持 per-app language，Activity 自动 recreate。
 * - Android < 13：[AppCompatDelegate.setApplicationLocales] 存储 locale 偏好并触发 Activity
 *   recreate；同时配合 [wrapContext] 在 [android.app.Activity.attachBaseContext] 中手动应用
 *   Configuration，确保 Compose stringResource() 加载正确语言的资源。
 * - 用户偏好持久化到 [AppSettings]，下次启动优先读取用户选择而非系统语言。
 *
 * 参考：
 * - https://developer.android.com/guide/topics/resources/app-languages
 * - appcompat 1.6+ release notes（setApplicationLocales 支持任意 Activity）
 *
 * RTL 扩展能力：AndroidManifest 已声明 supportsRtl="true"，新增阿拉伯语等 RTL 语言时
 * 只需在 locales_config 与 strings.xml 中补充资源，布局方向由系统自动镜像。
 */
object LocaleManager {

    private const val TAG = "LocaleManager"

    /** 跟随系统语言（不强制指定 locale） */
    const val LANG_SYSTEM = "system"

    /**
     * 应用支持的语言列表（精简版 v2.0.202.0+）。
     * code → (BCP 47 tag, 原生显示名, 用于资源目录的 locale 文件夹)
     *
     * 精简原则：
     * - 仅保留主要用户群与高 ROI 语言，避免 APK 体积膨胀
     * - 简体中文 + 英文 + 繁体中文 三语覆盖大中华区 + 国际用户（>90% 目标群体）
     * - 繁简转换由 OpenCC s2twp 自动生成，维护成本最低
     *
     * 新增语言步骤：
     * 1. 在此列表追加条目
     * 2. 在 res/xml/locales_config.xml 追加 <locale>
     * 3. 创建 res/values-<tag>/strings.xml
     */
    data class LanguageOption(
        val code: String,           // 持久化存储用
        val tag: String,            // BCP 47，用于 LocaleList / LocaleManager
        val nativeName: String,     // 该语言下的自称（用于选择器展示）
        val resDir: String          // 资源目录后缀，如 zh-rCN
    )

    val SUPPORTED_LANGUAGES: List<LanguageOption> = listOf(
        LanguageOption(LANG_SYSTEM, "", "跟随系统 / Follow System", ""),
        LanguageOption("zh-CN", "zh-CN", "简体中文", "zh-rCN"),
        LanguageOption("en", "en", "English", "en"),
        LanguageOption("zh-TW", "zh-TW", "繁體中文", "zh-rTW"),
    )

    /**
     * 在 [android.app.Activity.attachBaseContext] 中调用，为 Android < 13 手动应用
     * 用户保存的 locale，使 Compose 的 [androidx.compose.res.stringResource] 加载
     * 对应语言的资源。
     *
     * Android 13+ 由系统 LocaleManager 统一处理，此方法对 13+ 是 no-op。
     */
    fun wrapContext(base: Context): Context {
        val saved = runCatching { AppSettings.getInstance(base).appLanguage }.getOrDefault(LANG_SYSTEM)
        if (saved == LANG_SYSTEM) return base

        val option = SUPPORTED_LANGUAGES.find { it.code == saved } ?: return base
        val locale = parseLocale(option.tag)

        // 设置默认 Locale（影响 java.util.Formatter / String.format 等）
        Locale.setDefault(locale)

        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        // Android 24+ (API 24+) 用 LocaleList.setDefault 设置默认列表
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
        }

        return base.createConfigurationContext(config)
    }

    /**
     * 切换应用语言并持久化偏好。
     *
     * - 保存到 [AppSettings]
     * - 调用 [AppCompatDelegate.setApplicationLocales]（API 33+ 系统自动 recreate；
     *   API < 33 触发 AppCompat 的 ActivityLifecycleCallbacks recreate）
     * - 对 API < 33 的纯 ComponentActivity 场景，额外手动 recreate 以确保立即生效
     *
     * @param activity 当前 Activity，用于 API < 33 时手动 recreate；为 null 则仅保存+设置
     */
    fun applyLanguage(context: Context, code: String, activity: Activity? = null) {
        Log.i(TAG, "applyLanguage: $code")

        // 1. 持久化用户偏好
        AppSettings.getInstance(context).appLanguage = code

        // 2. 通过 AppCompatDelegate 设置（API 33+ 委托系统 LocaleManager）
        val locales = if (code == LANG_SYSTEM) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            val option = SUPPORTED_LANGUAGES.find { it.code == code }
            if (option != null) {
                LocaleListCompat.forLanguageTags(option.tag)
            } else {
                LocaleListCompat.getEmptyLocaleList()
            }
        }
        AppCompatDelegate.setApplicationLocales(locales)

        // 3. API < 33：AppCompatDelegate 已通过 ActivityLifecycleCallbacks 触发 recreate，
        //    但纯 ComponentActivity（非 AppCompatActivity）下可能不触发，手动 recreate 兜底
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && activity != null) {
            activity.recreate()
        }
    }

    /** 读取当前持久化的语言 code */
    fun getSavedLanguage(context: Context): String =
        runCatching { AppSettings.getInstance(context).appLanguage }.getOrDefault(LANG_SYSTEM)

    /** 获取当前语言的原生显示名（用于设置页展示） */
    fun getSavedLanguageDisplayName(context: Context): String {
        val code = getSavedLanguage(context)
        return SUPPORTED_LANGUAGES.find { it.code == code }?.nativeName
            ?: SUPPORTED_LANGUAGES.first().nativeName
    }

    /** BCP 47 tag → java.util.Locale */
    private fun parseLocale(tag: String): Locale {
        return if (tag.contains("-")) {
            val parts = tag.split("-", limit = 2)
            Locale(parts[0], parts[1])
        } else {
            Locale(tag)
        }
    }
}
