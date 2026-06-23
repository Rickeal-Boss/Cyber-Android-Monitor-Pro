package com.example.deviceinfoviewer.util

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.Log

/**
 * 预测性返回手势兼容性检测工具
 *
 * 国产定制 ROM (MIUI/ColorOS/OriginOS/HarmonyOS) 对 Android 原生预测性返回手势的支持参差不齐:
 * - Android 13-14: 需开发者选项开启"预测性返回动画"，14+ 默认开启
 * - Android 15+: 系统强制启用，但国产 ROM 可能有自己的手势拦截层
 * - 部分国产 ROM 即使 enableOnBackInvokedCallback=true，也可能不触发 OnBackInvokedCallback
 *
 * 本工具用于运行时检测系统支持情况，输出诊断日志，辅助排查兼容性问题。
 *
 * 参考:
 * - developer.android.com/guide/navigation/custom-back/predictive-back-gesture
 * - OnBackInvokedDispatcher (Android 13+ 平台 API)
 * - OnBackPressedDispatcher (AndroidX 兼容层，所有版本可用)
 */
object BackGestureCompat {

    private const val TAG = "BackGestureCompat"

    /**
     * 检测当前系统是否支持预测性返回手势的进度回调
     *
     * 判定条件:
     * 1. Build.VERSION.SDK_INT >= 33 (OnBackInvokedCallback API 门槛)
     * 2. Activity 的 onCreate 中已通过 AndroidManifest 声明 enableOnBackInvokedCallback=true
     *    (此处通过反射 OnBackInvokedDispatcher 检测，非权威但足够诊断)
     *
     * 注意: 即使返回 true，国产 ROM 仍可能因自身手势拦截层导致进度回调不触发。
     * 此时 PredictiveBackHandler 的 flow 为空 → 立即完成，等价普通 BackHandler，
     * 覆盖层仍能正常关闭（仅无缩放/位移进度动画）。
     */
    fun isPredictiveBackSupported(activity: Activity): Boolean {
        // Android 13+ 才有 OnBackInvokedDispatcher
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Log.d(TAG, "API ${Build.VERSION.SDK_INT} < 33, predictive back NOT supported (legacy BackHandler)")
            return false
        }

        // 反射检测 OnBackInvokedDispatcher 是否存在
        return try {
            val dispatcher = activity.javaClass.getMethod("getOnBackInvokedDispatcher").invoke(activity)
            val supported = dispatcher != null
            Log.d(TAG, "API ${Build.VERSION.SDK_INT}, OnBackInvokedDispatcher=${if (supported) "available" else "null"}, " +
                    "predictive back ${if (supported) "SUPPORTED" else "NOT supported"}")
            logOemInfo()
            supported
        } catch (e: Throwable) {
            Log.w(TAG, "OnBackInvokedDispatcher reflection failed, predictive back NOT supported", e)
            false
        }
    }

    /**
     * 识别并记录国产 ROM 信息（辅助诊断）
     */
    private fun logOemInfo() {
        val manufacturer = Build.MANUFACTURER?.lowercase() ?: ""
        val brand = Build.BRAND?.lowercase() ?: ""
        val romInfo = when {
            manufacturer.contains("xiaomi") || brand.contains("xiaomi") || brand.contains("redmi") -> "MIUI/HyperOS"
            manufacturer.contains("oppo") || brand.contains("oppo") -> "ColorOS"
            manufacturer.contains("vivo") || brand.contains("vivo") -> "OriginOS/FuntouchOS"
            manufacturer.contains("huawei") || brand.contains("huawei") || brand.contains("honor") -> "HarmonyOS/EMUI"
            manufacturer.contains("samsung") || brand.contains("samsung") -> "OneUI"
            manufacturer.contains("meizu") -> "Flyme"
            else -> "AOSP/other ($manufacturer/$brand)"
        }
        Log.d(TAG, "OEM ROM: $romInfo, model=${Build.MODEL}, sdk=${Build.VERSION.SDK_INT}")
    }

    /**
     * 检测系统设置中预测性返回动画开关状态（仅 Android 13-14，15+ 强制开启）
     *
     * 通过 Settings.Global 读取（非公开 key，可能失败）。
     * 仅用于诊断日志，不作为功能判定依据。
     * 
     * @param context Activity 或 Application 上下文，用于获取 contentResolver
     */
    fun logPredictiveBackDevOptionState(context: Context) {
        if (Build.VERSION.SDK_INT !in Build.VERSION_CODES.TIRAMISU..Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 15+ 强制启用，无需检测
            return
        }
        try {
            // 非公开 key，可能因 ROM 定制失败
            val value = android.provider.Settings.Global.getInt(
                context.contentResolver,
                "predictive_back_animated",
                -1
            )
            Log.d(TAG, "predictive_back_animated dev option = $value (0=off, 1=on, -1=not found)")
        } catch (e: Throwable) {
            Log.d(TAG, "predictive_back_animated dev option not readable (ROM customized)")
        }
    }
}
