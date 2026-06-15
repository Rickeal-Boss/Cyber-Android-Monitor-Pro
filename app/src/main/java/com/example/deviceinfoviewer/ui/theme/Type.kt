package com.example.deviceinfoviewer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.deviceinfoviewer.R

// ═══════════════ 字体族 — 双层字体体系 ═══════════════

/**
 * Layer 1: Orbitron Bold — 标题、重要指标、数值大字
 * 来源: https://github.com/theleagueof/orbitron
 */
val OrbitronBold = FontFamily(
    Font(R.font.orbitron_bold, FontWeight.Bold),
)

/**
 * Layer 2: Orbitron Medium — 数据展示、英文正文、标签
 * 来源: https://github.com/theleagueof/orbitron
 */
val OrbitronMedium = FontFamily(
    Font(R.font.orbitron_medium, FontWeight.Medium),
)

// 应用默认字体（中文系统字体兜底）
val AppFontFamily = FontFamily.Default

// ═══════════════ Material 3 Typography ═══════════════

/**
 * 字体层级规则:
 * - display / headline → Orbitron Bold (标题/大数值)
 * - body*             → Orbitron Medium (数据/正文)
 * - label*            → 系统默认 (中文 UI 标签)
 *
 * 注: 中文文本由系统默认字体（Noto Sans SC / MiSans / 思源黑体）自动兜底。
 */
val Typography = Typography(
    // ── 大标题: Orbitron Bold ──
    displayLarge = TextStyle(fontFamily = OrbitronBold, fontWeight = FontWeight.Bold, fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp),
    displayMedium = TextStyle(fontFamily = OrbitronBold, fontWeight = FontWeight.Bold, fontSize = 45.sp, lineHeight = 52.sp),
    displaySmall = TextStyle(fontFamily = OrbitronBold, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontFamily = OrbitronBold, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = OrbitronBold, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = OrbitronBold, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),

    // ── 正文/数据: Orbitron Medium ──
    bodyLarge = TextStyle(fontFamily = OrbitronMedium, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),

    // ── 标签/UI 控件: 系统默认 ──
    labelLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
)
