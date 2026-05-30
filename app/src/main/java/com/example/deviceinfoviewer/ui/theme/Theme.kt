package com.example.deviceinfoviewer.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * 纯紫暗色方案 — 赛博朋克蝙蝠侠风
 * 无论深浅模式，用同一套暗紫方案
 */
private val CyberpunkColorScheme = darkColorScheme(
    primary             = NeonPurple,
    onPrimary           = TextOnPrimary,
    primaryContainer    = NeonPurpleDeep,
    onPrimaryContainer  = NeonPurplePale,

    secondary           = NeonCyan,
    onSecondary         = CyberBackground,
    secondaryContainer  = NeonPurpleDeep,
    onSecondaryContainer = NeonCyan,

    tertiary            = NeonMagenta,
    onTertiary          = TextOnPrimary,
    tertiaryContainer   = NeonPurpleDeep,
    onTertiaryContainer = NeonMagenta,

    error               = ErrorNeon,
    onError             = CyberBackground,
    errorContainer      = ErrorNeon.copy(alpha = 0.15f),
    onErrorContainer    = ErrorNeon,

    background          = CyberBackground,
    onBackground        = TextPrimary,

    surface             = CyberSurface,
    onSurface           = TextPrimary,
    surfaceVariant      = CyberSurfaceDark,
    onSurfaceVariant    = TextSecondary,

    outline             = DividerCyber,
    outlineVariant      = DividerCyber.copy(alpha = 0.5f)
)

@Composable
fun DeviceInfoViewerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // ⚡ 核心：无论系统是浅/深色模式，始终使用赛博朋克暗紫方案
    MaterialTheme(
        colorScheme  = CyberpunkColorScheme,
        typography   = Typography,
        content      = content
    )
}
