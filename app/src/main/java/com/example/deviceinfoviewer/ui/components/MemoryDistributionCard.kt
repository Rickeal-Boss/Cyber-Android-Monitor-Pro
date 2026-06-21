package com.example.deviceinfoviewer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.deviceinfoviewer.FormatUtils
import com.example.deviceinfoviewer.R
import com.example.deviceinfoviewer.ui.theme.*

/**
 * 内存分布分类颜色 — Batman 赛博朋克主题下的 5 类
 */
object MemoryDistColors {
    val App        = Color(0xFFC084FC)  // 亮紫 — 应用内存 (主 NeonPurple 亮化)
    val Cached     = Color(0xFF818CF8)  // 靛蓝 — 缓存 (调和紫/蓝之间)
    val System     = Color(0xFF00D4FF)  // 青色 — 系统 (NeonCyan)
    val Free       = Color(0xFF34C759)  // 绿色 — 空闲 (SuccessNeon)
    val Other      = Color(0xFF94A3B8)  // 灰色 — 其他 (TextSecondary)
}

private data class MemCategory(
    val label: String,
    val valueKB: Long,
    val color: Color
)

/**
 * 内存分布卡片 — 水平层叠条形图 + 图例
 *
 * 展示 5 类内存的占比 (应用 / 缓存 / 系统 / 空闲 / 其他)，
 * 顶部水平条按比例分配宽度，底部图例列出各分类的数值和百分比。
 */
@Composable
fun MemoryDistributionCard(
    totalKB: Long,
    appKB: Long,
    cachedKB: Long,
    systemKB: Long,
    freeKB: Long,
    otherKB: Long,
    modifier: Modifier = Modifier
) {
    if (totalKB <= 0) return

    val categories = listOf(
        MemCategory(stringResource(R.string.memory_dist_app), appKB, MemoryDistColors.App),
        MemCategory(stringResource(R.string.memory_dist_cached), cachedKB, MemoryDistColors.Cached),
        MemCategory(stringResource(R.string.memory_dist_system), systemKB, MemoryDistColors.System),
        MemCategory(stringResource(R.string.memory_dist_free), freeKB, MemoryDistColors.Free),
        MemCategory(stringResource(R.string.memory_dist_other), otherKB, MemoryDistColors.Other)
    ).filter { it.valueKB >= 0 }

    if (categories.isEmpty()) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 10.dp, shape = RoundedCornerShape(12.dp), ambientColor = PurpleGlowLight),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CardGradient)
                .hdrHighlight(12.dp)
        ) {
            Column(Modifier.padding(14.dp)) {
                // 标题
                Text(
                    stringResource(R.string.memory_dist_title),
                    fontSize = 11.sp,
                    color = TextSecondary,
                    letterSpacing = 0.5.sp
                )
                Spacer(Modifier.height(4.dp))

                // 总量概览
                Text(
                    formatTotalSummary(totalKB, categories),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonPurpleBright,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.5.sp
                )
                Spacer(Modifier.height(10.dp))

                // === 水平层叠条形图 ===
                val totalFloat = totalKB.toFloat()
                if (totalFloat > 0) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                    ) {
                        categories.forEach { cat ->
                            val fraction = (cat.valueKB.toFloat() / totalFloat).coerceIn(0f, 1f)
                            Box(
                                Modifier
                                    .weight(fraction.coerceAtLeast(0.001f))
                                    .fillMaxHeight()
                                    .background(cat.color)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // === 图例 (5 行) ===
                categories.forEach { cat ->
                    val pct = if (totalKB > 0) cat.valueKB.toFloat() / totalKB * 100f else 0f
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 颜色圆点
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(cat.color)
                        )
                        Spacer(Modifier.width(8.dp))
                        // 分类名
                        Text(
                            cat.label,
                            fontSize = 13.sp,
                            color = TextPrimary,
                            modifier = Modifier.width(32.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        // 数值
                        Text(
                            FormatUtils.formatBytes(cat.valueKB * 1024),
                            fontSize = 12.sp,
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f)
                        )
                        // 占比
                        Text(
                            "%.1f%%".format(pct),
                            fontSize = 12.sp,
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }
    }
}

private fun formatTotalSummary(totalKB: Long, categories: List<MemCategory>): String {
    val usedKB = categories
        .filter { it.label != "空闲" }
        .sumOf { it.valueKB.coerceAtLeast(0) }
    return "${FormatUtils.formatBytes(usedKB * 1024)} / ${FormatUtils.formatBytes(totalKB * 1024)}"
}
