package com.rb.cybermonitorpro.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * 赛博风自绘图标库 — material-icons-extended / material-icons-core 全量替代
 *
 * 背景:
 * - material-icons-extended 携带 2000+ 图标类, release APK 膨胀 2-4MB, 已移除。
 * - material-icons-core 原为 material3 传递依赖, 全仓约 22 处通用图标引用
 *   (Settings / PlayArrow / Star / Favorite / Check / Home / Info / Share / Search / ArrowBack)
 *   已由本文件自绘替代。
 *
 * 此处以 24×24 描边风格自绘 (与 res/drawable/ic_cyber_* 同一视觉语言),
 * 白色描边 + Icon(tint=...) 着色, 与 Material 图标用法完全一致。
 *
 * ★ 新增 (2026-08-10): 第二批 10 个图标, 彻底去除 material-icons-core 依赖。
 */
object CyberIcons {

    private fun cyberIcon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply(block).build()

    private fun ImageVector.Builder.strokePath(data: String, width: Float = 1.8f) {
        addPath(
            pathData = addPathNodes(data),
            stroke = SolidColor(Color.White),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }

    private fun ImageVector.Builder.fillPath(data: String) {
        addPath(
            pathData = addPathNodes(data),
            fill = SolidColor(Color.White),
        )
    }

    /** 悬浮窗 — 田字四格窗 (替代 Icons.Filled.Window) */
    val Window: ImageVector by lazy {
        cyberIcon("CyberWindow") {
            strokePath("M4,4 L20,4 L20,20 L4,20 Z")
            strokePath("M12,4 L12,20", 1.5f)
            strokePath("M4,12 L20,12", 1.5f)
        }
    }

    /** 语言 — 地球 + 经纬线 (替代 Icons.Filled.Language) */
    val Language: ImageVector by lazy {
        cyberIcon("CyberLanguage") {
            // 外球
            strokePath("M12,3 A9,9 0 1,0 12,21 A9,9 0 1,0 12,3 Z")
            // 纵向经线 (椭圆)
            strokePath("M12,3 A4.5,9 0 1,0 12,21 A4.5,9 0 1,0 12,3 Z", 1.5f)
            // 赤道纬线
            strokePath("M3.5,12 L20.5,12", 1.5f)
        }
    }

    /** 拖拽手柄 — 三条横线 (替代 Icons.Filled.DragHandle) */
    val DragHandle: ImageVector by lazy {
        cyberIcon("CyberDragHandle") {
            strokePath("M4,7 L20,7")
            strokePath("M4,12 L20,12")
            strokePath("M4,17 L20,17")
        }
    }

    /** 时钟/计划 — 圆盘 + 指针 (替代 Icons.Filled.Schedule) */
    val Schedule: ImageVector by lazy {
        cyberIcon("CyberSchedule") {
            strokePath("M12,3 A9,9 0 1,0 12,21 A9,9 0 1,0 12,3 Z")
            strokePath("M12,7 L12,12 L15.5,14")
        }
    }

    /** 光照 — 中心光斑 + 八向光线 (设置页 GlobalLight 卡片图标) */
    val Light: ImageVector by lazy {
        cyberIcon("CyberLight") {
            // 中心光斑
            strokePath("M12,8.5 A3.5,3.5 0 1,0 12,15.5 A3.5,3.5 0 1,0 12,8.5 Z")
            // 八向光线
            strokePath("M12,2.5 L12,5 M12,19 L12,21.5 M2.5,12 L5,12 M19,12 L21.5,12", 1.5f)
            strokePath("M5.3,5.3 L7,7 M17,17 L18.7,18.7 M18.7,5.3 L17,7 M7,17 L5.3,18.7", 1.5f)
        }
    }

    /** 电池 — 切角方框 + 正极接头 + 闪电 (替代 Icons.Filled.BatteryFull) */
    val BatteryFull: ImageVector by lazy {
        cyberIcon("CyberBattery") {
            // 电池主体 (切角方框)
            strokePath("M3,7 L19,7 L19,17 L3,17 Z")
            // 正极接头
            strokePath("M19,10 L22,10 L22,14 L19,14")
            // 闪电 (角切风格)
            strokePath("M12,5.5 L8,13 L11,13 L10.5,18.5 L14.5,11 L11.5,11 L12,5.5 Z", 1.5f)
        }
    }

    /** 传感器 — 脉冲波形 + 两侧信号弧 (替代 Icons.Filled.Sensors) */
    val Sensors: ImageVector by lazy {
        cyberIcon("CyberSensors") {
            // 脉冲波形主线
            strokePath("M2,12 L6,12 L8,7 L11,17 L13,7 L15,12 L22,12")
            // 左侧信号弧
            strokePath("M5,9 Q3.5,12 5,15", 1.5f)
            // 右侧信号弧
            strokePath("M19,9 Q20.5,12 19,15", 1.5f)
        }
    }

    // ═══════════════════════════════════════════════════════
    //  第二批: 替代剩余 material-icons-core 通用图标 (2026-08-10)
    // ═══════════════════════════════════════════════════════

    /** 设置 — 六角齿轮 + 内环 + 中心方点 + 顶点刻度 (替代 Icons.Filled.Settings) */
    val Settings: ImageVector by lazy {
        cyberIcon("CyberSettings") {
            // 六角齿轮外框
            strokePath("M12,2.5 L19,6.5 L19,17.5 L12,21.5 L5,17.5 L5,6.5 Z")
            // 内环
            strokePath("M12,8 A4,4 0 1,0 12,16 A4,4 0 1,0 12,8 Z", 1.5f)
            // 中心方点 (填充)
            fillPath("M11,11 L13,11 L13,13 L11,13 Z")
            // 齿轮顶点标记
            strokePath("M12,2.5 L12,4.5 M19,6.5 L17.5,7.5 M19,17.5 L17.5,16.5 M12,21.5 L12,19.5 M5,17.5 L6.5,16.5 M5,6.5 L6.5,7.5", 1.5f)
        }
    }

    /** 播放 — 切角方框 + 播放三角 + 内部电路细节 (替代 Icons.Filled.PlayArrow) */
    val PlayArrow: ImageVector by lazy {
        cyberIcon("CyberPlayArrow") {
            // 切角外框
            strokePath("M5,3 L19,3 L21,5 L21,19 L19,21 L5,21 L3,19 L3,5 Z", 1.5f)
            // 播放三角
            strokePath("M8.5,7.5 L17,12 L8.5,16.5 Z")
            // 三角内部电路细节
            strokePath("M11,10 L14,12 L11,14", 1.2f)
        }
    }

    /** 星标 — 五角棱面星 + 中心竖直刻线 (替代 Icons.Filled.Star) */
    val Star: ImageVector by lazy {
        cyberIcon("CyberStar") {
            // 五角星 (棱面化)
            strokePath("M12,2.5 L14.2,8.8 L21,9.3 L15.7,13.5 L17.5,20.5 L12,16.8 L6.5,20.5 L8.3,13.5 L3,9.3 L9.8,8.8 Z")
            // 中心竖直刻线
            strokePath("M12,11 L12,15", 1.2f)
        }
    }

    /** 收藏/电池 — 棱面心形 + 内部脉冲线 (替代 Icons.Filled.Favorite) */
    val Favorite: ImageVector by lazy {
        cyberIcon("CyberFavorite") {
            // 棱面心形
            strokePath("M12,21 L4,13 L4,8 L7,4.5 L12,8.5 L17,4.5 L20,8 L20,13 Z")
            // 内部脉冲线 (心率计风格)
            strokePath("M7,12.5 L9,12.5 L10,10 L11.5,15 L12.5,10 L14,15 L15,12.5 L17,12.5", 1.2f)
        }
    }

    /** 勾选 — 切角方框 + 对勾标记 (替代 Icons.Filled.Check) */
    val Check: ImageVector by lazy {
        cyberIcon("CyberCheck") {
            // 切角方框
            strokePath("M5,3 L19,3 L21,5 L21,19 L19,21 L5,21 L3,19 L3,5 Z", 1.5f)
            // 勾选标记
            strokePath("M7.5,12 L10.5,15 L16.5,8.5")
        }
    }

    /** 主页/总览 — 切角屋顶 + 房屋主体 + 门框 + 窗户横线 (替代 Icons.Filled.Home) */
    val Home: ImageVector by lazy {
        cyberIcon("CyberHome") {
            // 屋顶 (切角三角形)
            strokePath("M3,11 L12,3 L21,11")
            // 房屋主体
            strokePath("M5,9.5 L5,20 L19,20 L19,9.5")
            // 门
            strokePath("M10,14 L10,20 L14,20 L14,14 Z", 1.5f)
            // 窗户横线
            strokePath("M10,11.5 L14,11.5", 1.2f)
        }
    }

    /** 信息 — 六角外框 + 上方填充方点 + 竖线 (替代 Icons.Filled.Info) */
    val Info: ImageVector by lazy {
        cyberIcon("CyberInfo") {
            // 六角外框
            strokePath("M12,2.5 L20,7 L20,17 L12,21.5 L4,17 L4,7 Z")
            // 上方方点 (填充)
            fillPath("M11,6.5 L13,6.5 L13,8.5 L11,8.5 Z")
            // 竖线
            strokePath("M12,10.5 L12,16.5", 1.5f)
        }
    }

    /** 分享 — 顶部方节点 + 左下/右下方节点 + 三连线 (替代 Icons.Filled.Share) */
    val Share: ImageVector by lazy {
        cyberIcon("CyberShare") {
            // 顶部节点
            strokePath("M9,2.5 L15,2.5 L15,7.5 L9,7.5 Z", 1.5f)
            // 左下节点
            strokePath("M3,15.5 L8,15.5 L8,20.5 L3,20.5 Z", 1.5f)
            // 右下节点
            strokePath("M16,15.5 L21,15.5 L21,20.5 L16,20.5 Z", 1.5f)
            // 连线
            strokePath("M12,7.5 L12,11.5 M12,11.5 L5.5,15.5 M12,11.5 L18.5,15.5")
        }
    }

    /** 搜索 — 八边形切角透镜 + 内部扫描线 + 手柄 (替代 Icons.Filled.Search) */
    val Search: ImageVector by lazy {
        cyberIcon("CyberSearch") {
            // 切角透镜 (八边形)
            strokePath("M10,3 L14.5,4 L17,7 L18,10.5 L17,14 L14.5,17 L10,18 L5.5,17 L3,14 L2,10.5 L3,7 L5.5,4 Z")
            // 透镜内扫描线
            strokePath("M5,10.5 L15,10.5", 1.2f)
            // 手柄
            strokePath("M15,15 L20,20", 1.5f)
        }
    }

    /** 返回 — 棱面箭头 + 尾杆 + 箭头内部棱面线 (替代 Icons.AutoMirrored.Filled.ArrowBack) */
    val ArrowBack: ImageVector by lazy {
        cyberIcon("CyberArrowBack") {
            // 箭头 (棱面)
            strokePath("M15,4 L6,12 L15,20")
            // 尾杆
            strokePath("M6,12 L20,12", 1.5f)
            // 箭头内部棱面线
            strokePath("M15,4 L12,7 L15,8 M15,20 L12,17 L15,16", 1.2f)
        }
    }
}
