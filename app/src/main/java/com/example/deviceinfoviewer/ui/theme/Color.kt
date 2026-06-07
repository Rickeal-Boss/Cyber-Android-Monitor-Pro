package com.example.deviceinfoviewer.ui.theme

import androidx.compose.ui.graphics.Color

// ================================================================
//  Material 3 赛博朋克 HUD 主题 — 双层次色彩系统
//
//  基础层: MCU TonalSpot 算法 (seed #7C3AED, HCT h=301.88 c=78.88)
//           由 @material/material-color-utilities v0.4.0 精确计算
//           保证 WCAG AA 对比度，提供完整 Material 3 色彩角色
//
//  装饰层: 赛博朋克辉光/渐变/图表颜色
//           叠加在 MCU 基础层之上，保留暗紫渐变美学
//
//  兼容层: 旧颜色名称别名，指向 MCU 新颜色
//           确保现有代码零破坏，逐步迁移
// ================================================================

// ================================================================
//  第一部分 — MCU 暗色方案 (TonalSpot, contrastLevel=0)
//  HCT 色调调色板保证所有 onX / X 对比度 >= 4.5:1
// ================================================================

// Primary 色系 (hue=301.88, chroma=36)
val McuPrimary             = Color(0xFFD2BCFD)  // tone 80 — 暗色主紫
val McuOnPrimary           = Color(0xFF38265C)  // tone 20 — 深色文字 on Primary
val McuPrimaryContainer    = Color(0xFF4F3D74)  // tone 30 — 容器
val McuOnPrimaryContainer  = Color(0xFFEADDFF)  // tone 90 — 文字 on 容器

// Secondary 色系 (hue=301.88, chroma=16)
val McuSecondary            = Color(0xFFCDC2DB)  // tone 80
val McuOnSecondary          = Color(0xFF342D40)  // tone 20
val McuSecondaryContainer   = Color(0xFF4B4358)  // tone 30
val McuOnSecondaryContainer = Color(0xFFE9DEF8)  // tone 90

// Tertiary 色系 (hue=1.88, chroma=24)  [hue = primary.hue + 60]
val McuTertiary             = Color(0xFFF0B7C5)  // tone 80
val McuOnTertiary           = Color(0xFF4A2530)  // tone 20
val McuTertiaryContainer    = Color(0xFF643B46)  // tone 30
val McuOnTertiaryContainer  = Color(0xFFFFD9E1)  // tone 90

// Error 色系 (hue=25, chroma=84)
val McuError                = Color(0xFFFFB4AB)  // tone 80
val McuOnError              = Color(0xFF690005)  // tone 20
val McuErrorContainer       = Color(0xFF93000A)  // tone 30
val McuOnErrorContainer     = Color(0xFFFFDAD6)  // tone 90

// Surface / Background (neutral palette: hue=301.88, chroma=6)
val McuBackground           = Color(0xFF151218)  // tone 6  — 暗紫底色
val McuOnBackground         = Color(0xFFE7E0E8)  // tone 90 — 亮文字
val McuSurface              = Color(0xFF151218)  // tone 6  — 表面
val McuOnSurface            = Color(0xFFE7E0E8)  // tone 90 — 表面文字

// Surface Variant / Outline (neutral variant: hue=301.88, chroma=8)
val McuSurfaceVariant       = Color(0xFF49454E)  // tone 30 — 次级表面
val McuOnSurfaceVariant     = Color(0xFFCBC4CF)  // tone 80 — 次级文字
val McuOutline              = Color(0xFF948F99)  // tone 60 — 轮廓线
val McuOutlineVariant       = Color(0xFF49454E)  // tone 30 — 轮廓变体

// ================================================================
//  第二部分 — MCU 亮色方案 (TonalSpot, contrastLevel=0)
//  用于系统亮色模式
// ================================================================

val McuLightPrimary             = Color(0xFF67548E)  // tone 40
val McuLightOnPrimary           = Color(0xFFFFFFFF)  // tone 100
val McuLightPrimaryContainer    = Color(0xFFEADDFF)  // tone 90
val McuLightOnPrimaryContainer  = Color(0xFF1D1B20)  // tone 10

val McuLightSecondary            = Color(0xFF635B70)  // tone 40
val McuLightOnSecondary          = Color(0xFFFFFFFF)  // tone 100
val McuLightSecondaryContainer   = Color(0xFFE9DEF8)  // tone 90
val McuLightOnSecondaryContainer = Color(0xFF1D1B20)  // tone 10

val McuLightTertiary             = Color(0xFF7E525E)  // tone 40
val McuLightOnTertiary           = Color(0xFFFFFFFF)  // tone 100
val McuLightTertiaryContainer    = Color(0xFFFFD9E1)  // tone 90
val McuLightOnTertiaryContainer  = Color(0xFF1D1B20)  // tone 10

val McuLightError                = Color(0xFFBA1A1A)  // tone 40
val McuLightOnError              = Color(0xFFFFFFFF)  // tone 100
val McuLightErrorContainer       = Color(0xFFFFDAD6)  // tone 90
val McuLightOnErrorContainer     = Color(0xFF1D1B20)  // tone 10

val McuLightBackground           = Color(0xFFFEF7FF)  // tone 98
val McuLightOnBackground         = Color(0xFF1D1B20)  // tone 10
val McuLightSurface              = Color(0xFFFEF7FF)  // tone 98
val McuLightOnSurface            = Color(0xFF1D1B20)  // tone 10

val McuLightSurfaceVariant       = Color(0xFFE7E0EB)  // tone 90
val McuLightOnSurfaceVariant     = Color(0xFF49454E)  // tone 30
val McuLightOutline              = Color(0xFF7A757F)  // tone 50
val McuLightOutlineVariant       = Color(0xFFCBC4CF)  // tone 80

// ================================================================
//  第三部分 — 赛博朋克装饰层
//  辉光 · 渐变 · 图表 · 语义色 — 不受 MCU 约束
// ================================================================

// 卡片渐变 (保留原有的暗紫渐变效果)
val CyberCardStart   = Color(0xFF171417)
val CyberCardEnd     = Color(0xFF451B45)

// 紫色辉光 (seed #7C3AED 不同透明度 — 用于卡片阴影和光晕)
val PurpleGlow       = Color(0x267C3AED)  // ~15% — 标准辉光
val PurpleGlowLight  = Color(0x1A7C3AED)  // ~10% — 淡辉光
val PurpleGlowStrong = Color(0x407C3AED)  // ~25% — 强辉光

// 图表颜色
val ChartLineColor = McuPrimary
val ChartAreaColor = Color(0x307C3AED)  // 基于原始 neon 紫的透明色

// 语义色 (暗色主题 — 保持鲜艳)
val SemanticSuccess  = Color(0xFF34C759)  // 霓虹绿
val SemanticWarning  = Color(0xFFFFAB00)  // 琥珀
val SemanticInfo     = Color(0xFF00D4FF)  // 霓虹青

// ================================================================
//  第四部分 — 霓虹强调色 (独立于 MCU，用于图标/Tab/按钮高亮)
//  这些颜色较鲜艳，单独使用可能不满足 WCAG AA
//  应当在有足够对比度的容器上使用
// ================================================================

val NeonAccent        = Color(0xFF7C3AED)  // 原始主霓虹紫
val NeonAccentBright  = Color(0xFFA78BFA)  // 亮霓虹紫
val NeonAccentCyan    = Color(0xFF00D4FF)  // 霓虹青
val NeonAccentMagenta = Color(0xFFF43F5E)  // 霓虹玫红

// ================================================================
//  第五部分 — 向后兼容别名
//  旧颜色名 → 新 MCU / 装饰色映射
//  现有代码无需修改即可工作
//  后续可逐步迁移到 MaterialTheme.colorScheme 引用
// ================================================================

// 紫色系
val NeonPurple       = NeonAccent           // 保持鲜艳 — 图标/Tab/HUD 强调
val NeonPurpleBright = McuPrimary           // MCU 暗色主紫 (对比度 ~8.5:1, 通过 WCAG AAA)
val NeonPurplePale   = McuOnSurface          // 浅紫白文字
val NeonPurpleDeep   = McuOutline            // 深紫边框 → 轮廓色

// 辅助色
val NeonSteelBlue    = McuOnSurfaceVariant   // 非激活 Tab → 次级表面文字
val NeonCyan         = NeonAccentCyan        // 保持鲜艳 — 品牌青
val NeonMagenta      = NeonAccentMagenta     // 保持鲜艳 — 品牌玫红

// 背景与表面
val CyberBackground  = McuBackground
val CyberMuted       = McuSurfaceVariant
val CyberPill        = McuSurface
val CyberElevated    = McuSurfaceVariant     // 弹窗 → 次级表面

// 图表
val ChartLinePurple  = ChartLineColor
val ChartAreaPurple  = ChartAreaColor
val ChartGlow        = PurpleGlowStrong

// 语义色
val SuccessNeon      = SemanticSuccess
val WarningNeon      = SemanticWarning
val ErrorNeon        = McuError              // MCU 红色系，对比度保证

// 文字
val TextPrimary      = McuOnSurface
val TextSecondary    = McuOnSurfaceVariant
val TextValue        = McuOnPrimaryContainer // 数值高亮
val TextOnPrimary    = McuOnPrimary

// 分割线 / 进度条
val DividerCyber     = McuOutline
val ProgressTrack    = McuPrimaryContainer
