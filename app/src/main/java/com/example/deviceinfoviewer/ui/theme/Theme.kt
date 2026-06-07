package com.example.deviceinfoviewer.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// ================================================================
//  MCU 暗色方案 — 保证 WCAG AA 对比度
//  种子色 #7C3AED, TonalSpot 变体, contrastLevel=0
//  所有 hex 由 @material/material-color-utilities v0.4.0 计算
// ================================================================
private val CyberDarkScheme = darkColorScheme(
    primary             = McuPrimary,
    onPrimary           = McuOnPrimary,
    primaryContainer    = McuPrimaryContainer,
    onPrimaryContainer  = McuOnPrimaryContainer,
    secondary           = McuSecondary,
    onSecondary         = McuOnSecondary,
    secondaryContainer  = McuSecondaryContainer,
    onSecondaryContainer = McuOnSecondaryContainer,
    tertiary            = McuTertiary,
    onTertiary          = McuOnTertiary,
    tertiaryContainer   = McuTertiaryContainer,
    onTertiaryContainer = McuOnTertiaryContainer,
    error               = McuError,
    onError             = McuOnError,
    errorContainer      = McuErrorContainer,
    onErrorContainer    = McuOnErrorContainer,
    background          = McuBackground,
    onBackground        = McuOnBackground,
    surface             = McuSurface,
    onSurface           = McuOnSurface,
    surfaceVariant      = McuSurfaceVariant,
    onSurfaceVariant    = McuOnSurfaceVariant,
    outline             = McuOutline,
    outlineVariant      = McuOutlineVariant,
    inverseSurface      = McuOnSurface,
    inverseOnSurface    = McuSurface,
    inversePrimary      = McuLightPrimary,
    surfaceTint         = McuPrimary,
)

// ================================================================
//  MCU 亮色方案 — 用于系统亮色模式
// ================================================================
private val CyberLightScheme = lightColorScheme(
    primary             = McuLightPrimary,
    onPrimary           = McuLightOnPrimary,
    primaryContainer    = McuLightPrimaryContainer,
    onPrimaryContainer  = McuLightOnPrimaryContainer,
    secondary           = McuLightSecondary,
    onSecondary         = McuLightOnSecondary,
    secondaryContainer  = McuLightSecondaryContainer,
    onSecondaryContainer = McuLightOnSecondaryContainer,
    tertiary            = McuLightTertiary,
    onTertiary          = McuLightOnTertiary,
    tertiaryContainer   = McuLightTertiaryContainer,
    onTertiaryContainer = McuLightOnTertiaryContainer,
    error               = McuLightError,
    onError             = McuLightOnError,
    errorContainer      = McuLightErrorContainer,
    onErrorContainer    = McuLightOnErrorContainer,
    background          = McuLightBackground,
    onBackground        = McuLightOnBackground,
    surface             = McuLightSurface,
    onSurface           = McuLightOnSurface,
    surfaceVariant      = McuLightSurfaceVariant,
    onSurfaceVariant    = McuLightOnSurfaceVariant,
    outline             = McuLightOutline,
    outlineVariant      = McuLightOutlineVariant,
    inverseSurface      = McuLightOnSurface,
    inverseOnSurface    = McuLightSurface,
    inversePrimary      = McuPrimaryContainer,
    surfaceTint         = McuLightPrimary,
)

/**
 * Device Info Viewer 主题入口
 *
 * 三层策略:
 *   1. dynamicColor=true + API 31+ → 系统壁纸动态取色
 *   2. 暗色模式 → MCU 暗色方案 (WCAG AA 保证)
 *   3. 亮色模式 → MCU 亮色方案
 *
 * 赛博朋克装饰层 (辉光/渐变) 在组件中通过 Color.kt 装饰色独立引用，
 * 不受 MaterialTheme.colorScheme 约束。
 */
@Composable
fun DeviceInfoViewerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> CyberDarkScheme
        else      -> CyberLightScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content     = content
    )
}
