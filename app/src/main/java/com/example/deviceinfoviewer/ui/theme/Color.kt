package com.example.deviceinfoviewer.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================
//  Cyberpunk Mobile HUD 主题 (Ardot 设计稿)
//  暗紫渐变 · 霓虹辉光 · 钢蓝辅色
// ============================================

// ── 背景与表面 ──
val CyberBackground     = Color(0xFF0A0A0F)   // 深黑底色
val CyberCardStart      = Color(0xFF171417)   // 卡片渐变起点 (暗紫黑)
val CyberCardEnd        = Color(0xFF451B45)   // 卡片渐变终点 (暗紫)
val CyberMuted          = Color(0xFF27273B)   // 图标底板/次级表面
val CyberPill           = Color(0xFF1E1C35)   // 底部药丸/浮层
val CyberElevated       = Color(0xFF18182A)   // 弹窗表面

// ── 霓虹紫色系 ──
val NeonPurple          = Color(0xFF7C3AED)   // 主霓虹紫 (Ardot Primary)
val NeonPurpleBright    = Color(0xFFA78BFA)   // 高亮紫 (Ardot Secondary)
val NeonPurplePale      = Color(0xFFE2E8F0)   // 浅紫白文字
val NeonPurpleDeep      = Color(0xFF4C1D95)   // 深紫边框

// ── 辅助色 ──
val NeonSteelBlue       = Color(0xFF3D70B8)   // 钢蓝 (非激活Tab)
val NeonCyan            = Color(0xFF00D4FF)   // 霓虹青
val NeonMagenta         = Color(0xFFF43F5E)   // 玫瑰红 (Ardot Accent)

// ── 辉光阴影色 ──
val PurpleGlow          = Color(0x267C3AED)   // ~15% 紫色辉光
val PurpleGlowLight     = Color(0x1A7C3AED)   // ~10% 淡辉光
val PurpleGlowStrong    = Color(0x407C3AED)   // ~25% 强辉光 (底部药丸)

// ── 图表颜色 ──
val ChartLinePurple     = NeonPurple
val ChartAreaPurple     = Color(0x307C3AED)   // ~19% 透明
val ChartGlow           = Color(0x507C3AED)   // 辉光

// ── 功能色 ──
val SuccessNeon         = Color(0xFF34C759)   // 荧光绿 (设计稿)
val WarningNeon         = Color(0xFFFFAB00)   // 琥珀霓虹
val ErrorNeon           = Color(0xFFFF1744)   // 猩红霓虹

// ── 文字色 ──
val TextPrimary         = Color(0xFFE2E8F0)   // 主文字
val TextSecondary       = Color(0xFF94A3B8)   // 副文字
val TextValue           = NeonPurpleBright     // 数值高亮
val TextOnPrimary       = Color(0xFFFFFFFF)

// ── 分割线 ──
val DividerCyber        = Color(0xFF4C1D95)   // 紫色分割线
val ProgressTrack       = Color(0xFF1A1028)   // 进度条背景
