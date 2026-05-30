package com.example.deviceinfoviewer.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================
//  Batman Cyberpunk 紫色主题
//  纯紫霓虹 · 深黑背景 · 高对比赛博朋克
// ============================================

// ── 背景与表面 ──
val CyberBackground  = Color(0xFF0A0A0F)   // 深黑带微紫
val CyberSurface     = Color(0xFF12121A)   // 卡片表面
val CyberSurfaceDark = Color(0xFF0D0D14)   // 更深表面
val CyberElevated    = Color(0xFF18182A)   // 浮层/弹窗

// ── 霓虹紫色系 ──
val NeonPurple        = Color(0xFFB347FF)  // 主霓虹紫
val NeonPurpleBright  = Color(0xFFD05CFF)  // 高亮紫
val NeonPurplePale    = Color(0xFFE8C6FF)  // 浅紫（文字用）
val NeonPurpleDeep    = Color(0xFF5A1080)  // 深紫

// ── 高亮色 ──
val NeonCyan          = Color(0xFF00F0FF)  // 霓虹青（次要高亮）
val NeonMagenta       = Color(0xFFFF00E5)  // 霓虹品红
val ElectricBlue      = Color(0xFF4A80FF)  // 电光蓝

// ── 图表颜色 ──
val ChartLinePurple = NeonPurple
val ChartAreaPurple = Color(0x30B347FF)    // ~19% 透明
val ChartGlow       = Color(0x50B347FF)    // 辉光

// ── 功能色 ──
val SuccessNeon  = Color(0xFF00E676)       // 荧光绿
val WarningNeon  = Color(0xFFFFAB00)       // 琥珀霓虹
val ErrorNeon    = Color(0xFFFF1744)       // 猩红霓虹

// ── 文字色 ──
val TextPrimary    = Color(0xFFF0E6FF)     // 主文字（浅紫白）
val TextSecondary  = Color(0xFF8A7AA0)     // 副文字
val TextValue      = NeonPurpleBright      // 数值高亮
val TextOnPrimary  = Color(0xFFFFFFFF)

// ── 分割线与杂项 ──
val DividerCyber   = Color(0xFF2A1A3E)     // 紫色分割线
val ProgressTrack  = Color(0xFF1A1028)     // 进度条背景
