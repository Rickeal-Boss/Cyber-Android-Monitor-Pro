package com.example.deviceinfoviewer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.deviceinfoviewer.R

/**
 * Orbitron 字体族 — 科技感等宽几何无衬线字体
 *
 * 字体规范（用户要求）：
 * - 标题及重要指标：Orbitron Bold
 * - 数据展示与正文：Orbitron Medium
 *
 * CJK 兜底：Orbitron 仅含拉丁/数字字形。Compose + Skia 文本引擎在遇到字体不支持的
 * 字符（中文/日文/韩文/阿拉伯文等）时，会自动 fallback 到系统默认字体，无需手动配置
 * fallback chain。因此中文 locale 下标题仍使用 Orbitron 渲染英文/数字部分，中文部分
 * 自动降级到系统字体（符合 RTL/CJK 语言"优先降至系统字体"的要求）。
 *
 * 字体文件：res/font/orbitron_bold.ttf, res/font/orbitron_medium.ttf
 */
val OrbitronFontFamily = FontFamily(
    Font(R.font.orbitron_bold, FontWeight.Bold),
    Font(R.font.orbitron_bold, FontWeight.SemiBold),   // SemiBold → 复用 Bold 文件
    Font(R.font.orbitron_medium, FontWeight.Medium),
    Font(R.font.orbitron_medium, FontWeight.Normal),    // Normal → 复用 Medium 文件
    Font(R.font.orbitron_medium, FontWeight.Light),     // Light → 复用 Medium 文件
)

// Material 3 Typography — Orbitron 字体
// 标题系（display/headline/title）= Bold；数据与正文系（body/label）= Medium
val Typography = Typography(
    // ── 标题：Orbitron Bold ──
    displayLarge = TextStyle(fontFamily = OrbitronFontFamily, fontWeight = FontWeight.Bold, fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp),
    displayMedium = TextStyle(fontFamily = OrbitronFontFamily, fontWeight = FontWeight.Bold, fontSize = 45.sp, lineHeight = 52.sp),
    displaySmall = TextStyle(fontFamily = OrbitronFontFamily, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontFamily = OrbitronFontFamily, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = OrbitronFontFamily, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = OrbitronFontFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = OrbitronFontFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = OrbitronFontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontFamily = OrbitronFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),

    // ── 数据展示与正文：Orbitron Medium ──
    bodyLarge = TextStyle(fontFamily = OrbitronFontFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
    bodyMedium = TextStyle(fontFamily = OrbitronFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
    bodySmall = TextStyle(fontFamily = OrbitronFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelLarge = TextStyle(fontFamily = OrbitronFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = OrbitronFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontFamily = OrbitronFontFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
)
