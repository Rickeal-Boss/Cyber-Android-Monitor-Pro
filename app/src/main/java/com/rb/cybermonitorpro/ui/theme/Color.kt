package com.rb.cybermonitorpro.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

// ============================================
//  镜瓷白 · 天青 主题 (Edition 6.0.606.0)
//  浅色 = 镜瓷白系 · 深色 = 天青墨系
//  40 个色板 token 均为可变 State，深浅切换由 updateThemeColors(dark) 批量赋值
//  业务代码引用 token 名零改动（仅读取当前值，可变色自动触发重组）
// ============================================

// ── 浅色基准色 (镜瓷白) ──
private val LightCyberBackground     = Color(0xFFF0F2EF)
private val LightCyberCardStart      = Color(0xFFFFFFFF)
private val LightCyberCardEnd        = Color(0xFFFFFFFF)
private val LightCyberMuted          = Color(0xFFEDEFEB)
private val LightCyberPill           = Color(0xFFF7F8F5)
private val LightCyberElevated       = Color(0xFFFFFFFF)
private val LightNeonPurple          = Color(0xFF4F7A70)
private val LightNeonPurpleBright    = Color(0xFF6E9A8D)
private val LightNeonPurplePale      = Color(0xFFE3EAE7)
private val LightNeonPurpleDeep      = Color(0xFF3D6259)
private val LightNeonSteelBlue       = Color(0xFF6E746D)
private val LightNeonCyan            = Color(0xFF5C8A7E)
private val LightNeonMagenta         = Color(0xFFC77D9E)
private val LightNeonMagentaPurple   = Color(0xFF6E9A8D)
private val LightPurpleGlow          = Color(0x264F7A70)   // ~15% 天青辉光
private val LightPurpleGlowLight     = Color(0x1A4F7A70)   // ~10% 淡辉光
private val LightPurpleGlowStrong    = Color(0x404F7A70)   // ~25% 强辉光
private val LightChartLinePurple     = Color(0xFF4F7A70)
private val LightChartAreaPurple     = Color(0x294F7A70)   // ~16% 透明
private val LightChartGlow           = Color(0x334F7A70)   // ~20% 辉光
private val LightSuccessNeon         = Color(0xFF4CAF7D)
private val LightWarningNeon         = Color(0xFFD4943A)
private val LightErrorNeon           = Color(0xFFD4574E)
private val LightTitaniumGold        = Color(0xFFC9A84C)
private val LightDeepRedAlert        = Color(0xFFB83A3A)
private val LightNeonDeepPink        = Color(0xFFC77D9E)
private val LightTextPrimary         = Color(0xFF2B302D)
private val LightTextSecondary       = Color(0xFF6E746D)
private val LightTextValue           = Color(0xFF3D443F)
private val LightTextOnPrimary       = Color(0xFFFFFFFF)
private val LightDividerCyber        = Color(0xFFE0E3DF)
private val LightProgressTrack       = Color(0xFFE8EAE6)

// ── 嵌瓷色板 浅色基准 (潮汕嵌瓷工艺色: 宝蓝×柿红×瓷绿 等, 图表线专用) ──
private val LightPorcelainBlue       = Color(0xFF3B6EA5)
private val LightPorcelainRed        = Color(0xFFC25B3A)
private val LightPorcelainGreen      = Color(0xFF2E8C7A)
private val LightPorcelainGold       = Color(0xFFC9A227)
private val LightPorcelainViolet     = Color(0xFF7B6BA8)
private val LightPorcelainPink       = Color(0xFFB85C7E)
private val LightPorcelainNeutral    = Color(0xFFAEBEB8)
private val LightPorcelainInk        = Color(0xFF3A3A40)

// ── 深色基准色 (天青墨) ──
private val DarkCyberBackground      = Color(0xFF14161A)
private val DarkCyberCardStart       = Color(0xFF1E2226)
private val DarkCyberCardEnd         = Color(0xFF1E2226)
private val DarkCyberMuted           = Color(0xFF2A2E33)
private val DarkCyberPill            = Color(0xFF1E2226)
private val DarkCyberElevated        = Color(0xFF25292D)
private val DarkNeonPurple           = Color(0xFF8FBFB2)
private val DarkNeonPurpleBright     = Color(0xFFA9CFC4)
private val DarkNeonPurplePale       = Color(0xFF35423D)
private val DarkNeonPurpleDeep       = Color(0xFF6FA392)
private val DarkNeonSteelBlue        = Color(0xFF9AA39E)
private val DarkNeonCyan             = Color(0xFF9FC9BE)
private val DarkNeonMagenta          = Color(0xFFE098B8)
private val DarkNeonMagentaPurple    = Color(0xFFA9CFC4)
private val DarkPurpleGlow           = Color(0x268FBFB2)   // ~15% 天青辉光
private val DarkPurpleGlowLight      = Color(0x1A8FBFB2)   // ~10% 淡辉光
private val DarkPurpleGlowStrong     = Color(0x408FBFB2)   // ~25% 强辉光
private val DarkChartLinePurple      = Color(0xFF8FBFB2)
private val DarkChartAreaPurple      = Color(0x298FBFB2)   // ~16% 透明
private val DarkChartGlow            = Color(0x338FBFB2)   // ~20% 辉光
private val DarkSuccessNeon          = Color(0xFF6FCF97)
private val DarkWarningNeon          = Color(0xFFE8B05C)
private val DarkErrorNeon            = Color(0xFFE8766E)
private val DarkTitaniumGold         = Color(0xFFD4B86A)
private val DarkDeepRedAlert         = Color(0xFFD05858)
private val DarkNeonDeepPink         = Color(0xFFE098B8)
private val DarkTextPrimary          = Color(0xFFE8EAE7)
private val DarkTextSecondary        = Color(0xFF9AA39E)
private val DarkTextValue            = Color(0xFFCBD3CF)
private val DarkTextOnPrimary        = Color(0xFF14161A)
private val DarkDividerCyber         = Color(0xFF2A2E33)
private val DarkProgressTrack        = Color(0xFF2A2E33)

// ── 嵌瓷色板 深色基准 ──
private val DarkPorcelainBlue        = Color(0xFF7FA8D0)
private val DarkPorcelainRed         = Color(0xFFE08A66)
private val DarkPorcelainGreen       = Color(0xFF6FBFAD)
private val DarkPorcelainGold        = Color(0xFFDCC066)
private val DarkPorcelainViolet      = Color(0xFFAD9FD0)
private val DarkPorcelainPink        = Color(0xFFDA94AC)
private val DarkPorcelainNeutral     = Color(0xFFC9D6D1)
private val DarkPorcelainInk         = Color(0xFF55555E)

// ── 40 个可变色板 token (初始 = 浅色基准) ──
var CyberBackground     by mutableStateOf(LightCyberBackground)
var CyberCardStart      by mutableStateOf(LightCyberCardStart)
var CyberCardEnd        by mutableStateOf(LightCyberCardEnd)
var CyberMuted          by mutableStateOf(LightCyberMuted)
var CyberPill           by mutableStateOf(LightCyberPill)
var CyberElevated       by mutableStateOf(LightCyberElevated)
var NeonPurple          by mutableStateOf(LightNeonPurple)
var NeonPurpleBright    by mutableStateOf(LightNeonPurpleBright)
var NeonPurplePale      by mutableStateOf(LightNeonPurplePale)
var NeonPurpleDeep      by mutableStateOf(LightNeonPurpleDeep)
var NeonSteelBlue       by mutableStateOf(LightNeonSteelBlue)
var NeonCyan            by mutableStateOf(LightNeonCyan)
var NeonMagenta         by mutableStateOf(LightNeonMagenta)
var NeonMagentaPurple   by mutableStateOf(LightNeonMagentaPurple)
var PurpleGlow          by mutableStateOf(LightPurpleGlow)
var PurpleGlowLight     by mutableStateOf(LightPurpleGlowLight)
var PurpleGlowStrong    by mutableStateOf(LightPurpleGlowStrong)
var ChartLinePurple     by mutableStateOf(LightChartLinePurple)
var ChartAreaPurple     by mutableStateOf(LightChartAreaPurple)
var ChartGlow           by mutableStateOf(LightChartGlow)
var SuccessNeon         by mutableStateOf(LightSuccessNeon)
var WarningNeon         by mutableStateOf(LightWarningNeon)
var ErrorNeon           by mutableStateOf(LightErrorNeon)
var TitaniumGold        by mutableStateOf(LightTitaniumGold)
var DeepRedAlert        by mutableStateOf(LightDeepRedAlert)
var NeonDeepPink        by mutableStateOf(LightNeonDeepPink)
var TextPrimary         by mutableStateOf(LightTextPrimary)
var TextSecondary       by mutableStateOf(LightTextSecondary)
var TextValue           by mutableStateOf(LightTextValue)
var TextOnPrimary       by mutableStateOf(LightTextOnPrimary)
var DividerCyber        by mutableStateOf(LightDividerCyber)
var ProgressTrack       by mutableStateOf(LightProgressTrack)

// ── 8 个嵌瓷色板 token (图表线专用, 初始 = 浅色基准) ──
var PorcelainBlue       by mutableStateOf(LightPorcelainBlue)
var PorcelainRed        by mutableStateOf(LightPorcelainRed)
var PorcelainGreen      by mutableStateOf(LightPorcelainGreen)
var PorcelainGold       by mutableStateOf(LightPorcelainGold)
var PorcelainViolet     by mutableStateOf(LightPorcelainViolet)
var PorcelainPink       by mutableStateOf(LightPorcelainPink)
var PorcelainNeutral    by mutableStateOf(LightPorcelainNeutral)
var PorcelainInk        by mutableStateOf(LightPorcelainInk)

// ── 固定釉影 token — 不随主题切换，供各卡片阴影复用 ──
internal val AmbientShadow = Color(0x0D2A2E33)
internal val SpotShadow    = Color(0x142A2E33)

/**
 * 按主题深浅批量赋值 40 个色板 token。
 * 在 DeviceInfoViewerTheme 的 SideEffect 中调用；赋值即触发读取方重组。
 */
internal fun updateThemeColors(dark: Boolean) {
    if (dark) {
        CyberBackground     = DarkCyberBackground
        CyberCardStart      = DarkCyberCardStart
        CyberCardEnd        = DarkCyberCardEnd
        CyberMuted          = DarkCyberMuted
        CyberPill           = DarkCyberPill
        CyberElevated       = DarkCyberElevated
        NeonPurple          = DarkNeonPurple
        NeonPurpleBright    = DarkNeonPurpleBright
        NeonPurplePale      = DarkNeonPurplePale
        NeonPurpleDeep      = DarkNeonPurpleDeep
        NeonSteelBlue       = DarkNeonSteelBlue
        NeonCyan            = DarkNeonCyan
        NeonMagenta         = DarkNeonMagenta
        NeonMagentaPurple   = DarkNeonMagentaPurple
        PurpleGlow          = DarkPurpleGlow
        PurpleGlowLight     = DarkPurpleGlowLight
        PurpleGlowStrong    = DarkPurpleGlowStrong
        ChartLinePurple     = DarkChartLinePurple
        ChartAreaPurple     = DarkChartAreaPurple
        ChartGlow           = DarkChartGlow
        SuccessNeon         = DarkSuccessNeon
        WarningNeon         = DarkWarningNeon
        ErrorNeon           = DarkErrorNeon
        TitaniumGold        = DarkTitaniumGold
        DeepRedAlert        = DarkDeepRedAlert
        NeonDeepPink        = DarkNeonDeepPink
        TextPrimary         = DarkTextPrimary
        TextSecondary       = DarkTextSecondary
        TextValue           = DarkTextValue
        TextOnPrimary       = DarkTextOnPrimary
        DividerCyber        = DarkDividerCyber
        ProgressTrack       = DarkProgressTrack
        PorcelainBlue       = DarkPorcelainBlue
        PorcelainRed        = DarkPorcelainRed
        PorcelainGreen      = DarkPorcelainGreen
        PorcelainGold       = DarkPorcelainGold
        PorcelainViolet     = DarkPorcelainViolet
        PorcelainPink       = DarkPorcelainPink
        PorcelainNeutral    = DarkPorcelainNeutral
        PorcelainInk        = DarkPorcelainInk
    } else {
        CyberBackground     = LightCyberBackground
        CyberCardStart      = LightCyberCardStart
        CyberCardEnd        = LightCyberCardEnd
        CyberMuted          = LightCyberMuted
        CyberPill           = LightCyberPill
        CyberElevated       = LightCyberElevated
        NeonPurple          = LightNeonPurple
        NeonPurpleBright    = LightNeonPurpleBright
        NeonPurplePale      = LightNeonPurplePale
        NeonPurpleDeep      = LightNeonPurpleDeep
        NeonSteelBlue       = LightNeonSteelBlue
        NeonCyan            = LightNeonCyan
        NeonMagenta         = LightNeonMagenta
        NeonMagentaPurple   = LightNeonMagentaPurple
        PurpleGlow          = LightPurpleGlow
        PurpleGlowLight     = LightPurpleGlowLight
        PurpleGlowStrong    = LightPurpleGlowStrong
        ChartLinePurple     = LightChartLinePurple
        ChartAreaPurple     = LightChartAreaPurple
        ChartGlow           = LightChartGlow
        SuccessNeon         = LightSuccessNeon
        WarningNeon         = LightWarningNeon
        ErrorNeon           = LightErrorNeon
        TitaniumGold        = LightTitaniumGold
        DeepRedAlert        = LightDeepRedAlert
        NeonDeepPink        = LightNeonDeepPink
        TextPrimary         = LightTextPrimary
        TextSecondary       = LightTextSecondary
        TextValue           = LightTextValue
        TextOnPrimary       = LightTextOnPrimary
        DividerCyber        = LightDividerCyber
        ProgressTrack       = LightProgressTrack
        PorcelainBlue       = LightPorcelainBlue
        PorcelainRed        = LightPorcelainRed
        PorcelainGreen      = LightPorcelainGreen
        PorcelainGold       = LightPorcelainGold
        PorcelainViolet     = LightPorcelainViolet
        PorcelainPink       = LightPorcelainPink
        PorcelainNeutral    = LightPorcelainNeutral
        PorcelainInk        = LightPorcelainInk
    }
}
