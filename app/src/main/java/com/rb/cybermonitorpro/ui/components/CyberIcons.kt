package com.rb.cybermonitorpro.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * 赛博风自绘图标库 — material-icons-extended 裁剪替代
 *
 * 背景: 全仓仅 6 个图标真正依赖 material-icons-extended
 * (Window / Language / DragHandle / Sensors / Schedule / BatteryFull),
 * 该依赖携带 2000+ 图标类, release APK 膨胀 2-4MB。
 * 此处以 24×24 描边风格自绘 (与 res/drawable/ic_cyber_* 同一视觉语言),
 * BatteryFull / Sensors 直接复用 ic_cyber_battery / ic_cyber_sensors 的路径数据。
 *
 * 白色描边 + Icon(tint=...) 着色, 与 Material 图标用法完全一致。
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

    /** 电池 — 复用 ic_cyber_battery.xml 路径 (替代 Icons.Filled.BatteryFull) */
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

    /** 传感器 — 复用 ic_cyber_sensors.xml 路径 (替代 Icons.Filled.Sensors) */
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
}
