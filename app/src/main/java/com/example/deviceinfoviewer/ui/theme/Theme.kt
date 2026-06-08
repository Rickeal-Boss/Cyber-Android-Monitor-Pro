package com.example.deviceinfoviewer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Cyberpunk Mobile HUD 暗色方案 — 匹配 Ardot 设计稿
 * 无论系统深浅模式，始终使用暗紫方案
 */
private val CyberpunkColorScheme = darkColorScheme(
    primary             = NeonPurple,           // #7C3AED
    onPrimary           = TextOnPrimary,
    primaryContainer    = NeonPurpleDeep,       // #4C1D95
    onPrimaryContainer  = NeonPurpleBright,     // #A78BFA

    secondary           = NeonSteelBlue,        // #3D70B8
    onSecondary         = TextOnPrimary,
    secondaryContainer  = CyberMuted,           // #27273B
    onSecondaryContainer = NeonCyan,

    tertiary            = NeonMagenta,          // #F43F5E
    onTertiary          = TextOnPrimary,
    tertiaryContainer   = CyberMuted,
    onTertiaryContainer = NeonMagenta,

    error               = ErrorNeon,
    onError             = CyberBackground,
    errorContainer      = ErrorNeon.copy(alpha = 0.15f),
    onErrorContainer    = ErrorNeon,

    background          = CyberBackground,     // #0A0A0F
    onBackground        = TextPrimary,

    surface             = CyberPill,           // #1E1C35
    onSurface           = TextPrimary,
    surfaceVariant      = CyberMuted,          // #27273B
    onSurfaceVariant    = TextSecondary,

    outline             = NeonPurpleDeep,      // #4C1D95
    outlineVariant      = NeonPurpleDeep.copy(alpha = 0.5f)
)

@Composable
fun DeviceInfoViewerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme  = CyberpunkColorScheme,
        content      = content
    )
}
