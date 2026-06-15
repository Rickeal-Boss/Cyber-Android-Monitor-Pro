package com.example.deviceinfoviewer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.deviceinfoviewer.R

// ═══════════════ 字体族 — 三层字体体系 ═══════════════

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

/**
 * Layer 3: 更纱黑体 SC (Sarasa Gothic SC) — 中文正文、中文指标
 * 来源: https://github.com/be5invis/Sarasa-Gothic
 *
 * 更纱黑体 SC 是一款基于 Iosevka 和思源黑体的等宽中文编程字体，
 * 在 UI 场景下提供均匀的中英文混排间距。
 */
val SarasaGothic = FontFamily(
    Font(R.font.sarasagothicsc_regular, FontWeight.Normal),
)

/**
 * 混合字体族 — Orbitron Medium + Sarasa Gothic 混排,
 * 英文/数字走 Orbitron，中文自动 fallback 到 Sarasa
 */
val MixedFontFamily = FontFamily(
    Font(R.font.orbitron_medium, FontWeight.Medium),
    Font(R.font.sarasagothicsc_regular, FontWeight.Normal),
)

/**
 * Bold 混合字体族 — 标题级混排
 */
val MixedBoldFontFamily = FontFamily(
    Font(R.font.orbitron_bold, FontWeight.Bold),
    Font(R.font.sarasagothicsc_regular, FontWeight.Normal),
)

// 应用默认字体（中文界面基准）
val AppFontFamily = SarasaGothic

// ═══════════════ Material 3 Typography ═══════════════

/**
 * 字体层级规则:
 * - display* / headline* → Orbitron Bold (标题)
 * - bodyLarge → MixedBoldFontFamily (重要指标/数值)
 * - bodyMedium / bodySmall → MixedFontFamily (数据/正文)
 * - label* → SarasaGothic (中文标签/UI 文字)
 */
val Typography = Typography(
    // ── 大标题: Orbitron Bold ──
    displayLarge = TextStyle(fontFamily = OrbitronBold, fontWeight = FontWeight.Bold, fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp),
    displayMedium = TextStyle(fontFamily = OrbitronBold, fontWeight = FontWeight.Bold, fontSize = 45.sp, lineHeight = 52.sp),
    displaySmall = TextStyle(fontFamily = OrbitronBold, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontFamily = OrbitronBold, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = OrbitronBold, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = OrbitronBold, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),

    // ── 正文/数据: MixedFontFamily (Orbitron Medium + Sarasa fallback) ──
    bodyLarge = TextStyle(fontFamily = MixedBoldFontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
    bodyMedium = TextStyle(fontFamily = MixedFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
    bodySmall = TextStyle(fontFamily = MixedFontFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),

    // ── 标签/UI 控件: SarasaGothic 中文主导 ──
    labelLarge = TextStyle(fontFamily = SarasaGothic, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = SarasaGothic, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontFamily = SarasaGothic, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
)
