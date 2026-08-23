package com.rb.cybermonitorpro.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * 镜瓷白 · 天青 主题 — 浅色(镜瓷白) / 深色(天青墨) 双方案。
 * 色板 token 为可变 State；SideEffect 内按 darkTheme 批量赋值，读取方自动重组。
 * colorScheme 每次组合现取可变变量值，保证深浅切换后 token 立即生效。
 * 状态栏/导航栏仅设透明与图标明暗（isAppearanceLightStatusBars），不写 statusBarColor
 * （target 35 已忽略该属性）。
 */
private fun LightCyberpunkScheme() = lightColorScheme(
    primary             = NeonPurple,
    onPrimary           = TextOnPrimary,
    primaryContainer    = NeonPurpleDeep,
    onPrimaryContainer  = NeonPurpleBright,

    secondary           = NeonSteelBlue,
    onSecondary         = TextOnPrimary,
    secondaryContainer  = CyberMuted,
    onSecondaryContainer = NeonCyan,

    tertiary            = NeonMagenta,
    onTertiary          = TextOnPrimary,
    tertiaryContainer   = CyberMuted,
    onTertiaryContainer = NeonMagenta,

    error               = ErrorNeon,
    onError             = CyberBackground,
    errorContainer      = ErrorNeon.copy(alpha = 0.15f),
    onErrorContainer    = ErrorNeon,

    background          = CyberBackground,
    onBackground        = TextPrimary,

    surface             = CyberPill,
    onSurface           = TextPrimary,
    surfaceVariant      = CyberMuted,
    onSurfaceVariant    = TextSecondary,

    outline             = NeonPurpleDeep,
    outlineVariant      = NeonPurpleDeep.copy(alpha = 0.5f)
)

private fun DarkCyberpunkScheme() = darkColorScheme(
    primary             = NeonPurple,
    onPrimary           = TextOnPrimary,
    primaryContainer    = NeonPurpleDeep,
    onPrimaryContainer  = NeonPurpleBright,

    secondary           = NeonSteelBlue,
    onSecondary         = TextOnPrimary,
    secondaryContainer  = CyberMuted,
    onSecondaryContainer = NeonCyan,

    tertiary            = NeonMagenta,
    onTertiary          = TextOnPrimary,
    tertiaryContainer   = CyberMuted,
    onTertiaryContainer = NeonMagenta,

    error               = ErrorNeon,
    onError             = CyberBackground,
    errorContainer      = ErrorNeon.copy(alpha = 0.15f),
    onErrorContainer    = ErrorNeon,

    background          = CyberBackground,
    onBackground        = TextPrimary,

    surface             = CyberPill,
    onSurface           = TextPrimary,
    surfaceVariant      = CyberMuted,
    onSurfaceVariant    = TextSecondary,

    outline             = NeonPurpleDeep,
    outlineVariant      = NeonPurpleDeep.copy(alpha = 0.5f)
)

@Composable
fun DeviceInfoViewerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // 深浅切换时批量赋值色板 token（值变化即触发读取方重组）
    SideEffect { updateThemeColors(darkTheme) }

    // 状态栏/导航栏图标明暗跟随主题；颜色透明由 MainActivity enableEdgeToEdge 处理
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme  = if (darkTheme) DarkCyberpunkScheme() else LightCyberpunkScheme(),
        typography   = Typography,
        content      = content
    )
}

/** 从 Compose Context 递归找出 Activity（Context 可能被 wrapper 包裹） */
private fun android.content.Context.findActivity(): android.app.Activity? {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is android.app.Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
